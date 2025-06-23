package ai.solace.zlib.streams

import kotlin.IllegalArgumentException

actual abstract class InputStream {
    actual abstract fun read(): Int
    @Suppress("UNUSED")
    actual open fun read(b: ByteArray): Int = read(b, 0, b.size)

    actual open fun read(b: ByteArray, off: Int, len: Int): Int {
        if (off < 0 || len < 0 || len > b.size - off) {
            throw IllegalArgumentException("Wrong arguments")
        }
        if (len == 0) {
            return 0
        }

        val c = read()
        if (c == -1) {
            return -1
        }
        b[off] = c.toByte()

        var i = 1
        try {
            while (i < len) {
                val c2 = read()
                if (c2 == -1) {
                    break
                }
                b[off + i] = c2.toByte()
                i++
            }
        } catch (_: Exception) {
            // Ignore exceptions during reading
        }
        return i
    }

    @Suppress("UNUSED")
    actual open fun skip(n: Long): Long {
        var remaining = n
        val size = if (n < 0) 0 else if (n > 2048) 2048 else n.toInt()
        val skipBuffer = ByteArray(size)
        while (remaining > 0) {
            val nr = read(skipBuffer, 0, if (remaining > size) size else remaining.toInt())
            if (nr < 0) {
                break
            }
            remaining -= nr
        }
        return n - remaining
    }

    actual open fun available(): Int = 0

    actual open fun close() {}
}
