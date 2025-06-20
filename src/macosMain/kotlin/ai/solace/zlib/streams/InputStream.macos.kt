package ai.solace.zlib.streams

actual abstract class InputStream {
    actual abstract fun read(): Int
    actual open fun read(b: ByteArray): Int = read(b, 0, b.size)
    actual open fun read(b: ByteArray, off: Int, len: Int): Int {
        if (off < 0 || len < 0 || len > b.size - off) {
            throw IndexOutOfBoundsException()
        }
        if (len == 0) {
            return 0
        }
        var c = read()
        if (c == -1) {
            return -1
        }
        b[off] = c.toByte()
        var i = 1
        try {
            while (i < len) {
                c = read()
                if (c == -1) {
                    break
                }
                b[off + i] = c.toByte()
                i++
            }
        } catch (ee: Exception) {
        }
        return i
    }
    actual open fun skip(n: Long): Long = 0
    actual open fun available(): Int = 0
    actual open fun close() {}
}