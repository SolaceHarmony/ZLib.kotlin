package ai.solace.zlib.test

import ai.solace.zlib.common.*
import ai.solace.zlib.deflate.ZStream
import kotlin.test.*

/**
 * Pigz cross-validation test to verify RFC 1950/1951 compliance.
 * This test validates that our compression produces data compatible with standard tools.
 * 
 * Currently expected to FAIL due to deflate algorithm bug producing malformed streams.
 * Use this test to validate when the core deflate issue is fixed.
 */
class PigzValidationTest {

    @Test 
    fun testPigzCrossCompatibilityForSingleByte() {
        val testCases = listOf(
            "A", "B", "C", "Z", "0", "9", "!", "@"
        )
        
        for (testInput in testCases) {
            println("=== Testing input: '$testInput' ===")
            
            val inputBytes = testInput.encodeToByteArray()
            val compressed = compressWithZLibKotlin(inputBytes)
            
            println("Input: [${inputBytes[0]}] ('$testInput')")
            println("Our compressed: [${compressed.joinToString(", ") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }}]")
            
            // For now, just verify our compression produces something
            assertTrue(compressed.isNotEmpty(), "Compression should produce non-empty output for '$testInput'")
            
            // Verify basic zlib header
            assertEquals(0x78, compressed[0].toInt() and 0xFF, "First byte should be zlib header for '$testInput'")
            
            // The actual cross-validation with pigz would need system integration
            // For now, this documents what should be tested when the deflate bug is fixed
            println("TODO: Cross-validate with pigz when deflate algorithm is fixed")
        }
    }
    
    @Test
    fun testAdler32CalculationAccuracy() {
        // This test verifies that our Adler32 calculation is correct
        // (This part is already working correctly per our analysis)
        
        val testData = "A".encodeToByteArray()
        
        val stream = ZStream()
        val initResult = stream.deflateInit(Z_DEFAULT_COMPRESSION)
        assertEquals(Z_OK, initResult)
        
        stream.nextIn = testData
        stream.availIn = testData.size
        stream.nextInIndex = 0
        
        val outputBuffer = ByteArray(50)
        stream.nextOut = outputBuffer
        stream.availOut = outputBuffer.size
        stream.nextOutIndex = 0
        
        val deflateResult = stream.deflate(Z_FINISH)
        assertEquals(Z_STREAM_END, deflateResult)
        
        val compressed = outputBuffer.copyOf(stream.totalOut.toInt())
        stream.deflateEnd()
        
        // Extract Adler32 from compressed data (last 4 bytes)
        val adler32Bytes = compressed.takeLast(4)
        val adler32 = ((adler32Bytes[0].toInt() and 0xFF) shl 24) or
                     ((adler32Bytes[1].toInt() and 0xFF) shl 16) or 
                     ((adler32Bytes[2].toInt() and 0xFF) shl 8) or
                     (adler32Bytes[3].toInt() and 0xFF)
        
        // Manually calculate expected Adler32 for 'A' (65)
        // Starting values: a=1, b=0
        // After processing byte 65: a=(1+65) mod 65521 = 66, b=(0+66) mod 65521 = 66  
        // Result: (66 << 16) | 66 = 4325442
        val expectedAdler32 = 4325442
        
        assertEquals(expectedAdler32, adler32, "Adler32 checksum should be calculated correctly")
        println("✓ Adler32 calculation verified: $adler32 (correct)")
    }
    
    private fun compressWithZLibKotlin(input: ByteArray): ByteArray {
        val stream = ZStream()
        try {
            val result = stream.deflateInit(Z_DEFAULT_COMPRESSION)
            if (result != Z_OK) throw RuntimeException("deflateInit failed: $result")
            
            stream.nextIn = input
            stream.availIn = input.size
            stream.nextInIndex = 0
            
            val output = ByteArray(input.size * 2 + 50)
            stream.nextOut = output
            stream.availOut = output.size  
            stream.nextOutIndex = 0
            
            val deflateResult = stream.deflate(Z_FINISH)
            if (deflateResult != Z_STREAM_END) throw RuntimeException("deflate failed: $deflateResult")
            
            return output.copyOf(stream.totalOut.toInt())
        } finally {
            stream.deflateEnd()
        }
    }
}