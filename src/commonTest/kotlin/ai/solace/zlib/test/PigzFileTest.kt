package ai.solace.zlib.test

import ai.solace.zlib.deflate.ZStream
import ai.solace.zlib.common.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PigzFileTest {
    
    @Test
    fun testDecompressPigzFile() {
        // Read the compressed file
        val compressedBytes = readCompressedFile("test_input.txt.zz")
        println("[DEBUG_LOG] Read ${compressedBytes.size} bytes from test_input.txt.zz")
        
        // Print the first few bytes for debugging
        if (compressedBytes.isNotEmpty()) {
            val bytesToShow = minOf(compressedBytes.size, 20)
            println("[DEBUG_LOG] First $bytesToShow bytes: ${compressedBytes.slice(0 until bytesToShow).joinToString(", ") { "0x" + (it.toInt() and 0xFF).toString(16).padStart(2, '0').uppercase() }}")
        }
        
        // Decompress the file
        val decompressedBytes = decompressZlibData(compressedBytes)
        
        // Convert to string and print
        val decompressedString = decompressedBytes.decodeToString()
        println("[DEBUG_LOG] Decompressed string: $decompressedString")
        
        // Verify the result
        val expectedString = "Hello World Hello World Hello World Hello World Hello World"
        assertEquals(expectedString, decompressedString, "Decompressed content does not match expected string")
    }
    
    private fun readCompressedFile(filename: String): ByteArray {
        // In a real test, we would read from the file system
        // For this test, we'll use a hardcoded byte array representing the file content
        // This is the content of test_input.txt.zz
        return byteArrayOf(
            0x78.toByte(), 0x5e.toByte(), 0xf3.toByte(), 0x48.toByte(), 0xcd.toByte(), 0xc9.toByte(), 0xc9.toByte(), 0x57.toByte(),
            0x08.toByte(), 0xcf.toByte(), 0x2f.toByte(), 0xca.toByte(), 0x49.toByte(), 0x51.toByte(), 0xf0.toByte(), 0x20.toByte(),
            0x8d.toByte(), 0xcd.toByte(), 0x05.toByte(), 0x00.toByte(), 0x89.toByte(), 0x90.toByte(), 0x15.toByte(), 0x17.toByte()
        )
    }
    
    private fun decompressZlibData(compressedData: ByteArray): ByteArray {
        val stream = ZStream()
        
        // Initialize for inflation
        var err = stream.inflateInit()
        println("[DEBUG_LOG] inflateInit returned: $err, msg=${stream.msg}")
        assertTrue(err == Z_OK, "inflateInit failed. Error: $err, Msg: ${stream.msg}")
        
        // Set up input
        stream.nextIn = compressedData
        stream.availIn = compressedData.size
        stream.nextInIndex = 0
        
        // Prepare output buffer - make it larger than we expect to need
        val outputBuffer = ByteArray(1024)
        stream.nextOut = outputBuffer
        stream.availOut = outputBuffer.size
        stream.nextOutIndex = 0
        
        // Inflate the data
        println("[DEBUG_LOG] Before inflate: availIn=${stream.availIn}, nextInIndex=${stream.nextInIndex}, availOut=${stream.availOut}")
        err = stream.inflate(Z_FINISH)
        println("[DEBUG_LOG] inflate returned: $err, msg=${stream.msg}, totalOut=${stream.totalOut}")
        
        // Check for errors
        if (err != Z_STREAM_END) {
            println("[DEBUG_LOG] inflate did not return Z_STREAM_END, checking stream state")
            println("[DEBUG_LOG] Stream state: availIn=${stream.availIn}, nextInIndex=${stream.nextInIndex}, availOut=${stream.availOut}, nextOutIndex=${stream.nextOutIndex}")
            println("[DEBUG_LOG] Stream internal state: dState=${stream.dState}, iState=${stream.iState}, adler=${stream.adler}")
        }
        
        assertTrue(err == Z_STREAM_END, "Inflation failed, error: $err, msg: ${stream.msg}")
        
        // Clean up
        err = stream.inflateEnd()
        println("[DEBUG_LOG] inflateEnd returned: $err, msg=${stream.msg}")
        assertTrue(err == Z_OK, "inflateEnd failed. Error: $err, Msg: ${stream.msg}")
        
        // Return the decompressed data
        return outputBuffer.copyOf(stream.totalOut.toInt())
    }
}