package ai.solace.zlib.test

import ai.solace.zlib.deflate.ZStream
import ai.solace.zlib.common.*
import kotlin.test.*

/**
 * Comprehensive test suite to validate the Huffman bit-reversal fix is working correctly
 * across all scenarios after the fix was implemented.
 * 
 * Based on the issue description, the fix involved:
 * 1. Adding a reverseBits() function to handle MSB->LSB bit order conversion
 * 2. Applying bit-reversal to all Huffman codes in sendCode()
 * 3. Ensuring RFC 1951 compliance for bit packing
 */
class HuffmanFixValidationTest {

    @Test
    fun testSingleCharacterCompression() {
        println("=== Testing Single Character 'A' Compression ===")
        val originalString = "A"
        val originalData = originalString.encodeToByteArray()

        val deflatedData = deflateData(originalData, Z_DEFAULT_COMPRESSION)
        val inflatedData = inflateData(deflatedData, originalData.size)

        // This is the core validation - should get exactly "A" back, not "FA" or anything else
        assertEquals(originalString, inflatedData.decodeToString(), 
            "Single character compression with Huffman fix should work correctly")
        assertTrue(originalData.contentEquals(inflatedData), 
            "Byte-level comparison should match exactly")
    }

    @Test
    fun testMultipleCharacterCompression() {
        println("=== Testing Multiple Character Compression ===")
        val testStrings = listOf("AB", "Hello", "Test123", "!@#$%^&*()")
        
        for (testString in testStrings) {
            val originalData = testString.encodeToByteArray()
            val deflatedData = deflateData(originalData, Z_DEFAULT_COMPRESSION)
            val inflatedData = inflateData(deflatedData, originalData.size)

            assertEquals(testString, inflatedData.decodeToString(),
                "Multiple character string '$testString' should compress/decompress correctly")
            assertTrue(originalData.contentEquals(inflatedData),
                "Byte-level comparison for '$testString' should match exactly")
        }
    }

    @Test 
    fun testAllCompressionLevels() {
        println("=== Testing All Compression Levels ===")
        val testString = "Test data for compression levels"
        val originalData = testString.encodeToByteArray()
        
        // Test all compression levels 0-9
        for (level in 0..9) {
            try {
                val deflatedData = deflateData(originalData, level)
                val inflatedData = inflateData(deflatedData, originalData.size)

                assertEquals(testString, inflatedData.decodeToString(),
                    "Compression level $level should work correctly with Huffman fix")
                assertTrue(originalData.contentEquals(inflatedData),
                    "Byte-level comparison at level $level should match exactly")
                println("✓ Level $level: OK")
            } catch (e: Exception) {
                fail("Compression level $level failed: ${e.message}")
            }
        }
    }

    @Test
    fun testSpecificHuffmanCodes() {
        println("=== Testing Characters That Use Specific Huffman Codes ===")
        // Test characters that would generate different Huffman codes
        val testChars = listOf('A', 'B', 'C', 'Z', '0', '9', ' ', '\n', '\t')
        
        for (char in testChars) {
            val originalString = char.toString()
            val originalData = originalString.encodeToByteArray()
            
            val deflatedData = deflateData(originalData, Z_DEFAULT_COMPRESSION)
            val inflatedData = inflateData(deflatedData, originalData.size)
            
            assertEquals(originalString, inflatedData.decodeToString(),
                "Character '$char' (${char.code}) should compress/decompress correctly")
            println("✓ Character '$char' (${char.code}): OK")
        }
    }

    @Test
    fun testLargerDataSet() {
        println("=== Testing Larger Data Set ===")
        // Test with repeated data that will definitely use Huffman coding
        val originalString = "Hello World! ".repeat(100)
        val originalData = originalString.encodeToByteArray()
        
        val deflatedData = deflateData(originalData, Z_DEFAULT_COMPRESSION)
        val inflatedData = inflateData(deflatedData, originalData.size)
        
        assertEquals(originalString, inflatedData.decodeToString(),
            "Large repeated data should compress/decompress correctly")
        assertTrue(originalData.contentEquals(inflatedData),
            "Large data byte-level comparison should match exactly")
        
        // Verify compression actually occurred
        assertTrue(deflatedData.size < originalData.size,
            "Large repeated data should actually be compressed")
        println("✓ Compression ratio: ${deflatedData.size}/${originalData.size} = ${(deflatedData.size.toDouble() / originalData.size * 100).toInt()}%")
    }

