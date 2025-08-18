# InfCodes Iteration Limit Fix

## Problem
The original implementation used a hardcoded `maxIterations = 10000` limit in `InfCodes.proc()` to prevent infinite loops. However, this limit was too small for large valid inputs and could cause premature `Z_DATA_ERROR` failures.

## Analysis
The iteration count in `InfCodes.proc()` is driven by the amount of data being decompressed. Each iteration processes one symbol from the compressed stream:
- **Literals**: 1 byte of output per iteration
- **Length/Distance pairs**: Up to 258 bytes of output per iteration

For a window size of 32KB (typical with `wbits=15`), in the worst case of all literals, we would need 32,768 iterations to fill the window. The old limit of 10,000 was clearly insufficient.

## Solution
Replace the hardcoded limit with a proportional limit based on window size:

```kotlin
val maxIterations = s.end * 4  // 4x window size provides safety margin
```

## New Iteration Limits
- **wbits=8** (256 bytes): 1,024 iterations 
- **wbits=15** (32,768 bytes): 131,072 iterations
- **Previous limit**: 10,000 iterations (inadequate for large windows)

## Safety Considerations
The 4x multiplier provides a reasonable safety margin:
1. **Base case**: In theory, `window_size` iterations could be needed
2. **Multiple blocks**: A stream may contain multiple compressed blocks
3. **State transitions**: Some iterations don't produce output but advance state
4. **Compression patterns**: Mixed literals and matches require varying iterations

## Testing
Added `LargeStreamIterationTest` to verify:
- Large streams (50KB) decompress without hitting iteration limits
- Small windows still have appropriate proportional limits
- The change doesn't affect normal operation

## Backwards Compatibility
This change only affects the internal iteration limit calculation. The external API remains unchanged, and valid streams that previously failed will now succeed.