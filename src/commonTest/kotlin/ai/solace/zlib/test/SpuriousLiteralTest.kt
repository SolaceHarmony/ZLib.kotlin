package ai.solace.zlib.test

import ai.solace.zlib.deflate.ZStream
import ai.solace.zlib.common.*
import kotlin.test.*

/**
 * Test specifically for the spurious literal issue described in #22.
 * When deflating a single 'A' character, it should produce only one literal (65)
 * followed by an end-of-block marker, not a spurious literal (70) followed by (65).
 */
class SpuriousLiteralTest {

    @Test
    fun testSingleCharacterDeflationProducesCorrectOutput() {
        val originalString = "A"
        val originalData = originalString.encodeToByteArray()

        // First, deflate the data
        val deflatedData = deflateData(originalData, Z_DEFAULT_COMPRESSION)
        assertTrue(deflatedData.isNotEmpty(), "Deflated data should not be empty")

        // Then, inflate it back
        val inflatedData = inflateData(deflatedData, originalData.size)

        // This is the key assertion: we should get back exactly "A", not "FA"
        assertEquals(originalString, inflatedData.decodeToString(), 
            "Single character deflation/inflation should return the original character, not with spurious prefix")
        assertTrue(originalData.contentEquals(inflatedData), 
            "Inflated byte array should match original exactly")
    }

    @Test
    fun testMinimalInputsForSpuriousLiterals() {
        val testCases = listOf("A", "B", "X", "Z", "1", "9")
        
        for (testChar in testCases) {
            val originalData = testChar.encodeToByteArray()
            val deflatedData = deflateData(originalData, Z_DEFAULT_COMPRESSION)
            val inflatedData = inflateData(deflatedData, originalData.size)
            
            assertEquals(testChar, inflatedData.decodeToString(), 
                "Character '$testChar' should deflate/inflate without spurious literals")
        }
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