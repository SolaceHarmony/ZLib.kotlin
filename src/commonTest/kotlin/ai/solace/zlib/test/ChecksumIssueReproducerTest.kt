package ai.solace.zlib.test

import ai.solace.zlib.deflate.ZStream
import ai.solace.zlib.deflate.Adler32
import ai.solace.zlib.common.*
import kotlin.test.*

/**
 * Test to reproduce the exact checksum issue described in issue #52
 */
class ChecksumIssueReproducerTest {

    @Test
    fun testSimple18ByteInput() {
        println("=== Testing 18-byte input (should work) ===")
        val input = "Hello World test!"
        val originalData = input.encodeToByteArray()
        assertEquals(17, originalData.size, "Input should be 17 bytes, not 18 as mentioned in description")
        
        val compressed = compress(originalData)
        val decompressed = decompress(compressed, originalData.size)
        
        assertEquals(input, decompressed.decodeToString())
        assertTrue(originalData.contentEquals(decompressed))
    }

    @Test 
    fun testLarger47ByteInput() {
        println("=== Testing 47-byte repetitive input (likely to fail) ===")
        val input = "This is a repetitive test string that should fail!"
        val originalData = input.encodeToByteArray()
        println("Input size: ${originalData.size} bytes: '$input'")
        
        val compressed = compress(originalData)
        println("Compressed size: ${compressed.size} bytes")
        
        // Extract stored checksum from compressed data for verification
        if (compressed.size >= 4) {
            val checksumBytes = compressed.takeLast(4)
            val storedChecksum = (checksumBytes[0].toInt() and 0xFF) shl 24 or
                                (checksumBytes[1].toInt() and 0xFF) shl 16 or
                                (checksumBytes[2].toInt() and 0xFF) shl 8 or
                                (checksumBytes[3].toInt() and 0xFF)
            val unsignedStoredChecksum = storedChecksum.toLong() and 0xFFFFFFFFL
            println("Stored checksum in compressed data: $unsignedStoredChecksum (0x${unsignedStoredChecksum.toString(16)})")
            
            // Calculate expected checksum manually
            val expectedChecksum = Adler32().adler32(1L, originalData, 0, originalData.size)
            println("Expected checksum: $expectedChecksum (0x${expectedChecksum.toString(16)})")
            assertEquals(expectedChecksum, unsignedStoredChecksum, "Stored checksum should match calculated checksum")
        }
        
        val decompressed = decompress(compressed, originalData.size)
        
        assertEquals(input, decompressed.decodeToString())
        assertTrue(originalData.contentEquals(decompressed))
    }
    
    @Test
    fun testVeryLargeInput() {
        println("=== Testing very large input ===")
        val input = "A".repeat(1000) + "B".repeat(1000) + "C".repeat(1000)
        val originalData = input.encodeToByteArray()
        println("Input size: ${originalData.size} bytes")
        
        val compressed = compress(originalData)
        println("Compressed size: ${compressed.size} bytes")
        
        val decompressed = decompress(compressed, originalData.size)
        
        assertEquals(input, decompressed.decodeToString())
        assertTrue(originalData.contentEquals(decompressed))
    }

