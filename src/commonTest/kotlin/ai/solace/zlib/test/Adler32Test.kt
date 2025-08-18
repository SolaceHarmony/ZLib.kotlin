package ai.solace.zlib.test

import ai.solace.zlib.deflate.Adler32
import ai.solace.zlib.common.ADLER_BASE
import ai.solace.zlib.common.ADLER_NMAX
import ai.solace.zlib.common.ZlibLogger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

class Adler32Test {

    @Test
    fun testBasicFunctionality() {
        val adler32 = Adler32()

        // Test with null buffer (should return 1)
        val nullResult = adler32.adler32(0, null, 0, 0)
        assertEquals(1L, nullResult, "Adler32 of null buffer should be 1")

        // Test with empty buffer
        val emptyBuffer = ByteArray(0)
        val emptyResult = adler32.adler32(1L, emptyBuffer, 0, 0)
        assertEquals(1L, emptyResult, "Adler32 of empty buffer should remain unchanged")

        // Test with a simple string "abc"
        val testString = "abc"
        val testBuffer = testString.encodeToByteArray()
        val testResult = adler32.adler32(1L, testBuffer, 0, testBuffer.size)

        // Expected value for "abc" is 0x024D0127 (standard Adler32 with initial value 1)
        assertEquals(0x024D0127L, testResult, "Adler32 of 'abc' should be 0x024D0127")

        // Test with a longer string to ensure NMAX handling works correctly
        val longString = "a".repeat(5000)
        val longBuffer = longString.encodeToByteArray()
        val longResult = adler32.adler32(1L, longBuffer, 0, longBuffer.size)

        // This value should be consistent regardless of NMAX value (3854 or 5552)
        // The actual value is calculated based on the algorithm
        val expectedLongResult = calculateExpectedAdler32(longBuffer)
        assertEquals(expectedLongResult, longResult, "Adler32 of long string should match expected value")
    }

    @Test
    fun testWikipediaExample() {
        val adler32 = Adler32()

        // Test with the Wikipedia example string "Wikipedia"
        val wikipediaString = "Wikipedia"
        val wikipediaBuffer = wikipediaString.encodeToByteArray()
        val wikipediaResult = adler32.adler32(1L, wikipediaBuffer, 0, wikipediaBuffer.size)

        // Expected value for "Wikipedia" is 0x11E60398 (300286872) as per the Wikipedia example
        assertEquals(0x11E60398L, wikipediaResult, "Adler32 of 'Wikipedia' should be 0x11E60398 (300286872)")

        // Verify the calculation step by step as shown in the Wikipedia example
        var s1 = 1L
        var s2 = 0L

        // Character: W, ASCII: 87
        s1 += 87 // 1 + 87 = 88
        s2 += s1 // 0 + 88 = 88

        // Character: i, ASCII: 105
        s1 += 105 // 88 + 105 = 193
        s2 += s1 // 88 + 193 = 281

        // Character: k, ASCII: 107
        s1 += 107 // 193 + 107 = 300
        s2 += s1 // 281 + 300 = 581

        // Character: i, ASCII: 105
        s1 += 105 // 300 + 105 = 405
        s2 += s1 // 581 + 405 = 986

        // Character: p, ASCII: 112
        s1 += 112 // 405 + 112 = 517
        s2 += s1 // 986 + 517 = 1503

        // Character: e, ASCII: 101
        s1 += 101 // 517 + 101 = 618
        s2 += s1 // 1503 + 618 = 2121

        // Character: d, ASCII: 100
        s1 += 100 // 618 + 100 = 718
        s2 += s1 // 2121 + 718 = 2839

        // Character: i, ASCII: 105
        s1 += 105 // 718 + 105 = 823
        s2 += s1 // 2839 + 823 = 3662

        // Character: a, ASCII: 97
        s1 += 97 // 823 + 97 = 920
        s2 += s1 // 3662 + 920 = 4582

        // Final values: s1 = 920 (0x398), s2 = 4582 (0x11E6)
        // Combined: (s2 << 16) | s1 = 0x11E60398 = 300286872
        assertEquals(920L, s1, "s1 should be 920 (0x398)")
        assertEquals(4582L, s2, "s2 should be 4582 (0x11E6)")
        assertEquals(0x11E60398L, (s2 shl 16) or s1, "Combined value should be 0x11E60398")
    }

