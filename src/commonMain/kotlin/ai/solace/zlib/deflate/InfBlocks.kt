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

    // State variables used in processing
    private var result: Int = Z_OK
    private var outputBytesLeft: Int = 0
    private var outputPointer: Int = 0

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
        c?.let { 
            it[0] = check
        }

        // Clean up resources based on current mode
        when (mode) {
            IBLK_BTREE, IBLK_DTREE -> blens = null
            IBLK_CODES -> codes?.free()
            else -> { /* No cleanup needed for other modes */
            }
        }

        // Reset the state machine
        mode = IBLK_TYPE
        bitk = 0
        bitb = 0
        read = 0
        write = 0

        // Clear the window buffer to prevent leftover data from appearing in output
        window.fill(0)
        println("[RESET_DEBUG] InfBlocks reset: read=$read, write=$write, window size=${window.size}")

        // Reset checksum if we have a checksum function
        if (checkfn != null && z != null) {
            z.adler = Adler32().adler32(0L, null, 0, 0)
            check = z.adler
        }
    }

    // Store the ZStream used by inflateFlush
    private var proc_z: ZStream? = null

    // Removed invalid duplicate state machine code block that was outside any function.

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
        var inputPointer = z.nextInIndex
        var bytesAvailable = z.availIn
        var bitBuffer = bitb
        var bitsInBuffer = bitk
        var outputPointer = write
        var outputBytesLeft = if (outputPointer < read) read - outputPointer - 1 else end - outputPointer
        var result: Int
        var returnCode = rIn

        println("[PROC_DEBUG] InfBlocks.proc called: mode=$mode, write=$write, read=$read, outputPointer=$outputPointer")

        // Process input and output based on current state
        while (true) {
            when (mode) {
                IBLK_TYPE -> {
                    // Need at least 3 bits to determine block type
                    while (bitsInBuffer < 3) {
                        if (bytesAvailable == 0) {
                            bitb = bitBuffer; bitk = bitsInBuffer; z.availIn =
                                bytesAvailable; z.totalIn += (inputPointer - z.nextInIndex).toLong(); z.nextInIndex =
                                inputPointer; write = outputPointer
                            return inflateFlush(returnCode)
                        }
                        bytesAvailable--
                        bitBuffer = bitBuffer or ((z.nextIn!![inputPointer++].toInt() and 0xff) shl bitsInBuffer)
                        bitsInBuffer += 8
                    }

                    // Extract block type info
                    val t = bitBuffer and 7
                    last = t and 1

                    // Consume the block type bits
                    bitBuffer = bitBuffer ushr 3; bitsInBuffer -= 3

                    when (t ushr 1) {
                        0 -> { // Stored block
                            // Skip any remaining bits in current byte
                            val bitsToSkip = bitsInBuffer and 7
                            bitBuffer = bitBuffer ushr bitsToSkip; bitsInBuffer -= bitsToSkip
                            mode = IBLK_LENS
                        }

                        1 -> { // Fixed Huffman block
                            val bl = IntArray(1)
                            val bd = IntArray(1)
                            val tl = arrayOf(IntArray(0))
                            val tlIndex = IntArray(1)
                            val td = arrayOf(IntArray(0))
                            val tdIndex = IntArray(1)
                            InfTree.inflateTreesFixedWithIndices(bl, bd, tl, tlIndex, td, tdIndex, z)
                            codes = InfCodes(bl[0], bd[0], tl[0], tlIndex[0], td[0], tdIndex[0])
                            mode = IBLK_CODES
                        }

                        2 -> { // Dynamic Huffman block
                            mode = IBLK_TABLE
                        }

                        else -> { // Invalid block type
                            mode = IBLK_BAD
                            z.msg = "invalid block type"
                            returnCode = Z_DATA_ERROR
                            bitb = bitBuffer; bitk = bitsInBuffer; z.availIn =
                                bytesAvailable; z.totalIn += (inputPointer - z.nextInIndex).toLong(); z.nextInIndex =
                                inputPointer; write = outputPointer
                            return inflateFlush(returnCode)
                        }
                    }
                }

                IBLK_LENS -> {
                    // Need 32 bits to read block length and check

                    // Ensure we have 32 bits available
                    while (bitsInBuffer < 32) {
                        if (bytesAvailable == 0) {
                            bitb = bitBuffer; bitk = bitsInBuffer; z.availIn =
                                bytesAvailable; z.totalIn += (inputPointer - z.nextInIndex).toLong(); z.nextInIndex =
                                inputPointer; write = outputPointer
                            return inflateFlush(returnCode)
                        }
                        bytesAvailable--
                        bitBuffer = bitBuffer or ((z.nextIn!![inputPointer++].toInt() and 0xff) shl bitsInBuffer)
                        bitsInBuffer += 8
                    }


                    // Extract values for verification using the bit buffer directly
                    val storedLen = bitBuffer and 0xffff


                    // Verify block length integrity: storedNLen should be ~storedLen
                    val invertedValue = (bitBuffer.inv() ushr 16) and 0xffff
                    val originalValue = bitBuffer and 0xffff

                    if (invertedValue != originalValue) {
                        mode = IBLK_BAD
                        z.msg = "invalid stored block lengths"
                        returnCode = Z_DATA_ERROR
                        bitb = bitBuffer; bitk = bitsInBuffer; z.availIn =
                            bytesAvailable; z.totalIn += (inputPointer - z.nextInIndex).toLong(); z.nextInIndex =
                            inputPointer; write = outputPointer
                        return inflateFlush(returnCode)
                    }


                    // Store the length (low 16 bits of b)
                    left = storedLen

                    // Clear bit buffer completely as in C# implementation: b = k = 0; // dump bits
                    bitBuffer = 0
                    bitsInBuffer = 0


                    // Determine next state based on block content
                    mode = if (left != 0) IBLK_STORED else if (last != 0) IBLK_DRY else IBLK_TYPE
                }

                IBLK_STORED -> {
                    if (bytesAvailable == 0) {
                        bitb = bitBuffer; bitk = bitsInBuffer; z.availIn =
                            bytesAvailable; z.totalIn += (inputPointer - z.nextInIndex).toLong(); z.nextInIndex =
                            inputPointer; write = outputPointer
                        return inflateFlush(returnCode)
                    }
                    if (outputBytesLeft == 0) {
                        if (outputPointer == end && read != 0) {
                            outputPointer = 0; outputBytesLeft =
                                if (outputPointer < read) read - outputPointer - 1 else end - outputPointer
                        }
                        if (outputBytesLeft == 0) {
                            write = outputPointer; returnCode = inflateFlush(); outputPointer = write; outputBytesLeft =
                                if (outputPointer < read) read - outputPointer - 1 else end - outputPointer
                            if (outputPointer == end && read != 0) {
                                outputPointer = 0; outputBytesLeft =
                                    if (outputPointer < read) read - outputPointer - 1 else end - outputPointer
                            }
                            if (outputBytesLeft == 0) {
                                bitb = bitBuffer; bitk = bitsInBuffer; z.availIn =
                                    bytesAvailable; z.totalIn += (inputPointer - z.nextInIndex).toLong(); z.nextInIndex =
                                    inputPointer; write = outputPointer
                                return inflateFlush(returnCode)
                            }
                        }
                    }
                    returnCode = Z_OK

                    // Calculate how many bytes to copy in this iteration
                    var t = left
                    if (t > bytesAvailable) t = bytesAvailable
                    if (t > outputBytesLeft) t = outputBytesLeft


                    // Copy data from input directly to output window
                    // In Pascal: zmemcpy(q, p, t);
                    if (t > 0) {
                        // Use array copy for better performance and to match Pascal implementation
                        z.nextIn!!.copyInto(window, outputPointer, inputPointer, inputPointer + t)
                    }

                    // Update pointers and counters
                    inputPointer += t
                    bytesAvailable -= t
                    outputPointer += t
                    outputBytesLeft -= t
                    left -= t


                    // If we've copied all data for this stored block, move to next state
                    if (left == 0) {
                        mode = if (last != 0) IBLK_DRY else IBLK_TYPE
                    }
                }

                IBLK_TABLE -> {
                    while (bitsInBuffer < 14) {
                        if (bytesAvailable != 0) {
                            returnCode = Z_OK
                        } else {
                            bitb = bitBuffer; bitk = bitsInBuffer; z.availIn =
                                bytesAvailable; z.totalIn += (inputPointer - z.nextInIndex).toLong(); z.nextInIndex =
                                inputPointer; write = outputPointer
                            return inflateFlush(returnCode)
                        }
                        bytesAvailable--
                        bitBuffer = bitBuffer or ((z.nextIn!![inputPointer++].toInt() and 0xff) shl bitsInBuffer)
                        bitsInBuffer += 8
                    }
                    table = (bitBuffer and 0x3fff)
                    val t = table
                    if ((t and 0x1f) > 29 || ((t ushr 5) and 0x1f) > 29) {
                        mode = IBLK_BAD
                        z.msg = "too many length or distance symbols"
                        returnCode = Z_DATA_ERROR
                        bitb = bitBuffer; bitk = bitsInBuffer; z.availIn =
                            bytesAvailable; z.totalIn += (inputPointer - z.nextInIndex).toLong(); z.nextInIndex =
                            inputPointer; write = outputPointer
                        return inflateFlush(returnCode)
                    }
                    val totalSymbols = 258 + (t and 0x1f) + ((t ushr 5) and 0x1f)
                    blens = IntArray(totalSymbols)
                    bitBuffer = bitBuffer ushr 14; bitsInBuffer -= 14
                    index = 0
                    mode = IBLK_BTREE
                }

                IBLK_BTREE -> {
                    while (index < 4 + (table ushr 10)) {
                        while (bitsInBuffer < 3) {
                            if (bytesAvailable != 0) {
                                returnCode = Z_OK
                            } else {
                                bitb = bitBuffer; bitk = bitsInBuffer; z.availIn =
                                    bytesAvailable; z.totalIn += (inputPointer - z.nextInIndex).toLong(); z.nextInIndex =
                                    inputPointer; write = outputPointer
                                return inflateFlush(returnCode)
                            }
                            bytesAvailable--
                            bitBuffer = bitBuffer or ((z.nextIn!![inputPointer++].toInt() and 0xff) shl bitsInBuffer)
                            bitsInBuffer += 8
                        }
                        blens!![IBLK_BORDER[index++]] = bitBuffer and 7
                        bitBuffer = bitBuffer ushr 3; bitsInBuffer -= 3
                    }
                    while (index < 19) {
                        blens!![IBLK_BORDER[index++]] = 0
                    }
                    bb[0] = 7
                    val treeResult = InfTree.inflateTreesBits(blens!!, bb, tb, hufts, z)
                    if (treeResult != Z_OK) {
                        returnCode = treeResult
                        if (returnCode == Z_DATA_ERROR) {
                            blens = null
                            mode = IBLK_BAD
                        }
                        bitb = bitBuffer; bitk = bitsInBuffer; z.availIn =
                            bytesAvailable; z.totalIn += (inputPointer - z.nextInIndex).toLong(); z.nextInIndex =
                            inputPointer; write = outputPointer
                        return inflateFlush(returnCode)
                    }
                    index = 0
                    mode = IBLK_DTREE
                }

                IBLK_DTREE -> {
                    val tableConfig = table
                    while (index < 258 + (tableConfig and 0x1f) + ((tableConfig ushr 5) and 0x1f)) {
                        var extraBitsNeeded: Int
                        val codeBitsRequired: Int
                        var codeValue: Int
                        var tempTableBits = bb[0]
                        while (bitsInBuffer < tempTableBits) {
                            if (z.availIn != 0) {
                                returnCode = Z_OK
                            } else {
                                bitb = bitb; bitk = bitk; z.availIn =
                                    z.availIn; z.totalIn += (z.nextInIndex - z.nextInIndex).toLong(); z.nextInIndex =
                                    z.nextInIndex; write = write
                                return inflateFlush(returnCode)
                            }
                            z.availIn--
                            bitb = bitb or ((z.nextIn!![z.nextInIndex++].toInt() and 0xff) shl bitk)
                            bitk += 8
                        }
                        tempTableBits = hufts[(tb[0][0] + (bitb and IBLK_INFLATE_MASK[tempTableBits])) * 3 + 1]
                        codeValue = hufts[(tb[0][0] + (bitb and IBLK_INFLATE_MASK[tempTableBits])) * 3 + 2]
                        if (codeValue < 16) {
                            bitb = bitb ushr tempTableBits; bitk -= tempTableBits
                            blens!![index++] = codeValue
                        } else {
                            extraBitsNeeded = if (codeValue == 18) 7 else codeValue - 14
                            codeBitsRequired = if (codeValue == 18) 11 else 3
                            while (bitsInBuffer < tempTableBits + extraBitsNeeded) {
                                if (z.availIn != 0) {
                                    returnCode = Z_OK
                                } else {
                                    bitb = bitb; bitk = bitk; z.availIn =
                                        z.availIn; z.totalIn += (z.nextInIndex - z.nextInIndex).toLong(); z.nextInIndex =
                                        z.nextInIndex; write = write
                                    return inflateFlush(returnCode)
                                }
                                z.availIn--
                                bitb =
                                    bitb or ((z.nextIn!![z.nextInIndex++].toInt() and 0xff) shl bitk)
                                bitk += 8
                            }
                            bitb = bitb ushr tempTableBits; bitk -= tempTableBits
                            var repeatCount = codeBitsRequired + (bitb and IBLK_INFLATE_MASK[extraBitsNeeded])
                            bitb = bitb ushr extraBitsNeeded; bitk -= extraBitsNeeded
                            extraBitsNeeded = index
                            val tableCheck = table
                            if (extraBitsNeeded + repeatCount > 258 + (tableCheck and 0x1f) + ((tableCheck ushr 5) and 0x1f) || (codeValue == 16 && extraBitsNeeded < 1)) {
                                blens = null
                                mode = IBLK_BAD
                                z.msg = "invalid bit length repeat"
                                returnCode = Z_DATA_ERROR
                                bitb = bitb; bitk = bitk; z.availIn =
                                    z.availIn; z.totalIn += (z.nextInIndex - z.nextInIndex).toLong(); z.nextInIndex =
                                    z.nextInIndex; write = write
                                return inflateFlush(returnCode)
                            }
                            codeValue = if (codeValue == 16) blens!![extraBitsNeeded - 1] else 0
                            do {
                                blens!![extraBitsNeeded++] = codeValue
                            } while (--repeatCount != 0)
                            index = extraBitsNeeded
                        }
                    }
                    tb[0][0] = -1
                    val bl_ = IntArray(1)
                    val bd_ = IntArray(1)
                    val tl_ = arrayOf(IntArray(1))
                    val td_ = arrayOf(IntArray(1))
                    bl_[0] = 9; bd_[0] = 6
                    val finalTableConfig = table
                    val inflateResult = InfTree.inflateTreesDynamic(
                        257 + (finalTableConfig and 0x1f),
                        1 + ((finalTableConfig ushr 5) and 0x1f),
                        blens!!,
                        bl_,
                        bd_,
                        tl_,
                        td_,
                        hufts,
                        z
                    )
                    if (inflateResult != Z_OK) {
                        if (inflateResult == Z_DATA_ERROR) {
                            blens = null
                            mode = IBLK_BAD
                        }
                        returnCode = inflateResult
                        bitb = bitb; bitk = bitsInBuffer; z.availIn =
                            z.availIn; z.totalIn += (inputPointer - z.nextInIndex).toLong(); z.nextInIndex =
                            inputPointer; write = outputPointer
                        return inflateFlush(returnCode)
                    }
                    codes = InfCodes(bl_[0], bd_[0], hufts, tl_[0][0], hufts, td_[0][0])
                    blens = null
                    mode = IBLK_CODES
                }

                IBLK_CODES -> {
                    // Debug log for code processing
                    println("[INFBLOCKS_DEBUG] Processing codes: bitBuffer=$bitb, bitsInBuffer=$bitk")

                    // Save bit buffer state before calling proc
                    bitb = bitb
                    bitk = bitk

                    println("[CODES_DEBUG] Before InfCodes.proc: write=$write, outputPointer=$outputPointer")

                    val codesResult = codes!!.proc(this, z, returnCode)

                    println("[CODES_DEBUG] After InfCodes.proc: write=$write, codesResult=$codesResult")

                    // Restore bit buffer state after proc returns
                    bitb = bitb
                    bitk = bitk

                    if (codesResult == Z_STREAM_END) {
                        // End of block
                        codes = null
                        mode = if (last != 0) IBLK_DRY else IBLK_TYPE
                        break
                    } else {
                        // Not at end of block yet
                        if (codesResult == Z_DATA_ERROR) {
                            // Handle data error
                            mode = IBLK_BAD
                            z.msg = "invalid distance code"
                            result = Z_DATA_ERROR
                            saveState(z, bitb, bitk, z.availIn, z.nextInIndex, write)
                            return inflateFlush(result)
                        }

                        if (codesResult == Z_BUF_ERROR) {
                            // No progress is possible, need more input or output space
                            saveState(z, bitb, bitk, z.availIn, z.nextInIndex, write)
                            return inflateFlush(codesResult)
                        }

                        // Otherwise we got Z_OK, update our state from the saved values

                        // Make sure we're advancing - if not, we're stuck and need to return
                        if (z.availIn == z.availIn && z.availOut == z.nextOut!!.size - z.nextOutIndex) {
                            result = Z_BUF_ERROR
                            saveState(z, bitb, bitk, z.availIn, z.nextInIndex, write)
                            return inflateFlush(result)
                        }

                        // Update our local variables
                        inputPointer = z.nextInIndex
                        bytesAvailable = z.availIn
                        outputPointer = write
                        outputBytesLeft = if (outputPointer < read) read - outputPointer - 1 else end - outputPointer

                        // Check if we need to return for more output space
                        if (outputBytesLeft == 0) {
                            bitb = bitBuffer
                            bitk = bitsInBuffer
                            z.availIn = bytesAvailable
                            z.totalIn += (inputPointer - z.nextInIndex).toLong()
                            z.nextInIndex = inputPointer
                            write = outputPointer
                            return inflateFlush(returnCode)
                        }
                    }
                }

                IBLK_DRY -> {
                    // Debug log for dry state
                    println("[INFBLOCKS_DEBUG] Processing dry state")

                    // Check if we need to flush more output
                    if (outputBytesLeft == 0) {
                        // Handle window wrap-around
                        if (outputPointer == end && read != 0) {
                            outputPointer = 0
                            outputBytesLeft =
                                if (outputPointer < read) read - outputPointer - 1 else end - outputPointer
                        }

                        if (outputBytesLeft == 0) {
                            // Flush the last bytes and recalculate buffer space
                            write = outputPointer
                            result = inflateFlush()
                            outputPointer = write
                            outputBytesLeft =
                                if (outputPointer < read) read - outputPointer - 1 else end - outputPointer

                            // Handle window wrap-around again if needed
                            if (outputPointer == end && read != 0) {
                                outputPointer = 0
                                outputBytesLeft =
                                    if (outputPointer < read) read - outputPointer - 1 else end - outputPointer
                            }

                            // If still no space, return to try again later
                            if (outputBytesLeft == 0) {
                                saveState(z, bitBuffer, bitsInBuffer, bytesAvailable, inputPointer, outputPointer)
                                return inflateFlush(result)
                            }
                        }
                    }

                    // Mark as done and return
                    result = Z_STREAM_END
                    saveState(z, bitBuffer, bitsInBuffer, bytesAvailable, inputPointer, outputPointer)
                    return inflateFlush(result)
                }

                IBLK_BAD -> {
                    // Debug log for bad state
                    println("[INFBLOCKS_DEBUG] Processing bad state")
                    result = Z_DATA_ERROR
                    saveState(z, bitBuffer, bitsInBuffer, bytesAvailable, inputPointer, outputPointer)
                    return inflateFlush(result)
                }

                else -> {
                    // Debug log for unknown state
                    println("[INFBLOCKS_DEBUG] Unknown state: mode=$mode")
                    result = Z_STREAM_ERROR
                    saveState(z, bitBuffer, bitsInBuffer, bytesAvailable, inputPointer, outputPointer)
                    return inflateFlush(result)
                }
            }
        }

        // Update the inflate blocks state and return
        saveState(z, bitBuffer, bitsInBuffer, bytesAvailable, inputPointer, outputPointer)
        return inflateFlush(returnCode)
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
     * @param windowWritePointer Current write position in output window
     * @return true if enough bits are available, false if more input is needed
     */
    internal fun ensureBits(bits: Int, z: ZStream, b: Int, k: Int, n: Int, p: Int, windowWritePointer: Int): Boolean {
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
                write = windowWritePointer
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

    // (Removed duplicate declaration of proc_z)


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
     * Calculates the available output buffer space.
     * Takes into account window wrap-around conditions.
     */
    private fun calculateOutputBytesLeft(): Int {
        return if (write < read) read - write - 1 else end - write
    }

    /**
     * Updates the output bytes left count.
     * This is called after write pointer updates to maintain correct buffer space tracking.
     */
    private fun updateOutputBytesLeft() {
        outputBytesLeft = calculateOutputBytesLeft()
    }

}
