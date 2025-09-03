package ai.solace.zlib.test

import ai.solace.zlib.deflate.ZStream
import ai.solace.zlib.common.*
import kotlin.test.*
import kotlin.test.Ignore

/**
 * Minimal debug test to understand what's failing with multiple characters
 */
@Ignore
class DebugHuffmanTest {

    @Test
    fun testTwoCharacters() {
        println("=== Debug Test: Two Characters 'AB' ===")
        val originalString = "AB"
        val originalData = originalString.encodeToByteArray()
        
        println("Original: '$originalString' = [${originalData.joinToString(", ") { (it.toInt() and 0xFF).toString() }}]")

        try {
            val deflatedData = deflateData(originalData, Z_DEFAULT_COMPRESSION)
            println("Deflated: ${deflatedData.size} bytes = [${deflatedData.joinToString(", ") { (it.toInt() and 0xFF).toString() }}]")
            
            val inflatedData = inflateData(deflatedData, originalData.size)
            val inflatedString = inflatedData.decodeToString()
            println("Inflated: '$inflatedString' = [${inflatedData.joinToString(", ") { (it.toInt() and 0xFF).toString() }}]")
            
            if (originalString == inflatedString) {
                println("✅ SUCCESS")
            } else {
                println("❌ FAILURE: Expected '$originalString', got '$inflatedString'")
                fail("Two character test failed")
            }
        } catch (e: Exception) {
            println("❌ ERROR: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }

    private fun deflateData(input: ByteArray, level: Int): ByteArray {
        val stream = ZStream()
        var err = stream.deflateInit(level)
        if (err != Z_OK) {
            println("deflateInit failed: $err, ${stream.msg}")
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
            println("deflate failed: $err, ${stream.msg}")
            throw RuntimeException("deflate failed: $err, ${stream.msg}")
        }

        val result = outputBuffer.copyOf(stream.totalOut.toInt())

        err = stream.deflateEnd()
        if (err != Z_OK) {
            println("deflateEnd failed: $err, ${stream.msg}")
            throw RuntimeException("deflateEnd failed: $err, ${stream.msg}")
        }

        return result
    }

    private fun inflateData(inputDeflated: ByteArray, originalSizeHint: Int): ByteArray {
        val stream = ZStream()
        var err = stream.inflateInit(MAX_WBITS)
        if (err != Z_OK) {
            println("inflateInit failed: $err, ${stream.msg}")
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
            println("inflate failed: $err, ${stream.msg}")
            throw RuntimeException("inflate failed: $err, ${stream.msg}")
        }

        val result = outputBuffer.copyOf(stream.totalOut.toInt())

        err = stream.inflateEnd()
        if (err != Z_OK) {
            println("inflateEnd failed: $err, ${stream.msg}")
            throw RuntimeException("inflateEnd failed: $err, ${stream.msg}")
        }

        return result
    }
}
