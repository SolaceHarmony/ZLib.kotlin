package ai.solace.zlib.test

import ai.solace.zlib.deflate.ZStream
import ai.solace.zlib.common.*
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class BlockLengthTest {

    @Test
    fun testStoredBlockLength() {
        // Create a simple string to compress
        val originalString = "Test"
        val originalData = originalString.encodeToByteArray()

        // Create a ZStream for deflation
        val deflateStream = ZStream()
        var err = deflateStream.deflateInit(Z_DEFAULT_COMPRESSION) // Use default compression
        assertTrue(err == Z_OK, "deflateInit failed. Error: $err, Msg: ${deflateStream.msg}")

        // Set up input
        deflateStream.nextIn = originalData
        deflateStream.availIn = originalData.size

        // Set up output buffer
        val outputBuffer = ByteArray(100) // Plenty of space
        deflateStream.nextOut = outputBuffer
        deflateStream.availOut = outputBuffer.size

        // Deflate
        err = deflateStream.deflate(Z_FINISH)
        assertTrue(err == Z_STREAM_END, "deflate failed. Error: $err, Msg: ${deflateStream.msg}")

        // Get the compressed data
        val compressedSize = deflateStream.totalOut.toInt()
        val compressedData = outputBuffer.copyOf(compressedSize)

        // Clean up deflate stream
        deflateStream.deflateEnd()

        // Now inflate the data
        val inflateStream = ZStream()
        err = inflateStream.inflateInit(MAX_WBITS)
        assertTrue(err == Z_OK, "inflateInit failed. Error: $err, Msg: ${inflateStream.msg}")

        // Set up input
        inflateStream.nextIn = compressedData
        inflateStream.availIn = compressedData.size

        // Set up output buffer
        val inflatedBuffer = ByteArray(100) // Plenty of space
        inflateStream.nextOut = inflatedBuffer
        inflateStream.availOut = inflatedBuffer.size

        // Inflate
        println("[DEBUG_LOG] Starting inflation")
        do {
            err = inflateStream.inflate(Z_FINISH)
            println("[DEBUG_LOG] Inflate result: $err, msg: ${inflateStream.msg}")

            // If we get an error other than Z_BUF_ERROR, fail the test
            if (err < Z_OK && err != Z_BUF_ERROR) {
                assertTrue(false, "Inflation failed with unexpected error: $err, msg: ${inflateStream.msg}")
            }
        } while (err != Z_STREAM_END)

        // Get the inflated data
        val inflatedSize = inflateStream.totalOut.toInt()
        val inflatedData = inflatedBuffer.copyOf(inflatedSize)

        // Clean up inflate stream
        inflateStream.inflateEnd()

        // Verify the result
        assertEquals(originalString, inflatedData.decodeToString(), "Inflated data does not match original")
    }
}
