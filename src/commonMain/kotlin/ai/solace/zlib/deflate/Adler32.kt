package ai.solace.zlib.deflate

import ai.solace.zlib.bitwise.checksum.Adler32Utils
import ai.solace.zlib.common.ZlibLogger

/**
 * Adler-32 checksum algorithm implementation.
 *
 * The Adler-32 checksum is calculated using two 16-bit checksums A and B.
 * The checksums are combined into a 32-bit integer as follows:
 * Adler-32(D) = B × 65536 + A
 *
 * Where:
 * A = 1 + D₁ + D₂ + ... + Dₙ (mod 65521)
 * B = (1 + D₁) + (1 + D₁ + D₂) + ... + (1 + D₁ + D₂ + ... + Dₙ) (mod 65521)
 *   = n×D₁ + (n−1)×D₂ + (n−2)×D₃ + ... + Dₙ + n (mod 65521)
 *
 * The value 65521 (ADLER_BASE) is the largest prime number smaller than 2¹⁶ (65536).
 *
 * Based on the original C implementation by Mark Adler, and the Pascal translation.
 * The implementation processes data in chunks of ADLER_NMAX bytes and applies
 * the modulo operation after each chunk (not after each byte) for efficiency.
 * 
 * This class now delegates to Adler32Utils for the actual implementation.
 */
class Adler32 {
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
            ZlibLogger.logAdler32("null buffer provided, returning initial value 1", "adler32")
            return 1L
        }
        
        ZlibLogger.logAdler32("Starting Adler32 calculation: initial=$adler, index=$index, len=$len", "adler32")
        
        var s1 = adler and 0xffff
        var s2 = (adler ushr 16) and 0xffff
        ZlibLogger.logAdler32Calc(s1, s2, functionName = "adler32")
        ZlibLogger.logAdler32("Initial s1=$s1, s2=$s2 from adler=$adler", "adler32")
        
        var k: Int
        var i = index
        var l = len
        var chunkCount = 0
        
        while (l > 0) {
            k = if (l < NMAX) l else NMAX
            l -= k
            chunkCount++
            ZlibLogger.logAdler32("Processing chunk $chunkCount: k=$k bytes, remaining l=$l", "adler32")
            
            while (k >= 16) {
                // Unrolled loop for performance - process 16 bytes at once
                val startS1 = s1
                val startS2 = s2
                s1 += buf[i++].toUByte().toLong(); s2 += s1
                s1 += buf[i++].toUByte().toLong(); s2 += s1
                s1 += buf[i++].toUByte().toLong(); s2 += s1
                s1 += buf[i++].toUByte().toLong(); s2 += s1
                s1 += buf[i++].toUByte().toLong(); s2 += s1
                s1 += buf[i++].toUByte().toLong(); s2 += s1
                s1 += buf[i++].toUByte().toLong(); s2 += s1
                s1 += buf[i++].toUByte().toLong(); s2 += s1
                s1 += buf[i++].toUByte().toLong(); s2 += s1
                s1 += buf[i++].toUByte().toLong(); s2 += s1
                s1 += buf[i++].toUByte().toLong(); s2 += s1
                s1 += buf[i++].toUByte().toLong(); s2 += s1
                s1 += buf[i++].toUByte().toLong(); s2 += s1
                s1 += buf[i++].toUByte().toLong(); s2 += s1
                s1 += buf[i++].toUByte().toLong(); s2 += s1
                s1 += buf[i++].toUByte().toLong(); s2 += s1
                k -= 16
                ZlibLogger.logAdler32("16-byte block: s1 $startS1->$s1 (+${s1-startS1}), s2 $startS2->$s2 (+${s2-startS2})", "adler32")
            }
            if (k != 0) {
                ZlibLogger.logAdler32("Processing remaining $k bytes individually", "adler32")
                do {
                    val byte = buf[i++].toUByte().toLong()
                    s1 += byte
                    s2 += s1
                    ZlibLogger.logAdler32Calc(s1, s2, byte.toInt(), i-1, "adler32")
                } while (--k != 0)
            }
            val beforeMod1 = s1
            val beforeMod2 = s2
            s1 %= BASE
            s2 %= BASE
            ZlibLogger.logAdler32("Modulo operation: s1 $beforeMod1->$s1, s2 $beforeMod2->$s2 (mod $BASE)", "adler32")
        }
        
        val result = (s2 shl 16) or s1
        ZlibLogger.logAdler32("Final result: s1=$s1, s2=$s2, combined=${result} (0x${result.toString(16)})", "adler32")
        return result
    }

    companion object {
        // largest prime smaller than 65536
        private const val BASE = 65521

        // NMAX is the largest n such that 255n(n+1)/2 + (n+1)(BASE-1) <= 2^32-1
        private const val NMAX = 5552
    }
}
