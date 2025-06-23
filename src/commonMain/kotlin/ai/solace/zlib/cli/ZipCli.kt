package ai.solace.zlib.cli

import ai.solace.zlib.deflate.ZStream
import ai.solace.zlib.deflate.ZStreamException
import ai.solace.zlib.common.*

/**
 * Simple ZIP CLI tool using ZLib.kotlin
 * 
 * This tool provides basic file compression and decompression functionality
 * using the ZLib.kotlin library.
 */

fun main(args: Array<String>) {
    println("ZLib.kotlin ZIP CLI Tool")
    println("=======================")

    if (args.isEmpty() || args[0] == "-h" || args[0] == "--help") {
        printUsage()
        return
    }

    try {
        when (args[0]) {
            "compress", "c" -> {
                if (args.size < 3) {
                    println("Error: Not enough arguments for compression")
                    printUsage()
                    return
                }

                val inputFile = args[1]
                val outputFile = args[2]

                // Optional compression level
                val level = if (args.size > 3) {
                    args[3].toIntOrNull() ?: Z_DEFAULT_COMPRESSION
                } else {
                    Z_DEFAULT_COMPRESSION
                }

                compressFile(inputFile, outputFile, level)
            }

            "decompress", "d" -> {
                if (args.size < 3) {
                    println("Error: Not enough arguments for decompression")
                    printUsage()
                    return
                }

                val inputFile = args[1]
                val outputFile = args[2]

                decompressFile(inputFile, outputFile)
            }

            else -> {
                println("Error: Unknown command '${args[0]}'")
                printUsage()
            }
        }
    } catch (e: Exception) {
        println("Error: ${e.message}")
    }
}

/**
 * Print usage information
 */
fun printUsage() {
    println("Usage:")
    println("  compress|c <input-file> <output-file> [compression-level]")
    println("  decompress|d <input-file> <output-file>")
    println()
    println("Commands:")
    println("  compress, c     Compress a file")
    println("  decompress, d   Decompress a file")
    println()
    println("Options:")
    println("  compression-level   Optional compression level (0-9)")
    println("                      0: No compression")
    println("                      1: Best speed")
    println("                      6: Default compression")
    println("                      9: Best compression")
}

/**
 * Compress a file using ZLib compression
 */
fun compressFile(inputFile: String, outputFile: String, level: Int = Z_DEFAULT_COMPRESSION) {
    println("Compressing $inputFile to $outputFile (level: $level)...")

    try {
        val inputData = readFile(inputFile)
        println("Input file size: ${inputData.size} bytes")

        val compressedData = compressData(inputData, level)
        println("Compressed size: ${compressedData.size} bytes")
        val ratio = (inputData.size.toDouble() / compressedData.size)
        println("Compression ratio: ${(ratio * 100).toInt() / 100.0}")

        writeFile(outputFile, compressedData)
        println("Compression complete!")

    } catch (e: Exception) {
        throw Exception("Failed to compress file: ${e.message}")
    }
}

/**
 * Decompress a file using ZLib decompression
 */
fun decompressFile(inputFile: String, outputFile: String) {
    println("Decompressing $inputFile to $outputFile...")

    try {
        val inputData = readFile(inputFile)
        println("Input file size: ${inputData.size} bytes")

        val decompressedData = decompressData(inputData)
        println("Decompressed size: ${decompressedData.size} bytes")

        writeFile(outputFile, decompressedData)
        println("Decompression complete!")

    } catch (e: Exception) {
        throw Exception("Failed to decompress file: ${e.message}")
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

/**
 * Read a file into a byte array
 */
expect fun readFile(path: String): ByteArray

/**
 * Write a byte array to a file
 */
expect fun writeFile(path: String, data: ByteArray)
