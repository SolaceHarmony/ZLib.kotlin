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

import ai.solace.zlib.common.* // Import all constants

internal class Inflate {

    // Constants previously defined here are now in ai.solace.zlib.common.Constants
    // MAX_WBITS, PRESET_DICT, Z_NO_FLUSH, Z_PARTIAL_FLUSH, Z_SYNC_FLUSH, Z_FULL_FLUSH,
    // Z_FINISH, Z_DEFLATED, Z_OK, Z_STREAM_END, Z_NEED_DICT, Z_ERRNO, Z_STREAM_ERROR,
    // Z_DATA_ERROR, Z_MEM_ERROR, Z_BUF_ERROR, Z_VERSION_ERROR
    //
    // Mode constants (METHOD, FLAG, etc.) are now INF_METHOD, INF_FLAG, etc. in Constants.kt
    // mark array is now INF_MARK in Constants.kt

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
        z.istate!!.mode = if (z.istate!!.nowrap != 0) INF_BLOCKS else INF_METHOD
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
                INF_METHOD -> {
                    if (z.avail_in == 0) return r
                    r = fMut
                    z.avail_in--
                    z.total_in++
                    if ((z.istate!!.method.also { z.istate!!.method = z.next_in!![z.next_in_index++] }
                            .toInt() and 0xf) != Z_DEFLATED) {
                        z.istate!!.mode = INF_BAD
                        z.msg = "unknown compression method"
                        z.istate!!.marker = 5 // can't try inflateSync
                        break
                    }
                    if ((z.istate!!.method.toInt() shr 4) + 8 > z.istate!!.wbits) {
                        z.istate!!.mode = INF_BAD
                        z.msg = "invalid window size"
                        z.istate!!.marker = 5 // can't try inflateSync
                        break
                    }
                    z.istate!!.mode = INF_FLAG
                }

                INF_FLAG -> {
                    if (z.avail_in == 0) return r
                    r = fMut

                    z.avail_in--
                    z.total_in++
                    b = z.next_in!![z.next_in_index++].toInt() and 0xff

                    if (((z.istate!!.method.toInt() shl 8) + b) % 31 != 0) {
                        z.istate!!.mode = INF_BAD
                        z.msg = "incorrect header check"
                        z.istate!!.marker = 5 // can't try inflateSync
                        break
                    }

                    if (b and PRESET_DICT == 0) {
                        z.istate!!.mode = INF_BLOCKS
                        break
                    }
                    z.istate!!.mode = INF_DICT4
                }

                INF_DICT4 -> {
                    if (z.avail_in == 0) return r
                    r = fMut

                    z.avail_in--
                    z.total_in++
                    z.istate!!.need =
                        ((z.next_in!![z.next_in_index++].toInt() and 0xff) shl 24).toLong() and 0xff000000L.toInt()
                            .toLong()
                    z.istate!!.mode = INF_DICT3
                }

                INF_DICT3 -> {
                    if (z.avail_in == 0) return r
                    r = fMut

                    z.avail_in--
                    z.total_in++
                    z.istate!!.need +=
                        ((z.next_in!![z.next_in_index++].toInt() and 0xff) shl 16).toLong() and 0xff0000L
                    z.istate!!.mode = INF_DICT2
                }

                INF_DICT2 -> {
                    if (z.avail_in == 0) return r
                    r = fMut

                    z.avail_in--
                    z.total_in++
                    z.istate!!.need +=
                        ((z.next_in!![z.next_in_index++].toInt() and 0xff) shl 8).toLong() and 0xff00L
                    z.istate!!.mode = INF_DICT1
                }

                INF_DICT1 -> {
                    if (z.avail_in == 0) return r
                    r = fMut

                    z.avail_in--
                    z.total_in++
                    z.istate!!.need += (z.next_in!![z.next_in_index++].toInt() and 0xff).toLong()
                    z.adler = z.istate!!.need
                    z.istate!!.mode = INF_DICT0
                    return Z_NEED_DICT
                }

