package ai.solace.zlib.bitwise

import ai.solace.zlib.common.ZlibLogger

/**
 * ArithmeticBitwiseOps - Configurable arithmetic-only bitwise operations for cross-platform compatibility
 * 
 * This class provides arithmetic-only implementations of bitwise operations that work consistently
 * across all Kotlin platforms, including Kotlin/Native. It's particularly useful for porting
 * 8-bit, 16-bit, or 32-bit programs where bitwise operations need to behave identically
 * regardless of the target platform.
 * 
 * The bit length parameter allows proper handling of boundary values and overflow conditions
 * that match the original target architecture.
 * 
 * @param bitLength The number of bits for operations (8, 16, or 32)
 */
class ArithmeticBitwiseOps(private val bitLength: Int) {
    
    init {
        require(bitLength in listOf(8, 16, 32)) {
            "Bit length must be 8, 16, or 32"
        }
    }
    
    // Computed boundary values based on bit length
    private val maxValue: Long = (1L shl bitLength) - 1
    private val signBit: Long = 1L shl (bitLength - 1)
    private val mask: Long = maxValue
    
    /**
     * Normalizes a value to fit within the specified bit length
     * @param value The value to normalize
     * @return The value masked to the bit length
     */
    fun normalize(value: Long): Long {
        return value and mask
    }
    
    /**
     * Performs left shift using arithmetic operations
     * @param value The value to shift
     * @param bits Number of bits to shift left
     * @return The shifted value, normalized to bit length
     */
    fun leftShift(value: Long, bits: Int): Long {
        if (bits < 0 || bits >= bitLength) {
            ZlibLogger.logBitwise("leftShift($value, $bits) -> 0 (out of range for ${bitLength}-bit)", "leftShift")
            return 0L
        }
        if (bits == 0) {
            val result = normalize(value)
            ZlibLogger.logBitwise("leftShift($value, $bits) -> $result (no shift, normalized)", "leftShift")
            return result
        }
        
        var result = normalize(value)
        val originalResult = result
        repeat(bits) { 
            result = normalize(result * 2)
        }
        ZlibLogger.logBitwise("leftShift($value, $bits) -> $result [${bitLength}-bit: $originalResult * 2^$bits = $result]", "leftShift")
        return result
    }
    
    /**
     * Performs unsigned right shift using arithmetic operations
     * @param value The value to shift
     * @param bits Number of bits to shift right
     * @return The shifted value
     */
    fun rightShift(value: Long, bits: Int): Long {
        if (bits < 0 || bits >= bitLength) return 0L
        if (bits == 0) return normalize(value)
        
        var result = normalize(value)
        repeat(bits) { 
            result /= 2
        }
        return result
    }
    
    /**
     * Creates a bit mask with the specified number of bits set
     * @param bits Number of bits to set (0 to bitLength)
     * @return A mask with the lowest 'bits' bits set to 1
     */
    fun createMask(bits: Int): Long {
        if (bits < 0) return 0L
        if (bits >= bitLength) return mask
        if (bits == 0) return 0L
        
        // Calculate 2^bits - 1 using repeated multiplication
        var result = 1L
        repeat(bits) { result *= 2 }
        return result - 1
    }
    
    /**
     * Extracts the lowest N bits from a value
     * @param value The value to extract bits from
     * @param bits Number of bits to extract
     * @return The value of the lowest 'bits' bits
     */
    fun extractBits(value: Long, bits: Int): Long {
        if (bits <= 0) return 0L
        if (bits >= bitLength) return normalize(value)
        
        val maskValue = createMask(bits)
        return normalize(value) % (maskValue + 1)
    }
    
    /**
     * Checks if a bit is set at the specified position
     * @param value The value to check
     * @param bitPosition The position of the bit to check (0-based from LSB)
     * @return true if the bit is set, false otherwise
     */
    fun isBitSet(value: Long, bitPosition: Int): Boolean {
        if (bitPosition < 0 || bitPosition >= bitLength) return false
        
        val normalizedValue = normalize(value)
        val powerOf2 = leftShift(1L, bitPosition)
        return (normalizedValue / powerOf2) % 2 == 1L
    }
    
    /**
     * Performs bitwise OR using arithmetic operations
     * @param value1 First value
     * @param value2 Second value
     * @return The result of value1 OR value2
     */
    fun or(value1: Long, value2: Long): Long {
        var result = 0L
        var powerOf2 = 1L
        var remaining1 = normalize(value1)
        var remaining2 = normalize(value2)
        
        for (i in 0 until bitLength) {
            if (remaining1 == 0L && remaining2 == 0L) break
            
            val bit1 = remaining1 % 2
            val bit2 = remaining2 % 2
            
            // OR the bits: 0|0=0, 0|1=1, 1|0=1, 1|1=1
            if (bit1 == 1L || bit2 == 1L) {
                result += powerOf2
            }
            
            remaining1 /= 2
            remaining2 /= 2
            powerOf2 *= 2
        }
        
        return result
    }
    
