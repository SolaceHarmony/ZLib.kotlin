package ai.solace.zlib.clean

import ai.solace.zlib.bitwise.ArithmeticBitwiseOps
import ai.solace.zlib.bitwise.checksum.Adler32Utils
import ai.solace.zlib.common.ZlibLogger

/**
 * ArithmeticCleanDeflate - Deflate decompressor using pure arithmetic operations.
 * This version uses ArithmeticBitReader and ArithmeticBitwiseOps for all bitwise operations,
 * making it compatible with platforms that don't support native bitwise ops.
 * 
 * Handles all three DEFLATE block types:
 * - Type 0: Stored (uncompressed) blocks
 * - Type 1: Fixed Huffman coding blocks
 * - Type 2: Dynamic Huffman coding blocks
 */
class ArithmeticCleanDeflate {
    
    private val ops = ArithmeticBitwiseOps.BITS_32
    private val ops8 = ArithmeticBitwiseOps.BITS_8
    private val ops16 = ArithmeticBitwiseOps.BITS_16
    


    private fun handleStoredBlock(reader: ArithmeticBitReader): ByteArray {
        reader.alignToByte()
        val bytesToCopy = 4
        val lenBytes = reader.peekBytes(bytesToCopy)
        
        if (lenBytes.size < 4) {
            throw IllegalStateException("Unexpected EOF in stored block header")
        }
        
        reader.take(16)
        reader.take(16)
        
        val len = ops.or(
            ops8.normalize(lenBytes[0].toLong()),
            ops.leftShift(ops8.normalize(lenBytes[1].toLong()), 8)
        ).toInt()
        
        val data = reader.peekBytes(len)
        repeat(len) { reader.take(8) }
        return data
    }

    private fun buildFixedLiteralTable(): CanonicalHuffman.FullTable {
        // RFC1951: fixed lit/len lengths  
        val lens = IntArray(288)
        for (i in 0..143) lens[i] = 8
        for (i in 144..255) lens[i] = 9  
        for (i in 256..279) lens[i] = 7
        for (i in 280..287) lens[i] = 8
        return CanonicalHuffman.buildFull(lens)
    }

    private fun buildFixedDistTable(): CanonicalHuffman.FullTable {
        val lens = IntArray(32) { 5 }
        return CanonicalHuffman.buildFull(lens)
    }

    private fun decodeSymbol(reader: ArithmeticBitReader, table: CanonicalHuffman.FullTable): Int {
        if (table.maxLen == 0) throw IllegalStateException("Empty Huffman table")
        val look = reader.peek(table.maxLen)
        val len = table.bits[look]
        if (len == 0) {
            throw IllegalStateException("Invalid Huffman prefix")
        }
        val sym = table.vals[look]
        reader.take(len)
        return sym
    }

    private fun handleFixedHuffmanBlock(reader: ArithmeticBitReader): ByteArray {
        val litTable = buildFixedLiteralTable()
        val distTable = buildFixedDistTable()
        val output = mutableListOf<Byte>()
        
        while (true) {
            val symbol = decodeSymbol(reader, litTable)
            when {
                symbol < 256 -> output.add(symbol.toByte())
                symbol == 256 -> break
                symbol > 256 -> {
                    val lengthBase = arrayOf(
                        3, 4, 5, 6, 7, 8, 9, 10, 11, 13, 15, 17, 19, 23, 27, 31,
                        35, 43, 51, 59, 67, 83, 99, 115, 131, 163, 195, 227, 258
                    )
                    val lengthExtra = arrayOf(
                        0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2,
                        3, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5, 5, 0
                    )
                    
                    val lengthIndex = symbol - 257
                    if (lengthIndex < 0 || lengthIndex >= lengthBase.size) {
                        throw IllegalStateException("Invalid length symbol: $symbol (index: $lengthIndex)")
                    }
                    val length = lengthBase[lengthIndex] + reader.take(lengthExtra[lengthIndex])
                    val distCode = decodeSymbol(reader, distTable)
                    val distance = when {
                        distCode <= 3 -> distCode + 1
                        else -> {
                            val extraBits = ops.rightShift((distCode - 2).toLong(), 1).toInt()
                            val base = ops.leftShift(1L, ops.rightShift((distCode - 2).toLong(), 1).toInt() + 1).toInt() + 1
                            val offset = ops.and(distCode.toLong(), 1L).toInt()
                            val extra = reader.take(extraBits)
                            val shifted = ops.leftShift(offset.toLong(), extraBits).toInt()
                            base + shifted + extra
                        }
                    }
                    
                    repeat(length) {
                        if (distance > output.size) {
                            throw IllegalStateException("Invalid distance: $distance > ${output.size}")
                        }
                        output.add(output[output.size - distance])
                    }
                }
            }
        }
        return output.toByteArray()
    }

