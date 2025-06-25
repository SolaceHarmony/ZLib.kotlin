@file:OptIn(ExperimentalUnsignedTypes::class)
package ai.solace.zlib.common

/**
 * This file houses constants extracted from various zlib implementation files.
 * It provides centralized access to all constants used throughout the library.
 */

/**
 * The version string for the zlib library implementation
 */
private const val version_Renamed_Field: String = "1.0.2"

/**
 * Returns the version string of this zlib implementation
 *
 * @return Version string of the library
 */
fun version(): String {
    return version_Renamed_Field
}

/**
 * Compression levels
 * 
 * Z_NO_COMPRESSION = 0: No compression, data is stored without compression
 * Z_BEST_SPEED = 1: Fastest compression with lower compression ratio
 * Z_BEST_COMPRESSION = 9: Best compression ratio but slower performance
 * Z_DEFAULT_COMPRESSION = -1: Default compression level, balance between speed and compression
 */
const val Z_NO_COMPRESSION = 0
const val Z_BEST_SPEED = 1
const val Z_BEST_COMPRESSION = 9
const val Z_DEFAULT_COMPRESSION = -1

/**
 * Compression strategy constants
 * 
 * Z_DEFAULT_STRATEGY = 0: Default compression strategy, good for general purpose compression
 * Z_FILTERED = 1: Filtered compression strategy, good for data produced by a filter or predictor
 * Z_HUFFMAN_ONLY = 2: Huffman-only strategy, forces Huffman encoding only (no string match)
 */
const val Z_DEFAULT_STRATEGY = 0
const val Z_FILTERED = 1
const val Z_HUFFMAN_ONLY = 2

/**
 * Flush mode constants
 * 
 * Z_NO_FLUSH = 0: No flush, normal compression operation
 * Z_PARTIAL_FLUSH = 1: Partial flush, for advanced compression only
 * Z_SYNC_FLUSH = 2: Sync flush, flushes to byte boundary for synchronization. Returns to compressing from current point
 * Z_FULL_FLUSH = 3: Full flush, like sync flush but resets compression state. Can be used for recovering from errors
 * Z_FINISH = 4: Finish compression stream, indicating all input has been provided
 */
const val Z_NO_FLUSH = 0
const val Z_PARTIAL_FLUSH = 1
const val Z_SYNC_FLUSH = 2
const val Z_FULL_FLUSH = 3
const val Z_FINISH = 4

/**
 * Return and error codes
 * 
 * Z_OK = 0: Operation completed successfully
 * Z_STREAM_END = 1: End of stream reached
 * Z_NEED_DICT = 2: Dictionary needed for decompression
 * Z_ERRNO = -1: System error (file operations, etc.)
 * Z_STREAM_ERROR = -2: Stream structure was inconsistent (invalid initialization or internal state corruption)
 * Z_DATA_ERROR = -3: Input data was corrupted or incomplete
 * Z_MEM_ERROR = -4: Not enough memory to complete operation
 * Z_BUF_ERROR = -5: No progress possible or buffer error (no more output space or no more input data)
 * Z_VERSION_ERROR = -6: Incompatible zlib library version
 */
const val Z_OK = 0
const val Z_STREAM_END = 1
const val Z_NEED_DICT = 2
const val Z_ERRNO = -1
const val Z_STREAM_ERROR = -2
const val Z_DATA_ERROR = -3
const val Z_MEM_ERROR = -4
const val Z_BUF_ERROR = -5
const val Z_VERSION_ERROR = -6

// Adler32 constants
const val ADLER_BASE = 65521 // largest prime smaller than 65536
const val ADLER_NMAX = 3854 // NMAX is the largest n such that 255n(n+1)/2 + (n+1)(BASE-1) <= 2^31-1

/**
 * Constants for InfBlocks state machine
 * 
 * IBLK_TYPE = 0: Initial block header reading state
 * IBLK_LENS = 1: Reading length for stored block
 * IBLK_STORED = 2: Copying stored block data
 * IBLK_TABLE = 3: Reading table for dynamic block
 * IBLK_BTREE = 4: Building bit length tree
 * IBLK_DTREE = 5: Building distance tree
 * IBLK_CODES = 6: Processing codes for dynamic block
 * IBLK_DRY = 7: Flushing remaining output
 * IBLK_DONE = 8: Block processing complete
 * IBLK_BAD = 9: Error state - invalid block data
 * IBLK_MANY = 1440: Maximum number of code tree entries
 */
