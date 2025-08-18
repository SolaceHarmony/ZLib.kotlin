# ZLib.kotlin CLI and pigz Integration Testing Guide

## Overview

This document provides comprehensive testing procedures for validating the integration between the ZLib.kotlin CLI tool and the pigz compression utility using the `-z` option (zlib format compression).

## Prerequisites

### Required Software

1. **pigz** - Parallel implementation of gzip with zlib format support
   ```bash
   # Install pigz (Ubuntu/Debian)
   sudo apt-get install pigz
   
   # Verify installation
   pigz --version
   ```

2. **ZLib.kotlin CLI executable** - Built from the project
   ```bash
   # Build the CLI executable
   ./gradlew linkDebugExecutableLinuxX64
   
   # The executable will be located at:
   # ./build/bin/linux/debugExecutable/zlib-cli.kexe
   ```

### Understanding pigz -z Option

The `-z` option in pigz creates files in zlib format (RFC 1950) instead of the default gzip format:

- **Default pigz**: Creates `.gz` files using gzip format
- **pigz -z**: Creates `.zz` files using zlib format (compatible with ZLib.kotlin)

## Automated Testing

### Quick Test

Run the comprehensive test script:

```bash
# Make the script executable
chmod +x scripts/test_cli_pigz_integration.sh

# Run all tests
./scripts/test_cli_pigz_integration.sh

# Run with verbose output
./scripts/test_cli_pigz_integration.sh -v

# Show help
./scripts/test_cli_pigz_integration.sh -h
```

The script automatically tests:
1. **Basic pigz -z functionality** - Compression and decompression cycle
2. **CLI decompression of pigz files** - ZLib.kotlin reads pigz -z output
3. **CLI compression verification** - pigz -d reads ZLib.kotlin output  
4. **Cross-compatibility matrix** - All combinations work
5. **Compression levels** - Different levels produce valid output
6. **Binary data handling** - Non-text files work correctly

### Test Output Example

```
========================================
ZLib.kotlin CLI and pigz Integration Tests
========================================

[INFO] Setting up test environment...
[SUCCESS] pigz found: /usr/bin/pigz
[INFO] pigz version: pigz 2.8
[SUCCESS] CLI executable found: ./build/bin/linux/debugExecutable/zlib-cli.kexe

Running Tests...
----------------
[TEST 1] Basic pigz -z compression
[INFO] Compressed /tmp/zlib_cli_pigz_tests/small.txt with pigz -z
[INFO] Verified zlib header (78xx)
[SUCCESS] pigz -z compression/decompression cycle successful

[TEST 2] CLI decompression of pigz -z compressed files
[INFO] Compressed with pigz -z
[SUCCESS] CLI successfully decompressed pigz -z file

... (additional test results)

========================================
Test Summary
========================================
Total Tests: 6
Passed:     6
Failed:     0
[SUCCESS] All tests passed!
```

## Manual Testing Procedures

### Test 1: Basic pigz -z Compression

Create a test file and verify pigz -z creates valid zlib format:

```bash
# Create test file
echo "Hello World! This is a test for ZLib compression." > test_input.txt

# Compress with pigz -z (zlib format)
pigz -z -c test_input.txt > test_input.txt.zz

# Verify zlib header (should start with 78xx)
hexdump -C test_input.txt.zz | head -1
# Expected output: 00000000  78 xx ... (zlib header)

# Decompress with pigz -d
pigz -d -c test_input.txt.zz > test_output.txt

# Verify files match
diff test_input.txt test_output.txt
# Should produce no output (files identical)
```

### Test 2: CLI Decompression of pigz Files

Test that ZLib.kotlin CLI can decompress pigz -z compressed files:

```bash
# Create and compress test file with pigz -z
echo "ZLib.kotlin can decompress pigz files!" > pigz_test.txt
pigz -z -c pigz_test.txt > pigz_test.txt.zz

# Decompress with ZLib.kotlin CLI
./build/bin/linux/debugExecutable/zlib-cli.kexe decompress pigz_test.txt.zz cli_output.txt

# Verify the result
diff pigz_test.txt cli_output.txt
# Should produce no output (files identical)

# Check file sizes
ls -la pigz_test.txt pigz_test.txt.zz cli_output.txt
```

### Test 3: CLI Compression Verified with pigz

Test that pigz can decompress ZLib.kotlin CLI compressed files:

```bash
# Create test file
echo "ZLib.kotlin CLI produces compatible zlib files!" > cli_test.txt

# Compress with ZLib.kotlin CLI
./build/bin/linux/debugExecutable/zlib-cli.kexe compress cli_test.txt cli_test.txt.zz

# Verify zlib header
hexdump -C cli_test.txt.zz | head -1
# Expected: 78xx header

# Decompress with pigz -d
pigz -d -c cli_test.txt.zz > pigz_output.txt

# Verify files match
diff cli_test.txt pigz_output.txt
# Should produce no output (files identical)
```

### Test 4: Cross-Compatibility Matrix

Comprehensive test of all combinations:

