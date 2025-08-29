package ai.solace.zlib.bitwise.checksum

import ai.solace.zlib.bitwise.BitwiseOps
import ai.solace.zlib.common.ADLER_BASE
import ai.solace.zlib.common.ADLER_NMAX

/**
 * Adler32 checksum implementation using plain arithmetic operations.
 *
 * The algorithm processes the input in chunks of [ADLER_NMAX] bytes to avoid
 * integer overflow. For each chunk it accumulates two sums:
 *  - **a**: the sum of all bytes
 *  - **b**: the sum of running values of a
 *
 * The final checksum is `(b shl 16) or a` where both a and b are computed
 * modulo [ADLER_BASE].
 *
 * This simplified version avoids the previous bit-shift engine abstraction
 * which produced incorrect results on some platforms.
 */
class Adler32Utils {
    companion object {

        /**
         * Calculates or updates an Adler-32 checksum.
         *
         * @param adler Initial checksum value (use 1 for new checksums)
         * @param buf   Data buffer to calculate checksum for
         * @param index Starting index in the buffer
         * @param len   Number of bytes to process
         * @return Updated Adler-32 checksum
         */
        fun adler32(adler: Long, buf: ByteArray?, index: Int, len: Int): Long {
            if (buf == null) return 1L

            var a = (adler and 0xFFFF).toLong()
            var b = ((adler ushr 16) and 0xFFFF)

            var i = index
            val end = index + len

            while (i < end) {
                val chunkEnd = minOf(i + ADLER_NMAX, end)
                while (i < chunkEnd) {
                    a += BitwiseOps.byteToUnsignedInt(buf[i])
                    b += a
                    i++
                }
                a %= ADLER_BASE
                b %= ADLER_BASE
            }

            return ((b and 0xFFFF) shl 16) or (a and 0xFFFF)
        }
    }
}