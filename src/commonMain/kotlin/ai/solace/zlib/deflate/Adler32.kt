package ai.solace.zlib.deflate

import ai.solace.zlib.common.*

class Adler32 {

    // Constants moved to common.Constants.kt

    internal fun adler32(adler: Long, buf: ByteArray?, index: Int, len: Int): Long {
        if (buf == null) {
            return 1L
        }

        var s1 = adler and 0xffff
        var s2 = (adler shr 16) and 0xffff
        var localLen = len
        var localIndex = index

        while (localLen > 0) {
            var k = if (localLen < ADLER_NMAX) localLen else ADLER_NMAX
            localLen -= k
            while (k >= 16) {
                repeat(16) {
                    s1 += (buf[localIndex++].toInt() and 0xff).toLong()
                    s2 += s1
                }
                k -= 16
            }
            while (k-- > 0) {
                s1 += (buf[localIndex++].toInt() and 0xff).toLong()
                s2 += s1
            }
            s1 %= ADLER_BASE
            s2 %= ADLER_BASE
        }
        return (s2 shl 16) or s1
    }
}
