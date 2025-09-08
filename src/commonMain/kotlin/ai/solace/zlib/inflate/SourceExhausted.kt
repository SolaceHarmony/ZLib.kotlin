package ai.solace.zlib.inflate

/**
 * Indicates that the underlying source was exhausted before enough bits/bytes
 * could be read to satisfy the current operation. This is not a data error;
 * callers should typically translate this to Z_BUF_ERROR at a higher level.
 */
class SourceExhausted(message: String? = null) : Exception(message)
