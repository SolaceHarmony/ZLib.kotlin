# ZLib.kotlin API (Current, 2025-09-07)

This section documents the current, ground-truth API based on the code under src/commonMain. The legacy Java-style ZStream API documented below is retained only for historical reference and does not reflect the current implementation.

- Kotlin version: 2.1.20
- Platforms: Multiplatform (Native targets configured in Gradle)
- IO: Okio BufferedSource/BufferedSink for streaming

Contents (current API):
- Compression/Decompression
- Streams (bit-level)
- Bitwise utilities and engine
- Checksum
- CLI
- Constants
- Examples

---

## Compression/Decompression

Package: ai.solace.zlib.deflate / ai.solace.zlib.inflate

- DeflateStream.compressZlib(source: okio.BufferedSource, sink: okio.BufferedSink, level: Int = 6): Long
  - Compresses from source to sink with a zlib wrapper. Returns the number of input bytes consumed.
  - level <= 0 uses stored blocks, 1 uses fixed Huffman, >=2 uses dynamic Huffman.

- InflateStream.inflateZlib(source: okio.BufferedSource, sink: okio.BufferedSink): Pair<Int, Long>
  - Decompresses a zlib stream from source to sink.
  - Returns (resultCode, bytesWritten). resultCode is ai.solace.zlib.common.Z_OK on success.

Example:
```kotlin
val inPath = "input.txt".toPath()
val outPath = "output.zz".toPath()
FileSystem.SYSTEM.source(inPath).buffer().use { src ->
    FileSystem.SYSTEM.sink(outPath).buffer().use { snk ->
        val bytesIn = DeflateStream.compressZlib(src, snk, level = 6)
        println("Compressed $bytesIn bytes")
    }
}

// Decompress
val outTxt = "output.txt".toPath()
FileSystem.SYSTEM.source(outPath).buffer().use { src ->
    FileSystem.SYSTEM.sink(outTxt).buffer().use { snk ->
        val (rc, bytesOut) = InflateStream.inflateZlib(src, snk)
        check(rc == Z_OK)
        println("Decompressed $bytesOut bytes")
    }
}
```

---

## Streams (bit-level)

Package: ai.solace.zlib.inflate

- class StreamingBitReader(source: okio.BufferedSource)
  - peek(n: Int): Int // 0..16 bits (LSB-first)
  - take(n: Int): Int // consume n bits
  - alignToByte()
  - readAlignedByte(): Int
  - peekBytes(count: Int): ByteArray // diagnostics; may return empty

- class StreamingBitWriter(sink: okio.BufferedSink)
  - writeBits(value: Int, count: Int)
  - alignToByte()
  - flush()
  - bitMod8(): Int

---

## Bitwise utilities and engine

Package: ai.solace.zlib.bitwise

- enum class BitShiftMode { NATIVE, ARITHMETIC }
- data class ShiftResult(value: Long, carry: Long = 0, overflow: Boolean = false)
- class BitShiftEngine(mode: BitShiftMode = NATIVE, bitWidth: Int = 32)
  - leftShift(value: Long, bits: Int): ShiftResult
  - rightShift(value: Long, bits: Int): ShiftResult
  - unsignedRightShift(value: Long, bits: Int): ShiftResult
  - normalize(value: Long): Long
  - withMode(newMode: BitShiftMode): BitShiftEngine
  - withBitWidth(newBitWidth: Int): BitShiftEngine

- object BitwiseOps (top-level functions in file)
  - createMask(bits: Int): Int
  - extractBits(value: Int, bits: Int): Int
  - extractBitRange(value: Int, startBit: Int, bitCount: Int): Int
  - combine16Bit(high: Int, low: Int): Int
  - combine16BitToLong(high: Long, low: Long): Long
  - getHigh16Bits(value: Int): Int; getLow16Bits(value: Int): Int
  - byteToUnsignedInt(b: Byte): Int
  - getHigh16BitsArithmetic(value: Long): Int; getLow16BitsArithmetic(value: Long): Int
  - combine16BitArithmetic(high: Int, low: Int): Long
  - leftShiftArithmetic(value: Int, bits: Int): Int; rightShiftArithmetic(value: Int, bits: Int): Int
  - createMaskArithmetic(bits: Int): Int; extractBitsArithmetic(value: Int, bits: Int): Int
  - isBitSetArithmetic(value: Int, bitPosition: Int): Boolean
  - orArithmetic(value1: Int, value2: Int): Int
  - orArithmeticGeneral(value1: Int, value2: Int): Int
  - rotateLeft(value: Int, bits: Int): Int; rotateRight(value: Int, bits: Int): Int
  - withArithmeticEngine(): ai.solace.zlib.bitwise.ArithmeticBitwiseOps
  - urShiftImproved(number: Int, bits: Int, engine: BitShiftEngine = defaultEngine32): Int
  - urShiftImproved(number: Long, bits: Int, engine: BitShiftEngine = defaultEngine64): Long

