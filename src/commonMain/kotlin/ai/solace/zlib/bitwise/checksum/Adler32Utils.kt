package ai.solace.zlib.bitwise.checksum

import ai.solace.zlib.bitwise.BitwiseOps

/**
 * Adler32 checksum implementation using the BitwiseOps library
 * Based on the implementation in ZLib.kotlin
 */
class Adler32Utils {
    companion object {
        /**
         * The largest prime smaller than 65536
         */
        const val BASE = 65521
        
        /**
         * NMAX is the largest n such that 255n(n+1)/2 + (n+1)(BASE-1) <= 2^32-1
         */
        const val NMAX = 5552
        
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
            
            var s1 = BitwiseOps.getLow16Bits(adler.toInt()).toLong()
            var s2 = BitwiseOps.getHigh16Bits(adler.toInt()).toLong()
            var k: Int
            var localLen = len
            var localIndex = index
            
            while (localLen > 0) {
                k = if (localLen < NMAX) localLen else NMAX
                localLen -= k
                
                // Process chunks of 16 bytes for better performance
                while (k >= 16) {
                    for (i in 0 until 16) {
                        s1 += BitwiseOps.byteToUnsignedInt(buf[localIndex++]).toLong()
                        s2 += s1
                    }
                    k -= 16
                }
                
                // Process remaining bytes
                if (k != 0) {
                    do {
                        s1 += BitwiseOps.byteToUnsignedInt(buf[localIndex++]).toLong()
                        s2 += s1
                        k--
                    } while (k != 0)
                }
                
                // Apply modulo operation
                s1 %= BASE
                s2 %= BASE
            }
            
            // Combine s1 and s2 into the final checksum
            return BitwiseOps.combine16BitToLong(s2, s1)
        }
    }
}