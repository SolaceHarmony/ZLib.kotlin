package ai.solace.zlib

import ai.solace.zlib.common.*
import ai.solace.zlib.deflate.ZStream
import ai.solace.zlib.deflate.ZStreamException

/**
 * ZLibCompression provides a high-level API for zlib compression and decompression operations.
 *
 * This class serves as the main entry point to the library, offering simple methods for common
 * compression tasks while hiding the complexity of working directly with ZStream and other
 * lower-level components.
 */
class ZLibCompression {
    companion object {
        /**
         * Returns the zlib library version.
         *
         * @return Version string of the library
         */
        fun version(): String = ai.solace.zlib.common.version()

        /**
         * Compresses the provided data using zlib compression algorithm.
         *
         * @param data The byte array to compress
         * @param level The compression level (0-9). Default is Z_DEFAULT_COMPRESSION (-1)
         *              which provides a good compromise between speed and compression ratio.
         * @param flush The flush mode. Default is Z_FINISH to complete compression in one call.
         * @return The compressed data as a byte array
         * @throws ZStreamException If compression fails
         */
        fun compress(data: ByteArray, level: Int = Z_DEFAULT_COMPRESSION, flush: Int = Z_FINISH): ByteArray {
            // Creating a new stream for compression
            val stream = ZStream()

            // Initialize the stream for compression
            val err = stream.deflateInit(level)
            if (err != Z_OK) {
                throw ZStreamException("Failed to initialize deflate: ${stream.msg}")
            }

            try {
                // Set up input
                stream.nextIn = data
                stream.availIn = data.size

                // Estimate the output size (compressed data is usually smaller, but we add some margin)
                val estimatedSize = (data.size * 1.1 + 12).toInt()
                val output = ByteArray(estimatedSize)

                // Set up output
                stream.nextOut = output
                stream.availOut = output.size

                // Perform compression
                val deflateErr = stream.deflate(flush)
                if (deflateErr != Z_STREAM_END && deflateErr != Z_OK) {
                    throw ZStreamException("Compression failed: ${stream.msg}")
                }

                // Return the compressed data with correct size
                val compressedSize = output.size - stream.availOut
                return output.copyOf(compressedSize)
            } finally {
                // Always clean up the stream
                stream.deflateEnd()
            }
        }

        /**
         * Decompresses the provided zlib-compressed data.
         *
         * @param compressedData The compressed byte array to decompress
         * @param estimatedOriginalSize An estimate of the original data size to optimize buffer allocation.
         *                              If not provided, a default expansion factor will be used.
         * @return The decompressed data as a byte array
         * @throws ZStreamException If decompression fails
         */
        fun decompress(compressedData: ByteArray, estimatedOriginalSize: Int = 0): ByteArray {
            // Creating a new stream for decompression
            val stream = ZStream()

            // Initialize the stream for decompression
            val err = stream.inflateInit()
            if (err != Z_OK) {
                throw ZStreamException("Failed to initialize inflate: ${stream.msg}")
            }

            try {
                // Set up input
                stream.nextIn = compressedData
                stream.availIn = compressedData.size

                // Calculate output buffer size
                val outputSize = if (estimatedOriginalSize > 0) {
                    estimatedOriginalSize
                } else {
                    // If no estimate provided, use an expansion factor
                    compressedData.size * 4
                }

                val output = ByteArray(outputSize)
                stream.nextOut = output
                stream.availOut = output.size

                // Try to decompress
                var inflateErr = stream.inflate(Z_NO_FLUSH)

                // If our buffer wasn't big enough, continue with larger buffers
                if (inflateErr == Z_BUF_ERROR || inflateErr == Z_OK) {
                    val chunks = mutableListOf<ByteArray>()
                    var totalSize = 0

                    // Add what we've already decompressed
                    val firstChunkSize = output.size - stream.availOut
                    if (firstChunkSize > 0) {
                        chunks.add(output.copyOf(firstChunkSize))
                        totalSize += firstChunkSize
                    }

                    // Continue decompressing with new buffers
                    while (inflateErr != Z_STREAM_END) {
                        if (inflateErr != Z_BUF_ERROR && inflateErr != Z_OK) {
                            throw ZStreamException("Decompression failed: ${stream.msg}")
                        }

                        // Allocate a new buffer for additional output
                        val nextBuffer = ByteArray(outputSize)
                        stream.nextOut = nextBuffer
                        stream.availOut = nextBuffer.size

                        // Continue decompression
                        inflateErr = stream.inflate(Z_NO_FLUSH)

                        // Add this chunk to our list if it has any data
                        val chunkSize = nextBuffer.size - stream.availOut
                        if (chunkSize > 0) {
                            chunks.add(nextBuffer.copyOf(chunkSize))
                            totalSize += chunkSize
                        }
                    }

                    // Combine all chunks into a single result array
                    if (chunks.size > 1) {
                        val result = ByteArray(totalSize)
                        var position = 0

                        for (chunk in chunks) {
                            chunk.copyInto(result, position)
                            position += chunk.size
                        }

                        return result
                    } else if (chunks.size == 1) {
                        return chunks[0]
                    } else {
                        return ByteArray(0)  // Empty result
                    }
                } else if (inflateErr != Z_STREAM_END) {
                    throw ZStreamException("Decompression failed: ${stream.msg}")
                }

                // Return the decompressed data with correct size
                val decompressedSize = output.size - stream.availOut
                return output.copyOf(decompressedSize)
            } finally {
                // Always clean up the stream
                stream.inflateEnd()
            }
        }
    }
}
