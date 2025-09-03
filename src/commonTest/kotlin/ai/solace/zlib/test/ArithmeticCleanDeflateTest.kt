package ai.solace.zlib.test

import ai.solace.zlib.clean.ArithmeticCleanDeflate
import ai.solace.zlib.clean.CleanDeflate
import ai.solace.zlib.common.Z_OK
import ai.solace.zlib.common.ZlibLogger
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/**
 * Test to verify ArithmeticCleanDeflate produces the same results as CleanDeflate
 */
class ArithmeticCleanDeflateTest {
    
    @Test
    fun testArithmeticVsNativeSmallData() {
        // Enable logging for debugging
        ZlibLogger.ENABLE_LOGGING = true
        ZlibLogger.DEBUG_ENABLED = true
        // Create a simple compressed data sample
        val original = "Hello, World! This is a test of the arithmetic deflate implementation."
        
        // Use a pre-compressed zlib sample (compressed with standard tools)
        // This is "Hello, World!" compressed with zlib
        val compressed = byteArrayOf(
            0x78.toByte(), 0x9c.toByte(), // zlib header
            0xf3.toByte(), 0x48.toByte(), 0xcd.toByte(), 0xc9.toByte(), 0xc9.toByte(), 0xd7.toByte(),
            0x51.toByte(), 0x08.toByte(), 0xcf.toByte(), 0x2f.toByte(), 0xca.toByte(), 0x49.toByte(),
            0x51.toByte(), 0x04.toByte(), 0x00.toByte(), // deflate data
            0x1f.toByte(), 0x9e.toByte(), 0x04.toByte(), 0x6a.toByte() // adler32
        )
        
        val arithmetic = ArithmeticCleanDeflate()
        
        val arithmeticResult = arithmetic.decompress(compressed)
        val (nativeStatus, nativeResult) = CleanDeflate.inflateZlib(compressed)
        
        // Check that native decompression succeeded
        assertEquals(Z_OK, nativeStatus, "Native decompression should succeed")
        
        // Both should produce the same output
        assertContentEquals(nativeResult, arithmeticResult, 
            "Arithmetic and native implementations should produce identical output")
        
        // Verify the actual content
        val expected = "Hello, World!"
        assertEquals(expected, arithmeticResult.decodeToString(),
            "Arithmetic decompression should produce correct output")
    }
    
    @Test
    fun testArithmeticStoredBlock() {
        // Create a zlib stream with an uncompressed (stored) block
        // Format: [zlib header] [block header] [len] [nlen] [data] [adler32]
        val data = "STORED"
        val dataBytes = data.encodeToByteArray()
        
        // Build the compressed data manually
        val compressed = mutableListOf<Byte>()
        
        // ZLIB header (no dictionary, default compression)
        compressed.add(0x78.toByte()) // CMF
        compressed.add(0x01.toByte()) // FLG (adjusted for checksum)
        
        // DEFLATE stored block
        compressed.add(0x01.toByte()) // BFINAL=1, BTYPE=00 (stored)
        
        // Length (little-endian)
        compressed.add((dataBytes.size and 0xFF).toByte())
        compressed.add((dataBytes.size shr 8).toByte())
        
        // One's complement of length
        compressed.add((dataBytes.size.inv() and 0xFF).toByte())
        compressed.add((dataBytes.size.inv() shr 8).toByte())
        
        // Raw data
        compressed.addAll(dataBytes.toList())
        
        // Calculate Adler32 for the uncompressed data
        var a = 1L
        var b = 0L
        for (byte in dataBytes) {
            a = (a + (byte.toInt() and 0xFF)) % 65521
            b = (b + a) % 65521
        }
        val adler32 = (b shl 16) or a
        
        // Add Adler32 (big-endian)
        compressed.add((adler32 shr 24).toByte())
        compressed.add((adler32 shr 16).toByte())
        compressed.add((adler32 shr 8).toByte())
        compressed.add((adler32 and 0xFF).toByte())
        
        val compressedArray = compressed.toByteArray()
        
        val arithmetic = ArithmeticCleanDeflate()
        
        val arithmeticResult = arithmetic.decompress(compressedArray)
        val (nativeStatus, nativeResult) = CleanDeflate.inflateZlib(compressedArray)
        
        assertEquals(Z_OK, nativeStatus, "Native decompression should succeed")
        
        assertContentEquals(nativeResult, arithmeticResult,
            "Stored block: Arithmetic and native should produce identical output")
        assertEquals(data, arithmeticResult.decodeToString(),
            "Stored block: Should decompress to original data")
    }
}