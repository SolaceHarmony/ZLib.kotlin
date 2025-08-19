package ai.solace.zlib.test

import ai.solace.zlib.deflate.ZStream
import ai.solace.zlib.common.*
import kotlin.test.*

/**
 * Test to identify remaining issues after Huffman fix.
 * Focus on the specific failures we've seen.
 */
class RemainingIssuesTest {

    @Test
    fun testCompressionLevel0() {
        println("=== Testing Compression Level 0 (No Compression) ===")
        // Level 0 uses stored blocks, not Huffman coding
        // This should work regardless of Huffman fix
        val originalString = "A"
        val originalData = originalString.encodeToByteArray()
        
        println("Testing single 'A' with Z_NO_COMPRESSION...")
        try {
            val deflatedData = deflateData(originalData, Z_NO_COMPRESSION)
            println("Deflated: ${deflatedData.size} bytes = [${deflatedData.joinToString(", ") { (it.toInt() and 0xFF).toString() }}]")
            
            val inflatedData = inflateData(deflatedData, originalData.size)
            val inflatedString = inflatedData.decodeToString()
            println("Inflated: '$inflatedString'")
            
            assertEquals(originalString, inflatedString, "Level 0 compression should work")
            println("✅ Level 0 works")
        } catch (e: Exception) {
            println("❌ Level 0 failed: ${e.message}")
            throw e
        }
    }

    @Test
    fun testCompressionLevel1() {
        println("=== Testing Compression Level 1 (Minimal Huffman) ===")
        val originalString = "A"
        val originalData = originalString.encodeToByteArray()
        
        println("Testing single 'A' with level 1...")
        try {
            val deflatedData = deflateData(originalData, 1)
            println("Deflated: ${deflatedData.size} bytes = [${deflatedData.joinToString(", ") { (it.toInt() and 0xFF).toString() }}]")
            
            val inflatedData = inflateData(deflatedData, originalData.size)
            val inflatedString = inflatedData.decodeToString()
            println("Inflated: '$inflatedString'")
            
            assertEquals(originalString, inflatedString, "Level 1 compression should work")
            println("✅ Level 1 works")
        } catch (e: Exception) {
            println("❌ Level 1 failed: ${e.message}")
            throw e
        }
    }

    @Test
    fun testMultipleCharacterAnalysis() {
        println("=== Analyzing Multi-character Issue ===")
        
        // Test why 'AB' fails but 'A' works
        listOf("A", "AB", "ABC").forEach { testString ->
            println("\n--- Testing '$testString' ---")
            val originalData = testString.encodeToByteArray()
            
            try {
                val deflatedData = deflateData(originalData, Z_DEFAULT_COMPRESSION)
                println("Deflated '$testString': ${deflatedData.size} bytes = [${deflatedData.joinToString(" ") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }}]")
                
                val inflatedData = inflateData(deflatedData, originalData.size)
                val result = inflatedData.decodeToString()
                
                if (testString == result) {
                    println("✅ '$testString' -> '$result' SUCCESS")
                } else {
                    println("❌ '$testString' -> '$result' FAILED")
                }
            } catch (e: Exception) {
                println("❌ '$testString' EXCEPTION: ${e.message}")
                if (testString == "A") {
                    // If 'A' fails, that's a regression
                    throw e
                } else {
                    // If multi-char fails, that's the issue we need to fix
                    println("This is the multi-character issue we need to fix")
                }
            }
        }
    }

    private fun deflateData(input: ByteArray, level: Int): ByteArray {
        val stream = ZStream()
        var err = stream.deflateInit(level)
        if (err != Z_OK) {
            throw RuntimeException("deflateInit failed: $err, ${stream.msg}")
        }

        stream.nextIn = input
        stream.availIn = input.size
        stream.nextInIndex = 0

        val outputBuffer = ByteArray(input.size * 2 + 100)
        stream.nextOut = outputBuffer
        stream.availOut = outputBuffer.size
        stream.nextOutIndex = 0

        err = stream.deflate(Z_FINISH)
        if (err != Z_STREAM_END) {
            throw RuntimeException("deflate failed: $err, ${stream.msg}")
        }

        val result = outputBuffer.copyOf(stream.totalOut.toInt())

        err = stream.deflateEnd()
        if (err != Z_OK) {
            throw RuntimeException("deflateEnd failed: $err, ${stream.msg}")
        }

        return result
    }

    private fun inflateData(inputDeflated: ByteArray, originalSizeHint: Int): ByteArray {
        val stream = ZStream()
        var err = stream.inflateInit(MAX_WBITS)
        if (err != Z_OK) {
            throw RuntimeException("inflateInit failed: $err, ${stream.msg}")
        }

        stream.nextIn = inputDeflated
        stream.availIn = inputDeflated.size
        stream.nextInIndex = 0

        val outputBuffer = ByteArray(originalSizeHint * 4 + 100)
        stream.nextOut = outputBuffer
        stream.availOut = outputBuffer.size
        stream.nextOutIndex = 0

        err = stream.inflate(Z_FINISH)
        if (err != Z_STREAM_END) {
            throw RuntimeException("inflate failed: $err, ${stream.msg}")
        }

        val result = outputBuffer.copyOf(stream.totalOut.toInt())

        err = stream.inflateEnd()
        if (err != Z_OK) {
            throw RuntimeException("inflateEnd failed: $err, ${stream.msg}")
        }

        return result
    }
}