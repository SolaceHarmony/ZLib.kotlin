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

import kotlin.collections.get
import kotlin.invoke

class InfBlocks(private val z: ZStream, private val checkfn: Any?, private val w: Int) {
    private val MANY = 1440

    // And'ing with mask[n] masks the lower n bits
    private val inflate_mask = intArrayOf(
        0x00000000,
        0x00000001,
        0x00000003,
        0x00000007,
        0x0000000f,
        0x0000001f,
        0x0000003f,
        0x0000007f,
        0x000000ff,
        0x000001ff,
        0x000003ff,
        0x000007ff,
        0x00000fff,
        0x00001fff,
        0x00003fff,
        0x00007fff,
        0x0000ffff
    )

    // Table for deflate from PKZIP's appnote.txt.
    internal val border = intArrayOf(16, 17, 18, 0, 8, 7, 9, 6, 10, 5, 11, 4, 12, 3, 13, 2, 14, 1, 15)

    private val Z_OK = 0
    private val Z_STREAM_END = 1
    private val Z_NEED_DICT = 2
    private val Z_ERRNO = -1
    private val Z_STREAM_ERROR = -2
    private val Z_DATA_ERROR = -3
    private val Z_MEM_ERROR = -4
    private val Z_BUF_ERROR = -5
    private val Z_VERSION_ERROR = -6

    private val TYPE = 0 // get type bits (3, including end bit)
    private val LENS = 1 // get lengths for stored
    private val STORED = 2 // processing stored block
    private val TABLE = 3 // get table lengths
    private val BTREE = 4 // get bit lengths tree for a dynamic block
    private val DTREE = 5 // get length, distance trees for a dynamic block
    private val CODES = 6 // processing fixed or dynamic block
    private val DRY = 7 // output remaining window bytes
    private val DONE = 8 // finished last block, done
    private val BAD = 9 // ot a data error--stuck here

    internal var mode = 0 // current inflate_block mode 

    internal var left = 0 // if STORED, bytes left to copy 

    internal var table = 0 // table lengths (14 bits)
    internal var index = 0 // index into blens (or border)
    internal var blens: IntArray? = null // bit lengths of codes
    internal var bb = IntArray(1) // bit length tree depth
    internal var tb = IntArray(1) // bit length decoding tree

    internal lateinit var codes: InfCodes // if CODES, current state

    internal var last = 0 // true if this block is the last block

    // mode independent information
    internal var bitk = 0 // bits in bit buffer
    internal var bitb = 0 // bit buffer
    internal var hufts: IntArray // single malloc for tree space
    internal var window: ByteArray // sliding window
    internal var end = 0 // one byte after sliding window
    internal var read = 0 // window read pointer
    internal var write = 0 // window write pointer
    internal var check: Long = 0 // check on output

    init {
        hufts = IntArray(MANY * 3)
        window = ByteArray(w)
        end = w
        mode = TYPE
        reset(z, null)
    }

    internal fun reset(z: ZStream, c: LongArray?) {
        if (c != null) c[0] = check
        if (mode == BTREE || mode == DTREE) {
            blens = null
        }
        if (mode == CODES) {
            codes.free(z)
        }
        mode = TYPE
        bitk = 0
        bitb = 0
        read = 0
        write = 0

        if (checkfn != null) z.adler = check = z._adler.adler32(0L, null, 0, 0)
    }

