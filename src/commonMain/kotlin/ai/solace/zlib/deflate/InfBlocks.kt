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
        ZlibLogger.debug("[RESET_DEBUG] InfBlocks reset: read=$read, write=$write, window size=${window.size}")

        // Reset checksum if we have a checksum function
        if (checkfn != null && z != null) {
            z.adler = Adler32().adler32(0L, null, 0, 0)
            check = z.adler
        }
    }

    // Store the ZStream used by inflateFlush
    private var proc_z: ZStream? = null

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

        ZlibLogger.debug("[PROC_DEBUG] InfBlocks.proc called: mode=$mode, write=$write, read=$read, outputPointer=$outputPointer")

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
                            return inflateFlush(z, returnCode)
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
                            return inflateFlush(z, returnCode)
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
                            return inflateFlush(z, returnCode)
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
                        return inflateFlush(z, returnCode)
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
                        return inflateFlush(z, returnCode)
                    }
                    if (outputBytesLeft == 0) {
                        if (outputPointer == end && read != 0) {
                            outputPointer = 0; outputBytesLeft =
                                if (outputPointer < read) read - outputPointer - 1 else end - outputPointer
                        }
                        if (outputBytesLeft == 0) {
                            write = outputPointer; returnCode = inflateFlush(z); outputPointer = write; outputBytesLeft =
                                if (outputPointer < read) read - outputPointer - 1 else end - outputPointer
                            if (outputPointer == end && read != 0) {
                                outputPointer = 0; outputBytesLeft =
                                    if (outputPointer < read) read - outputPointer - 1 else end - outputPointer
                            }
                            if (outputBytesLeft == 0) {
                                bitb = bitBuffer; bitk = bitsInBuffer; z.availIn =
                                    bytesAvailable; z.totalIn += (inputPointer - z.nextInIndex).toLong(); z.nextInIndex =
                                    inputPointer; write = outputPointer
                                return inflateFlush(z, returnCode)
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
                            return inflateFlush(z, returnCode)
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
                        return inflateFlush(z, returnCode)
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
                                return inflateFlush(z, returnCode)
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
                        return inflateFlush(z, returnCode)
                    }
                    index = 0
                    mode = IBLK_DTREE
                }

                IBLK_DTREE -> {
                    // Initialize state
                    val numLengths = 257 + (table and 0x1f)
                    val numDistances = 1 + ((table ushr 5) and 0x1f)
                    val totalCodes = numLengths + numDistances
                    
                    // Ensure we have space for codes
                    if (totalCodes > 288) {
                        blens = null
                        mode = IBLK_BAD
                        z.msg = "too many length or distance symbols"
                        return inflateFlush(z, Z_DATA_ERROR)
                    }
                    
                    // Process codes
                    while (index < totalCodes) {
                        // Get bits for code
                        val needBits = bb[0]
                        while (bitk < needBits) {
                            if (z.availIn == 0) {
                                bitb = bitBuffer
                                bitk = bitsInBuffer
                                z.availIn = bytesAvailable
                                z.totalIn += (inputPointer - z.nextInIndex).toLong()
                                z.nextInIndex = inputPointer
                                write = outputPointer
                                return inflateFlush(z, Z_OK)
                            }
                            bytesAvailable--
                            bitBuffer = bitBuffer or ((z.nextIn!![inputPointer++].toInt() and 0xff) shl bitsInBuffer)
                            bitsInBuffer += 8
                        }
                        
                        // Extract code
                        val treeBits = hufts[(tb[0][0] + (bitBuffer and IBLK_INFLATE_MASK[needBits])) * 3 + 1]
                        val code = hufts[(tb[0][0] + (bitBuffer and IBLK_INFLATE_MASK[needBits])) * 3 + 2]
                        
                        // Remove used bits
                        bitBuffer = bitBuffer ushr treeBits
                        bitsInBuffer -= treeBits
                        
                        // Process the code
                        if (code < 16) {
                            // Direct length
                            blens!![index++] = code
                        } else {
                            // Repeat length
                            val (extraBits, baseLength) = when (code) {
                                16 -> 2 to 3   // Copy previous
                                17 -> 3 to 3   // Short zero run
                                else -> 7 to 11 // Long zero run
                            }
                            
                            // Get extra bits
                            while (bitsInBuffer < extraBits) {
                                if (bytesAvailable == 0) {
                                    bitb = bitBuffer
                                    bitk = bitsInBuffer
                                    z.availIn = bytesAvailable
                                    z.totalIn += (inputPointer - z.nextInIndex).toLong()
                                    z.nextInIndex = inputPointer
                                    write = outputPointer
                                    return inflateFlush(z, Z_OK)
                                }
                                bytesAvailable--
                                bitBuffer = bitBuffer or ((z.nextIn!![inputPointer++].toInt() and 0xff) shl bitsInBuffer)
                                bitsInBuffer += 8
                            }
                            
                            // Calculate repeat count
                            val repeatCount = baseLength + (bitBuffer and IBLK_INFLATE_MASK[extraBits])
                            bitBuffer = bitBuffer ushr extraBits
                            bitsInBuffer -= extraBits
                            
                            // Check bounds
                            if (index + repeatCount > totalCodes || (code == 16 && index == 0)) {
                                blens = null
                                mode = IBLK_BAD
                                z.msg = "invalid bit length repeat"
                                bitb = bitBuffer
                                bitk = bitsInBuffer
                                z.availIn = bytesAvailable
                                z.totalIn += (inputPointer - z.nextInIndex).toLong()
                                z.nextInIndex = inputPointer
                                write = outputPointer
                                return inflateFlush(z, Z_DATA_ERROR)
                            }
                            
                            // Apply repeat
                            val value = if (code == 16) blens!![index - 1] else 0
                            repeat(repeatCount) {
                                blens!![index++] = value
                            }
                        }
                    }
                    
                    // Initialize trees
                    val bl = IntArray(1).also { it[0] = 9 }  // Max bits for literal/length tree
                    val bd = IntArray(1).also { it[0] = 6 }  // Max bits for distance tree
                    val tl = arrayOf(IntArray(1))  // Literal/length tree result
                    val td = arrayOf(IntArray(1))  // Distance tree result
                    
                    // Build Huffman trees
                    when (val buildResult = InfTree.inflateTreesDynamic(
                        numLengths,      // Number of literal/length codes
                        numDistances,     // Number of distance codes
                        blens!!,         // Bit lengths
                        bl, bd,          // Result: max bits
                        tl, td,          // Result: Huffman trees
                        hufts,           // Working space
                        z                // For messages
                    )) {
                        Z_OK -> {
                            codes = InfCodes(bl[0], bd[0], tl[0], td[0])
                            blens = null  // Free memory
                            mode = IBLK_CODES  // Next state
                        }
                        Z_DATA_ERROR -> {
                            blens = null
                            mode = IBLK_BAD
                            bitb = bitBuffer
                            bitk = bitsInBuffer
                            z.availIn = bytesAvailable
                            z.totalIn += (inputPointer - z.nextInIndex).toLong()
                            z.nextInIndex = inputPointer
                            write = outputPointer
                            return inflateFlush(z, Z_DATA_ERROR)
                        }
                        else -> {
                            bitb = bitBuffer
                            bitk = bitsInBuffer
                            z.availIn = bytesAvailable
                            z.totalIn += (inputPointer - z.nextInIndex).toLong()
                            z.nextInIndex = inputPointer
                            write = outputPointer
                            return inflateFlush(z, buildResult)
                        }
                    }
                }

                IBLK_CODES -> {
                    // Debug log for code processing
                    ZlibLogger.debug("[INFBLOCKS_DEBUG] Processing codes: bitBuffer=$bitBuffer, bitsInBuffer=$bitsInBuffer")

                    // Save bit buffer state
                    bitb = bitBuffer
                    bitk = bitsInBuffer

                    ZlibLogger.debug("[CODES_DEBUG] Before InfCodes.proc: write=$write, outputPointer=$outputPointer")

                    while (true) {
                        val codesResult = codes!!.proc(this, z, returnCode)

                        ZlibLogger.debug("[CODES_DEBUG] After InfCodes.proc: write=$write, codesResult=$codesResult")

                        // Update local variables from object state
                        bitBuffer = bitb
                        bitsInBuffer = bitk

                        var shouldReturn = false
                        var returnValue = returnCode

                        if (codesResult == Z_STREAM_END) {
                            // End of block
                            codes = null
                            mode = if (last != 0) IBLK_DRY else IBLK_TYPE
                            saveState(z, bitBuffer, bitsInBuffer, bytesAvailable, inputPointer, outputPointer)
                            shouldReturn = true
                            returnValue = inflateFlush(z, returnCode)
                        } else {
                            // Not at end of block yet
                            if (codesResult == Z_DATA_ERROR) {
                                // Handle data error
                                mode = IBLK_BAD
                                z.msg = "invalid distance code"
                                result = Z_DATA_ERROR
                                saveState(z, bitBuffer, bitsInBuffer, z.availIn, z.nextInIndex, write)
                                shouldReturn = true
                                returnValue = inflateFlush(z, result)
                            }

                            if (!shouldReturn && codesResult == Z_BUF_ERROR) {
                                // No progress is possible, need more input or output space
                                saveState(z, bitBuffer, bitsInBuffer, z.availIn, z.nextInIndex, write)
                                shouldReturn = true
                                returnValue = inflateFlush(z, codesResult)
                            }

                            // Otherwise we got Z_OK, update our state from the saved values

                            // Make sure we're advancing - if not, we're stuck and need to return
                            if (!shouldReturn && z.availIn == bytesAvailable && z.availOut == z.nextOut!!.size - z.nextOutIndex) {
                                result = Z_BUF_ERROR
                                saveState(z, bitBuffer, bitsInBuffer, z.availIn, z.nextInIndex, write)
                                shouldReturn = true
                                returnValue = inflateFlush(z, result)
                            }

                            // Update our local variables
                            if (!shouldReturn) {
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
                                    shouldReturn = true
                                    returnValue = inflateFlush(z, returnCode)
                                }
                            }
                        }
                        if (shouldReturn) return returnValue
                    }
                }

                IBLK_DRY -> {
                    // Debug log for dry state
                    ZlibLogger.debug("[INFBLOCKS_DEBUG] Processing dry state")

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
                            result = inflateFlush(z)
                            outputPointer = write
                            outputBytesLeft =
                                if (outputPointer < read) read - outputPointer - 1 else end - outputPointer

                            // Handle window wrap-around again if needed
                            if (outputPointer == end && read != 0) {
                                outputPointer = 0
                                outputBytesLeft =
                                    if (outputPointer < read) read - outputPointer - 1 else end - outputPointer
                            }

                            // If still no space, save state and return
                            if (outputBytesLeft == 0) {
                                saveState(z, bitBuffer, bitsInBuffer, bytesAvailable, inputPointer, outputPointer)
                                return inflateFlush(z, result)
                            }
                        }
                    }

                    // Mark as done and set result
                    result = Z_STREAM_END
                    saveState(z, bitBuffer, bitsInBuffer, bytesAvailable, inputPointer, outputPointer)
                    return inflateFlush(z, result)
                }

                IBLK_BAD -> {
                    // Debug log for bad state
                    ZlibLogger.debug("[INFBLOCKS_DEBUG] Processing bad state")
                    result = Z_DATA_ERROR
                    saveState(z, bitBuffer, bitsInBuffer, bytesAvailable, inputPointer, outputPointer)
                    return inflateFlush(z, result)
                }

                else -> {
                    // Debug log for unknown state
                    ZlibLogger.debug("[INFBLOCKS_DEBUG] Unknown state: mode=$mode")
                    result = Z_STREAM_ERROR
                    saveState(z, bitBuffer, bitsInBuffer, bytesAvailable, inputPointer, outputPointer)
                    return inflateFlush(z, result)
                }
            }
        }
    }

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
    private fun inflateFlush(z: ZStream? = proc_z, r: Int = Z_OK): Int {
        return inflateFlush(this, z ?: return Z_STREAM_ERROR, r)
    }

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