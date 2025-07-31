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
    ZlibLogger.log("ZLib.kotlin Compression CLI Tool")
    ZlibLogger.log("=============================")

    if (args.isEmpty() || args[0] == "-h" || args[0] == "--help") {
        printUsage()
        return
    }

    try {
        when (args[0]) {
            "compress", "c" -> {
                if (args.size < 3) {
                    ZlibLogger.log("Error: Not enough arguments for compression")
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
                    ZlibLogger.log("Error: Not enough arguments for decompression")
                    printUsage()
                    return
                }

                val inputFile = args[1]
                val outputFile = args[2]

                decompressFile(inputFile, outputFile)
            }

            else -> {
                ZlibLogger.log("Error: Unknown command '${args[0]}'")
                printUsage()
            }
        }
    } catch (e: Exception) {
        ZlibLogger.log("Error: ${e.message}")
    }
}

/**
 * Print usage information
 */
fun printUsage() {
    ZlibLogger.log("Usage:")
    ZlibLogger.log("  compress|c <input-file> <output-file> [compression-level]")
    ZlibLogger.log("  decompress|d <input-file> <output-file>")
    ZlibLogger.log("")
    ZlibLogger.log("Commands:")
    ZlibLogger.log("  compress, c     Compress a file using zlib format")
    ZlibLogger.log("  decompress, d   Decompress a file in zlib format")
    ZlibLogger.log("")
    ZlibLogger.log("Notes:")
    ZlibLogger.log("  - This tool uses zlib compression format (RFC 1950), not ZIP archive format")
    ZlibLogger.log("  - Compressed files cannot be opened with standard ZIP tools")
    ZlibLogger.log("")
    ZlibLogger.log("Options:")
    ZlibLogger.log("  compression-level   Optional compression level (0-9)")
    ZlibLogger.log("                      0: No compression")
    ZlibLogger.log("                      1: Best speed")
    ZlibLogger.log("                      6: Default compression")
    ZlibLogger.log("                      9: Best compression")
}

/**
 * Compress a file using ZLib compression
 */
fun compressFile(inputFile: String, outputFile: String, level: Int = Z_DEFAULT_COMPRESSION) {
    ZlibLogger.log("Compressing $inputFile to $outputFile (level: $level)...")

    try {
        val inputData = readFile(inputFile)
        ZlibLogger.log("Input file size: ${inputData.size} bytes")

        val compressedData = compressData(inputData, level)
        ZlibLogger.log("Compressed size: ${compressedData.size} bytes")
        val ratio = (inputData.size.toDouble() / compressedData.size)
        ZlibLogger.log("Compression ratio: ${(ratio * 100).toInt() / 100.0}")

        writeFile(outputFile, compressedData)
        ZlibLogger.log("Compression complete!")

    } catch (e: Exception) {
        throw Exception("Failed to compress file: ${e.message}")
    }
}

/**
 * Decompress a file using ZLib decompression
 */
fun decompressFile(inputFile: String, outputFile: String) {
    ZlibLogger.log("Decompressing $inputFile to $outputFile...")

    try {
        val inputData = readFile(inputFile)
        ZlibLogger.log("Input file size: ${inputData.size} bytes")

        try {
            // Use the new ZLibCompression wrapper for direct decompression
            val decompressedData = ZLibCompression.decompress(inputData)
            ZlibLogger.log("Decompressed size: ${decompressedData.size} bytes")

            // Write the output
            writeFile(outputFile, decompressedData)
            ZlibLogger.log("Decompression complete!")
        } catch (e: ZStreamException) {
            // If direct decompression fails, try the alternative approach with recompression
            ZlibLogger.log("Direct decompression failed: ${e.message}")
            ZlibLogger.log("Trying alternative approach...")

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
    ZlibLogger.log("Using alternative decompression method...")

    try {
        // First recompress the data
        val recompressedData = ZLibCompression.compress(inputData)
        ZlibLogger.log("Recompressed size: ${recompressedData.size} bytes")

        // Now decompress the recompressed data
        val decompressedData = ZLibCompression.decompress(recompressedData)
        ZlibLogger.log("Alternative decompression size: ${decompressedData.size} bytes")

        if (decompressedData.isEmpty()) {
            throw ZStreamException("Alternative decompression produced no output")
        }

        // Write the output
        writeFile(outputFile, decompressedData)
        ZlibLogger.log("Alternative decompression complete!")
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