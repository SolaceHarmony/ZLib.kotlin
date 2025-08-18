package ai.solace.zlib.test

import ai.solace.zlib.deflate.ZStream
import ai.solace.zlib.common.*
import kotlin.test.*

/**
 * Test cases specifically for window wrap-around and long-distance copies in inflate operations.
 * These tests target the potential inflate/deflate failure issue where boundary checks
 * incorrectly flag valid streams as errors.
 */
class InflateWindowWrapTest {

    /**
     * Test window wrap-around during distance copying.
     * This test creates a scenario where copy operations need to wrap around the window buffer.
     */
    @Test
    fun testWindowWrapAroundCopy() {
        // Create test data that will result in window wrap-around during copy
        val originalData = "Hello World! ".repeat(100) // Create repetitive data that compresses well
        val input = originalData.encodeToByteArray()

        // Compress the data
        val compressedData = compressData(input)
        assertTrue(compressedData.isNotEmpty(), "Compression should produce output")

        // Decompress and verify
        val decompressed = decompressData(compressedData)
        assertEquals(input.decodeToString(), decompressed.decodeToString(),
            "Decompressed data should match original")
    }

    /**
     * Test long-distance copy operations.
     * This test creates data that requires copying from earlier positions in the window.
     */
    @Test
    fun testLongDistanceCopy() {
        // Create data with patterns that require long-distance copies
        val pattern = "ABCDEFGHIJKLMNOP"
        val originalData = pattern + "X".repeat(1000) + pattern // Pattern, filler, then repeat pattern
        val input = originalData.encodeToByteArray()

        // Compress the data
        val compressedData = compressData(input)
        assertTrue(compressedData.isNotEmpty(), "Compression should produce output")

        // Decompress and verify
        val decompressed = decompressData(compressedData)
        assertEquals(input.decodeToString(), decompressed.decodeToString(),
            "Decompressed data should match original with long-distance copies")
    }

    /**
     * Test edge case where copy distance equals window end.
     * This targets the specific boundary check issue in InfCodes.kt line 875.
     */
    @Test
    fun testCopyDistanceAtWindowBoundary() {
        // Create data that will produce a copy distance exactly at the window boundary
        val pattern = "ABC"
        val data = pattern.repeat(2000) // Create enough repetition to fill window
        val input = data.encodeToByteArray()

        // Compress the data
        val compressedData = compressData(input)
        assertTrue(compressedData.isNotEmpty(), "Compression should produce output")

        // Decompress and verify
        val decompressed = decompressData(compressedData)
        assertEquals(input.decodeToString(), decompressed.decodeToString(),
            "Decompressed data should handle boundary copy distances correctly")
    }

    private fun compressData(input: ByteArray): ByteArray {
        val stream = ZStream()
        val err = stream.deflateInit(Z_DEFAULT_COMPRESSION)
        assertTrue(err == Z_OK, "deflateInit should succeed, got: $err")

        stream.nextIn = input
        stream.availIn = input.size

        val outputBuffer = ByteArray(input.size * 2 + 20)
        stream.nextOut = outputBuffer
        stream.availOut = outputBuffer.size

        val deflateErr = stream.deflate(Z_FINISH)
        assertTrue(deflateErr == Z_STREAM_END, "deflate should complete successfully, got: $deflateErr")

        val compressed = outputBuffer.copyOf(stream.totalOut.toInt())

        val endErr = stream.deflateEnd()
        assertTrue(endErr == Z_OK, "deflateEnd should succeed, got: $endErr")

        return compressed
    }

    private fun decompressData(compressed: ByteArray): ByteArray {
        val stream = ZStream()
        val err = stream.inflateInit()
        assertTrue(err == Z_OK, "inflateInit should succeed, got: $err")

        stream.nextIn = compressed
        stream.availIn = compressed.size

        val outputBuffer = ByteArray(compressed.size * 4 + 100)
        stream.nextOut = outputBuffer
        stream.availOut = outputBuffer.size

        val inflateErr = stream.inflate(Z_FINISH)
        assertTrue(inflateErr == Z_STREAM_END, "inflate should complete successfully, got: $inflateErr, msg: ${stream.msg}")

        val decompressed = outputBuffer.copyOf(stream.totalOut.toInt())

        val endErr = stream.inflateEnd()
        assertTrue(endErr == Z_OK, "inflateEnd should succeed, got: $endErr")

        return decompressed
    }
}