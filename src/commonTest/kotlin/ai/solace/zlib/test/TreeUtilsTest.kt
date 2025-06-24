package ai.solace.zlib.test

import ai.solace.zlib.common.END_BLOCK
import ai.solace.zlib.common.L_CODES
import ai.solace.zlib.common.MAX_BITS
import ai.solace.zlib.deflate.StaticTree
import ai.solace.zlib.deflate.biReverse
import ai.solace.zlib.deflate.dCode
import ai.solace.zlib.deflate.genCodes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue


class TreeUtilsTest {
    @Test
    fun initialTest() {
        assertTrue(true, "Placeholder test for TreeUtilsTest")
    }

    @Test
    fun testDCode() {
        // Test cases based on RFC 1951, section 3.2.6 and typical d_code logic
        // TREE_DIST_CODE is used by d_code
        assertEquals(0, dCode(0), "d_code(0) failed")
        assertEquals(1, dCode(1), "d_code(1) failed")
        assertEquals(2, dCode(2), "d_code(2) failed")
        assertEquals(3, dCode(3), "d_code(3) failed")
        assertEquals(4, dCode(4), "d_code(4) failed")

        // Based on typical zlib d_code mapping using TREE_DIST_CODE from Constants.kt
        // (Assuming TREE_DIST_CODE is populated as per standard zlib tables)
        assertEquals(5, dCode(6), "d_code(6) failed") // Example
        // assertEquals(16, d_code(257), "d_code for dist 257 (actual value 257) failed") // d_code takes distance - 1
        assertEquals(16, dCode(257-1), "d_code for dist 257 (idx 256) failed") // distance = 257, so dist_code index is 256
        assertEquals(16, dCode(300-1), "d_code for dist 300 (idx 299) failed")
        assertEquals(17, dCode(385-1), "d_code for dist 385 (idx 384) failed")

        assertEquals(29, dCode(24577-1), "d_code for dist 24577 (idx 24576) failed")
        assertEquals(29, dCode(32768-1), "d_code for dist 32768 (idx 32767) failed")
    }

    @Test
    fun testBiReverse() {
        // bi_reverse(code, len)
        assertEquals(0b1011, biReverse(0b1101, 4), "bi_reverse(13, 4) failed") // 13 (1101) -> 11 (1011)
        assertEquals(0b1, biReverse(0b1, 1), "bi_reverse(1, 1) failed")     // 1 (1) -> 1 (1)
        assertEquals(0b0, biReverse(0b0, 1), "bi_reverse(0, 1) failed")     // 0 (0) -> 0 (0)
        assertEquals(0b010, biReverse(0b010, 3), "bi_reverse(2, 3) failed") // 2 (010) -> 2 (010)
        assertEquals(0b0001, biReverse(0b1000, 4), "bi_reverse(8, 4) failed") // 8 (1000) -> 1 (0001)
        assertEquals(85, biReverse(85, 7), "bi_reverse(85, 7) failed")       // 85 (1010101) -> 85 (1010101)
        assertEquals(64, biReverse(1, 7), "bi_reverse(1, 7) for len 7 failed") // 1 (0000001) -> 64 (1000000)
        assertEquals(0, biReverse(0, 5), "bi_reverse(0,5) failed") // 0 (00000) -> 0 (00000)
    }

    @Test
    fun testGenCodesSimpleScenario() {
        // gen_codes(tree: ShortArray, max_code: Int, bl_count: ShortArray)
        val maxCode = 2
        val tree = ShortArray((maxCode + 1) * 2)
        val blCount = ShortArray(MAX_BITS + 1)

        // Code 0: length 1
        // Code 1: length 2
        // Code 2: length 2
        tree[0 * 2 + 1] = 1
        tree[1 * 2 + 1] = 2
        tree[2 * 2 + 1] = 2

        blCount[1] = 1
        blCount[2] = 2

        genCodes(tree, maxCode, blCount)

        // Expected Huffman codes (after bi_reverse):
        // Code 0 (len 1): Initial code 0. bi_reverse(0, 1) = 0.
        // Codes of len 2 start after codes of len 1.
        // Next_code for len 1 is 0.
        // Next_code for len 2 is (0 + bl_count[1]) << 1 = (0+1)<<1 = 2.
        // Code 1 (len 2): Initial code 2. bi_reverse(2, 2) = bi_reverse(0b10, 2) = 0b01 = 1.
        // Code 2 (len 2): Initial code 3. bi_reverse(3, 2) = bi_reverse(0b11, 2) = 0b11 = 3.

        val expectedTree = shortArrayOf(
            biReverse(0, 1).toShort(), 1,  // Code 0, Huffman code 0
            biReverse(2, 2).toShort(), 2,  // Code 1, Huffman code 1
            biReverse(3, 2).toShort(), 2   // Code 2, Huffman code 3
        )
        // Corrected assertion: tree elements are Short, ensure comparison is consistent
        val actualCodes = tree.map { it.toInt() }
        val expectedCodesInt = expectedTree.map { it.toInt() }
        assertEquals(expectedCodesInt.toList(), actualCodes.toList(), "gen_codes did not produce expected Huffman codes for simple scenario.")
    }

    // Placeholder for a more complex gen_codes test, perhaps with StaticTree
    @Test
    fun testGenCodesWithStaticTree() {
        // This test is more to ensure gen_codes runs without error on static trees
        // and perhaps check a known value like the code for END_BLOCK.
        // A full verification of all static tree codes is extensive.

        val staticLDesc = StaticTree.static_l_desc
        // IMPORTANT: Use a copy for gen_codes as it modifies the tree array
        val staticLTreeCopy = StaticTree.static_ltree.copyOf()
        val blCount = ShortArray(MAX_BITS + 1)

        // Populate bl_count for static_ltree (lengths are in static_ltree[i*2+1])
        // The original static_ltree is already populated with lengths.
        for (i in 0 until L_CODES) { // max_code for static_ltree is L_CODES - 1
            val len = StaticTree.static_ltree[i * 2 + 1].toInt() // Read from original static tree
            if (len != 0) {
                blCount[len]++
            }
        }

        try {
            // gen_codes will populate staticLTreeCopy[i*2] with the huffman codes
            genCodes(staticLTreeCopy, staticLDesc.maxCode, blCount)

            // Check a known code, e.g. END_BLOCK (value 256)
            // The length of END_BLOCK code in static_ltree is 7. (from StaticTree.static_ltree[END_BLOCK*2+1])
            // The Huffman code assigned by gen_codes for END_BLOCK (256) in static_ltree is 0x00 (after bi_reverse).
            // Before bi_reverse, it's code 0 for length 7. bi_reverse(0, 7) = 0.
            assertEquals(0.toShort(), staticLTreeCopy[END_BLOCK * 2], "END_BLOCK code in static_ltree mismatch after gen_codes.")
            assertTrue(true, "gen_codes ran with static_ltree without throwing an error.")
        } catch (e: Exception) {
            assertTrue(false, "gen_codes threw an exception with static_ltree: ${e.message}")
        }
    }
}
