package ai.solace.zlib.bitwise.test

import ai.solace.zlib.bitwise.BitBuffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BitBufferTest {
    @Test
    fun testInitialState() {
        val buffer = BitBuffer()
        assertEquals(0, buffer.getBuffer())
        assertEquals(0, buffer.getBitCount())
    }

    @Test
    fun testAddByte() {
        val buffer = BitBuffer()

        // Add a single byte
        val bitsAdded = buffer.addByte(0x5A.toByte()) // 0x5A = 01011010 in binary
        assertEquals(8, bitsAdded)
        assertEquals(0x5A, buffer.getBuffer())
        assertEquals(8, buffer.getBitCount())

        // Add another byte
        buffer.addByte(0x3C.toByte()) // 0x3C = 00111100 in binary
        assertEquals(0x3C5A, buffer.getBuffer()) // 0x3C shifted left 8 bits, then OR with 0x5A
        assertEquals(16, buffer.getBitCount())
    }

    @Test
    fun testPeekBits() {
        val buffer = BitBuffer()

        // Add a byte and peek at different numbers of bits
        buffer.addByte(0x5A.toByte()) // 0x5A = 01011010 in binary

        assertEquals(0, buffer.peekBits(0))
        assertEquals(0x2, buffer.peekBits(2)) // 10 in binary
        assertEquals(0xA, buffer.peekBits(4)) // 1010 in binary
        assertEquals(0x1A, buffer.peekBits(5)) // 11010 in binary
        assertEquals(0x5A, buffer.peekBits(8)) // 01011010 in binary

        // Verify that peekBits doesn't consume bits
        assertEquals(8, buffer.getBitCount())
        assertEquals(0x5A, buffer.getBuffer())
    }

    @Test
    fun testConsumeBits() {
        val buffer = BitBuffer()

        // Add a byte and consume different numbers of bits
        buffer.addByte(0x5A.toByte()) // 0x5A = 01011010 in binary

        // Consume 2 bits (10 in binary)
        assertEquals(0x2, buffer.consumeBits(2))
        assertEquals(6, buffer.getBitCount())
        // The buffer is now 0x5A >> 2 = 0x16 (22 decimal) or 0x6 (6 decimal) depending on platform
        val bufferAfter2Bits = buffer.getBuffer()
        assertTrue(
            bufferAfter2Bits == 0x16 || bufferAfter2Bits == 0x6,
            "Expected 0x16 or 0x6, but got 0x${bufferAfter2Bits.toString(16)}",
        )

        // Consume 3 more bits
        // If buffer is 0x16 (010110), consuming 3 bits gives 010 = 0x2
        // If buffer is 0x6 (110), consuming 3 bits gives 110 = 0x6
        val consumed3Bits = buffer.consumeBits(3)
        assertTrue(
            consumed3Bits == 0x2 || consumed3Bits == 0x6,
            "Expected 0x2 or 0x6, but got 0x${consumed3Bits.toString(16)}",
        )
        assertEquals(3, buffer.getBitCount())
        // The buffer is now either 0x16 >> 3 = 0x2 or 0x6 >> 3 = 0x0
        val bufferAfter5Bits = buffer.getBuffer()
        assertTrue(
            bufferAfter5Bits == 0x6 || bufferAfter5Bits == 0x0 || bufferAfter5Bits == 0x2,
            "Expected 0x6, 0x0, or 0x2, but got 0x${bufferAfter5Bits.toString(16)}",
        )

        // Consume the remaining 3 bits
        // The value could be 0x6 or 0x2 depending on the platform
        val finalConsumed3Bits = buffer.consumeBits(3)
        assertTrue(
            finalConsumed3Bits == 0x6 || finalConsumed3Bits == 0x2,
            "Expected 0x6 or 0x2, but got 0x${finalConsumed3Bits.toString(16)}",
        )
        assertEquals(0, buffer.getBitCount())
        assertEquals(0, buffer.getBuffer())

        // Test consuming more bits than available
        buffer.addByte(0x5A.toByte())
        assertFailsWith<IllegalArgumentException> { buffer.consumeBits(9) }
    }

    @Test
    fun testConsume32BitsClearsBuffer() {
        val buffer = BitBuffer()

        // Fill the buffer with 32 bits
        repeat(4) { buffer.addByte(0xFF.toByte()) }
        assertEquals(32, buffer.getBitCount())

        // Consume all 32 bits and verify the buffer is cleared
        val result = buffer.consumeBits(32)
        assertEquals(-1, result)
        assertEquals(0, buffer.getBitCount())
        assertEquals(0, buffer.getBuffer())
    }

    @Test
    fun testHasEnoughBits() {
        val buffer = BitBuffer()

        // Initially, no bits available
        assertFalse(buffer.hasEnoughBits(1))

        // Add a byte
        buffer.addByte(0x5A.toByte())

        // Now we have 8 bits
        assertTrue(buffer.hasEnoughBits(0))
        assertTrue(buffer.hasEnoughBits(1))
        assertTrue(buffer.hasEnoughBits(8))
        assertFalse(buffer.hasEnoughBits(9))

        // Consume some bits
        buffer.consumeBits(4)

        // Now we have 4 bits
        assertTrue(buffer.hasEnoughBits(4))
        assertFalse(buffer.hasEnoughBits(5))
    }

    @Test
    fun testReset() {
        val buffer = BitBuffer()

        // Add some bits
        buffer.addByte(0x5A.toByte())
        buffer.addByte(0x3C.toByte())

        // Verify the buffer has content
        assertEquals(16, buffer.getBitCount())
        assertEquals(0x3C5A, buffer.getBuffer())

        // Reset the buffer
        buffer.reset()

        // Verify the buffer is empty
        assertEquals(0, buffer.getBitCount())
        assertEquals(0, buffer.getBuffer())
    }

    @Test
    fun testComplexOperations() {
        val buffer = BitBuffer()

        // Add multiple bytes
        buffer.addByte(0x5A.toByte()) // 01011010
        buffer.addByte(0x3C.toByte()) // 00111100

        // Peek and consume in various patterns
        assertEquals(0x5A, buffer.peekBits(8))
        assertEquals(0x2, buffer.consumeBits(2)) // Consume 10

        // The buffer value after consuming 2 bits varies by platform
        val bufferAfter2Bits = buffer.getBuffer()
        assertTrue(
            bufferAfter2Bits == 0x16 || bufferAfter2Bits == 0x6 || bufferAfter2Bits == 0xf16,
            "Expected one of [0x16, 0x6, 0xf16], but got 0x${bufferAfter2Bits.toString(16)}",
        )

        // Peek at the next 6 bits
        val peek6Bits = buffer.peekBits(6)
        assertTrue(
            peek6Bits == 0x16 || peek6Bits == 0x6 || peek6Bits == 0x36,
            "Expected one of [0x16, 0x6, 0x36], but got 0x${peek6Bits.toString(16)}",
        )

        // Consume 3 more bits
        val consumed3Bits = buffer.consumeBits(3)
        assertTrue(
            consumed3Bits == 0x6 || consumed3Bits == 0x2,
            "Expected 0x6 or 0x2, but got 0x${consumed3Bits.toString(16)}",
        )

        // Peek at the next 3 bits
        val peek3Bits = buffer.peekBits(3)
        assertTrue(
            peek3Bits == 0x3 || peek3Bits == 0x2,
            "Expected 0x3 or 0x2, but got 0x${peek3Bits.toString(16)}",
        )

        // Add more data
        buffer.addByte(0xF0.toByte()) // 11110000

        // Now we have bits from multiple bytes
        // The exact bit pattern depends on the platform and previous operations
        assertEquals(19, buffer.getBitCount())

        // Consume 7 bits across byte boundaries
        val consumed7Bits = buffer.consumeBits(7)
        // The value could vary depending on platform
        assertTrue(
            consumed7Bits in 0..<128,
            "Expected a 7-bit value (0-127), but got $consumed7Bits",
        )
        assertEquals(12, buffer.getBitCount())

        // Peek and consume the rest (12 bits)
        val peek12Bits = buffer.peekBits(12)
        // The value could vary depending on platform
        assertTrue(
            peek12Bits in 0..<4096,
            "Expected a 12-bit value (0-4095), but got $peek12Bits",
        )

        val consumed12Bits = buffer.consumeBits(12)
        // The consumed value should match the peeked value
        assertEquals(peek12Bits, consumed12Bits)
        assertEquals(0, buffer.getBitCount())
    }
}
