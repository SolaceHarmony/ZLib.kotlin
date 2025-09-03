package ai.solace.zlib.clean

import ai.solace.zlib.bitwise.ArithmeticBitwiseOps
import ai.solace.zlib.common.ZlibLogger

/**
 * Canonical Huffman builder and simple decoder for clarity and debugging.
 *
 * Given code lengths per symbol, assigns canonical codes and decodes by
 * reading one bit at a time (sufficient for validation and provenance runs).
 */
object CanonicalHuffman {
    class Node(var left: Node? = null, var right: Node? = null, var sym: Int = -1)
    data class Table(
        val maxLen: Int,
        val codesByLen: Array<MutableMap<Int, Int>>, // len -> map(code -> symbol) (for debugging)
        val root: Node
    )

    /**
     * Build canonical codes from code lengths. See RFC 1951.
     */
    fun build(lengths: IntArray): Table {
        val ops = ArithmeticBitwiseOps.BITS_32
        val maxLen = lengths.maxOrNull() ?: 0
        val blCount = IntArray(maxLen + 1)
        for (l in lengths) if (l > 0) blCount[l]++

        val nextCode = IntArray(maxLen + 1)
        var code = 0
        for (bits in 1..maxLen) {
            // Use arithmetic operations instead of native bitwise
            code = ops.leftShift((code + blCount[bits - 1]).toLong(), 1).toInt()
            nextCode[bits] = code
        }

        val codesByLen = Array(maxLen + 1) { mutableMapOf<Int, Int>() }
        val root = Node()
        for (sym in lengths.indices) {
            val len = lengths[sym]
            if (len == 0) continue
            val assigned = nextCode[len]
            nextCode[len] = assigned + 1
            // Reverse code bits so MSB-first traversal of 'rev' matches LSB-first reading order
            val rev = reverseBits(assigned, len)
            codesByLen[len][rev] = sym
            if (sym == 256 || sym == 65 || sym == 97) {
                kotlin.io.println("[HUFF-BUILD] sym=$sym len=$len assigned=${assigned.toString(2)} rev=${rev.toString(2)}")
            }
            var node = root
            for (i in (len - 1) downTo 0) {
                // Use arithmetic operations instead of native bitwise
                val bit = ops.and(ops.rightShift(rev.toLong(), i), 1L).toInt()
                // Try inverted mapping to match stream bit order
                if (bit == 0) {
                    if (node.right == null) node.right = Node()
                    node = node.right!!
                } else {
                    if (node.left == null) node.left = Node()
                    node = node.left!!
                }
            }
            node.sym = sym
        }
        return Table(maxLen, codesByLen, root)
    }

    /**
     * Memory-for-simplicity decode table: size = 2^maxLen.
     * Index by the low maxLen bits from the bitstream.
     * bits[i] = number of bits to consume (0 means invalid entry)
     * vals[i] = symbol for that entry
     */
    data class FullTable(
        val maxLen: Int,
        val bits: IntArray,
        val vals: IntArray
    )

    /**
     * Build a full lookup table occupying 2^maxLen entries.
     * For each symbol with length L and canonical code C (MSB-first), we reverse(C,L) to get
     * the bit order as it appears on the wire (LSB-first). We then fill all indices whose low L bits
     * equal that reversed code.
     */
    fun buildFull(lengths: IntArray): FullTable {
        val ops = ArithmeticBitwiseOps.BITS_32
        val maxLen = lengths.maxOrNull() ?: 0
        if (maxLen == 0) return FullTable(0, IntArray(1), IntArray(1))

        val blCount = IntArray(maxLen + 1)
        for (l in lengths) if (l > 0) blCount[l]++

        val nextCode = IntArray(maxLen + 1)
        var code = 0
        for (bits in 1..maxLen) {
            // Use arithmetic operations instead of native bitwise
            code = ops.leftShift((code + blCount[bits - 1]).toLong(), 1).toInt()
            nextCode[bits] = code
        }

        // Use arithmetic operations instead of native bitwise
        val size = ops.leftShift(1L, maxLen).toInt()
        val bitsTab = IntArray(size)
        val valsTab = IntArray(size)

        for (sym in lengths.indices) {
            val len = lengths[sym]
            if (len == 0) continue
            val assigned = nextCode[len]
            nextCode[len] = assigned + 1
            val rev = reverseBits(assigned, len)
            // Use arithmetic operations instead of native bitwise
            val stride = ops.leftShift(1L, len).toInt()
            var idx = rev
            while (idx < size) {
                bitsTab[idx] = len
                valsTab[idx] = sym
                idx += stride
            }
        }
        return FullTable(maxLen, bitsTab, valsTab)
    }

    /** Decode one symbol using the FullTable approach. */
    fun decodeOne(br: BitReader, table: FullTable): Int {
        if (table.maxLen == 0) throw IllegalStateException("Empty Huffman table")
        // Ensure we have up to maxLen bits available
        val look = br.peek(table.maxLen)
        val len = table.bits[look]
        if (len == 0) {
            val upcoming = br.peekBytes(8)
            val hex = upcoming.joinToString("") { b -> ((b.toInt() and 0xFF).toString(16).padStart(2, '0')) }
            throw IllegalStateException("Invalid Huffman prefix (look=${look.toString(2).padStart(table.maxLen,'0')}) nextBytes=$hex")
        }
        val sym = table.vals[look]
        br.take(len)
        return sym
    }
    /** Reverse the lowest len bits of x (for LSB-first decoding). */
    private fun reverseBits(x: Int, len: Int): Int {
        val ops = ArithmeticBitwiseOps.BITS_32
        var v = x
        var r = 0
        repeat(len) {
            // Use arithmetic operations instead of native bitwise
            r = ops.or(ops.leftShift(r.toLong(), 1), ops.and(v.toLong(), 1L)).toInt()
            v = ops.rightShift(v.toLong(), 1).toInt()
        }
        return r
    }

    /**
     * Decode one symbol from the bitstream using the canonical table.
     * Reads one bit at a time to form the code and checks the map for that length.
     */
    fun decodeOne(br: BitReader, table: Table): Int {
        var node = table.root
        var len = 0
        while (len < table.maxLen) {
            val bit = br.take(1)
            kotlin.io.println("[HUFF] take bit=$bit len=${len + 1}")
            node = if (bit == 0) node.left ?: break else node.right ?: break
            len++
            if (node.sym >= 0) {
                ZlibLogger.log("[DEBUG_LOG] Huffman.decode len=$len sym=${node.sym}")
                kotlin.io.println("[HUFF] dec len=$len sym=${node.sym}")
                return node.sym
            }
        }
        throw IllegalStateException("Huffman decode failure: no path to leaf within ${table.maxLen} bits")
    }
}
