package ai.solace.zlib.deflate

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

import ai.solace.zlib.common.*

class InfBlocks(z: ZStream, internal val checkfn: Any?, w: Int) {
    companion object {
        private val IBLK_INFLATE_MASK = intArrayOf(
            0x00000000, 0x00000001, 0x00000003, 0x00000007, 0x0000000f,
            0x0000001f, 0x0000003f, 0x0000007f, 0x000000ff, 0x000001ff,
            0x000003ff, 0x000007ff, 0x00000fff, 0x00001fff, 0x00003fff,
            0x00007fff, 0x0000ffff
        )
        private val IBLK_BORDER = intArrayOf(16, 17, 18, 0, 8, 7, 9, 6, 10, 5, 11, 4, 12, 3, 13, 2, 14, 1, 15)
    }

    private var mode = IBLK_TYPE
    private var left = 0
    private var table = 0
    private var index = 0
    private var blens: IntArray? = null
    private val bb = IntArray(1)
    private val tb = IntArray(1)
    private var codes: InfCodes? = null
    private var last = 0
    internal var bitk = 0
    internal var bitb = 0
    private val hufts: IntArray = IntArray(IBLK_MANY * 3)
    internal val window: ByteArray = ByteArray(w)
    internal val end: Int = w
    internal var read = 0
    internal var write = 0
    internal var check: Long = 0

    init {
        this.mode = IBLK_TYPE
        reset(z, null)
    }

    fun reset(z: ZStream?, c: LongArray?) {
        if (c != null) c[0] = check
        if (mode == IBLK_BTREE || mode == IBLK_DTREE) {
            blens = null
        }
        if (mode == IBLK_CODES) {
            codes?.free(z)
        }
        mode = IBLK_TYPE
        bitk = 0
        bitb = 0
        read = 0
        write = 0
        if (checkfn != null) {
            z?.adler = Adler32().adler32(0L, null, 0, 0)
            check = z!!.adler
        }
    }