    @Test
    fun testIncrementalUpdates() {
        val adler32 = Adler32()

        // Test incremental updates with the string "Hello, World!"
        val testString = "Hello, World!"
        val testBuffer = testString.encodeToByteArray()

        // Split the buffer into multiple parts
        val part1 = testBuffer.copyOfRange(0, 5) // "Hello"
        val part2 = testBuffer.copyOfRange(5, 7) // ", "
        val part3 = testBuffer.copyOfRange(7, testBuffer.size) // "World!"

        // Calculate the checksum incrementally
        var checksum = 1L // Initial value
        checksum = adler32.adler32(checksum, part1, 0, part1.size)
        checksum = adler32.adler32(checksum, part2, 0, part2.size)
        checksum = adler32.adler32(checksum, part3, 0, part3.size)

        // Calculate the checksum in one go for comparison
        val expectedChecksum = adler32.adler32(1L, testBuffer, 0, testBuffer.size)

        // The incremental checksum should match the one-go checksum
        assertEquals(expectedChecksum, checksum, "Incremental checksum should match one-go checksum")
    }

    @Test
    fun testEdgeCases() {
        val adler32 = Adler32()

        // Test with short messages (weakness mentioned in the issue description)
        // For short messages, the checksum has poor coverage of the 32 available bits
        val shortMessage = "abc"
        val shortBuffer = shortMessage.encodeToByteArray()
        val shortResult = adler32.adler32(1L, shortBuffer, 0, shortBuffer.size)

        // The result should be less than 65521 (ADLER_BASE) for the s1 part
        assertTrue((shortResult and 0xffff) < ADLER_BASE, "s1 part should be less than ADLER_BASE")

        // Test with small incremental changes (weakness mentioned in the issue description)
        val baseString = "base string for testing"
        val baseBuffer = baseString.encodeToByteArray()
        val baseResult = adler32.adler32(1L, baseBuffer, 0, baseBuffer.size)

        val changedString = "base string for testinG" // Changed last character to uppercase
        val changedBuffer = changedString.encodeToByteArray()
        val changedResult = adler32.adler32(1L, changedBuffer, 0, changedBuffer.size)

        // The checksums should be different
        assertTrue(baseResult != changedResult, "Checksums should be different for small changes")

        // Test with strings generated from common prefix and consecutive numbers
        // (weakness mentioned in the issue description)
        val prefix = "label_"
        val checksums = mutableSetOf<Long>()

        for (i in 1..100) {
            val labelString = "$prefix$i"
            val labelBuffer = labelString.encodeToByteArray()
            val labelResult = adler32.adler32(1L, labelBuffer, 0, labelBuffer.size)
            checksums.add(labelResult)
        }

        // There should be 100 unique checksums (no collisions)
        assertEquals(100, checksums.size, "There should be 100 unique checksums for 100 different labels")
    }

    @Test
    fun testBoundaryConditions() {
        val adler32 = Adler32()

        // Test with a string of length exactly ADLER_NMAX
        val exactNmaxString = "a".repeat(ADLER_NMAX)
        val exactNmaxBuffer = exactNmaxString.encodeToByteArray()
        val exactNmaxResult = adler32.adler32(1L, exactNmaxBuffer, 0, exactNmaxBuffer.size)

        // Test with a string of length ADLER_NMAX + 1
        val overNmaxString = "a".repeat(ADLER_NMAX + 1)
        val overNmaxBuffer = overNmaxString.encodeToByteArray()
        val overNmaxResult = adler32.adler32(1L, overNmaxBuffer, 0, overNmaxBuffer.size)

        // The results should be different
        assertTrue(exactNmaxResult != overNmaxResult, "Checksums should be different for NMAX and NMAX+1 length strings")

        // Test with a string of length 2*ADLER_NMAX
        val doubleNmaxString = "a".repeat(2 * ADLER_NMAX)
        val doubleNmaxBuffer = doubleNmaxString.encodeToByteArray()
        val doubleNmaxResult = adler32.adler32(1L, doubleNmaxBuffer, 0, doubleNmaxBuffer.size)

        // Calculate the expected result using our helper function
        val expectedDoubleNmaxResult = calculateExpectedAdler32(doubleNmaxBuffer)
        assertEquals(expectedDoubleNmaxResult, doubleNmaxResult, "Checksum for 2*NMAX length string should match expected value")
    }
    
