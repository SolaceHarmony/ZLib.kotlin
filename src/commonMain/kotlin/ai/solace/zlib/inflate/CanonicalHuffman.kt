package ai.solace.zlib.inflate

import ai.solace.zlib.bitwise.ArithmeticBitwiseOps

/**
 * Canonical Huffman utilities (full table) for fast decoding.
 */
object CanonicalHuffman {
    /**
     * Dense decode table: size = 2^maxLen.
     * Index by the low maxLen bits from the bitstream.
     * `bits[i]` = number of bits to consume (0 means invalid entry)
     * `vals[i]` = symbol for that entry
     */
    data class FullTable(
        val maxLen: Int,
        val bits: IntArray,
        val vals: IntArray,
    ) {
        init {
            require(maxLen >= 0) { "maxLen must be non-negative: $maxLen" }
            // DEFLATE requires code lengths ≤ 15
            require(maxLen <= 15) { "maxLen must be ≤ 15 for DEFLATE, was $maxLen" }
            require(bits.size == vals.size) { "bits.size (${bits.size}) must equal vals.size (${vals.size})" }
            val expectedSizeLong = 1L shl maxLen
            require(expectedSizeLong <= Int.MAX_VALUE) { "2^$maxLen exceeds Int capacity" }
            val expectedSize = expectedSizeLong.toInt()
            require(bits.size == expectedSize) { "Array sizes (${bits.size}) must be exactly 2^maxLen ($expectedSize)" }
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is FullTable) return false
            if (maxLen != other.maxLen) return false
            if (!bits.contentEquals(other.bits)) return false
            if (!vals.contentEquals(other.vals)) return false
            return true
        }

        override fun hashCode(): Int {
            var result = maxLen
            result = 31 * result + bits.contentHashCode()
            result = 31 * result + vals.contentHashCode()
            return result
        }
    }

    /**
     * Build a full lookup table occupying 2^maxLen entries.
     * For each symbol with length L and canonical code C (MSB-first), we reverse(C,L) to get
     * the bit order as it appears on the wire (LSB-first). We then fill all indices whose low L bits
     * equal that reversed code.
     */
    fun buildFull(lengths: IntArray): FullTable {
        val ops = ArithmeticBitwiseOps.BITS_32
        val maxLen = lengths.maxOrNull() ?: 0
        if (maxLen == 0) return FullTable(0, IntArray(1), IntArray(1))

        val nextCode = computeNextCode(lengths, maxLen, ops)

        // Use arithmetic operations instead of native bitwise
        val size = ops.leftShift(1L, maxLen).toInt()
        val bitsTab = IntArray(size)
        val valsTab = IntArray(size)

        for (sym in lengths.indices) {
            val len = lengths[sym]
            if (len == 0) continue
            val assigned = nextCode[len]
            nextCode[len] = assigned + 1
            val rev = reverseBits(assigned, len)
            // Use arithmetic operations instead of native bitwise
            val stride = ops.leftShift(1L, len).toInt()
            var idx = rev
            while (idx < size) {
                bitsTab[idx] = len
                valsTab[idx] = sym
                idx += stride
            }
        }
        return FullTable(maxLen, bitsTab, valsTab)
    }

    // Shared computation for canonical Huffman next codes
    private fun computeNextCode(
        lengths: IntArray,
        maxLen: Int,
        ops: ArithmeticBitwiseOps,
    ): IntArray {
        val blCount = IntArray(maxLen + 1)
        for (l in lengths) if (l > 0) blCount[l]++
        val nextCode = IntArray(maxLen + 1)
        var code = 0
        for (bits in 1..maxLen) {
            // Use arithmetic operations instead of native bitwise
            code = ops.leftShift((code + blCount[bits - 1]).toLong(), 1).toInt()
            nextCode[bits] = code
        }
        return nextCode
    }

    /** Build encoder codes (LSB-first bit order) for given code lengths. */
    fun buildEncoder(lengths: IntArray): Pair<IntArray, IntArray> {
        val ops = ArithmeticBitwiseOps.BITS_32
        val maxLen = lengths.maxOrNull() ?: 0
        val nextCode = computeNextCode(lengths, maxLen, ops)
        val codes = IntArray(lengths.size)
        val lens = IntArray(lengths.size)
        for (sym in lengths.indices) {
            val len = lengths[sym]
            if (len == 0) continue
            val assigned = nextCode[len]
            nextCode[len] = assigned + 1
            // Reverse to LSB-first for writing
            val rev = reverseBits(assigned, len)
            codes[sym] = rev
            lens[sym] = len
        }
        return codes to lens
    }

    /** Decode one symbol using the FullTable approach with a StreamingBitReader. */
    fun decodeOne(
        br: StreamingBitReader,
        table: FullTable,
    ): Int {
        if (table.maxLen == 0) error("Empty Huffman table")
        val look = br.peek(table.maxLen)
        val len = table.bits[look]
        if (len == 0) {
            val upcoming = br.peekBytes(8)
            val hex = upcoming.joinToString("") { b -> ((b.toInt() and 0xFF).toString(16).padStart(2, '0')) }
            error("Invalid Huffman prefix (look=${look.toString(2).padStart(table.maxLen,'0')}) nextBytes=$hex")
        }
        val sym = table.vals[look]
        br.take(len)
        return sym
    }

    /** Reverse the lowest len bits of x (for LSB-first decoding). */
    private fun reverseBits(
        x: Int,
        len: Int,
    ): Int {
        val ops = ArithmeticBitwiseOps.BITS_32
        var v = x
        var r = 0
        repeat(len) {
            // Use arithmetic operations instead of native bitwise
            r = ops.or(ops.leftShift(r.toLong(), 1), ops.and(v.toLong(), 1L)).toInt()
            v = ops.rightShift(v.toLong(), 1).toInt()
        }
        return r
    }
}
