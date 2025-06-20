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

        stream.next_in = input
        stream.avail_in = input.size

        // Ensure buffer is large enough for this simple test case
        val outputBuffer = ByteArray(input.size * 2 + 20)
        stream.next_out = outputBuffer
        stream.avail_out = outputBuffer.size

        err = stream.deflate(Z_FINISH)
        assertTrue(err == Z_STREAM_END, "Test setup deflate failed, error: $err, msg: ${stream.msg}")

        val result = outputBuffer.copyOf(stream.total_out.toInt())

        err = stream.deflateEnd()
        assertTrue(err == Z_OK, "Test setup deflateEnd failed. Error: $err, Msg: ${stream.msg}")
        return result
    }

    private fun inflateDataInternal(inputDeflated: ByteArray, originalSizeHint: Int): ByteArray {
        val stream = ZStream()

        // Default window bits for zlib is 15 (MAX_WBITS)
        var err = stream.inflateInit(MAX_WBITS)
        assertTrue(err == Z_OK, "inflateInit failed. Error: $err, Msg: ${stream.msg}")

        stream.next_in = inputDeflated
        stream.avail_in = inputDeflated.size
        stream.next_in_index = 0

        val outputBuffer = ByteArray(originalSizeHint * 2 + 100) // Ensure buffer is large enough
        stream.next_out = outputBuffer
        stream.avail_out = outputBuffer.size
        stream.next_out_index = 0

        var loopCount = 0 // Safety break
        // Loop for inflation is needed if output buffer might be too small or input is chunked.
        // For this test, we provide one large chunk and expect one Z_STREAM_END.
        // A more robust general purpose inflate would loop on Z_OK and Z_BUF_ERROR if output buffer needs expansion.
        err = stream.inflate(Z_NO_FLUSH) // Z_NO_FLUSH is typical until all input is consumed or Z_STREAM_END is expected.
                                        // Z_FINISH can also be used if an early end is desired.

        // Check if stream ended. If not, and avail_in is 0, it means more output buffer might be needed,
        // or it's a Z_BUF_ERROR for other reasons.
        // This simplified test expects Z_STREAM_END in one go if input is complete and output buffer is sufficient.
        assertTrue(err == Z_STREAM_END, "inflate did not return Z_STREAM_END on first call. error: $err, msg: ${stream.msg}, avail_in: ${stream.avail_in}, avail_out: ${stream.avail_out}")

        val inflatedSize = stream.total_out.toInt()
        val result = outputBuffer.copyOf(inflatedSize)

        err = stream.inflateEnd()
        assertTrue(err == Z_OK, "inflateEnd failed. Error: $err, Msg: ${stream.msg}")

        return result
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
