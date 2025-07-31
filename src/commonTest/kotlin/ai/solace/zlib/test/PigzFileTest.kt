package ai.solace.zlib.test

import ai.solace.zlib.deflate.ZStream
import ai.solace.zlib.common.*
import kotlin.test.*

class PigzFileTest {
    
    @Test
    fun testDecompressPigzFile() {
        val compressedBytes = readCompressedFile("test_input.txt.zz")
        val decompressedBytes = decompressZlibData(compressedBytes)
        val decompressedString = decompressedBytes.decodeToString()
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

        var err = stream.inflateInit()
        assertTrue(err == Z_OK, "inflateInit failed. Error: $err, Msg: ${stream.msg}")

        stream.nextIn = compressedData
        stream.availIn = compressedData.size

        val outputBuffer = ByteArray(1024)
        stream.nextOut = outputBuffer
        stream.availOut = outputBuffer.size

        err = stream.inflate(Z_FINISH)
        assertTrue(err == Z_STREAM_END, "Inflation failed, error: $err, Msg: ${stream.msg}")

        err = stream.inflateEnd()
        assertTrue(err == Z_OK, "inflateEnd failed. Error: $err, Msg: ${stream.msg}")

        return outputBuffer.copyOf(stream.totalOut.toInt())
    }
}