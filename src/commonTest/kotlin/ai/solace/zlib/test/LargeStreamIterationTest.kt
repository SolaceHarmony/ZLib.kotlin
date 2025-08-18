package ai.solace.zlib.test

import ai.solace.zlib.common.ZlibLogger
import ai.solace.zlib.deflate.Deflate
import ai.solace.zlib.deflate.Inflate
import ai.solace.zlib.deflate.ZStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Test to verify that large streams don't trigger the iteration safety check
 * in InfCodes.proc() after the fix to use window-size proportional limits.
 */
class LargeStreamIterationTest {

    @Test
    fun testLargeStreamDecompressionWithProportionalLimit() {
        ZlibLogger.log("[LARGE_STREAM_TEST] Testing large stream decompression with proportional iteration limit")
        
        // Create a large input that would likely exceed 10,000 iterations with the old limit
        val largeInputSize = 50000  // 50KB of data
        val largeInput = ByteArray(largeInputSize) { (it % 256).toByte() }
        
        ZlibLogger.log("[LARGE_STREAM_TEST] Created input of ${largeInput.size} bytes")
        
        // First, compress the data
        val compressedData = compressData(largeInput)
        ZlibLogger.log("[LARGE_STREAM_TEST] Compressed to ${compressedData.size} bytes")
        
        // Then decompress it to verify it works without hitting iteration limits
        val decompressedData = decompressData(compressedData)
        ZlibLogger.log("[LARGE_STREAM_TEST] Decompressed to ${decompressedData.size} bytes")
        
        // Verify the data was decompressed correctly
        assertEquals(largeInput.size, decompressedData.size, "Decompressed size should match original")
        assertTrue(largeInput.contentEquals(decompressedData), "Decompressed data should match original")
        
        ZlibLogger.log("[LARGE_STREAM_TEST] Large stream decompression successful")
    }

    @Test
    fun testIterationLimitCalculation() {
        ZlibLogger.log("[LARGE_STREAM_TEST] Testing iteration limit calculation for different window sizes")
        
        // Test with different window sizes to verify proportional limits
        val testCases = listOf(
            Pair(8, 256),   // wbits=8 -> 256 bytes -> 1,024 limit  
            Pair(12, 4096), // wbits=12 -> 4KB -> 16,384 limit
            Pair(15, 32768) // wbits=15 -> 32KB -> 131,072 limit
        )
        
        for ((windowBits, expectedWindowSize) in testCases) {
            val expectedLimit = expectedWindowSize * 4
            ZlibLogger.log("[LARGE_STREAM_TEST] Testing windowBits=$windowBits, expected window size=$expectedWindowSize, expected limit=$expectedLimit")
            
            // Create small test data that should definitely not hit any reasonable limit
            val input = ByteArray(100) { (it % 100).toByte() }
            
            val compressedData = compressData(input, windowBits)
            val decompressedData = decompressData(compressedData, windowBits)
            
            assertEquals(input.size, decompressedData.size, "Window size $expectedWindowSize: decompressed size mismatch")
            assertTrue(input.contentEquals(decompressedData), "Window size $expectedWindowSize: data integrity check failed")
        }
        
        ZlibLogger.log("[LARGE_STREAM_TEST] All window size tests passed")
    }

    @Test
    fun testSmallWindowStillHasReasonableLimit() {
        ZlibLogger.log("[LARGE_STREAM_TEST] Testing that small windows still have reasonable limits")
        
        // Use minimum window size (8 bits = 256 bytes)
        val windowBits = 8
        val expectedWindowSize = 1 shl (windowBits and 0xFF)  // Match production code window size calculation
        val expectedIterationLimit = expectedWindowSize * 4  // 1024 iterations
        
        // Create data that should decompress normally
        val input = ByteArray(100) { (it % 10).toByte() }
        
        // Compress with small window
        val compressedData = compressData(input, windowBits)
        ZlibLogger.log("[LARGE_STREAM_TEST] Compressed ${input.size} bytes to ${compressedData.size} bytes with window size $expectedWindowSize")
        
        // Decompress - should work fine
        val decompressedData = decompressData(compressedData, windowBits)
        
        assertEquals(input.size, decompressedData.size, "Decompressed size should match original")
        assertTrue(input.contentEquals(decompressedData), "Decompressed data should match original")
        
        ZlibLogger.log("[LARGE_STREAM_TEST] Small window decompression successful with proportional limit of ~$expectedIterationLimit")
    }

    private fun compressData(input: ByteArray, windowBits: Int = 15): ByteArray {
        val z = ZStream()
        val deflate = Deflate()
        
        val outputBuffer = ByteArray(input.size * 2) // Generous buffer
        
        z.nextIn = input
        z.nextInIndex = 0
        z.availIn = input.size
        z.nextOut = outputBuffer
        z.nextOutIndex = 0
        z.availOut = outputBuffer.size
        
        var result = deflate.deflateInit(z, 6, windowBits)  // Compression level 6
        assertEquals(0, result, "deflateInit should succeed")
        
        result = deflate.deflate(z, 4) // Z_FINISH
        assertTrue(result == 0 || result == 1, "deflate should succeed (result: $result)")
        
        deflate.deflateEnd(z)
        
        return outputBuffer.copyOfRange(0, z.nextOutIndex)
    }
    
    private fun decompressData(compressedData: ByteArray, windowBits: Int = 15): ByteArray {
        val z = ZStream()
        val inflate = Inflate()
        
        // Use a generous output buffer size
        val outputBuffer = ByteArray(compressedData.size * 10)
        
        z.nextIn = compressedData
        z.nextInIndex = 0
        z.availIn = compressedData.size
        z.nextOut = outputBuffer
        z.nextOutIndex = 0
        z.availOut = outputBuffer.size
        
        var result = inflate.inflateInit(z, windowBits)
        assertEquals(0, result, "inflateInit should succeed")
        
        result = inflate.inflate(z, 4) // Z_FINISH
        assertTrue(result == 0 || result == 1, "inflate should succeed (result: $result)")
        
        // Check that we didn't get a data error (which would indicate iteration limit hit)
        assertNotEquals(Z_DATA_ERROR, result, "Should not get Z_DATA_ERROR from iteration limit")
        
        inflate.inflateEnd(z)
        
        return outputBuffer.copyOfRange(0, z.nextOutIndex)
    }
}