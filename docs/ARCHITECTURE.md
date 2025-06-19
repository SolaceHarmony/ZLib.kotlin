# Architecture Document: ZLib Implementation

This document outlines the refactored architecture of the Kotlin ZLib implementation.

## Overview

The primary goal of the refactoring was to improve code organization, reduce file sizes, and separate concerns by:
- Centralizing constants.
- Extracting utility/helper functions.
- Moving inner classes to their own files.

## Key Components

### 1. Constants
- **`src/commonMain/kotlin/ai/solace/zlib/common/Constants.kt`**: Houses all global constants for the zlib operations (e.g., status codes, compression levels, algorithm-specific fixed values).

### 2. Core Logic Files (Illustrative - actual cleanup was partial)
- **`src/commonMain/kotlin/ai/solace/zlib/deflate/Deflate.kt`**: Contains the main logic for ZLib deflation (compression).
  - *Intended Refactoring*: Many helper functions were moved to `DeflateUtils.kt`. Original methods in `Deflate.kt` were intended to be cleaned up to delegate to these utilities, but this was deferred due to tooling issues.
- **`src/commonMain/kotlin/ai/solace/zlib/deflate/Inflate.kt`**: Contains the main logic for ZLib inflation (decompression).
- **`src/commonMain/kotlin/ai/solace/zlib/deflate/InfBlocks.kt`**: Manages inflation blocks.
  - *Intended Refactoring*: The `inflate_flush` utility was moved to `InfBlocksUtils.kt`. Original method in `InfBlocks.kt` was intended to be cleaned up, but this was deferred.
- **`src/commonMain/kotlin/ai/solace/zlib/deflate/Tree.kt`**: Logic for Huffman tree construction.
  - *Refactoring*: Utility functions moved to `TreeUtils.kt`. `Tree.kt` now contains wrappers or calls these directly.

### 3. Utility Modules
- **`src/commonMain/kotlin/ai/solace/zlib/deflate/DeflateUtils.kt`**: Contains helper functions extracted from `Deflate.kt`. These functions perform specific sub-tasks of the deflation process (e.g., byte/bit manipulation, block handling).
- **`src/commonMain/kotlin/ai/solace/zlib/deflate/InfBlocksUtils.kt`**: Contains helper functions extracted from `InfBlocks.kt` (currently `inflate_flush`).
- **`src/commonMain/kotlin/ai/solace/zlib/deflate/TreeUtils.kt`**: Contains helper functions for Huffman tree operations, extracted from `Tree.kt`.

### 4. Configuration
- **`src/commonMain/kotlin/ai/solace/zlib/deflate/Config.kt`**: Defines the `Config` class used to store deflation parameters for different compression levels.

### 5. Other Key Classes
- **`src/commonMain/kotlin/ai/solace/zlib/deflate/ZStream.kt`**: Manages the overall stream state for both compression and decompression.
- **`src/commonMain/kotlin/ai/solace/zlib/deflate/Adler32.kt`**: Implements the Adler-32 checksum algorithm.
- **`src/commonMain/kotlin/ai/solace/zlib/deflate/StaticTree.kt`**: Defines the static Huffman trees used in compression.
- **`src/commonMain/kotlin/ai/solace/zlib/deflate/InfCodes.kt`**: Handles Huffman codes during inflation.
- **`src/commonMain/kotlin/ai/solace/zlib/deflate/ZInputStream.kt`**: Provides an `InputStream` interface for ZLib operations.
- **`src/commonMain/kotlin/ai/solace/zlib/deflate/ZStreamException.kt`**: Custom exception class.


## Interactions (High-Level)

### Mermaid Diagram: Key Component Interactions

```mermaid
graph TD
    subgraph "Core Logic"
        Deflate["Deflate.kt"]
        Inflate["Inflate.kt"]
        InfBlocks["InfBlocks.kt"]
        Tree["Tree.kt"]
        ZStream["ZStream.kt"]
    end

    subgraph "Utility Modules"
        Constants["common/Constants.kt"]
        ConfigKt["Config.kt"]
        DeflateUtils["DeflateUtils.kt"]
        InfBlocksUtils["InfBlocksUtils.kt"]
        TreeUtils["TreeUtils.kt"]
    end

    subgraph "Data Structures & Algo"
        Adler32["Adler32.kt"]
        StaticTree["StaticTree.kt"]
        InfCodes["InfCodes.kt"]
    end

    subgraph "Stream Handling"
        ZInputStream["ZInputStream.kt"]
        ZStreamException["ZStreamException.kt"]
    end

    %% Relationships
    Deflate --> DeflateUtils
    Deflate --> ConfigKt
    Deflate --> Constants
    Deflate --> Tree
    Deflate --> ZStream
    Deflate --> StaticTree % For config_table and other direct uses

    Inflate --> Constants
    Inflate --> InfBlocks
    Inflate --> ZStream
    Inflate --> InfCodes

    InfBlocks --> InfBlocksUtils
    InfBlocks --> Constants
    InfBlocks --> ZStream
    InfBlocks --> InfCodes % InfBlocks creates InfCodes instance

    Tree --> TreeUtils
    Tree --> Constants
    Tree --> StaticTree % Accesses static_l_desc etc.

    ZStream --> Constants
    ZStream --> Adler32

    ZInputStream --> ZStream
    ZInputStream --> ZStreamException
    ZInputStream --> Constants % For Z_NO_FLUSH etc.

    DeflateUtils --> Constants
    InfBlocksUtils --> Constants
    TreeUtils --> Constants
    ConfigKt --> Constants % If Config uses any, good to show dependency
    StaticTree --> Constants
    InfCodes --> Constants
    Adler32 --> Constants
```

This diagram shows how `Deflate.kt` would ideally use `DeflateUtils.kt`, `InfBlocks.kt` use `InfBlocksUtils.kt`, and `Tree.kt` use `TreeUtils.kt`. It also shows their dependencies on `Constants.kt` and other components.
