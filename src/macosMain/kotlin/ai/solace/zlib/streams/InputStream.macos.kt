package ai.solace.zlib.streams

actual abstract class InputStream {
    abstract fun read(b: ByteArray, off: Int, len: Int): Int
    abstract fun available(): Int
}