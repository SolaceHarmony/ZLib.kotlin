package ai.solace.zlib.bitwise

/**
 * BitBuffer - A class for managing bit-level operations on a buffer
 * Useful for compression algorithms that work at the bit level
 */
class BitBuffer {
    private var buffer: Int = 0
    private var bitCount: Int = 0

    /**
     * Gets the current bit buffer value
     * @return The current buffer value
     */
    fun getBuffer(): Int = buffer

    /**
     * Gets the current number of valid bits in the buffer
     * @return The bit count
     */
    fun getBitCount(): Int = bitCount

    /**
     * Adds bits from a byte to the buffer
     * @param b The byte to add
     * @return The number of bits added (always 8)
     */
    fun addByte(b: Byte): Int {
        buffer = buffer or (BitwiseOps.byteToUnsignedInt(b) shl bitCount)
        bitCount += 8
        return 8
    }

    /**
     * Peeks at the next N bits without consuming them
     * @param bits Number of bits to peek
     * @return The value of the next N bits
     */
    fun peekBits(bits: Int): Int {
        return buffer and BitwiseOps.createMask(bits)
    }

    /**
     * Consumes N bits from the buffer
     * @param bits Number of bits to consume
     * @return The value of the consumed bits
     */
    fun consumeBits(bits: Int): Int {
        require(bits <= bitCount) { "Not enough bits in buffer" }

        // Get the lowest N bits
        val result = buffer and BitwiseOps.createMask(bits)

        // Shift the buffer or clear it if consuming all bits
        if (bits >= 32 || bitCount - bits == 0) {
            buffer = 0
        } else {
            buffer = buffer ushr bits
        }

        // Update the bit count
        bitCount -= bits

        return result
    }

    /**
     * Checks if the buffer has at least N bits available
     * @param bits Number of bits to check for
     * @return true if at least N bits are available, false otherwise
     */
    fun hasEnoughBits(bits: Int): Boolean {
        return bitCount >= bits
    }

    /**
     * Resets the buffer to empty state
     */
    fun reset() {
        buffer = 0
        bitCount = 0
    }
}