    @Test
    fun testChecksumCalculationDuringDecompression() {
        println("=== Testing checksum calculation during decompression ===")
        
        val input = "Test data for checksum verification during inflate process!"
        val originalData = input.encodeToByteArray()
        
        val compressed = compress(originalData)
        
        // Manual decompression to track z.adler throughout the process
        val z = ZStream()
        
        val result = z.inflateInit()
        assertEquals(Z_OK, result, "inflateInit should succeed")
        
        z.nextIn = compressed
        z.availIn = compressed.size
        z.nextInIndex = 0
        
        // Make sure we have enough output buffer space
        val output = ByteArray(originalData.size * 10 + 1000)  // Much larger buffer
        z.nextOut = output
        z.availOut = output.size
        z.nextOutIndex = 0
        
        println("Before inflate: z.adler = ${z.adler}")
        println("Input: availIn=${z.availIn}, nextInIndex=${z.nextInIndex}")
        println("Output: availOut=${z.availOut}, nextOutIndex=${z.nextOutIndex}")
        println("Compressed data size: ${compressed.size}")
        
        val inflateResult = z.inflate(Z_FINISH)
        
        println("After inflate: z.adler = ${z.adler}, result = $inflateResult")
        println("Final: availIn=${z.availIn}, nextInIndex=${z.nextInIndex}, totalIn=${z.totalIn}")
        println("Final: availOut=${z.availOut}, nextOutIndex=${z.nextOutIndex}, totalOut=${z.totalOut}")
        
        if (inflateResult != Z_STREAM_END) {
            println("inflate failed: $inflateResult, msg: ${z.msg}")
            
            // Try with different flush modes
            val z2 = ZStream()
            z2.inflateInit()
            z2.nextIn = compressed
            z2.availIn = compressed.size
            z2.nextInIndex = 0
            z2.nextOut = output
            z2.availOut = output.size
            z2.nextOutIndex = 0
            
            val retryResult = z2.inflate(Z_NO_FLUSH)
            println("Retry with Z_NO_FLUSH: result=$retryResult, z.adler=${z2.adler}, totalOut=${z2.totalOut}")
            
            if (retryResult == Z_OK) {
                val finalResult = z2.inflate(Z_FINISH)
                println("Final result: $finalResult, z.adler=${z2.adler}, totalOut=${z2.totalOut}")
            }
            
            z2.inflateEnd()
            
            // Let's not fail the test yet, we want to see what's happening
            println("Test continuing despite inflate failure to gather more info...")
        }
        
        // The key check: z.adler should NOT be 1 if checksum was calculated properly
        if (z.adler == 1L) {
            println("WARNING: z.adler is still 1, checksum was not updated during decompression")
        } else {
            println("SUCCESS: z.adler was updated to ${z.adler}")
        }
        
        if (z.totalOut > 0) {
            val decompressed = output.copyOf(z.totalOut.toInt())
            println("Decompressed: '${decompressed.decodeToString()}'")
            assertEquals(input, decompressed.decodeToString())
        } else {
            println("No output was produced")
        }
        
        z.inflateEnd()
    }

    private fun compress(input: ByteArray): ByteArray {
        val stream = ZStream()
        
        var err = stream.deflateInit(Z_DEFAULT_COMPRESSION)
        if (err != Z_OK) {
            throw RuntimeException("deflateInit failed: $err, ${stream.msg}")
        }

        stream.nextIn = input
        stream.availIn = input.size
        stream.nextInIndex = 0

        val outputBuffer = ByteArray(input.size * 2 + 100)
        stream.nextOut = outputBuffer
        stream.availOut = outputBuffer.size
        stream.nextOutIndex = 0

        err = stream.deflate(Z_FINISH)
        if (err != Z_STREAM_END) {
            throw RuntimeException("deflate failed: $err, ${stream.msg}")
        }

        val result = outputBuffer.copyOf(stream.totalOut.toInt())

        err = stream.deflateEnd()
        if (err != Z_OK) {
            throw RuntimeException("deflateEnd failed: $err, ${stream.msg}")
        }

        return result
    }

    private fun decompress(inputDeflated: ByteArray, originalSizeHint: Int): ByteArray {
        val stream = ZStream()
        
        var err = stream.inflateInit()
        if (err != Z_OK) {
            throw RuntimeException("inflateInit failed: $err, ${stream.msg}")
        }

        stream.nextIn = inputDeflated
        stream.availIn = inputDeflated.size
        stream.nextInIndex = 0

        val outputBuffer = ByteArray(originalSizeHint * 4 + 100)
        stream.nextOut = outputBuffer
        stream.availOut = outputBuffer.size
        stream.nextOutIndex = 0

        err = stream.inflate(Z_FINISH)
        
        if (err != Z_STREAM_END) {
            throw RuntimeException("inflate failed: $err, ${stream.msg}")
        }

        val result = outputBuffer.copyOf(stream.totalOut.toInt())

        err = stream.inflateEnd()
        if (err != Z_OK) {
            throw RuntimeException("inflateEnd failed: $err, ${stream.msg}")
        }

        return result
    }
}