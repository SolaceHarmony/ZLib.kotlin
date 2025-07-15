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

/**
 * Internal implementation of the decompression algorithm.
 * 
 * This class handles the state machine for decompression of zlib-compressed data.
 * It processes the zlib header, manages decompression blocks, and verifies checksums.
 */
internal class Inflate {

    // Constants previously defined here are now in ai.solace.zlib.common.Constants
    // MAX_WBITS, PRESET_DICT, Z_NO_FLUSH, Z_PARTIAL_FLUSH, Z_SYNC_FLUSH, Z_FULL_FLUSH,
    // Z_FINISH, Z_DEFLATED, Z_OK, Z_STREAM_END, Z_NEED_DICT, Z_ERRNO, Z_STREAM_ERROR,
    // Z_DATA_ERROR, Z_MEM_ERROR, Z_BUF_ERROR, Z_VERSION_ERROR
    //
    // Mode constants (METHOD, FLAG, etc.) are now INF_METHOD, INF_FLAG, etc. in Constants.kt
    // mark array is now INF_MARK in Constants.kt

    /** Current inflate mode (INF_METHOD, INF_FLAG, etc.) */
    internal var mode = 0

    /** Compression method byte from the zlib header */
    internal var method = 0

    /** Computed Adler-32 checksum value */
    internal var was = longArrayOf(0)

    /** Expected checksum value from the stream */
    internal var need: Long = 0

    /** Marker bytes count for inflateSync */
    internal var marker = 0

    /** Flag for no zlib wrapper (1 = no header/trailer) */
    internal var nowrap = 0

    /** Log2 of the window size (8..15, defaults to 15) */
    internal var wbits = 0

    /** Current inflate_blocks state for processing compressed blocks */
    internal var blocks: InfBlocks? = null

    /**
     * Resets the decompression state.
     * 
     * This method resets counters and state variables to prepare for a new decompression
     * session or to recover from an error.
     * 
     * @param z The ZStream containing the decompression state
     * @return Z_OK on success, Z_STREAM_ERROR if z or z.istate is null
     */
    internal fun inflateReset(z: ZStream?): Int {
        if (z == null || z.iState == null) return Z_STREAM_ERROR

        z.totalIn = 0
        z.totalOut = 0
        z.msg = null
        z.iState!!.mode = if (z.iState!!.nowrap != 0) INF_BLOCKS else INF_METHOD
        z.iState!!.blocks!!.reset(z, null)
        return Z_OK
    }

    /**
     * Ends the decompression process and releases resources.
     * 
     * @param z The ZStream containing the decompression state
     * @return Z_OK on success
     */
    internal fun inflateEnd(z: ZStream?): Int {
        blocks?.free(z)
        blocks = null
        //    ZFREE(z, z->state);
        return Z_OK
    }

    /**
     * Initializes the decompression process.
     * 
     * This method sets up the decompression state, handles the nowrap option,
     * and initializes the inflate blocks.
     * 
     * @param z The ZStream to initialize for decompression
     * @param w The window bits parameter (8-15), or negative for nowrap mode
     * @return Z_OK on success, Z_STREAM_ERROR if parameters are invalid
     */
    internal fun inflateInit(z: ZStream, w: Int): Int {
        z.msg = null
        blocks = null

        // handle undocumented nowrap option (no zlib header or check)
        nowrap = 0
        var wMut = w
        if (wMut < 0) {
            wMut = -wMut
            nowrap = 1
        }

        // set window size
        if (wMut < 8 || wMut > 15) {
            inflateEnd(z)
            return Z_STREAM_ERROR
        }
        wbits = wMut

        z.iState!!.blocks = InfBlocks(z, if (z.iState!!.nowrap != 0) null else this, 1 shl wMut)

        // reset state
        inflateReset(z)
        return Z_OK
    }

