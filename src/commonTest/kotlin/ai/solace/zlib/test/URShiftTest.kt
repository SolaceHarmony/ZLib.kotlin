package ai.solace.zlib.test

import kotlin.test.Test

/**
 * Test class to verify the correct implementation of the C# URShift operation in Kotlin.
 * 
 * The original C# code was:
 * if (((SupportClass.URShift((~ b), 16)) & 0xffff) != (b & 0xffff))
 * 
 * We need to find the correct Kotlin equivalent.
 */
class URShiftTest {

    /**
     * Simulates the C# URShift operation for Int values.
     * 
     * Based on the C# implementation:
     * public static int URShift(int number, int bits) {
     *     if (number >= 0)
     *         return number >> bits;
     *     else
     *         return (number >> bits) + (2 << ~bits);
     * }
     */
    private fun urShift(number: Int, bits: Int): Int {
        return if (number >= 0) {
            number ushr bits
        } else {
            (number ushr bits) + (2 shl bits.inv())
        }
    }

    @Test
    fun testURShiftImplementation() {
        // Test with various values to understand the behavior
        println("[DEBUG_LOG] Testing URShift implementation")

        // Test case 1: Valid block where storedLen and storedNLen are complements
        val validBlock = 0x12345678 // Lower 16 bits: 0x5678, Upper 16 bits: 0x1234
        val storedLen = validBlock and 0xffff // 0x5678
        val storedNLen = (validBlock ushr 16) and 0xffff // 0x1234

        println("[DEBUG_LOG] Valid block: 0x${validBlock.toString(16)}")
        println("[DEBUG_LOG] storedLen: 0x${storedLen.toString(16)}")
        println("[DEBUG_LOG] storedNLen: 0x${storedNLen.toString(16)}")

        // Original C# expression: ((SupportClass.URShift((~ b), 16)) & 0xffff)
        val originalExpr = (urShift(validBlock.inv(), 16) and 0xffff)
        println("[DEBUG_LOG] Original C# expression result: 0x${originalExpr.toString(16)}")

        // Current Kotlin implementation: ((b.inv() ushr 16) and 0xFFFF)
        val currentImpl = ((validBlock.inv() ushr 16) and 0xffff)
        println("[DEBUG_LOG] Current Kotlin implementation result: 0x${currentImpl.toString(16)}")

        // Compare results
        println("[DEBUG_LOG] Original == Current: ${originalExpr == currentImpl}")
        
        // Test with a few more values
        testValue(0x00010001) // Lower 16 bits: 0x0001, Upper 16 bits: 0x0001
        testValue(0xFFFF0000.toInt()) // Lower 16 bits: 0x0000, Upper 16 bits: 0xFFFF
        testValue(0x0000FFFF) // Lower 16 bits: 0xFFFF, Upper 16 bits: 0x0000
        testValue(0xFFFFFFFF.toInt()) // Lower 16 bits: 0xFFFF, Upper 16 bits: 0xFFFF
    }

    private fun testValue(value: Int) {
        val storedLen = value and 0xffff
        val storedNLen = (value ushr 16) and 0xffff

        println("[DEBUG_LOG] Testing value: 0x${value.toString(16)}")
        println("[DEBUG_LOG] storedLen: 0x${storedLen.toString(16)}")
        println("[DEBUG_LOG] storedNLen: 0x${storedNLen.toString(16)}")

        // Original C# expression
        val originalExpr = (urShift(value.inv(), 16) and 0xffff)
        println("[DEBUG_LOG] Original C# expression result: 0x${originalExpr.toString(16)}")

        // Current Kotlin implementation
        val currentImpl = ((value.inv() ushr 16) and 0xffff)
        println("[DEBUG_LOG] Current Kotlin implementation result: 0x${currentImpl.toString(16)}")

        // Compare results
        println("[DEBUG_LOG] Original == Current: ${originalExpr == currentImpl}")
        println("[DEBUG_LOG] storedLen == Original: ${storedLen == originalExpr}")
        println("[DEBUG_LOG] -----------------------------------")
    }
}