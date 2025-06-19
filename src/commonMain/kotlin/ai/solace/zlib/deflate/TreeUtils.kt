package ai.solace.zlib.deflate

import ai.solace.zlib.common.*
import componentace.compression.libs.zlib.deflate.Deflate // Required for Deflate type
import componentace.compression.libs.zlib.deflate.Tree // Required for Tree type

// This file will house utility functions previously in Tree.kt's companion object or Tree class.
// Functions will be adjusted to be top-level and accept necessary instances if they were member functions.

// --- Functions moved from Tree.Companion ---

// Mapping from a distance to a distance code. dist is the distance - 1 and
// must not have side effects. _dist_code[256] and _dist_code[257] are never
// used.
internal fun d_code(dist: Int): Int { // Changed visibility to internal as it was default (public) in companion but seems util
    return if ((dist) < 256) TREE_DIST_CODE[dist].toInt() else TREE_DIST_CODE[256 + (dist shr 7)].toInt()
}

// Generate the codes for a given tree and bit counts (which need not be
// optimal).
// IN assertion: the array bl_count contains the bit length statistics for
// the given tree and the field len is set for all tree elements.
// OUT assertion: the field code is set for all tree elements of non
//     zero code length.
internal fun gen_codes(tree: ShortArray, max_code: Int, bl_count: ShortArray) { // Changed visibility
    val next_code = ShortArray(MAX_BITS + 1) // next code value for each bit length
    var code: Short = 0 // running code value
    var bits: Int // bit index
    var n: Int // code index

    // The distribution counts are first used to generate the code values
    // without bit reversal.
    bits = 1
    while (bits <= MAX_BITS) {
        next_code[bits] = (code + bl_count[bits - 1] shl 1).toShort()
        code = next_code[bits]
        bits++
    }

    // Check that the bit counts in bl_count are consistent. The last code
    // must be all ones.
    //Assert (code + bl_count[MAX_BITS]-1 == (1<<MAX_BITS)-1,
    //        "inconsistent bit counts");
    //Tracev((stderr,"\ngen_codes: max_code %d ", max_code));

    var len: Int
    n = 0
    while (n <= max_code) {
        len = tree[n * 2 + 1].toInt()
        if (len != 0) {
            // Now reverse the bits
            tree[n * 2] = bi_reverse(next_code[len]++, len).toShort()
        }
        n++
    }
}

// Reverse the first len bits of a code, using straightforward code (a faster
// method would use a table)
// IN assertion: 1 <= len <= 15
internal fun bi_reverse(code: Int, len: Int): Int { // Changed visibility
    var res = 0
    var c = code
    var l = len
    do {
        res = res or (c and 1)
        c = c ushr 1
        res = res shl 1
    } while (--l > 0)
    return res ushr 1
}

// --- Functions moved from Tree class (now top-level, taking Tree instance) ---

// Compute the optimal bit lengths for a tree and update the total bit length
// for the current block.
// IN assertion: the fields freq and dad are set, heap[heap_max] and
//    above are the tree nodes sorted by increasing frequency.
// OUT assertions: the field len is set to the optimal bit length, the
//     array bl_count contains the frequencies for each bit length.
//     The length opt_len is updated; static_len is also updated if stree is
//     not null.
internal fun gen_bitlen(treeInstance: Tree, s: Deflate) {
    val tree = treeInstance.dyn_tree
    val stree = treeInstance.stat_desc.static_tree
    val extra = treeInstance.stat_desc.extra_bits
    val baseRenamed = treeInstance.stat_desc.extra_base
    val max_length = treeInstance.stat_desc.max_length
    var h: Int // heap index
    var n: Int
    var m: Int // iterate over the tree elements
    var bits: Int // bit length
    var xbits: Int // extra bits
    var f: Short // frequency
    var overflow = 0 // number of elements with bit length too large

    bits = 0
    while (bits <= MAX_BITS) {
        s.bl_count[bits] = 0
        bits++
    }

    // In a first pass, compute the optimal bit lengths (which may
    // overflow in the case of the bit length tree).
    tree[s.heap[s.heap_max] * 2 + 1] = 0 // root of the heap

    h = s.heap_max + 1
    while (h < HEAP_SIZE) {
        n = s.heap[h]
        bits = tree[tree[n * 2 + 1] * 2 + 1] + 1
        if (bits > max_length) {
            bits = max_length
            overflow++
        }
        tree[n * 2 + 1] = bits.toShort()
        // We overwrite tree[n*2+1] which is no longer needed

        if (n > treeInstance.max_code) continue // not a leaf node

        s.bl_count[bits]++
        xbits = 0
        if (n >= baseRenamed) xbits = extra!![n - baseRenamed] // extra can be null
        f = tree[n * 2]
        s.opt_len += (f * (bits + xbits)).toLong()
        if (stree != null) s.static_len += f * (stree[n * 2 + 1] + xbits).toLong()
        h++
    }
    if (overflow == 0) return

    // This happens for example on obj2 and pic of the Calgary corpus
    // Find the first bit length which could increase:
    do {
        bits = max_length - 1
        while (s.bl_count[bits] == 0) bits--
        s.bl_count[bits]-- // move one leaf down the tree
        s.bl_count[bits + 1] = (s.bl_count[bits + 1] + 2).toShort() // move one overflow item as its brother
        s.bl_count[max_length]--
        // The brother of the overflow item also moves one step up,
        // but this does not affect bl_count[max_length]
        overflow -= 2
    } while (overflow > 0)

    bits = max_length
    while (bits != 0) {
        n = s.bl_count[bits].toInt()
        while (n != 0) {
            m = s.heap[--h]
            if (m > treeInstance.max_code) continue
            if (tree[m * 2 + 1].toInt() != bits) {
                s.opt_len =
                    (s.opt_len + (bits.toLong() - tree[m * 2 + 1].toLong()) * tree[m * 2].toLong()).toInt()
                tree[m * 2 + 1] = bits.toShort()
            }
            n--
        }
        bits--
    }
}

