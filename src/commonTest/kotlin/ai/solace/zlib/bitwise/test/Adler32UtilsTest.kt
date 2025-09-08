package ai.solace.zlib.bitwise.test

import ai.solace.zlib.bitwise.checksum.Adler32Utils
import kotlin.test.Test
import kotlin.test.assertEquals

class Adler32UtilsTest {
    @Test
    fun testInitialValue() {
        // Test with null buffer
        assertEquals(1L, Adler32Utils.adler32(1L, null, 0, 0))

        // Test with empty buffer
        val emptyBuffer = ByteArray(0)
        assertEquals(1L, Adler32Utils.adler32(1L, emptyBuffer, 0, 0))
    }

    @Test
    fun testSingleByte() {
        // Test with a single byte
        val buffer = byteArrayOf(0x61) // ASCII 'a'

        // Initial adler value is 1
        val adler = Adler32Utils.adler32(1L, buffer, 0, 1)

        // Expected values:
        // s1 = 1 + 0x61 = 0x62
        // s2 = 0 + 0x62 = 0x62 (s2 starts at 0, not 1)
        // adler32 = (s2 << 16) | s1 = 0x00620062
        assertEquals(0x00620062L, adler)
    }

    @Test
    fun testMultipleBytes() {
        // Test with multiple bytes
        val buffer = byteArrayOf(0x61, 0x62, 0x63) // ASCII "abc"

        // Initial adler value is 1
        val adler = Adler32Utils.adler32(1L, buffer, 0, 3)

        // Expected values:
        // s1 = 1 + 0x61 + 0x62 + 0x63 = 0x127
        // s2 = 0 + (1 + 0x61) + (1 + 0x61 + 0x62) + (1 + 0x61 + 0x62 + 0x63) = 0x24D
        // adler32 = (s2 << 16) | s1 = 0x024D0127
        assertEquals(0x024D0127L, adler)
    }

    @Test
    fun testLargeBuffer() {
        // Test with a larger buffer
        val buffer = ByteArray(1000) { it.toByte() }

        // Initial adler value is 1
        val adler = Adler32Utils.adler32(1L, buffer, 0, 1000)

        // Expected value calculated using reference implementation (Python zlib.adler32)
        assertEquals(0x1D03E73CL, adler)
    }

    @Test
    fun testIncrementalUpdate() {
        // Test updating the checksum incrementally
        val buffer1 = byteArrayOf(0x61, 0x62, 0x63) // ASCII "abc"
        val buffer2 = byteArrayOf(0x64, 0x65, 0x66) // ASCII "def"

        // Calculate checksum for first buffer
        val adler1 = Adler32Utils.adler32(1L, buffer1, 0, 3)

        // Update checksum with second buffer
        val adler2 = Adler32Utils.adler32(adler1, buffer2, 0, 3)

        // Calculate checksum for combined buffer
        val combinedBuffer = byteArrayOf(0x61, 0x62, 0x63, 0x64, 0x65, 0x66) // ASCII "abcdef"
        val adlerCombined = Adler32Utils.adler32(1L, combinedBuffer, 0, 6)

        // The incremental update should match the combined calculation
        assertEquals(adlerCombined, adler2)
    }

    @Test
    fun testWithOffset() {
        // Test with an offset into the buffer
        val buffer = byteArrayOf(0x00, 0x00, 0x61, 0x62, 0x63, 0x00, 0x00) // ASCII "abc" with padding

        // Calculate checksum starting at offset 2 for length 3
        val adler = Adler32Utils.adler32(1L, buffer, 2, 3)

        // This should match the checksum for "abc"
        val expectedAdler = Adler32Utils.adler32(1L, byteArrayOf(0x61, 0x62, 0x63), 0, 3)
        assertEquals(expectedAdler, adler)
    }

    @Test
    fun testWithChunks() {
        // Test processing a buffer in chunks
        val buffer = ByteArray(10000) { it.toByte() }

        // Calculate checksum for the entire buffer
        val adlerFull = Adler32Utils.adler32(1L, buffer, 0, 10000)

        // Calculate checksum in chunks
        var adlerChunked = 1L
        val chunkSize = 1000
        for (i in 0 until 10) {
            adlerChunked = Adler32Utils.adler32(adlerChunked, buffer, i * chunkSize, chunkSize)
        }

        // The chunked calculation should match the full calculation
        assertEquals(adlerFull, adlerChunked)
    }

    @Test
    fun testKnownValues() {
        // Test with known values from reference implementations

        // Empty string
        assertEquals(0x00000001L, Adler32Utils.adler32(1L, byteArrayOf(), 0, 0))

        // "a"
        assertEquals(0x00620062L, Adler32Utils.adler32(1L, byteArrayOf(0x61), 0, 1))

        // "abc"
        assertEquals(0x024D0127L, Adler32Utils.adler32(1L, byteArrayOf(0x61, 0x62, 0x63), 0, 3))

        // "message digest"
        val message =
            byteArrayOf(
                // "message "
                0x6D, 0x65, 0x73, 0x73, 0x61, 0x67, 0x65, 0x20,
                // "digest"
                0x64, 0x69, 0x67, 0x65, 0x73, 0x74,
            )
        assertEquals(0x29750586L, Adler32Utils.adler32(1L, message, 0, message.size))

        // "abcdefghijklmnopqrstuvwxyz"
        val alphabet =
            byteArrayOf(
                // "abcdefghijklm"
                0x61, 0x62, 0x63, 0x64, 0x65, 0x66, 0x67, 0x68, 0x69, 0x6A, 0x6B, 0x6C, 0x6D,
                // "nopqrstuvwxyz"
                0x6E, 0x6F, 0x70, 0x71, 0x72, 0x73, 0x74, 0x75, 0x76, 0x77, 0x78, 0x79, 0x7A,
            )
        assertEquals(0x90860B20L, Adler32Utils.adler32(1L, alphabet, 0, alphabet.size))
    }
}
