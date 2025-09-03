package ai.solace.zlib.clean

import ai.solace.zlib.common.ZlibLogger

/**
 * Minimal LSB-first bit reader over a ByteArray.
 * Maintains a 32-bit buffer to amortize loads and logs progress via ZlibLogger.
 */
class BitReader(private val data: ByteArray) {
    private var bitBuffer: Int = 0
    private var bitCount: Int = 0
    private var index: Int = 0

    fun bitsInBuffer(): Int = bitCount
    fun bytesConsumed(): Int = index
    fun totalSize(): Int = data.size

    /**
     * Peek upcoming raw bytes without consuming from the underlying array starting at current byte boundary.
     * Does not modify bitBuffer/bitCount (align first if needed in the caller if you want byte-aligned view).
     */
    fun peekBytes(count: Int): ByteArray {
        val start = index
        val end = kotlin.math.min(data.size, start + count)
        return data.copyOfRange(start, end)
    }

    private fun fill(minBits: Int) {
        while (bitCount < minBits && index < data.size) {
            val b = data[index++].toInt() and 0xFF
            bitBuffer = bitBuffer or (b shl bitCount)
            bitCount += 8
        }
    }

    fun peek(n: Int): Int {
        require(n in 0..16) { "peek supports 0..16 bits" }
        fill(n)
        return bitBuffer and ((1 shl n) - 1)
    }

    fun take(n: Int): Int {
        val v = peek(n)
        bitBuffer = bitBuffer ushr n
        bitCount -= n
        return v
    }

    fun alignToByte() {
        val drop = bitCount % 8
        if (drop != 0) {
            ZlibLogger.log("[DEBUG_LOG] BitReader.alignToByte dropping $drop bits")
            take(drop)
        }
    }

    fun eof(): Boolean = (bitCount == 0) && (index >= data.size)
}
