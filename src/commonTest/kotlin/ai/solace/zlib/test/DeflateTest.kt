package ai.solace.zlib.test

import ai.solace.zlib.deflate.ZStream // Deflate class is not directly used by test, ZStream handles it.
import ai.solace.zlib.common.*
import kotlin.test.Test
import kotlin.test.assertTrue

class DeflateTest {

    private fun deflateData(input: ByteArray, level: Int): ByteArray {
        val stream = ZStream()

        var err = stream.deflateInit(level) // Use ZStream's method
        assertTrue(err == Z_OK, "deflateInit failed. Error: $err, Msg: ${stream.msg}")

        stream.nextIn = input
        stream.availIn = input.size
        stream.nextInIndex = 0

        // Initial buffer, might need to be larger or handled with looping for general case
        val outputBufferInitialSize = if (input.isEmpty()) 100 else input.size * 2
        val outputBuffer = ByteArray(outputBufferInitialSize)
        stream.nextOut = outputBuffer
        stream.availOut = outputBuffer.size
        stream.nextOutIndex = 0

        err = stream.deflate(Z_FINISH)
        // For very small inputs or certain compression levels, Z_STREAM_END might not be immediate
        // if output buffer is too small. The current setup assumes outputBuffer is large enough.
        assertTrue(err == Z_STREAM_END, "deflate failed, error: $err, msg: ${stream.msg}")

        val deflatedSize = stream.totalOut.toInt()
        val result = outputBuffer.copyOf(deflatedSize)

        err = stream.deflateEnd()
        assertTrue(err == Z_OK, "deflateEnd failed. Error: $err, Msg: ${stream.msg}")

        return result
    }

    @Test
    fun basicDeflationTest() {
        val inputString = "Hello World Hello World Hello World Hello World Hello World Hello World Hello World Hello World Hello World Hello World"
        val inputData = inputString.encodeToByteArray()

        val deflatedData = deflateData(inputData, Z_DEFAULT_COMPRESSION)

        assertTrue(deflatedData.isNotEmpty(), "Deflated data should not be empty")
        assertTrue(!inputData.contentEquals(deflatedData), "Deflated data should be different from input")
        // This assertion is strong; for some very short or incompressible inputs, deflate might not make it smaller.
        // For "Hello World" repeated many times, it should be smaller.
        assertTrue(deflatedData.size < inputData.size, "Deflated data (size ${deflatedData.size}) should be smaller than input (size ${inputData.size}) for compressible string")
    }

    @Test
    fun differentCompressionLevelsTest() {
        val inputString = "This is a test string for different compression levels. This string has some repetition. This string has some repetition."
        val inputData = inputString.encodeToByteArray()

        val noCompressionData = deflateData(inputData, Z_NO_COMPRESSION)
        val defaultCompressionData = deflateData(inputData, Z_DEFAULT_COMPRESSION)
        val bestCompressionData = deflateData(inputData, Z_BEST_COMPRESSION)

        assertTrue(noCompressionData.isNotEmpty(), "Z_NO_COMPRESSION output should not be empty")
        assertTrue(defaultCompressionData.isNotEmpty(), "Z_DEFAULT_COMPRESSION output should not be empty")
        assertTrue(bestCompressionData.isNotEmpty(), "Z_BEST_COMPRESSION output should not be empty")

        assertTrue(bestCompressionData.size < inputData.size, "Z_BEST_COMPRESSION (size ${bestCompressionData.size}) should be smaller than input (size ${inputData.size})")
        assertTrue(defaultCompressionData.size < inputData.size, "Z_DEFAULT_COMPRESSION (size ${defaultCompressionData.size}) should be smaller than input (size ${inputData.size})")

        assertTrue(bestCompressionData.size <= defaultCompressionData.size,
            "Z_BEST_COMPRESSION (size ${bestCompressionData.size}) should be <= Z_DEFAULT_COMPRESSION (size ${defaultCompressionData.size})")

        // Z_NO_COMPRESSION output includes zlib headers and a checksum, so it's typically slightly larger.
        val overhead = noCompressionData.size - inputData.size
        assertTrue(overhead > 0 && overhead < 20, "Overhead for Z_NO_COMPRESSION (${overhead} bytes) is not within expected small positive range. Output size: ${noCompressionData.size}, Input size: ${inputData.size}")
    }
}
