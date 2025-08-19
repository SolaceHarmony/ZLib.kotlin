package ai.solace.zlib.test

import ai.solace.zlib.deflate.ZStream
import ai.solace.zlib.deflate.Adler32
import ai.solace.zlib.common.*
import kotlin.test.*

/**
 * Test to isolate the checksum issue during compression/decompression
 */
class ChecksumDebuggingTest {

    @Test
    fun testChecksumIssue() {
        println("=== Checksum Debugging Test ===")
        
        val originalString = "AB"
        val originalData = originalString.encodeToByteArray()
        
        println("Original: '$originalString' = [${originalData.joinToString(", ") { (it.toInt() and 0xFF).toString() }}]")
        
        // Calculate expected checksum
        val adler = Adler32()
        val expectedChecksum = adler.adler32(1L, originalData, 0, originalData.size)
        println("Expected Adler32 checksum: $expectedChecksum (0x${expectedChecksum.toString(16)})")
        
        // Compress
        val compressed = compressWithDetails(originalData)
        println("Compressed: ${compressed.size} bytes = [${compressed.joinToString(", ") { (it.toInt() and 0xFF).toString() }}]")
        
        // Extract checksum from compressed data
        // In zlib format, the last 4 bytes are the Adler32 checksum in big-endian
        if (compressed.size >= 4) {
            val checksumBytes = compressed.takeLast(4)
            val storedChecksum = (checksumBytes[0].toInt() and 0xFF) shl 24 or
                                (checksumBytes[1].toInt() and 0xFF) shl 16 or
                                (checksumBytes[2].toInt() and 0xFF) shl 8 or
                                (checksumBytes[3].toInt() and 0xFF)
            val unsignedStoredChecksum = storedChecksum.toLong() and 0xFFFFFFFFL
            println("Stored checksum in compressed data: $unsignedStoredChecksum (0x${unsignedStoredChecksum.toString(16)})")
            
            if (expectedChecksum == unsignedStoredChecksum) {
                println("✅ Checksum in compressed data matches expected")
            } else {
                println("❌ Checksum mismatch! Expected: $expectedChecksum, Stored: $unsignedStoredChecksum")
            }
        }
        
        // Try to decompress using the same assert pattern as the other tests
        val decompressed = decompressWithDetails(compressed, originalData.size)
        val result = decompressed.decodeToString()
        
        assertEquals(originalString, result, "Decompression should work correctly")
        assertTrue(originalData.contentEquals(decompressed), "Byte arrays should match")
    }

    private fun compressWithDetails(input: ByteArray): ByteArray {
        val stream = ZStream()
        println("--- Compression Details ---")
        
        var err = stream.deflateInit(Z_DEFAULT_COMPRESSION)
        if (err != Z_OK) {
            throw RuntimeException("deflateInit failed: $err, ${stream.msg}")
        }
        println("deflateInit: OK")

        stream.nextIn = input
        stream.availIn = input.size
        stream.nextInIndex = 0

        val outputBuffer = ByteArray(input.size * 2 + 100)
        stream.nextOut = outputBuffer
        stream.availOut = outputBuffer.size
        stream.nextOutIndex = 0
        
        println("Before deflate: availIn=${stream.availIn}, availOut=${stream.availOut}")

        err = stream.deflate(Z_FINISH)
        if (err != Z_STREAM_END) {
            throw RuntimeException("deflate failed: $err, ${stream.msg}")
        }
        println("deflate: OK, produced ${stream.totalOut} bytes")

        val result = outputBuffer.copyOf(stream.totalOut.toInt())

        err = stream.deflateEnd()
        if (err != Z_OK) {
            throw RuntimeException("deflateEnd failed: $err, ${stream.msg}")
        }
        println("deflateEnd: OK")

        return result
    }

    private fun decompressWithDetails(inputDeflated: ByteArray, originalSizeHint: Int): ByteArray {
        val stream = ZStream()
        println("--- Decompression Details ---")
        
        var err = stream.inflateInit(MAX_WBITS)
        if (err != Z_OK) {
            throw RuntimeException("inflateInit failed: $err, ${stream.msg}")
        }
        println("inflateInit: OK")

        stream.nextIn = inputDeflated
        stream.availIn = inputDeflated.size
        stream.nextInIndex = 0

        val outputBuffer = ByteArray(originalSizeHint * 4 + 100)
        stream.nextOut = outputBuffer
        stream.availOut = outputBuffer.size
        stream.nextOutIndex = 0
        
        println("Before inflate: availIn=${stream.availIn}, availOut=${stream.availOut}")

        err = stream.inflate(Z_FINISH)
        println("inflate result: $err, ${stream.msg ?: "no message"}")
        
        if (err != Z_STREAM_END) {
            throw RuntimeException("inflate failed: $err, ${stream.msg}")
        }
        println("inflate: OK, produced ${stream.totalOut} bytes")

        val result = outputBuffer.copyOf(stream.totalOut.toInt())

        err = stream.inflateEnd()
        if (err != Z_OK) {
            throw RuntimeException("inflateEnd failed: $err, ${stream.msg}")
        }
        println("inflateEnd: OK")

        return result
    }
}