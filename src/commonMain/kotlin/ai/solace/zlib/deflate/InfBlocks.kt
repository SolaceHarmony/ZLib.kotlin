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
    companion object {
        // Use the IBLK_INFLATE_MASK from Constants.kt instead of duplicating it
        private val IBLK_INFLATE_MASK = ai.solace.zlib.common.IBLK_INFLATE_MASK

        // Use the IBLK_BORDER from Constants.kt instead of duplicating it
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

    /** One byte after sliding window */
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
     * @param c Array to store the current check value, or null
     */
    fun reset(z: ZStream?, c: LongArray?) {
        if (c != null) c[0] = check
        if (mode == IBLK_BTREE || mode == IBLK_DTREE) {
            blens = null
        }
        if (mode == IBLK_CODES) {
            codes?.free()
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

    /**
     * Processes a block of compressed data.
     * 
     * This is the main method that implements the state machine for decompression.
     * It handles different block types (stored, fixed, and dynamic), manages the bit buffer,
     * and coordinates with InfCodes for actual decompression.
     * 
     * @param z The ZStream containing input data and decompression state
     * @param rIn The initial result status
     * @return Updated result status (Z_OK, Z_STREAM_END, or error code)
     */
    fun proc(z: ZStream?, rIn: Int): Int {
        // Variables to hold local copies of state
        var p: Int         // input data pointer
        var n: Int         // bytes available at input
        var q: Int         // output window write pointer
        var m: Int         // bytes to end of window or read pointer
        var b: Int         // bit buffer
        var k: Int         // bits in bit buffer
        var result: Int = rIn  // current inflate status
        var r: Int = rIn       // working copy of result

        // Initialize local variables from the zstream and blocks state
        p = z!!.nextInIndex
        n = z.availIn
        b = bitb
        k = bitk
        q = write
        m = if (q < read) read - q - 1 else end - q

        // Process input and output based on current state
        processBlocks@ while (true) {
            when (mode) {
                IBLK_TYPE -> {
                    while (k < 3) {
                        if (n != 0) {
                            r = Z_OK
                        } else {
                            bitb = b; bitk = k; z.availIn = n; z.totalIn += (p - z.nextInIndex).toLong(); z.nextInIndex = p; write = q
                            return inflateFlush()
                        }
                        n--
                        b = b or ((z.nextIn!![p++].toInt() and 0xff) shl k)
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
                            InfTree.inflateTreesFixed(bl, bd, tl, td, z)
                            codes = InfCodes(bl[0], bd[0], tl[0], td[0])
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
                            bitb = b; bitk = k; z.availIn = n; z.totalIn += (p - z.nextInIndex).toLong(); z.nextInIndex = p; write = q
                            return inflateFlush()
                        }
                    }
                }
                IBLK_LENS -> {
                    while (k < 32) {
                        if (n != 0) {
                            r = Z_OK
                        } else {
                            bitb = b; bitk = k; z.availIn = n; z.totalIn += (p - z.nextInIndex).toLong(); z.nextInIndex = p; write = q
                            return inflateFlush()
                        }
                        n--
                        b = b or ((z.nextIn!![p++].toInt() and 0xff) shl k)
                        k += 8
                    }
                    if (((b.inv() ushr 16) and 0xffff) != (b and 0xffff)) {
                        mode = IBLK_BAD
                        z.msg = "invalid stored block lengths"
                        r = Z_DATA_ERROR
                        bitb = b; bitk = k; z.availIn = n; z.totalIn += (p - z.nextInIndex).toLong(); z.nextInIndex = p; write = q
                        return inflateFlush()
                    }
                    left = (b and 0xffff)
                    b = 0; k = 0
                    mode = if (left != 0) IBLK_STORED else if (last != 0) IBLK_DRY else IBLK_TYPE
                }
                IBLK_STORED -> {
                    if (n == 0) {
                        bitb = b; bitk = k; z.availIn = n; z.totalIn += (p - z.nextInIndex).toLong(); z.nextInIndex = p; write = q
                        return inflateFlush()
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
                                return inflateFlush()
                            }
                        }
                    }
                    r = Z_OK
                    var t = left
                    if (t > n) t = n
                    if (t > m) t = m
                    z.nextIn!!.copyInto(window, q, p, p + t)
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
                            bitb = b; bitk = k; z.availIn = n; z.totalIn += (p - z.nextInIndex).toLong(); z.nextInIndex = p; write = q
                            return inflateFlush()
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
                        return inflateFlush()
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
                                return inflateFlush()
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
                        return inflateFlush()
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
                                return inflateFlush()
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
                                    return inflateFlush()
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
                                return inflateFlush()
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
                        return inflateFlush()
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
                            bitb = b
                            bitk = k
                            z.availIn = n
                            z.totalIn += (p - z.nextInIndex).toLong()
                            z.nextInIndex = p
                            write = q
                            return inflateFlush()
                        }

                        if (codesResult == Z_BUF_ERROR) {
                            // No progress is possible, need more input or output space
                            bitb = b
                            bitk = k
                            z.availIn = n
                            z.totalIn += (p - z.nextInIndex).toLong()
                            z.nextInIndex = p
                            write = q
                            return inflateFlush()
                        }

                        // Otherwise we got Z_OK, update our state from the saved values

                        // Make sure we're advancing - if not, we're stuck and need to return
                        if (z.availIn == n && z.availOut == z.nextOut!!.size - z.nextOutIndex) {
                            result = Z_BUF_ERROR
                            bitb = b
                            bitk = k
                            z.availIn = n
                            z.totalIn += (p - z.nextInIndex).toLong()
                            z.nextInIndex = p
                            write = q
                            return inflateFlush()
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
                            return inflateFlush()
                        }
                    }
                }

                IBLK_DRY -> {
                    // Check if we need to flush more output
                    if (m == 0) {
                        if (q == end && read != 0) {
                            q = 0
                            m = if (q < read) read - q - 1 else end - q
                        }

                        if (m == 0) {
                            // Flush the last bytes
                            write = q
                            result = inflateFlush()
                            q = write
                            m = if (q < read) read - q - 1 else end - q

                            if (q == end && read != 0) {
                                q = 0
                                m = if (q < read) read - q - 1 else end - q
                            }

                            if (m == 0) {
                                bitb = b
                                bitk = k
                                z.availIn = n
                                z.totalIn += (p - z.nextInIndex).toLong()
                                z.nextInIndex = p
                                write = q
                                return inflateFlush()
                            }
                        }
                    }

                    // Mark as done
                    result = Z_STREAM_END
                    bitb = b
                    bitk = k
                    z.availIn = n
                    z.totalIn += (p - z.nextInIndex).toLong()
                    z.nextInIndex = p
                    write = q
                    return inflateFlush()
                }

                IBLK_BAD -> {
                    result = Z_DATA_ERROR
                    bitb = b
                    bitk = k
                    z.availIn = n
                    z.totalIn += (p - z.nextInIndex).toLong()
                    z.nextInIndex = p
                    write = q
                    return inflateFlush()
                }

                else -> {
                    result = Z_STREAM_ERROR
                    bitb = b
                    bitk = k
                    z.availIn = n
                    z.totalIn += (p - z.nextInIndex).toLong()
                    z.nextInIndex = p
                    write = q
                    return inflateFlush()
                }
            }
        }

        // Update the inflate blocks state
        bitb = b
        bitk = k
        z.availIn = n
        z.totalIn += (p - z.nextInIndex).toLong()
        z.nextInIndex = p
        write = q

        return inflateFlush()
    }

    /**
     * Flushes as much output as possible to the output buffer.
     * 
     * This method copies data from the sliding window to the output buffer,
     * updates checksums, and handles window wrap-around.
     * 
     * @return Updated result status (Z_OK, Z_STREAM_END, Z_BUF_ERROR, or Z_STREAM_ERROR)
     */
    private fun inflateFlush(): Int {
        var n: Int
        var p: Int
        var q: Int
        val z = checkfn as? ZStream ?: return Z_STREAM_ERROR

        // Compute number of bytes to copy
        p = z.nextOutIndex
        q = read

        // Compute first block to copy
        n = if (q <= write) write - q else end - q
        if (n > z.availOut) n = z.availOut
        if (n != 0 && read == write) {
            // Avoid copying zeros
            z.msg = "need more output space"
            return Z_BUF_ERROR
        }

        // Copy the data
        if (n > 0) {
            window.copyInto(z.nextOut!!, p, q, q + n)
            p += n
            q += n
            // Update counters
            z.nextOutIndex = p
            z.totalOut += n.toLong()
            z.availOut -= n
            read = q

            // Update checksums if applicable
            z.adler = (checkfn as Adler32).adler32(check, window, q - n, n)
            check = z.adler
        }

        // See if more to copy at beginning of window
        if (q == end) {
            // Wrap q around to beginning
            q = 0
            if (write == end) {
                write = 0
            }

            // Compute bytes to copy
            n = if (write > q) write - q else 0
            if (n > z.availOut) n = z.availOut

            if (n != 0 && read == write) {
                // Avoid copying zeros
                z.msg = "need more output space"
                return Z_BUF_ERROR
            }

            // Copy the data
            if (n > 0) {
                window.copyInto(z.nextOut!!, p, q, q + n)
                p += n
                q += n
                // Update counters
                z.nextOutIndex = p
                z.totalOut += n.toLong()
                z.availOut -= n
                read = q

                // Update checksums if applicable
                z.adler = (checkfn as Adler32).adler32(check, window, q - n, n)
                check = z.adler
            }
        }

        return if (read != write) Z_OK else Z_STREAM_END
    }

    /**
     * Releases allocated resources.
     * 
     * In the original C implementation, this would free memory.
     * In Kotlin, we rely on garbage collection, but still need to clear references.
     * 
     * @param z The ZStream containing the decompression state
     */
    internal fun free(z: ZStream?) {
        // In the original implementation, this would free memory
        // In Kotlin, we rely on garbage collection, so we just need to clear references
        reset(z, null)
        window.fill(0)
        codes = null
    }

    /**
     * Sets a dictionary for decompression.
     * 
     * This method copies the dictionary into the sliding window and
     * sets up the window pointers appropriately.
     * 
     * @param dictionary The byte array containing the dictionary
     * @param index The starting index in the dictionary
     * @param length The length of the dictionary to use
     */
    internal fun setDictionary(dictionary: ByteArray, index: Int, length: Int) {
        dictionary.copyInto(window, 0, index, index + length)
        read = 0
        write = length
    }

    /**
     * Indicates if inflate is at a synchronization point.
     * 
     * @return 1 if at a synchronization point, 0 otherwise
     */
    internal val syncPoint: Int
        get() = if (mode == IBLK_LENS) 1 else 0
}
