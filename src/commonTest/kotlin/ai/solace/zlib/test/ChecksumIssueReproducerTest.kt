package ai.solace.zlib.test

import ai.solace.zlib.deflate.ZStream
import ai.solace.zlib.deflate.Adler32
import ai.solace.zlib.common.*
import kotlin.test.*
import kotlin.test.Ignore

/**
 * Test to reproduce the exact checksum issue described in issue #52
 */
@Ignore
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
    fun testSimpleDebuggingCase() {
        println("=== Simple debugging case ===")
        
        // Use the most basic possible case that should work
        val input = "AB"
        val originalData = input.encodeToByteArray()
        
        println("Original: '$input' = [${originalData.joinToString(", ") { (it.toInt() and 0xFF).toString() }}]")
        
        val compressed = compress(originalData)
        println("Compressed: ${compressed.size} bytes = [${compressed.joinToString(", ") { (it.toInt() and 0xFF).toString() }}]")
        
        // Try decompression with detailed logging
        val z = ZStream()
        
        val result = z.inflateInit()
        assertEquals(Z_OK, result, "inflateInit should succeed")
        
        z.nextIn = compressed
        z.availIn = compressed.size
        z.nextInIndex = 0
        
        val output = ByteArray(1000)
        z.nextOut = output
        z.availOut = output.size
        z.nextOutIndex = 0
        
        println("About to call inflate...")
        val inflateResult = z.inflate(Z_FINISH)
        
        println("Inflate result: $inflateResult")
        println("totalIn: ${z.totalIn}, totalOut: ${z.totalOut}")
        println("z.adler: ${z.adler}")
        
        if (inflateResult == Z_STREAM_END) {
            val decompressed = output.copyOf(z.totalOut.toInt())
            println("Success! Decompressed: '${decompressed.decodeToString()}'")
            assertEquals(input, decompressed.decodeToString())
        } else {
            println("Failed with error $inflateResult: ${z.msg}")
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