    /**
     * Performs decompression on the input stream.
     * 
     * This is the main decompression method that implements a state machine to process
     * compressed data. It handles the zlib header, processes compressed blocks, and
     * verifies checksums.
     * 
     * @param z The ZStream containing input data and decompression state
     * @param f The flush mode (Z_NO_FLUSH, Z_SYNC_FLUSH, Z_FINISH, etc.)
     * @return Z_OK on success, Z_STREAM_END when decompression is complete,
     *         or an error code (Z_STREAM_ERROR, Z_DATA_ERROR, etc.)
     */
    internal fun inflate(z: ZStream?, f: Int): Int {
        println("[INFLATE_DEBUG] inflate() called with flush=$f")
        var r: Int
        var b: Int

        if (z == null || z.iState == null || z.nextIn == null) {
            return Z_STREAM_ERROR
        }

        // Initialize with appropriate value based on flush parameter
        val fMut = if (f == Z_FINISH) Z_BUF_ERROR else Z_OK
        r = Z_BUF_ERROR

        // Safety counter to prevent infinite loops
        var iterations = 0
        val maxIterations = 1000

        while (true) {
            // Safety check to prevent infinite loops
            iterations++
            if (iterations > maxIterations) {
                z.msg = "Too many iterations during inflation, possible corrupt data"
                z.iState!!.mode = INF_BAD
                return Z_DATA_ERROR
            }

            // Check for exhausted output buffer before processing
            if (z.availOut == 0 && z.iState!!.mode != INF_BAD) {
                return Z_BUF_ERROR
            }

            when (z.iState!!.mode) {
                INF_METHOD -> {
                    if (z.availIn == 0) return r
                    r = fMut
                    z.availIn--
                    z.totalIn++
                    z.iState!!.method = z.nextIn!![z.nextInIndex++].toInt() and 0xff
                    if ((z.iState!!.method and 0xf) != Z_DEFLATED) {
                        z.iState!!.mode = INF_BAD
                        z.msg = "unknown compression method"
                        z.iState!!.marker = 5 // can't try inflateSync
                        break
                    }
                    if ((z.iState!!.method shr 4) + 8 > z.iState!!.wbits) {
                        z.iState!!.mode = INF_BAD
                        z.msg = "invalid window size"
                        z.iState!!.marker = 5 // can't try inflateSync
                        break
                    }
                    z.iState!!.mode = INF_FLAG
                }

                INF_FLAG -> {
                    if (z.availIn == 0) return r
                    r = fMut

                    z.availIn--
                    z.totalIn++
                    b = z.nextIn!![z.nextInIndex++].toInt() and 0xff

                    if (((z.iState!!.method shl 8) + b) % 31 != 0) {
                        z.iState!!.mode = INF_BAD
                        z.msg = "incorrect header check"
                        z.iState!!.marker = 5 // can't try inflateSync
                        break
                    }

                    if (b and PRESET_DICT == 0) {
                        z.iState!!.mode = INF_BLOCKS
                        break
                    }
                    z.iState!!.mode = INF_DICT4
                }

                INF_DICT4 -> {
                    if (z.availIn == 0) return r
                    r = fMut

                    z.availIn--
                    z.totalIn++
                    z.iState!!.need = ((z.nextIn!![z.nextInIndex++].toInt() and 0xff).toLong() shl 24)
                    z.iState!!.mode = INF_DICT3
                }

                INF_DICT3 -> {
                    if (z.availIn == 0) return r
                    r = fMut

                    z.availIn--
                    z.totalIn++
                    z.iState!!.need += ((z.nextIn!![z.nextInIndex++].toInt() and 0xff).toLong() shl 16)
                    z.iState!!.mode = INF_DICT2
                }

                INF_DICT2 -> {
                    if (z.availIn == 0) return r
                    r = fMut

                    z.availIn--
                    z.totalIn++
                    z.iState!!.need += ((z.nextIn!![z.nextInIndex++].toInt() and 0xff).toLong() shl 8)
                    z.iState!!.mode = INF_DICT1
                }

                INF_DICT1 -> {
                    if (z.availIn == 0) return r
                    r = fMut

                    z.availIn--
                    z.totalIn++
                    z.iState!!.need += (z.nextIn!![z.nextInIndex++].toInt() and 0xff).toLong()
                    z.adler = z.iState!!.need
                    z.iState!!.mode = INF_DICT0
                    println("[DEBUG] INF_DICT1: Calculated Adler checksum: ${z.adler}, Expected: ${z.iState!!.need}")
                    return Z_NEED_DICT
                }

                INF_DICT0 -> {
                    z.iState!!.mode = INF_BAD
                    z.msg = "need dictionary"
                    z.iState!!.marker = 0 // can try inflateSync
                    return Z_STREAM_ERROR
                }

                INF_BLOCKS -> {
                    r = z.iState!!.blocks!!.proc(z, r)
                    if (r == Z_DATA_ERROR) {
                        z.iState!!.mode = INF_BAD
                        z.iState!!.marker = 0 // can try inflateSync
                        break
                    }
                    if (r == Z_OK) {
                        r = fMut
                    }

                    // Handle output buffer exhaustion
                    if (z.availOut == 0) {
                        // If we can't output any more data but still have input to process
                        // return with buffer error so caller can provide more output space
                        if (z.availIn > 0) {
                            return Z_BUF_ERROR
                        }
                    }

                    if (r != Z_STREAM_END) {
                        return r
                    }
                    r = fMut
                    z.iState!!.blocks!!.reset(z, z.iState!!.was)
                    z.iState!!.mode = if (z.iState!!.nowrap != 0) INF_DONE else INF_CHECK4
                }

                INF_CHECK4 -> {
                    if (z.availIn == 0) return r
                    r = fMut

                    z.availIn--
                    z.totalIn++
                    // Pascal: z.state^.sub.check.need := uLong(z.next_in^) shl 24;
                    val byteValue = (z.nextIn!![z.nextInIndex++].toInt() and 0xff).toUInt()
                    z.iState!!.need = (byteValue shl 24).toLong() and 0xFFFFFFFFL
                    z.iState!!.mode = INF_CHECK3
                }

                INF_CHECK3 -> {
                    if (z.availIn == 0) return r
                    r = fMut

                    z.availIn--
                    z.totalIn++
                    // Pascal: Inc(z.state^.sub.check.need, uLong(z.next_in^) shl 16);
                    val byteValue = (z.nextIn!![z.nextInIndex++].toInt() and 0xff).toUInt()
                    z.iState!!.need = (z.iState!!.need + ((byteValue shl 16).toLong())) and 0xFFFFFFFFL
                    z.iState!!.mode = INF_CHECK2
                }

                INF_CHECK2 -> {
                    if (z.availIn == 0) return r
                    r = fMut

                    z.availIn--
                    z.totalIn++
                    // Pascal: Inc(z.state^.sub.check.need, uLong(z.next_in^) shl 8);
                    val byteValue = (z.nextIn!![z.nextInIndex++].toInt() and 0xff).toUInt()
                    z.iState!!.need = (z.iState!!.need + ((byteValue shl 8).toLong())) and 0xFFFFFFFFL
                    z.iState!!.mode = INF_CHECK1
                }

                INF_CHECK1 -> {
                    if (z.availIn == 0) return r
                    r = fMut

                    z.availIn--
                    z.totalIn++
                    // Pascal: Inc(z.state^.sub.check.need, uLong(z.next_in^));
                    val byteValue = (z.nextIn!![z.nextInIndex++].toInt() and 0xff).toUInt()
                    z.iState!!.need = (z.iState!!.need + byteValue.toLong()) and 0xFFFFFFFFL

                    // Pascal: if (z.state^.sub.check.was <> z.state^.sub.check.need) then
                    if ((z.iState!!.was[0] and 0xFFFFFFFFL) != z.iState!!.need) {
                        z.iState!!.mode = INF_BAD
                        z.msg = "incorrect data check"
                        z.iState!!.marker = 5 // can't try inflateSync
                        break
                    }

                    z.iState!!.mode = INF_DONE
                }

                INF_DONE -> return Z_STREAM_END
                INF_BAD -> return Z_DATA_ERROR
                else -> return Z_STREAM_ERROR
            }
        }
        return Z_OK
    }

