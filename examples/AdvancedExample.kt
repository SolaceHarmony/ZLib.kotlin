package examples

import ai.solace.zlib.deflate.ZStream
import ai.solace.zlib.deflate.ZStreamException
import ai.solace.zlib.common.*

/**
 * Advanced compression example demonstrating different compression levels and strategies
 */

fun main() {
    val testData = generateTestData()
    println("=== ZLib.kotlin Advanced Compression Example ===")
    println("Test data size: ${testData.size} bytes")
    println()
    
    // Test different compression levels
    testCompressionLevels(testData)
    println()
    
    // Test different compression strategies
    testCompressionStrategies(testData)
    println()
    
    // Test streaming compression for large data
    testStreamingCompression(testData)
}

private fun generateTestData(): ByteArray {
    // Generate test data with different patterns for comprehensive testing
    val builder = StringBuilder()
    
    // Highly compressible data (repeated patterns)
    repeat(100) {
        builder.append("Hello World! This is a test string with repeated content. ")
    }
    
    // Semi-random data
    repeat(50) {
        builder.append("Random-${(1..1000).random()}-Data-${('A'..'Z').random()}")
    }
    
    // JSON-like structured data
    repeat(20) {
        builder.append("""{"id": $it, "name": "user_$it", "active": ${it % 2 == 0}, "score": ${(1..100).random()}}""")
    }
    
    return builder.toString().encodeToByteArray()
}

private fun testCompressionLevels(testData: ByteArray) {
    println("--- Compression Level Comparison ---")
    
    val levels = listOf(
        Z_NO_COMPRESSION to "No compression",
        Z_BEST_SPEED to "Best speed",
        3 to "Fast",
        Z_DEFAULT_COMPRESSION to "Default",
        6 to "Balanced",
        Z_BEST_COMPRESSION to "Best compression"
    )
    
    levels.forEach { (level, description) ->
        try {
            val startTime = System.currentTimeMillis()
            val compressed = compressData(testData, level)
            val endTime = System.currentTimeMillis()
            
            val ratio = testData.size.toDouble() / compressed.size
            val savedPercent = (1.0 - compressed.size.toDouble() / testData.size) * 100
            
            println("$description (level $level):")
            println("  Size: ${compressed.size} bytes")
            println("  Ratio: ${String.format("%.2f", ratio)}")
            println("  Saved: ${String.format("%.1f", savedPercent)}%")
            println("  Time: ${endTime - startTime}ms")
            println()
            
        } catch (e: Exception) {
            println("$description (level $level): Failed - ${e.message}")
            println()
        }
    }
}

private fun testCompressionStrategies(testData: ByteArray) {
    println("--- Compression Strategy Comparison ---")
    
    val strategies = listOf(
        Z_DEFAULT_STRATEGY to "Default strategy",
        Z_FILTERED to "Filtered strategy",
        Z_HUFFMAN_ONLY to "Huffman only"
    )
    
    strategies.forEach { (strategy, description) ->
        try {
            val compressed = compressWithStrategy(testData, Z_DEFAULT_COMPRESSION, strategy)
            val ratio = testData.size.toDouble() / compressed.size
            val savedPercent = (1.0 - compressed.size.toDouble() / testData.size) * 100
            
            println("$description:")
            println("  Size: ${compressed.size} bytes")
            println("  Ratio: ${String.format("%.2f", ratio)}")
            println("  Saved: ${String.format("%.1f", savedPercent)}%")
            println()
            
        } catch (e: Exception) {
            println("$description: Failed - ${e.message}")
            println()
        }
    }
}

private fun testStreamingCompression(testData: ByteArray) {
    println("--- Streaming Compression Test ---")
    
    try {
        val startTime = System.currentTimeMillis()
        val compressed = compressStreaming(testData, chunkSize = 512)
        val endTime = System.currentTimeMillis()
        
        // Verify by decompressing
        val decompressed = decompressData(compressed)
        val isValid = testData.contentEquals(decompressed)
        
        println("Streaming compression (512-byte chunks):")
        println("  Original size: ${testData.size} bytes")
        println("  Compressed size: ${compressed.size} bytes")
        println("  Ratio: ${String.format("%.2f", testData.size.toDouble() / compressed.size)}")
        println("  Time: ${endTime - startTime}ms")
        println("  Data integrity: ${if (isValid) "PASSED" else "FAILED"}")
        println()
        
    } catch (e: Exception) {
        println("Streaming compression failed: ${e.message}")
        println()
    }
}