    internal fun proc(z: ZStream, r: Int): Int {
        var r = r
        var t: Int // temporary storage
        var b: Int // bit buffer
        var k: Int // bits in bit buffer
        var p: Int // input data pointer
        var n: Int // bytes available there
        var q: Int // output window write pointer
        var m: Int // bytes to end of window or read pointer

        // copy input/output information to locals (UPDATE macro restores)
        run {
            p = z.next_in_index
            n = z.avail_in
            b = bitb
            k = bitk
        }
        run {
            q = write
            m = if (q < read) read - q - 1 else end - q
        }

        // process input based on current state
        while (true) {
            when (mode) {
                TYPE -> {
                    while (k < 3) {
                        if (n != 0) {
                            r = Z_OK
                        } else {
                            bitb = b
                            bitk = k
                            z.avail_in = n
                            z.total_in += p - z.next_in_index
                            z.next_in_index = p
                            write = q
                            return inflate_flush(z, r)
                        }
                        n--
                        b = b or ((z.next_in[p++].toInt() and 0xff) shl k)
                        k += 8
                    }
                    t = b and 7
                    last = t and 1

                    when (t.ushr(1)) {
                        0 -> { // stored
                            b = b.ushr(3)
                            k -= 3
                        }

                        t = k and 7 // go to byte boundary
                                run {
                            b = b.ushr(t)
                            k -= t
                        }
                                mode = LENS // get length of stored block
                    }
                    1 -> { // fixed
                        val bl = IntArray(1)
                        val bd = IntArray(1)
                        val tl = Array(1) { IntArray(0) }
                        val td = Array(1) { IntArray(0) }

                        InfTree.inflate_trees_fixed(bl, bd, tl, td, z)
                        codes = InfCodes(bl[0], bd[0], tl[0], td[0], z)

                        run {
                            b = b.ushr(3)
                            k -= 3
                        }

                        mode = CODES
                    }
                    2 -> { // dynamic
                        run {
                            b = b.ushr(3)
                            k -= 3
                        }

                        mode = TABLE
                    }
                    3 -> { // illegal
                        run {
                            b = b.ushr(3)
                            k -= 3
                        }
                        mode = BAD
                        z.msg = "invalid block type"
                        r = Z_DATA_ERROR

                        bitb = b
                        bitk = k
                        z.avail_in = n
                        z.total_in += p - z.next_in_index
                        z.next_in_index = p
                        write = q
                        return inflate_flush(z, r)
                    }
                }

                LENS -> while (k < 32) {
                    if (n != 0) {
                        r = Z_OK
                    } else {
                        bitb = b
                        bitk = k
                        z.avail_in = n
                        z.total_in += p - z.next_in_index
                        z.next_in_index = p
                        write = q
                        return inflate_flush(z, r)
                    }
                    n--
                    b = b or ((z.next_in[p++].toInt() and 0xff) shl k)
                    k += 8
                }

                if ((~b ushr 16 and 0xffff.toInt()
                    )
                    != (b and 0xffff.toInt())
                    ) {
                    mode = BAD
                    z.msg = "invalid stored block lengths"
                    r = Z_DATA_ERROR

                    bitb = b
                    bitk = k
                    z.avail_in = n
                    z.total_in += p - z.next_in_index
                    z.next_in_index = p
                    write = q
                    return inflate_flush(z, r)
                }

                left = b and 0xffff.toInt()
                        b = 0
                    k = b // dump bits
                mode = if (left != 0) STORED else if (last != 0) DRY else TYPE
                        STORED -> if (n == 0) {
                    bitb = b
                    bitk = k
                    z.avail_in = n
                    z.total_in += p - z.next_in_index
                    z.next_in_index = p
                    write = q
                    return inflate_flush(z, r)
                }

                if (m == 0) {
                    if (q == end && read != 0) {
                        q = 0
                        m = if (q < read) read - q - 1 else end - q
                    }
                    if (m == 0) {
                        write = q
                        r = inflate_flush(z, r)
                        q = write
                        m = if (q < read) read - q - 1 else end - q
                        if (q == end && read != 0) {
                            q = 0
                            m = if (q < read) read - q - 1 else end - q
                        }
                        if (m == 0) {
                            bitb = b
                            bitk = k
                            z.avail_in = n
                            z.total_in += p - z.next_in_index
                            z.next_in_index = p
                            write = q
                            return inflate_flush(z, r)
                        }
                    }
                }
                        r = Z_OK

                        t = left
                    if (t > n) t = n
                            if (t > m) t = m
                    z

                    .next_in.copyInto(window, q, p, p + t)
                p += t
                        n -= t
                        q += t
                        m -= t
                        if ((left -= t) != 0) break
                                mode = if (last != 0) DRY else TYPE
                        TABLE -> while (k < 14) {
                    if (n != 0) {
                        r = Z_OK
                    } else {
                        bitb = b
                        bitk = k
                        z.avail_in = n
                        z.total_in += p - z.next_in_index
                        z.next_in_index = p
                        write = q
                        return inflate_flush(z, r)
                    }
                    n--
                    b = b or ((z.next_in[p++].toInt() and 0xff) shl k)
                    k += 8
                }

                table = t = b and 0x3fff
                    if ((t and 0x1f) > 29 || ((t shr 5) and 0x1f) > 29) {
                        mode = BAD
                        z.msg = "too many length or distance symbols"
                        r = Z_DATA_ERROR

                        bitb = b
                        bitk = k
                        z.avail_in = n
                        z.total_in += p - z.next_in_index
                        z.next_in_index = p
                        write = q
                        return inflate_flush(z, r)
                    }
                    t

                    = 258 + (t and 0x1f) + (t shr 5 and 0x1f)
                blens = IntArray(t)

                        run {
                    b = b.ushr(14)
                    k -= 14
                }

                        index = 0
                    mode = BTREE

                BTREE -> {
                    while (index < 4 + (table ushr 10)) {
                        while (k < 3) {
                            if (n != 0) {
                                r = Z_OK
                            } else {
                                bitb = b
                                bitk = k
                                z.avail_in = n
                                z.total_in += p - z.next_in_index
                                z.next_in_index = p
                                write = q
                                return inflate_flush(z, r)
                            }
                            n--
                            b = b or ((z.next_in[p++].toInt() and 0xff) shl k)
                            k += 8
                        }

                        blens!![border[index++]] = b and 7

                        run {
                            b = b.ushr(3)
                            k -= 3
                        }
                    }

                    while (index < 19) {
                        blens!![border[index++]] = 0
                    }

                    bb[0] = 7
                    t = InfTree.inflate_trees_bits(blens!!, bb, tb, hufts, z)
                    if (t != Z_OK) {
                        r = t
                        if (r == Z_DATA_ERROR) {
                            blens = null
                            mode = BAD
                        }

                        bitb = b
                        bitk = k
                        z.avail_in = n
                        z.total_in += p - z.next_in_index
                        z.next_in_index = p
                        write = q
                        return inflate_flush(z, r)
                    }

                    index = 0
                    mode = DTREE
                    DTREE -> while (true) {
                        t = table
                        if (!(index < 258 + (t and 0x1f) + (t shr 5 and 0x1f))) {
                            break
                        }

                        var i: Int
                        var j: Int
                        var c: Int

                        t = bb[0]

                        while (k < t) {
                            if (n != 0) {
                                r = Z_OK
                            } else {
                                bitb = b
                                bitk = k
                                z.avail_in = n
                                z.total_in += p - z.next_in_index
                                z.next_in_index = p
                                write = q
                                return inflate_flush(z, r)
                            }
                            n--
                            b = b or ((z.next_in[p++].toInt() and 0xff) shl k)
                            k += 8
                        }

                        t = hufts[(tb[0] + (b and inflate_mask[t])) * 3 + 1]
                        c = hufts[(tb[0] + (b and inflate_mask[t])) * 3 + 2]

                        if (c < 16) {
                            b = b.ushr(t)
                            k -= t
                            blens!![index++] = c
                        } else {
                            // c == 16..18
                            i = if (c == 18) 7 else c - 14
                            j = if (c == 18) 11 else 3

                            while (k < t + i) {
                                if (n != 0) {
                                    r = Z_OK
                                } else {
                                    bitb = b
                                    bitk = k
                                    z.avail_in = n
                                    z.total_in += p - z.next_in_index
                                    z.next_in_index = p
                                    write = q
                                    return inflate_flush(z, r)
                                }
                                n--
                                b = b or ((z.next_in[p++].toInt() and 0xff) shl k)
                                k += 8
                            }

                            b = b.ushr(t)
                            k -= t

                            j += (b and inflate_mask[i])

                            b = b.ushr(i)
                            k -= i

                            i = index
                            t = table
                            if (i + j > 258 + (t and 0x1f) + (t shr 5 and 0x1f) || c == 16 && i < 1) {
                                blens = null
                                mode = BAD
                                z.msg = "invalid bit length repeat"
                                r = Z_DATA_ERROR

                                bitb = b
                                bitk = k
                                z.avail_in = n
                                z.total_in += p - z.next_in_index
                                z.next_in_index = p
                                write = q
                                return inflate_flush(z, r)
                            }

                            c = if (c == 16) blens!![i - 1] else 0
                            do {
                                blens!![i++] = c
                            } while (--j != 0)
                            index = i
                        }
                    }
                    tb[0] = -1
                    val bl = IntArray(1)
                    val bd = IntArray(1)
                    val tl = IntArray(1)
                    val td = IntArray(1)

                    bl[0] = 9 // must be <= 9 for lookahead assumptions
                    bd[0] = 6 // must be <= 9 for lookahead assumptions
                    t = table
                    t = InfTree.inflate_trees_dynamic(
                        257 + (t and 0x1f),
                        1 + (t shr 5 and 0x1f),
                        blens!!,
                        bl,
                        bd,
                        tl,
                        td,
                        hufts,
                        z
                    )
                    if (t != Z_OK) {
                        if (t == Z_DATA_ERROR) {
                            blens = null
                            mode = BAD
                        }
                        r = t

                        bitb = b
                        bitk = k
                        z.avail_in = n
                        z.total_in += p - z.next_in_index
                        z.next_in_index = p
                        write = q
                        return inflate_flush(z, r)
                    }

                    codes = InfCodes(bl[0], bd[0], hufts, tl[0], hufts, td[0], z)
                    blens = null
                    mode = CODES
                    CODES -> {
                        bitb = b
                        bitk = k
                        z.avail_in = n
                        z.total_in += p - z.next_in_index
                        z.next_in_index = p
                        write = q

                        if ((r = codes.proc(this, z, r)) != Z_STREAM_END) {
                            return inflate_flush(z, r)
                        }
                        r = Z_OK
                        codes.free(z)

                        p = z.next_in_index
                        n = z.avail_in
                        b = bitb
                        k = bitk
                        q = write
                        m = if (q < read) read - q - 1 else end - q

                        if (last == 0) {
                            mode = TYPE
                            break
                        }
                        mode = DRY
                        DRY -> {
                        write = q
                        r = inflate_flush(z, r)
                        q = write
                        m = if (q < read) read - q - 1 else end - q
                        if (read != write) {
                            bitb = b
                            bitk = k
                            z.avail_in = n
                            z.total_in += p - z.next_in_index
                            z.next_in_index = p
                            write = q
                            return inflate_flush(z, r)
                        }
                        mode = DONE
                        DONE -> {
                        r = Z_STREAM_END

                        bitb = b
                        bitk = k
                        z.avail_in = n
                        z.total_in += p - z.next_in_index
                        z.next_in_index = p
                        write = q
                        return inflate_flush(z, r)
                    }
                        BAD -> {
                        r = Z_DATA_ERROR

                        bitb = b
                        bitk = k
                        z.avail_in = n
                        z.total_in += p - z.next_in_index
                        z.next_in_index = p
                        write = q
                        return inflate_flush(z, r)
                    }
                        else -> {
                        r = Z_STREAM_ERROR

                        bitb = b
                        bitk = k
                        z.avail_in = n
                        z.total_in += p - z.next_in_index
                        z.next_in_index = p
                        write = q
                        return inflate_flush(z, r)
                    }
                    }
                    }
                }
            }
        }
    }
}