    @Test
    fun testAdlerNmaxRegression() {
        val adler32 = Adler32()

        // Regression test for ADLER_NMAX boundary conditions as per issue #24
        // Test with inputs around ADLER_NMAX length (n = ADLER_NMAX, n = ADLER_NMAX + 1)
        
        // Test with exactly n = ADLER_NMAX
        val nmaxBuffer = ByteArray(ADLER_NMAX) { i -> (i % 256).toByte() }
        val nmaxResult = adler32.adler32(1L, nmaxBuffer, 0, nmaxBuffer.size)
        
        // Test with n = ADLER_NMAX + 1
        val nmaxPlus1Buffer = ByteArray(ADLER_NMAX + 1) { i -> (i % 256).toByte() }
        val nmaxPlus1Result = adler32.adler32(1L, nmaxPlus1Buffer, 0, nmaxPlus1Buffer.size)
        
        // Results should be different (this verifies chunking behavior)
        assertTrue(nmaxResult != nmaxPlus1Result, "Checksums should be different for n=ADLER_NMAX and n=ADLER_NMAX+1")
        
        // Test with n = ADLER_NMAX - 1
        val nmaxMinus1Buffer = ByteArray(ADLER_NMAX - 1) { i -> (i % 256).toByte() }
        val nmaxMinus1Result = adler32.adler32(1L, nmaxMinus1Buffer, 0, nmaxMinus1Buffer.size)
        
        // All three results should be different
        assertTrue(nmaxResult != nmaxMinus1Result, "Checksums should be different for n=ADLER_NMAX and n=ADLER_NMAX-1")
        assertTrue(nmaxPlus1Result != nmaxMinus1Result, "Checksums should be different for n=ADLER_NMAX+1 and n=ADLER_NMAX-1")
        
        // Verify that our implementation matches the expected algorithm for these boundary cases
        assertEquals(calculateExpectedAdler32(nmaxBuffer), nmaxResult, "n=ADLER_NMAX result should match expected")
        assertEquals(calculateExpectedAdler32(nmaxPlus1Buffer), nmaxPlus1Result, "n=ADLER_NMAX+1 result should match expected")
        assertEquals(calculateExpectedAdler32(nmaxMinus1Buffer), nmaxMinus1Result, "n=ADLER_NMAX-1 result should match expected")
        
        // Test that large inputs produce consistent results with chunking
        val largeBuffer = ByteArray(11000) { i -> (i % 256).toByte() } // ~2 chunks
        val largeResult = adler32.adler32(1L, largeBuffer, 0, largeBuffer.size)
        val expectedLargeResult = calculateExpectedAdler32(largeBuffer)
        assertEquals(expectedLargeResult, largeResult, "Large buffer result should match expected with proper chunking")
    }

    @OptIn(ExperimentalTime::class)
    @Test
    fun testPerformance() {
        val adler32 = Adler32()

        // Test with a large string to measure performance
        val largeString = "a".repeat(1_000_000)
        val largeBuffer = largeString.encodeToByteArray()

        // Measure the time it takes to calculate the checksum
        val duration: Duration = measureTime {
            adler32.adler32(1L, largeBuffer, 0, largeBuffer.size)
        }

        // Print the duration for informational purposes
        ZlibLogger.log("Time to calculate Adler32 for 1,000,000 bytes: $duration")

        // No assertion here, just informational
    }

    @Test
    fun testComparisonWithReference() {
        val adler32 = Adler32()

        // Test with the C example from the issue description
        val testString = "data"
        val testBuffer = testString.encodeToByteArray()

        // Calculate using our implementation
        val result = adler32.adler32(1L, testBuffer, 0, testBuffer.size)

        // Calculate using the reference implementation (our helper function)
        val expectedResult = calculateExpectedAdler32(testBuffer)

        // The results should match
        assertEquals(expectedResult, result, "Our implementation should match the reference implementation")
    }

    // Helper function to calculate expected Adler32 value using the same chunked algorithm
    private fun calculateExpectedAdler32(buffer: ByteArray): Long {
        var s1 = 1L
        var s2 = 0L
        var i = 0

        // Process data in chunks of ADLER_NMAX to prevent overflow
        while (i < buffer.size) {
            val chunkEnd = minOf(i + ADLER_NMAX, buffer.size)
            
            // Process bytes in current chunk without modulo
            while (i < chunkEnd) {
                s1 += (buffer[i].toInt() and 0xff)
                s2 += s1
                i++
            }
            
            // Apply modulo only after processing the chunk
            s1 %= ADLER_BASE
            s2 %= ADLER_BASE
        }

        return (s2 shl 16) or s1
    }
}
