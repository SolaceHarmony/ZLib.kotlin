// Copyright (c) 2006, ComponentAce
// http://www.componentace.com
// All rights reserved.

// Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:

// Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
// Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.
// Neither the name of ComponentAce nor the names of its contributors may be used to endorse or promote products derived from this software without specific prior written permission.
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

/*
Copyright (c) 2000,2001,2002,2003 ymnk, JCraft,Inc. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice,
this list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in
the documentation and/or other materials provided with the distribution.

3. The names of the authors may not be used to endorse or promote products
derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED WARRANTIES,
INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL JCRAFT,
INC. OR ANY CONTRIBUTORS TO THIS SOFTWARE BE LIABLE FOR ANY DIRECT, INDIRECT,
INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA,
OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,
EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/
/*
* This program is based on zlib-1.1.3, so all credit should go authors
* Jean-loup Gailly(jloup@gzip.org) and Mark Adler(madler@alumni.caltech.edu)
* and contributors of zlib.
*/
package ai.solace.zlib.deflate

import ai.solace.zlib.common.*

internal object InfTree {

    private const val Z_OK = 0
    private const val Z_BUF_ERROR = -5
    private const val Z_DATA_ERROR = -3
    private const val Z_MEM_ERROR = -4

    private val fixed_bl = intArrayOf(9)
    private val fixed_bd = intArrayOf(5)
    private val fixed_tl = arrayOf(IntArray(0))
    private val fixed_td = arrayOf(IntArray(0))
    private var fixed_built = false

    private fun huft_build(
        b: IntArray,
        bIndex: Int,
        n: Int,
        s: Int,
        d: IntArray?,
        e: IntArray?,
        t: Array<IntArray>,
        m: IntArray,
        hp: IntArray,
        hn: IntArray,
        v: IntArray
    ): Int {
        var a: Int // counter for codes of length k
        val c = IntArray(MAX_BITS + 1) // bit length count table
        var el: Int // length of EOB code (value 256)
        var f: Int // i repeats in table every f entries
        var g: Int // maximum code length
        var h: Int // table level
        var i: Int // counter, current code
        var j: Int // counter
        var k: Int // number of bits in current code
        var l: Int // bits per table (returned in m)
        var mask: Int // (1 << w) - 1, to avoid cc -1
        var p: Int // pointer into c[], b[], or v[]
        var q: Int // points to current table
        val r = IntArray(1) // table entry for structure assignment
        // Ensure u is large enough for all possible values of h
        // h starts at -1 and can go up to MAX_BITS, so we need at least MAX_BITS + 1 elements
        val u = IntArray(MAX_BITS + 1) // table stack
        var w: Int // bits before this table == (l * h)
        val x = IntArray(1) // bit length of current code
        var y: Int // number of dummy codes added
        var z: Int // number of entries in current table

        // Generate counts for each bit length
        el = if (n > 256) b[bIndex + 256] else MAX_BITS
        p = bIndex
        i = n
        do {
            c[b[p++]]++
        } while (--i > 0)

        if (c[0] == n) {
            t[0] = IntArray(0)
            m[0] = 0
            return Z_OK
        }

        // Find minimum and maximum length, bound *m by those
        l = m[0]
        j = 1
        while (j <= MAX_BITS && c[j] == 0) {
            j++
        }
        k = j
        if (l < j) {
            l = j
        }
        i = MAX_BITS
        while (i != 0 && c[i] == 0) {
            i--
        }
        g = i
        if (l > i) {
            l = i
        }
        m[0] = l

        // Adjust last length count to fill out codes, if needed
        y = 1 shl j
        while (j < i) {
            y -= c[j]
            if (y < 0) {
                return Z_DATA_ERROR
            }
            j++
            y = y shl 1
        }
        y -= c[i]
        if (y < 0) {
            return Z_DATA_ERROR
        }
        c[i] += y

        // Generate starting codes for each bit length
        x[0] = 0
        p = 0
        j = 1
        while (j < i) {
            x[0] += c[j]
            v[p++] = x[0]
            j++
        }

        // Make a table of values in order of bit lengths
        p = 0
        i = 0
        while (i < n) {
            j = b[bIndex + i]
            if (j != 0) {
                v[c[j]++] = i
            }
            i++
        }

        // Generate the Huffman codes and for each, make the table entries
        x[0] = 0
        i = 0 // current literal/length code
        p = 0 // pointer to current code length
        h = -1 // current table number
        w = 0 // bits before this table
        u[0] = 0 // table stack
        q = 0 // table base pointer
        z = 0 // number of entries in current table

        // go through the bit lengths (k already is bits in shortest code)
        while (k <= g) {
            a = c[k]
            while (a-- > 0) {
                // here i is the code
                while (k > w + l) {
                    h++
                    w += l // add bits already decoded

                    // compute minimum size table less than or equal to l bits
                    z = g - w
                    z = if (z > l) l else z
                    j = k - w
                    f = 1 shl j
                    if (f > a + 1) { // try a k-w bit table
                        f -= a + 1
                        p = k
                        while (++j < z) {
                            f = f shl 1
                            if (f <= c[++p]) {
                                break
                            }
                            f -= c[p]
                        }
                    }
                    z = 1 shl j
                    if (hn[0] + z > IBLK_MANY) {
                        return Z_DATA_ERROR
                    }
                    u[h] = q
                    hn[0] += z

                    // Create new table
                    // Create a new array with the correct size and copy elements from t[0]
                    val newSize = t[0].size + z * 3
                    val newArray = IntArray(newSize)
                    t[0].copyInto(newArray, 0, 0, t[0].size)
                    t[0] = newArray
                    r[0] = q
                    // connect to last table, if there is one
                    if (h != 0) {
                        x[0] = u[h - 1] + (x[0] ushr w)
                        // set up table entry in parent
                        r[0] = (l shl 24) or (j shl 16) or x[0]
                        t[0][(u[h-1] + (x[0] and ((1 shl w) -1)))*3] = r[0]
                    }
                }

                // set up table entry in current table
                r[0] = (k - w shl 24) or (i shl 16)
                j = 0
                while (j < k - w) {
                    r[0] = r[0] or (1 shl (w + j))
                    j++
                }
                t[0][(q + (x[0] and ((1 shl (w + l)) - 1)))*3] = r[0]

                // calculate next code
                j = 1 shl (k - 1)
                while (x[0] and j != 0) {
                    x[0] = x[0] xor j
                    j = j ushr 1
                }
                x[0] = x[0] xor j

                // create mask for next code
                mask = (1 shl (k - 1)) - 1
                while (x[0] and mask != c[k - 1]) {
                    c[k - 1]++
                    x[0] = (x[0] + 1) and mask
                }
            }
            k++
        }

        // Return Z_OK if we used all codes
        return if (y == 0 || g == 1) Z_OK else Z_DATA_ERROR
    }

