package ai.solace.zlib.test

import ai.solace.zlib.deflate.Inflate
import ai.solace.zlib.deflate.ZStream
import kotlin.test.Test
import kotlin.test.assertEquals

class PigzRealWorldTest {

    @Test
    fun testPigzRealWorldDecompression() {
        // Real zlib data from: echo "Hello World Hello World..." | pigz -z
        // Expected output: "Hello World Hello World Hello World Hello World Hello World Hello World Hello World Hello World Hello World Hello World"
        val compressedData = byteArrayOf(
            0x78.toByte(), 0x5e.toByte(), 0xf3.toByte(), 0x48.toByte(), 
            0xcd.toByte(), 0xc9.toByte(), 0xc9.toByte(), 0x57.toByte(),
            0x08.toByte(), 0xcf.toByte(), 0x2f.toByte(), 0xca.toByte(), 
            0x49.toByte(), 0x51.toByte(), 0xf0.toByte(), 0xa0.toByte(),
            0x1f.toByte(), 0x9b.toByte(), 0x0b.toByte(), 0x00.toByte(), 
            0x09.toByte(), 0xe0.toByte(), 0x2a.toByte(), 0x43.toByte()
        )

        val expectedOutput = "Hello World Hello World Hello World Hello World Hello World Hello World Hello World Hello World Hello World Hello World"

        println("[PIGZ_TEST] Starting real-world pigz decompression test")
        println("[PIGZ_TEST] Compressed data size: ${compressedData.size} bytes")
        println("[PIGZ_TEST] Expected output: '$expectedOutput'")
        println("[PIGZ_TEST] Expected length: ${expectedOutput.length}")

        val z = ZStream()
        val outputBuffer = ByteArray(1024) // Large buffer for output

        // Initialize inflation
        z.nextIn = compressedData
        z.nextInIndex = 0
        z.availIn = compressedData.size
        z.nextOut = outputBuffer
        z.nextOutIndex = 0
        z.availOut = outputBuffer.size

        val inflate = Inflate()
        var result = inflate.inflateInit(z, 15) // 15 is default window bits
        
        println("[PIGZ_TEST] inflateInit result: $result")
        assertEquals(0, result)

        // Perform decompression
        result = inflate.inflate(z, 4) // Z_FINISH = 4
        println("[PIGZ_TEST] inflate result: $result")
        println("[PIGZ_TEST] Output bytes written: ${z.nextOutIndex}")
        
        // Get the actual output
        val actualOutput = outputBuffer.sliceArray(0 until z.nextOutIndex).decodeToString()
        println("[PIGZ_TEST] Actual output: '$actualOutput'")
        println("[PIGZ_TEST] Actual length: ${actualOutput.length}")

        // Cleanup
        inflate.inflateEnd(z)

        // Verify the results
        assertEquals(expectedOutput, actualOutput)
        
        println("[PIGZ_TEST] ✅ SUCCESS: Real-world pigz decompression test passed!")
    }
}
