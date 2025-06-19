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
package componentace.compression.libs.zlib.deflate

import ai.solace.zlib.common.* // Import all constants

internal class InfCodes {

    private var mode: Int = 0 // current inflate_codes mode

    // mode dependent information
    private var len: Int = 0

    private lateinit var tree: IntArray // pointer into tree
    private var treeIndex: Int = 0
    private var need: Int = 0 // bits needed

    private var lit: Int = 0

    // if EXT or COPY, where and how much
    private var get_Renamed: Int = 0 // bits to get for extra
    private var dist: Int = 0 // distance back to copy from

    private var lbits: Byte = 0 // ltree bits decoded per branch
    private var dbits: Byte = 0 // dtree bits decoder per branch
    private lateinit var ltree: IntArray // literal/length/eob tree
    private var ltreeIndex: Int = 0 // literal/length/eob tree
    private lateinit var dtree: IntArray // distance tree
    private var dtreeIndex: Int = 0 // distance tree

    constructor(bl: Int, bd: Int, tl: IntArray, tlIndex: Int, td: IntArray, tdIndex: Int, z: ZStream) {
        mode = ICODES_START
        lbits = bl.toByte()
        dbits = bd.toByte()
        ltree = tl
        ltreeIndex = tlIndex
        dtree = td
        dtreeIndex = tdIndex
    }

    constructor(bl: Int, bd: Int, tl: IntArray, td: IntArray, z: ZStream) {
        mode = ICODES_START
        lbits = bl.toByte()
        dbits = bd.toByte()
        ltree = tl
        ltreeIndex = 0
        dtree = td
        dtreeIndex = 0
    }