    /**
     * Sets a dictionary for decompression.
     * 
     * This method is called when a preset dictionary is required for decompression.
     * It verifies the dictionary's checksum and sets it up for use by the decompression blocks.
     * 
     * @param z The ZStream containing the decompression state
     * @param dictionary The byte array containing the dictionary
     * @param dictLength The length of the dictionary
     * @return Z_OK on success, Z_STREAM_ERROR if state is invalid, Z_DATA_ERROR if checksum fails
     */
    internal fun inflateSetDictionary(z: ZStream?, dictionary: ByteArray?, dictLength: Int): Int {
        var lengthMut = dictLength
        var index = 0

        if (z == null || z.iState == null || z.iState!!.mode != INF_DICT0) return Z_STREAM_ERROR

        println("[DEBUG] Validating Adler-32 checksum: Calculated=${z.adlerChecksum!!.adler32(1L, dictionary, 0, dictLength)}, Expected=${z.adler}")
        if (z.adlerChecksum!!.adler32(1L, dictionary, 0, dictLength) != z.adler) {
            return Z_DATA_ERROR
        }

        z.adler = z.adlerChecksum!!.adler32(0, null, 0, 0)

        val wsize = 1 shl z.iState!!.wbits
        if (lengthMut >= wsize) {
            lengthMut = wsize
            index = dictLength - lengthMut
        }
        println("[DEBUG] Dictionary content: ${dictionary?.joinToString(", ")}")
        z.iState!!.blocks!!.setDictionary(dictionary!!, index, lengthMut)
        z.iState!!.mode = INF_BLOCKS
        return Z_OK
    }

