package ai.solace.zlib.inflate

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class CanonicalHuffmanFullTableInvariantTest {
    @Test
    fun constructingFullTableWithMismatchedSizesShouldFail() {
        assertFailsWith<IllegalArgumentException> {
            CanonicalHuffman.FullTable(
                maxLen = 3,
                bits = IntArray(8),
                vals = IntArray(7)
            )
        }
    }

    @Test
    fun constructingFullTableWithSizeNotEqualTo2PowMaxLenShouldFail() {
        // maxLen=4 requires size 16, but we pass 8
        assertFailsWith<IllegalArgumentException> {
            CanonicalHuffman.FullTable(
                maxLen = 4,
                bits = IntArray(8),
                vals = IntArray(8)
            )
        }
    }

    @Test
    fun constructingFullTableWithMaxLenGreaterThan15ShouldFail() {
        assertFailsWith<IllegalArgumentException> {
            CanonicalHuffman.FullTable(
                maxLen = 16,
                bits = IntArray(16),
                vals = IntArray(16)
            )
        }
    }

    @Test
    fun buildFullShouldRejectCodeLengthsExceedingDeflateLimit() {
        // A lengths array with one symbol of length 16 is invalid for DEFLATE
        val lengths = IntArray(1) { 16 }
        assertFailsWith<IllegalArgumentException> {
            CanonicalHuffman.buildFull(lengths)
        }
    }

    @Test
    fun buildFullShouldConstructValidTableForTypicalLengths() {
        // Valid simple table: two symbols with 1-bit codes
        val lengths = intArrayOf(1, 1)
        val table = CanonicalHuffman.buildFull(lengths)
        assertNotNull(table)
    }
}