private fun compressData(input: ByteArray, level: Int): ByteArray {
    val stream = ZStream()
    
    try {
        var result = stream.deflateInit(level)
        if (result != Z_OK) {
            throw ZStreamException("Failed to initialize compression: ${stream.msg}")
        }
        
        stream.next_in = input
        stream.avail_in = input.size
        stream.next_in_index = 0
        
        val outputBuffer = ByteArray(input.size + (input.size shr 12) + (input.size shr 14) + 11)
        stream.next_out = outputBuffer
        stream.avail_out = outputBuffer.size
        stream.next_out_index = 0
        
        result = stream.deflate(Z_FINISH)
        if (result != Z_STREAM_END) {
            throw ZStreamException("Compression failed: ${stream.msg}")
        }
        
        val compressedSize = stream.total_out.toInt()
        stream.deflateEnd()
        return outputBuffer.copyOf(compressedSize)
        
    } finally {
        stream.free()
    }
}

private fun compressWithStrategy(input: ByteArray, level: Int, strategy: Int): ByteArray {
    val stream = ZStream()
    
    try {
        var result = stream.deflateInit(level, Z_DEFLATED, DEF_WBITS, 8, strategy)
        if (result != Z_OK) {
            throw ZStreamException("Failed to initialize compression: ${stream.msg}")
        }
        
        stream.next_in = input
        stream.avail_in = input.size
        stream.next_in_index = 0
        
        val outputBuffer = ByteArray(input.size + (input.size shr 12) + (input.size shr 14) + 11)
        stream.next_out = outputBuffer
        stream.avail_out = outputBuffer.size
        stream.next_out_index = 0
        
        result = stream.deflate(Z_FINISH)
        if (result != Z_STREAM_END) {
            throw ZStreamException("Compression failed: ${stream.msg}")
        }
        
        val compressedSize = stream.total_out.toInt()
        stream.deflateEnd()
        return outputBuffer.copyOf(compressedSize)
        
    } finally {
        stream.free()
    }
}

private fun compressStreaming(input: ByteArray, chunkSize: Int): ByteArray {
    val stream = ZStream()
    val output = mutableListOf<Byte>()
    
    try {
        stream.deflateInit(Z_DEFAULT_COMPRESSION)
        
        val outputBuffer = ByteArray(chunkSize * 2) // Buffer for compressed chunks
        
        var offset = 0
        while (offset < input.size) {
            val currentChunkSize = minOf(chunkSize, input.size - offset)
            
            // Set input for this chunk
            stream.next_in = input.copyOfRange(offset, offset + currentChunkSize)
            stream.next_in_index = 0
            stream.avail_in = currentChunkSize
            
            val flush = if (offset + currentChunkSize >= input.size) Z_FINISH else Z_NO_FLUSH
            
            do {
                stream.next_out = outputBuffer
                stream.next_out_index = 0
                stream.avail_out = outputBuffer.size
                
                val result = stream.deflate(flush)
                if (result == Z_STREAM_ERROR) {
                    throw ZStreamException("Compression error: ${stream.msg}")
                }
                
                val bytesProduced = outputBuffer.size - stream.avail_out
                for (i in 0 until bytesProduced) {
                    output.add(outputBuffer[i])
                }
                
            } while (stream.avail_out == 0)
            
            offset += currentChunkSize
        }
        
        stream.deflateEnd()
        return output.toByteArray()
        
    } finally {
        stream.free()
    }
}

private fun decompressData(compressed: ByteArray): ByteArray {
    val stream = ZStream()
    
    try {
        var result = stream.inflateInit()
        if (result != Z_OK) {
            throw ZStreamException("Failed to initialize decompression: ${stream.msg}")
        }
        
        stream.next_in = compressed
        stream.avail_in = compressed.size
        stream.next_in_index = 0
        
        val outputBuffer = ByteArray(compressed.size * 4)
        stream.next_out = outputBuffer
        stream.avail_out = outputBuffer.size
        stream.next_out_index = 0
        
        result = stream.inflate(Z_FINISH)
        if (result != Z_STREAM_END && result != Z_OK) {
            throw ZStreamException("Decompression failed: ${stream.msg}")
        }
        
        val decompressedSize = stream.total_out.toInt()
        stream.inflateEnd()
        return outputBuffer.copyOf(decompressedSize)
        
    } finally {
        stream.free()
    }
}