const val IBLK_TYPE = 0
const val IBLK_LENS = 1
const val IBLK_STORED = 2
const val IBLK_TABLE = 3
const val IBLK_BTREE = 4
const val IBLK_DTREE = 5
const val IBLK_CODES = 6
const val IBLK_DRY = 7
const val IBLK_DONE = 8
const val IBLK_BAD = 9
const val IBLK_MANY = 1440

// Arrays needed for InfBlocks
val IBLK_BORDER = intArrayOf(16, 17, 18, 0, 8, 7, 9, 6, 10, 5, 11, 4, 12, 3, 13, 2, 14, 1, 15)

// Constants for Tree
const val MAX_BITS = 15
const val BL_CODES = 19
const val D_CODES = 30
const val LITERALS = 256
const val LENGTH_CODES = 29
const val L_CODES = LITERALS + 1 + LENGTH_CODES
const val HEAP_SIZE = 2 * L_CODES + 1
const val TREE_MAX_BL_BITS = 7 // Formerly MAX_BL_BITS in Tree.kt
const val END_BLOCK = 256
const val REP_3_6 = 16
const val REPZ_3_10 = 17
const val REPZ_11_138 = 18
const val BUF_SIZE = 8 * 2 // Formerly Buf_size in Tree.kt
const val TREE_DIST_CODE_LEN = 512 // Formerly DIST_CODE_LEN in Tree.kt

/**
 * Constants for Deflate compression
 * 
 * MAX_MEM_LEVEL = 9: Maximum memory level for deflate
 * MAX_WBITS = 15: Maximum window bits (15 = 32KB window). This determines the size of the sliding window for LZ77 compression
 * DEF_MEM_LEVEL = 8: Default memory level for deflate compression. Balances memory usage and compression performance
 */
const val MAX_MEM_LEVEL = 9
const val MAX_WBITS = 15
const val DEF_MEM_LEVEL = 8
/**
 * Error messages corresponding to zlib error codes.
 * 
 * The array is indexed using the negative of the error code minus 2.
 * For example, Z_NEED_DICT (2) corresponds to index 0,
 * Z_STREAM_ERROR (-2) corresponds to index 4, etc.
 */
val Z_ERRMSG = arrayOf(
    "need dictionary",
    "stream end",
    "",
    "file error",
    "stream error",
    "data error",
    "insufficient memory",
    "buffer error",
    "incompatible version",
    ""
)
/** 
 * Block processing result codes
 * 
 * NEED_MORE = 0: Block not completed, need more input or more output
 * BLOCK_DONE = 1: Block flush performed successfully
 * FINISH_STARTED = 2: Finish operation started, need only more output
 * FINISH_DONE = 3: Finish operation completed, stream fully processed
 */
const val NEED_MORE = 0
const val BLOCK_DONE = 1
const val FINISH_STARTED = 2
const val FINISH_DONE = 3
const val PRESET_DICT = 0x20 // preset dictionary flag in zlib header
/** 
 * Deflate state machine states
 * 
 * INIT_STATE = 42: Deflate stream initialized, ready for input
 * BUSY_STATE = 113: Deflate stream processing data
 * FINISH_STATE = 666: Deflate stream in final state
 */
const val INIT_STATE = 42
const val BUSY_STATE = 113
const val FINISH_STATE = 666
const val Z_DEFLATED = 8 // The deflate compression method
/** 
 * Block types for deflate
 * 
 * STORED_BLOCK = 0: Stored (uncompressed) block type
 * STATIC_TREES = 1: Block compressed with static Huffman trees
 * DYN_TREES = 2: Block compressed with dynamic Huffman trees
 */
const val STORED_BLOCK = 0
const val STATIC_TREES = 1
const val DYN_TREES = 2
const val Z_BINARY = 0
const val Z_ASCII = 1
const val Z_UNKNOWN = 2
const val MIN_MATCH = 3
const val MAX_MATCH = 258
const val MIN_LOOKAHEAD = MAX_MATCH + MIN_MATCH + 1

// Extra bits for length codes
val TREE_EXTRA_LBITS = intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2, 3, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5, 5, 0)

