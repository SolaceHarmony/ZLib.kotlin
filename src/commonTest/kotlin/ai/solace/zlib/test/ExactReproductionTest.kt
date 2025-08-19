package ai.solace.zlib.test

import ai.solace.zlib.deflate.ZStream
import ai.solace.zlib.common.*
import kotlin.test.*

/**
 * Test to reproduce the exact failure from HuffmanFixValidationTest
 */
class ExactReproductionTest {

    @Test
    fun testExactReproduction() {
        println("=== Exact Reproduction Test ===")
        
        // This is exactly the same code as in HuffmanFixValidationTest.testMultipleCharacterCompression
        val testStrings = listOf("AB")  // Just test "AB" first
        
        for (testString in testStrings) {
            println("Testing: '$testString'")
            val originalData = testString.encodeToByteArray()
            
            try {
                val deflatedData = deflateData(originalData, Z_DEFAULT_COMPRESSION)
                println("Deflated: ${deflatedData.size} bytes = [${deflatedData.joinToString(", ") { (it.toInt() and 0xFF).toString() }}]")
                
                val inflatedData = inflateData(deflatedData, originalData.size)
                val result = inflatedData.decodeToString()
                
                assertEquals(testString, result,
                    "Multiple character string '$testString' should compress/decompress correctly")
                assertTrue(originalData.contentEquals(inflatedData),
                    "Byte-level comparison for '$testString' should match exactly")
                println("✅ SUCCESS: '$testString' -> '$result'")
            } catch (e: Exception) {
                println("❌ FAILED: '$testString' -> Exception: ${e.message}")
                throw e  // This should trigger the same failure
            }
        }
    }

    // These are exactly the same methods as in HuffmanFixValidationTest
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