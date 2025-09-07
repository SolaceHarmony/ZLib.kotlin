package ai.solace.zlib.inflate

import ai.solace.zlib.bitwise.ArithmeticBitwiseOps
import okio.BufferedSink

/**
 * StreamingBitWriter - LSB-first bit writer to a streaming sink.
 * Uses arithmetic-only bit shifts via ArithmeticBitwiseOps.
 */
class StreamingBitWriter(private val sink: BufferedSink) {
    private val ops = ArithmeticBitwiseOps.BITS_32
    private var bitBuffer: Int = 0
    private var bitCount: Int = 0

    /** Write 'count' low bits of value (LSB-first). */
    fun writeBits(
        value: Int,
        count: Int,
    ) {
        if (count == 0) return
        // Append bits LSB-first: shift in lower 'count' bits of value at current bitCount
        val masked = value and ((1 shl count) - 1)
        val shifted = ops.leftShift(masked.toLong(), bitCount).toInt()
        bitBuffer = ops.or(bitBuffer.toLong(), shifted.toLong()).toInt()
        bitCount += count
        // Emit bytes while we have at least 8 bits
        while (bitCount >= 8) {
            val b = bitBuffer and 0xFF
            sink.writeByte(b)
            bitBuffer = ops.rightShift(bitBuffer.toLong(), 8).toInt()
            bitCount -= 8
        }
    }

    fun alignToByte() {
        val rem = bitCount % 8
        if (rem != 0) writeBits(0, 8 - rem)
    }

    fun flush() {
        alignToByte()
        sink.flush()
    }

    /** Current pending bit count modulo 8 (for estimating padding costs). */
    fun bitMod8(): Int = bitCount % 8
}
