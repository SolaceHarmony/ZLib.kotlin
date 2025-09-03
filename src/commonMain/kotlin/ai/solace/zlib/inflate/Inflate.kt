package ai.solace.zlib.inflate

import ai.solace.zlib.bitwise.ArithmeticBitwiseOps
import ai.solace.zlib.common.PRESET_DICT
import ai.solace.zlib.common.TREE_BASE_DIST
import ai.solace.zlib.common.TREE_BASE_LENGTH
import ai.solace.zlib.common.TREE_BL_ORDER
import ai.solace.zlib.common.TREE_EXTRA_DBITS
import ai.solace.zlib.common.TREE_EXTRA_LBITS
import ai.solace.zlib.common.Z_DATA_ERROR
import ai.solace.zlib.common.Z_DEFLATED
import ai.solace.zlib.common.Z_OK

/**
 * Minimal, readable DEFLATE decoder for provenance and debugging.
 * - Supports stored blocks and fixed Huffman blocks.
 * - Dynamic blocks return Z_DATA_ERROR (not implemented here) – useful for isolating issues.
 * - Zlib wrapper parsing (CMF/FLG) included.
 */
object Inflate {
    private val ops = ArithmeticBitwiseOps.BITS_32

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

    private fun readZlibHeader(br: BitReader): Boolean {
        // Align to byte and then read two bytes CMF/FLG
        br.alignToByte()
        if (br.bytesConsumed() + 2 > Int.MAX_VALUE) return false
        val b0 = readByte(br)
        val b1 = readByte(br)
        val cmf = b0
        val flg = b1
        // Use arithmetic operations instead of native bitwise
        val cm = ops.and(cmf.toLong(), 0x0FL).toInt()
        val cinfo = ops.and(ops.rightShift(cmf.toLong(), 4), 0x0FL).toInt()
        if (cm != Z_DEFLATED || cinfo > 7) return false
        // Use arithmetic operations instead of native bitwise
        if ((ops.or(ops.leftShift(cmf.toLong(), 8), flg.toLong()).toInt() % 31) != 0) return false
        val presetDict = ops.and(flg.toLong(), PRESET_DICT.toLong()) != 0L
        if (presetDict) {
            // skip 4-byte DICTID
            repeat(4) { readByte(br) }
        }
        return true
    }

    private fun readByte(br: BitReader): Int {
        br.alignToByte()
        return br.take(8)
    }

    private fun copyStored(
        br: BitReader,
        out: MutableList<Byte>,
    ): Int {
        // Move to next byte, then read LEN and NLEN
        br.alignToByte()
        val len = readByte(br) or (readByte(br) shl 8)
        val nlen = readByte(br) or (readByte(br) shl 8)
        if (len.inv() and 0xFFFF != nlen) return Z_DATA_ERROR
        repeat(len) {
            out.add(readByte(br).toByte())
        }
        return Z_OK
    }

    // Tables from zlib’s canonical arrays
    private val LENGTH_BASE = TREE_BASE_LENGTH
    private val LENGTH_EXTRA = TREE_EXTRA_LBITS
    private val DIST_BASE = TREE_BASE_DIST
    private val DIST_EXTRA = TREE_EXTRA_DBITS

    // TODO(detekt: CyclomaticComplexMethod): split into helpers (length decode, distance decode, copy)
    // TODO(detekt: TooGenericExceptionCaught/SwallowedException): narrow exception types and surface context
    private fun decodeFixed(
        br: BitReader,
        out: MutableList<Byte>,
    ): Int {
        val litTable = buildFixedLiteralTable()
        val distTable = buildFixedDistTable()

        loop@ while (true) {
            val sym =
                try {
                    CanonicalHuffman.decodeOne(br, litTable)
                } catch (e: Throwable) { // TODO: catch specific decoding exception and attach context
                    return Z_DATA_ERROR
                }
            when {
                sym < 256 -> out.add(sym.toByte())
                sym == 256 -> break@loop // end of block
                else -> {
                    val lenCode = sym - 257
                    if (lenCode !in 0..28) return Z_DATA_ERROR
                    val baseLen = LENGTH_BASE[lenCode]
                    val extra = LENGTH_EXTRA[lenCode]
                    val extraVal = if (extra > 0) br.take(extra) else 0
                    val length = baseLen + extraVal

                    val distSym =
                        try {
                            CanonicalHuffman.decodeOne(br, distTable)
                        } catch (e: Throwable) { // TODO: catch specific decoding exception and attach context
                            return Z_DATA_ERROR
                        }
                    if (distSym !in 0..29) return Z_DATA_ERROR
                    val baseDist = DIST_BASE[distSym]
                    val extraD = DIST_EXTRA[distSym]
                    val extraDVal = if (extraD > 0) br.take(extraD) else 0
                    val dist = baseDist + extraDVal

                    if (dist <= 0 || dist > out.size) return Z_DATA_ERROR
                    // copy with overlap allowed
                    var i = 0
                    while (i < length) {
                        val b = out[out.size - dist]
                        out.add(b)
                        i++
                    }
                }
            }
        }
        return Z_OK
    }

