package ai.solace.zlib.bitwise.test

import ai.solace.zlib.bitwise.huffman.HuffmanUtils
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class HuffmanUtilsTest {
    
    @Test
    fun testExtractCode() {
        // Test with various bit buffers and masks
        assertEquals(0, HuffmanUtils.extractCode(0, 0))
        assertEquals(0x5, HuffmanUtils.extractCode(0x12345, 0x7))
        assertEquals(0x45, HuffmanUtils.extractCode(0x12345, 0xFF))
        assertEquals(0x2345, HuffmanUtils.extractCode(0x12345, 0xFFFF))
        
        // Test with real-world examples (e.g., from Huffman coding)
        val bitBuffer = 0b10110101 // Binary representation
        val mask3Bits = 0b111 // 3-bit mask
        val mask5Bits = 0b11111 // 5-bit mask
        
        assertEquals(0b101, HuffmanUtils.extractCode(bitBuffer, mask3Bits))
        assertEquals(0b10101, HuffmanUtils.extractCode(bitBuffer, mask5Bits))
    }
    
    @Test
    fun testConsumeBits() {
        // Test consuming different numbers of bits
        assertEquals(0, HuffmanUtils.consumeBits(0, 0))
        assertEquals(0, HuffmanUtils.consumeBits(0xFF, 8))
        assertEquals(0x12, HuffmanUtils.consumeBits(0x1234, 8))
        assertEquals(0x1, HuffmanUtils.consumeBits(0x1234, 4))
        
        // Test with real-world examples
        val bitBuffer = 0b10110101 // Binary representation
        
        assertEquals(0b1011010, HuffmanUtils.consumeBits(bitBuffer, 1)) // Consume 1 bit
        assertEquals(0b101101, HuffmanUtils.consumeBits(bitBuffer, 2)) // Consume 2 bits
        assertEquals(0b10110, HuffmanUtils.consumeBits(bitBuffer, 3)) // Consume 3 bits
        assertEquals(0b1, HuffmanUtils.consumeBits(bitBuffer, 7)) // Consume 7 bits
        assertEquals(0, HuffmanUtils.consumeBits(bitBuffer, 8)) // Consume all bits
    }
    
    @Test
    fun testAddBits() {
        // Test adding bits to an empty buffer
        assertEquals(0x5A, HuffmanUtils.addBits(0, 0x5A.toByte(), 0))
        
        // Test adding bits to a non-empty buffer
        assertEquals(0x5A3C, HuffmanUtils.addBits(0x5A, 0x3C.toByte(), 8))
        
        // Test with different bit counts
        assertEquals(0x5A, HuffmanUtils.addBits(0, 0x5A.toByte(), 0))
        assertEquals(0x5A00, HuffmanUtils.addBits(0, 0x5A.toByte(), 8))
        assertEquals(0x5A0000, HuffmanUtils.addBits(0, 0x5A.toByte(), 16))
        
        // Test with existing bits in the buffer
        assertEquals(0x123405A, HuffmanUtils.addBits(0x1234, 0x5A.toByte(), 8))
    }
    
    @Test
    fun testIsBitSet() {
        // Test with various values and bit positions
        assertFalse(HuffmanUtils.isBitSet(0, 0))
        assertTrue(HuffmanUtils.isBitSet(1, 0))
        assertFalse(HuffmanUtils.isBitSet(2, 0))
        assertTrue(HuffmanUtils.isBitSet(2, 1))
        
        // Test with a more complex value
        val value = 0b10101010 // Binary representation
        
        assertTrue(HuffmanUtils.isBitSet(value, 1))
        assertFalse(HuffmanUtils.isBitSet(value, 2))
        assertTrue(HuffmanUtils.isBitSet(value, 3))
        assertFalse(HuffmanUtils.isBitSet(value, 4))
        assertTrue(HuffmanUtils.isBitSet(value, 5))
        assertFalse(HuffmanUtils.isBitSet(value, 6))
        assertTrue(HuffmanUtils.isBitSet(value, 7))
        
        // Test with all bits set
        val allBitsSet = -1 // All bits set to 1 in two's complement
        
        for (i in 0 until 32) {
            assertTrue(HuffmanUtils.isBitSet(allBitsSet, i))
        }
    }
    
    @Test
    fun testCombinedOperations() {
        // Test a sequence of operations that might be used in Huffman decoding
        var bitBuffer = 0
        var bitCount = 0
        
        // Add some bits
        bitBuffer = HuffmanUtils.addBits(bitBuffer, 0x5A.toByte(), bitCount)
        bitCount += 8
        
        // Extract a code
        val code = HuffmanUtils.extractCode(bitBuffer, 0x7) // Extract 3 bits
        assertEquals(0x2, code) // 010 in binary
        
        // Consume the code bits
        bitBuffer = HuffmanUtils.consumeBits(bitBuffer, 3)
        bitCount -= 3
        
        // Check if a specific bit is set in the remaining buffer
        assertTrue(HuffmanUtils.isBitSet(bitBuffer, 0)) // First bit should be 1
        assertFalse(HuffmanUtils.isBitSet(bitBuffer, 1)) // Second bit should be 0
        
        // Add more bits
        bitBuffer = HuffmanUtils.addBits(bitBuffer, 0x3C.toByte(), bitCount)
        bitCount += 8
        
        // Extract another code
        val code2 = HuffmanUtils.extractCode(bitBuffer, 0x1F) // Extract 5 bits
        assertEquals(0x1B, code2) // 11011 in binary
    }
}