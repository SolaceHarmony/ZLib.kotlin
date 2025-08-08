package ai.solace.zlib.deflate

import ai.solace.zlib.common.*
import ai.solace.zlib.bitwise.ArithmeticBitwiseOps
/**
 * Handles the processing of compressed data blocks during decompression.
 *
 * This class is responsible for managing the state machine that processes different
 * types of compressed blocks (stored, fixed, and dynamic), maintains the sliding window
 * for output, and handles checksums.
 *
 * @param z The ZStream containing the compression state
 * @param checkfn The checksum function (typically Adler32) or null if no checksum
 * @param w The window size (typically 1 << MAX_WBITS)
 */
class InfBlocks(z: ZStream, internal val checkfn: Any?, w: Int) {
    private val bitwiseOps = ArithmeticBitwiseOps.BITS_32
    /**
     * Static constants and utility methods for InfBlocks.
     */
    companion object {
        /**
         * Mask array used for bit manipulation in inflate operations.
         * Imported from Constants.kt to avoid duplication.
         */
        private val IBLK_INFLATE_MASK = ai.solace.zlib.common.IBLK_INFLATE_MASK

        /**
         * Border array used for dynamic Huffman tree construction.
         * Imported from Constants.kt to avoid duplication.
         */
        private val IBLK_BORDER = ai.solace.zlib.common.IBLK_BORDER
    }

    /** Current inflate block mode (IBLK_TYPE, IBLK_LENS, etc.) */
    private var mode = IBLK_TYPE

    /** Bytes left to copy for stored block */
    private var left = 0

    /** Table for dynamic blocks */
    private var table = 0

    /** Index into tables */
    private var index = 0

    /** Bit lengths for dynamic block */
    private var blens: IntArray? = null

    /** Bit length tree depth */
    private val bb = IntArray(1)

    /** Bit length decoding tree */
    private val tb = arrayOf(IntArray(1))

    /** InfCodes object for current block */
    private var codes: InfCodes? = null

    /** True if this block is the last block */
    private var last = 0

    /** Bits in bit buffer */
    internal var bitk = 0

    /** Bit buffer */
    internal var bitb = 0

    /** Single malloc for tree space */
    private val hufts: IntArray = IntArray(IBLK_MANY * 3)

    /** Sliding window for output */
    internal val window: ByteArray = ByteArray(w)

    /** One byte after a sliding window */
    internal val end: Int = w

    /** Window read pointer */
    internal var read = 0

    /** Window write pointer */
    internal var write = 0

    /** Check value on output */
    internal var check: Long = 0

    init {
        this.mode = IBLK_TYPE
        reset(z, null)
    }

    /**
     * Resets the inflate blocks state.
     *
     * This method resets the state machine, bit buffer, and window pointers to prepare
     * for processing a new set of blocks or to recover from an error.
     *
     * @param z The ZStream containing the decompression state
     * @param c Array to store the current check value, or null if no storage is needed
     */
    fun reset(z: ZStream?, c: LongArray?) {
        // Save check value if requested
        c?.let { 
            it[0] = check
        }

        // Clean up resources based on current mode
        when (mode) {
            IBLK_BTREE, IBLK_DTREE -> blens = null
            IBLK_CODES -> codes?.free()
            else -> { /* No cleanup needed for other modes */
            }
        }

        // Reset the state machine
        mode = IBLK_TYPE
        bitk = 0
        bitb = 0
        read = 0
        write = 0

        // Clear the window buffer to prevent leftover data from appearing in output
        window.fill(0)
        ZlibLogger.debug("[RESET_DEBUG] InfBlocks reset: read=$read, write=$write, window size=${window.size}")

        // Reset checksum if we have a checksum function
        if (checkfn != null && z != null) {
            z.adler = Adler32().adler32(0L, null, 0, 0)
            check = z.adler
        }
    }