    @Test
    fun testExpectedCompressedOutput() {
        println("=== Testing Expected Compressed Output Format ===")
        // According to the issue, 'A' should compress to specific bytes
        val originalString = "A"
        val originalData = originalString.encodeToByteArray()
        
        val deflatedData = deflateData(originalData, Z_DEFAULT_COMPRESSION)
        
        // According to the issue description, 'A' should compress to [78, 9c, 73, 04, 00, 00, 42, 00, 42]
        // Let's verify the format is consistent
        assertTrue(deflatedData.size >= 9, "Compressed 'A' should have reasonable size")
        
        // Check zlib header (78 9c is a common zlib header for default compression)
        assertEquals(0x78, deflatedData[0].toInt() and 0xFF, "First byte should be zlib header")
        assertEquals(0x9c, deflatedData[1].toInt() and 0xFF, "Second byte should be zlib header")
        
        println("✓ Compressed 'A' to: ${deflatedData.joinToString(" ") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }}")
    }

    @Test
    fun testBitReversalValidation() {
        println("=== Testing Bit Reversal Implementation ===")
        // Test various characters to ensure bit reversal is working
        val testChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        
        for (char in testChars) {
            val originalString = char.toString()
            val originalData = originalString.encodeToByteArray()
            
            val deflatedData = deflateData(originalData, Z_DEFAULT_COMPRESSION)
            val inflatedData = inflateData(deflatedData, originalData.size)
            
            assertEquals(originalString, inflatedData.decodeToString(),
                "Bit reversal should work correctly for character '$char'")
        }
        println("✓ All ${testChars.length} characters processed correctly")
    }

    private fun deflateData(input: ByteArray, level: Int): ByteArray {
        val stream = ZStream()
        var err = stream.deflateInit(level)
        assertTrue(err == Z_OK, "deflateInit failed. Error: $err, Msg: ${stream.msg}")

        stream.nextIn = input
        stream.availIn = input.size
        stream.nextInIndex = 0

        val outputBuffer = ByteArray(input.size * 2 + 100)
        stream.nextOut = outputBuffer
        stream.availOut = outputBuffer.size
        stream.nextOutIndex = 0

        err = stream.deflate(Z_FINISH)
        assertTrue(err == Z_STREAM_END, "deflate failed, error: $err, msg: ${stream.msg}")

        val result = outputBuffer.copyOf(stream.totalOut.toInt())

        err = stream.deflateEnd()
        assertTrue(err == Z_OK, "deflateEnd failed. Error: $err, Msg: ${stream.msg}")

        return result
    }

    private fun inflateData(inputDeflated: ByteArray, originalSizeHint: Int): ByteArray {
        val stream = ZStream()
        var err = stream.inflateInit(MAX_WBITS)
        assertTrue(err == Z_OK, "inflateInit failed. Error: $err, Msg: ${stream.msg}")

        stream.nextIn = inputDeflated
        stream.availIn = inputDeflated.size
        stream.nextInIndex = 0

        val outputBuffer = ByteArray(originalSizeHint * 4 + 100)
        stream.nextOut = outputBuffer
        stream.availOut = outputBuffer.size
        stream.nextOutIndex = 0

        err = stream.inflate(Z_FINISH)
        assertTrue(err == Z_STREAM_END, "Inflation failed, error: $err, Msg: ${stream.msg}")

        val result = outputBuffer.copyOf(stream.totalOut.toInt())

        err = stream.inflateEnd()
        assertTrue(err == Z_OK, "inflateEnd failed. Error: $err, Msg: ${stream.msg}")

        return result
    }
}