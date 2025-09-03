package ai.solace.zlib.test

import ai.solace.zlib.bitwise.ArithmeticBitwiseOps
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Test to verify that ArithmeticBitwiseOps produces the same results as native bitwise operations
 */
class ArithmeticBitwiseVerificationTest {

    @Test
    fun testNormalizeMatchesNativeAnd() {
        val ops8 = ArithmeticBitwiseOps(8)
        val ops16 = ArithmeticBitwiseOps(16)
        val ops32 = ArithmeticBitwiseOps(32)
        
        // Test 8-bit normalize
        for (value in listOf(0L, 1L, 127L, 128L, 255L, 256L, 1000L, -1L, -128L)) {
            val arithmetic = ops8.normalize(value)
            val native = value and 0xFF
            assertEquals(native, arithmetic, "8-bit normalize($value) mismatch")
        }
        
        // Test 16-bit normalize  
        for (value in listOf(0L, 1L, 32767L, 32768L, 65535L, 65536L, 100000L, -1L, -32768L)) {
            val arithmetic = ops16.normalize(value)
            val native = value and 0xFFFF
            assertEquals(native, arithmetic, "16-bit normalize($value) mismatch")
        }
        
        // Test 32-bit normalize
        for (value in listOf(0L, 1L, 0x7FFFFFFFL, 0x80000000L, 0xFFFFFFFFL, 0x100000000L, -1L)) {
            val arithmetic = ops32.normalize(value)
            val native = value and 0xFFFFFFFFL
            assertEquals(native, arithmetic, "32-bit normalize($value) mismatch")
        }
    }
    
    @Test
    fun testAndMatchesNative() {
        val ops32 = ArithmeticBitwiseOps(32)
        
        // Test various AND operations
        val testCases = listOf(
            0xFF to 0x55,
            0xFFFF to 0xAAAA,
            0x12345678 to 0xFF,
            0x12345678 to 0xFFFF,
            0x12345678 to 0xFFFFFFFF,
            0xDEADBEEF to 0xCAFEBABE,
            255 to 15,
            1024 to 1023,
            0 to 0xFFFFFFFF,
            0xFFFFFFFF to 0
        )
        
        for ((a, b) in testCases) {
            val arithmetic = ops32.and(a.toLong(), b.toLong())
            val native = a.toLong() and b.toLong()
            assertEquals(native, arithmetic, "AND($a, $b) mismatch")
        }
    }
    
    @Test 
    fun testOrMatchesNative() {
        val ops32 = ArithmeticBitwiseOps(32)
        
        val testCases = listOf(
            0xFF to 0x55,
            0x00 to 0xFF,
            0x12345678 to 0x87654321,
            0xF0F0F0F0 to 0x0F0F0F0F
        )
        
        for ((a, b) in testCases) {
            val arithmetic = ops32.or(a.toLong(), b.toLong())
            val native = a.toLong() or b.toLong()
            assertEquals(native, arithmetic, "OR($a, $b) mismatch")
        }
    }
    
    @Test
    fun testLeftShiftMatchesNative() {
        val ops32 = ArithmeticBitwiseOps(32)
        
        for (value in listOf(1L, 0xFFL, 0xAAAAL, 0x12345678L)) {
            for (shift in 0..16) {
                val arithmetic = ops32.leftShift(value, shift)
                val native = (value shl shift) and 0xFFFFFFFFL
                assertEquals(native, arithmetic, "leftShift($value, $shift) mismatch")
            }
        }
    }
    
    @Test
    fun testRightShiftMatchesNative() {
        val ops32 = ArithmeticBitwiseOps(32)
        
        for (value in listOf(0xFFL, 0xFFFFL, 0xAAAAAAAAL, 0x12345678L)) {
            for (shift in 0..16) {
                val arithmetic = ops32.rightShift(value, shift)
                val native = (value and 0xFFFFFFFFL) ushr shift
                assertEquals(native, arithmetic, "rightShift($value, $shift) mismatch")
            }
        }
    }
    
    @Test
    fun testExtractBitsMatchesNative() {
        val ops32 = ArithmeticBitwiseOps(32)
        
        val value = 0x12345678L
        
        for (bits in 1..16) {
            val arithmetic = ops32.extractBits(value, bits)
            val mask = (1L shl bits) - 1L
            val native = value and mask
            assertEquals(native, arithmetic, "extractBits($value, $bits) mismatch")
        }
    }
    
    @Test
    fun testXorMatchesNative() {
        val ops32 = ArithmeticBitwiseOps(32)
        
        val testCases = listOf(
            0xFF to 0x55,
            0xAAAAAAAA to 0x55555555,
            0x12345678 to 0x87654321,
            0 to 0xFFFFFFFF,
            0xFFFFFFFF to 0xFFFFFFFF
        )
        
        for ((a, b) in testCases) {
            val arithmetic = ops32.xor(a.toLong(), b.toLong())
            val native = a.toLong() xor b.toLong()
            assertEquals(native, arithmetic, "XOR($a, $b) mismatch")
        }
    }
    
    @Test
    fun testNotMatchesNative() {
        val ops32 = ArithmeticBitwiseOps(32)
        
        for (value in listOf(0L, 0xFFL, 0xAAAAAAAAL, 0x12345678L, 0xFFFFFFFFL)) {
            val arithmetic = ops32.not(value)
            val native = value.inv() and 0xFFFFFFFFL
            assertEquals(native, arithmetic, "NOT($value) mismatch")
        }
    }
}