    fun proc(z: ZStream, r_in: Int): Int {
        var r = r_in
        var p: Int = z.next_in_index
        var n: Int = z.avail_in
        var b: Int = bitb
        var k: Int = bitk
        var q: Int = write
        var m: Int = if (q < read) read - q - 1 else end - q

        while (true) {
            when (mode) {
                IBLK_TYPE -> {
                    while (k < 3) {
                        if (n != 0) {
                            r = Z_OK
                        } else {
                            bitb = b; bitk = k; z.avail_in = n; z.total_in += (p - z.next_in_index).toLong(); z.next_in_index = p; write = q
                            return inflate_flush(this, z, r)
                        }
                        n--
                        b = b or ((z.next_in!![p++].toInt() and 0xff) shl k)
                        k += 8
                    }
                    val t = b and 7
                    last = t and 1

                    when (t ushr 1) {
                        0 -> {
                            b = b ushr 3; k -= 3
                            val t2 = k and 7
                            b = b ushr t2; k -= t2
                            mode = IBLK_LENS
                        }
                        1 -> {
                            val bl = IntArray(1)
                            val bd = IntArray(1)
                            val tl = arrayOf(IntArray(0))
                            val td = arrayOf(IntArray(0))
                            InfTree.inflate_trees_fixed(bl, bd, tl, td, z)
                            codes = InfCodes(bl[0], bd[0], tl[0], td[0], z)
                            b = b ushr 3; k -= 3
                            mode = IBLK_CODES
                        }
                        2 -> {
                            b = b ushr 3; k -= 3
                            mode = IBLK_TABLE
                        }
                        3 -> {
                            b = b ushr 3; k -= 3
                            mode = IBLK_BAD
                            z.msg = "invalid block type"
                            r = Z_DATA_ERROR
                            bitb = b; bitk = k; z.avail_in = n; z.total_in += (p - z.next_in_index).toLong(); z.next_in_index = p; write = q
                            return inflate_flush(this, z, r)
                        }
                    }
                }
                IBLK_LENS -> {
                    while (k < 32) {
                        if (n != 0) {
                            r = Z_OK
                        } else {
                            bitb = b; bitk = k; z.avail_in = n; z.total_in += (p - z.next_in_index).toLong(); z.next_in_index = p; write = q
                            return inflate_flush(this, z, r)
                        }
                        n--
                        b = b or ((z.next_in!![p++].toInt() and 0xff) shl k)
                        k += 8
                    }
                    if (((b.inv() ushr 16) and 0xffff) != (b and 0xffff)) {
                        mode = IBLK_BAD
                        z.msg = "invalid stored block lengths"
                        r = Z_DATA_ERROR
                        bitb = b; bitk = k; z.avail_in = n; z.total_in += (p - z.next_in_index).toLong(); z.next_in_index = p; write = q
                        return inflate_flush(this, z, r)
                    }
                    left = (b and 0xffff)
                    b = 0; k = 0
                    mode = if (left != 0) IBLK_STORED else if (last != 0) IBLK_DRY else IBLK_TYPE
                }
                IBLK_STORED -> {
                    if (n == 0) {
                        bitb = b; bitk = k; z.avail_in = n; z.total_in += (p - z.next_in_index).toLong(); z.next_in_index = p; write = q
                        return inflate_flush(this, z, r)
                    }
                    if (m == 0) {
                        if (q == end && read != 0) {
                            q = 0; m = if (q < read) read - q - 1 else end - q
                        }
                        if (m == 0) {
                            write = q; r = inflate_flush(this, z, r); q = write; m = if (q < read) read - q - 1 else end - q
                            if (q == end && read != 0) {
                                q = 0; m = if (q < read) read - q - 1 else end - q
                            }
                            if (m == 0) {
                                bitb = b; bitk = k; z.avail_in = n; z.total_in += (p - z.next_in_index).toLong(); z.next_in_index = p; write = q
                                return inflate_flush(this, z, r)
                            }
                        }
                    }
                    r = Z_OK
                    var t = left
                    if (t > n) t = n
                    if (t > m) t = m
                    z.next_in!!.copyInto(window, q, p, p + t)
                    p += t; n -= t
                    q += t; m -= t
                    left -= t
                    if (left == 0) {
                        mode = if (last != 0) IBLK_DRY else IBLK_TYPE
                    }
                }
                IBLK_TABLE -> {
                    while (k < 14) {
                        if (n != 0) {
                            r = Z_OK
                        } else {
                            bitb = b; bitk = k; z.avail_in = n; z.total_in += (p - z.next_in_index).toLong(); z.next_in_index = p; write = q
                            return inflate_flush(this, z, r)
                        }
                        n--
                        b = b or ((z.next_in!![p++].toInt() and 0xff) shl k)
                        k += 8
                    }
                    table = (b and 0x3fff)
                    val t = table
                    if ((t and 0x1f) > 29 || ((t ushr 5) and 0x1f) > 29) {
                        mode = IBLK_BAD
                        z.msg = "too many length or distance symbols"
                        r = Z_DATA_ERROR
                        bitb = b; bitk = k; z.avail_in = n; z.total_in += (p - z.next_in_index).toLong(); z.next_in_index = p; write = q
                        return inflate_flush(this, z, r)
                    }
                    val t2 = 258 + (t and 0x1f) + ((t ushr 5) and 0x1f)
                    blens = IntArray(t2)
                    b = b ushr 14; k -= 14
                    index = 0
                    mode = IBLK_BTREE
                }
                IBLK_BTREE -> {
                    while (index < 4 + (table ushr 10)) {
                        while (k < 3) {
                            if (n != 0) {
                                r = Z_OK
                            } else {
                                bitb = b; bitk = k; z.avail_in = n; z.total_in += (p - z.next_in_index).toLong(); z.next_in_index = p; write = q
                                return inflate_flush(this, z, r)
                            }
                            n--
                            b = b or ((z.next_in!![p++].toInt() and 0xff) shl k)
                            k += 8
                        }
                        blens!![IBLK_BORDER[index++]] = b and 7
                        b = b ushr 3; k -= 3
                    }
                    while (index < 19) {
                        blens!![IBLK_BORDER[index++]] = 0
                    }
                    bb[0] = 7
                    val t = InfTree.inflate_trees_bits(blens!!, bb, tb, hufts, z)
                    if (t != Z_OK) {
                        r = t
                        if (r == Z_DATA_ERROR) {
                            blens = null
                            mode = IBLK_BAD
                        }
                        bitb = b; bitk = k; z.avail_in = n; z.total_in += (p - z.next_in_index).toLong(); z.next_in_index = p; write = q
                        return inflate_flush(this, z, r)
                    }
                    index = 0
                    mode = IBLK_DTREE
                }
                IBLK_DTREE -> {
                    val t = table
                    while (index < 258 + (t and 0x1f) + ((t ushr 5) and 0x1f)) {
                        var i: Int
                        val j: Int
                        var c: Int
                        var t2 = bb[0]
                        while (k < t2) {
                            if (n != 0) {
                                r = Z_OK
                            } else {
                                bitb = b; bitk = k; z.avail_in = n; z.total_in += (p - z.next_in_index).toLong(); z.next_in_index = p; write = q
                                return inflate_flush(this, z, r)
                            }
                            n--
                            b = b or ((z.next_in!![p++].toInt() and 0xff) shl k)
                            k += 8
                        }
                        t2 = hufts[(tb[0] + (b and IBLK_INFLATE_MASK[t2])) * 3 + 1]
                        c = hufts[(tb[0] + (b and IBLK_INFLATE_MASK[t2])) * 3 + 2]
                        if (c < 16) {
                            b = b ushr t2; k -= t2
                            blens!![index++] = c
                        } else {
                            i = if (c == 18) 7 else c - 14
                            j = if (c == 18) 11 else 3
                            while (k < t2 + i) {
                                if (n != 0) {
                                    r = Z_OK
                                } else {
                                    bitb = b; bitk = k; z.avail_in = n; z.total_in += (p - z.next_in_index).toLong(); z.next_in_index = p; write = q
                                    return inflate_flush(this, z, r)
                                }
                                n--
                                b = b or ((z.next_in!![p++].toInt() and 0xff) shl k)
                                k += 8
                            }
                            b = b ushr t2; k -= t2
                            var j2 = j + (b and IBLK_INFLATE_MASK[i])
                            b = b ushr i; k -= i
                            i = index
                            val t3 = table
                            if (i + j2 > 258 + (t3 and 0x1f) + ((t3 ushr 5) and 0x1f) || (c == 16 && i < 1)) {
                                blens = null
                                mode = IBLK_BAD
                                z.msg = "invalid bit length repeat"
                                r = Z_DATA_ERROR
                                bitb = b; bitk = k; z.avail_in = n; z.total_in += (p - z.next_in_index).toLong(); z.next_in_index = p; write = q
                                return inflate_flush(this, z, r)
                            }
                            c = if (c == 16) blens!![i - 1] else 0
                            do {
                                blens!![i++] = c
                            } while (--j2 != 0)
                            index = i
                        }
                    }
                    tb[0] = -1
                    val bl_ = IntArray(1)
                    val bd_ = IntArray(1)
                    val tl_ = IntArray(1)
                    val td_ = IntArray(1)
                    bl_[0] = 9; bd_[0] = 6
                    val t4 = table
                    val t5 = InfTree.inflate_trees_dynamic(257 + (t4 and 0x1f), 1 + ((t4 ushr 5) and 0x1f), blens!!, bl_, bd_, tl_, td_, hufts, z)
                    if (t5 != Z_OK) {
                        if (t5 == Z_DATA_ERROR) {
                            blens = null
                            mode = IBLK_BAD
                        }
                        r = t5
                        bitb = b; bitk = k; z.avail_in = n; z.total_in += (p - z.next_in_index).toLong(); z.next_in_index = p; write = q
                        return inflate_flush(this, z, r)
                    }
                    codes = InfCodes(bl_[0], bd_[0], hufts, tl_[0], hufts, td_[0], z)
                    blens = null
                    mode = IBLK_CODES
                }
                IBLK_CODES -> {
                    bitb = b; bitk = k; z.avail_in = n; z.total_in += (p - z.next_in_index).toLong(); z.next_in_index = p; write = q
                    r = codes!!.proc(this, z, r)
                    if (r != Z_STREAM_END) {
                        return inflate_flush(this, z, r)
                    }
                    r = Z_OK
                    codes!!.free(z)
                    p = z.next_in_index; n = z.avail_in; b = bitb; k = bitk; q = write; m = if (q < read) read - q - 1 else end - q
                    if (last == 0) {
                        mode = IBLK_TYPE
                        continue
                    }
                    mode = IBLK_DRY
                }
                IBLK_DRY -> {
                    write = q; r = inflate_flush(this, z, r); q = write; m = if (q < read) read - q - 1 else end - q
                    if (read != write) {
                        bitb = b; bitk = k; z.avail_in = n; z.total_in += (p - z.next_in_index).toLong(); z.next_in_index = p; write = q
                        return inflate_flush(this, z, r)
                    }
                    mode = IBLK_DONE
                }
                IBLK_DONE -> {
                    r = Z_STREAM_END
                    bitb = b; bitk = k; z.avail_in = n; z.total_in += (p - z.next_in_index).toLong(); z.next_in_index = p; write = q
                    return inflate_flush(this, z, r)
                }
                IBLK_BAD -> {
                    r = Z_DATA_ERROR
                    bitb = b; bitk = k; z.avail_in = n; z.total_in += (p - z.next_in_index).toLong(); z.next_in_index = p; write = q
                    return inflate_flush(this, z, r)
                }
                else -> {
                    r = Z_STREAM_ERROR
                    bitb = b; bitk = k; z.avail_in = n; z.total_in += (p - z.next_in_index).toLong(); z.next_in_index = p; write = q
                    return inflate_flush(this, z, r)
                }
            }
        }
    }

    fun free(z: ZStream?) {
        reset(z, null)
        // window = null // Not possible in Kotlin like this
        // hufts = null
    }

    fun set_dictionary(d: ByteArray, start: Int, n: Int) {
        d.copyInto(window, 0, start, start + n)
        read = n
        write = read
    }

    val sync_point: Int
        get() = if (mode == IBLK_LENS) 1 else 0

    // inflate_flush function moved to InfBlocksUtils.kt
}