    /**
     * Attempts to recover synchronization after corrupt data.
     * 
     * This method searches for a specific marker pattern in the input stream
     * to resynchronize the decompression process after encountering corrupt data.
     * 
     * @param z The ZStream containing the decompression state
     * @return Z_OK on successful resynchronization, Z_STREAM_ERROR if state is invalid,
     *         Z_BUF_ERROR if no input is available, Z_DATA_ERROR if sync pattern not found
     */
    internal fun inflateSync(z: ZStream?): Int {
        var m: Int // number of marker bytes found in a row
        var w: Long

        // set up
        if (z == null || z.iState == null) return Z_STREAM_ERROR
        if (z.iState!!.mode != INF_BAD) {
            z.iState!!.mode = INF_BAD
            z.iState!!.marker = 0
        }
        var n: Int = z.availIn.takeIf { it != 0 } ?: return Z_BUF_ERROR // number of bytes to look at
        var p: Int = z.nextInIndex // pointer to bytes
        m = z.iState!!.marker

        // search
        while (n != 0 && m < 4) {
            m = when {
                z.nextIn!![p].toUByte() == INF_MARK[m] -> m + 1
                z.nextIn!![p] != 0.toByte() -> 0
                else -> 4 - m
            }
            p++
            n--
        }

        // restore
        z.totalIn += p - z.nextInIndex
        z.nextInIndex = p
        z.availIn = n
        z.iState!!.marker = m

        // return no joy or set up to restart on a new block
        if (m != 4) {
            return Z_DATA_ERROR
        }
        var r: Long = z.totalIn
        w = z.totalOut
        inflateReset(z)
        z.totalIn = r
        z.totalOut = w
        z.iState!!.mode = INF_BLOCKS
        return Z_OK
    }

}
