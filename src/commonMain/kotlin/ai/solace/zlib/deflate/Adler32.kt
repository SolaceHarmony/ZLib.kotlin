package ai.solace.zlib.deflate

import ai.solace.zlib.common.*

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
 */
class Adler32 {
    /**
     * Calculates an Adler-32 checksum for the given data.
     *
     * This method is used internally by the ZStream class to calculate checksums
     * for compressed and decompressed data.
     *
     * @param adler Initial value for the checksum calculation. Use 1 to start a new calculation.
     * @param buf The data buffer to calculate the checksum for. If null, returns 1.
     * @param index Starting position in the buffer.
     * @param len Number of bytes to process.
     * @return The calculated Adler-32 checksum.
     */
    fun adler32(adler: Long, buf: ByteArray?, index: Int, len: Int): Long {
        // If buffer is null, return initial Adler-32 value (1)
        if (buf == null) {
            return 1L
        }

        // Extract the component values from the current adler value
        // s1 represents the A component (sum of all bytes plus one)
        // s2 represents the B component (sum of all A values at each step)
        var s1 = adler and 0xffff
        var s2 = (adler shr 16) and 0xffff
        var localLen = len
        var localIndex = index

        // Process the data in chunks to calculate the checksum
        while (localLen > 0) {
            // Process at most ADLER_NMAX bytes at a time
            // ADLER_NMAX is chosen to prevent overflow before applying the modulo operation
            // It's the largest n such that 255n(n+1)/2 + (n+1)(BASE-1) <= 2^31-1
            var k = if (localLen < ADLER_NMAX) localLen else ADLER_NMAX
            localLen -= k

            // Process each byte in the current chunk
            while (k > 0) {
                // Update s1: Add the current byte value
                // This implements: A = 1 + D₁ + D₂ + ... + Dₙ
                s1 += (buf[localIndex++].toInt() and 0xff).toLong()

                // Update s2: Add the updated s1 value
                // This implements: B = (1 + D₁) + (1 + D₁ + D₂) + ... + (1 + D₁ + D₂ + ... + Dₙ)
                s2 += s1

                k--
            }

            // Apply modulo after processing each chunk to prevent overflow
            // We use ADLER_BASE (65521) as the modulus, which is the largest prime less than 65536
            s1 %= ADLER_BASE
            s2 %= ADLER_BASE
        }

        // Combine s1 and s2 to form the final checksum
        // The formula is: Adler-32(D) = B × 65536 + A
        // Which is implemented as: (s2 << 16) | s1
        return (s2 shl 16) or s1
    }
}
