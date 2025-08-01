package ai.solace.zlib.test

import ai.solace.zlib.deflate.ZStream // Inflate class not directly used, ZStream handles it
import ai.solace.zlib.common.*
import kotlin.test.*

class InflateTest {

    private fun deflateDataForTestSetup(input: ByteArray, level: Int): ByteArray {
        val stream = ZStream()
        var err = stream.deflateInit(level)
        assertTrue(err == Z_OK, "Test setup deflateInit failed. Error: $err, Msg: ${stream.msg}")

        stream.nextIn = input
        stream.availIn = input.size

        val outputBuffer = ByteArray(input.size * 2 + 20)
        stream.nextOut = outputBuffer
        stream.availOut = outputBuffer.size

        err = stream.deflate(Z_FINISH)
        assertTrue(err == Z_STREAM_END, "Test setup deflate failed, error: $err, Msg: ${stream.msg}")

        val result = outputBuffer.copyOf(stream.totalOut.toInt())

        err = stream.deflateEnd()
        assertTrue(err == Z_OK, "Test setup deflateEnd failed. Error: $err, Msg: ${stream.msg}")

        return result
    }

    // Convert a hex string to a byte array for reference compressed data
    private fun hexStringToByteArray(hex: String): ByteArray {
        val len = hex.length
        val out = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            out[i / 2] = hex.substring(i, i + 2).toInt(16).toByte()
            i += 2
        }
        return out
    }

    private fun inflateDataInternal(inputDeflated: ByteArray, originalSizeHint: Int): ByteArray {
        println("=== Starting inflateDataInternal ===")
        val stream = ZStream()
        println("ZStream created")

        var err = stream.inflateInit(MAX_WBITS)
        println("inflateInit called, result: $err")
        assertTrue(err == Z_OK, "inflateInit failed. Error: $err, Msg: ${stream.msg}")

        stream.nextIn = inputDeflated
        stream.availIn = inputDeflated.size
        println("Input configured: ${inputDeflated.size} bytes")

        val outputBuffer = ByteArray(originalSizeHint * 4 + 200)
        stream.nextOut = outputBuffer
        stream.availOut = outputBuffer.size
        println("Output buffer configured: ${outputBuffer.size} bytes")

        println("About to call inflate with Z_FINISH")
        err = stream.inflate(Z_FINISH)
        println("inflate called, result: $err")
        assertTrue(err == Z_STREAM_END, "Inflation failed, error: $err, Msg: ${stream.msg}")

        val result = outputBuffer.copyOf(stream.totalOut.toInt())

        err = stream.inflateEnd()
        assertTrue(err == Z_OK, "inflateEnd failed. Error: $err, Msg: ${stream.msg}")

        return result
    }

    @Test
    fun basicInflationTest() {
        val originalString = "Hello World Hello World Hello World Hello World Hello World Hello World Hello World Hello World Hello World Hello World"
        val originalData = originalString.encodeToByteArray()

        val deflatedData = deflateDataForTestSetup(originalData, Z_DEFAULT_COMPRESSION)
        assertTrue(deflatedData.isNotEmpty(), "Deflated data for test setup is empty")
        assertTrue(!originalData.contentEquals(deflatedData), "Deflated data matches original in setup")

        val inflatedData = inflateDataInternal(deflatedData, originalData.size)

        assertTrue(inflatedData.isNotEmpty(), "Inflated data should not be empty")
        assertEquals(originalString, inflatedData.decodeToString(), "Inflated data does not match original string")
        assertTrue(originalData.contentEquals(inflatedData), "Inflated data does not match original byte array")
    }

    @Test
    fun inflateNoCompressionDataTest() {
        val originalString = "Test data with no compression and some length to ensure it's processed."
        val originalData = originalString.encodeToByteArray()

        val deflatedNoCompression = deflateDataForTestSetup(originalData, Z_NO_COMPRESSION)
        assertTrue(deflatedNoCompression.isNotEmpty(), "Deflated (no compression) data is empty")

        val inflatedData = inflateDataInternal(deflatedNoCompression, originalData.size)

        assertEquals(originalString, inflatedData.decodeToString(), "Inflated Z_NO_COMPRESSION data mismatch")
    }

    @Test
    fun minimalInputDataTest() {
        val originalString = "A"
        val originalData = originalString.encodeToByteArray()

        val deflatedData = deflateDataForTestSetup(originalData, Z_DEFAULT_COMPRESSION)
        assertTrue(deflatedData.isNotEmpty(), "Deflated data for minimal input is empty")

        val inflatedData = inflateDataInternal(deflatedData, originalData.size)

        assertTrue(inflatedData.isNotEmpty(), "Inflated data for minimal input should not be empty")
        assertEquals(originalString, inflatedData.decodeToString(), "Inflated data for minimal input does not match original string")
        assertTrue(originalData.contentEquals(inflatedData), "Inflated data for minimal input does not match original byte array")
    }

    @Test
    fun referenceInflationCompatibilityTest() {
        val compressedHex = "789ccb48cdc9c95728cf2fca4951c8406227e7e71614a51617a7a628a42496242a2467a4266703008a7f1106"
        val compressedData = hexStringToByteArray(compressedHex)
        val expectedString = "hello world hello world compressed data check"
        val result = inflateDataInternal(compressedData, expectedString.length)
        assertEquals(expectedString, result.decodeToString(), "Inflation output mismatched reference implementation")
    }
}