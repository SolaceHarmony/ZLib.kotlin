package ai.solace.zlib.test

import ai.solace.zlib.deflate.ZStream
import ai.solace.zlib.common.*
import kotlin.test.Test
import kotlin.test.assertEquals

class VerificationTest {
    
    @Test
    fun testBasicInflateDeflate() {
        val testStrings = listOf(
            "Hello World",
            "A",
            "The quick brown fox jumps over the lazy dog",
            "1234567890",
            "This is a longer test string to verify that compression and decompression work correctly."
        )
        
        for (original in testStrings) {
            val originalBytes = original.encodeToByteArray()
            
            // Compress
            val deflateStream = ZStream()
            val initResult = deflateStream.deflateInit(Z_DEFAULT_COMPRESSION)
            assertEquals(Z_OK, initResult, "deflateInit failed")
            
            deflateStream.nextIn = originalBytes
            deflateStream.availIn = originalBytes.size
            
            val compressedBuffer = ByteArray(originalBytes.size * 2 + 100)
            deflateStream.nextOut = compressedBuffer
            deflateStream.availOut = compressedBuffer.size
            
            val deflateResult = deflateStream.deflate(Z_FINISH)
            assertEquals(Z_STREAM_END, deflateResult, "deflate failed for: $original")
            
            val compressedData = compressedBuffer.copyOf(deflateStream.totalOut.toInt())
            deflateStream.deflateEnd()
            
            // Decompress
            val inflateStream = ZStream()
            assertEquals(Z_OK, inflateStream.inflateInit(), "inflateInit failed")
            
            inflateStream.nextIn = compressedData
            inflateStream.availIn = compressedData.size
            
            val decompressedBuffer = ByteArray(originalBytes.size * 4 + 100)
            inflateStream.nextOut = decompressedBuffer
            inflateStream.availOut = decompressedBuffer.size
            
            val inflateResult = inflateStream.inflate(Z_FINISH)
            assertEquals(Z_STREAM_END, inflateResult, "inflate failed for: $original")
            
            val decompressedData = decompressedBuffer.copyOf(inflateStream.totalOut.toInt())
            inflateStream.inflateEnd()
            
            val decompressed = decompressedData.decodeToString()
            assertEquals(original, decompressed, "Round-trip failed for: $original")
        }
    }
}