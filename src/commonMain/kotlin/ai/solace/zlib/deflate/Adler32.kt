package ai.solace.zlib.deflate

import ai.solace.zlib.bitwise.checksum.Adler32Utils

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
    fun adler32(
        adler: Long,
        buf: ByteArray?,
        index: Int,
        len: Int,
    ): Long {
        // Delegate to the unified arithmetic-only implementation to ensure consistency across platforms
        return Adler32Utils.adler32(adler, buf, index, len)
    }
}