    internal fun proc(s: InfBlocks, z: ZStream, r: Int): Int {
        var result = r
        var j: Int // temporary storage
        var tindex: Int // temporary pointer
        var e: Int // extra bits or operation
        var b = 0 // bit buffer
        var k = 0 // bits in bit buffer
        var p = 0 // input data pointer
        var n: Int // bytes available there
        var q: Int // output window write pointer
        var m: Int // bytes to end of window or read pointer
        var f: Int // pointer to copy strings from

        // copy input/output information to locals (UPDATE macro restores)
        p = z.next_in_index
        n = z.avail_in
        b = s.bitb
        k = s.bitk
        q = s.write
        m = if (q < s.read) s.read - q - 1 else s.end - q

        // process input and output based on current state
        while (true) {
            when (mode) {

                // waiting for "i:"=input, "o:"=output, "x:"=nothing
                ICODES_START -> { // x: set up for LEN
                    if (m >= 258 && n >= 10) {

                        s.bitb = b
                        s.bitk = k
                        z.avail_in = n
                        z.total_in += p - z.next_in_index
                        z.next_in_index = p
                        s.write = q
                        result = inflate_fast(lbits.toInt(), dbits.toInt(), ltree, ltreeIndex, dtree, dtreeIndex, s, z)

                        p = z.next_in_index
                        n = z.avail_in
                        b = s.bitb
                        k = s.bitk
                        q = s.write
                        m = if (q < s.read) s.read - q - 1 else s.end - q

                        if (result != Z_OK) {
                            mode = if (result == Z_STREAM_END) ICODES_WASH else ICODES_BADCODE
                            break
                        }
                    }
                    need = lbits.toInt()
                    tree = ltree
                    treeIndex = ltreeIndex

                    mode = ICODES_LEN
                    continue
                }

                ICODES_LEN -> {  // i: get length/literal/eob next
                    j = need

                    while (k < j) {
                        if (n != 0) result = Z_OK
                        else {

                            s.bitb = b
                            s.bitk = k
                            z.avail_in = n
                            z.total_in += p - z.next_in_index
                            z.next_in_index = p
                            s.write = q
                            return s.inflate_flush(z, result)
                        }
                        n--
                        b = b or ((z.next_in[p++].toInt() and 0xff) shl k)
                        k += 8
                    }

                    tindex = (treeIndex + (b and IBLK_INFLATE_MASK[j])) * 3

                    b = b ushr tree[tindex + 1]
                    k -= tree[tindex + 1]

                    e = tree[tindex]

                    if (e == 0) {
                        // literal
                        lit = tree[tindex + 2]
                        mode = ICODES_LIT
                        break
                    }
                    if (e and 16 != 0) {
                        // length
                        get_Renamed = e and 15
                        len = tree[tindex + 2]
                        mode = ICODES_LENEXT
                        break
                    }
                    if (e and 64 == 0) {
                        // next table
                        need = e
                        treeIndex = tindex / 3 + tree[tindex + 2]
                        break
                    }
                    if (e and 32 != 0) {
                        // end of block
                        mode = ICODES_WASH
                        break
                    }
                    mode = ICODES_BADCODE // invalid code
                    z.msg = "invalid literal/length code"
                    result = Z_DATA_ERROR

                    s.bitb = b
                    s.bitk = k
                    z.avail_in = n
                    z.total_in += p - z.next_in_index
                    z.next_in_index = p
                    s.write = q
                    return s.inflate_flush(z, result)
                }

                ICODES_LENEXT -> {  // i: getting length extra (have base)
                    j = get_Renamed

                    while (k < j) {
                        if (n != 0) result = Z_OK
                        else {

                            s.bitb = b
                            s.bitk = k
                            z.avail_in = n
                            z.total_in += p - z.next_in_index
                            z.next_in_index = p
                            s.write = q
                            return s.inflate_flush(z, result)
                        }
                        n--
                        b = b or ((z.next_in[p++].toInt() and 0xff) shl k)
                        k += 8
                    }

                    len += b and IBLK_INFLATE_MASK[j]

                    b = b ushr j
                    k -= j

                    need = dbits.toInt()
                    tree = dtree
                    treeIndex = dtreeIndex
                    mode = ICODES_DIST
                    continue
                }

                ICODES_DIST -> {  // i: get distance next
                    j = need

                    while (k < j) {
                        if (n != 0) result = Z_OK
                        else {

                            s.bitb = b
                            s.bitk = k
                            z.avail_in = n
                            z.total_in += p - z.next_in_index
                            z.next_in_index = p
                            s.write = q
                            return s.inflate_flush(z, result)
                        }
                        n--
                        b = b or ((z.next_in[p++].toInt() and 0xff) shl k)
                        k += 8
                    }

                    tindex = (treeIndex + (b and IBLK_INFLATE_MASK[j])) * 3

                    b = b ushr tree[tindex + 1]
                    k -= tree[tindex + 1]

                    e = tree[tindex]
                    if (e and 16 != 0) {
                        // distance
                        get_Renamed = e and 15
                        dist = tree[tindex + 2]
                        mode = ICODES_DISTEXT
                        break
                    }
                    if (e and 64 == 0) {
                        // next table
                        need = e
                        treeIndex = tindex / 3 + tree[tindex + 2]
                        break
                    }
                    mode = ICODES_BADCODE // invalid code
                    z.msg = "invalid distance code"
                    result = Z_DATA_ERROR

                    s.bitb = b
                    s.bitk = k
                    z.avail_in = n
                    z.total_in += p - z.next_in_index
                    z.next_in_index = p
                    s.write = q
                    return s.inflate_flush(z, result)
                }

                ICODES_DISTEXT -> {  // i: getting distance extra
                    j = get_Renamed

                    while (k < j) {
                        if (n != 0) result = Z_OK
                        else {

                            s.bitb = b
                            s.bitk = k
                            z.avail_in = n
                            z.total_in += p - z.next_in_index
                            z.next_in_index = p
                            s.write = q
                            return s.inflate_flush(z, result)
                        }
                        n--
                        b = b or ((z.next_in[p++].toInt() and 0xff) shl k)
                        k += 8
                    }

                    dist += b and IBLK_INFLATE_MASK[j]

                    b = b ushr j
                    k -= j

                    mode = ICODES_COPY
                    continue
                }

                ICODES_COPY -> {  // o: copying bytes in window, waiting for space
                    f = q - dist
                    while (f < 0) {
                        // modulo window size-"while" instead
                        f += s.end // of "if" handles invalid distances
                    }
                    while (len != 0) {

                        if (m == 0) {
                            if (q == s.end && s.read != 0) {
                                q = 0
                                m = if (q < s.read) s.read - q - 1 else s.end - q
                            }
                            if (m == 0) {
                                s.write = q
                                result = s.inflate_flush(z, result)
                                q = s.write
                                m = if (q < s.read) s.read - q - 1 else s.end - q

                                if (q == s.end && s.read != 0) {
                                    q = 0
                                    m = if (q < s.read) s.read - q - 1 else s.end - q
                                }

                                if (m == 0) {
                                    s.bitb = b
                                    s.bitk = k
                                    z.avail_in = n
                                    z.total_in += p - z.next_in_index
                                    z.next_in_index = p
                                    s.write = q
                                    return s.inflate_flush(z, result)
                                }
                            }
                        }

                        s.window[q++] = s.window[f++]
                        m--

                        if (f == s.end) f = 0
                        len--
                    }
                    mode = ICODES_START
                }

                ICODES_LIT -> {  // o: got literal, waiting for output space
                    if (m == 0) {
                        if (q == s.end && s.read != 0) {
                            q = 0
                            m = if (q < s.read) s.read - q - 1 else s.end - q
                        }
                        if (m == 0) {
                            s.write = q
                            result = s.inflate_flush(z, result)
                            q = s.write
                            m = if (q < s.read) s.read - q - 1 else s.end - q

                            if (q == s.end && s.read != 0) {
                                q = 0
                                m = if (q < s.read) s.read - q - 1 else s.end - q
                            }
                            if (m == 0) {
                                s.bitb = b
                                s.bitk = k
                                z.avail_in = n
                                z.total_in += p - z.next_in_index
                                z.next_in_index = p
                                s.write = q
                                return s.inflate_flush(z, result)
                            }
                        }
                    }
                    result = Z_OK

                    s.window[q++] = lit.toByte()
                    m--

                    mode = ICODES_START
                }

                ICODES_WASH -> {  // o: got eob, possibly more output
                    if (k > 7) {
                        // return unused byte, if any
                        k -= 8
                        n++
                        p-- // can always return one
                    }

                    s.write = q
                    result = s.inflate_flush(z, result)
                    q = s.write
                    m = if (q < s.read) s.read - q - 1 else s.end - q

                    if (s.read != s.write) {
                        s.bitb = b
                        s.bitk = k
                        z.avail_in = n
                        z.total_in += p - z.next_in_index
                        z.next_in_index = p
                        s.write = q
                        return s.inflate_flush(z, result)
                    }
                    mode = ICODES_END
                    continue
                }

                ICODES_END -> {
                    result = Z_STREAM_END
                    s.bitb = b
                    s.bitk = k
                    z.avail_in = n
                    z.total_in += p - z.next_in_index
                    z.next_in_index = p
                    s.write = q
                    return s.inflate_flush(z, result)
                }

                ICODES_BADCODE -> {  // x: got error

                    result = Z_DATA_ERROR

                    s.bitb = b
                    s.bitk = k
                    z.avail_in = n
                    z.total_in += p - z.next_in_index
                    z.next_in_index = p
                    s.write = q
                    return s.inflate_flush(z, result)
                }

                else -> {
                    result = Z_STREAM_ERROR

                    s.bitb = b
                    s.bitk = k
                    z.avail_in = n
                    z.total_in += p - z.next_in_index
                    z.next_in_index = p
                    s.write = q
                    return s.inflate_flush(z, result)
                }
            }
        }
    }

