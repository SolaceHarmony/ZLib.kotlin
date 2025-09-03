package ai.solace.zlib.bitwise

/**
 * BitShiftEngine - Unified interface for bit shift operations with configurable implementation
 * 
 * This class provides a unified way to perform bit shift operations using either:
 * 1. Native Kotlin bitwise operations (shl, shr, ushr) - fast but may have platform differences
 * 2. Arithmetic operations - slower but consistent across all platforms
 * 
 * The engine also handles carry operations properly and provides access to overflow/carry bits.
 */
enum class BitShiftMode {
    NATIVE,      // Use Kotlin's built-in shl, shr, ushr operations
    ARITHMETIC   // Use pure arithmetic operations for cross-platform consistency
}

data class ShiftResult(
    val value: Long,
    val carry: Long = 0,
    val overflow: Boolean = false
)

class BitShiftEngine(
    val mode: BitShiftMode = BitShiftMode.NATIVE,
    val bitWidth: Int = 32
) {
    
    init {
        require(bitWidth in listOf(8, 16, 32, 64)) {
            "Bit width must be 8, 16, 32, or 64"
        }
    }
    
    private val maxValue = when (bitWidth) {
        8 -> 0xFFL
        16 -> 0xFFFFL
        32 -> 0xFFFFFFFFL
        64 -> 0x7FFFFFFFFFFFFFFFL // Use max signed long to avoid overflow
        else -> throw IllegalArgumentException("Unsupported bit width")
    }
    
    private val arithmeticOps = if (bitWidth in 1..32) ArithmeticBitwiseOps(bitWidth) else null
    
    /**
     * Performs left shift with carry detection
     */
    fun leftShift(value: Long, bits: Int): ShiftResult {
        if (bits < 0 || bits >= bitWidth) {
            return ShiftResult(0L, 0L, true)
        }
        
        return when (mode) {
            BitShiftMode.NATIVE -> {
                val originalValue = normalize(value)
                val shiftedValue = when (bitWidth) {
                    8 -> (originalValue.toInt() shl bits).toLong()
                    16 -> (originalValue.toInt() shl bits).toLong()
                    32 -> (originalValue.toInt() shl bits).toLong()
                    64 -> originalValue shl bits
                    else -> throw IllegalStateException()
                }
                
                val result = normalize(shiftedValue)
                val carry = if (shiftedValue != result) (shiftedValue ushr bitWidth) else 0L
                val overflow = shiftedValue > maxValue
                
                ShiftResult(result, carry, overflow)
            }
            
            BitShiftMode.ARITHMETIC -> {
                if (arithmeticOps == null) {
                    // Fallback to native for 64-bit
                    return leftShift(value, bits)
                }
                
                val originalValue = normalize(value)
                var result = originalValue
                var carry = 0L
                var overflow = false
                
                repeat(bits) {
                    val doubled = result * 2
                    if (doubled > maxValue) {
                        carry = (carry * 2) + (doubled ushr bitWidth)
                        overflow = true
                    }
                    result = normalize(doubled)
                }
                
                ShiftResult(result, carry, overflow)
            }
        }
    }
    
    /**
     * Performs right shift (arithmetic for negative numbers)
     */
    fun rightShift(value: Long, bits: Int): ShiftResult {
        if (bits < 0 || bits >= bitWidth) {
            return ShiftResult(if (value < 0) -1L else 0L, 0L, false)
        }
        
        return when (mode) {
            BitShiftMode.NATIVE -> {
                val originalValue = normalize(value)
                val result = when (bitWidth) {
                    8 -> ((originalValue.toInt() and 0xFF) ushr bits).toLong()
                    16 -> ((originalValue.toInt() and 0xFFFF) ushr bits).toLong()
                    32 -> (originalValue.toInt() ushr bits).toLong()
                    64 -> originalValue ushr bits
                    else -> throw IllegalStateException()
                }
                
                ShiftResult(normalize(result), 0L, false)
            }
            
            BitShiftMode.ARITHMETIC -> {
                if (arithmeticOps == null) {
                    return rightShift(value, bits)
                }
                
                val result = arithmeticOps.rightShift(normalize(value), bits)
                ShiftResult(result, 0L, false)
            }
        }
    }
    
    /**
     * Performs unsigned right shift
     */
    fun unsignedRightShift(value: Long, bits: Int): ShiftResult {
        if (bits < 0 || bits >= bitWidth) {
            return ShiftResult(0L, 0L, false)
        }
        
        return when (mode) {
            BitShiftMode.NATIVE -> {
                val originalValue = normalize(value)
                val result = when (bitWidth) {
                    8 -> (originalValue.toInt() and 0xFF) ushr bits
                    16 -> (originalValue.toInt() and 0xFFFF) ushr bits  
                    32 -> (originalValue.toInt() ushr bits).toLong()
                    64 -> originalValue ushr bits
                    else -> throw IllegalStateException()
                }
                
                ShiftResult(normalize(result.toLong()), 0L, false)
            }
            
            BitShiftMode.ARITHMETIC -> {
                if (arithmeticOps == null) {
                    return unsignedRightShift(value, bits)
                }
                
                val result = arithmeticOps.rightShift(normalize(value), bits)
                ShiftResult(result, 0L, false)
            }
        }
    }
    
    /**
     * Normalize a value to fit within the bit width
     */
    private fun normalize(value: Long): Long {
        return when (bitWidth) {
            8 -> value and 0xFFL
            16 -> value and 0xFFFFL
            32 -> value and 0xFFFFFFFFL
            64 -> value
            else -> throw IllegalStateException()
        }
    }
    
    /**
     * Creates a copy of this engine with different settings
     */
    fun withMode(newMode: BitShiftMode): BitShiftEngine {
        return BitShiftEngine(newMode, bitWidth)
    }
    
    /**
     * Creates a copy of this engine with different bit width
     */
    fun withBitWidth(newBitWidth: Int): BitShiftEngine {
        return BitShiftEngine(mode, newBitWidth)
    }
}
