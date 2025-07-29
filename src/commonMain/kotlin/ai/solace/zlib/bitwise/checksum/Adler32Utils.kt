package ai.solace.zlib.bitwise.checksum

import ai.solace.zlib.common.ADLER_BASE
import ai.solace.zlib.common.ADLER_NMAX

/**
 * Adler32 checksum implementation
 * Based on the implementation in ZLib.kotlin
 * 
 * This implementation matches the original Adler32 class exactly to ensure compatibility.
 */
class Adler32Utils {
    companion object {
        /**
         * Calculates or updates an Adler-32 checksum
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
            
            // Use the same bit operations as the original Adler32 class
            var s1 = adler and 0xffff
            var s2 = (adler shr 16) and 0xffff
            var k: Int
            var localLen = len
            var localIndex = index
            
            while (localLen > 0) {
                k = if (localLen < ADLER_NMAX) localLen else ADLER_NMAX
                localLen -= k
                
                // Process chunks of 16 bytes for better performance
                while (k >= 16) {
                    for (i in 0 until 16) {
                        s1 += buf[localIndex++].toLong() and 0xff
                        s2 += s1
                    }
                    k -= 16
                }
                
                // Process remaining bytes
                if (k != 0) {
                    do {
                        s1 += buf[localIndex++].toLong() and 0xff
                        s2 += s1
                        k--
                    } while (k != 0)
                }
                
                // Apply modulo operation
                s1 %= ADLER_BASE
                s2 %= ADLER_BASE
            }
            
            // Combine s1 and s2 into the final checksum using the same operation as the original
            return (s2 shl 16) or s1
        }
    }
}