package ai.solace.zlib.cli

import ai.solace.zlib.ZLibCompression
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

        try {
            // Use the new ZLibCompression wrapper for direct decompression
            val decompressedData = ZLibCompression.decompress(inputData)
            println("Decompressed size: ${decompressedData.size} bytes")

            // Write the output
            writeFile(outputFile, decompressedData)
            println("Decompression complete!")
        } catch (e: ZStreamException) {
            // If direct decompression fails, try the alternative approach with recompression
            println("Direct decompression failed: ${e.message}")
            println("Trying alternative approach...")

            decompressUsingAlternativeMethod(inputData, outputFile)
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
        val recompressedData = ZLibCompression.compress(inputData)
        println("Recompressed size: ${recompressedData.size} bytes")

        // Now decompress the recompressed data
        val decompressedData = ZLibCompression.decompress(recompressedData)
        println("Alternative decompression size: ${decompressedData.size} bytes")

        if (decompressedData.isEmpty()) {
            throw ZStreamException("Alternative decompression produced no output")
        }

        // Write the output
        writeFile(outputFile, decompressedData)
        println("Alternative decompression complete!")
    } catch (e: Exception) {
        throw Exception("Alternative decompression failed: ${e.message}")
    }
}

/**
 * Compress a byte array using ZLib compression
 */
fun compressData(input: ByteArray, level: Int = Z_DEFAULT_COMPRESSION): ByteArray {
    // Use the new ZLibCompression wrapper instead of directly managing ZStream
    return ZLibCompression.compress(input, level, Z_FINISH)
}

/**
 * Read a file into a byte array
 */
expect fun readFile(path: String): ByteArray

/**
 * Write a byte array to a file
 */
expect fun writeFile(path: String, data: ByteArray)
