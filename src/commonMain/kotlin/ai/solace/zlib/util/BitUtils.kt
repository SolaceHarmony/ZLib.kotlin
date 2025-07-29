package ai.solace.zlib.util

/**
 * Utility functions for bit manipulation operations.
 */
object BitUtils {
    /**
     * Performs an unsigned right shift operation that matches the behavior of C#'s URShift.
     *
     * This implementation correctly handles negative numbers by adding a correction term
     * when the number is negative. This is necessary because Kotlin's native ushr operator
     * behaves differently than C#'s URShift for negative numbers.
     *
     * Based on the C# implementation:
     * ```csharp
     * public static int URShift(int number, int bits) {
     *     if (number >= 0)
     *         return number >> bits;
     *     else
     *         return (number >> bits) + (2 << ~bits);
     * }
     * ```
     *
     * @param number The number to shift
     * @param bits The number of bits to shift
     * @return The result of the unsigned right shift operation
     */
    fun urShift(number: Int, bits: Int): Int {
        return if (number >= 0) {
            number ushr bits
        } else {
            (number ushr bits) + (2 shl bits.inv())
        }
    }

    /**
     * Performs an unsigned right shift operation that matches the behavior of C#'s URShift.
     *
     * This implementation correctly handles negative numbers by adding a correction term
     * when the number is negative. This is necessary because Kotlin's native ushr operator
     * behaves differently than C#'s URShift for negative numbers.
     *
     * Based on the C# implementation:
     * ```csharp
     * public static long URShift(long number, int bits) {
     *     if (number >= 0)
     *         return number >> bits;
     *     else
     *         return (number >> bits) + (2L << ~bits);
     * }
     * ```
     *
     * @param number The number to shift
     * @param bits The number of bits to shift
     * @return The result of the unsigned right shift operation
     */
    fun urShift(number: Long, bits: Int): Long {
        return if (number >= 0) {
            number ushr bits
        } else {
            (number ushr bits) + (2L shl bits.inv())
        }
    }
}