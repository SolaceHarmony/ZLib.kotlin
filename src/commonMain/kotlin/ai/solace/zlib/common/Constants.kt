package ai.solace.zlib.common

// This file will house constants extracted from various zlib implementation files.

// Constants previously in Zlib.kt
private const val version_Renamed_Field: String = "1.0.2" // Assuming this was the version
fun version(): String {
    return version_Renamed_Field
}

// compression levels
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