    /**
     * Performs bitwise AND using arithmetic operations
     * @param value1 First value
     * @param value2 Second value
     * @return The result of value1 AND value2
     */
    fun and(value1: Long, value2: Long): Long {
        var result = 0L
        var powerOf2 = 1L
        var remaining1 = normalize(value1)
        var remaining2 = normalize(value2)
        
        ZlibLogger.logBitwise("and($value1, $value2) starting bit-by-bit analysis [${bitLength}-bit]", "and")
        
        for (i in 0 until bitLength) {
            if (remaining1 == 0L && remaining2 == 0L) break
            
            val bit1 = remaining1 % 2
            val bit2 = remaining2 % 2
            
            // AND the bits: 0&0=0, 0&1=0, 1&0=0, 1&1=1
            if (bit1 == 1L && bit2 == 1L) {
                result += powerOf2
                ZlibLogger.logBitwise("and: bit position $i: 1&1=1, adding $powerOf2 to result", "and")
            }
            
            remaining1 /= 2
            remaining2 /= 2
            powerOf2 *= 2
        }
        
        ZlibLogger.logBitwise("and($value1, $value2) -> $result [binary: ${value1.toString(2)} & ${value2.toString(2)} = ${result.toString(2)}]", "and")
        return result
    }
    
    /**
     * Performs bitwise XOR using arithmetic operations
     * @param value1 First value
     * @param value2 Second value
     * @return The result of value1 XOR value2
     */
    fun xor(value1: Long, value2: Long): Long {
        var result = 0L
        var powerOf2 = 1L
        var remaining1 = normalize(value1)
        var remaining2 = normalize(value2)
        
        for (i in 0 until bitLength) {
            if (remaining1 == 0L && remaining2 == 0L) break
            
            val bit1 = remaining1 % 2
            val bit2 = remaining2 % 2
            
            // XOR the bits: 0^0=0, 0^1=1, 1^0=1, 1^1=0
            if ((bit1 == 1L) != (bit2 == 1L)) {
                result += powerOf2
            }
            
            remaining1 /= 2
            remaining2 /= 2
            powerOf2 *= 2
        }
        
        return result
    }
    
    /**
     * Performs bitwise NOT using arithmetic operations
     * @param value The value to invert
     * @return The result of NOT value (all bits flipped within bit length)
     */
    fun not(value: Long): Long {
        return mask xor normalize(value)
    }
    
    /**
     * Rotates bits to the left
     * @param value The value to rotate
     * @param positions Number of positions to rotate
     * @return The rotated value
     */
    fun rotateLeft(value: Long, positions: Int): Long {
        val normalizedValue = normalize(value)
        val normalizedPositions = positions % bitLength
        
        if (normalizedPositions == 0) return normalizedValue
        
        val leftPart = leftShift(normalizedValue, normalizedPositions)
        val rightPart = rightShift(normalizedValue, bitLength - normalizedPositions)
        
        return or(leftPart, rightPart)
    }
    
    /**
     * Rotates bits to the right
     * @param value The value to rotate
     * @param positions Number of positions to rotate
     * @return The rotated value
     */
    fun rotateRight(value: Long, positions: Int): Long {
        val normalizedValue = normalize(value)
        val normalizedPositions = positions % bitLength
        
        if (normalizedPositions == 0) return normalizedValue
        
        val rightPart = rightShift(normalizedValue, normalizedPositions)
        val leftPart = leftShift(normalizedValue, bitLength - normalizedPositions)
        
        return or(leftPart, rightPart)
    }
    
    /**
     * Converts a signed value to unsigned representation
     * @param value The signed value
     * @return The unsigned representation within bit length
     */
    fun toUnsigned(value: Long): Long {
        return normalize(value)
    }
    
    /**
     * Converts an unsigned value to signed representation
     * @param value The unsigned value
     * @return The signed representation within bit length
     */
    fun toSigned(value: Long): Long {
        val normalizedValue = normalize(value)
        return if (normalizedValue >= signBit) {
            normalizedValue - (mask + 1)
        } else {
            normalizedValue
        }
    }
    
    companion object {
        /**
         * Pre-configured instance for 8-bit operations
         */
        val BITS_8 = ArithmeticBitwiseOps(8)
        
        /**
         * Pre-configured instance for 16-bit operations
         */
        val BITS_16 = ArithmeticBitwiseOps(16)
        
        /**
         * Pre-configured instance for 32-bit operations
         */
        val BITS_32 = ArithmeticBitwiseOps(32)
    }
}