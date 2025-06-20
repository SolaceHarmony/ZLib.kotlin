package examples

import ai.solace.zlib.deflate.ZStream
import ai.solace.zlib.deflate.ZStreamException
import ai.solace.zlib.common.*

/**
 * Simple compression and decompression example using ZLib.kotlin
 */

fun main() {
    // Sample data to compress
    val originalText = """
        Hello, ZLib.kotlin!
        
        This is a demonstration of the ZLib.kotlin compression library.
        ZLib.kotlin is a pure Kotlin implementation of the zlib compression algorithm,
        providing fast and efficient compression and decompression capabilities
        for Kotlin Multiplatform projects.
        
        The library supports various compression levels and strategies,
        making it suitable for different use cases from real-time data compression
        to maximum compression ratio scenarios.
        
        This text will be compressed and then decompressed to demonstrate
        the basic functionality of the library.
    """.trimIndent()
    
    val originalData = originalText.encodeToByteArray()
    
    println("=== ZLib.kotlin Compression Example ===")
    println("Original text length: ${originalData.size} bytes")
    println()
    
    try {
        // Compress the data
        val compressedData = compressData(originalData, Z_DEFAULT_COMPRESSION)
        println("Compressed length: ${compressedData.size} bytes")
        println("Compression ratio: ${String.format("%.2f", (originalData.size.toDouble() / compressedData.size))}")
        println("Space saved: ${String.format("%.1f", (1.0 - compressedData.size.toDouble() / originalData.size) * 100)}%")
        println()
        
        // Decompress the data
        val decompressedData = decompressData(compressedData)
        val decompressedText = decompressedData.decodeToString()
        
        println("Decompressed length: ${decompressedData.size} bytes")
        println("Data integrity check: ${if (originalData.contentEquals(decompressedData)) "PASSED" else "FAILED"}")
        println()
        
        if (originalText == decompressedText) {
            println("✅ Compression and decompression successful!")
        } else {
            println("❌ Data corruption detected!")
        }
        
    } catch (e: ZStreamException) {
        println("❌ ZLib error: ${e.message}")
    } catch (e: Exception) {
        println("❌ Unexpected error: ${e.message}")
    }
}

/**
 * Compress a byte array using ZLib compression
 */
fun compressData(input: ByteArray, level: Int = Z_DEFAULT_COMPRESSION): ByteArray {
    val stream = ZStream()
    
    try {
        // Initialize compression
        var result = stream.deflateInit(level)
        if (result != Z_OK) {
            throw ZStreamException("Failed to initialize compression: ${stream.msg}")
        }
        
        // Set up input
        stream.next_in = input
        stream.avail_in = input.size
        stream.next_in_index = 0
        
        // Prepare output buffer (conservative estimate)
        val outputBuffer = ByteArray(input.size + (input.size shr 12) + (input.size shr 14) + 11)
        stream.next_out = outputBuffer
        stream.avail_out = outputBuffer.size
        stream.next_out_index = 0
        
        // Compress
        result = stream.deflate(Z_FINISH)
        if (result != Z_STREAM_END) {
            throw ZStreamException("Compression failed: ${stream.msg}")
        }
        
        // Extract compressed data
        val compressedSize = stream.total_out.toInt()
        val compressed = outputBuffer.copyOf(compressedSize)
        
        // Clean up
        stream.deflateEnd()
        return compressed
        
    } finally {
        stream.free()
    }
}

/**
 * Decompress a byte array using ZLib decompression
 */
fun decompressData(compressed: ByteArray): ByteArray {
    val stream = ZStream()
    
    try {
        // Initialize decompression
        var result = stream.inflateInit()
        if (result != Z_OK) {
            throw ZStreamException("Failed to initialize decompression: ${stream.msg}")
        }
        
        // Set up input
        stream.next_in = compressed
        stream.avail_in = compressed.size
        stream.next_in_index = 0
        
        // Prepare output buffer (estimate 4x the compressed size)
        val outputBuffer = ByteArray(compressed.size * 4)
        stream.next_out = outputBuffer
        stream.avail_out = outputBuffer.size
        stream.next_out_index = 0
        
        // Decompress
        result = stream.inflate(Z_FINISH)
        if (result != Z_STREAM_END && result != Z_OK) {
            throw ZStreamException("Decompression failed: ${stream.msg}")
        }
        
        // Extract decompressed data
        val decompressedSize = stream.total_out.toInt()
        val decompressed = outputBuffer.copyOf(decompressedSize)
        
        // Clean up
        stream.inflateEnd()
        return decompressed
        
    } finally {
        stream.free()
    }
}
