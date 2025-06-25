# ZLib Deflate/Inflate Algorithm Annotations

This document provides detailed annotations on how our Kotlin implementation of ZLib follows the original C implementation's algorithms, with specific code examples and explanations of the implementation choices.

## 1. Deflate Compression Algorithm: Deep Dive

### 1.1 LZ77 Variation Implementation

The DEFLATE algorithm is fundamentally an LZ77 variant combined with Huffman encoding. Here's how our implementation handles the core LZ77 string-matching and replacement process:

#### 1.1.1 Hash Chain Structure

In our Kotlin implementation, we maintain hash chains using two key arrays:
- `head`: Maps hash values to the most recent string position with that hash
- `prev`: Links to previous positions with the same hash, forming a chain

```kotlin
// Key data structures for hash chains
internal lateinit var window: ByteArray    // Sliding window for input data
internal lateinit var prev: ShortArray     // Previous match for a position
internal lateinit var head: ShortArray     // Start of hash chain for each hash value
internal var hashSize: Int = 0           // Size of hash table
internal var hashMask: Int = 0           // Mask for hash function
internal var hashShift: Int = 0          // Number of bits to shift in hash function
```

#### 1.1.2 Hash Function Implementation

Our hash function is critical for finding repeated strings efficiently:

```kotlin
// Hash calculation (from Deflate.kt, fillWindow method)
insH = window[strStart].toInt() and 0xff
insH = (((insH shl hashShift) xor (window[strStart + 1].toInt() and 0xff)) and hashMask)

// When adding a string to the hash table (from deflateFast/deflateSlow)
insH = (((insH shl hashShift) xor (window[(strStart) + (MIN_MATCH - 1)].toInt() and 0xff)) and hashMask)
hashHead = (head[insH].toInt() and 0xffff)
prev[strStart and wMask] = head[insH]
head[insH] = strStart.toShort()
```

This 3-byte rolling hash is critical for performance as it allows us to quickly identify potential matches without comparing entire strings.

#### 1.1.3 String Matching Flow

```
┌───────────────────┐
│ Input byte stream │
└─────────┬─────────┘
          ▼
┌─────────────────────┐
│ Calculate hash for  │
│ next 3-byte sequence│
└─────────┬───────────┘
          ▼
┌─────────────────────┐
│ Look up hash in     │
│ head[] table        │
└─────────┬───────────┘
          ▼
    ┌─────────────┐     Yes    ┌─────────────────────┐
    │ Match found?│─────────►  │ Follow chain using  │
    └──────┬──────┘            │ prev[] to find best │
           │                   │ match (longestMatch)│
           │ No               └──────────┬──────────┘
           ▼                             ▼
┌─────────────────────┐      ┌─────────────────────┐
│ Output literal byte │      │ Output length and   │
└─────────────────────┘      │ distance pair       │
                             └─────────────────────┘
```

### 1.2 Longest Match Algorithm - Enhanced Implementation

The `longestMatch` function is the heart of the LZ77 compression, and we've carefully optimized it based on the original zlib implementation. Here's our improved Kotlin implementation:

```kotlin
internal fun longestMatch(curMatchIn: Int): Int {
    var curMatch = curMatchIn
    var chainLength = maxChainLength
    var scan = strStart
    var match: Int
    var len: Int
    var bestLen = prevLength
    val limit = if (strStart > wSize - MIN_LOOKAHEAD) strStart - (wSize - MIN_LOOKAHEAD) else 0
    // IMPORTANT: We create a local copy of niceMatch that won't affect the instance variable
    var localNiceMatch = niceMatch
    val wmask = wMask
    val strend = strStart + MAX_MATCH
    var scanEnd1 = window[scan + bestLen - 1]
    var scanEnd = window[scan + bestLen]

    // Early termination optimization if we already have a good match
    if (prevLength >= goodMatch) {
        chainLength = chainLength shr 2
    }

    // Do not look beyond the end of available input
    if (localNiceMatch > lookAhead) {
        localNiceMatch = lookAhead
    }

    // Main matching loop
    do {
        match = curMatch
        
        // Quick check to eliminate obvious non-matches
        if (window[match + bestLen] != scanEnd || 
            window[match + bestLen - 1] != scanEnd1 || 
            window[match] != window[scan] || 
            window[++match] != window[scan + 1]) {
            curMatch = prev[curMatch and wmask].toInt() and 0xffff
            continue
        }

        // Move past the first two bytes that we already checked
        scan += 2
        match++
        
        // Performance optimization: unwrap the loop to compare 8 bytes at a time
        while (
            window[++scan] == window[++match] && 
            window[++scan] == window[++match] &&
            window[++scan] == window[++match] && 
            window[++scan] == window[++match] &&
            window[++scan] == window[++match] && 
            window[++scan] == window[++match] &&
            window[++scan] == window[++match] && 
            window[++scan] == window[++match] &&
            scan < strend
        ) {
            // No body needed - all the work is done in the condition
        }

        // Calculate match length
        len = MAX_MATCH - (strend - scan)
        scan = strend - MAX_MATCH

        // Update best match if this one is better
        if (len > bestLen) {
            matchStart = curMatchIn
            bestLen = len
            
            // Early exit for sufficiently good matches
            if (len >= localNiceMatch) break
            
            // Update end markers for next comparison
            scanEnd1 = window[scan + bestLen - 1]
            scanEnd = window[scan + bestLen]
        }
        
        curMatch = prev[curMatch and wmask].toInt() and 0xffff
    } while (curMatch > limit && --chainLength != 0)

    return if (bestLen <= lookAhead) bestLen else lookAhead
}
```

#### 1.2.1 Key Optimizations in longestMatch

