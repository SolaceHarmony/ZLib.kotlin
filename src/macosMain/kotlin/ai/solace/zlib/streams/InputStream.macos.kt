package ai.solace.zlib.streams

actual abstract class InputStream {
    actual abstract fun read(): Int
    actual open fun read(b: ByteArray): Int = read(b, 0, b.size)
    actual abstract fun read(b: ByteArray, off: Int, len: Int): Int
    actual open fun skip(n: Long): Long = 0
    actual abstract fun available(): Int
    actual open fun close() {}
}