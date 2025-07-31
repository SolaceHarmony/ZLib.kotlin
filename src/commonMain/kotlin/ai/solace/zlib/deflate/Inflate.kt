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
        println("[DEBUG_LOG] Inflate.inflate() called with flush=$f")
        var r: Int
        var b: Int

        if (z == null || z.iState == null || z.nextIn == null) {
            println("[DEBUG_LOG] Inflate.inflate error: z=$z, z.iState=${z?.iState}, z.nextIn=${z?.nextIn}")
            return Z_STREAM_ERROR
        }

        println("[DEBUG_LOG] Initial state: mode=${z.iState!!.mode}, availIn=${z.availIn}, nextInIndex=${z.nextInIndex}, availOut=${z.availOut}")
        
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
                println("[DEBUG_LOG] Too many iterations: $iterations")
                return Z_DATA_ERROR
            }

            println("[DEBUG_LOG] Iteration $iterations: mode=${z.iState!!.mode}, availIn=${z.availIn}, nextInIndex=${z.nextInIndex}, availOut=${z.availOut}")

            // Check for exhausted output buffer before processing
            if (z.availOut == 0 && z.iState!!.mode != INF_BAD) {
                println("[DEBUG_LOG] Output buffer exhausted, returning Z_BUF_ERROR")
                return Z_BUF_ERROR
            }
            
            // Store the current mode for debugging
            val currentMode = z.iState!!.mode

            when (z.iState!!.mode) {
                INF_METHOD -> {
                    println("[DEBUG_LOG] Processing INF_METHOD state")
                    // Need at least 1 byte for this state
                    if (z.availIn < 1) {
                        println("[DEBUG_LOG] Not enough input data for INF_METHOD, returning $r")
                        return r
                    }
                    // Removed unused assignment to r = fMut
                    z.availIn--
                    z.totalIn++
                    try {
                        z.iState!!.method = bitwiseOps.and(z.nextIn!![z.nextInIndex++].toLong(), 0xffL).toInt()
                        println("[DEBUG_LOG] Read method byte: ${z.iState!!.method}")
                    } catch (e: Exception) {
                        println("[DEBUG_LOG] Exception in INF_METHOD: ${e.message}")
                        println("[DEBUG_LOG] nextIn size: ${z.nextIn?.size}, nextInIndex: ${z.nextInIndex}")
                        throw e
                    }
                    if (bitwiseOps.and(z.iState!!.method.toLong(), 0xfL).toInt() != Z_DEFLATED) {
                        z.iState!!.mode = INF_BAD
                        z.msg = "unknown compression method"
                        z.iState!!.marker = 5 // can't try inflateSync
                        println("[DEBUG_LOG] Unknown compression method: ${z.iState!!.method}")
                        break
                    }
                    if ((bitwiseOps.rightShift(z.iState!!.method.toLong(), 4).toInt() + 8) > z.iState!!.wbits) {
                        z.iState!!.mode = INF_BAD
                        z.msg = "invalid window size"
                        z.iState!!.marker = 5 // can't try inflateSync
                        println("[DEBUG_LOG] Invalid window size: ${(bitwiseOps.rightShift(z.iState!!.method.toLong(), 4).toInt() + 8)} > ${z.iState!!.wbits}")
                        break
                    }
                    z.iState!!.mode = INF_FLAG
                    println("[DEBUG_LOG] Transitioning to INF_FLAG state")
                }

                INF_FLAG -> {
                    println("[DEBUG_LOG] Processing INF_FLAG state")
                    // Need at least 1 byte for this state
                    if (z.availIn < 1) {
                        println("[DEBUG_LOG] Not enough input data for INF_FLAG, returning $r")
                        return r
                    }
                    // Removed unused assignment to r = fMut

                    z.availIn--
                    z.totalIn++
                    b = bitwiseOps.and(z.nextIn!![z.nextInIndex++].toLong(), 0xffL).toInt()
                    println("[DEBUG_LOG] Read flag byte: $b (0x${b.toString(16).uppercase()})")

                    if ((bitwiseOps.leftShift(z.iState!!.method.toLong(), 8).toInt() + b) % 31 != 0) {
                        z.iState!!.mode = INF_BAD
                        z.msg = "incorrect header check"
                        z.iState!!.marker = 5 // can't try inflateSync
                        println("[DEBUG_LOG] Incorrect header check, transitioning to INF_BAD state")
                        break
                    }

                    if (bitwiseOps.and(b.toLong(), PRESET_DICT.toLong()).toInt() == 0) {
                        z.iState!!.mode = INF_BLOCKS
                        println("[DEBUG_LOG] No preset dictionary, transitioning to INF_BLOCKS state")
                        // Don't break here, continue to the next iteration with the INF_BLOCKS state
                        continue
                    }
                    z.iState!!.mode = INF_DICT4
                    println("[DEBUG_LOG] Preset dictionary found, transitioning to INF_DICT4 state")
                }

                INF_DICT4 -> {
                    // Need at least 1 byte for this state
                    if (z.availIn < 1) return r
                    z.availIn--
                    z.totalIn++
                    z.iState!!.need = bitwiseOps.leftShift(bitwiseOps.and(z.nextIn!![z.nextInIndex++].toLong(), 0xffL), 24)
                    z.iState!!.mode = INF_DICT3
                }

                INF_DICT3 -> {
                    // Need at least 1 byte for this state
                    if (z.availIn < 1) return r

                    z.availIn--
                    z.totalIn++
                    z.iState!!.need += bitwiseOps.leftShift(bitwiseOps.and(z.nextIn!![z.nextInIndex++].toLong(), 0xffL), 16)
                    z.iState!!.mode = INF_DICT2
                }

                INF_DICT2 -> {
                    // Need at least 1 byte for this state
                    if (z.availIn < 1) return r

                    z.availIn--
                    z.totalIn++
                    z.iState!!.need += bitwiseOps.leftShift(bitwiseOps.and(z.nextIn!![z.nextInIndex++].toLong(), 0xffL), 8)
                    z.iState!!.mode = INF_DICT1
                }

                INF_DICT1 -> {
                    // Need at least 1 byte for this state
                    if (z.availIn < 1) return r

                    z.availIn--
                    z.totalIn++
                    z.iState!!.need += bitwiseOps.and(z.nextIn!![z.nextInIndex++].toLong(), 0xffL)
                    // Store expected Adler-32 checksum from stream
                    z.adler = z.iState!!.need
                    z.iState!!.mode = INF_DICT0
                    ZlibLogger.debug("[DEBUG] INF_DICT1: Calculated Adler checksum: ${z.adler}, Expected: ${z.iState!!.need}")
                    // Signal to caller that a dictionary is needed
                    return Z_NEED_DICT
                }

                INF_DICT0 -> {
                    // Remain in INF_DICT0 until inflateSetDictionary is called and succeeds
                    z.msg = "need dictionary"
                    return Z_STREAM_ERROR
                }

                INF_BLOCKS -> {
                    println("[DEBUG_LOG] Processing INF_BLOCKS state")
                    
                    // Verify blocks is not null before proceeding
                    if (z.iState!!.blocks == null) {
                        println("[DEBUG_LOG] Error: blocks is null in INF_BLOCKS state")
                        z.iState!!.mode = INF_BAD
                        z.msg = "blocks is null"
                        return Z_DATA_ERROR
                    }
                    
                    try {
                        println("[DEBUG_LOG] Calling blocks.proc with r=$r, availIn=${z.availIn}, nextInIndex=${z.nextInIndex}")
                        r = z.iState!!.blocks!!.proc(z, r) // Call proc on blocks
                        println("[DEBUG_LOG] blocks.proc returned r=$r")
                    } catch (e: Exception) {
                        println("[DEBUG_LOG] Exception in blocks.proc: ${e.message}")
                        println("[DEBUG_LOG] Exception stack trace: ${e.stackTraceToString()}")
                        throw e
                    }
                    
                    if (r == Z_DATA_ERROR) {
                        println("[DEBUG_LOG] Z_DATA_ERROR from blocks.proc, msg: ${z.msg}")
                        z.iState!!.mode = INF_BAD
                        z.iState!!.marker = 0 // can try inflateSync
                        // Return Z_DATA_ERROR directly instead of breaking to the end of the loop
                        return Z_DATA_ERROR
                    }
                    if (r == Z_OK) {
                        r = fMut
                        println("[DEBUG_LOG] Changed r to fMut=$fMut")
                    }

                    // Handle output buffer exhaustion
                    if (z.availOut == 0) {
                        println("[DEBUG_LOG] Output buffer exhausted in INF_BLOCKS")
                        // If we can't output any more data but still have input to process
                        // return with buffer error so caller can provide more output space
                        if (z.availIn > 0) {
                            println("[DEBUG_LOG] Still have input data, returning Z_BUF_ERROR")
                            return Z_BUF_ERROR
                        }
                    }

                    if (r != Z_STREAM_END) {
                        println("[DEBUG_LOG] Not at stream end, returning r=$r")
                        return r
                    }
                    
                    println("[DEBUG_LOG] Stream end reached, resetting blocks")
                    r = fMut
                    try {
                        z.iState!!.blocks!!.reset(z, z.iState!!.was)
                    } catch (e: Exception) {
                        println("[DEBUG_LOG] Exception in blocks.reset: ${e.message}")
                        println("[DEBUG_LOG] Exception stack trace: ${e.stackTraceToString()}")
                        throw e
                    }
                    
                    z.iState!!.mode = if (z.iState!!.nowrap != 0) INF_DONE else INF_CHECK4
                    println("[DEBUG_LOG] Transitioning to ${if (z.iState!!.nowrap != 0) "INF_DONE" else "INF_CHECK4"} state")
                }

                INF_CHECK4 -> {
                    // Need at least 2 bytes for this state
                    if (z.availIn < 2) return r
                    r = fMut

                    // Read first byte
                    z.availIn--
                    z.totalIn++
                    
                    // Read second byte
                    z.availIn--
                    z.totalIn++
                    z.iState!!.need = bitwiseOps.and(bitwiseOps.leftShift(z.nextIn!![z.nextInIndex++].toLong(), 24), 0xff000000L)
                    z.iState!!.mode = INF_CHECK3
                }

                INF_CHECK3 -> {
                    // Need at least 1 byte for this state
                    if (z.availIn < 1) return r
                    z.availIn--
                    z.totalIn++
                    z.iState!!.need += bitwiseOps.and(bitwiseOps.leftShift(z.nextIn!![z.nextInIndex++].toLong(), 16), 0x00ff0000L)
                    z.iState!!.mode = INF_CHECK2
                }

                INF_CHECK2 -> {
                    // Need at least 1 byte for this state
                    if (z.availIn < 1) return r
                    z.availIn--
                    z.totalIn++
                    z.iState!!.need += bitwiseOps.and(bitwiseOps.leftShift(z.nextIn!![z.nextInIndex++].toLong(), 8), 0x0000ff00L)
                    z.iState!!.mode = INF_CHECK1
                }

                INF_CHECK1 -> {
                    // Need at least 1 byte for this state
                    if (z.availIn < 1) return r
                    z.availIn--
                    z.totalIn++
                    z.iState!!.need += bitwiseOps.and(z.nextIn!![z.nextInIndex++].toLong(), 0x000000ffL)

                    if (z.iState!!.was[0] != z.iState!!.need) {
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
            
            // After processing a state, check if the mode has changed
            if (z.iState!!.mode != currentMode) {
                println("[DEBUG_LOG] Mode changed from $currentMode to ${z.iState!!.mode}, continuing to next iteration")
                continue  // Continue to the next iteration with the new mode
            } else {
                println("[DEBUG_LOG] Mode unchanged, returning Z_OK")
                return Z_OK
            }
        }
        
        println("[DEBUG_LOG] Exited while loop, returning Z_OK")
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

        ZlibLogger.debug("[DEBUG] Validating Adler-32 checksum: Calculated=${z.adlerChecksum!!.adler32(1L, dictionary, 0, dictLength)}, Expected=${z.adler}")
        if (z.adlerChecksum!!.adler32(1L, dictionary, 0, dictLength) != z.adler) {
            return Z_DATA_ERROR
        }

        z.adler = z.adlerChecksum!!.adler32(0, null, 0, 0)

        val wsize = bitwiseOps.leftShift(1L, z.iState!!.wbits).toInt()
        if (lengthMut >= wsize) {
            lengthMut = wsize
            index = dictLength - lengthMut
        }
        ZlibLogger.debug("[DEBUG] Dictionary content: ${dictionary?.joinToString(", ")}")
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
        return Z_OK
    }

}
