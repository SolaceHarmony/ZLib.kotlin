package ai.solace.zlib.test

import ai.solace.zlib.deflate.ZStream
import ai.solace.zlib.common.*
import kotlin.test.*

/**
 * Additional test to verify the spurious literal fix works with different compression levels.
 * This test specifically targets the compression levels that use deflateSlow (levels 4-9).
 */
class DeflateCompressionLevelTest {

    @Test
    fun testSingleCharacterWithSlowCompression() {
        // Test levels 4-9 which use deflateSlow (lazy matching)
        val slowCompressionLevels = listOf(4, 5, 6, 7, 8, 9)
        
        for (level in slowCompressionLevels) {
            val originalString = "A"
            val originalData = originalString.encodeToByteArray()

            val deflatedData = deflateData(originalData, level)
            val inflatedData = inflateData(deflatedData, originalData.size)

            assertEquals(originalString, inflatedData.decodeToString(), 
                "Single character 'A' should deflate/inflate correctly at compression level $level (deflateSlow)")
        }
    }

    @Test 
    fun testSingleCharacterWithFastCompression() {
        // Test levels 1-3 which use deflateFast (no lazy matching)
        val fastCompressionLevels = listOf(1, 2, 3)
        
        for (level in fastCompressionLevels) {
            val originalString = "A"
            val originalData = originalString.encodeToByteArray()

            val deflatedData = deflateData(originalData, level)
            val inflatedData = inflateData(deflatedData, originalData.size)

            assertEquals(originalString, inflatedData.decodeToString(),
                "Single character 'A' should deflate/inflate correctly at compression level $level (deflateFast)")
        }
    }

    @Test
    fun testSingleCharacterWithNoCompression() {
        // Test level 0 which uses deflateStored (no compression)
        val originalString = "A"
        val originalData = originalString.encodeToByteArray()

        val deflatedData = deflateData(originalData, Z_NO_COMPRESSION)
        val inflatedData = inflateData(deflatedData, originalData.size)

        assertEquals(originalString, inflatedData.decodeToString(),
            "Single character 'A' should deflate/inflate correctly with no compression (deflateStored)")
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