- class ArithmeticBitwiseOps(bitLength: Int)
  - normalize(value: Long): Long; leftShift(value: Long, bits: Int): Long; rightShift(value: Long, bits: Int): Long
  - createMask(bits: Int): Long; isPowerOfTwo(n: Long): Boolean; estimateMaxBitsFor(value: Long): Int
  - extractBits(value: Long, bits: Int): Long; isBitSet(value: Long, bitPosition: Int): Boolean
  - or(value1: Long, value2: Long): Long; and(value1: Long, value2: Long): Long; xor(value1: Long, value2: Long): Long; not(value: Long): Long
  - rotateLeft(value: Long, positions: Int): Long; rotateRight(value: Long, positions: Int): Long
  - toUnsigned(value: Long): Long; toSigned(value: Long): Long
  - Companion presets: ArithmeticBitwiseOps.BITS_32

- class BitBuffer
  - getBuffer(): Int; getBitCount(): Int; addByte(b: Byte): Int
  - peekBits(bits: Int): Int; consumeBits(bits: Int): Int; hasEnoughBits(bits: Int): Boolean; reset()

---

## Checksum

Package: ai.solace.zlib.bitwise.checksum

- object Adler32Utils
  - adler32(adler: Long, buf: ByteArray?, index: Int, len: Int): Long
    - Uses arithmetic-only logic and Byte->Int conversion via BitwiseOps.byteToUnsignedInt.

---

## CLI

Package: ai.solace.zlib.cli

- fun main(args: Array<String>)
  - Commands:
    - compress|deflate <input> <output.zz> [level]
    - decompress|inflate <input.zz> <output>
    - log-on | log-off | help
  - Uses Okio FileSystem to read/write files.

---

## Constants

Package: ai.solace.zlib.common.Constants (file)

Key constants you will typically use:
- Compression levels: Z_NO_COMPRESSION(0), Z_BEST_SPEED(1), Z_BEST_COMPRESSION(9), Z_DEFAULT_COMPRESSION(-1)
- Strategies: Z_DEFAULT_STRATEGY(0), Z_FILTERED(1), Z_HUFFMAN_ONLY(2)
- Flush: Z_NO_FLUSH(0), Z_PARTIAL_FLUSH(1), Z_SYNC_FLUSH(2), Z_FULL_FLUSH(3), Z_FINISH(4)
- Return codes: Z_OK(0), Z_STREAM_END(1), Z_NEED_DICT(2), Z_ERRNO(-1), Z_STREAM_ERROR(-2), Z_DATA_ERROR(-3), Z_MEM_ERROR(-4), Z_BUF_ERROR(-5), Z_VERSION_ERROR(-6)
- Window bits: MAX_WBITS = 15 (32KiB)
- Method: Z_DEFLATED = 8

For the full list (Huffman tables, tree parameters, state codes, etc.), see the Constants.kt file.

---

## Examples

- See examples/BasicExample.kt and examples/AdvancedExample.kt for end-to-end usage.
- Unit tests under src/commonTest/kotlin demonstrate edge cases and exact semantics for bitwise operations, shifts, and Adler-32.

---

## Legacy (Java-style) API — historical reference

The content below documents an older design (ZStream/ZInputStream) and does not match the current implementation. Prefer the sections above for the real API.

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
val zinput = ZInputStream(inputStream)
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
throw ZStreamException("message")
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

This API documentation is part of ZLib.kotlin, licensed under the [Apache License 2.0](../LICENSE). See [NOTICE](../NOTICE) for attribution and third-party notices.

---

*For more examples and usage patterns, see the test file [Adler32Test.kt](../src/commonTest/kotlin/ai/solace/zlib/test/Adler32Test.kt) and other tests in that directory.*
