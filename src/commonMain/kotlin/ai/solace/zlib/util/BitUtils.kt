package ai.solace.zlib.util

import ai.solace.zlib.bitwise.BitShiftEngine
import ai.solace.zlib.bitwise.BitShiftMode
import ai.solace.zlib.bitwise.BitwiseOps

/**
 * Utility functions for bit manipulation operations.
 *
 * This class now delegates to BitwiseOps and BitShiftEngine for the actual implementation,
 * providing both legacy compatibility and improved functionality.
 */
object BitUtils {
    // Default engines for BitUtils operations - using native for performance
    private val defaultEngineInt = BitShiftEngine(BitShiftMode.NATIVE, 32)
    private val defaultEngineLong = BitShiftEngine(BitShiftMode.NATIVE, 64)

    /**
     * Performs an unsigned right shift operation that matches the behavior of C#'s URShift.
     *
     * This is the legacy method maintained for compatibility. For new code, consider
     * using urShiftImproved() which provides better consistency.
     *
     * @param number The number to shift
     * @param bits The number of bits to shift
     * @return The result of the unsigned right shift operation
     */
    fun urShift(
        number: Int,
        bits: Int,
    ): Int {
        return BitwiseOps.urShift(number, bits)
    }

    /**
     * Performs an unsigned right shift operation that matches the behavior of C#'s URShift.
     *
     * This is the legacy method maintained for compatibility. For new code, consider
     * using urShiftImproved() which provides better consistency.
     *
     * @param number The number to shift
     * @param bits The number of bits to shift
     * @return The result of the unsigned right shift operation
     */
    fun urShift(
        number: Long,
        bits: Int,
    ): Long {
        return BitwiseOps.urShift(number, bits)
    }

    /**
     * Improved unsigned right shift operation using BitShiftEngine.
     *
     * This provides more consistent behavior across platforms and better handling
     * of edge cases compared to the legacy urShift methods.
     *
     * @param number The number to shift
     * @param bits The number of bits to shift
     * @param engine The BitShiftEngine to use (defaults to native mode for performance)
     * @return The result of the unsigned right shift operation
     */
    fun urShiftImproved(
        number: Int,
        bits: Int,
        engine: BitShiftEngine = defaultEngineInt,
    ): Int {
        return BitwiseOps.urShiftImproved(number, bits, engine)
    }

    /**
     * Improved unsigned right shift operation using BitShiftEngine.
     *
     * This provides more consistent behavior across platforms and better handling
     * of edge cases compared to the legacy urShift methods.
     *
     * @param number The number to shift
     * @param bits The number of bits to shift
     * @param engine The BitShiftEngine to use (defaults to native mode for performance)
     * @return The result of the unsigned right shift operation
     */
    fun urShiftImproved(
        number: Long,
        bits: Int,
        engine: BitShiftEngine = defaultEngineLong,
    ): Long {
        return BitwiseOps.urShiftImproved(number, bits, engine)
    }

    /**
     * Creates a BitUtils instance configured for arithmetic operations
     * @return A function that performs urShift using arithmetic operations
     */
    fun withArithmeticMode(): BitShiftEngine {
        return BitShiftEngine(BitShiftMode.ARITHMETIC, 32)
    }

    /**
     * Creates a BitUtils instance configured for native operations
     * @return A function that performs urShift using native operations
     */
    fun withNativeMode(): BitShiftEngine {
        return BitShiftEngine(BitShiftMode.NATIVE, 32)
    }
}
