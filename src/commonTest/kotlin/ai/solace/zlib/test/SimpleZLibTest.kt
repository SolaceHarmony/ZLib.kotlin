package ai.solace.zlib.test

import ai.solace.zlib.deflate.ZStream
import ai.solace.zlib.common.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A simple test class to directly test compression and decompression
 * with detailed logging to identify issues.
 */
class SimpleZLibTest {
    
    @Test
    fun testMinimalInput() {
        println("Starting simple ZLib test...")
        
        // Test with minimal input - a single character
        val originalString = "A"
        val originalData = originalString.encodeToByteArray()
        
        println("Original data: '$originalString' (${originalData.size} bytes)")
        
        // Compress the data
        val deflatedData = deflateData(originalData)
        println("Compressed data size: ${deflatedData.size} bytes")
        
        // Print the compressed data in hex format
        println("Compressed data (hex): ${deflatedData.joinToString("") { 
            val hex = (it.toInt() and 0xFF).toString(16).uppercase()
            if (hex.length == 1) "0$hex" else hex 
        }}")
        
        // Decompress the data
        val inflatedData = inflateData(deflatedData, originalData.size)
        
        // Check if decompression was successful
        if (inflatedData.isNotEmpty()) {
            val inflatedString = inflatedData.decodeToString()
            println("Decompressed data: '$inflatedString' (${inflatedData.size} bytes)")
            
            assertEquals(originalString, inflatedString, "Decompressed data does not match original string")
            assertTrue(originalData.contentEquals(inflatedData), "Decompressed data does not match original byte array")
        } else {
            assertTrue(false, "Decompression failed, no data returned")
        }
    }
    
    /**
     * Compress the given data using ZStream.
     */
    private fun deflateData(input: ByteArray): ByteArray {
        println("Deflating data...")
        
        val stream = ZStream()
        var err = stream.deflateInit(Z_DEFAULT_COMPRESSION)
        println("deflateInit returned: $err, msg=${stream.msg}")
        
        stream.nextIn = input
        stream.availIn = input.size
        
        val outputBuffer = ByteArray(input.size * 2 + 20) // Ensure buffer is large enough
        stream.nextOut = outputBuffer
        stream.availOut = outputBuffer.size
        
        println("Before deflate: availIn=${stream.availIn}, nextInIndex=${stream.nextInIndex}, availOut=${stream.availOut}")
        err = stream.deflate(Z_FINISH)
        println("deflate returned: $err, msg=${stream.msg}, totalOut=${stream.totalOut}")
        
        if (err != Z_STREAM_END) {
            println("deflate did not return Z_STREAM_END, checking stream state")
            println("Stream state: availIn=${stream.availIn}, nextInIndex=${stream.nextInIndex}, availOut=${stream.availOut}, nextOutIndex=${stream.nextOutIndex}")
        }
        
        val result = outputBuffer.copyOf(stream.totalOut.toInt())
        
        err = stream.deflateEnd()
        println("deflateEnd returned: $err, msg=${stream.msg}")
        
        return result
    }
    
    /**
     * Decompress the given data using ZStream.
     */
    private fun inflateData(input: ByteArray, originalSize: Int): ByteArray {
        println("Inflating data...")
        
        val stream = ZStream()
        var err = stream.inflateInit()
        println("inflateInit returned: $err, msg=${stream.msg}")
        
        stream.nextIn = input
        stream.availIn = input.size
        stream.nextInIndex = 0
        
        val outputBuffer = ByteArray(originalSize * 4 + 200) // Ensure buffer is large enough
        stream.nextOut = outputBuffer
        stream.availOut = outputBuffer.size
        stream.nextOutIndex = 0
        
        println("Before inflate: availIn=${stream.availIn}, nextInIndex=${stream.nextInIndex}, availOut=${stream.availOut}")
        
        // Print the input data in hex format
        println("Input data (hex): ${input.joinToString("") { 
            val hex = (it.toInt() and 0xFF).toString(16).uppercase()
            if (hex.length == 1) "0$hex" else hex 
        }}")
        
        err = stream.inflate(Z_FINISH)
        println("inflate returned: $err, msg=${stream.msg}, totalOut=${stream.totalOut}")
        
        if (err != Z_STREAM_END) {
            println("inflate did not return Z_STREAM_END, checking stream state")
            println("Stream state: availIn=${stream.availIn}, nextInIndex=${stream.nextInIndex}, availOut=${stream.availOut}, nextOutIndex=${stream.nextOutIndex}")
            println("Stream internal state: iState=${stream.iState}, dState=${stream.dState}, adler=${stream.adler}")
            
            // If there was an error, return an empty array
            return ByteArray(0)
        }
        
        val result = outputBuffer.copyOf(stream.totalOut.toInt())
        
        err = stream.inflateEnd()
        println("inflateEnd returned: $err, msg=${stream.msg}")
        
        return result
    }
}