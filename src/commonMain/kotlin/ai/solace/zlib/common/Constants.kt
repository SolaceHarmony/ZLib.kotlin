package ai.solace.zlib.common

// This file will house constants extracted from various zlib implementation files.

const val Z_NO_COMPRESSION = 0
const val Z_BEST_SPEED = 1
const val Z_BEST_COMPRESSION = 9
const val Z_DEFAULT_COMPRESSION = -1

// compression strategy
const val Z_FILTERED = 1
const val Z_HUFFMAN_ONLY = 2
const val Z_DEFAULT_STRATEGY = 0

const val Z_NO_FLUSH = 0
const val Z_PARTIAL_FLUSH = 1
const val Z_SYNC_FLUSH = 2
const val Z_FULL_FLUSH = 3
const val Z_FINISH = 4

const val Z_OK = 0
const val Z_STREAM_END = 1
const val Z_NEED_DICT = 2
const val Z_ERRNO = -1
const val Z_STREAM_ERROR = -2
const val Z_DATA_ERROR = -3
const val Z_MEM_ERROR = -4
const val Z_BUF_ERROR = -5
const val Z_VERSION_ERROR = -6

private const val version_Renamed_Field: String = "1.0.2"
fun version(): String {
    return version_Renamed_Field
}

// Constants from Deflate.kt
const val MAX_MEM_LEVEL = 9
const val MAX_WBITS = 15 // 32K LZ77 window
const val DEF_MEM_LEVEL = 8 // Default memLevel

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

// block not completed, need more input or more output
const val NEED_MORE = 0

// block flush performed
const val BLOCK_DONE = 1

// finish started, need only more output at next deflate
const val FINISH_STARTED = 2

// finish done, accept no more input or output
const val FINISH_DONE = 3

// preset dictionary flag in zlib header
const val PRESET_DICT = 0x20

const val INIT_STATE = 42
const val BUSY_STATE = 113
const val FINISH_STATE = 666

// The deflate compression method
const val Z_DEFLATED = 8

const val STORED_BLOCK = 0
const val STATIC_TREES = 1
const val DYN_TREES = 2

// The three kinds of block type
const val Z_BINARY = 0
const val Z_ASCII = 1
const val Z_UNKNOWN = 2

const val BUF_SIZE = 8 * 2 // Size of bit buffer in bi_buf

// repeat previous bit length 3-6 times (2 bits of repeat count)
const val REP_3_6 = 16

// repeat a zero length 3-10 times  (3 bits of repeat count)
const val REPZ_3_10 = 17

// repeat a zero length 11-138 times  (7 bits of repeat count)
const val REPZ_11_138 = 18

const val MIN_MATCH = 3
const val MAX_MATCH = 258
const val MIN_LOOKAHEAD = MAX_MATCH + MIN_MATCH + 1

const val MAX_BITS = 15
const val D_CODES = 30
const val BL_CODES = 19
const val LENGTH_CODES = 29
const val LITERALS = 256
const val L_CODES = LITERALS + 1 + LENGTH_CODES
const val HEAP_SIZE = 2 * L_CODES + 1

const val END_BLOCK = 256

// Constants from Inflate.kt
// Inflate modes
const val INF_METHOD = 0 // waiting for method byte
const val INF_FLAG = 1 // waiting for flag byte
const val INF_DICT4 = 2 // four dictionary check bytes to go
const val INF_DICT3 = 3 // three dictionary check bytes to go
const val INF_DICT2 = 4 // two dictionary check bytes to go
const val INF_DICT1 = 5 // one dictionary check byte to go
const val INF_DICT0 = 6 // waiting for inflateSetDictionary
const val INF_BLOCKS = 7 // decompressing blocks
const val INF_CHECK4 = 8 // four check bytes to go
const val INF_CHECK3 = 9 // three check bytes to go
const val INF_CHECK2 = 10 // two check bytes to go
const val INF_CHECK1 = 11 // one check byte to go
const val INF_DONE = 12 // finished check, done
const val INF_BAD = 13 // got an error--stay here

// InflateSync marker
val INF_MARK = byteArrayOf(0, 0, (-1).toByte(), (-1).toByte())

// Constants from InfBlocks.kt
const val IBLK_MANY = 1440

// And'ing with mask[n] masks the lower n bits
val IBLK_INFLATE_MASK = intArrayOf(
    0x00000000,
    0x00000001,
    0x00000003,
    0x00000007,
    0x0000000f,
    0x0000001f,
    0x0000003f,
    0x0000007f,
    0x000000ff,
    0x000001ff,
    0x000003ff,
    0x000007ff,
    0x00000fff,
    0x00001fff,
    0x00003fff,
    0x00007fff,
    0x0000ffff
)

// Table for deflate from PKZIP's appnote.txt.
val IBLK_BORDER = intArrayOf(16, 17, 18, 0, 8, 7, 9, 6, 10, 5, 11, 4, 12, 3, 13, 2, 14, 1, 15)

// InfBlocks modes
const val IBLK_TYPE = 0 // get type bits (3, including end bit)
const val IBLK_LENS = 1 // get lengths for stored
const val IBLK_STORED = 2 // processing stored block
const val IBLK_TABLE = 3 // get table lengths
const val IBLK_BTREE = 4 // get bit lengths tree for a dynamic block
const val IBLK_DTREE = 5 // get length, distance trees for a dynamic block
const val IBLK_CODES = 6 // processing fixed or dynamic block
const val IBLK_DRY = 7 // output remaining window bytes
const val IBLK_DONE = 8 // finished last block, done
const val IBLK_BAD = 9 // ot a data error--stuck here

