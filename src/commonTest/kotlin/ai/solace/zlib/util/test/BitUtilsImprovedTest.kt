package ai.solace.zlib.util.test

import ai.solace.zlib.bitwise.BitShiftMode
import ai.solace.zlib.util.BitUtils
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Test for updated BitUtils with improved bit shift operations
 */
class BitUtilsImprovedTest {
    @Test
    fun testLegacyCompatibility() {
        // Ensure that legacy urShift methods still work for existing code

        // Test some basic positive cases that should be stable
        assertEquals(0x1234, BitUtils.urShift(0x12345678, 16))
        assertEquals(0x123, BitUtils.urShift(0x12345678, 20))

        // Test that method signatures haven't changed
        val intResult: Int = BitUtils.urShift(0x12345678, 4)
        val longResult: Long = BitUtils.urShift(0x123456789ABCDEF0L, 4)

        assertTrue(intResult >= 0, "Should handle result correctly")
        assertTrue(longResult >= 0, "Should handle result correctly")
    }

    @Test
    fun testImprovedUrShiftConsistency() {
        // Test that improved methods provide better consistency
        val testValues = listOf(0x12345678, -1, 0x80000000.toInt(), 0x7FFFFFFF)
        val testShifts = listOf(1, 4, 8, 16)

        for (value in testValues) {
            for (shift in testShifts) {
                val nativeEngine = BitUtils.withNativeMode()
                val arithmeticEngine = BitUtils.withArithmeticMode()

                val nativeResult = BitUtils.urShiftImproved(value, shift, nativeEngine)
                val arithmeticResult = BitUtils.urShiftImproved(value, shift, arithmeticEngine)

                assertEquals(
                    nativeResult,
                    arithmeticResult,
                    "Improved urShift should be consistent between engines for value=0x${value.toString(16)}, shift=$shift",
                )
            }
        }
    }

    @Test
    fun testImprovedVsLegacyForPositiveNumbers() {
        // For positive numbers, improved should behave the same as legacy for most cases
        val positiveValues = listOf(0x12345678, 0x7FFFFFFF, 1, 1024)
        val shifts = listOf(1, 4, 8, 16)

        for (value in positiveValues) {
            for (shift in shifts) {
                val legacy = BitUtils.urShift(value, shift)
                val improved = BitUtils.urShiftImproved(value, shift)

                // For positive numbers, both should generally give the same result as ushr
                val expected = value ushr shift
                assertEquals(
                    expected,
                    improved,
                    "Improved urShift should match ushr for positive values: $value >>> $shift",
                )
                assertEquals(
                    expected,
                    legacy,
                    "Legacy urShift should match ushr for positive values: $value >>> $shift",
                )
            }
        }
    }

    @Test
    fun testEngineConfiguration() {
        // Test that engine configuration methods work correctly
        val nativeEngine = BitUtils.withNativeMode()
        val arithmeticEngine = BitUtils.withArithmeticMode()

        assertEquals(BitShiftMode.NATIVE, nativeEngine.mode)
        assertEquals(BitShiftMode.ARITHMETIC, arithmeticEngine.mode)

        // Test that they can be used for operations
        val testValue = 0x12345678
        val testShift = 8

        val nativeResult = BitUtils.urShiftImproved(testValue, testShift, nativeEngine)
        val arithmeticResult = BitUtils.urShiftImproved(testValue, testShift, arithmeticEngine)

        // Results should be consistent
        assertEquals(nativeResult, arithmeticResult, "Engine configurations should produce consistent results")
    }

    @Test
    fun testDefaultBehavior() {
        // Test that default behavior (without specifying engine) works correctly
        val testValue = 0x87654321.toInt()
        val testShift = 4

        val result = BitUtils.urShiftImproved(testValue, testShift)

        // Should be a valid positive result
        assertTrue(result >= 0, "Default improved urShift should produce positive result")

        // Should match what we expect from unsigned right shift
        val expected = (testValue.toLong() and 0xFFFFFFFFL) ushr testShift
        assertEquals(expected.toInt(), result, "Should match expected unsigned shift result")
    }

    @Test
    fun testEdgeCasesHandling() {
        // Test that improved methods handle edge cases better than legacy

        // Test shift by 0
        assertEquals(0x12345678, BitUtils.urShiftImproved(0x12345678, 0))
        assertEquals(-1, BitUtils.urShiftImproved(-1, 0))

        // Test shift by >= 32 (should return 0)
        assertEquals(0, BitUtils.urShiftImproved(0x12345678, 32))
        assertEquals(0, BitUtils.urShiftImproved(-1, 32))
        assertEquals(0, BitUtils.urShiftImproved(0x12345678, 33))

        // Test negative shift (should be handled safely)
        assertEquals(0, BitUtils.urShiftImproved(0x12345678, -1))
        assertEquals(0, BitUtils.urShiftImproved(-1, -1))

        // Test with Long values
        assertEquals(0L, BitUtils.urShiftImproved(0x123456789ABCDEF0L, 64))
        assertEquals(0L, BitUtils.urShiftImproved(-1L, -1))
    }

    @Test
    fun testDocumentedBehaviorForNegativeNumbers() {
        // Test that improved urShift provides documented, consistent behavior for negative numbers

        val negativeTestCases =
            listOf(
                // -1 >>> 1 should give max positive int
                Triple(-1, 1, 0x7FFFFFFF),
                // Negative byte value
                Triple(-256, 8, 0x00FFFFFF),
                // Min int value
                Triple(Int.MIN_VALUE, 1, 0x40000000),
            )

        for ((value, shift, expectedResult) in negativeTestCases) {
            val result = BitUtils.urShiftImproved(value, shift)
            assertEquals(
                expectedResult,
                result,
                "urShiftImproved($value, $shift) should equal 0x${expectedResult.toString(16)}",
            )
        }
    }
}
