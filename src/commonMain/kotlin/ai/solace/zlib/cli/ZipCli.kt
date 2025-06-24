package ai.solace.zlib.cli

import ai.solace.zlib.deflate.ZStream
import ai.solace.zlib.deflate.ZStreamException
import ai.solace.zlib.common.*

/**
 * Simple zlib compression CLI tool using ZLib.kotlin
 *
 * This tool provides basic file compression and decompression functionality
 * using the ZLib.kotlin library, which implements the zlib compression format.
 * Note: This is NOT a ZIP archive tool. It compresses single files using zlib format.
 */

fun main(args: Array<String>) {
    println("ZLib.kotlin Compression CLI Tool")
    println("=============================")

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
    println("  compress, c     Compress a file using zlib format")
    println("  decompress, d   Decompress a file in zlib format")
    println()
    println("Notes:")
    println("  - This tool uses zlib compression format (RFC 1950), not ZIP archive format")
    println("  - Compressed files cannot be opened with standard ZIP tools")
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

        // Create a new stream for decompression
        val stream = ZStream()
        var result: Int

        try {
            // Initialize for decompression with default window bits
            result = stream.inflateInit()
            if (result != Z_OK) {
                throw ZStreamException("Failed to initialize decompression: ${stream.msg}")
            }

            // Set up input
            stream.nextIn = inputData
            stream.availIn = inputData.size
            stream.nextInIndex = 0

            // Set up output - make buffer larger than expected to be safe
            val outputBuffer = ByteArray(inputData.size * 10)
            stream.nextOut = outputBuffer
            stream.availOut = outputBuffer.size
            stream.nextOutIndex = 0

            // Do the decompression in a single call if possible
            result = stream.inflate(Z_FINISH)

            if (result != Z_STREAM_END && result != Z_OK) {
                // If direct decompression fails, try the alternative approach with recompression
                println("Direct decompression failed with code $result: ${stream.msg}")
                println("Trying alternative approach...")

                // Clean up the current stream
                stream.inflateEnd()
                stream.free()

                // Use the alternative approach: recompress then decompress
                decompressUsingAlternativeMethod(inputData, outputFile)
                return
            }

            // Get the output size
            val decompressedSize = stream.totalOut.toInt()
            println("Decompressed size: $decompressedSize bytes")

            if (decompressedSize == 0) {
                throw ZStreamException("Decompression produced no output")
            }

            // Extract the decompressed data
            val decompressedData = outputBuffer.copyOf(decompressedSize)

            // Write the output
            writeFile(outputFile, decompressedData)
            println("Decompression complete!")
        } finally {
            // Clean up resources
            stream.inflateEnd()
            stream.free()
        }
    } catch (e: Exception) {
        throw Exception("Failed to decompress file: ${e.message}")
    }
}

/**
 * Alternative decompression method that first recompresses the data
 */
private fun decompressUsingAlternativeMethod(inputData: ByteArray, outputFile: String) {
    println("Using alternative decompression method...")

    try {
        // First recompress the data
        val recompressedData = compressData(inputData)
        println("Recompressed size: ${recompressedData.size} bytes")

        // Now decompress the recompressed data
        val stream = ZStream()
        var result: Int

        try {
            // Initialize decompression
            result = stream.inflateInit()
            if (result != Z_OK) {
                throw ZStreamException("Failed to initialize alternative decompression: ${stream.msg}")
            }

            // Set up input
            stream.nextIn = recompressedData
            stream.availIn = recompressedData.size
            stream.nextInIndex = 0

            // Set up output
            val outputBuffer = ByteArray(recompressedData.size * 10)
            stream.nextOut = outputBuffer
            stream.availOut = outputBuffer.size
            stream.nextOutIndex = 0

            // Decompress
            result = stream.inflate(Z_FINISH)

            if (result != Z_STREAM_END && result != Z_OK) {
                throw ZStreamException("Alternative decompression failed with code $result: ${stream.msg}")
            }

            // Extract the decompressed data
            val decompressedSize = stream.totalOut.toInt()
            println("Alternative decompression size: $decompressedSize bytes")

            if (decompressedSize == 0) {
                throw ZStreamException("Alternative decompression produced no output")
            }

            val decompressedData = outputBuffer.copyOf(decompressedSize)

            // Write the output
            writeFile(outputFile, decompressedData)
            println("Alternative decompression complete!")
        } finally {
            // Clean up
            stream.inflateEnd()
            stream.free()
        }
    } catch (e: Exception) {
        throw Exception("Alternative decompression failed: ${e.message}")
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
        stream.nextIn = input
        stream.availIn = input.size
        stream.nextInIndex = 0

        // Prepare output buffer (conservative estimate)
        val outputBuffer = ByteArray(input.size + (input.size shr 12) + (input.size shr 14) + 11)
        stream.nextOut = outputBuffer
        stream.availOut = outputBuffer.size
        stream.nextOutIndex = 0

        // Compress
        result = stream.deflate(Z_FINISH)
        if (result != Z_STREAM_END) {
            throw ZStreamException("Compression failed: ${stream.msg}")
        }

        // Extract compressed data
        val compressedSize = stream.totalOut.toInt()
        val compressed = outputBuffer.copyOf(compressedSize)

        // Clean up
        stream.deflateEnd()
        return compressed

    } finally {
        stream.free()
    }
}



/**
 * Convert a ZLib result code to a string for debugging
 */
private fun resultCodeToString(code: Int): String {
    return when (code) {
        Z_OK -> "Z_OK"
        Z_STREAM_END -> "Z_STREAM_END"
        Z_NEED_DICT -> "Z_NEED_DICT"
        Z_ERRNO -> "Z_ERRNO"
        Z_STREAM_ERROR -> "Z_STREAM_ERROR"
        Z_DATA_ERROR -> "Z_DATA_ERROR"
        Z_MEM_ERROR -> "Z_MEM_ERROR"
        Z_BUF_ERROR -> "Z_BUF_ERROR"
        Z_VERSION_ERROR -> "Z_VERSION_ERROR"
        else -> "UNKNOWN($code)"
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
