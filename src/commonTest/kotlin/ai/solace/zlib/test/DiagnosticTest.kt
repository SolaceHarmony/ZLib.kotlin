package ai.solace.zlib.test

import ai.solace.zlib.deflate.ZStream
import ai.solace.zlib.common.*
import kotlin.test.*

/**
 * Diagnostic test to troubleshoot multi-character decompression issue
 */
class DiagnosticTest {

    @Test
    fun testWithLoggingEnabled() {
        // Enable logging to create zlib.log
        ZlibLogger.ENABLE_LOGGING = true
        ZlibLogger.DEBUG_ENABLED = true
        
        println("=== Testing Single Character 'A' ===")
        try {
            val singleResult = testCompression("A")
            println("Single char result: $singleResult")
        } catch (e: Exception) {
            println("Single char failed: ${e.message}")
        }
        
        println("\n=== Testing Multi Character 'AB' ===")
        try {
            val multiResult = testCompression("AB")
            println("Multi char result: $multiResult")
        } catch (e: Exception) {
            println("Multi char failed: ${e.message}")
            e.printStackTrace()
        }
        
        // Disable logging after test
        ZlibLogger.ENABLE_LOGGING = false
        ZlibLogger.DEBUG_ENABLED = false
    }
    
    private fun testCompression(input: String): String {
        val originalData = input.encodeToByteArray()
        println("Testing: '$input' (${originalData.size} bytes)")
        
        // Compress
        val compressed = deflateData(originalData, Z_DEFAULT_COMPRESSION)
        println("Compressed: ${compressed.size} bytes - ${compressed.joinToString(" ") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }}")
        
        // Decompress
        val decompressed = inflateData(compressed, originalData.size)
        val result = decompressed.decodeToString()
        println("Decompressed: '$result' (${decompressed.size} bytes)")
        
        return result
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