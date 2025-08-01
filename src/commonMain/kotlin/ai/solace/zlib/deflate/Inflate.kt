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
import ai.solace.zlib.bitwise.ArithmeticBitwiseOps

/**
 * Internal implementation of the decompression algorithm.
 *
 * This class handles the state machine for decompression of zlib-compressed data.
 * It processes the zlib header, manages decompression blocks, and verifies checksums.
 */
internal class Inflate {
    private val bitwiseOps = ArithmeticBitwiseOps.BITS_32

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
        ZlibLogger.debug("inflateReset: mode=${z.iState!!.mode}")
        return Z_OK
    }

    /**
     * Ends the decompression process and releases resources.
     * 
     * @param z The ZStream containing the decompression state
     * @return Z_OK on success
     */
    internal fun inflateEnd(z: ZStream?): Int {
        ZlibLogger.debug("inflateEnd")
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
        z.iState = this  // Initialize the inflate state
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

        z.iState!!.blocks = InfBlocks(z, if (z.iState!!.nowrap != 0) null else this, bitwiseOps.leftShift(1L, wMut).toInt())
        ZlibLogger.debug("inflateInit: wbits=$wbits nowrap=$nowrap")

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
    internal fun inflate(z: ZStream, f: Int): Int {
        ZlibLogger.logInflate("Starting inflate with f=$f, availIn=${z.availIn}, availOut=${z.availOut}")
        if (z.iState == null || z.nextIn == null) {
            ZlibLogger.debug("inflate: Z_STREAM_ERROR: iState or nextIn is null")
            return Z_STREAM_ERROR
        }

        val fMut = Z_OK
        var r = if (f == Z_FINISH) Z_OK else Z_BUF_ERROR

        while (true) {
            ZlibLogger.logInflate("Processing mode=${z.iState!!.mode}, availIn=${z.availIn}, availOut=${z.availOut}")
            when (z.iState!!.mode) {
                INF_METHOD -> {
                    if (z.availIn == 0) {
                        ZlibLogger.logInflate("No input available, returning r=$r")
                        return r
                    }
                    r = fMut
                    z.availIn--
                    z.totalIn++
                    z.iState!!.method = z.nextIn!![z.nextInIndex++].toInt()
                    ZlibLogger.logInflate("Read method byte: ${z.iState!!.method} (compression=${z.iState!!.method and 0xf}, windowBits=${(z.iState!!.method shr 4) + 8})")
                    ZlibLogger.logBitwise("and(${z.iState!!.method}, 15) -> ${z.iState!!.method and 0xf}")
                    if ((z.iState!!.method and 0xf) != Z_DEFLATED) {
                        z.iState!!.mode = INF_BAD
                        z.msg = "unknown compression method"
                        z.iState!!.marker = 5 // can't try inflateSync
                        ZlibLogger.debug("inflate: unknown compression method")
                        break
                    }
                    ZlibLogger.logBitwise("rightShift(${z.iState!!.method}, 4) -> ${z.iState!!.method shr 4}")
                    if ((z.iState!!.method shr 4) + 8 > z.iState!!.wbits) {
                        z.iState!!.mode = INF_BAD
                        z.msg = "invalid window size"
                        z.iState!!.marker = 5 // can't try inflateSync
                        ZlibLogger.debug("inflate: invalid window size")
                        break
                    }
                    z.iState!!.mode = INF_FLAG
                    ZlibLogger.logInflate("Method processed successfully, moving to FLAG mode")
                }

                INF_FLAG -> {
                    if (z.availIn == 0) {
                        ZlibLogger.logInflate("No input available for FLAG, returning r=$r")
                        return r
                    }
                    r = fMut
                    z.availIn--
                    z.totalIn++
                    val b = z.nextIn!![z.nextInIndex++].toInt() and 0xff
                    ZlibLogger.logInflate("Read flag byte: $b (0x${b.toString(16)})")
                    ZlibLogger.logBitwise("and($b, 255) -> $b")
                    
                    val headerCheck = ((z.iState!!.method shl 8) + b) % 31
                    ZlibLogger.logBitwise("leftShift(${z.iState!!.method}, 8) -> ${z.iState!!.method shl 8}")
                    ZlibLogger.logInflate("Header check calculation: ((${z.iState!!.method} << 8) + $b) % 31 = $headerCheck")
                    
                    if (headerCheck != 0) {
                        z.iState!!.mode = INF_BAD
                        z.msg = "incorrect header check"
                        z.iState!!.marker = 5 // can't try inflateSync
                        ZlibLogger.debug("inflate: incorrect header check")
                        break
                    }
                    ZlibLogger.debug("inflate: FLAG: b=$b")
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
                    z.iState!!.need = (z.nextIn!![z.nextInIndex++].toInt() and 0xff shl 24 and -0x1000000).toLong()
                    z.iState!!.mode = INF_DICT3
                }

                INF_DICT3 -> {
                    if (z.availIn == 0) return r
                    r = fMut
                    z.availIn--
                    z.totalIn++
                    z.iState!!.need += (z.nextIn!![z.nextInIndex++].toInt() and 0xff shl 16).toLong()
                    z.iState!!.mode = INF_DICT2
                }

                INF_DICT2 -> {
                    if (z.availIn == 0) return r
                    r = fMut
                    z.availIn--
                    z.totalIn++
                    z.iState!!.need += (z.nextIn!![z.nextInIndex++].toInt() and 0xff shl 8).toLong()
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
                    ZlibLogger.debug("inflate: DICT: adler=${z.adler}")
                    return Z_NEED_DICT
                }

                INF_DICT0 -> {
                    z.iState!!.mode = INF_BAD
                    z.msg = "need dictionary"
                    z.iState!!.marker = 0
                    ZlibLogger.debug("inflate: need dictionary")
                    return Z_STREAM_ERROR
                }

                INF_BLOCKS -> {
                    r = z.iState!!.blocks!!.proc(z, r)
                    ZlibLogger.debug("inflate: BLOCKS: r=$r")
                    if (r == Z_DATA_ERROR) {
                        z.iState!!.mode = INF_BAD
                        z.iState!!.marker = 0 // can't try inflateSync
                        ZlibLogger.debug("inflate: BLOCKS: Z_DATA_ERROR")
                        break
                    }
                    if (r == Z_OK) {
                        r = fMut
                    }
                    if (r != Z_STREAM_END) {
                        return r
                    }
                    r = fMut
                    z.iState!!.blocks!!.reset(z, z.iState!!.was)
                    if (z.iState!!.nowrap != 0) {
                        z.iState!!.mode = INF_DONE
                        break
                    }
                    z.iState!!.mode = INF_CHECK4
                }

                INF_CHECK4 -> {
                    if (z.availIn == 0) return r
                    r = fMut
                    z.availIn--
                    z.totalIn++
                    val b4 = z.nextIn!![z.nextInIndex++].toInt() and 0xff
                    z.iState!!.need = (b4 shl 24 and -0x1000000).toLong()
                    ZlibLogger.logInflate("CHECK4: Read byte $b4, need now = ${z.iState!!.need}")
                    ZlibLogger.logBitwise("leftShift($b4, 24) -> ${b4 shl 24}")
                    ZlibLogger.logBitwise("and(${b4 shl 24}, ${-0x1000000}) -> ${b4 shl 24 and -0x1000000}")
                    z.iState!!.mode = INF_CHECK3
                }

                INF_CHECK3 -> {
                    if (z.availIn == 0) return r
                    r = fMut
                    z.availIn--
                    z.totalIn++
                    val b3 = z.nextIn!![z.nextInIndex++].toInt() and 0xff
                    val addValue = (b3 shl 16).toLong()
                    z.iState!!.need += addValue
                    ZlibLogger.logInflate("CHECK3: Read byte $b3, adding $addValue, need now = ${z.iState!!.need}")
                    ZlibLogger.logBitwise("leftShift($b3, 16) -> ${b3 shl 16}")
                    z.iState!!.mode = INF_CHECK2
                }

                INF_CHECK2 -> {
                    if (z.availIn == 0) return r
                    r = fMut
                    z.availIn--
                    z.totalIn++
                    val b2 = z.nextIn!![z.nextInIndex++].toInt() and 0xff
                    val addValue = (b2 shl 8).toLong()
                    z.iState!!.need += addValue
                    ZlibLogger.logInflate("CHECK2: Read byte $b2, adding $addValue, need now = ${z.iState!!.need}")
                    ZlibLogger.logBitwise("leftShift($b2, 8) -> ${b2 shl 8}")
                    z.iState!!.mode = INF_CHECK1
                }

                INF_CHECK1 -> {
                    if (z.availIn == 0) return r
                    r = fMut
                    z.availIn--
                    z.totalIn++
                    val b1 = z.nextIn!![z.nextInIndex++].toInt() and 0xff
                    z.iState!!.need += b1.toLong()
                    ZlibLogger.logInflate("CHECK1: Read final byte $b1, final need = ${z.iState!!.need}")
                    ZlibLogger.logInflate("Checksum verification: computed=${z.iState!!.was[0]}, expected=${z.iState!!.need}")
                    ZlibLogger.logAdler32("Final checksum comparison: was=${z.iState!!.was[0]} vs need=${z.iState!!.need}")
                    
                    if (z.iState!!.was[0] != z.iState!!.need) {
                        z.iState!!.mode = INF_BAD
                        z.msg = "incorrect data check"
                        z.iState!!.marker = 5 // can't try inflateSync
                        ZlibLogger.logInflate("CHECKSUM MISMATCH: computed=${z.iState!!.was[0]}, expected=${z.iState!!.need}")
                        break
                    }
                    ZlibLogger.logInflate("Checksum verification successful - decompression complete")
                    z.iState!!.mode = INF_DONE
                }
                INF_DONE -> return Z_STREAM_END
                INF_BAD -> return Z_DATA_ERROR
                else -> return Z_STREAM_ERROR
            }
        }
        return r
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
    internal fun inflateSetDictionary(z: ZStream, dictionary: ByteArray, dictLength: Int): Int {
        var length = dictLength
        var index = 0
        if (z.iState == null || z.iState!!.mode != INF_DICT0) {
            return Z_STREAM_ERROR
        }

        val adler = z.adlerChecksum!!.adler32(1L, dictionary, 0, length)
        if (adler != z.adler) {
            return Z_DATA_ERROR
        }

        z.adler = z.adlerChecksum!!.adler32(0, null, 0, 0)

        val wsize = bitwiseOps.leftShift(1L, z.iState!!.wbits).toInt()
        if (length >= wsize) {
            length = wsize
            index = dictLength - length
        }

        ZlibLogger.debug("inflateSetDictionary: wsize=$wsize length=$length index=$index")
        z.iState!!.blocks!!.setDictionary(dictionary, index, length)
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
    @OptIn(kotlin.ExperimentalUnsignedTypes::class)
    internal fun inflateSync(z: ZStream?): Int {
        var m: Int // number of marker bytes found in a row

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
        var w: Long = z.totalOut
        inflateReset(z)
        z.totalIn = r
        z.totalOut = w
        z.iState!!.mode = INF_BLOCKS
        ZlibLogger.debug("inflateSync: sync found")
        return Z_OK
    }

}