                INF_DICT0 -> {
                    z.istate!!.mode = INF_BAD
                    z.msg = "need dictionary"
                    z.istate!!.marker = 0 // can try inflateSync
                    return Z_STREAM_ERROR
                }

                INF_BLOCKS -> {
                    r = z.istate!!.blocks!!.proc(z, r)
                    if (r == Z_DATA_ERROR) {
                        z.istate!!.mode = INF_BAD
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
                    z.istate!!.mode = if (z.istate!!.nowrap != 0) INF_DONE else INF_CHECK4
                }

                INF_CHECK4 -> {
                    if (z.avail_in == 0) return r
                    r = fMut

                    z.avail_in--
                    z.total_in++
                    z.istate!!.need =
                        ((z.next_in!![z.next_in_index++].toInt() and 0xff) shl 24).toLong() and 0xff000000L.toInt()
                            .toLong()
                    z.istate!!.mode = INF_CHECK3
                }

                INF_CHECK3 -> {
                    if (z.avail_in == 0) return r
                    r = fMut

                    z.avail_in--
                    z.total_in++
                    z.istate!!.need +=
                        ((z.next_in!![z.next_in_index++].toInt() and 0xff) shl 16).toLong() and 0xff0000L
                    z.istate!!.mode = INF_CHECK2
                }

                INF_CHECK2 -> {
                    if (z.avail_in == 0) return r
                    r = fMut

                    z.avail_in--
                    z.total_in++
                    z.istate!!.need +=
                        ((z.next_in!![z.next_in_index++].toInt() and 0xff) shl 8).toLong() and 0xff00L
                    z.istate!!.mode = INF_CHECK1
                }

                INF_CHECK1 -> {
                    if (z.avail_in == 0) return r
                    r = fMut

                    z.avail_in--
                    z.total_in++
                    z.istate!!.need += (z.next_in!![z.next_in_index++].toInt() and 0xff).toLong()

                    if (z.istate!!.was[0].toInt() != z.istate!!.need.toInt()) {
                        z.istate!!.mode = INF_BAD
                        z.msg = "incorrect data check"
                        z.istate!!.marker = 5 // can't try inflateSync
                        break
                    }

                    z.istate!!.mode = INF_DONE
                }

                INF_DONE -> return Z_STREAM_END
                INF_BAD -> return Z_DATA_ERROR
                else -> return Z_STREAM_ERROR
            }
        }
        return TODO("Provide the return value")
    }

    internal fun inflateSetDictionary(z: ZStream?, dictionary: ByteArray?, dictLength: Int): Int {
        val lengthMut = dictLength.coerceAtMost(1 shl z!!.istate!!.wbits)
        val index = if (lengthMut < dictLength) dictLength - lengthMut else 0

        if (z == null || z.istate == null || z.istate!!.mode != INF_DICT0) return Z_STREAM_ERROR

        if (z._adler!!.adler32(1L, dictionary, 0, dictLength) != z.adler) {
            return Z_DATA_ERROR
        }

        z.adler = z._adler!!.adler32(0, null, 0, 0)
        z.istate!!.blocks!!.set_dictionary(dictionary!!, index, lengthMut)
        z.istate!!.mode = INF_BLOCKS
        return Z_OK
    }

    // private val mark = byteArrayOf(0, 0, (-1).toByte(), (-1).toByte()) // Moved to INF_MARK in Constants.kt

    internal fun inflateSync(z: ZStream?): Int {
        var n: Int // number of bytes to look at
        var p: Int // pointer to bytes
        var m: Int // number of marker bytes found in a row
        var r: Long
        var w: Long

        // set up
        if (z == null || z.istate == null) return Z_STREAM_ERROR
        if (z.istate!!.mode != INF_BAD) {
            z.istate!!.mode = INF_BAD
            z.istate!!.marker = 0
        }
        n = z.avail_in.takeIf { it != 0 } ?: return Z_BUF_ERROR
        p = z.next_in_index
        m = z.istate!!.marker

        // search
        while (n != 0 && m < 4) {
            m = when {
                z.next_in!![p] == INF_MARK[m] -> m + 1 // INF_MARK from common
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
        z.istate!!.mode = INF_BLOCKS
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