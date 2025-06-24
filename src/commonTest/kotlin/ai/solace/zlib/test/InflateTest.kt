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

    private fun inflateDataInternal(inputDeflated: ByteArray, originalSizeHint: Int): ByteArray {
        try {
            println("[DEBUG_LOG] Starting inflateDataInternal")
            println("[DEBUG_LOG] inputDeflated size: ${inputDeflated.size}")
            println("[DEBUG_LOG] originalSizeHint: $originalSizeHint")

            val stream = ZStream()

            // Default window bits for zlib is 15 (MAX_WBITS)
            println("[DEBUG_LOG] Calling inflateInit")
            var err = stream.inflateInit(MAX_WBITS)
            println("[DEBUG_LOG] inflateInit result: $err, msg: ${stream.msg}")
            assertTrue(err == Z_OK, "inflateInit failed. Error: $err, Msg: ${stream.msg}")

            stream.nextIn = inputDeflated
            stream.availIn = inputDeflated.size
            stream.nextInIndex = 0

            val outputBuffer = ByteArray(originalSizeHint * 2 + 100) // Ensure buffer is large enough
            stream.nextOut = outputBuffer
            stream.availOut = outputBuffer.size
            stream.nextOutIndex = 0

            println("[DEBUG_LOG] Starting inflation loop")
            // Loop to fully inflate the data
            var loopCount = 0
            do {
                println("[DEBUG_LOG] Inflation loop iteration: ${++loopCount}")
                println("[DEBUG_LOG] Before inflate: availIn=${stream.availIn}, nextInIndex=${stream.nextInIndex}, availOut=${stream.availOut}, nextOutIndex=${stream.nextOutIndex}")

                err = stream.inflate(Z_NO_FLUSH)

                println("[DEBUG_LOG] After inflate: result=$err, msg=${stream.msg}, availIn=${stream.availIn}, nextInIndex=${stream.nextInIndex}, availOut=${stream.availOut}, nextOutIndex=${stream.nextOutIndex}")

                // Z_BUF_ERROR is ok if we are out of output space, but this test assumes enough space.
                // Z_OK means progress was made.
                // Z_STREAM_END means we are done.
                if (err < Z_OK && err != Z_BUF_ERROR) {
                    println("[DEBUG_LOG] Inflation failed with error: $err, msg: ${stream.msg}")
                    assertTrue(false, "Inflation failed with unexpected error: $err, msg: ${stream.msg}")
                }
            } while (err != Z_STREAM_END)

            val inflatedSize = stream.totalOut.toInt()
            println("[DEBUG_LOG] Inflation completed. Total output size: $inflatedSize")
            val result = outputBuffer.copyOf(inflatedSize)

            println("[DEBUG_LOG] Calling inflateEnd")
            err = stream.inflateEnd()
            println("[DEBUG_LOG] inflateEnd result: $err, msg: ${stream.msg}")
            assertTrue(err == Z_OK, "inflateEnd failed. Error: $err, Msg: ${stream.msg}")

            println("[DEBUG_LOG] inflateDataInternal completed successfully")
            return result
        } catch (e: Exception) {
            println("[DEBUG_LOG] Exception in inflateDataInternal: ${e.message}")
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
}
