package ai.solace.zlib.bitwise.checksum

import ai.solace.zlib.bitwise.BitwiseOps
import ai.solace.zlib.bitwise.BitShiftEngine
import ai.solace.zlib.bitwise.BitShiftMode
import ai.solace.zlib.common.ADLER_BASE
import ai.solace.zlib.common.ADLER_NMAX

/**
 * Adler32 checksum implementation using configurable bit shift operations.
 * 
 * This implementation can use either:
 * - Native Kotlin bitwise operations (fast, but may have platform differences)
 * - Arithmetic-only operations (slower, but fully consistent across platforms)
 * 
 * The choice is made through the BitShiftEngine configuration.
 * 
 * Algorithm:
 * - Data is processed in chunks of ADLER_NMAX bytes to prevent integer overflow
 * - For each chunk: a = sum of bytes (mod 65521), b = sum of running sums (mod 65521)
 * - Final checksum: b * 65536 + a
 * 
 * This implementation follows the standard Adler-32 algorithm specification.
 */
class Adler32Utils {
    companion object {
        
        // Default engine uses native operations for better performance
        private val defaultEngine = BitShiftEngine(BitShiftMode.NATIVE, 32)
        
        /**
         * Calculates or updates an Adler-32 checksum using the default bit shift engine
         * @param adler Initial checksum value (use 1 for new checksums)
         * @param buf Data buffer to calculate checksum for
         * @param index Starting index in the buffer
         * @param len Number of bytes to process
         * @return Updated Adler-32 checksum
         */
        fun adler32(adler: Long, buf: ByteArray?, index: Int, len: Int): Long {
            return adler32(adler, buf, index, len, defaultEngine)
        }
        
        /**
         * Calculates or updates an Adler-32 checksum using the specified bit shift engine
         * @param adler Initial checksum value (use 1 for new checksums)
         * @param buf Data buffer to calculate checksum for
         * @param index Starting index in the buffer
         * @param len Number of bytes to process
         * @param engine BitShiftEngine to use for bit operations
         * @return Updated Adler-32 checksum
         */
        fun adler32(adler: Long, buf: ByteArray?, index: Int, len: Int, engine: BitShiftEngine): Long {
            if (buf == null) return 1L

            val MOD = ADLER_BASE
            
            // Extract a and b from the input adler value using the engine
            val highExtracted = engine.unsignedRightShift(adler, 16)
            var a = (adler and 0xFFFF).toInt()
            var b = highExtracted.value.toInt()

            var i = index
            val end = index + len
            
            // Process data in chunks of ADLER_NMAX to prevent overflow
            while (i < end) {
                val chunkEnd = minOf(i + ADLER_NMAX, end)
                
                // Process bytes in current chunk without modulo
                while (i < chunkEnd) {
                    val unsigned = BitwiseOps.byteToUnsignedInt(buf[i])
                    a += unsigned
                    b += a
                    i++
                }
                
                // Apply modulo only after processing the chunk
                a %= MOD
                b %= MOD
            }

            // Combine a and b into the final result using the engine
            val bShifted = engine.leftShift(b.toLong(), 16)
            return bShifted.value or (a.toLong() and 0xFFFF)
        }
        
        /**
         * Creates an Adler32Utils instance that uses arithmetic operations for full cross-platform consistency
         * @return Configured utility function
         */
        fun withArithmeticEngine(): (Long, ByteArray?, Int, Int) -> Long {
            val arithmeticEngine = BitShiftEngine(BitShiftMode.ARITHMETIC, 32)
            return { adler, buf, index, len -> adler32(adler, buf, index, len, arithmeticEngine) }
        }
        
        /**
         * Creates an Adler32Utils instance that uses native operations for best performance
         * @return Configured utility function
         */
        fun withNativeEngine(): (Long, ByteArray?, Int, Int) -> Long {
            val nativeEngine = BitShiftEngine(BitShiftMode.NATIVE, 32)
            return { adler, buf, index, len -> adler32(adler, buf, index, len, nativeEngine) }
        }
    }
}