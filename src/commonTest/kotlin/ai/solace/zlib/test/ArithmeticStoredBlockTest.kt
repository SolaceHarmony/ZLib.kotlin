package ai.solace.zlib.test

import ai.solace.zlib.clean.ArithmeticCleanDeflate
import ai.solace.zlib.common.ZlibLogger
import kotlin.test.Test
import kotlin.test.assertEquals

class ArithmeticStoredBlockTest {
    
    @Test
    fun testStoredBlockOnly() {
        ZlibLogger.ENABLE_LOGGING = true
        ZlibLogger.DEBUG_ENABLED = true
        
        // Create a simple stored block manually
        val data = "TEST"
        val dataBytes = data.encodeToByteArray()
        
        // Build compressed data
        val compressed = mutableListOf<Byte>()
        
        // ZLIB header
        compressed.add(0x78.toByte()) // CMF: method=8, window=7
        compressed.add(0x01.toByte()) // FLG: no dict, fast compression
        
        // DEFLATE stored block: BFINAL=1, BTYPE=00
        compressed.add(0x01.toByte()) // 00000001 = BFINAL=1, BTYPE=00
        
        // Length (little-endian)
        val len = dataBytes.size
        compressed.add((len and 0xFF).toByte())
        compressed.add((len shr 8).toByte())
        
        // One's complement of length
        val nlen = len.inv()
        compressed.add((nlen and 0xFF).toByte())
        compressed.add((nlen shr 8).toByte())
        
        // Raw data
        compressed.addAll(dataBytes.toList())
        
        // Calculate Adler32 manually
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
        compressed.add((adler32).toByte())
        
        println("Compressed data: ${compressed.map { it.toUByte().toString(16).padStart(2, '0') }.joinToString(" ")}")
        
        val arithmetic = ArithmeticCleanDeflate()
        try {
            val result = arithmetic.decompress(compressed.toByteArray())
            assertEquals(data, result.decodeToString(), "Should decompress correctly")
            println("SUCCESS: Decompressed '$data'")
        } catch (e: Exception) {
            println("ERROR: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }
}