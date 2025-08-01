package ai.solace.zlib.bitwise.checksum

import ai.solace.zlib.bitwise.BitwiseOps
import ai.solace.zlib.common.ADLER_BASE
import ai.solace.zlib.common.ADLER_NMAX

/**
 * Adler32 checksum implementation using arithmetic-only operations for Kotlin/Native portability.
 * 
 * This implementation follows the reference arithmetic-only approach specified in issue #14:
 * - Uses modular arithmetic instead of bitwise operations
 * - Converts signed bytes to unsigned using arithmetic (not bitwise masking)  
 * - Combines high/low 16-bit values using multiplication and addition
 * - Fully compatible with Kotlin/Native and all multiplatform targets
 * 
 * Algorithm:
 * - a = 1 + byte1 + byte2 + ... + byteN (mod 65521)
 * - b = a1 + a2 + ... + aN (mod 65521) 
 * - Final checksum: b * 65536 + a
 */
class Adler32Utils {
    companion object {
        /**
         * Calculates or updates an Adler-32 checksum using arithmetic-only operations
         * @param adler Initial checksum value (use 1 for new checksums)
         * @param buf Data buffer to calculate checksum for
         * @param index Starting index in the buffer
         * @param len Number of bytes to process
         * @return Updated Adler-32 checksum
         */
        fun adler32(adler: Long, buf: ByteArray?, index: Int, len: Int): Long {
            if (buf == null) return 1L

            val MOD = ADLER_BASE
            // Extract a and b from the input adler value using arithmetic-only operations
            var a = BitwiseOps.getLow16BitsArithmetic(adler)
            var b = BitwiseOps.getHigh16BitsArithmetic(adler)

            var i = index
            val end = index + len
            while (i < end) {
                val unsigned = BitwiseOps.byteToUnsignedInt(buf[i])
                a = (a + unsigned) % MOD
                b = (b + a) % MOD
                i++
            }

            // Combine a and b into the final result using arithmetic-only operations
            return BitwiseOps.combine16BitArithmetic(b, a)
        }
    }
}