1. **Local niceMatch Variable**:
   - We carefully maintain a local copy of `niceMatch` to prevent modifying the class-level variable
   - This ensures that the threshold for "nice" matches remains consistent across multiple calls
   - Without this fix, the compression ratio could degrade as the algorithm might prematurely terminate match searches

2. **Early Chain Termination**:
   - When we already have a match better than `goodMatch`, we reduce the chain length to 25% of its original value
   - This significantly speeds up compression when good matches are common

3. **Quick Rejection Check**:
   - Before doing a full byte-by-byte comparison, we check a few key positions:
     1. The end of the potential match (where the previous best match ended)
     2. The very beginning of the match
   - If these don't match, we immediately skip to the next candidate
   - This avoids expensive comparisons for strings that obviously won't match

4. **Loop Unrolling**:
   - The inner loop is unrolled to compare 8 bytes at once
   - This reduces loop overhead and improves performance dramatically
   - The CPU's branch prediction works better with fewer conditional branches

5. **Deterministic Behavior**:
   - We never search beyond the end of available input (`lookAhead`)
   - This ensures deterministic compression regardless of the input

#### 1.2.2 Match Length Calculation

Once we've found the longest possible match, we calculate its length:

```kotlin
len = MAX_MATCH - (strend - scan)
```

This calculation handles cases where:
- We reached the maximum match length (258 bytes)
- We hit the end of the available input data
- We found a mismatch that terminated the comparison

#### 1.2.3 Visual Representation of String Matching

```
Window:      [...........................................]
             ^                  ^                      ^
             |                  |                      |
             0                strStart               window_size

Current:                       [abcdefgh..........]
                                ^       ^
                                |       |
                             strStart   strStart+lookAhead

Hash lookup:                   [abc] -> position X
                                ^
                                |
                             strStart

Match found:  [abc.......]
             ^
             |
           Position X

Compare:      [abcdefgh]    <-- Current string at strStart
             [abcdefgh]     <-- Found string at position X
```

This visualization shows how the sliding window allows us to find matches within previously processed data, which is the essence of LZ77 compression.

### 3.2 Window Sliding in fillWindow

When sliding the window in `fillWindow`, there's a critical sequence:

```kotlin
window.copyInto(window, 0, wSize, wSize)
matchStart -= wSize
strStart -= wSize
blockStart -= wSize
```

This correctly moves the second half of the window to the beginning, but there's a subtle detail in how the hash chains are updated after the window slides. Each hash chain entry needs to be adjusted by subtracting the window size, or cleared if it would point before the start of the buffer.

The way your code handles this is:

```kotlin
n = hashSize
p = n
do {
    m = (head[--p].toInt() and 0xffff)
    head[p] = if (m >= wSize) (m - wSize).toShort() else 0.toShort()
} while (--n != 0)
```

This looks correct, but it's worth double-checking that all pointers in the hash chains are properly adjusted, especially when working with large files.

### 1.3 Bit-Level Operations and Buffer Management

Bit-level operations are critical for efficient compression. Our implementation carefully manages bit buffers to ensure proper packing of variable-length codes:

#### 1.3.1 Bit Buffer Management in sendBits Function

```kotlin
internal fun sendBits(d: Deflate, value: Int, length: Int) {
    if (length == 0) return

    val bufSize = 16  // Bits in a Short * 2
    val oldBiValid = d.biValid

    // If there's not enough room in the current buffer for all the bits
    if (oldBiValid > bufSize - length) {
        // First, fill the current buffer with as many bits as will fit
        val bitsInCurrentBuf = bufSize - oldBiValid
        val bitsForNextBuf = length - bitsInCurrentBuf

        // Fill the current buffer with the low-order bits that fit
        val lowBits = if (bitsInCurrentBuf >= 31)
            value
        else
            value and ((1 shl bitsInCurrentBuf) - 1)

        val biBufVal = d.biBuf.toInt() and 0xffff
        val combinedVal = biBufVal or (lowBits shl oldBiValid)

        // Flush the full buffer
        putShort(d, combinedVal)

        // Store remaining high-order bits in the new buffer
        d.biBuf = (value ushr bitsInCurrentBuf).toShort()
        d.biValid = bitsForNextBuf
    } else {
        // Enough room in the current buffer, just add the bits
        val mask = if (length >= 31)
            -1 // All bits set if length would cause overflow
        else
            (1 shl length) - 1

        val biBufInt = (d.biBuf.toInt() and 0xffff) or ((value and mask) shl oldBiValid)
        d.biBuf = biBufInt.toShort()
        d.biValid = oldBiValid + length
    }
}
```

#### 1.3.2 Key Implementation Considerations

1. **Handling Buffer Overflow**:
   - Unlike C, Kotlin's Int type is signed, requiring careful handling of bit-shift operations
   - We use explicit bit masking with `and 0xffff` to simulate unsigned 16-bit integers
   - Special handling for shifts with large values (>= 31) prevents silent overflow errors

2. **Buffer Splitting Strategy**:
   - When bits don't fit in the current buffer:
     1. Fill the remaining space in the current buffer with lower bits
     2. Flush the full buffer to the output
     3. Store the remaining higher bits in the next buffer

3. **Performance Optimization**:
   - Direct bit manipulation is used rather than bit-by-bit operations
   - Buffer flushes only occur when necessary (buffer is full)

4. **Correctness Guarantees**:
   - All paths ensure that `biValid` correctly tracks the number of valid bits
   - Proper handling of edge cases (length = 0, large shifts, etc.)

This approach ensures that our Huffman codes and other variable-length bit sequences are correctly packed into the output stream with minimal overhead.