```bash
# Create test data
echo "Cross-compatibility test data for ZLib.kotlin and pigz integration." > cross_test.txt

# Test 1: CLI compress -> pigz decompress
./build/bin/linux/debugExecutable/zlib-cli.kexe compress cross_test.txt cli_compressed.zz
pigz -d -c cli_compressed.zz > cli_to_pigz.txt
diff cross_test.txt cli_to_pigz.txt

# Test 2: pigz compress -> CLI decompress  
pigz -z -c cross_test.txt > pigz_compressed.zz
./build/bin/linux/debugExecutable/zlib-cli.kexe decompress pigz_compressed.zz pigz_to_cli.txt
diff cross_test.txt pigz_to_cli.txt

echo "Cross-compatibility tests completed!"
```

### Test 5: Different Compression Levels

Test various compression levels work correctly:

```bash
# Create larger test file for better compression testing
{
    echo "ZLib Compression Level Testing"
    echo "=============================="
    for i in {1..100}; do
        echo "Line $i: This repetitive content should compress well at higher levels."
    done
} > compression_test.txt

# Test different compression levels
for level in 1 6 9; do
    echo "Testing compression level $level..."
    
    # Compress with CLI at specific level
    ./build/bin/linux/debugExecutable/zlib-cli.kexe compress compression_test.txt "level_${level}.zz" $level
    
    # Verify with pigz decompression
    pigz -d -c "level_${level}.zz" > "level_${level}_result.txt"
    
    # Check correctness
    if diff compression_test.txt "level_${level}_result.txt" > /dev/null; then
        echo "✓ Level $level: PASS"
        ls -la compression_test.txt "level_${level}.zz"
    else
        echo "✗ Level $level: FAIL"
    fi
done
```

### Test 6: Binary Data Handling

Test that binary data is handled correctly:

```bash
# Create binary test data
head -c 1024 /dev/urandom > binary_test.bin

# Test pigz round-trip
pigz -z -c binary_test.bin > binary_pigz.zz
pigz -d -c binary_pigz.zz > binary_pigz_result.bin
cmp binary_test.bin binary_pigz_result.bin && echo "pigz binary: PASS" || echo "pigz binary: FAIL"

# Test CLI round-trip (if executable available)
./build/bin/linux/debugExecutable/zlib-cli.kexe compress binary_test.bin binary_cli.zz
./build/bin/linux/debugExecutable/zlib-cli.kexe decompress binary_cli.zz binary_cli_result.bin
cmp binary_test.bin binary_cli_result.bin && echo "CLI binary: PASS" || echo "CLI binary: FAIL"

# Test cross-compatibility
pigz -d -c binary_cli.zz > binary_cross1.bin
cmp binary_test.bin binary_cross1.bin && echo "CLI->pigz binary: PASS" || echo "CLI->pigz binary: FAIL"

./build/bin/linux/debugExecutable/zlib-cli.kexe decompress binary_pigz.zz binary_cross2.bin
cmp binary_test.bin binary_cross2.bin && echo "pigz->CLI binary: PASS" || echo "pigz->CLI binary: FAIL"
```

## Understanding Test Results

### Success Indicators

1. **File Size Changes**: Compressed files should be smaller than originals (except for very small files)
2. **Header Verification**: All compressed files should start with `78xx` (zlib header)
3. **Bit-for-bit Accuracy**: Decompressed files must be identical to originals
4. **Cross-Compatibility**: Files compressed by one tool can be decompressed by the other

### Expected Compression Ratios

- **Repetitive text**: 60-80% compression
- **Diverse text**: 30-50% compression  
- **Binary/random data**: 0-10% compression (may even expand)

### Common Issues and Solutions

1. **"Header not found" errors**:
   - Verify the file was compressed with `-z` flag
   - Check file isn't corrupted: `hexdump -C file.zz | head -1`

2. **CLI executable not found**:
   - Build the executable: `./gradlew linkDebugExecutableLinuxX64`
   - Check path: `ls -la build/bin/linux/debugExecutable/zlib-cli.kexe`

3. **Permission errors**:
   - Make script executable: `chmod +x scripts/test_cli_pigz_integration.sh`

4. **Network issues during build**:
   - The automated tests work with just pigz even if CLI can't be built
   - Use manual testing procedures once CLI is available

## Integration with CI/CD

The test script can be integrated into continuous integration:

```bash
# In CI script
./scripts/test_cli_pigz_integration.sh
if [ $? -eq 0 ]; then
    echo "✅ pigz integration tests passed"
else
    echo "❌ pigz integration tests failed"
    exit 1
fi
```

## Performance Considerations

### Compression Speed vs Ratio

- **Level 1**: Fastest compression, larger files
- **Level 6**: Balanced (default)
- **Level 9**: Best compression, slower

### Memory Usage

- CLI tool: Uses internal buffers (typically 4-8KB)
- pigz: Can use multiple cores and larger buffers
- For large files: Consider streaming or chunked processing

## Troubleshooting Guide

### Debug Information

Enable verbose output in tests:
```bash
./scripts/test_cli_pigz_integration.sh -v
```

Check detailed compression information:
```bash
# Verbose pigz output
pigz -v -z input.txt

# Check CLI output
./zlib-cli --help
```

### File Format Analysis

Analyze compressed file structure:
```bash
# Show hex dump of compressed file
hexdump -C file.zz | head -5

# Show file information
file file.zz

# Compare compression ratios
ls -la input.txt file.zz
```

This comprehensive testing approach ensures full compatibility between ZLib.kotlin CLI and pigz -z functionality, validating both individual components and their integration.