internal fun free(z: ZStream) {
    reset(z, null)
    window = byteArrayOf()
    hufts = intArrayOf()
}

internal fun set_dictionary(d: ByteArray, start: Int, n: Int) {
    d.copyInto(window, 0, start, start + n)
    read = n
    write = read
}

// Returns true if inflate is currently at the end of a block generated
// by Z_SYNC_FLUSH or Z_FULL_FLUSH.
internal fun sync_point(): Int {
    return if (mode == LENS) 1 else 0
}

// copy as much as possible from the sliding window to the output area
internal fun inflate_flush(z: ZStream, r: Int): Int {
    var r = r
    var n: Int
    var p: Int
    var q: Int

    // local copies of source and destination pointers
    p = z.next_out_index
    q = read

    // compute number of bytes to copy as far as end of window
    n = (if (q <= write) write else end) - q
    if (n > z.avail_out) n = z.avail_out
    if (n != 0 && r == Z_BUF_ERROR) r = Z_OK

    // update counters
    z.avail_out -= n
    z.total_out += n.toLong()

    // update check information
    if (checkfn != null) z.adler = check = z._adler.adler32(check, window, q, n)

    // copy as far as end of window
    window.copyInto(z.next_out, p, q, q + n)
    p += n
    q += n

    // see if more to copy at beginning of window
    if (q == end) {
        // wrap pointers
        q = 0
        if (write == end) write = 0

        // compute bytes to copy
        n = write - q
        if (n > z.avail_out) n = z.avail_out
        if (n != 0 && r == Z_BUF_ERROR) r = Z_OK

        // update counters
        z.avail_out -= n
        z.total_out += n.toLong()

        // update check information
        if (checkfn != null) z.adler = check = z._adler.adler32(check, window, q, n)

        // copy
        window.copyInto(z.next_out, p, q, q + n)
        p += n
        q += n
    }

    // update pointers
    z.next_out_index = p
    read = q

    // done
    return r
}
}