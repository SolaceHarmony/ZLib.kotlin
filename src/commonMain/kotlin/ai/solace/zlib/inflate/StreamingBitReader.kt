package ai.solace.zlib.inflate

import okio.BufferedSource

/**
 * StreamingBitReader - LSB-first bit reader over a streaming source.
 * Maintains a small bit buffer and pulls more bytes from the underlying source on demand.
 */
class StreamingBitReader(private val source: BufferedSource) {
    private var bitBuffer: Int = 0
    private var bitCount: Int = 0

    fun alignToByte() {
        val drop = bitCount % 8
        if (drop != 0) take(drop)
    }

    fun peek(n: Int): Int {
        require(n in 0..16) { "peek supports 0..16 bits" }
        fill(n)
        val mask = if (n == 0) 0 else (1 shl n) - 1
        return bitBuffer and mask
    }

    fun take(n: Int): Int {
        val v = peek(n)
        bitBuffer = bitBuffer ushr n
        bitCount -= n
        return v
    }

    /** Read a single byte at next byte boundary. */
    fun readAlignedByte(): Int {
        alignToByte()
        fill(8)
        return take(8)
    }

    /** For error messages only. Implementation returns empty when peeking is not feasible. */
    fun peekBytes(count: Int): ByteArray {
        // In streaming mode, full non-destructive peek across byte boundary is not guaranteed.
        // Returning empty is acceptable for diagnostic-only use.
        return ByteArray(0)
    }

    private fun fill(minBits: Int) {
        while (bitCount < minBits && !source.exhausted()) {
            val b = source.readByte().toInt() and 0xFF
            bitBuffer = bitBuffer or (b shl bitCount)
            bitCount += 8
        }
    }
}
