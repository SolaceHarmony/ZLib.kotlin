package ai.solace.zlib.test

import ai.solace.zlib.deflate.ZStream // Inflate class not directly used, ZStream handles it
import ai.solace.zlib.common.*
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class InflateTest {

    // Helper function to deflate data (used for test setup)
    private fun deflateDataForTestSetup(input: ByteArray, level: Int): ByteArray {
        val stream = ZStream()
        var err = stream.deflateInit(level)
        assertTrue(err == Z_OK, "Test setup deflateInit failed. Error: $err, Msg: ${stream.msg}")

        stream.nextIn = input
        stream.availIn = input.size

        // Ensure buffer is large enough for this simple test case
        val outputBuffer = ByteArray(input.size * 2 + 20)
        stream.nextOut = outputBuffer
        stream.availOut = outputBuffer.size

        err = stream.deflate(Z_FINISH)
        assertTrue(err == Z_STREAM_END, "Test setup deflate failed, error: $err, msg: ${stream.msg}")

        val result = outputBuffer.copyOf(stream.totalOut.toInt())

        err = stream.deflateEnd()
        assertTrue(err == Z_OK, "Test setup deflateEnd failed. Error: $err, Msg: ${stream.msg}")
        return result
    }

    // Convert a hex string to a byte array for reference compressed data
    private fun hexStringToByteArray(hex: String): ByteArray {
        val len = hex.length
        val out = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            out[i / 2] = hex.substring(i, i + 2).toInt(16).toByte()
            i += 2
        }
        return out
    }

    private fun inflateDataInternal(inputDeflated: ByteArray, originalSizeHint: Int): ByteArray {
        try {

            val stream = ZStream()

            // Default window bits for zlib is 15 (MAX_WBITS)
            var err = stream.inflateInit(MAX_WBITS)
            assertTrue(err == Z_OK, "inflateInit failed. Error: $err, Msg: ${stream.msg}")

            stream.nextIn = inputDeflated
            stream.availIn = inputDeflated.size
            stream.nextInIndex = 0

            val outputBuffer = ByteArray(originalSizeHint * 4 + 200) // Increased buffer size for safety
            stream.nextOut = outputBuffer
            stream.availOut = outputBuffer.size
            stream.nextOutIndex = 0

            // Loop to fully inflate the data
            var loopCount = 0
            var lastInIndex: Int
            var lastOutIndex: Int

            do {

                // Store current positions to detect stalls
                lastInIndex = stream.nextInIndex
                lastOutIndex = stream.nextOutIndex

                err = stream.inflate(Z_NO_FLUSH)


                // Z_BUF_ERROR is normally ok, but if we're not making progress, we need to break the loop
                if (err == Z_BUF_ERROR &&
                    lastInIndex == stream.nextInIndex &&
                    lastOutIndex == stream.nextOutIndex) {
                    break
                }

                // Z_OK means progress was made.
                // Z_STREAM_END means we are done.
                if (err < Z_OK && err != Z_BUF_ERROR) {
                    val errorMsg = """
                        [DEBUG_LOG] Inflation failed with error: $err, msg: ${stream.msg}
                        [DEBUG_LOG] Error code details: Z_STREAM_ERROR=$Z_STREAM_ERROR, Z_DATA_ERROR=$Z_DATA_ERROR, Z_MEM_ERROR=$Z_MEM_ERROR
                        [DEBUG_LOG] Input data (full): ${inputDeflated.joinToString(", ") { it.toString() }}
                        [DEBUG_LOG] Expected output size: $originalSizeHint
                        [DEBUG_LOG] Partial output so far (first 50 chars): ${outputBuffer.copyOf(stream.totalOut.toInt()).decodeToString().take(50)}
                    """.trimIndent()
                    println(errorMsg)
                    // Intentionally fail with the error message
                    assertTrue(false, "Inflation failed with unexpected error: $err, msg: ${stream.msg}")
                }

                // Add a safety check to prevent extremely long loops
                if (loopCount > 1000) {
                    assertTrue(false, "Inflation appears to be in an infinite loop after $loopCount iterations")
                }

            } while (err != Z_STREAM_END)

            val inflatedSize = stream.totalOut.toInt()
            val result = outputBuffer.copyOf(inflatedSize)

            err = stream.inflateEnd()
            assertTrue(err == Z_OK, "inflateEnd failed. Error: $err, Msg: ${stream.msg}")

            return result
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }

    @Test
    fun basicInflationTest() {
        val originalString = "Hello World Hello World Hello World Hello World Hello World Hello World Hello World Hello World Hello World Hello World"
        val originalData = originalString.encodeToByteArray()

        val deflatedData = deflateDataForTestSetup(originalData, Z_DEFAULT_COMPRESSION)
        assertTrue(deflatedData.isNotEmpty(), "Deflated data for test setup is empty")
        assertTrue(!originalData.contentEquals(deflatedData), "Deflated data matches original in setup")

        val inflatedData = inflateDataInternal(deflatedData, originalData.size)

        assertTrue(inflatedData.isNotEmpty(), "Inflated data should not be empty")
        assertEquals(originalString, inflatedData.decodeToString(), "Inflated data does not match original string")
        assertTrue(originalData.contentEquals(inflatedData), "Inflated data does not match original byte array")
    }

    @Test
    fun inflateNoCompressionDataTest() {
        val originalString = "Test data with no compression and some length to ensure it's processed."
        val originalData = originalString.encodeToByteArray()

        val deflatedNoCompression = deflateDataForTestSetup(originalData, Z_NO_COMPRESSION)
        assertTrue(deflatedNoCompression.isNotEmpty(), "Deflated (no compression) data is empty")

        val inflatedData = inflateDataInternal(deflatedNoCompression, originalData.size)

        assertEquals(originalString, inflatedData.decodeToString(), "Inflated Z_NO_COMPRESSION data mismatch")
    }

    @Test
    fun minimalInputDataTest() {
        println("[DEBUG] Testing with minimal input data for compression and decompression.")

        val originalString = "A"
        val originalData = originalString.encodeToByteArray()

        val deflatedData = deflateDataForTestSetup(originalData, Z_DEFAULT_COMPRESSION)
        assertTrue(deflatedData.isNotEmpty(), "Deflated data for minimal input is empty")

        val inflatedData = inflateDataInternal(deflatedData, originalData.size)

        assertTrue(inflatedData.isNotEmpty(), "Inflated data for minimal input should not be empty")
        assertEquals(originalString, inflatedData.decodeToString(), "Inflated data for minimal input does not match original string")
        assertTrue(originalData.contentEquals(inflatedData), "Inflated data for minimal input does not match original byte array")
    }

    @Test
    fun referenceInflationCompatibilityTest() {
        val compressedHex = "789ccb48cdc9c95728cf2fca4951c8406227e7e71614a51617a7a628a42496242a2467a4266703008a7f1106"
        val compressedData = hexStringToByteArray(compressedHex)
        val expectedString = "hello world hello world compressed data check"
        val result = inflateDataInternal(compressedData, expectedString.length)
        assertEquals(expectedString, result.decodeToString(), "Inflation output mismatched reference implementation")
    }
}
