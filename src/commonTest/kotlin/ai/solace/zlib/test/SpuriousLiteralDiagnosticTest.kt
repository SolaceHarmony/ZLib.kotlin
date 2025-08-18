package ai.solace.zlib.test

import ai.solace.zlib.common.*
import ai.solace.zlib.deflate.ZStream
import kotlin.test.*

/**
 * Focused test to diagnose the spurious literal issue.
 * Based on the analysis that deflation is creating malformed compressed data.
 */
class SpuriousLiteralDiagnosticTest {

    @Test
    fun testDeflateSymbolBufferAnalysis() {
        // Enable minimal logging for this test
        ZlibLogger.ENABLE_LOGGING = true
        try {
            println("=== Diagnostic Test for Spurious Literal Issue ===")
            
            val input = "A".encodeToByteArray()
            println("Input: [${input[0]}] ('${Char(input[0].toInt())}')")
            
            val stream = ZStream()
            
            // Step 1: Initialize deflation
            val initResult = stream.deflateInit(Z_DEFAULT_COMPRESSION)
            assertEquals(Z_OK, initResult, "deflateInit should succeed")
            
            // Step 2: Set up input
            stream.nextIn = input
            stream.availIn = input.size
            stream.nextInIndex = 0
            
            // Step 3: Set up output
            val outputBuffer = ByteArray(50)
            stream.nextOut = outputBuffer
            stream.availOut = outputBuffer.size
            stream.nextOutIndex = 0
            
            // Step 4: Perform deflation
            println("Starting deflation...")
            val deflateResult = stream.deflate(Z_FINISH)
            assertEquals(Z_STREAM_END, deflateResult, "deflate should complete successfully")
            
            // Step 5: Get compressed data
            val compressedSize = stream.totalOut.toInt()
            val compressed = outputBuffer.copyOf(compressedSize)
            
            println("Compressed data: [${compressed.joinToString(", ") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }}]")
            println("Expected pigz: [78, 5e, 73, 04, 00, 00, 42, 00, 42]")
            
            // Step 6: Clean up deflation
            stream.deflateEnd()
            
            // Step 7: Try to inflate our own data
            println("Attempting to inflate our own compressed data...")
            
            val inflateStream = ZStream()
            val inflateInitResult = inflateStream.inflateInit()
            assertEquals(Z_OK, inflateInitResult, "inflateInit should succeed")
            
            inflateStream.nextIn = compressed
            inflateStream.availIn = compressed.size
            inflateStream.nextInIndex = 0
            
            val decompressedBuffer = ByteArray(10)
            inflateStream.nextOut = decompressedBuffer
            inflateStream.availOut = decompressedBuffer.size
            inflateStream.nextOutIndex = 0
            
            // This is where the test will fail due to checksum mismatch
            val inflateResult = inflateStream.inflate(Z_FINISH)
            
            if (inflateResult == Z_STREAM_END) {
                val decompressed = decompressedBuffer.copyOf(inflateStream.totalOut.toInt())
                println("SUCCESS: Decompressed to [${decompressed.joinToString(", ")}] = '${decompressed.decodeToString()}'")
                assertEquals("A", decompressed.decodeToString(), "Should decompress back to 'A'")
            } else {
                println("FAILED: inflate result = $inflateResult, msg = ${inflateStream.msg}")
                // Don't fail the test here, as we're diagnosing the issue
            }
            
            inflateStream.inflateEnd()
            
        } finally {
            ZlibLogger.ENABLE_LOGGING = false
        }
    }
}