package ai.solace.zlib.streams

actual abstract class InputStream {
    actual abstract fun read(): Int
    actual open fun read(b: ByteArray): Int {
        TODO("Not yet implemented")
    }

    actual open fun read(b: ByteArray, off: Int, len: Int): Int {
        TODO("Not yet implemented")
    }

    actual open fun skip(n: Long): Long {
        TODO("Not yet implemented")
    }

    actual open fun available(): Int {
        TODO("Not yet implemented")
    }

    actual open fun close() {
    }
}