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

internal class Inflate {

    private val MAX_WBITS = 15 // 32K LZ77 window

    // preset dictionary flag in zlib header
    private val PRESET_DICT = 0x20

    internal val Z_NO_FLUSH = 0
    internal val Z_PARTIAL_FLUSH = 1
    internal val Z_SYNC_FLUSH = 2
    internal val Z_FULL_FLUSH = 3
    internal val Z_FINISH = 4

    private val Z_DEFLATED = 8

    private val Z_OK = 0
    private val Z_STREAM_END = 1
    private val Z_NEED_DICT = 2
    private val Z_ERRNO = -1
    private val Z_STREAM_ERROR = -2
    private val Z_DATA_ERROR = -3
    private val Z_MEM_ERROR = -4
    private val Z_BUF_ERROR = -5
    private val Z_VERSION_ERROR = -6

    private val METHOD = 0 // waiting for method byte
    private val FLAG = 1 // waiting for flag byte
    private val DICT4 = 2 // four dictionary check bytes to go
    private val DICT3 = 3 // three dictionary check bytes to go
    private val DICT2 = 4 // two dictionary check bytes to go
    private val DICT1 = 5 // one dictionary check byte to go
    private val DICT0 = 6 // waiting for inflateSetDictionary
    private val BLOCKS = 7 // decompressing blocks
    private val CHECK4 = 8 // four check bytes to go
    private val CHECK3 = 9 // three check bytes to go
    private val CHECK2 = 10 // two check bytes to go
    private val CHECK1 = 11 // one check byte to go
    private val DONE = 12 // finished check, done
    private val BAD = 13 // got an error--stay here

    internal var mode = 0 // current inflate mode

    // mode dependent information
    internal var method = 0 // if FLAGS, method byte

    // if CHECK, check values to compare
    internal var was = longArrayOf(0) // computed check value
    internal var need: Long = 0 // stream check value

    // if BAD, inflateSync's marker bytes count
    internal var marker = 0

    // mode independent information
    internal var nowrap = 0 // flag for no wrapper
    internal var wbits = 0 // log2(window size)  (8..15, defaults to 15)

    internal var blocks: InfBlocks? = null // current inflate_blocks state

    internal fun inflateReset(z: ZStream?): Int {
        if (z == null || z.istate == null) return Z_STREAM_ERROR

        z.total_in = 0
        z.total_out = 0
        z.msg = null
        z.istate!!.mode = if (z.istate!!.nowrap != 0) BLOCKS else METHOD
        z.istate!!.blocks!!.reset(z, null)
        return Z_OK
    }

    internal fun inflateEnd(z: ZStream?): Int {
        blocks?.free(z)
        blocks = null
        //    ZFREE(z, z->state);
        return Z_OK
    }

    internal fun inflateInit(z: ZStream?, w: Int): Int {
        z?.msg = null
        blocks = null

        // handle undocumented nowrap option (no zlib header or check)
        nowrap = 0
        var wMut = w
        if (w < 0) {
            wMut = -w
            nowrap = 1
        }

        // set window size
        if (wMut < 8 || wMut > 15) {
            inflateEnd(z)
            return Z_STREAM_ERROR
        }
        wbits = wMut

        z!!.istate!!.blocks = InfBlocks(z!!, if (z!!.istate!!.nowrap != 0) null else this, 1 shl wMut)

        // reset state
        inflateReset(z)
        return Z_OK
    }

