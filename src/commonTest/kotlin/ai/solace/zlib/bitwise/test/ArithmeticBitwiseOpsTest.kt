package ai.solace.zlib.bitwise.test

import ai.solace.zlib.bitwise.ArithmeticBitwiseOps
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArithmeticBitwiseOpsTest {
    
    @Test
    fun test8BitOperations() {
        val ops = ArithmeticBitwiseOps.BITS_8
        
        // Test boundary values
        assertEquals(255L, ops.normalize(255L))
        assertEquals(0L, ops.normalize(256L))
        assertEquals(255L, ops.normalize(-1L))
        
        // Test left shift
        assertEquals(2L, ops.leftShift(1L, 1))
        assertEquals(0L, ops.leftShift(128L, 1)) // Overflow wraps to 0
        assertEquals(0L, ops.leftShift(1L, 8)) // Shift beyond bit length
        
        // Test right shift
        assertEquals(1L, ops.rightShift(2L, 1))
        assertEquals(0L, ops.rightShift(1L, 1))
        
        // Test OR
        assertEquals(7L, ops.or(5L, 2L)) // 101 | 010 = 111
        assertEquals(255L, ops.or(255L, 128L)) // All bits set
        
        // Test AND
        assertEquals(0L, ops.and(5L, 2L)) // 101 & 010 = 000
        assertEquals(5L, ops.and(5L, 255L)) // 101 & 11111111 = 101
        
        // Test XOR
        assertEquals(7L, ops.xor(5L, 2L)) // 101 ^ 010 = 111
        assertEquals(0L, ops.xor(5L, 5L)) // Same values = 0
        
        // Test NOT
        assertEquals(250L, ops.not(5L)) // ~00000101 = 11111010 = 250
        
        // Test bit checking
        assertTrue(ops.isBitSet(5L, 0)) // Bit 0 of 101 is set
        assertFalse(ops.isBitSet(5L, 1)) // Bit 1 of 101 is not set
        assertTrue(ops.isBitSet(5L, 2)) // Bit 2 of 101 is set
    }
    
    @Test
    fun test16BitOperations() {
        val ops = ArithmeticBitwiseOps.BITS_16
        
        // Test boundary values
        assertEquals(65535L, ops.normalize(65535L))
        assertEquals(0L, ops.normalize(65536L))
        assertEquals(65535L, ops.normalize(-1L))
        
        // Test operations with larger values
        assertEquals(32768L, ops.leftShift(16384L, 1))
        assertEquals(0L, ops.leftShift(32768L, 1)) // Overflow wraps to 0
        
        // Test with values larger than 8-bit
        assertEquals(258L, ops.or(256L, 2L)) // 0x100 | 0x002 = 0x102
        assertEquals(0L, ops.and(256L, 255L)) // 0x100 & 0xFF = 0x000
    }
    
    @Test
    fun test32BitOperations() {
        val ops = ArithmeticBitwiseOps.BITS_32
        
        // Test with large 32-bit values
        val large1 = 0x12345678L
        val large2 = 0x87654321L
        
        // Test OR
        val orResult = ops.or(large1, large2)
        assertEquals(0x97755779L, orResult)
        
        // Test AND
        val andResult = ops.and(large1, large2)
        assertEquals(0x02244220L, andResult)
        
        // Test XOR
        val xorResult = ops.xor(large1, large2)
        assertEquals(0x95511559L, xorResult)
    }
    
    @Test
    fun testRotation() {
        val ops = ArithmeticBitwiseOps.BITS_8
        
        // Test rotate left: 00000001 -> 00000010
        assertEquals(2L, ops.rotateLeft(1L, 1))
        
        // Test rotate left with wrap: 10000000 -> 00000001
        assertEquals(1L, ops.rotateLeft(128L, 1))
        
        // Test rotate right: 00000010 -> 00000001
        assertEquals(1L, ops.rotateRight(2L, 1))
        
        // Test rotate right with wrap: 00000001 -> 10000000
        assertEquals(128L, ops.rotateRight(1L, 1))
    }
    
    @Test
    fun testSignedUnsignedConversion() {
        val ops = ArithmeticBitwiseOps.BITS_8
        
        // Test unsigned to signed conversion
        assertEquals(-1L, ops.toSigned(255L)) // 0xFF = -1 in signed 8-bit
        assertEquals(-128L, ops.toSigned(128L)) // 0x80 = -128 in signed 8-bit
        assertEquals(127L, ops.toSigned(127L)) // 0x7F = 127 in signed 8-bit
        
        // Test signed to unsigned conversion (normalize does this)
        assertEquals(255L, ops.toUnsigned(-1L))
        assertEquals(128L, ops.toUnsigned(-128L))
    }
    
    @Test
    fun testMaskCreation() {
        val ops = ArithmeticBitwiseOps.BITS_8
        
        assertEquals(0L, ops.createMask(0))
        assertEquals(1L, ops.createMask(1)) // 0b1
        assertEquals(3L, ops.createMask(2)) // 0b11
        assertEquals(7L, ops.createMask(3)) // 0b111
        assertEquals(15L, ops.createMask(4)) // 0b1111
        assertEquals(255L, ops.createMask(8)) // 0b11111111
        assertEquals(255L, ops.createMask(10)) // Clamped to 8 bits
    }
    
    @Test
    fun testExtractBits() {
        val ops = ArithmeticBitwiseOps.BITS_8
        
        val value = 0b10110101L // 181
        
        assertEquals(1L, ops.extractBits(value, 1)) // Last 1 bit: 1
        assertEquals(1L, ops.extractBits(value, 2)) // Last 2 bits: 01 = 1  
        assertEquals(5L, ops.extractBits(value, 3)) // Last 3 bits: 101 = 5
        assertEquals(21L, ops.extractBits(value, 5)) // Last 5 bits: 10101 = 21
        assertEquals(181L, ops.extractBits(value, 8)) // All 8 bits: 181
    }
    
    @Test
    fun testHuffmanUtilsCompatibility() {
        // Test that this can replace the HuffmanUtils operations
        val ops = ArithmeticBitwiseOps.BITS_32
        
        // Test extractCode equivalent
        val bitBuffer = 0x12345L
        val bitMask = 0x7L
        assertEquals(5L, ops.and(bitBuffer, bitMask))
        
        // Test consumeBits equivalent
        assertEquals(291L, ops.rightShift(0x1234L, 4))
        
        // Test addBits equivalent
        val result = ops.or(0x5AL, ops.leftShift(0x3CL, 8))
        assertEquals(15450L, result)
        
        // Test isBitSet equivalent
        assertTrue(ops.isBitSet(11L, 0))
        assertTrue(ops.isBitSet(11L, 1))
        assertFalse(ops.isBitSet(11L, 2))
        assertTrue(ops.isBitSet(11L, 3))
    }
}
