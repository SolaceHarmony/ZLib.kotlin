package ai.solace.zlib.deflate

import ai.solace.zlib.common.*

// Utility functions for Tree operations

// Originally Tree.d_code
internal fun dCode(dist: Int): Int {
    return if ((dist) < 256) TREE_DIST_CODE[dist].toInt() else TREE_DIST_CODE[256 + (dist shr 7)].toInt()
}

// Originally Tree.gen_codes
internal fun genCodes(tree: ShortArray, maxCode: Int, blCount: ShortArray) {
    val nextCode = ShortArray(MAX_BITS + 1)
    var code: Short = 0
    var bits: Int
    var n: Int

    bits = 1
    while (bits <= MAX_BITS) {
        code = ((code + blCount[bits - 1]) shl 1).toShort()
        nextCode[bits] = code
        bits++
    }

    n = 0
    while (n <= maxCode) {
        val len = tree[n * 2 + 1].toInt()
        if (len != 0) {
            tree[n * 2] = biReverse(nextCode[len]++.toInt(), len).toShort()
        }
        n++
    }
}

// Originally Tree.bi_reverse
internal fun biReverse(codeIn: Int, len: Int): Int {
    var res = 0
    var code = codeIn
    var l = len
    do {
        res = res or (code and 1)
        code = code ushr 1
        res = res shl 1
    } while (--l > 0)
    return res ushr 1
}

// Originally Tree.gen_bitlen
internal fun genBitten(treeInstance: Tree, s: Deflate) {
    val tree = treeInstance.dynTree
    val stree = treeInstance.statDesc.staticTree
    val extra = treeInstance.statDesc.extraBits
    val baseRenamed = treeInstance.statDesc.extraBase
    val maxLength = treeInstance.statDesc.maxLength
    var h: Int
    var n: Int
    var m: Int
    var bits: Int
    var xbits: Int
    var f: Short
    var overflow = 0

    bits = 0
    while (bits <= MAX_BITS) {
        s.blCount[bits] = 0
        bits++
    }

    tree[s.heap[s.heapMax] * 2 + 1] = 0

    h = s.heapMax + 1
    while (h < HEAP_SIZE) {
        n = s.heap[h]
        bits = tree[tree[n * 2 + 1] * 2 + 1] + 1
        if (bits > maxLength) {
            bits = maxLength
            overflow++
        }
        tree[n * 2 + 1] = bits.toShort()

        if (n > treeInstance.maxCode) {
            h++
            continue
        }

        s.blCount[bits]++
        xbits = 0
        if (n >= baseRenamed) xbits = extra!![n - baseRenamed]
        f = tree[n * 2]
        s.optLen += (f * (bits + xbits)).toLong()
        if (stree != null) s.staticLen += (f * (stree[n * 2 + 1] + xbits)).toLong()
        h++
    }
    if (overflow == 0) return

    do {
        bits = maxLength - 1
        while (s.blCount[bits].toInt() == 0) bits-- // Added .toInt() for comparison
        s.blCount[bits]--
        s.blCount[bits + 1] = (s.blCount[bits + 1] + 2).toShort()
        s.blCount[maxLength]--
        overflow -= 2
    } while (overflow > 0)

    bits = maxLength
    while (bits != 0) {
        n = s.blCount[bits].toInt()
        while (n != 0) {
            m = s.heap[--h]
            if (m > treeInstance.maxCode) continue
            if (tree[m * 2 + 1].toInt() != bits) {
                s.optLen = (s.optLen + (bits.toLong() - tree[m * 2 + 1].toLong()) * tree[m * 2].toLong())
                tree[m * 2 + 1] = bits.toShort()
            }
            n--
        }
        bits--
    }
}

// Originally Tree.build_tree
internal fun buildTree(treeInstance: Tree, s: Deflate) {
    val tree = treeInstance.dynTree
    val stree = treeInstance.statDesc.staticTree
    val elems = treeInstance.statDesc.elems
    var n: Int
    var m: Int
    var maxCodeLocal = -1 // Renamed from max_code to avoid conflict with treeInstance.max_code
    var node: Int

    s.heapLen = 0
    s.heapMax = HEAP_SIZE

    n = 0
    while (n < elems) {
        if (tree[n * 2].toInt() != 0) {
            s.heap[++s.heapLen] = n
            maxCodeLocal = n
            s.depth[n] = 0.toByte()
        } else {
            tree[n * 2 + 1] = 0
        }
        n++
    }

    while (s.heapLen < 2) {
        node = if (maxCodeLocal < 2) ++maxCodeLocal else 0
        s.heap[++s.heapLen] = node
        tree[node * 2] = 1
        s.depth[node] = 0.toByte()
        s.optLen--
        if (stree != null) s.staticLen -= stree[node * 2 + 1].toLong()
    }
    treeInstance.maxCode = maxCodeLocal

    n = s.heapLen / 2
    while (n >= 1) {
        s.pqdownheap(tree, n)
        n--
    }

    node = elems
    do {
        n = s.heap[1]
        s.heap[1] = s.heap[s.heapLen--]
        s.pqdownheap(tree, 1)
        m = s.heap[1]

        s.heap[--s.heapMax] = n
        s.heap[--s.heapMax] = m

        tree[node * 2] = (tree[n * 2] + tree[m * 2]).toShort()
        s.depth[node] = (maxOf(s.depth[n], s.depth[m]) + 1).toByte()
        tree[n * 2 + 1] = node.toShort()
        tree[m * 2 + 1] = node.toShort()

        s.heap[1] = node++
        s.pqdownheap(tree, 1)
    } while (s.heapLen >= 2)

    s.heap[--s.heapMax] = s.heap[1]
    genBitten(treeInstance, s)
    genCodes(tree, treeInstance.maxCode, s.blCount)
}
