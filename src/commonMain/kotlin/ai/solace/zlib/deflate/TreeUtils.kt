package ai.solace.zlib.deflate

import ai.solace.zlib.common.*

// Utility functions for Tree operations

// Originally Tree.d_code
internal fun d_code(dist: Int): Int {
    return if ((dist) < 256) TREE_DIST_CODE[dist].toInt() else TREE_DIST_CODE[256 + (dist shr 7)].toInt()
}

// Originally Tree.gen_codes
internal fun gen_codes(tree: ShortArray, max_code: Int, bl_count: ShortArray) {
    val next_code = ShortArray(MAX_BITS + 1)
    var code: Short = 0
    var bits: Int
    var n: Int

    bits = 1
    while (bits <= MAX_BITS) {
        code = ((code + bl_count[bits - 1]) shl 1).toShort()
        next_code[bits] = code
        bits++
    }

    n = 0
    while (n <= max_code) {
        val len = tree[n * 2 + 1].toInt()
        if (len != 0) {
            tree[n * 2] = bi_reverse(next_code[len]++.toInt(), len).toShort()
        }
        n++
    }
}

// Originally Tree.bi_reverse
internal fun bi_reverse(code_in: Int, len: Int): Int {
    var res = 0
    var code = code_in
    var l = len
    do {
        res = res or (code and 1)
        code = code ushr 1
        res = res shl 1
    } while (--l > 0)
    return res ushr 1
}

// Originally Tree.gen_bitlen
internal fun gen_bitlen(treeInstance: Tree, s: Deflate) {
    val tree = treeInstance.dyn_tree
    val stree = treeInstance.stat_desc.static_tree
    val extra = treeInstance.stat_desc.extra_bits
    val baseRenamed = treeInstance.stat_desc.extra_base
    val max_length = treeInstance.stat_desc.max_length
    var h: Int
    var n: Int
    var m: Int
    var bits: Int
    var xbits: Int
    var f: Short
    var overflow = 0

    bits = 0
    while (bits <= MAX_BITS) {
        s.bl_count[bits] = 0
        bits++
    }

    tree[s.heap[s.heap_max] * 2 + 1] = 0

    h = s.heap_max + 1
    while (h < HEAP_SIZE) {
        n = s.heap[h]
        bits = tree[tree[n * 2 + 1] * 2 + 1] + 1
        if (bits > max_length) {
            bits = max_length
            overflow++
        }
        tree[n * 2 + 1] = bits.toShort()

        if (n > treeInstance.max_code) {
            h++
            continue
        }

        s.bl_count[bits]++
        xbits = 0
        if (n >= baseRenamed) xbits = extra!![n - baseRenamed]
        f = tree[n * 2]
        s.opt_len += (f * (bits + xbits)).toLong()
        if (stree != null) s.static_len += (f * (stree[n * 2 + 1] + xbits)).toLong()
        h++
    }
    if (overflow == 0) return

    do {
        bits = max_length - 1
        while (s.bl_count[bits].toInt() == 0) bits-- // Added .toInt() for comparison
        s.bl_count[bits]--
        s.bl_count[bits + 1] = (s.bl_count[bits + 1] + 2).toShort()
        s.bl_count[max_length]--
        overflow -= 2
    } while (overflow > 0)

    bits = max_length
    while (bits != 0) {
        n = s.bl_count[bits].toInt()
        while (n != 0) {
            m = s.heap[--h]
            if (m > treeInstance.max_code) continue
            if (tree[m * 2 + 1].toInt() != bits) {
                s.opt_len = (s.opt_len + (bits.toLong() - tree[m * 2 + 1].toLong()) * tree[m * 2].toLong())
                tree[m * 2 + 1] = bits.toShort()
            }
            n--
        }
        bits--
    }
}

// Originally Tree.build_tree
internal fun build_tree(treeInstance: Tree, s: Deflate) {
    val tree = treeInstance.dyn_tree
    val stree = treeInstance.stat_desc.static_tree
    val elems = treeInstance.stat_desc.elems
    var n: Int
    var m: Int
    var max_code_local = -1 // Renamed from max_code to avoid conflict with treeInstance.max_code
    var node: Int

    s.heap_len = 0
    s.heap_max = HEAP_SIZE

    n = 0
    while (n < elems) {
        if (tree[n * 2].toInt() != 0) {
            s.heap[++s.heap_len] = n
            max_code_local = n
            s.depth[n] = 0.toByte()
        } else {
            tree[n * 2 + 1] = 0
        }
        n++
    }

    while (s.heap_len < 2) {
        node = if (max_code_local < 2) ++max_code_local else 0
        s.heap[++s.heap_len] = node
        tree[node * 2] = 1
        s.depth[node] = 0.toByte()
        s.opt_len--
        if (stree != null) s.static_len -= stree[node * 2 + 1].toLong()
    }
    treeInstance.max_code = max_code_local

    n = s.heap_len / 2
    while (n >= 1) {
        s.pqdownheap(tree, n)
        n--
    }

    node = elems
    do {
        n = s.heap[1]
        s.heap[1] = s.heap[s.heap_len--]
        s.pqdownheap(tree, 1)
        m = s.heap[1]

        s.heap[--s.heap_max] = n
        s.heap[--s.heap_max] = m

        tree[node * 2] = (tree[n * 2] + tree[m * 2]).toShort()
        s.depth[node] = (maxOf(s.depth[n], s.depth[m]) + 1).toByte()
        tree[n * 2 + 1] = node.toShort()
        tree[m * 2 + 1] = node.toShort()

        s.heap[1] = node++
        s.pqdownheap(tree, 1)
    } while (s.heap_len >= 2)

    s.heap[--s.heap_max] = s.heap[1]
    gen_bitlen(treeInstance, s)
    gen_codes(tree, treeInstance.max_code, s.bl_count)
}
