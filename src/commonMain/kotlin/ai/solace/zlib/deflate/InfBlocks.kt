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

/**
 * Handles the processing of compressed data blocks during decompression.
 * 
 * This class is responsible for managing the state machine that processes different
 * types of compressed blocks (stored, fixed, and dynamic), maintains the sliding window
 * for output, and handles checksums.
 * 
 * @param z The ZStream containing the compression state
 * @param checkfn The checksum function (typically Adler32) or null if no checksum
 * @param w The window size (typically 1 << MAX_WBITS)
 */
class InfBlocks(z: ZStream, internal val checkfn: Any?, w: Int) {
    /**
     * Static constants and utility methods for InfBlocks.
     */
    companion object {
        /**
         * Mask array used for bit manipulation in inflate operations.
         * Imported from Constants.kt to avoid duplication.
         */
        private val IBLK_INFLATE_MASK = ai.solace.zlib.common.IBLK_INFLATE_MASK

        /**
         * Border array used for dynamic Huffman tree construction.
         * Imported from Constants.kt to avoid duplication.
         */
        private val IBLK_BORDER = ai.solace.zlib.common.IBLK_BORDER
    }

    /** Current inflate block mode (IBLK_TYPE, IBLK_LENS, etc.) */
    private var mode = IBLK_TYPE

    /** Bytes left to copy for stored block */
    private var left = 0

    /** Table for dynamic blocks */
    private var table = 0

    /** Index into tables */
    private var index = 0

    /** Bit lengths for dynamic block */
    private var blens: IntArray? = null

    /** Bit length tree depth */
    private val bb = IntArray(1)

    /** Bit length decoding tree */
    private val tb = arrayOf(IntArray(1))

    /** InfCodes object for current block */
    private var codes: InfCodes? = null

    /** True if this block is the last block */
    private var last = 0

    /** Bits in bit buffer */
    internal var bitk = 0

    /** Bit buffer */
    internal var bitb = 0

    /** Single malloc for tree space */
    private val hufts: IntArray = IntArray(IBLK_MANY * 3)

    /** Sliding window for output */
    internal val window: ByteArray = ByteArray(w)

    /** One byte after a sliding window */
    internal val end: Int = w

    /** Window read pointer */
    internal var read = 0

    /** Window write pointer */
    internal var write = 0

    /** Check value on output */
    internal var check: Long = 0

    init {
        this.mode = IBLK_TYPE
        reset(z, null)
    }

    /**
     * Resets the inflate blocks state.
     * 
     * This method resets the state machine, bit buffer, and window pointers to prepare
     * for processing a new set of blocks or to recover from an error.
     * 
     * @param z The ZStream containing the decompression state
     * @param c Array to store the current check value, or null if no storage is needed
     */
    fun reset(z: ZStream?, c: LongArray?) {
        // Save check value if requested
        c?.let { it[0] = check }

        // Clean up resources based on current mode
        when (mode) {
            IBLK_BTREE, IBLK_DTREE -> blens = null
            IBLK_CODES -> codes?.free()
            else -> { /* No cleanup needed for other modes */ }
        }

        // Reset the state machine
        mode = IBLK_TYPE
        bitk = 0
        bitb = 0
        read = 0
        write = 0

        // Clear the window buffer to prevent leftover data from appearing in output
        window.fill(0)

        // Reset checksum if we have a checksum function
        if (checkfn != null && z != null) {
            z.adler = Adler32().adler32(0L, null, 0, 0)
            check = z.adler
        }
    }