    /**
     * Processes a block of compressed data.
     *
     * This is the main method that implements the state machine for decompression.
     * It handles different block types (stored, fixed, and dynamic), manages the bit buffer,
     * and coordinates with InfCodes for actual decompression. The method processes data
     * incrementally and may need to be called multiple times to complete decompression.
     *
     * @param z The ZStream containing input data and decompression state
     * @param rIn The initial result status code (typically Z_OK)
     * @return Updated result status code:
     *         - Z_OK if more input/output is needed
     *         - Z_STREAM_END if decompression is complete
     *         - Z_DATA_ERROR if input data is corrupted
     *         - Z_BUF_ERROR if buffer space is needed
     *         - Z_STREAM_ERROR if the stream state is inconsistent
     */
    fun proc(z: ZStream?, rIn: Int): Int {
        ZlibLogger.logInflate("InfBlocks.proc: mode=$mode, write=$write, read=$read, rIn=$rIn")
        if (z == null) {
            return Z_STREAM_ERROR
        }

        // Initialize local variables from the zstream and blocks state
        var inputPointer = z.nextInIndex
        var bytesAvailable = z.availIn
        var bitBuffer = bitb
        var bitsInBuffer = bitk
        var outputPointer = write
        var outputBytesLeft = if (outputPointer < read) read - outputPointer - 1 else end - outputPointer
        var returnCode = rIn

        ZlibLogger.logInflate("Initial state: inputPointer=$inputPointer, bytesAvailable=$bytesAvailable, bitBuffer=$bitBuffer, bitsInBuffer=$bitsInBuffer")

        // Process input and output based on current state
        while (true) {
            ZlibLogger.logInflate("Processing block mode=$mode, bitBuffer=0x${bitBuffer.toString(16)}, bitsInBuffer=$bitsInBuffer")
            when (mode) {
                IBLK_TYPE -> {
                    while (bitsInBuffer < 3) {
                        if (bytesAvailable == 0) {
                            ZlibLogger.logInflate("Not enough input bytes for TYPE")
                            return Z_BUF_ERROR
                        }
                        bytesAvailable--
                        val nextByte = z.nextIn!![inputPointer++].toLong() and 0xFFL
                        val shifted = bitwiseOps.leftShift(nextByte, bitsInBuffer)
                        bitBuffer = bitwiseOps.or(bitBuffer.toLong(), shifted).toInt()
                        bitsInBuffer += 8
                        ZlibLogger.logBitwise("Reading byte: $nextByte, shifted by $bitsInBuffer: $shifted, new bitBuffer=0x${bitBuffer.toString(16)}")
                    }

                    val t = bitwiseOps.extractBits(bitBuffer.toLong(), 3).toInt()
                    last = bitwiseOps.extractBits(t.toLong(), 1).toInt()

                    ZlibLogger.logInflate("Block type bits: $t, is_last=$last")
                    ZlibLogger.logBitwise("extractBits($bitBuffer, 3) -> $t")
                    ZlibLogger.logBitwise("extractBits($t, 1) -> $last")

                    bitBuffer = bitwiseOps.rightShift(bitBuffer.toLong(), 3).toInt(); bitsInBuffer -= 3

                    val blockType = bitwiseOps.rightShift(t.toLong(), 1).toInt()
                    ZlibLogger.logInflate("Block type: $blockType (0=stored, 1=fixed, 2=dynamic)")
                    
                    when (blockType) {
                        0 -> { // Stored block
                            ZlibLogger.logInflate("Processing stored block")
                            val bitsToSkip = bitsInBuffer and 7
                            bitBuffer = bitwiseOps.rightShift(bitBuffer.toLong(), bitsToSkip).toInt(); bitsInBuffer -= bitsToSkip
                            mode = IBLK_LENS
                        }

                        1 -> { // Fixed Huffman block
                            ZlibLogger.logInflate("Processing fixed Huffman block")
                            val bl = IntArray(1)
                            val bd = IntArray(1)
                            val tl = arrayOf(IntArray(0))
                            val td = arrayOf(IntArray(0))
                            ZlibLogger.logHuffman("Calling InfTree.inflateTreesFixed")
                            val fixedTreeResult = InfTree.inflateTreesFixed(bl, bd, tl, td, z)
                            ZlibLogger.logHuffman("InfTree.inflateTreesFixed returned: $fixedTreeResult, literalBits=${bl[0]}, distanceBits=${bd[0]}")
                            codes = InfCodes(bl[0], bd[0], tl[0], td[0])
                            mode = IBLK_CODES
                        }

                        2 -> { // Dynamic Huffman block
                            mode = IBLK_TABLE
                        }

                        else -> { // Invalid block type
                            mode = IBLK_BAD
                            z.msg = "invalid block type"
                            return Z_DATA_ERROR
                        }
                    }
                }

                IBLK_LENS -> {
                    while (bitsInBuffer < 32) {
                        if (bytesAvailable == 0) {
                            return Z_BUF_ERROR
                        }
                        bytesAvailable--
                        bitBuffer = bitwiseOps.or(bitBuffer.toLong(), bitwiseOps.leftShift(bitwiseOps.and(z.nextIn!![inputPointer++].toLong(), 0xFFL), bitsInBuffer)).toInt()
                        bitsInBuffer += 8
                    }

                    val storedLen = bitwiseOps.extractBits(bitBuffer.toLong(), 16).toInt()
                    val storedNLen = bitwiseOps.extractBits(bitwiseOps.rightShift(bitBuffer.toLong(), 16), 16).toInt()

                    if (storedLen != bitwiseOps.extractBits(storedNLen.inv().toLong(), 16).toInt()) {
                        mode = IBLK_BAD
                        z.msg = "invalid stored block lengths"
                        return Z_DATA_ERROR
                    }

                    left = storedLen
                    bitBuffer = 0
                    bitsInBuffer = 0
                    mode = if (left != 0) IBLK_STORED else if (last != 0) IBLK_DRY else IBLK_TYPE
                }

                IBLK_STORED -> {
                    if (bytesAvailable == 0) {
                        return Z_BUF_ERROR
                    }
                    if (outputBytesLeft == 0) {
                        if (outputPointer == end && read != 0) {
                            outputPointer = 0; outputBytesLeft =
                                if (outputPointer < read) read - outputPointer - 1 else end - outputPointer
                        }
                        if (outputBytesLeft == 0) {
                            write = outputPointer
                            returnCode = z.inflateFlush(returnCode)
                            outputPointer = write
                            outputBytesLeft = if (outputPointer < read) read - outputPointer - 1 else end - outputPointer
                            if (outputPointer == end && read != 0) {
                                outputPointer = 0; outputBytesLeft =
                                    if (outputPointer < read) read - outputPointer - 1 else end - outputPointer
                            }
                            if (outputBytesLeft == 0) {
                                return Z_BUF_ERROR
                            }
                        }
                    }

                    var t = left
                    if (t > bytesAvailable) t = bytesAvailable
                    if (t > outputBytesLeft) t = outputBytesLeft

                    if (t > 0) {
                        z.nextIn!!.copyInto(window, outputPointer, inputPointer, inputPointer + t)
                    }

                    inputPointer += t
                    bytesAvailable -= t
                    outputPointer += t
                    outputBytesLeft -= t
                    left -= t

                    if (left == 0) {
                        mode = if (last != 0) IBLK_DRY else IBLK_TYPE
                    }
                }

                IBLK_TABLE -> {
                    while (bitsInBuffer < 14) {
                        if (bytesAvailable == 0) {
                            return Z_BUF_ERROR
                        }
                        bytesAvailable--
                        bitBuffer = bitwiseOps.or(bitBuffer.toLong(), bitwiseOps.leftShift(bitwiseOps.and(z.nextIn!![inputPointer++].toLong(), 0xFFL), bitsInBuffer)).toInt()
                        bitsInBuffer += 8
                    }
                    table = bitwiseOps.extractBits(bitBuffer.toLong(), 14).toInt()
                    val t = table
                    if (bitwiseOps.extractBits(t.toLong(), 5) > 29 || bitwiseOps.extractBits(bitwiseOps.rightShift(t.toLong(), 5), 5) > 29) {
                        mode = IBLK_BAD
                        z.msg = "too many length or distance symbols"
                        return Z_DATA_ERROR
                    }
                    val totalSymbols = 258 + bitwiseOps.and(t.toLong(), 0x1fL).toInt() + bitwiseOps.and(bitwiseOps.rightShift(t.toLong(), 5), 0x1fL).toInt()
                    blens = IntArray(totalSymbols)
                    bitBuffer = bitwiseOps.rightShift(bitBuffer.toLong(), 14).toInt(); bitsInBuffer -= 14
                    index = 0
                    mode = IBLK_BTREE
                }

                IBLK_BTREE -> {
                    while (index < 4 + bitwiseOps.rightShift(table.toLong(), 10).toInt()) {
                        while (bitsInBuffer < 3) {
                            if (bytesAvailable == 0) {
                                return Z_BUF_ERROR
                            }
                            bytesAvailable--
                            bitBuffer = bitwiseOps.or(bitBuffer.toLong(), bitwiseOps.leftShift(bitwiseOps.and(z.nextIn!![inputPointer++].toLong(), 0xFFL), bitsInBuffer)).toInt()
                            bitsInBuffer += 8
                        }
                        blens!![IBLK_BORDER[index++]] = bitwiseOps.and(bitBuffer.toLong(), 7L).toInt()
                        bitBuffer = bitwiseOps.rightShift(bitBuffer.toLong(), 3).toInt(); bitsInBuffer -= 3
                    }
                    while (index < 19) {
                        blens!![IBLK_BORDER[index++]] = 0
                    }
                    bb[0] = 7
                    val treeResult = InfTree.inflateTreesBits(blens!!, bb, tb, hufts, z)
                    if (treeResult != Z_OK) {
                        if (treeResult == Z_DATA_ERROR) {
                            blens = null
                            mode = IBLK_BAD
                        }
                        return treeResult
                    }
                    index = 0
                    mode = IBLK_DTREE
                }

                IBLK_DTREE -> {
                    val numLengths = 257 + bitwiseOps.and(table.toLong(), 0x1fL).toInt()
                    val numDistances = 1 + bitwiseOps.and(bitwiseOps.rightShift(table.toLong(), 5), 0x1fL).toInt()
                    val totalCodes = numLengths + numDistances
                    
                    if (totalCodes > 288) {
                        blens = null
                        mode = IBLK_BAD
                        z.msg = "too many length or distance symbols"
                        return Z_DATA_ERROR
                    }
                    
                    while (index < totalCodes) {
                        val needBits = bb[0]
                        while (bitsInBuffer < needBits) {
                            if (z.availIn == 0) {
                                bitb = bitBuffer
                                bitk = bitsInBuffer
                                z.availIn = bytesAvailable
                                return Z_BUF_ERROR
                            }
                            bytesAvailable--
                            bitBuffer = bitwiseOps.or(bitBuffer.toLong(), bitwiseOps.leftShift(bitwiseOps.and(z.nextIn!![inputPointer++].toLong(), 0xFFL), bitsInBuffer)).toInt()
                            bitsInBuffer += 8
                        }
                        
                        // Use the table array directly with base index 0
                        val tableArray = tb[0]
                        val tblIdx = bitwiseOps.and(bitBuffer.toLong(), IBLK_INFLATE_MASK[needBits].toLong()).toInt() * 3
                        val treeBits = tableArray[tblIdx + 1]
                        val code = tableArray[tblIdx + 2]
                        
                        bitBuffer = bitwiseOps.rightShift(bitBuffer.toLong(), treeBits).toInt()
                        bitsInBuffer -= treeBits
                        
                        if (code < 16) {
                            blens!![index++] = code
                        } else {
                            val (extraBits, baseLength) = when (code) {
                                16 -> 2 to 3
                                17 -> 3 to 3
                                else -> 7 to 11
                            }
                            
                            while (bitsInBuffer < extraBits) {
                                if (bytesAvailable == 0) {
                                    bitb = bitBuffer
                                    bitk = bitsInBuffer
                                    z.availIn = bytesAvailable
                                    return Z_BUF_ERROR
                                }
                                bytesAvailable--
                                bitBuffer = bitwiseOps.or(
                                    bitBuffer.toLong(),
                                    bitwiseOps.leftShift(
                                        bitwiseOps.and(z.nextIn!![inputPointer++].toLong(), 0xFFL),
                                        bitsInBuffer
                                    )
                                ).toInt()
                                bitsInBuffer += 8
                            }
                            
                            val repeatCount = baseLength +
                                bitwiseOps.and(
                                    bitBuffer.toLong(),
                                    IBLK_INFLATE_MASK[extraBits].toLong()
                                ).toInt()
                            bitBuffer = bitwiseOps.rightShift(bitBuffer.toLong(), extraBits).toInt()
                            bitsInBuffer -= extraBits
                            
                            if (index + repeatCount > totalCodes || (code == 16 && index == 0)) {
                                blens = null
                                mode = IBLK_BAD
                                z.msg = "invalid bit length repeat"
                                return Z_DATA_ERROR
                            }
                            
                            val value = if (code == 16) blens!![index - 1] else 0
                            repeat(repeatCount) {
                                blens!![index++] = value
                            }
                        }
                    }
                    
                    val bl = IntArray(1).also { it[0] = 9 }
                    val bd = IntArray(1).also { it[0] = 6 }
                    val tl = arrayOf(IntArray(0))
                    val td = arrayOf(IntArray(1))
                    
                    when (val buildResult = InfTree.inflateTreesDynamic(
                        numLengths,
                        numDistances,
                        blens!!,
                        bl, bd,
                        tl, td,
                        hufts,
                        z
                    )) {
                        Z_OK -> {
                            codes = InfCodes(bl[0], bd[0], tl[0], td[0])
                            blens = null
                            mode = IBLK_CODES
                        }
                        Z_DATA_ERROR -> {
                            blens = null
                            mode = IBLK_BAD
                            return Z_DATA_ERROR
                        }
                        else -> {
                            return buildResult
                        }
                    }
                }

                IBLK_CODES -> {
                    if (codes == null) {
                        return Z_STREAM_ERROR
                    }
                    
                    bitb = bitBuffer
                    bitk = bitsInBuffer
                    z.availIn = bytesAvailable
                    z.nextInIndex = inputPointer
                    this.write = outputPointer

                    val codesResult = codes!!.proc(this, z, returnCode)

                    bitBuffer = bitb
                    bitsInBuffer = bitk
                    bytesAvailable = z.availIn
                    inputPointer = z.nextInIndex
                    outputPointer = this.write
                    outputBytesLeft = if (outputPointer < read) read - outputPointer - 1 else end - outputPointer

                    if (codesResult == Z_STREAM_END) {
                        returnCode = Z_OK
                        codes = null
                        mode = if (last != 0) IBLK_DRY else IBLK_TYPE
                    } else {
                        return codesResult
                    }
                }

                IBLK_DRY -> {
                    write = outputPointer  // Save current position; inflateFlush may modify write
                    val result = z.inflateFlush(returnCode)
                    // Restore position (write may have been modified by inflateFlush)
                    if (read != write) {
                        return z.inflateFlush(result)
                    }
                    mode = IBLK_DONE
                    return Z_STREAM_END
                }

                IBLK_BAD -> {
                    return Z_DATA_ERROR
                }

                else -> {
                    return Z_STREAM_ERROR
                }
            }
        }
    }