// Construct one Huffman tree and assigns the code bit strings and lengths.
// Update the total bit length for the current block.
// IN assertion: the field freq is set for all tree elements.
// OUT assertions: the fields len and code are set to the optimal bit length
//     and corresponding code. The length opt_len is updated; static_len is
//     also updated if stree is not null. The field max_code is set.
internal fun build_tree(treeInstance: Tree, s: Deflate) {
    val tree = treeInstance.dyn_tree
    val stree = treeInstance.stat_desc.static_tree
    val elems = treeInstance.stat_desc.elems
    var n: Int
    var m: Int // iterate over heap elements
    var max_code = -1 // largest code with non zero frequency
    var node: Int // new node being created

    // Construct the initial heap, with least frequent element in
    // heap[1]. The sons of heap[n] are heap[2*n] and heap[2*n+1].
    // heap[0] is not used.
    s.heap_len = 0
    s.heap_max = HEAP_SIZE

    n = 0
    while (n < elems) {
        if (tree[n * 2].toInt() != 0) {
            s.heap[++s.heap_len] = n
            max_code = n
            s.depth[n] = 0.toByte()
        } else {
            tree[n * 2 + 1] = 0
        }
        n++
    }

    // The pkzip format requires that at least one distance code exists,
    // and that at least one bit should be sent even if there is only one
    // possible code. So to avoid special checks later on we force at least
    // two codes of non zero frequency.
    while (s.heap_len < 2) {
        node = if (max_code < 2) ++max_code else 0
        s.heap[++s.heap_len] = node
        tree[node * 2] = 1
        s.depth[node] = 0.toByte()
        s.opt_len--
        if (stree != null) s.static_len -= stree[node * 2 + 1].toLong()
        // node is 0 or 1 so it does not have extra bits
    }
    treeInstance.max_code = max_code

    // The elements heap[heap_len/2+1 .. heap_len] are leaves of the tree,
    // establish sub-heaps of increasing lengths:

    n = s.heap_len / 2
    while (n >= 1) {
        s.pqdownheap(tree, n)
        n--
    }

    // Construct the Huffman tree by repeatedly combining the least two
    // frequent nodes.

    node = elems // next internal node of the tree
    do {
        // n = node of least frequency
        n = s.heap[1]
        s.heap[1] = s.heap[s.heap_len--]
        s.pqdownheap(tree, 1)
        m = s.heap[1] // m = node of next least frequency

        s.heap[--s.heap_max] = n // keep the nodes sorted by frequency
        s.heap[--s.heap_max] = m

        // Create a new node father of n and m
        tree[node * 2] = (tree[n * 2] + tree[m * 2]).toShort()
        s.depth[node] = (maxOf(s.depth[n], s.depth[m]) + 1).toByte()
        tree[n * 2 + 1] = node.toShort()
        tree[m * 2 + 1] = node.toShort()

        // and insert the new node in the heap
        s.heap[1] = node++
        s.pqdownheap(tree, 1)
    } while (s.heap_len >= 2)

    s.heap[--s.heap_max] = s.heap[1]

    // At this point, the fields freq and dad are set. We can now
    // generate the bit lengths.

    gen_bitlen(treeInstance, s)

    // The field len is now set, we can generate the bit codes
    gen_codes(tree, max_code, s.bl_count)
}