    private fun buildDynamicHuffmanTrees(reader: ArithmeticBitReader): Pair<HuffmanTree, HuffmanTree> {
        val hlit = reader.take(5) + 257
        val hdist = reader.take(5) + 1
        val hclen = reader.take(4) + 4
        
        val codeLengthOrder = intArrayOf(16, 17, 18, 0, 8, 7, 9, 6, 10, 5, 11, 4, 12, 3, 13, 2, 14, 1, 15)
        val codeLengths = IntArray(19)
        
        repeat(hclen) { i ->
            codeLengths[codeLengthOrder[i]] = reader.take(3)
        }
        
        val codeTree = buildHuffmanTree(codeLengths)
        val lengths = IntArray(hlit + hdist)
        var i = 0
        
        while (i < lengths.size) {
            val symbol = codeTree.decode(reader)
            when {
                symbol <= 15 -> lengths[i++] = symbol
                symbol == 16 -> {
                    val count = reader.take(2) + 3
                    val prev = if (i > 0) lengths[i - 1] else 0
                    repeat(count) {
                        if (i < lengths.size) lengths[i++] = prev
                    }
                }
                symbol == 17 -> {
                    val count = reader.take(3) + 3
                    repeat(count) {
                        if (i < lengths.size) lengths[i++] = 0
                    }
                }
                symbol == 18 -> {
                    val count = reader.take(7) + 11
                    repeat(count) {
                        if (i < lengths.size) lengths[i++] = 0
                    }
                }
            }
        }
        
        val litLengths = lengths.sliceArray(0 until hlit)
        val distLengths = lengths.sliceArray(hlit until hlit + hdist)
        
        return Pair(buildHuffmanTree(litLengths), buildHuffmanTree(distLengths))
    }

    private fun handleDynamicHuffmanBlock(reader: ArithmeticBitReader): ByteArray {
        val (litTree, distTree) = buildDynamicHuffmanTrees(reader)
        val output = mutableListOf<Byte>()
        
        while (true) {
            val symbol = litTree.decode(reader)
            when {
                symbol < 256 -> output.add(symbol.toByte())
                symbol == 256 -> break
                symbol > 256 -> {
                    val lengthBase = arrayOf(
                        3, 4, 5, 6, 7, 8, 9, 10, 11, 13, 15, 17, 19, 23, 27, 31,
                        35, 43, 51, 59, 67, 83, 99, 115, 131, 163, 195, 227, 258
                    )
                    val lengthExtra = arrayOf(
                        0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2,
                        3, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5, 5, 0
                    )
                    
                    val lengthIndex = symbol - 257
                    if (lengthIndex < 0 || lengthIndex >= lengthBase.size) {
                        throw IllegalStateException("Invalid length symbol: $symbol (index: $lengthIndex)")
                    }
                    val length = lengthBase[lengthIndex] + reader.take(lengthExtra[lengthIndex])
                    
                    val distCode = distTree.decode(reader)
                    val distance = when {
                        distCode <= 3 -> distCode + 1
                        else -> {
                            val extraBits = ops.rightShift((distCode - 2).toLong(), 1).toInt()
                            val base = ops.leftShift(1L, ops.rightShift((distCode - 2).toLong(), 1).toInt() + 1).toInt() + 1
                            val offset = ops.and(distCode.toLong(), 1L).toInt()
                            val extra = reader.take(extraBits)
                            val shifted = ops.leftShift(offset.toLong(), extraBits).toInt()
                            base + shifted + extra
                        }
                    }
                    
                    repeat(length) {
                        if (distance > output.size) {
                            throw IllegalStateException("Invalid distance: $distance > ${output.size}")
                        }
                        output.add(output[output.size - distance])
                    }
                }
            }
        }
        return output.toByteArray()
    }