    /**
     * Releases allocated resources used by this InfBlocks instance.
     *
     * While Kotlin handles memory management through garbage collection, this method
     * explicitly clears references and sensitive data to ensure proper cleanup and
     * prevent potential memory leaks or security issues.
     *
     * @param z The ZStream containing the decompression state
     */
    internal fun free(z: ZStream?) {
        reset(z, null)
        window.fill(0)  // Clear window data for security/memory reasons
        codes = null    // Release references to any InfCodes objects
    }

    /**
     * Sets a dictionary for decompression.
     *
     * This method initializes the sliding window with a preset dictionary,
     * which can improve compression ratios for certain types of data. It copies
     * the dictionary content into the window and sets up pointers appropriately.
     *
     * @param dictionary The byte array containing the preset dictionary data
     * @param index The starting index in the dictionary array
     * @param length The number of bytes to use from the dictionary
     */
    internal fun setDictionary(dictionary: ByteArray, index: Int, length: Int) {
        // Copy dictionary content to window with bounds checking
        val actualLength = minOf(length, window.size)
        dictionary.copyInto(
            destination = window,
            destinationOffset = 0,
            startIndex = index,
            endIndex = index + actualLength
        )
        read = 0
        write = actualLength
    }

    /**
     * Flushes data from the sliding window to the caller’s output buffer.
     * Mirrors the behaviour of zlib’s inflate_flush().
     */
    internal fun inflateFlush(z: ZStream?, r: Int): Int {
        val result = r
        if (z == null) {
            return Z_STREAM_ERROR
        }

        var avail = z.availOut
        var nextOut = z.nextOutIndex
        var winRead = read

        // Determine how many bytes can be copied in one go.
        val count = if (winRead <= write) write - winRead else end - winRead
        if (avail > count) avail = count
        if (avail == 0) {
            return result     // Nothing to copy
        }

        // First copy (may wrap later)
        window.copyInto(
            destination = z.nextOut!!,
            destinationOffset = nextOut,
            startIndex = winRead,
            endIndex = winRead + avail
        )

        // Update checksum using the exact window segment just flushed
        z.adler = Adler32().adler32(z.adler, window, winRead, avail)
        this.check = z.adler

        nextOut += avail
        winRead += avail
        z.totalOut += avail.toLong()
        z.availOut -= avail

        // Handle window wrap-around.
        if (winRead == end) {
            winRead = 0
            if (write == end) write = 0
        }

        read = winRead
        z.nextOutIndex = nextOut

        // If window emptied completely, keep pointers in sync.
        if (read == write) {
            read = 0
            write = 0
        }

        return result
    }
}