# ZLib.kotlin Examples

This directory contains practical examples demonstrating how to use ZLib.kotlin for various compression and decompression scenarios.

## Examples Overview

### BasicExample.kt
A simple demonstration of basic compression and decompression operations:
- Basic text compression and decompression
- Error handling
- Data integrity verification
- Performance metrics

**Run with:**
```bash
./gradlew run -Pmain=examples.BasicExampleKt
```

### AdvancedExample.kt
Advanced compression techniques and performance analysis:
- Compression level comparison (speed vs. size trade-offs)
- Compression strategy comparison
- Streaming compression for large data
- Performance benchmarking

**Run with:**
```bash
./gradlew run -Pmain=examples.AdvancedExampleKt
```

## Key Concepts Demonstrated

### Compression Levels
- `Z_NO_COMPRESSION` (0): No compression, fastest
- `Z_BEST_SPEED` (1): Fast compression, larger output
- `Z_DEFAULT_COMPRESSION` (-1): Balanced approach
- `Z_BEST_COMPRESSION` (9): Maximum compression, slower

### Compression Strategies
- `Z_DEFAULT_STRATEGY`: General purpose compression
- `Z_FILTERED`: For data with small values and random distribution
- `Z_HUFFMAN_ONLY`: Only Huffman coding, no string matching

### Error Handling
- Always check return codes (`Z_OK`, `Z_STREAM_END`, etc.)
- Use try-finally blocks to ensure resource cleanup
- Handle `ZStreamException` for zlib-specific errors

### Memory Management
- Always call `stream.free()` to release resources
- Use appropriate buffer sizes to avoid memory issues
- Consider streaming for large data sets

## Sample Output

### Basic Example
```
=== ZLib.kotlin Compression Example ===
Original text length: 892 bytes

Compressed length: 394 bytes
Compression ratio: 2.26
Space saved: 55.8%

Decompressed length: 892 bytes
Data integrity check: PASSED

✅ Compression and decompression successful!
```

### Advanced Example
```
=== ZLib.kotlin Advanced Compression Example ===
Test data size: 2847 bytes

--- Compression Level Comparison ---
No compression (level 0):
  Size: 2855 bytes
  Ratio: 1.00
  Saved: -0.3%
  Time: 2ms

Best speed (level 1):
  Size: 1204 bytes
  Ratio: 2.36
  Saved: 57.7%
  Time: 5ms

Default (level -1):
  Size: 1089 bytes
  Ratio: 2.61
  Saved: 61.7%
  Time: 8ms

Best compression (level 9):
  Size: 1076 bytes
  Ratio: 2.65
  Saved: 62.2%
  Time: 12ms
```

## Usage Tips

1. **Choose the right compression level** based on your needs:
   - Real-time applications: Use `Z_BEST_SPEED`
   - Storage/archival: Use `Z_BEST_COMPRESSION`
   - General use: Use `Z_DEFAULT_COMPRESSION`

2. **Buffer sizing guidelines**:
   - Compression output buffer: `input.size + (input.size >> 12) + (input.size >> 14) + 11`
   - Decompression output buffer: Estimate based on expected expansion ratio

3. **Performance optimization**:
   - Reuse `ZStream` instances when possible (call `deflateReset()` or `inflateReset()`)
   - Use streaming for large data to control memory usage
   - Consider the trade-off between compression time and output size

4. **Error handling best practices**:
   - Always check return codes
   - Use try-finally blocks for resource cleanup
   - Handle specific error conditions appropriately

## Building and Running

Make sure you have the ZLib.kotlin library available in your classpath. The examples can be run directly or integrated into your own projects.

For Gradle projects, add the examples to your `src/main/kotlin` directory and configure the main class in your `build.gradle.kts`:

```kotlin
application {
    mainClass.set("examples.BasicExampleKt")
    // or
    mainClass.set("examples.AdvancedExampleKt")
}
```

## Contributing

Feel free to contribute additional examples that demonstrate other use cases or advanced features of ZLib.kotlin!