    // TODO(detekt: LongMethod/CyclomaticComplexMethod): refactor into phases (read code lengths, build tables, decode loop)
    private fun decodeDynamic(
        br: BitReader,
        out: MutableList<Byte>,
    ): Int {
        // Read HLIT, HDIST, HCLEN
        val hlit = br.take(5) + 257
        val hdist = br.take(5) + 1
        val hclen = br.take(4) + 4

        // Read code length code lengths in specified order
        val order = TREE_BL_ORDER
        val clen = IntArray(19)
        for (i in 0 until hclen) {
            clen[order[i]] = br.take(3)
        }
        // Others remain 0
        val clTableFull = CanonicalHuffman.buildFull(clen)

        // Read literal/length + distance code lengths using RLE symbols
        val litLenLens = IntArray(hlit)
        val distLens = IntArray(hdist)
        var i = 0
        while (i < hlit) {
            val sym = CanonicalHuffman.decodeOne(br, clTableFull)
            when (sym) {
                in 0..15 -> {
                    litLenLens[i++] = sym
                }
                16 -> {
                    if (i == 0) return Z_DATA_ERROR
                    val repeat = 3 + br.take(2)
                    val prev = litLenLens[i - 1]
                    repeat(repeat) { if (i < hlit) litLenLens[i++] = prev else return Z_DATA_ERROR }
                }
                17 -> {
                    val repeat = 3 + br.take(3)
                    repeat(repeat) { if (i < hlit) litLenLens[i++] = 0 else return Z_DATA_ERROR }
                }
                18 -> {
                    val repeat = 11 + br.take(7)
                    repeat(repeat) { if (i < hlit) litLenLens[i++] = 0 else return Z_DATA_ERROR }
                }
                else -> return Z_DATA_ERROR
            }
        }
        i = 0
        while (i < hdist) {
            val sym = CanonicalHuffman.decodeOne(br, clTableFull)
            when (sym) {
                in 0..15 -> {
                    distLens[i++] = sym
                }
                16 -> {
                    if (i == 0) return Z_DATA_ERROR
                    val repeat = 3 + br.take(2)
                    val prev = distLens[i - 1]
                    repeat(repeat) { if (i < hdist) distLens[i++] = prev else return Z_DATA_ERROR }
                }
                17 -> {
                    val repeat = 3 + br.take(3)
                    repeat(repeat) { if (i < hdist) distLens[i++] = 0 else return Z_DATA_ERROR }
                }
                18 -> {
                    val repeat = 11 + br.take(7)
                    repeat(repeat) { if (i < hdist) distLens[i++] = 0 else return Z_DATA_ERROR }
                }
                else -> return Z_DATA_ERROR
            }
        }

        val litTable = CanonicalHuffman.buildFull(litLenLens)
        val distTable = CanonicalHuffman.buildFull(distLens)

        // Decode stream symbols
        loop@ while (true) {
            val sym = CanonicalHuffman.decodeOne(br, litTable)
            when {
                sym < 256 -> out.add(sym.toByte())
                sym == 256 -> break@loop
                else -> {
                    val lenCode = sym - 257
                    if (lenCode !in 0..28) return Z_DATA_ERROR
                    val baseLen = LENGTH_BASE[lenCode]
                    val extra = LENGTH_EXTRA[lenCode]
                    val extraVal = if (extra > 0) br.take(extra) else 0
                    val length = baseLen + extraVal

                    val distSym = CanonicalHuffman.decodeOne(br, distTable)
                    if (distSym !in 0..29) return Z_DATA_ERROR
                    val baseDist = DIST_BASE[distSym]
                    val extraD = DIST_EXTRA[distSym]
                    val extraDVal = if (extraD > 0) br.take(extraD) else 0
                    val dist = baseDist + extraDVal

                    if (dist <= 0 || dist > out.size) return Z_DATA_ERROR
                    var j = 0
                    while (j < length) {
                        val b = out[out.size - dist]
                        out.add(b)
                        j++
                    }
                }
            }
        }
        return Z_OK
    }

    /**
     * Inflate a zlib-wrapped stream supporting stored and fixed blocks.
     * Dynamic blocks return Z_DATA_ERROR to indicate unsupported in this path.
     */
    fun inflateZlib(input: ByteArray): Pair<Int, ByteArray> {
        val br = BitReader(input)
        if (!readZlibHeader(br)) {
            return Z_DATA_ERROR to byteArrayOf()
        }

        val out = mutableListOf<Byte>()
        var last: Int
        do {
            last = br.take(1)
            val btype = br.take(2)
            when (btype) {
                0 -> { // stored
                    val r = copyStored(br, out)
                    if (r != Z_OK) return r to byteArrayOf()
                }
                1 -> { // fixed
                    val r = decodeFixed(br, out)
                    if (r != Z_OK) return r to byteArrayOf()
                }
                2 -> {
                    val r = decodeDynamic(br, out)
                    if (r != Z_OK) return r to byteArrayOf()
                }
                else -> return Z_DATA_ERROR to byteArrayOf()
            }
        } while (last == 0)

        // Optionally verify Adler32 (zlib trailer)
        // Our reader may be mid-byte; align and try reading trailer if present
        br.alignToByte()
        // Not strictly required here; primary goal is structural decoding

        return Z_OK to out.toByteArray()
    }
}
