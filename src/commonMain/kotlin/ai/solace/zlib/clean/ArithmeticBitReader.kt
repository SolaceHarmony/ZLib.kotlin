package ai.solace.zlib.clean

import ai.solace.zlib.bitwise.ArithmeticBitwiseOps
import ai.solace.zlib.common.ZlibLogger

/**
 * ArithmeticBitReader - LSB-first bit reader using pure arithmetic operations.
 * This version uses ArithmeticBitwiseOps instead of native bitwise operations,
 * making it compatible with platforms that don't support native bitwise ops.
 */
class ArithmeticBitReader(private val data: ByteArray) {
    private val ops = ArithmeticBitwiseOps.BITS_32
    private val ops8 = ArithmeticBitwiseOps.BITS_8
    
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
            val b = ops8.normalize(data[index++].toLong()).toInt()
            // Use arithmetic left shift and OR
            val shifted = ops.leftShift(b.toLong(), bitCount).toInt()
            bitBuffer = ops.or(bitBuffer.toLong(), shifted.toLong()).toInt()
            bitCount += 8
        }
    }

    fun peek(n: Int): Int {
        require(n in 0..16) { "peek supports 0..16 bits" }
        fill(n)
        // Create mask using arithmetic operations
        val mask = ops.createMask(n).toInt()
        return ops.and(bitBuffer.toLong(), mask.toLong()).toInt()
    }

    fun take(n: Int): Int {
        val v = peek(n)
        // Use arithmetic right shift
        bitBuffer = ops.rightShift(bitBuffer.toLong(), n).toInt()
        bitCount -= n
        return v
    }

    fun alignToByte() {
        val drop = bitCount % 8
        if (drop != 0) {
            ZlibLogger.log("[DEBUG_LOG] ArithmeticBitReader.alignToByte dropping $drop bits")
            take(drop)
        }
    }

    fun eof(): Boolean = (bitCount == 0) && (index >= data.size)
}