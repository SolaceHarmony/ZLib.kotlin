package ai.solace.zlib.test

import ai.solace.zlib.clean.ArithmeticBitReader
import kotlin.test.Test
import kotlin.test.assertEquals

class SimpleArithmeticTest {
    
    @Test
    fun testArithmeticBitReaderBasic() {
        // Simple test data
        val data = byteArrayOf(0xFF.toByte(), 0x00.toByte())
        val reader = ArithmeticBitReader(data)
        
        // Test reading bits
        assertEquals(1, reader.take(1), "First bit should be 1")
        assertEquals(7, reader.take(3), "Next 3 bits should be 111 = 7")
        assertEquals(15, reader.take(4), "Next 4 bits should be 1111 = 15")
        assertEquals(0, reader.take(8), "Next 8 bits (second byte) should be 0")
    }
    
    @Test 
    fun testArithmeticBitReaderPeek() {
        val data = byteArrayOf(0xAA.toByte()) // 10101010
        val reader = ArithmeticBitReader(data)
        
        // Peek should not consume
        assertEquals(0, reader.peek(1), "LSB should be 0")  // 10101010 -> LSB is 0
        assertEquals(0, reader.peek(1), "Peek again should give same result")
        
        // Now consume
        assertEquals(0, reader.take(1), "Take should consume the bit")
        assertEquals(1, reader.peek(1), "Next bit should be 1")
    }
}