    internal fun inflate(z: ZStream?, f: Int): Int {
        var r: Int
        var b: Int
        if (z == null || z.istate == null || z.next_in == null) return Z_STREAM_ERROR
        val fMut = if (f == Z_FINISH) Z_BUF_ERROR else Z_OK
        r = Z_BUF_ERROR
        while (true) {
            //System.out.println("mode: "+z.istate.mode);
            when (z.istate!!.mode) {
                METHOD -> {
                    if (z.avail_in == 0) return r
                    r = fMut
                    z.avail_in--
                    z.total_in++
                    if ((z.istate!!.method.also { z.istate!!.method = z.next_in!![z.next_in_index++] }
                            .toInt() and 0xf) != Z_DEFLATED) {
                        z.istate!!.mode = BAD
                        z.msg = "unknown compression method"
                        z.istate!!.marker = 5 // can't try inflateSync
                        break
                    }
                    if ((z.istate!!.method.toInt() shr 4) + 8 > z.istate!!.wbits) {
                        z.istate!!.mode = BAD
                        z.msg = "invalid window size"
                        z.istate!!.marker = 5 // can't try inflateSync
                        break
                    }
                    z.istate!!.mode = FLAG
                }

                FLAG -> {
                    if (z.avail_in == 0) return r
                    r = fMut

                    z.avail_in--
                    z.total_in++
                    b = z.next_in!![z.next_in_index++].toInt() and 0xff

                    if (((z.istate!!.method.toInt() shl 8) + b) % 31 != 0) {
                        z.istate!!.mode = BAD
                        z.msg = "incorrect header check"
                        z.istate!!.marker = 5 // can't try inflateSync
                        break
                    }

                    if (b and PRESET_DICT == 0) {
                        z.istate!!.mode = BLOCKS
                        break
                    }
                    z.istate!!.mode = DICT4
                }

                DICT4 -> {
                    if (z.avail_in == 0) return r
                    r = fMut

                    z.avail_in--
                    z.total_in++
                    z.istate!!.need =
                        ((z.next_in!![z.next_in_index++].toInt() and 0xff) shl 24).toLong() and 0xff000000L.toInt()
                            .toLong()
                    z.istate!!.mode = DICT3
                }

                DICT3 -> {
                    if (z.avail_in == 0) return r
                    r = fMut

                    z.avail_in--
                    z.total_in++
                    z.istate!!.need +=
                        ((z.next_in!![z.next_in_index++].toInt() and 0xff) shl 16).toLong() and 0xff0000L
                    z.istate!!.mode = DICT2
                }

                DICT2 -> {
                    if (z.avail_in == 0) return r
                    r = fMut

                    z.avail_in--
                    z.total_in++
                    z.istate!!.need +=
                        ((z.next_in!![z.next_in_index++].toInt() and 0xff) shl 8).toLong() and 0xff00L
                    z.istate!!.mode = DICT1
                }

                DICT1 -> {
                    if (z.avail_in == 0) return r
                    r = fMut

                    z.avail_in--
                    z.total_in++
                    z.istate!!.need += (z.next_in!![z.next_in_index++].toInt() and 0xff).toLong()
                    z.adler = z.istate!!.need
                    z.istate!!.mode = DICT0
                    return Z_NEED_DICT
                }

                DICT0 -> {
                    z.istate!!.mode = BAD
                    z.msg = "need dictionary"
                    z.istate!!.marker = 0 // can try inflateSync
                    return Z_STREAM_ERROR
                }

                BLOCKS -> {
                    r = z.istate!!.blocks!!.proc(z, r)
                    if (r == Z_DATA_ERROR) {
                        z.istate!!.mode = BAD
                        z.istate!!.marker = 0 // can try inflateSync
                        break
                    }
                    if (r == Z_OK) {
                        r = fMut
                    }
                    if (r != Z_STREAM_END) {
                        return r
                    }
                    r = fMut
                    z.istate!!.blocks!!.reset(z, z.istate!!.was)
                    z.istate!!.mode = if (z.istate!!.nowrap != 0) DONE else CHECK4
                }

                CHECK4 -> {
                    if (z.avail_in == 0) return r
                    r = fMut

                    z.avail_in--
                    z.total_in++
                    z.istate!!.need =
                        ((z.next_in!![z.next_in_index++].toInt() and 0xff) shl 24).toLong() and 0xff000000L.toInt()
                            .toLong()
                    z.istate!!.mode = CHECK3
                }

                CHECK3 -> {
                    if (z.avail_in == 0) return r
                    r = fMut

                    z.avail_in--
                    z.total_in++
                    z.istate!!.need +=
                        ((z.next_in!![z.next_in_index++].toInt() and 0xff) shl 16).toLong() and 0xff0000L
                    z.istate!!.mode = CHECK2
                }

                CHECK2 -> {
                    if (z.avail_in == 0) return r
                    r = fMut

                    z.avail_in--
                    z.total_in++
                    z.istate!!.need +=
                        ((z.next_in!![z.next_in_index++].toInt() and 0xff) shl 8).toLong() and 0xff00L
                    z.istate!!.mode = CHECK1
                }

                CHECK1 -> {
                    if (z.avail_in == 0) return r
                    r = fMut

                    z.avail_in--
                    z.total_in++
                    z.istate!!.need += (z.next_in!![z.next_in_index++].toInt() and 0xff).toLong()

                    if (z.istate!!.was[0].toInt() != z.istate!!.need.toInt()) {
                        z.istate!!.mode = BAD
                        z.msg = "incorrect data check"
                        z.istate!!.marker = 5 // can't try inflateSync
                        break
                    }

                    z.istate!!.mode = DONE
                }

                DONE -> return Z_STREAM_END
                BAD -> return Z_DATA_ERROR
                else -> return Z_STREAM_ERROR
            }
        }
        return TODO("Provide the return value")
    }

