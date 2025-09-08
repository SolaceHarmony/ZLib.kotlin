# BitShift Engine - Universal Bit Operations

This document describes the improved bit shift operations implemented to address issue #20.

## Overview

The BitShift Engine provides a unified interface for bit shift operations that can use either:
- **Native operations** (using Kotlin's built-in `shl`, `shr`, `ushr`) - faster but may have platform differences
- **Arithmetic operations** - slower but mathematically consistent across all platforms

This is particularly important for algorithms like Adler32 and other legacy operations that were developed for 8-bit or 16-bit systems and are sensitive to bit operation inconsistencies.

## Key Features

### 1. Unified Interface
```kotlin
val engine = BitShiftEngine(BitShiftMode.NATIVE, 32)
val result = engine.leftShift(0x12345678L, 4)
println("Value: 0x${result.value.toString(16)}, Overflow: ${result.overflow}, Carry: ${result.carry}")
```

### 2. Carry and Overflow Detection
```kotlin
val engine8 = BitShiftEngine(BitShiftMode.ARITHMETIC, 8)
val result = engine8.leftShift(255, 1)  // Will overflow 8-bit
// result.overflow == true, result.carry contains overflow bits
```

### 3. Configurable Bit Width
- 8-bit operations for legacy byte-oriented algorithms
- 16-bit operations for segment:offset addressing and 16-bit checksums  
- 32-bit operations for modern algorithms
- 64-bit operations for large data processing

### 4. Adler32 Interop
```kotlin
// Adler-32 checksum (platform-consistent arithmetic implementation)
val checksum = Adler32Utils.adler32(1L, data, 0, data.size)
```

Notes:
- Adler32Utils internally uses arithmetic-only operations and does not accept a BitShiftEngine.
- Use BitShiftEngine for bit-shift behavior studies or when you need carry/overflow metadata, not for Adler32.

## Usage Examples

### Basic Operations
```kotlin
val engine = BitShiftEngine(BitShiftMode.NATIVE, 32)

// Left shift with overflow detection
val leftResult = engine.leftShift(0x80000000L, 1)
if (leftResult.overflow) {
    println("Overflow detected! Carry: ${leftResult.carry}")
}

// Right shift (arithmetic)
val rightResult = engine.rightShift(-1, 1)  // Sign-extending

// Unsigned right shift
val unsignedResult = engine.unsignedRightShift(-1, 1)  // Zero-filling
```

### Cross-Platform Consistency
```kotlin
val nativeEngine = BitShiftEngine(BitShiftMode.NATIVE, 32)
val arithmeticEngine = BitShiftEngine(BitShiftMode.ARITHMETIC, 32)

val value = 0x12345678L
val nativeResult = nativeEngine.leftShift(value, 4)
val arithmeticResult = arithmeticEngine.leftShift(value, 4)

assert(nativeResult.value == arithmeticResult.value) // Always true
```

### Legacy Algorithm Support
```kotlin
// 8-bit CRC calculation
val engine8 = BitShiftEngine(BitShiftMode.ARITHMETIC, 8)
var crc = 0L
val polynomial = 0x07L

for (byte in data) {
    val shifted = engine8.leftShift(crc, 1)
    crc = shifted.value xor (byte.toLong() and 0xFF)
    if (crc > 0x7F) {
        crc = crc xor polynomial
    }
}
```

### Performance Notes
- Native mode uses CPU bit shifts (fastest).
- Arithmetic mode uses addition/multiplication/division to emulate shifts; portable and deterministic across platforms.
- See benchmark-style assertions in tests (e.g., ImprovedBitShiftTest) for expected equivalence, not microbenchmarks.

## Migration Guide

### From BitwiseOps.urShift()
```kotlin
// Old way (may have platform inconsistencies)
val oldResult = BitwiseOps.urShift(value, shift)

// New way (consistent across platforms)
val newResult = BitwiseOps.urShiftImproved(value, shift)

// Or with explicit engine choice
val engine = BitShiftEngine(BitShiftMode.ARITHMETIC, 32)
val consistentResult = BitwiseOps.urShiftImproved(value, shift, engine)
```

### From BitUtils.urShift()
```kotlin
// Old way (legacy compatibility maintained)
val oldResult = BitUtils.urShift(value, shift)

// New way (improved consistency)
val newResult = BitUtils.urShiftImproved(value, shift)

// With custom engine
val arithmeticEngine = BitUtils.withArithmeticMode()
val consistentResult = BitUtils.urShiftImproved(value, shift, arithmeticEngine)
```

## Testing and Validation

### Comprehensive Test Suite
- `BitShiftSandboxTest`: Validates consistency between operation modes
- `Adler32EngineTest`: Tests checksum calculation with both engines
- `ImprovedBitShiftTest`: Validates bug fixes and mathematical correctness
- `BitUtilsImprovedTest`: Tests enhanced utility functions

### Running the Sandbox
```kotlin
// In tests or demo code
BitShiftSandbox.runAllDemonstrations()

// Or individual demonstrations
BitShiftSandbox.demonstrateBasicOperations()
BitShiftSandbox.demonstrateCarryAndOverflow()
BitShiftSandbox.demonstrateAdler32Integration()
```

## Architecture

### ShiftResult Type
```kotlin
data class ShiftResult(
    val value: Long,        // The shifted value
    val carry: Long = 0,    // Any carry/overflow bits
    val overflow: Boolean = false  // Whether overflow occurred
)
```

### Engine Configuration
```kotlin
enum class BitShiftMode {
    NATIVE,      // Use Kotlin's built-in shl, shr, ushr operations
    ARITHMETIC   // Use pure arithmetic operations
}

class BitShiftEngine(
    val mode: BitShiftMode = BitShiftMode.NATIVE,
    val bitWidth: Int = 32
)
```

## Performance Characteristics

- **Native Mode**: Faster execution, uses CPU bit shift instructions
- **Arithmetic Mode**: Slower but mathematically consistent across all platforms
- **Recommended**: Use Native for performance-critical code, Arithmetic for cross-platform consistency

## Backward Compatibility

All existing APIs remain functional:
- `BitwiseOps.urShift()` - legacy implementation preserved
- `BitUtils.urShift()` - legacy implementation preserved
- New `*Improved()` methods provide enhanced functionality
- Factory functions allow easy migration to new approaches

## Bug Fixes Addressed

1. **Inconsistent urShift behavior** for negative numbers across platforms
2. **Poor edge case handling** for shifts >= bit width or negative shifts
3. **Platform-specific hardcoded values** replaced with mathematical calculations
4. **Missing carry/overflow information** now available through ShiftResult
5. **Lack of configurable operation modes** now addressed with BitShiftEngine

## Future Considerations

The BitShiftEngine provides a foundation for:
- Additional bit manipulation operations
- Performance optimizations for specific platforms
- Integration with more legacy algorithms
- Custom bit width operations beyond 8/16/32/64

For more details, see the comprehensive test suite and sandbox demonstrations.