// Extra bits for distance codes
val TREE_EXTRA_DBITS = intArrayOf(0, 0, 0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7, 8, 8, 9, 9, 10, 10, 11, 11, 12, 12, 13, 13)

// Extra bits for bit length codes
val TREE_EXTRA_BLBITS = intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2, 3, 7)

// Order of the bit length code lengths
val TREE_BL_ORDER = intArrayOf(16, 17, 18, 0, 8, 7, 9, 6, 10, 5, 11, 4, 12, 3, 13, 2, 14, 1, 15)

// Base length for each length code
val TREE_BASE_LENGTH = intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 10, 12, 14, 16, 20, 24, 28, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 255)

// Base distance for each distance code
val TREE_BASE_DIST = intArrayOf(0, 1, 2, 3, 4, 6, 8, 12, 16, 24, 32, 48, 64, 96, 128, 192, 256, 384, 512, 768, 1024, 1536, 2048, 3072, 4096, 6144, 8192, 12288, 16384, 24576)

// Tree dist code list - matches Pascal _dist_code array exactly
internal val TREE_DIST_CODE = ubyteArrayOf(
    // First 256 values correspond to distances 0-255 (direct lookup)
    0u, 1u, 2u, 3u, 4u, 4u, 5u, 5u, 6u, 6u, 6u, 6u, 7u, 7u, 7u, 7u, 8u, 8u, 8u, 8u, 8u, 8u, 8u, 8u, 9u, 9u, 9u, 9u, 9u, 9u, 9u, 9u,
    10u, 10u, 10u, 10u, 10u, 10u, 10u, 10u, 10u, 10u, 10u, 10u, 10u, 10u, 10u, 10u, 11u, 11u, 11u, 11u, 11u, 11u, 11u, 11u,
    11u, 11u, 11u, 11u, 11u, 11u, 11u, 11u, 12u, 12u, 12u, 12u, 12u, 12u, 12u, 12u, 12u, 12u, 12u, 12u, 12u, 12u, 12u, 12u,
    12u, 12u, 12u, 12u, 12u, 12u, 12u, 12u, 12u, 12u, 12u, 12u, 12u, 12u, 12u, 12u, 13u, 13u, 13u, 13u, 13u, 13u, 13u, 13u,
    13u, 13u, 13u, 13u, 13u, 13u, 13u, 13u, 13u, 13u, 13u, 13u, 13u, 13u, 13u, 13u, 13u, 13u, 13u, 13u, 13u, 13u, 13u, 13u,
    14u, 14u, 14u, 14u, 14u, 14u, 14u, 14u, 14u, 14u, 14u, 14u, 14u, 14u, 14u, 14u, 14u, 14u, 14u, 14u, 14u, 14u, 14u, 14u,
    14u, 14u, 14u, 14u, 14u, 14u, 14u, 14u, 14u, 14u, 14u, 14u, 14u, 14u, 14u, 14u, 14u, 14u, 14u, 14u, 14u, 14u, 14u, 14u,
    14u, 14u, 14u, 14u, 14u, 14u, 14u, 14u, 14u, 14u, 14u, 14u, 14u, 14u, 14u, 14u, 15u, 15u, 15u, 15u, 15u, 15u, 15u, 15u,
    15u, 15u, 15u, 15u, 15u, 15u, 15u, 15u, 15u, 15u, 15u, 15u, 15u, 15u, 15u, 15u, 15u, 15u, 15u, 15u, 15u, 15u, 15u, 15u,
    15u, 15u, 15u, 15u, 15u, 15u, 15u, 15u, 15u, 15u, 15u, 15u, 15u, 15u, 15u, 15u, 15u, 15u, 15u, 15u, 15u, 15u, 15u, 15u,
    15u, 15u, 15u, 15u, 15u, 15u, 15u, 15u, 
    // Second 256 values for distances >= 256, using (dist >> 7) for lookup
    0u, 0u, 16u, 17u, 18u, 18u, 19u, 19u, 20u, 20u, 20u, 20u, 21u, 21u, 21u, 21u, 22u, 22u, 22u, 22u, 22u, 22u, 22u, 22u,
    23u, 23u, 23u, 23u, 23u, 23u, 23u, 23u, 24u, 24u, 24u, 24u, 24u, 24u, 24u, 24u, 24u, 24u, 24u, 24u, 24u, 24u, 24u, 24u,
    25u, 25u, 25u, 25u, 25u, 25u, 25u, 25u, 25u, 25u, 25u, 25u, 25u, 25u, 25u, 25u, 26u, 26u, 26u, 26u, 26u, 26u, 26u, 26u,
    26u, 26u, 26u, 26u, 26u, 26u, 26u, 26u, 26u, 26u, 26u, 26u, 26u, 26u, 26u, 26u, 26u, 26u, 26u, 26u, 26u, 26u, 26u, 26u,
    27u, 27u, 27u, 27u, 27u, 27u, 27u, 27u, 27u, 27u, 27u, 27u, 27u, 27u, 27u, 27u, 27u, 27u, 27u, 27u, 27u, 27u, 27u, 27u,
    27u, 27u, 27u, 27u, 27u, 27u, 27u, 27u, 28u, 28u, 28u, 28u, 28u, 28u, 28u, 28u, 28u, 28u, 28u, 28u, 28u, 28u, 28u, 28u,
    28u, 28u, 28u, 28u, 28u, 28u, 28u, 28u, 28u, 28u, 28u, 28u, 28u, 28u, 28u, 28u, 28u, 28u, 28u, 28u, 28u, 28u, 28u, 28u,
    28u, 28u, 28u, 28u, 28u, 28u, 28u, 28u, 28u, 28u, 28u, 28u, 28u, 28u, 28u, 28u, 28u, 28u, 28u, 28u, 28u, 28u, 28u, 28u,
    29u, 29u, 29u, 29u, 29u, 29u, 29u, 29u, 29u, 29u, 29u, 29u, 29u, 29u, 29u, 29u, 29u, 29u, 29u, 29u, 29u, 29u, 29u, 29u,
    29u, 29u, 29u, 29u, 29u, 29u, 29u, 29u, 29u, 29u, 29u, 29u, 29u, 29u, 29u, 29u, 29u, 29u, 29u, 29u, 29u, 29u, 29u, 29u,
    29u, 29u, 29u, 29u, 29u, 29u, 29u, 29u, 29u, 29u, 29u, 29u, 29u, 29u, 29u, 29u, 29u, 29u, 29u, 29u, 29u, 29u, 29u, 29u
)

