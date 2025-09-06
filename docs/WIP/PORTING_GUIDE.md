# Pascal to Kotlin zlib Porting Guide

This guide outlines key considerations and mappings for porting the zlib library from Pascal to Kotlin.

## 1. Data Types

| Pascal            | Kotlin                        | Notes                                                               |
|-------------------|-------------------------------|---------------------------------------------------------------------|
| `Byte`            | `Byte`                        | 8-bit signed integer. For unsigned behavior, use `toUByte()`.       |
| `Word`            | `Short`                       | 16-bit signed integer. For unsigned, use `toUShort()`.              |
| `LongInt`         | `Int`                         | 32-bit signed integer.                                              |
| `uLong`           | `Long`                        | Use `Long` to hold unsigned 32-bit values. Mask with `0xFFFFFFFFL`. |
| `Pointer`         | `ByteArray`, `IntArray`, etc. | Direct pointer arithmetic is replaced by array indexing.            |
| `^` (dereference) | `[index]`                     | Pointer dereferencing becomes array access.                         |

## 2. Bitwise Operations

Kotlin's bitwise operations (`shl`, `shr`, `and`, `or`, `xor`) are similar to Pascal's, but it's crucial to handle signed vs. unsigned shifts correctly, especially with `shr`.

## 3. Control Structures

- **`case` statements:** Map to Kotlin's `when` expressions.
- **`for` and `while` loops:** Direct equivalents exist in Kotlin.
- **`goto`:** Avoid `goto`. Refactor into loops, conditionals, or functions. The original zlib code uses `goto` for state transitions; in Kotlin, this is managed with the `mode` variable and a `when` statement inside a `while` loop.

## 4. Unsigned 32-bit Integers (Adler-32 Checksum)

The Adler-32 checksum requires unsigned 32-bit arithmetic. In Kotlin, `Int` is signed. To correctly implement this:

- Use `Long` to store the checksum value.
- When reading byte data, convert it to `Long` and mask with `0xFFL` to prevent sign extension.
- After additions, mask the result with `0xFFFFFFFFL` to simulate 32-bit overflow.

**Pascal:**
```pascal
var adler: uLong;
...
adler := adler + (byteValue and $FF);
```

**Kotlin:**
```kotlin
var adler: Long = 0
adler = (adler + (byteValue.toLong() and 0xFFL)) and 0xFFFFFFFFL
```

## 5. State Machine

The Pascal implementation uses `goto` to jump between states. The Kotlin port uses a `while(true)` loop and a `when(mode)` expression to manage the state machine, which is a cleaner approach. Ensure that every state transition is correctly mapped.

## 6. Null Pointers vs. Nullable Types

Pascal's `nil` pointers map to Kotlin's nullable types (`?`). Always check for nulls.

By following these guidelines, the Kotlin port can more accurately reflect the logic of the original Pascal implementation while adhering to modern programming practices.
