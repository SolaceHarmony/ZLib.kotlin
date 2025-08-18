package ai.solace.zlib.bitwise.test

import ai.solace.zlib.bitwise.BitwiseOps
import ai.solace.zlib.bitwise.BitShiftEngine
import ai.solace.zlib.bitwise.BitShiftMode
import kotlin.test.*

/**
 * Test for improved bit shift operations that fix existing bugs
 */
class ImprovedBitShiftTest {
    
    @Test
    fun testImprovedUrShiftConsistency() {
        val testValues = listOf(
            0x12345678, -0x12345678, 0x7FFFFFFF, 0x80000000.toInt(), -1, 0, 1, 255, 256
        )
        val testShifts = listOf(0, 1, 4, 8, 16, 31, 32)
        
        val nativeEngine = BitShiftEngine(BitShiftMode.NATIVE, 32)
        val arithmeticEngine = BitShiftEngine(BitShiftMode.ARITHMETIC, 32)
        
        for (value in testValues) {
            for (shift in testShifts) {
                // Test both engines produce consistent results
                val nativeResult = BitwiseOps.urShiftImproved(value, shift, nativeEngine)
                val arithmeticResult = BitwiseOps.urShiftImproved(value, shift, arithmeticEngine)
                
                if (shift < 32) {
                    assertEquals(nativeResult, arithmeticResult, 
                        "urShiftImproved inconsistent for value=0x${value.toString(16)}, shift=$shift")
                } else {
                    // Both should return 0 for shifts >= 32
                    assertEquals(0, nativeResult, "Should return 0 for shift >= 32")
                    assertEquals(0, arithmeticResult, "Should return 0 for shift >= 32")
                }
            }
        }
    }
    
    @Test
    fun testImprovedUrShiftMathematicalCorrectness() {
        // Test that improved urShift produces mathematically correct results
        val testCases = listOf(
            // value, shift, expected unsigned result
            Triple(0x12345678, 4, 0x01234567),
            Triple(0x12345678, 8, 0x00123456),
            Triple(0x12345678, 16, 0x00001234),
            Triple(-1, 1, 0x7FFFFFFF), // -1 shifted should give max positive
            Triple(0x80000000.toInt(), 1, 0x40000000), // Min int shifted  
            Triple(-256, 8, 0x00FFFFFF), // Negative number
        )
        
        for ((value, shift, expected) in testCases) {
            val result = BitwiseOps.urShiftImproved(value, shift)
            assertEquals(expected, result, 
                "urShiftImproved(0x${value.toString(16)}, $shift) should equal 0x${expected.toString(16)}")
        }
    }
    
    @Test
    fun testImprovedUrShiftLongValues() {
        val testCases = listOf(
            Triple(0x123456789ABCDEF0L, 4, 0x0123456789ABCDEFL),
            Triple(0x123456789ABCDEF0L, 32, 0x12345678L),
            Triple(-1L, 1, 0x7FFFFFFFFFFFFFFFL),
            Triple(Long.MIN_VALUE, 1, 0x4000000000000000L),
        )
        
        for ((value, shift, expected) in testCases) {
            val result = BitwiseOps.urShiftImproved(value, shift)
            assertEquals(expected, result,
                "urShiftImproved(0x${value.toString(16)}, $shift) should equal 0x${expected.toString(16)}")
        }
    }
    
    @Test  
    fun testComparisonWithNativeUshr() {
        // For positive numbers, improved urShift should match native ushr
        val positiveValues = listOf(0, 1, 0x12345678, 0x7FFFFFFF)
        val shifts = listOf(0, 1, 4, 8, 16, 31)
        
        for (value in positiveValues) {
            for (shift in shifts) {
                val improved = BitwiseOps.urShiftImproved(value, shift)
                val native = value ushr shift
                
                assertEquals(native, improved,
                    "For positive values, urShiftImproved should match ushr: $value >>> $shift")
            }
        }
    }
    
    @Test
    fun testEdgeCases() {
        // Test edge cases that might have caused bugs in the original implementation
        
        // Shift by 0 should return original value
        assertEquals(0x12345678, BitwiseOps.urShiftImproved(0x12345678, 0))
        assertEquals(-1, BitwiseOps.urShiftImproved(-1, 0))
        
        // Shift by >= bit width should return 0
        assertEquals(0, BitwiseOps.urShiftImproved(0x12345678, 32))
        assertEquals(0, BitwiseOps.urShiftImproved(-1, 32))
        assertEquals(0, BitwiseOps.urShiftImproved(0x12345678, 33))
        
        // Negative shift should return 0 (safe handling)
        assertEquals(0, BitwiseOps.urShiftImproved(0x12345678, -1))
        assertEquals(0, BitwiseOps.urShiftImproved(-1, -1))
        
        // Test with maximum values
        val maxInt = Int.MAX_VALUE
        val minInt = Int.MIN_VALUE
        
        assertEquals(maxInt ushr 1, BitwiseOps.urShiftImproved(maxInt, 1))
        assertEquals(0x40000000, BitwiseOps.urShiftImproved(minInt, 1)) // Should be positive
    }
    
    @Test
    fun testIntegrationWithBitShiftEngine() {
        // Test that using different engines with urShiftImproved works correctly
        val value = 0x87654321.toInt() // Negative in 32-bit signed
        val shift = 8
        
        val nativeEngine = BitShiftEngine(BitShiftMode.NATIVE, 32)
        val arithmeticEngine = BitShiftEngine(BitShiftMode.ARITHMETIC, 32)
        
        val nativeResult = BitwiseOps.urShiftImproved(value, shift, nativeEngine)
        val arithmeticResult = BitwiseOps.urShiftImproved(value, shift, arithmeticEngine)
        
        // Both should produce the same mathematically correct result
        assertEquals(nativeResult, arithmeticResult,
            "Native and arithmetic engines should produce same result")
        
        // The result should be positive (since it's unsigned right shift)
        assertTrue(nativeResult >= 0, "Unsigned right shift result should be positive")
        assertTrue(arithmeticResult >= 0, "Unsigned right shift result should be positive")
    }
}