// Constants from InfCodes.kt
// InfCodes modes
const val ICODES_START = 0 // x: set up for LEN
const val ICODES_LEN = 1 // i: get length/literal/eob next
const val ICODES_LENEXT = 2 // i: getting length extra (have base)
const val ICODES_DIST = 3 // i: get distance next
const val ICODES_DISTEXT = 4 // i: getting distance extra
const val ICODES_COPY = 5 // o: copying bytes in window, waiting for space
const val ICODES_LIT = 6 // o: got literal, waiting for output space
const val ICODES_WASH = 7 // o: got eob, possibly still output waiting
const val ICODES_END = 8 // x: got eob and all data flushed
const val ICODES_BADCODE = 9 // x: got error

// Constants from Tree.kt
const val TREE_MAX_BL_BITS = 7 // Bit length codes must not exceed MAX_BL_BITS bits
const val TREE_DIST_CODE_LEN = 512 // see definition of array dist_code below

// extra bits for each length code
val TREE_EXTRA_LBITS = intArrayOf(
    0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2, 3, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5, 5, 0
)

// extra bits for each distance code
val TREE_EXTRA_DBITS = intArrayOf(
    0, 0, 0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7, 8, 8, 9, 9, 10, 10, 11, 11, 12, 12, 13, 13
)

// extra bits for each bit length code
val TREE_EXTRA_BLBITS = intArrayOf(
    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2, 3, 7
)

val TREE_BL_ORDER = byteArrayOf(
    16, 17, 18, 0, 8, 7, 9, 6, 10, 5, 11, 4, 12, 3, 13, 2, 14, 1, 15
)

val TREE_DIST_CODE = byteArrayOf(
    0, 1, 2, 3, 4, 4, 5, 5, 6, 6, 6, 6, 7, 7, 7, 7, 8, 8, 8, 8, 8, 8, 8, 8, 9, 9, 9, 9, 9, 9, 9, 9, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 12, 12, 12, 12, 12, 12, 12, 12, 12, 12, 12, 12, 12, 12, 12, 12, 12, 12, 12, 12, 12, 12, 12, 12, 12, 12, 12, 12, 12, 12, 12, 12, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 0, 0, 16, 17, 18, 18, 19, 19, 20, 20, 20, 20, 21, 21, 21, 21, 22, 22, 22, 22, 22, 22, 22, 22, 23, 23, 23, 23, 23, 23, 23, 23, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 25, 25, 25, 25, 25, 25, 25, 25, 25, 25, 25, 25, 25, 25, 25, 25, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 27, 27, 27, 27, 27, 27, 27, 27, 27, 27, 27, 27, 27, 27, 27, 27, 27, 27, 27, 27, 27, 27, 27, 27, 27, 27, 27, 27, 27, 27, 27, 27, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 29, 29, 29, 29, 29, 29, 29, 29, 29, 29, 29, 29, 29, 29, 29, 29, 29, 29, 29, 29, 29, 29, 29, 29, 29, 29, 29, 29, 29, 29, 29, 29, 29, 29, 29, 29, 29, 29, 29, 29, 29, 29, 29, 29, 29, 29, 29, 29, 29, 29, 29, 29, 29, 29, 29, 29, 29, 29, 29, 29, 29, 29, 29, 29
)

val TREE_LENGTH_CODE = byteArrayOf(
    0, 1, 2, 3, 4, 5, 6, 7, 8, 8, 9, 9, 10, 10, 11, 11, 12, 12, 12, 12, 13, 13, 13, 13, 14, 14, 14, 14, 15, 15, 15, 15, 16, 16, 16, 16, 16, 16, 16, 16, 17, 17, 17, 17, 17, 17, 17, 17, 18, 18, 18, 18, 18, 18, 18, 18, 19, 19, 19, 19, 19, 19, 19, 19, 20, 20, 20, 20, 20, 20, 20, 20, 20, 20, 20, 20, 20, 20, 20, 20, 21, 21, 21, 21, 21, 21, 21, 21, 21, 21, 21, 21, 21, 21, 21, 21, 22, 22, 22, 22, 22, 22, 22, 22, 22, 22, 22, 22, 22, 22, 22, 22, 23, 23, 23, 23, 23, 23, 23, 23, 23, 23, 23, 23, 23, 23, 23, 23, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 25, 25, 25, 25, 25, 25, 25, 25, 25, 25, 25, 25, 25, 25, 25, 25, 25, 25, 25, 25, 25, 25, 25, 25, 25, 25, 25, 25, 25, 25, 25, 25, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 27, 27, 27, 27, 27, 27, 27, 27, 27, 27, 27, 27, 27, 27, 27, 27, 27, 27, 27, 27, 27, 27, 27, 27, 27, 27, 27, 27, 27, 27, 27, 28
)

val TREE_BASE_LENGTH = intArrayOf(
    0, 1, 2, 3, 4, 5, 6, 7, 8, 10, 12, 14, 16, 20, 24, 28, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 0
)

val TREE_BASE_DIST = intArrayOf(
    0, 1, 2, 3, 4, 6, 8, 12, 16, 24, 32, 48, 64, 96, 128, 192, 256, 384, 512, 768, 1024, 1536, 2048, 3072, 4096, 6144, 8192, 12288, 16384, 24576
)

// Constants from Adler32.kt
// largest prime smaller than 65536
const val ADLER_BASE = 65521
// NMAX is the largest n such that 255n(n+1)/2 + (n+1)(BASE-1) <= 2^32-1
const val ADLER_NMAX = 5552