    internal fun inflateSetDictionary(z: ZStream?, dictionary: ByteArray?, dictLength: Int): Int {
        val lengthMut = dictLength.coerceAtMost(1 shl z!!.istate!!.wbits)
        val index = if (lengthMut < dictLength) dictLength - lengthMut else 0

        if (z == null || z.istate == null || z.istate!!.mode != DICT0) return Z_STREAM_ERROR

        if (z._adler!!.adler32(1L, dictionary, 0, dictLength) != z.adler) {
            return Z_DATA_ERROR
        }

        z.adler = z._adler!!.adler32(0, null, 0, 0)
        z.istate!!.blocks!!.set_dictionary(dictionary!!, index, lengthMut)
        z.istate!!.mode = BLOCKS
        return Z_OK
    }

    private val mark = byteArrayOf(0, 0, (-1).toByte(), (-1).toByte())

    internal fun inflateSync(z: ZStream?): Int {
        var n: Int // number of bytes to look at
        var p: Int // pointer to bytes
        var m: Int // number of marker bytes found in a row
        var r: Long
        var w: Long

        // set up
        if (z == null || z.istate == null) return Z_STREAM_ERROR
        if (z.istate!!.mode != BAD) {
            z.istate!!.mode = BAD
            z.istate!!.marker = 0
        }
        n = z.avail_in.takeIf { it != 0 } ?: return Z_BUF_ERROR
        p = z.next_in_index
        m = z.istate!!.marker

        // search
        while (n != 0 && m < 4) {
            m = when {
                z.next_in!![p] == mark[m] -> m + 1
                z.next_in!![p] != 0.toByte() -> 0
                else -> 4 - m
            }
            p++
            n--
        }

        // restore
        z.total_in += p - z.next_in_index
        z.next_in_index = p
        z.avail_in = n
        z.istate!!.marker = m

        // return no joy or set up to restart on a new block
        if (m != 4) {
            return Z_DATA_ERROR
        }
        r = z.total_in
        w = z.total_out
        inflateReset(z)
        z.total_in = r
        z.total_out = w
        z.istate!!.mode = BLOCKS
        return Z_OK
    }

    // Returns true if inflate is currently at the end of a block generated
    // by Z_SYNC_FLUSH or Z_FULL_FLUSH. This function is used by one PPP
    // implementation to provide an additional safety check. PPP uses Z_SYNC_FLUSH
    // but removes the length bytes of the resulting empty stored block. When
    // decompressing, PPP checks that at the end of input packet, inflate is
    // waiting for these length bytes.
    internal fun inflateSyncPoint(z: ZStream?): Int {
        if (z == null || z.istate == null || z.istate!!.blocks == null) return Z_STREAM_ERROR
        return z.istate!!.blocks!!.sync_point
    }
}