    /**
     * Processes a block of compressed data.
     * 
     * This is the main method that implements the state machine for decompression.
     * It handles different block types (stored, fixed, and dynamic), manages the bit buffer,
     * and coordinates with InfCodes for actual decompression. The method processes data
     * incrementally and may need to be called multiple times to complete decompression.
     * 
     * @param z The ZStream containing input data and decompression state
     * @param rIn The initial result status code (typically Z_OK)
     * @return Updated result status code:
     *         - Z_OK if more input/output is needed
     *         - Z_STREAM_END if decompression is complete
     *         - Z_DATA_ERROR if input data is corrupted
     *         - Z_BUF_ERROR if buffer space is needed
     *         - Z_STREAM_ERROR if the stream state is inconsistent
     */
    fun proc(z: ZStream?, rIn: Int): Int {
        if (z == null) return Z_STREAM_ERROR

        // Store the ZStream for use by inflateFlush
        proc_z = z

        // Initialize local variables from the zstream and blocks state
        var p = z.nextInIndex
        var n = z.availIn
        var b = bitb
        var k = bitk
        var q = write
        var m = if (q < read) read - q - 1 else end - q
        var result: Int
        var r = rIn

        // Process input and output based on current state
        processBlocks@ while (true) {
            when (mode) {
                IBLK_TYPE -> {
                    // Need at least 3 bits to determine block type
                    if (!ensureBits(3, z, b, k, n, p, q)) {
                        return inflateFlush(r)
                    }

                    // Extract block type info
                    val t = b and 7
                    last = t and 1

                    // Consume the block type bits
                    b = b ushr 3; k -= 3

                    when (t ushr 1) {
                        0 -> { // Stored block
                            // Skip any remaining bits in current byte
                            val t2 = k and 7
                            b = b ushr t2; k -= t2
                            mode = IBLK_LENS
                        }
                        1 -> { // Fixed Huffman block
                            val bl = IntArray(1)
                            val bd = IntArray(1)
                            val tl = arrayOf(IntArray(0))
                            val td = arrayOf(IntArray(0))
                            InfTree.inflateTreesFixed(bl, bd, tl, td, z)
                            codes = InfCodes(bl[0], bd[0], tl[0], td[0])
                            mode = IBLK_CODES
                        }
                        2 -> { // Dynamic Huffman block
                            mode = IBLK_TABLE
                        }
                        else -> { // Invalid block type
                            mode = IBLK_BAD
                            z.msg = "invalid block type"
                            r = Z_DATA_ERROR
                            bitb = b; bitk = k; z.availIn = n; z.totalIn += (p - z.nextInIndex).toLong(); z.nextInIndex = p; write = q
                            return inflateFlush(r)
                        }
                    }
                }
                IBLK_LENS -> {
                    // Need 32 bits to read block length and check
                    println("[DEBUG] IBLK_LENS: Starting block length verification")
                    println("[DEBUG] Initial bit buffer state: b=${b.toString(16)}, k=$k bits")

                    if (!ensureBits(32, z, b, k, n, p, q)) {
                        println("[DEBUG] Not enough bits available, returning")
                        return inflateFlush(r)
                    }

                    // After ensuring bits are available
                    println("[DEBUG] After ensureBits: b=${bitb.toString(16)}, k=$bitk bits")

                    // Extract values for verification
                    val storedLen = bitb and 0xffff
                    val storedNLen = (bitb ushr 16) and 0xffff

                    println("[DEBUG] Block length values:")
                    println("[DEBUG]   storedLen: ${storedLen.toString(16)} (${storedLen})")
                    println("[DEBUG]   storedNLen: ${storedNLen.toString(16)} (${storedNLen})")
                    println("[DEBUG]   storedLen + storedNLen = ${(storedLen + storedNLen).toString(16)} (${storedLen + storedNLen})")
                    println("[DEBUG]   0xFFFF = ${0xffff.toString(16)} (${0xffff})")

                    // Verify block length integrity using the exact logic from Pascal translation
                    if ((((b ushr 16) and 0xffff) xor b) and 0xffff != 0) {
                        println("[DEBUG] Block length check FAILED: storedLen + storedNLen != 0xFFFF")
                        mode = IBLK_BAD
                        z.msg = "invalid stored block lengths"
                        r = Z_DATA_ERROR
                        bitb = b; bitk = k; z.availIn = n; z.totalIn += (p - z.nextInIndex).toLong(); z.nextInIndex = p; write = q
                        return inflateFlush(r)
                    }

                    println("[DEBUG] Block length check PASSED, stored length: $storedLen")

                    // Store the length (low 16 bits of b)
                    left = storedLen

                    // Clear bit buffer completely as in Pascal
                    // In Pascal: k = 0; b = 0;  // Dump bits
                    k = 0
                    b = 0  // Explicitly clear local variables before continuing
                    bitb = 0
                    bitk = 0

                    println("[DEBUG] Cleared bit buffer: b=$bitb, k=$bitk")

                    // Determine next state based on block content
                    mode = if (left != 0) IBLK_STORED else if (last != 0) IBLK_DRY else IBLK_TYPE
                    println("[DEBUG] New mode: $mode")
                }
                IBLK_STORED -> {
                    if (n == 0) {
                        bitb = b; bitk = k; z.availIn = n; z.totalIn += (p - z.nextInIndex).toLong(); z.nextInIndex = p; write = q
                        return inflateFlush(r)
                    }
                    if (m == 0) {
                        if (q == end && read != 0) {
                            q = 0; m = if (q < read) read - q - 1 else end - q
                        }
                        if (m == 0) {
                            write = q; r = inflateFlush(); q = write; m = if (q < read) read - q - 1 else end - q
                            if (q == end && read != 0) {
                                q = 0; m = if (q < read) read - q - 1 else end - q
                            }
                            if (m == 0) {
                                bitb = b; bitk = k; z.availIn = n; z.totalIn += (p - z.nextInIndex).toLong(); z.nextInIndex = p; write = q
                                return inflateFlush(r)
                            }
                        }
                    }
                    r = Z_OK

                    // Calculate how many bytes to copy in this iteration
                    var t = left
                    if (t > n) t = n
                    if (t > m) t = m

                    println("[DEBUG] IBLK_STORED: Processing stored data")
                    println("[DEBUG] left=$t, n=$n, m=$m")

                    // Copy data from input directly to output window
                    // In Pascal: zmemcpy(q, p, t);
                    if (t > 0) {
                        // Copy from the input buffer to the output window
                        for (i in 0 until t) {
                            window[q + i] = z.nextIn!![p + i]
                        }
                    }

                    // Update pointers and counters
                    p += t
                    n -= t
                    q += t
                    m -= t
                    left -= t

                    println("[DEBUG] After copy: left=$left, n=$n, m=$m, p=$p, q=$q")

                    // If we've copied all data for this stored block, move to next state
                    if (left == 0) {
                        mode = if (last != 0) IBLK_DRY else IBLK_TYPE
                        println("[DEBUG] Stored block fully processed, new mode: $mode")
                    }
                }
                IBLK_TABLE -> {
                    while (k < 14) {
                        if (n != 0) {
                            r = Z_OK
                        } else {
                            bitb = b; bitk = k; z.availIn = n; z.totalIn += (p - z.nextInIndex).toLong(); z.nextInIndex = p; write = q
                            return inflateFlush(r)
                        }
                        n--
                        b = b or ((z.nextIn!![p++].toInt() and 0xff) shl k)
                        k += 8
                    }
                    table = (b and 0x3fff)
                    val t = table
                    if ((t and 0x1f) > 29 || ((t ushr 5) and 0x1f) > 29) {
                        mode = IBLK_BAD
                        z.msg = "too many length or distance symbols"
                        r = Z_DATA_ERROR
                        bitb = b; bitk = k; z.availIn = n; z.totalIn += (p - z.nextInIndex).toLong(); z.nextInIndex = p; write = q
                        return inflateFlush(r)
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
                                bitb = b; bitk = k; z.availIn = n; z.totalIn += (p - z.nextInIndex).toLong(); z.nextInIndex = p; write = q
                                return inflateFlush(r)
                            }
                            n--
                            b = b or ((z.nextIn!![p++].toInt() and 0xff) shl k)
                            k += 8
                        }
                        blens!![IBLK_BORDER[index++]] = b and 7
                        b = b ushr 3; k -= 3
                    }
                    while (index < 19) {
                        blens!![IBLK_BORDER[index++]] = 0
                    }
                    bb[0] = 7
                    val treeResult = InfTree.inflateTreesBits(blens!!, bb, tb, hufts, z)
                    if (treeResult != Z_OK) {
                        r = treeResult
                        if (r == Z_DATA_ERROR) {
                            blens = null
                            mode = IBLK_BAD
                        }
                        bitb = b; bitk = k; z.availIn = n; z.totalIn += (p - z.nextInIndex).toLong(); z.nextInIndex = p; write = q
                        return inflateFlush(r)
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
                                bitb = b; bitk = k; z.availIn = n; z.totalIn += (p - z.nextInIndex).toLong(); z.nextInIndex = p; write = q
                                return inflateFlush(r)
                            }
                            n--
                            b = b or ((z.nextIn!![p++].toInt() and 0xff) shl k)
                            k += 8
                        }
                        t2 = hufts[(tb[0][0] + (b and IBLK_INFLATE_MASK[t2])) * 3 + 1]
                        c = hufts[(tb[0][0] + (b and IBLK_INFLATE_MASK[t2])) * 3 + 2]
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
                                    bitb = b; bitk = k; z.availIn = n; z.totalIn += (p - z.nextInIndex).toLong(); z.nextInIndex = p; write = q
                                    return inflateFlush(r)
                                }
                                n--
                                b = b or ((z.nextIn!![p++].toInt() and 0xff) shl k)
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
                                bitb = b; bitk = k; z.availIn = n; z.totalIn += (p - z.nextInIndex).toLong(); z.nextInIndex = p; write = q
                                return inflateFlush(r)
                            }
                            c = if (c == 16) blens!![i - 1] else 0
                            do {
                                blens!![i++] = c
                            } while (--j2 != 0)
                            index = i
                        }
                    }
                    tb[0][0] = -1
                    val bl_ = IntArray(1)
                    val bd_ = IntArray(1)
                    val tl_ = arrayOf(IntArray(1))
                    val td_ = arrayOf(IntArray(1))
                    bl_[0] = 9; bd_[0] = 6
                    val t4 = table
                    val inflateResult = InfTree.inflateTreesDynamic(257 + (t4 and 0x1f), 1 + ((t4 ushr 5) and 0x1f), blens!!, bl_, bd_, tl_, td_, hufts, z)
                    if (inflateResult != Z_OK) {
                        if (inflateResult == Z_DATA_ERROR) {
                            blens = null
                            mode = IBLK_BAD
                        }
                        r = inflateResult
                        bitb = b; bitk = k; z.availIn = n; z.totalIn += (p - z.nextInIndex).toLong(); z.nextInIndex = p; write = q
                        return inflateFlush(r)
                    }
                    codes = InfCodes(bl_[0], bd_[0], hufts, tl_[0][0], hufts, td_[0][0])
                    blens = null
                    mode = IBLK_CODES
                }
                IBLK_CODES -> {
                    // Save bit buffer state before calling proc
                    bitb = b
                    bitk = k

                    val codesResult = codes!!.proc(this, z, r)

                    // Restore bit buffer state after proc returns
                    b = bitb
                    k = bitk

                    if (codesResult == Z_STREAM_END) {
                        // End of block
                        codes = null
                        mode = if (last != 0) IBLK_DRY else IBLK_TYPE
                        break@processBlocks
                    } else {
                        // Not at end of block yet
                        if (codesResult == Z_DATA_ERROR) {
                            // Handle data error
                            mode = IBLK_BAD
                            z.msg = "invalid distance code"
                            result = Z_DATA_ERROR
                            saveState(z, b, k, n, p, q)
                            return inflateFlush(result)
                        }

                        if (codesResult == Z_BUF_ERROR) {
                            // No progress is possible, need more input or output space
                            saveState(z, b, k, n, p, q)
                            return inflateFlush(codesResult)
                        }

                        // Otherwise we got Z_OK, update our state from the saved values

                        // Make sure we're advancing - if not, we're stuck and need to return
                        if (z.availIn == n && z.availOut == z.nextOut!!.size - z.nextOutIndex) {
                            result = Z_BUF_ERROR
                            saveState(z, b, k, n, p, q)
                            return inflateFlush(result)
                        }

                        // Update our local variables
                        p = z.nextInIndex
                        n = z.availIn
                        q = write
                        m = if (q < read) read - q - 1 else end - q

                        // Check if we need to return for more output space
                        if (m == 0) {
                            bitb = b
                            bitk = k
                            z.availIn = n
                            z.totalIn += (p - z.nextInIndex).toLong()
                            z.nextInIndex = p
                            write = q
                            return inflateFlush(r)
                        }
                    }
                }

                IBLK_DRY -> {
                    // Check if we need to flush more output
                    if (m == 0) {
                        // Handle window wrap-around
                        if (q == end && read != 0) {
                            q = 0
                            m = if (q < read) read - q - 1 else end - q
                        }

                        if (m == 0) {
                            // Flush the last bytes and recalculate buffer space
                            write = q
                            result = inflateFlush()
                            q = write
                            m = if (q < read) read - q - 1 else end - q

                            // Handle window wrap-around again if needed
                            if (q == end && read != 0) {
                                q = 0
                                m = if (q < read) read - q - 1 else end - q
                            }

                            // If still no space, return to try again later
                            if (m == 0) {
                                saveState(z, b, k, n, p, q)
                                return inflateFlush(result)
                            }
                        }
                    }

                    // Mark as done and return
                    result = Z_STREAM_END
                    saveState(z, b, k, n, p, q)
                    return inflateFlush(result)
                }

                IBLK_BAD -> {
                    result = Z_DATA_ERROR
                    saveState(z, b, k, n, p, q)
                    return inflateFlush(result)
                }

                else -> {
                    result = Z_STREAM_ERROR
                    saveState(z, b, k, n, p, q)
                    return inflateFlush(result)
                }
            }
        }

        // Update the inflate blocks state and return
        saveState(z, b, k, n, p, q)
        return inflateFlush(r)
    }

    /**
     * Flushes as much output as possible to the output buffer.
     * 
     * This method copies data from the sliding window to the output buffer,
     * updates checksums, and handles window wrap-around.
     * 
     * @return Updated result status (Z_OK, Z_STREAM_END, Z_BUF_ERROR, or Z_STREAM_ERROR)
     */
                 /**
                  * Ensures that the specified number of bits are available in the bit buffer.
                  * 
                  * This method reads bytes from the input stream as needed to fill the bit buffer
                  * with at least the specified number of bits. It's used throughout the decompression
                  * process to ensure enough bits are available for various decoding operations.
                  * 
                  * @param bits Number of bits needed in the buffer
                  * @param z ZStream containing input data
                  * @param b Current bit buffer value
                  * @param k Current number of bits in buffer
                  * @param n Available bytes in input
                  * @param p Current input index
                  * @param q Current write position in output window
                  * @return true if enough bits are available, false if more input is needed
                  */
            private fun ensureBits(bits: Int, z: ZStream, b: Int, k: Int, n: Int, p: Int, q: Int): Boolean {
        var bLocal = b
        var kLocal = k
        var nLocal = n
        var pLocal = p

        while (kLocal < bits) {
            if (nLocal == 0) {
                // Not enough input available, save state and return
                bitb = bLocal
                bitk = kLocal
                z.availIn = nLocal
                z.totalIn += (pLocal - z.nextInIndex).toLong()
                z.nextInIndex = pLocal
                write = q
                return false
            }
            nLocal--
            bLocal = bLocal or ((z.nextIn!![pLocal++].toInt() and 0xff) shl kLocal)
            kLocal += 8
        }

        // Update passed values with our locals
        bitb = bLocal
        bitk = kLocal
        z.availIn = nLocal
        z.nextInIndex = pLocal

        return true
            }

                 /**
                  * Saves the current processing state to allow returning to the caller.
                  * 
                  * This helper method updates the ZStream and InfBlocks state variables
                  * before returning from the processing loop. It ensures that all state
                  * is properly synchronized between the local variables and object fields.
                  * 
                  * @param z The ZStream to update with current state
                  * @param b Current bit buffer value
                  * @param k Current number of bits in buffer
                  * @param n Available input bytes remaining
                  * @param p Current input position in the buffer
                  * @param q Current output position in the window
                  */
            private fun saveState(z: ZStream, b: Int, k: Int, n: Int, p: Int, q: Int) {
        bitb = b
        bitk = k
        z.availIn = n
        z.totalIn += (p - z.nextInIndex).toLong()
        z.nextInIndex = p
        write = q
            }

    /**
     * Flushes as much output as possible to the output buffer.
     * 
     * This method copies data from the sliding window to the output buffer,
      * updates checksums, and handles window wrap-around. It's called at the end
      * of processing blocks or when more output space is needed.
      * 
      * @param r The result code to return (typically Z_OK, Z_STREAM_END, Z_DATA_ERROR, etc.)
      * @return Updated result status code:
      *         - Z_OK if data remains to be flushed
      *         - Z_STREAM_END if all data has been flushed
      *         - Z_BUF_ERROR if the output buffer is full
      *         - Z_DATA_ERROR if input data is corrupted
      *         - Z_STREAM_ERROR if the ZStream is invalid
     */
    private fun inflateFlush(r: Int = Z_OK): Int {
        // Get the ZStream from the proc method
        val z = proc_z ?: return Z_STREAM_ERROR

        // If we have an error code other than Z_BUF_ERROR, return it after flushing
        if (r != Z_OK && r != Z_STREAM_END && r != Z_BUF_ERROR) {
            return r
        }

        // Initialize positions
        var p = z.nextOutIndex
        var q = read

        // Check if we need to handle window wrap-around
        if (q == end) {
            q = 0
            if (write == end) write = 0
        }

        // Calculate available output space
        var n = if (q <= write) write - q else end - q
        if (n > z.availOut) n = z.availOut

        // Error if attempting to copy from empty window
        if (n != 0 && read == write) {
            z.msg = "need more output space"
            return Z_BUF_ERROR
        }

        // Update counters
        z.availOut -= n
        z.totalOut += n.toLong()

        // Update checksum if applicable
        if (checkfn != null && n > 0) {
            z.adler = z.adlerChecksum!!.adler32(check, window, q, n)
            check = z.adler
        }

        // Copy data to output buffer
        if (n > 0) {
            window.copyInto(z.nextOut!!, p, q, q + n)
            p += n
            q += n
        }

        // Handle window wrap-around if needed
        if (q == end) {
            q = 0
            if (write == end) write = 0

            // Calculate available bytes after wrap-around
            n = if (write > q) write - q else 0
            if (n > z.availOut) n = z.availOut

            if (n != 0 && read == write) {
                z.msg = "need more output space"
                return Z_BUF_ERROR
            }

            // Update counters for wrap-around data
            z.availOut -= n
            z.totalOut += n.toLong()

            // Update checksum if applicable
            if (checkfn != null && n > 0) {
                z.adler = z.adlerChecksum!!.adler32(check, window, q, n)
                check = z.adler
            }

            // Copy remaining data after wrap-around
            if (n > 0) {
                window.copyInto(z.nextOut!!, p, q, q + n)
                p += n
                q += n
            }
        }

        // Update output index
        z.nextOutIndex = p
        read = q

        // Return status based on whether all data has been processed
        return if (read != write) Z_OK else Z_STREAM_END
    }

    // Store the ZStream from the proc method
    private var proc_z: ZStream? = null


    /**
     * Releases allocated resources used by this InfBlocks instance.
     * 
     * While Kotlin handles memory management through garbage collection, this method
     * explicitly clears references and sensitive data to ensure proper cleanup and
     * prevent potential memory leaks or security issues.
     * 
     * @param z The ZStream containing the decompression state
     */
    internal fun free(z: ZStream?) {
        reset(z, null)
        window.fill(0)  // Clear window data for security/memory reasons
        codes = null    // Release references to any InfCodes objects
    }

    /**
     * Sets a dictionary for decompression.
     * 
     * This method initializes the sliding window with a preset dictionary,
     * which can improve compression ratios for certain types of data. It copies
     * the dictionary content into the window and sets up pointers appropriately.
     * 
     * @param dictionary The byte array containing the preset dictionary data
     * @param index The starting index in the dictionary array
     * @param length The number of bytes to use from the dictionary
     */
    internal fun setDictionary(dictionary: ByteArray, index: Int, length: Int) {
        // Copy dictionary content to window with bounds checking
        val actualLength = minOf(length, window.size)
        dictionary.copyInto(
            destination = window,
            destinationOffset = 0,
            startIndex = index,
            endIndex = index + actualLength
        )
        read = 0
        write = actualLength
    }

    /**
     * Indicates if the inflate process is currently at a synchronization point.
     * 
     * Synchronization points allow a decompressor to resynchronize after an error.
     * This property returns a value that indicates whether the current state is
     * at such a synchronization boundary.
     * 
     * @return 1 if at a synchronization point, 0 otherwise
     */
    internal val syncPoint: Int
        get() = if (mode == IBLK_LENS) 1 else 0
}
