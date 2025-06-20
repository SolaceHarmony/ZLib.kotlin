# ZLib.kotlin API Documentation

[![License: Zlib](https://img.shields.io/badge/license-Zlib-lightgrey.svg)](https://opensource.org/licenses/Zlib)
[![Kotlin](https://img.shields.io/badge/Kotlin-Native-blue.svg)](https://kotlinlang.org/)

## Table of Contents

- [Overview](#overview)
- [Core Classes](#core-classes)
  - [ZStream](#zstream)
  - [ZInputStream](#zinputstream)
  - [ZStreamException](#zstreamexception)
- [Constants](#constants)
- [Compression Examples](#compression-examples)
- [Decompression Examples](#decompression-examples)
- [Advanced Usage](#advanced-usage)
- [Error Handling](#error-handling)
- [Performance Considerations](#performance-considerations)

---

## Overview

ZLib.kotlin provides a pure Kotlin implementation of the zlib compression library. The API is designed to be familiar to users of the original zlib library while providing Kotlin-idiomatic interfaces.

The library supports both low-level streaming compression/decompression via `ZStream` and higher-level stream-based operations via `ZInputStream`.

---

## Core Classes

### ZStream

The primary class for compression and decompression operations. Provides low-level control over the compression process.

#### Constructor

```kotlin
val stream = ZStream()
```

#### Key Properties

```kotlin
// Input buffer management
var next_in: ByteArray?        // Input data buffer
var next_in_index: Int         // Current position in input buffer
var avail_in: Int              // Available bytes in input buffer
var total_in: Long             // Total bytes processed from input

// Output buffer management
var next_out: ByteArray?       // Output data buffer
var next_out_index: Int        // Current position in output buffer
var avail_out: Int             // Available space in output buffer
var total_out: Long            // Total bytes written to output

// Status and diagnostics
var msg: String?               // Error message (if any)
var adler: Long                // Adler-32 checksum
var data_type: Int             // Data type hint (text/binary)
```

#### Compression Methods

```kotlin
// Initialize compression
fun deflateInit(): Int
fun deflateInit(level: Int): Int
fun deflateInit(level: Int, method: Int, windowBits: Int, memLevel: Int, strategy: Int): Int

// Perform compression
fun deflate(flush: Int): Int

// Finalize compression
fun deflateEnd(): Int

// Advanced compression control
fun deflateSetDictionary(dictionary: ByteArray, dictLength: Int): Int
fun deflateParams(level: Int, strategy: Int): Int
fun deflateCopy(dest: ZStream): Int
fun deflateReset(): Int
```

#### Decompression Methods

```kotlin
// Initialize decompression
fun inflateInit(): Int
fun inflateInit(windowBits: Int): Int

// Perform decompression
fun inflate(flush: Int): Int

// Finalize decompression
fun inflateEnd(): Int

// Advanced decompression control
fun inflateSetDictionary(dictionary: ByteArray, dictLength: Int): Int
fun inflateSync(): Int
fun inflateReset(): Int
```

#### Utility Methods

```kotlin
// Release resources
fun free()
```

### ZInputStream

A higher-level streaming interface for decompression that implements the `InputStream` interface.

#### Constructor

```kotlin
val zinput = ZInputStream(inputStream: InputStream)
```

#### Methods

```kotlin
// Read decompressed data
override fun read(): Int
override fun read(buffer: ByteArray): Int
override fun read(buffer: ByteArray, offset: Int, length: Int): Int

// Stream control
override fun close()
override fun available(): Int

// Get total bytes read/written
fun getTotalIn(): Long
fun getTotalOut(): Long
```

### ZStreamException

Exception class for zlib-specific errors.

#### Constructor

```kotlin
ZStreamException(message: String)
```

---

## Constants

All constants are defined in `ai.solace.zlib.common.Constants`:

### Compression Levels

```kotlin
const val Z_NO_COMPRESSION = 0
const val Z_BEST_SPEED = 1
const val Z_BEST_COMPRESSION = 9
const val Z_DEFAULT_COMPRESSION = -1
```

### Compression Strategies

```kotlin
const val Z_FILTERED = 1
const val Z_HUFFMAN_ONLY = 2
const val Z_DEFAULT_STRATEGY = 0
```

### Flush Types

```kotlin
const val Z_NO_FLUSH = 0
const val Z_PARTIAL_FLUSH = 1
const val Z_SYNC_FLUSH = 2
const val Z_FULL_FLUSH = 3
const val Z_FINISH = 4
```

### Return Codes

```kotlin
const val Z_OK = 0
const val Z_STREAM_END = 1
const val Z_NEED_DICT = 2
const val Z_ERRNO = -1
const val Z_STREAM_ERROR = -2
const val Z_DATA_ERROR = -3
const val Z_MEM_ERROR = -4
const val Z_BUF_ERROR = -5
const val Z_VERSION_ERROR = -6
```

### Window Bits

```kotlin
const val MAX_WBITS = 15
const val DEF_WBITS = MAX_WBITS
```

---

## Compression Examples

### Basic Compression

```kotlin
import ai.solace.zlib.deflate.ZStream
import ai.solace.zlib.common.*

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
        
        // Prepare output buffer
        val outputBuffer = ByteArray(input.size * 2) // Conservative estimate
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

// Usage
val originalData = "Hello, ZLib.kotlin! This is a test string for compression.".encodeToByteArray()
val compressed = compressData(originalData, Z_BEST_COMPRESSION)
println("Original size: ${originalData.size}, Compressed size: ${compressed.size}")
```

### Streaming Compression

```kotlin
fun compressLargeData(input: ByteArray): ByteArray {
    val stream = ZStream()
    val output = mutableListOf<Byte>()
    
    try {
        stream.deflateInit(Z_DEFAULT_COMPRESSION)
        
        val chunkSize = 1024
        val outputBuffer = ByteArray(chunkSize)
        
        for (offset in input.indices step chunkSize) {
            val currentChunkSize = minOf(chunkSize, input.size - offset)
            
            // Set input
            stream.next_in = input
            stream.next_in_index = offset
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
        }
        
        stream.deflateEnd()
        return output.toByteArray()
        
    } finally {
        stream.free()
    }
}
```

---

## Decompression Examples

### Basic Decompression

```kotlin
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
        
        // Prepare output buffer (estimate larger size)
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

// Usage
val decompressed = decompressData(compressed)
val originalString = decompressed.decodeToString()
println("Decompressed: $originalString")
```

### Using ZInputStream

```kotlin
import ai.solace.zlib.deflate.ZInputStream
import ai.solace.zlib.streams.InputStream

fun decompressWithStream(compressedData: ByteArray): ByteArray {
    val inputStream = ByteArrayInputStream(compressedData)
    val zis = ZInputStream(inputStream)
    
    return zis.use { stream ->
        stream.readAllBytes()
    }
}
```

---

## Advanced Usage

### Custom Compression Parameters

```kotlin
fun compressWithCustomParams(
    input: ByteArray,
    level: Int = Z_DEFAULT_COMPRESSION,
    strategy: Int = Z_DEFAULT_STRATEGY,
    windowBits: Int = DEF_WBITS,
    memLevel: Int = 8
): ByteArray {
    val stream = ZStream()
    
    try {
        // Initialize with custom parameters
        val result = stream.deflateInit(level, Z_DEFLATED, windowBits, memLevel, strategy)
        if (result != Z_OK) {
            throw ZStreamException("Failed to initialize compression: ${stream.msg}")
        }
        
        // ... rest of compression logic
        
    } finally {
        stream.free()
    }
}
```

### Using Dictionary Compression

```kotlin
fun compressWithDictionary(input: ByteArray, dictionary: ByteArray): ByteArray {
    val stream = ZStream()
    
    try {
        stream.deflateInit(Z_DEFAULT_COMPRESSION)
        
        // Set dictionary
        val result = stream.deflateSetDictionary(dictionary, dictionary.size)
        if (result != Z_OK) {
            throw ZStreamException("Failed to set dictionary: ${stream.msg}")
        }
        
        // ... rest of compression logic
        
    } finally {
        stream.free()
    }
}

fun decompressWithDictionary(compressed: ByteArray, dictionary: ByteArray): ByteArray {
    val stream = ZStream()
    
    try {
        stream.inflateInit()
        
        // Set up for decompression
        stream.next_in = compressed
        stream.avail_in = compressed.size
        stream.next_in_index = 0
        
        val outputBuffer = ByteArray(compressed.size * 4)
        stream.next_out = outputBuffer
        stream.avail_out = outputBuffer.size
        stream.next_out_index = 0
        
        // Attempt decompression
        var result = stream.inflate(Z_NO_FLUSH)
        
        // If dictionary is needed
        if (result == Z_NEED_DICT) {
            result = stream.inflateSetDictionary(dictionary, dictionary.size)
            if (result != Z_OK) {
                throw ZStreamException("Failed to set dictionary: ${stream.msg}")
            }
            result = stream.inflate(Z_FINISH)
        }
        
        if (result != Z_STREAM_END) {
            throw ZStreamException("Decompression failed: ${stream.msg}")
        }
        
        val decompressedSize = stream.total_out.toInt()
        stream.inflateEnd()
        return outputBuffer.copyOf(decompressedSize)
        
    } finally {
        stream.free()
    }
}
```

---

## Error Handling

### Return Code Checking

```kotlin
fun safeCompress(input: ByteArray): ByteArray {
    val stream = ZStream()
    
    try {
        var result = stream.deflateInit()
        when (result) {
            Z_OK -> { /* Continue */ }
            Z_MEM_ERROR -> throw ZStreamException("Out of memory")
            Z_VERSION_ERROR -> throw ZStreamException("Version mismatch")
            Z_STREAM_ERROR -> throw ZStreamException("Invalid parameters")
            else -> throw ZStreamException("Unknown error: $result")
        }
        
        // ... compression logic with error checking
        
    } finally {
        stream.free()
    }
}
```

### Exception Handling

```kotlin
fun robustCompress(input: ByteArray): ByteArray? {
    return try {
        compressData(input)
    } catch (e: ZStreamException) {
        println("Compression failed: ${e.message}")
        null
    } catch (e: Exception) {
        println("Unexpected error: ${e.message}")
        null
    }
}
```

---

## Performance Considerations

### Buffer Sizing

- For compression, output buffer should be at least `input.size + (input.size >> 12) + (input.size >> 14) + 11`
- For decompression, output buffer size depends on compression ratio (typically 2-4x compressed size)
- Use streaming operations for large data to control memory usage

### Compression Level Trade-offs

- `Z_BEST_SPEED` (1): Fastest compression, larger output
- `Z_DEFAULT_COMPRESSION` (-1): Balanced speed/size
- `Z_BEST_COMPRESSION` (9): Smallest output, slower compression

### Memory Management

```kotlin
// Always call free() to release native resources
stream.free()

// Or use try-finally
try {
    // ... use stream
} finally {
    stream.free()
}
```

### Reusing Streams

```kotlin
// Reset stream for reuse instead of creating new instances
stream.deflateReset()  // or inflateReset()
```

---

## Thread Safety

**Important**: `ZStream` instances are **not thread-safe**. Each thread should use its own `ZStream` instance, or external synchronization must be provided.

---

## License

This API documentation is part of ZLib.kotlin, released under the [zlib License](../LICENSE).

---

*For more examples and usage patterns, see the [test files](../src/commonTest/kotlin/ai/solace/zlib/test/) in the project repository.*