    internal fun free(z: ZStream) {
        //  ZFREE(z, c);
    }

    // Called with number of bytes left to write in window at least 258
    // (the maximum string length) and number of input bytes available
    // at least ten. The ten bytes are six bytes for the longest length/
    // distance pair plus four bytes for overloading the bit buffer.

    internal fun inflate_fast(
        bl: Int,
        bd: Int,
        tl: IntArray,
        tlIndex: Int,
        td: IntArray,
        tdIndex: Int,
        s: InfBlocks,
        z: ZStream
    ): Int {
        var t: Int // temporary pointer
        var tp: IntArray // temporary pointer
        var tpIndex: Int // temporary pointer
        var e: Int // extra bits or operation
        var b: Int // bit buffer
        var k: Int // bits in bit buffer
        var p: Int // input data pointer
        var n: Int // bytes available there
        var q: Int // output window write pointer
        var m: Int // bytes to end of window or read pointer
        var ml: Int // mask for literal/length tree
        var md: Int // mask for distance tree
        var c: Int // bytes to copy
        var d: Int // distance back to copy from
        var r: Int // copy source pointer

        // load input, output, bit values
        p = z.next_in_index
        n = z.avail_in
        b = s.bitb
        k = s.bitk
        q = s.write
        m = if (q < s.read) s.read - q - 1 else s.end - q

        // initialize masks
        ml = IBLK_INFLATE_MASK[bl]
        md = IBLK_INFLATE_MASK[bd]

        // do until not enough input or output space for fast loop
        do {
            // assume called with m >= 258 && n >= 10
            // get literal/length code
            while (k < 20) {
                // max bits for literal/length code
                n--
                b = b or ((z.next_in[p++].toInt() and 0xff) shl k)
                k += 8
            }

            t = b and ml
            tp = tl
            tpIndex = tlIndex
            if (tp[(tpIndex + t) * 3] == 0) {
                b = b ushr tp[(tpIndex + t) * 3 + 1]
                k -= tp[(tpIndex + t) * 3 + 1]

                s.window[q++] = tp[(tpIndex + t) * 3 + 2].toByte()
                m--
                continue
            }

            do {

                b = b ushr tp[(tpIndex + t) * 3 + 1]
                k -= tp[(tpIndex + t) * 3 + 1]

                if (tp[(tpIndex + t) * 3] and 16 != 0) {
                    e = tp[(tpIndex + t) * 3] and 15
                    c = tp[(tpIndex + t) * 3 + 2] + (b and IBLK_INFLATE_MASK[e])

                    b = b ushr e
                    k -= e

                    // decode distance base of block to copy
                    while (k < 15) {
                        // max bits for distance code
                        n--
                        b = b or ((z.next_in[p++].toInt() and 0xff) shl k)
                        k += 8
                    }

                    t = b and md
                    tp = td
                    tpIndex = tdIndex
                    e = tp[(tpIndex + t) * 3]

                    do {

                        b = b ushr tp[(tpIndex + t) * 3 + 1]
                        k -= tp[(tpIndex + t) * 3 + 1]

                        if ((e and 16) != 0) {
                            // get extra bits to add to distance base
                            e = e and 15
                            while (k < e) {
                                // get extra bits (up to 13)
                                n--
                                b = b or ((z.next_in[p++].toInt() and 0xff) shl k)
                                k += 8
                            }

                            d = tp[(tpIndex + t) * 3 + 2] + (b and IBLK_INFLATE_MASK[e])

                            b = b ushr e
                            k -= e

                            // do the copy
                            m -= c
                            if (q >= d) {
                                // offset before dest
                                //  just copy
                                r = q - d
                                if (q - r > 0 && 2 > (q - r)) {
                                    s.window[q++] = s.window[r++]
                                    c-- // minimum count is three,
                                    s.window[q++] = s.window[r++]
                                    c-- // so unroll loop a little
                                } else {
                                    System.arraycopy(s.window, r, s.window, q, 2)
                                    q += 2
                                    r += 2
                                    c -= 2
                                }
                            } else {
                                // else offset after destination
                                r = q - d
                                do {
                                    r += s.end // force pointer in window
                                } while (r < 0) // covers invalid distances
                                e = s.end - r
                                if (c > e) {
                                    // if source crosses,
                                    c -= e // wrapped copy
                                    if (q - r > 0 && e > (q - r)) {
                                        do {
                                            s.window[q++] = s.window[r++]
                                        } while (--e != 0)
                                    } else {
                                        System.arraycopy(s.window, r, s.window, q, e)
                                        q += e
                                        r += e
                                        e = 0
                                    }
                                    r = 0 // copy rest from start of window
                                }
                            }

                            // copy all or what's left
                            if (q - r > 0 && c > (q - r)) {
                                do {
                                    s.window[q++] = s.window[r++]
                                } while (--c != 0)
                            } else {
                                System.arraycopy(s.window, r, s.window, q, c)
                                q += c
                                r += c
                                c = 0
                            }
                            break
                        } else if ((e and 64) == 0) {
                            t += tp[(tpIndex + t) * 3 + 2]
                            t += (b and IBLK_INFLATE_MASK[e])
                            e = tp[(tpIndex + t) * 3]
                        } else {
                            z.msg = "invalid distance code"

                            c = z.avail_in - n
                            c = (k ushr 3).coerceAtMost(c)
                            n += c
                            p -= c
                            k -= c shl 3

                            s.bitb = b
                            s.bitk = k
                            z.avail_in = n
                            z.total_in += p - z.next_in_index
                            z.next_in_index = p
                            s.write = q

                            return Z_DATA_ERROR
                        }
                    } while (true)
                    break
                }

                if ((tp[(tpIndex + t) * 3] and 64) == 0) {
                    t += tp[(tpIndex + t) * 3 + 2]
                    t += (b and IBLK_INFLATE_MASK[tp[(tpIndex + t) * 3]])
                    if (tp[(tpIndex + t) * 3] == 0) {

                        b = b ushr tp[(tpIndex + t) * 3 + 1]
                        k -= tp[(tpIndex + t) * 3 + 1]

                        s.window[q++] = tp[(tpIndex + t) * 3 + 2].toByte()
                        m--
                        break
                    }
                } else if (tp[(tpIndex + t) * 3] and 32 != 0) {

                    c = z.avail_in - n
                    c = (k ushr 3).coerceAtMost(c)
                    n += c
                    p -= c
                    k -= c shl 3

                    s.bitb = b
                    s.bitk = k
                    z.avail_in = n
                    z.total_in += p - z.next_in_index
                    z.next_in_index = p
                    s.write = q

                    return Z_STREAM_END
                } else {
                    z.msg = "invalid literal/length code"

                    c = z.avail_in - n
                    c = (k ushr 3).coerceAtMost(c)
                    n += c
                    p -= c
                    k -= c shl 3

                    s.bitb = b
                    s.bitk = k
                    z.avail_in = n
                    z.total_in += p - z.next_in_index
                    z.next_in_index = p
                    s.write = q

                    return Z_DATA_ERROR
                }
            } while (true)
        } while (m >= 258 && n >= 10)

        // not enough input or output--restore pointers and return
        c = z.avail_in - n
        c = (k ushr 3).coerceAtMost(c)
        n += c
        p -= c
        k -= c shl 3

        s.bitb = b
        s.bitk = k
        z.avail_in = n
        z.total_in += p - z.next_in_index
        z.next_in_index = p
        s.write = q

        return Z_OK
    }

    companion object {
        // Constants previously defined here are now in ai.solace.zlib.common.Constants
        // inflate_mask is IBLK_INFLATE_MASK
        // Z_OK, Z_STREAM_END, etc. are already in common
        // Mode constants (START, LEN, etc.) are now ICODES_START, ICODES_LEN, etc. in Constants.kt
    }
}