    fun decompress(data: ByteArray): ByteArray {
        val reader = ArithmeticBitReader(data)
        val cmf = reader.take(8)
        val flg = reader.take(8)
        
        val cm = ops.and(cmf.toLong(), 0x0FL).toInt()
        val cinfo = ops.rightShift(cmf.toLong(), 4).toInt()
        val fcheck = ops.and(flg.toLong(), 0x1FL).toInt()
        val fdict = ops.and(ops.rightShift(flg.toLong(), 5), 1L).toInt()
        val flevel = ops.rightShift(flg.toLong(), 6).toInt()
        
        ZlibLogger.log("ZLIB Header: CM=$cm, CINFO=$cinfo, FCHECK=$fcheck, FDICT=$fdict, FLEVEL=$flevel")
        
        if (cm != 8) throw IllegalArgumentException("Unsupported compression method: $cm")
        if (fdict != 0) throw IllegalArgumentException("Dictionary not supported")
        
        val checkValue = ops.or(
            ops.leftShift(cmf.toLong(), 8),
            flg.toLong()
        ).toInt()
        if (checkValue % 31 != 0) {
            throw IllegalArgumentException("Invalid ZLIB header checksum")
        }
        
        val output = mutableListOf<ByteArray>()
        
        while (!reader.eof()) {
            val bfinal = reader.take(1)
            val btype = reader.take(2)
            
            ZlibLogger.log("Block: BFINAL=$bfinal, BTYPE=$btype")
            
            val blockData = when (btype) {
                0 -> handleStoredBlock(reader)
                1 -> handleFixedHuffmanBlock(reader)
                2 -> handleDynamicHuffmanBlock(reader)
                3 -> throw IllegalArgumentException("Reserved block type")
                else -> throw IllegalArgumentException("Invalid block type: $btype")
            }
            
            output.add(blockData)
            
            if (bfinal == 1) break
        }
        
        reader.alignToByte()
        val storedAdler = ByteArray(4)
        repeat(4) { i -> storedAdler[i] = reader.take(8).toByte() }
        
        val decompressed = output.flatMap { it.toList() }.toByteArray()
        
        val storedAdlerValue = ops.or(
            ops.or(
                ops.leftShift(ops8.normalize(storedAdler[0].toLong()), 24),
                ops.leftShift(ops8.normalize(storedAdler[1].toLong()), 16)
            ),
            ops.or(
                ops.leftShift(ops8.normalize(storedAdler[2].toLong()), 8),
                ops8.normalize(storedAdler[3].toLong())
            )
        )
        
        val computedAdler = Adler32Utils.adler32(1L, decompressed, 0, decompressed.size)
        
        ZlibLogger.log("Adler32: stored=${storedAdlerValue.toString(16)}, computed=${computedAdler.toString(16)}")
        
        if (storedAdlerValue != computedAdler) {
            throw IllegalStateException("Adler32 checksum mismatch")
        }
        
        return decompressed
    }
    
    inner class HuffmanTree(private val codes: IntArray, private val lengths: IntArray) {
        fun decode(reader: ArithmeticBitReader): Int {
            var code = 0
            var len = 1
            
            while (len <= 15) {
                code = ops.leftShift(code.toLong(), 1).toInt()
                code = ops.or(code.toLong(), reader.take(1).toLong()).toInt()
                
                for (i in codes.indices) {
                    if (lengths[i] == len && codes[i] == code) {
                        return i
                    }
                }
                len++
            }
            
            throw IllegalStateException("Invalid Huffman code")
        }
    }
    
    private fun buildHuffmanTree(codeLengths: IntArray): HuffmanTree {
        val maxLen = codeLengths.maxOrNull() ?: 0
        val blCount = IntArray(maxLen + 1)
        
        for (len in codeLengths) {
            if (len > 0) blCount[len]++
        }
        
        val nextCode = IntArray(maxLen + 1)
        var code = 0
        for (bits in 1..maxLen) {
            code = ops.leftShift((code + blCount[bits - 1]).toLong(), 1).toInt()
            nextCode[bits] = code
        }
        
        val codes = IntArray(codeLengths.size)
        for (i in codeLengths.indices) {
            val len = codeLengths[i]
            if (len > 0) {
                codes[i] = nextCode[len]++
            }
        }
        
        return HuffmanTree(codes, codeLengths)
    }
}