    internal fun inflate_trees_bits(
        c: IntArray,
        bb: IntArray,
        tb: Array<IntArray>,
        hp: IntArray,
        z: ZStream
    ): Int {
        var result: Int
        val hn = intArrayOf(0)
        val v = IntArray(19)
        result = huft_build(c, 0, 19, 19, null, null, tb, bb, hp, hn, v)
        if (result == Z_DATA_ERROR) {
            z.msg = "oversubscribed dynamic bit lengths tree"
        } else if (result == Z_BUF_ERROR || bb[0] == 0) {
            z.msg = "incomplete dynamic bit lengths tree"
            result = Z_DATA_ERROR
        }
        return result
    }

    internal fun inflate_trees_dynamic(
        nl: Int,
        nd: Int,
        c: IntArray,
        bl: IntArray,
        bd: IntArray,
        tl: Array<IntArray>,
        td: Array<IntArray>,
        hp: IntArray,
        z: ZStream
    ): Int {
        var result: Int
        val hn = intArrayOf(0)
        val v = IntArray(288)

        // build literal/length tree
        result = huft_build(c, 0, nl, L_CODES, TREE_EXTRA_LBITS, TREE_BASE_LENGTH, tl, bl, hp, hn, v)
        if (result != Z_OK || bl[0] == 0) {
            if (result == Z_DATA_ERROR) {
                z.msg = "oversubscribed literal/length tree"
            } else if (result != Z_MEM_ERROR) {
                z.msg = "incomplete literal/length tree"
                result = Z_DATA_ERROR
            }
            return result
        }

        // build distance tree
        result = huft_build(c, nl, nd, 0, TREE_EXTRA_DBITS, TREE_BASE_DIST, td, bd, hp, hn, v)
        if (result != Z_OK || (bd[0] == 0 && nl > 257)) {
            if (result == Z_DATA_ERROR) {
                z.msg = "oversubscribed distance tree"
            } else if (result == Z_BUF_ERROR) {
                z.msg = "incomplete distance tree"
                result = Z_DATA_ERROR
            } else if (result != Z_MEM_ERROR) {
                z.msg = "empty distance tree with lengths"
                result = Z_DATA_ERROR
            }
            return result
        }
        return Z_OK
    }

    internal fun inflate_trees_fixed(
        bl: IntArray,
        bd: IntArray,
        tl: Array<IntArray>,
        td: Array<IntArray>,
        z: ZStream
    ) {
        if (!fixed_built) {
            val l = IntArray(288)
            var i = 0
            while (i < 144) l[i] = 8
            while (i < 256) l[i] = 9
            while (i < 280) l[i] = 7
            while (i < 288) l[i] = 8
            fixed_bl[0] = 9

            var hn = intArrayOf(0)
            val v = IntArray(288)
            val hp = IntArray(0)

            var result = huft_build(l, 0, 288, L_CODES, TREE_EXTRA_LBITS, TREE_BASE_LENGTH, fixed_tl, fixed_bl, hp, hn, v)
            if (result != Z_OK) {
                z.msg = "fixed length tree"
                // handle error
            }

            i = 0
            while (i < 30) l[i] = 5
            fixed_bd[0] = 5
            result = huft_build(l, 0, 30, 0, TREE_EXTRA_DBITS, TREE_BASE_DIST, fixed_td, fixed_bd, hp, hn, v)
            if (result != Z_OK) {
                z.msg = "fixed distance tree"
                // handle error
            }
            fixed_built = true
        }
        tl[0] = fixed_tl[0]
        td[0] = fixed_td[0]
        bl[0] = fixed_bl[0]
        bd[0] = fixed_bd[0]
    }
}