/**
 * States for the Inflate state machine
 * 
 * INF_METHOD = 0: Waiting for method byte
 * INF_FLAG = 1: Waiting for flag byte
 * INF_DICT4 = 2, INF_DICT3 = 3, INF_DICT2 = 4, INF_DICT1 = 5, INF_DICT0 = 6: Waiting for dictionary ID (4 bytes)
 * INF_BLOCKS = 7: Processing inflate blocks
 * INF_CHECK4 = 8, INF_CHECK3 = 9, INF_CHECK2 = 10, INF_CHECK1 = 11: Waiting for checksum bytes (4 bytes)
 * INF_DONE = 12: Decompression finished successfully
 * INF_BAD = 13: Error state - invalid data encountered
 */
const val INF_METHOD = 0
const val INF_FLAG = 1
const val INF_DICT4 = 2
const val INF_DICT3 = 3
const val INF_DICT2 = 4
const val INF_DICT1 = 5
const val INF_DICT0 = 6
const val INF_BLOCKS = 7
const val INF_CHECK4 = 8
const val INF_CHECK3 = 9
const val INF_CHECK2 = 10
const val INF_CHECK1 = 11
const val INF_DONE = 12
const val INF_BAD = 13

val INF_MARK = ubyteArrayOf(0u, 0u, 0xFFu, 0xFFu)

