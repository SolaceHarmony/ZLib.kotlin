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
        val originalString = "Hello World "
        val originalData = originalString.encodeToByteArray()

        // Use known-good compressed data created with pigz
        // This is "Hello World " compressed with pigz -z
        val deflatedData = byteArrayOf(
            0x78.toByte(), 0x5e.toByte(), // zlib header  
            0xf3.toByte(), 0x48.toByte(), 0xcd.toByte(), 0xc9.toByte(), 0xc9.toByte(), 0x57.toByte(),
            0x08.toByte(), 0xcf.toByte(), 0x2f.toByte(), 0xca.toByte(), 0x49.toByte(), 0x51.toByte(),
            0x00.toByte(), 0x00.toByte(), // end of block
            0x1c.toByte(), 0x48.toByte(), 0x04.toByte(), 0x3d.toByte() // checksum
        )
        
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