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
            if (buf == null) {
                return 1L
            }
            
            // Extract initial s1 and s2 values using arithmetic operations
            var s1 = BitwiseOps.getLow16BitsArithmetic(adler).toLong()
            var s2 = BitwiseOps.getHigh16BitsArithmetic(adler).toLong()
            var k: Int
            var localLen = len
            var localIndex = index
            
            while (localLen > 0) {
                k = if (localLen < ADLER_NMAX) localLen else ADLER_NMAX
                localLen -= k
                
                // Process chunks of 16 bytes for better performance
                while (k >= 16) {
                    for (i in 0 until 16) {
                        // Convert signed byte to unsigned using arithmetic only
                        val unsigned = BitwiseOps.byteToUnsignedInt(buf[localIndex++])
                        s1 += unsigned
                        s2 += s1
                    }
                    k -= 16
                }
                
                // Process remaining bytes
                if (k != 0) {
                    do {
                        // Convert signed byte to unsigned using arithmetic only
                        val unsigned = BitwiseOps.byteToUnsignedInt(buf[localIndex++])
                        s1 += unsigned
                        s2 += s1
                        k--
                    } while (k != 0)
                }
                
                // Apply modulo operation
                s1 %= ADLER_BASE
                s2 %= ADLER_BASE
            }
            
            // Combine s1 and s2 into the final checksum using arithmetic operations only
            return BitwiseOps.combine16BitArithmetic(s2.toInt(), s1.toInt())
        }
    }
}