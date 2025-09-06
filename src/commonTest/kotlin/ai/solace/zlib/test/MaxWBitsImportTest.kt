package ai.solace.zlib.test

import ai.solace.zlib.common.MAX_WBITS
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Test to verify that MAX_WBITS constant is properly imported and accessible.
 * This addresses the issue where MAX_WBITS was referenced but not properly imported.
 */
class MaxWBitsImportTest {
    @Test
    fun testMaxWBitsIsAccessible() {
        // Test that MAX_WBITS constant is accessible without compilation errors
        val maxWindowBits = MAX_WBITS

        // MAX_WBITS should be 15 (representing 32K window size)
        assertEquals(15, maxWindowBits, "MAX_WBITS should be 15")
    }

    @Test
    fun testMaxWBitsCanBeUsedInComputations() {
        // Test that MAX_WBITS can be used in typical calculations
        val windowSize = 1 shl MAX_WBITS // 2^15 = 32768
        assertEquals(32768, windowSize, "Window size calculation should work with MAX_WBITS")

        // Test using MAX_WBITS in bitwise operations (common in zlib)
        val wbitsCheck = MAX_WBITS and 0x0F
        assertEquals(15, wbitsCheck, "Bitwise operations with MAX_WBITS should work")
    }

    @Test
    fun testMaxWBitsInInflateSetup() {
        // Test the specific use case mentioned in InfBlocksUtilsTest.kt
        // This verifies the exact scenario that was failing
        val defaultWbits = MAX_WBITS
        assertTrue(defaultWbits > 0, "MAX_WBITS should be positive")
        assertTrue(defaultWbits <= 15, "MAX_WBITS should not exceed 15")
        assertEquals(15, defaultWbits, "MAX_WBITS should be exactly 15 for standard zlib")
    }
}