// Maps a length to a length code
val TREE_LENGTH_CODE = ubyteArrayOf(
    0u, 1u, 2u, 3u, 4u, 5u, 6u, 7u, 8u, 8u, 9u, 9u, 10u, 10u, 11u, 11u, 12u, 12u, 12u, 12u, 13u, 13u, 13u, 13u, 14u, 14u, 14u, 14u, 15u, 15u, 15u, 15u,
    16u, 16u, 16u, 16u, 16u, 16u, 16u, 16u, 17u, 17u, 17u, 17u, 17u, 17u, 17u, 17u, 18u, 18u, 18u, 18u, 18u, 18u, 18u, 18u, 19u, 19u, 19u, 19u, 19u, 19u, 19u, 19u,
    20u, 20u, 20u, 20u, 20u, 20u, 20u, 20u, 20u, 20u, 20u, 20u, 20u, 20u, 20u, 20u, 21u, 21u, 21u, 21u, 21u, 21u, 21u, 21u, 21u, 21u, 21u, 21u, 21u, 21u, 21u, 21u,
    22u, 22u, 22u, 22u, 22u, 22u, 22u, 22u, 22u, 22u, 22u, 22u, 22u, 22u, 22u, 22u, 23u, 23u, 23u, 23u, 23u, 23u, 23u, 23u, 23u, 23u, 23u, 23u, 23u, 23u, 23u, 23u,
    24u, 24u, 24u, 24u, 24u, 24u, 24u, 24u, 24u, 24u, 24u, 24u, 24u, 24u, 24u, 24u, 24u, 24u, 24u, 24u, 24u, 24u, 24u, 24u, 24u, 24u, 24u, 24u, 24u, 24u, 24u, 24u,
    25u, 25u, 25u, 25u, 25u, 25u, 25u, 25u, 25u, 25u, 25u, 25u, 25u, 25u, 25u, 25u, 25u, 25u, 25u, 25u, 25u, 25u, 25u, 25u, 25u, 25u, 25u, 25u, 25u, 25u, 25u, 25u,
    26u, 26u, 26u, 26u, 26u, 26u, 26u, 26u, 26u, 26u, 26u, 26u, 26u, 26u, 26u, 26u, 26u, 26u, 26u, 26u, 26u, 26u, 26u, 26u, 26u, 26u, 26u, 26u, 26u, 26u, 26u, 26u,
    27u, 27u, 27u, 27u, 27u, 27u, 27u, 27u, 27u, 27u, 27u, 27u, 27u, 27u, 27u, 27u, 27u, 27u, 27u, 27u, 27u, 27u, 27u, 27u, 27u, 27u, 27u, 27u, 27u, 27u, 27u, 27u,
    27u, 27u, 27u, 27u, 27u, 27u, 27u, 27u, 27u, 27u, 27u, 27u, 27u, 27u, 27u, 27u, 27u, 27u, 27u, 27u, 27u, 27u, 27u, 27u, 27u, 27u, 27u, 27u, 27u, 27u, 27u, 27u,
    28u, 28u, 28u, 28u, 28u, 28u, 28u, 28u, 28u, 28u, 28u, 28u, 28u, 28u, 28u, 28u, 28u, 28u, 28u, 28u, 28u, 28u, 28u, 28u, 28u, 28u, 28u, 28u, 28u, 28u, 28u, 28u
)

/**
 * Bit masks for extracting the lowest N bits of a value.
 * Used extensively during Huffman decoding to extract specific bit patterns.
 * The index into the array represents the number of bits to mask.
 */
val IBLK_INFLATE_MASK = intArrayOf(
    0x00000000, 0x00000001, 0x00000003, 0x00000007, 0x0000000f,
    0x0000001f, 0x0000003f, 0x0000007f, 0x000000ff, 0x000001ff,
    0x000003ff, 0x000007ff, 0x00000fff, 0x00001fff, 0x00003fff,
    0x00007fff, 0x0000ffff
)

/**
 * Constants for InfCodes state machine
 * 
 * ICODES_START = 0: Starting state
 * ICODES_LEN = 1: Reading length codes
 * ICODES_LENEXT = 2: Reading length extra bits
 * ICODES_DIST = 3: Reading distance codes
 * ICODES_DISTEXT = 4: Reading distance extra bits
 * ICODES_COPY = 5: Copying matched string
 * ICODES_LIT = 6: Writing literal byte
 * ICODES_WASH = 7: Flushing output window
 * ICODES_END = 8: End of block
 * ICODES_BADCODE = 9: Error state - invalid code
 */
const val ICODES_START = 0
const val ICODES_LEN = 1
const val ICODES_LENEXT = 2
const val ICODES_DIST = 3
const val ICODES_DISTEXT = 4
const val ICODES_COPY = 5
const val ICODES_LIT = 6
const val ICODES_WASH = 7
const val ICODES_END = 8
const val ICODES_BADCODE = 9
