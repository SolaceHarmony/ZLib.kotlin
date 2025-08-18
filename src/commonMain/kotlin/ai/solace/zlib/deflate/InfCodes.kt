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
import ai.solace.zlib.bitwise.ArithmeticBitwiseOps

internal class InfCodes {
    private val bitwiseOps = ArithmeticBitwiseOps.BITS_32

    private var mode: Int = 0 // current inflate_codes mode

    // mode dependent information
    private var length: Int = 0

    private var tree: IntArray = IntArray(0)
    private var treeIndex: Int = 0
    private var bitsNeeded: Int = 0 // bits needed

    private var literal: Int = 0

    // if EXT or COPY, where and how much
    private var extraBitsNeeded: Int = 0 // bits to get for extra
    private var distance: Int = 0 // distance back to copy from

    private var lbits: Byte = 0 // ltree bits decoded per branch
    private var dbits: Byte = 0 // dtree bits decoder per branch
    private var ltree: IntArray = IntArray(0)
    private var ltreeIndex: Int = 0 // literal/length/eob tree
    private var dtree: IntArray = IntArray(0)
    private var dtreeIndex: Int = 0 // distance tree

    constructor(bl: Int, bd: Int, tl: IntArray, tlIndex: Int, td: IntArray, tdIndex: Int) {
        mode = ICODES_START
        lbits = bl.toByte()
        dbits = bd.toByte()
        ltree = tl
        ltreeIndex = tlIndex
        dtree = td
        dtreeIndex = tdIndex
    }

    constructor(bl: Int, bd: Int, tl: IntArray, td: IntArray) {
        mode = ICODES_START
        lbits = bl.toByte()
        dbits = bd.toByte()
        ltree = tl
        ltreeIndex = 0
        dtree = td
        dtreeIndex = 0
    }

    /**
     * Process literal/length/distance codes
     */
    internal fun proc(s: InfBlocks, z: ZStream, r: Int): Int {
        var result = r
        var tempStorage: Int // temporary storage
        var tableIndex: Int // temporary pointer
        var extraBitsOrOperation: Int // extra bits or operation
        var bitBuffer: Int // bit buffer
        var bitsInBuffer: Int // bits in bit buffer
        var inputPointer: Int // input data pointer
        var bytesAvailable: Int // bytes available there
        var outputWritePointer: Int // output window write pointer
        var outputBytesLeft: Int // bytes to end of window or read pointer
        var outputPointer: Int // pointer to copy strings from

        // copy input/output information to locals (UPDATE macro restores)
        inputPointer = z.nextInIndex
        bytesAvailable = z.availIn
        bitBuffer = s.bitb
        bitsInBuffer = s.bitk
        outputWritePointer = s.write
        outputBytesLeft = if (outputWritePointer < s.read) s.read - outputWritePointer - 1 else s.end - outputWritePointer

        // Safety check: max iterations to prevent infinite loops
        val maxIterations = 10000
        var iterationCount = 0

        ZlibLogger.log("[DEBUG_LOG] InfCodes.proc() started. Initial mode: $mode")

        // process input and output based on current state
        while (true) {
            iterationCount++

            // Safety check to prevent hanging
            if (iterationCount > maxIterations) {
                z.msg = "Too many iterations in InfCodes.proc, possible corrupt data"
                ZlibLogger.log("[DEBUG_LOG] InfCodes.proc() - Too many iterations: $iterationCount")
                return Z_DATA_ERROR
            }

            val currentMode = mode // Store current mode for logging
            ZlibLogger.log("[DEBUG_LOG] InfCodes.proc() - Iteration $iterationCount, Mode: $mode, availIn: $bytesAvailable, bitsInBuffer: $bitsInBuffer")

            when (mode) {

                // waiting for "i:"=input, "o:"=output, "x:"=nothing
                ICODES_START -> { // x: set up for LEN
                    bitsNeeded = lbits.toInt()
                    tree = ltree
                    treeIndex = ltreeIndex
                    mode = ICODES_LEN
                    ZlibLogger.log("[DEBUG_LOG] InfCodes.proc() - ICODES_START: Transitioning to ICODES_LEN. bitsNeeded=$bitsNeeded, treeIndex=$treeIndex")

                    // Temporarily disable fast path to use slow path only
                    // Fall through to ICODES_LEN state
                    if (false && outputBytesLeft >= 258 && bytesAvailable >= 10) {
                        ZlibLogger.log("[DEBUG_LOG] InfCodes.proc() - ICODES_START: Calling inflateFast. outputBytesLeft=$outputBytesLeft, bytesAvailable=$bytesAvailable")
                        s.bitb = bitBuffer
                        s.bitk = bitsInBuffer
                        z.availIn = bytesAvailable
                        z.totalIn += inputPointer - z.nextInIndex
                        z.nextInIndex = inputPointer
                        s.write = outputWritePointer
                        result = inflateFast(lbits.toInt(), dbits.toInt(), ltree, ltreeIndex, dtree, dtreeIndex, s, z)
                        inputPointer = z.nextInIndex
                        bytesAvailable = z.availIn
                        bitBuffer = s.bitb
                        bitsInBuffer = s.bitk
                        outputWritePointer = s.write
                        outputBytesLeft = if (outputWritePointer < s.read) s.read - outputWritePointer - 1 else s.end - outputWritePointer
                        ZlibLogger.log("[DEBUG_LOG] InfCodes.proc() - inflateFast returned: $result. availIn=$bytesAvailable, bitsInBuffer=$bitsInBuffer")
                        if (result != Z_OK) {
                            mode = if (result == Z_STREAM_END) ICODES_WASH else ICODES_BADCODE
                            ZlibLogger.log("[DEBUG_LOG] InfCodes.proc() - inflateFast resulted in non-OK: new mode=$mode")
                            continue  // Changed from break to continue
                        }
                    }

                    // Get the bits needed for the literal/length code
                    tempStorage = bitsNeeded

                    // Ensure we have enough bits
                    while (bitsInBuffer < tempStorage) {
                        ZlibLogger.log("[DEBUG_LOG] InfCodes.proc() - ICODES_START/LEN: Need more bits. bitsInBuffer=$bitsInBuffer, tempStorage=$tempStorage")
                        if (bytesAvailable != 0) result = Z_OK
                        else {
                            ZlibLogger.log("[DEBUG_LOG] InfCodes.proc() - ICODES_START/LEN: No more bytes available, returning Z_BUF_ERROR")
                            s.bitb = bitBuffer
                            s.bitk = bitsInBuffer
                            z.availIn = bytesAvailable
                            z.totalIn += inputPointer - z.nextInIndex
                            z.nextInIndex = inputPointer
                            s.write = outputWritePointer
                            return inflateFlush(s, z, result)
                        }
                        bytesAvailable--
                        ZlibLogger.log("[BITWISE_DEBUG] InfCodes START loading byte: ${bitwiseOps.and(z.nextIn!![inputPointer].toLong(), 0xffL)}")
                        bitBuffer = bitwiseOps.or(bitBuffer.toLong(), bitwiseOps.leftShift(bitwiseOps.and(z.nextIn!![inputPointer++].toLong(), 0xffL), bitsInBuffer)).toInt()
                        bitsInBuffer += 8
                    }

                    // Get the table index
                    tableIndex = (treeIndex + bitwiseOps.and(bitBuffer.toLong(), IBLK_INFLATE_MASK[tempStorage].toLong()).toInt()) * 3

                    ZlibLogger.log("[DEBUG_LOG] InfCodes.proc() - ICODES_START/LEN: tableIndex=$tableIndex, treeIndex=$treeIndex, bitBuffer=0x${bitBuffer.toString(16)}, mask=0x${IBLK_INFLATE_MASK[tempStorage].toString(16)}")
                    ZlibLogger.log("[DEBUG_LOG] InfCodes.proc() - ICODES_START/LEN: tree.size=${tree.size}, checking bounds for tableIndex=$tableIndex")

                    if (tableIndex < 0 || tableIndex + 2 >= tree.size) {
                        ZlibLogger.log("[DEBUG_LOG] InfCodes.proc() - ICODES_START/LEN: ERROR - tableIndex=$tableIndex out of bounds for tree.size=${tree.size}")
                        z.msg = "Array index out of bounds"
                        return Z_DATA_ERROR
                    }

                    ZlibLogger.log("[DEBUG_LOG] InfCodes.proc() - ICODES_START/LEN: tree[${tableIndex}]=${tree[tableIndex]}, tree[${tableIndex+1}]=${tree[tableIndex+1]}, tree[${tableIndex+2}]=${tree[tableIndex+2]}")

                    // Remove the bits we've used
                    bitBuffer = bitwiseOps.rightShift(bitBuffer.toLong(), tree[tableIndex + 1]).toInt()
                    bitsInBuffer -= tree[tableIndex + 1]

                    // Get the operation/extra bits
                    extraBitsOrOperation = tree[tableIndex]

                    ZlibLogger.log("[DEBUG_LOG] InfCodes.proc() - ICODES_START/LEN: After consuming ${tree[tableIndex + 1]} bits: bitBuffer=0x${bitBuffer.toString(16)}, bitsInBuffer=$bitsInBuffer, op=$extraBitsOrOperation")

                    // Process based on the operation
                    if (extraBitsOrOperation == 0) {
                        // Literal
                        literal = tree[tableIndex + 2]
                        ZlibLogger.log("[DEBUG_LOG] InfCodes.proc() - ICODES_START/LEN: Found literal=$literal (ASCII '${literal.toChar()}'), switching to ICODES_LIT")
                        mode = ICODES_LIT
                        continue  // Continue to process ICODES_LIT state
                    }

                    if (bitwiseOps.and(extraBitsOrOperation.toLong(), 16L).toInt() != 0) {
                        // Length
                        extraBitsNeeded = bitwiseOps.and(extraBitsOrOperation.toLong(), 15L).toInt()
                        length = tree[tableIndex + 2]
                        mode = ICODES_LENEXT
                        ZlibLogger.log("[DEBUG_LOG] InfCodes.proc() - ICODES_START/LEN: Found length code. extraBitsNeeded=$extraBitsNeeded, length=$length. Transitioning to ICODES_LENEXT")
                        continue  // Continue to process ICODES_LENEXT state
                    }

                    if (bitwiseOps.and(extraBitsOrOperation.toLong(), 32L).toInt() != 0) {
                        // End of block
                        mode = ICODES_WASH
                        ZlibLogger.log("[DEBUG_LOG] InfCodes.proc() - ICODES_START/LEN: End of block found. Transitioning to ICODES_WASH")
                        continue  // Continue to process ICODES_WASH state
                    }

                    if (bitwiseOps.and(extraBitsOrOperation.toLong(), 64L).toInt() == 0) {
                        // Next table
                        bitsNeeded = extraBitsOrOperation
                        treeIndex = tableIndex / 3 + tree[tableIndex + 2]
                        ZlibLogger.log("[DEBUG_LOG] InfCodes.proc() - ICODES_START/LEN: Next table reference. bitsNeeded=$bitsNeeded, treeIndex=$treeIndex")
                        continue  // Continue with new table reference
                    }

                    // Invalid code
                    mode = ICODES_BADCODE
                    z.msg = "invalid literal/length code"
                    result = Z_DATA_ERROR
                    ZlibLogger.log("[DEBUG_LOG] InfCodes.proc() - ICODES_START/LEN: Invalid literal/length code. Error: ${z.msg}")

                    s.bitb = bitBuffer
                    s.bitk = bitsInBuffer
                    z.availIn = bytesAvailable
                    z.totalIn += inputPointer - z.nextInIndex
                    z.nextInIndex = inputPointer
                    s.write = outputWritePointer
                    return inflateFlush(s, z, result)
                }

                ICODES_LEN -> {
                    ZlibLogger.log("[DEBUG_LOG] InfCodes.proc() - ICODES_LEN: using slow path (fast path temporarily disabled)")
                    // Skip inflateFast call to avoid OOB while stabilizing table semantics
                    // if (outputBytesLeft >= 258 && bytesAvailable >= 10) { ... }

                    // Get the bits needed for the literal/length code
                    tempStorage = bitsNeeded

                    // Ensure we have enough bits
                    while (bitsInBuffer < tempStorage) {
                        if (bytesAvailable != 0) result = Z_OK
                        else {
                            s.bitb = bitBuffer
                            s.bitk = bitsInBuffer
                            z.availIn = bytesAvailable
                            z.totalIn += inputPointer - z.nextInIndex
                            z.nextInIndex = inputPointer
                            s.write = outputWritePointer
                            return inflateFlush(s, z, result)
                        }
                        bytesAvailable--
                        bitBuffer = bitwiseOps.or(bitBuffer.toLong(), bitwiseOps.leftShift(bitwiseOps.and(z.nextIn!![inputPointer++].toLong(), 0xffL), bitsInBuffer)).toInt()
                        bitsInBuffer += 8
                    }

                    // Get the table index
                    tableIndex = (treeIndex + bitwiseOps.and(bitBuffer.toLong(), IBLK_INFLATE_MASK[tempStorage].toLong()).toInt()) * 3

                    // Remove the bits we've used
                    bitBuffer = bitwiseOps.rightShift(bitBuffer.toLong(), tree[tableIndex + 1]).toInt()
                    bitsInBuffer -= tree[tableIndex + 1]

                    // Get the operation/extra bits
                    extraBitsOrOperation = tree[tableIndex]

                    // Process based on the operation
                    if (extraBitsOrOperation == 0) {
                        // Literal
                        literal = tree[tableIndex + 2]
                        mode = ICODES_LIT
                        continue  // Continue to process ICODES_LIT state
                    }

                    if (bitwiseOps.and(extraBitsOrOperation.toLong(), 16L).toInt() != 0) {
                        // Length
                        extraBitsNeeded = bitwiseOps.and(extraBitsOrOperation.toLong(), 15L).toInt()
                        length = tree[tableIndex + 2]
                        mode = ICODES_LENEXT
                        continue  // Continue to process ICODES_LENEXT state
                    }

                    if (bitwiseOps.and(extraBitsOrOperation.toLong(), 32L).toInt() != 0) {
                        // End of block
                        mode = ICODES_WASH
                        continue  // Continue to process ICODES_WASH state
                    }

                    if (bitwiseOps.and(extraBitsOrOperation.toLong(), 64L).toInt() == 0) {
                        // Next table
                        bitsNeeded = extraBitsOrOperation
                        treeIndex = tableIndex / 3 + tree[tableIndex + 2]
                        continue  // Continue with new table reference
                    }

                    // Invalid code
                    mode = ICODES_BADCODE
                    z.msg = "invalid literal/length code"
                    result = Z_DATA_ERROR

                    s.bitb = bitBuffer
                    s.bitk = bitsInBuffer
                    z.availIn = bytesAvailable
                    z.totalIn += inputPointer - z.nextInIndex
                    z.nextInIndex = inputPointer
                    s.write = outputWritePointer
                    return inflateFlush(s, z, result)
                }

                ICODES_LENEXT -> {  // i: getting length extra (have base)
                    tempStorage = extraBitsNeeded

                    while (bitsInBuffer < tempStorage) {
                        if (bytesAvailable != 0) result = Z_OK
                        else {

                            s.bitb = bitBuffer
                            s.bitk = bitsInBuffer
                            z.availIn = bytesAvailable
                            z.totalIn += inputPointer - z.nextInIndex
                            z.nextInIndex = inputPointer
                            s.write = outputWritePointer
                            return inflateFlush(s, z, result)
                        }
                        bytesAvailable--
                        bitBuffer = bitwiseOps.or(bitBuffer.toLong(), bitwiseOps.leftShift(bitwiseOps.and(z.nextIn!![inputPointer++].toLong(), 0xffL), bitsInBuffer)).toInt()
                        bitsInBuffer += 8
                    }

                    length += bitwiseOps.and(bitBuffer.toLong(), IBLK_INFLATE_MASK[tempStorage].toLong()).toInt()

                    bitBuffer = bitwiseOps.rightShift(bitBuffer.toLong(), tempStorage).toInt()
                    bitsInBuffer -= tempStorage

                    bitsNeeded = dbits.toInt()
                    tree = dtree
                    treeIndex = dtreeIndex
                    mode = ICODES_DIST
                    continue
                }

                ICODES_DIST -> {  // i: get distance next
                    tempStorage = bitsNeeded

                    while (bitsInBuffer < tempStorage) {
                        if (bytesAvailable != 0) result = Z_OK
                        else {

                            s.bitb = bitBuffer
                            s.bitk = bitsInBuffer
                            z.availIn = bytesAvailable
                            z.totalIn += inputPointer - z.nextInIndex
                            z.nextInIndex = inputPointer
                            s.write = outputWritePointer
                            return inflateFlush(s, z, result)
                        }
                        bytesAvailable--
                        bitBuffer = bitwiseOps.or(bitBuffer.toLong(), bitwiseOps.leftShift(bitwiseOps.and(z.nextIn!![inputPointer++].toLong(), 0xffL), bitsInBuffer)).toInt()
                        bitsInBuffer += 8
                    }

                    tableIndex = (treeIndex + bitwiseOps.and(bitBuffer.toLong(), IBLK_INFLATE_MASK[tempStorage].toLong()).toInt()) * 3

                    bitBuffer = bitwiseOps.rightShift(bitBuffer.toLong(), tree[tableIndex + 1]).toInt()
                    bitsInBuffer -= tree[tableIndex + 1]

                    extraBitsOrOperation = tree[tableIndex]
                    if (bitwiseOps.and(extraBitsOrOperation.toLong(), 16L).toInt() != 0) {
                        // distance
                        extraBitsNeeded = bitwiseOps.and(extraBitsOrOperation.toLong(), 15L).toInt()
                        distance = tree[tableIndex + 2]
                        mode = ICODES_DISTEXT
                        continue  // Continue to process ICODES_DISTEXT state
                    }
                    if (bitwiseOps.and(extraBitsOrOperation.toLong(), 64L).toInt() == 0) {
                        // next table
                        bitsNeeded = extraBitsOrOperation
                        treeIndex = tableIndex / 3 + tree[tableIndex + 2]
                        continue  // Continue with new table reference
                    }
                    mode = ICODES_BADCODE // invalid code
                    z.msg = "invalid distance code"
                    result = Z_DATA_ERROR

                    s.bitb = bitBuffer
                    s.bitk = bitsInBuffer
                    z.availIn = bytesAvailable
                    z.totalIn += inputPointer - z.nextInIndex
                    z.nextInIndex = inputPointer
                    s.write = outputWritePointer
                    return inflateFlush(s, z, result)
                }

                ICODES_DISTEXT -> {  // i: getting distance extra
                    tempStorage = extraBitsNeeded

                    while (bitsInBuffer < tempStorage) {
                        if (bytesAvailable != 0) result = Z_OK
                        else {

                            s.bitb = bitBuffer
                            s.bitk = bitsInBuffer
                            z.availIn = bytesAvailable
                            z.totalIn += inputPointer - z.nextInIndex
                            z.nextInIndex = inputPointer
                            s.write = outputWritePointer
                            return inflateFlush(s, z, result)
                        }
                        bytesAvailable--
                        bitBuffer = bitwiseOps.or(bitBuffer.toLong(), bitwiseOps.leftShift(bitwiseOps.and(z.nextIn!![inputPointer++].toLong(), 0xffL), bitsInBuffer)).toInt()
                        bitsInBuffer += 8
                    }

                    distance += bitwiseOps.and(bitBuffer.toLong(), IBLK_INFLATE_MASK[tempStorage].toLong()).toInt()

                    bitBuffer = bitwiseOps.rightShift(bitBuffer.toLong(), tempStorage).toInt()
                    bitsInBuffer -= tempStorage

                    mode = ICODES_COPY
                    continue
                }

                ICODES_COPY -> {  // o: copying bytes in window, waiting for space
                    outputPointer = outputWritePointer - distance
                    while (outputPointer < 0) {
                        // modulo window size-"while" instead
                        outputPointer += s.end // of "if" handles invalid distances
                    }
                    while (length != 0) {

                        if (outputBytesLeft == 0) {
                            if (outputWritePointer == s.end && s.read != 0) {
                                outputWritePointer = 0
                                outputBytesLeft = if (outputWritePointer < s.read) s.read - outputWritePointer - 1 else s.end - outputWritePointer
                            }
                            if (outputBytesLeft == 0) {
                                s.write = outputWritePointer
                                result = inflateFlush(s, z, result)
                                outputWritePointer = s.write
                                outputBytesLeft = if (outputWritePointer < s.read) s.read - outputWritePointer - 1 else s.end - outputWritePointer

                                if (outputWritePointer == s.end && s.read != 0) {
                                    outputWritePointer = 0
                                    outputBytesLeft = if (outputWritePointer < s.read) s.read - outputWritePointer - 1 else s.end - outputWritePointer
                                }

                                if (outputBytesLeft == 0) {
                                    s.bitb = bitBuffer
                                    s.bitk = bitsInBuffer
                                    z.availIn = bytesAvailable
                                    z.totalIn += inputPointer - z.nextInIndex
                                    z.nextInIndex = inputPointer
                                    s.write = outputWritePointer
                                    return inflateFlush(s, z, result)
                                }
                            }
                        }

                        s.window[outputWritePointer++] = s.window[outputPointer++]
                        if (outputWritePointer == s.end) outputWritePointer = 0
                        outputBytesLeft--

                        if (outputPointer == s.end) outputPointer = 0
                        length--
                    }
                    mode = ICODES_START
                }

                ICODES_LIT -> {  // o: got literal, waiting for output space
                    if (outputBytesLeft == 0) {
                        if (outputWritePointer == s.end && s.read != 0) {
                            outputWritePointer = 0
                            outputBytesLeft = if (outputWritePointer < s.read) s.read - outputWritePointer - 1 else s.end - outputWritePointer
                        }
                        if (outputBytesLeft == 0) {
                            s.write = outputWritePointer
                            result = inflateFlush(s, z, result)
                            outputWritePointer = s.write
                            outputBytesLeft = if (outputWritePointer < s.read) s.read - outputWritePointer - 1 else s.end - outputWritePointer

                            if (outputWritePointer == s.end && s.read != 0) {
                                outputWritePointer = 0
                                outputBytesLeft = if (outputWritePointer < s.read) s.read - outputWritePointer - 1 else s.end - outputWritePointer
                            }
                            if (outputBytesLeft == 0) {
                                s.bitb = bitBuffer
                                s.bitk = bitsInBuffer
                                z.availIn = bytesAvailable
                                z.totalIn += inputPointer - z.nextInIndex
                                z.nextInIndex = inputPointer
                                s.write = outputWritePointer
                                return inflateFlush(s, z, result)
                            }
                        }
                    }
                    result = Z_OK

                    s.window[outputWritePointer++] = literal.toByte()
                    if (outputWritePointer == s.end) outputWritePointer = 0
                    outputBytesLeft--

                    mode = ICODES_START
                }

                ICODES_WASH -> {  // o: got eob, possibly more output
                    if (bitsInBuffer > 7) {
                        // return unused byte, if any
                        bitsInBuffer -= 8
                        bytesAvailable++
                        inputPointer-- // can always return one
                    }

                    s.write = outputWritePointer
                    result = inflateFlush(s, z, result)
                    outputWritePointer = s.write
                    outputBytesLeft = if (outputWritePointer < s.read) s.read - outputWritePointer - 1 else s.end - outputWritePointer

                    if (s.read != s.write) {
                        s.bitb = bitBuffer
                        s.bitk = bitsInBuffer
                        z.availIn = bytesAvailable
                        z.totalIn += inputPointer - z.nextInIndex
                        z.nextInIndex = inputPointer
                        s.write = outputWritePointer
                        return inflateFlush(s, z, result)
                    }
                    mode = ICODES_END
                    continue
                }

                ICODES_END -> {
                    result = Z_STREAM_END
                    s.bitb = bitBuffer
                    s.bitk = bitsInBuffer
                    z.availIn = bytesAvailable
                    z.totalIn += inputPointer - z.nextInIndex
                    z.nextInIndex = inputPointer
                    s.write = outputWritePointer
                    return inflateFlush(s, z, result)
                }

                ICODES_BADCODE -> {  // x: got error

                    result = Z_DATA_ERROR

                    s.bitb = bitBuffer
                    s.bitk = bitsInBuffer
                    z.availIn = bytesAvailable
                    z.totalIn += inputPointer - z.nextInIndex
                    z.nextInIndex = inputPointer
                    s.write = outputWritePointer
                    return inflateFlush(s, z, result)
                }

                else -> {
                    result = Z_STREAM_ERROR

                    s.bitb = bitBuffer
                    s.bitk = bitsInBuffer
                    z.availIn = bytesAvailable
                    z.totalIn += inputPointer - z.nextInIndex
                    z.nextInIndex = inputPointer
                    s.write = outputWritePointer
                    return inflateFlush(s, z, result)
                }
            }
        }
        // Removed unreachable code
    }

    internal fun free() {
        //  ZFREE(z, c);
    }

    /**
     * Fast inflation routine called when sufficient input and output space is available.
     * This optimized version processes data more efficiently when we have at least 258 bytes
     * of output space (maximum string length) and 10 bytes of input available.
     *
     * @param bl Literal/length tree bits per table lookup
     * @param bd Distance tree bits per table lookup  
     * @param tl Literal/length decode table
     * @param tlIndex Starting index in literal/length table
     * @param td Distance decode table
     * @param tdIndex Starting index in distance table
     * @param s InfBlocks state containing buffers and pointers
     * @param z ZStream containing input data and compression state
     * @return Z_OK on success, error code on failure
     */
    internal fun inflateFast(
        bl: Int,
        bd: Int,
        tl: IntArray,
        tlIndex: Int,
        td: IntArray,
        tdIndex: Int,
        s: InfBlocks,
        z: ZStream
    ): Int {
        ZlibLogger.log("[DEBUG_LOG] inflateFast called with bl=$bl, bd=$bd, tlIndex=$tlIndex, tdIndex=$tdIndex")
        ZlibLogger.log("[DEBUG_LOG] inflateFast called with outputWrite=${s.write}, windowSize=${s.end}")
        ZlibLogger.log("[DEBUG_LOG] Initial window content at write position: '${if (s.write < s.window.size) s.window[s.write].toInt().toChar() else '?'}' (${if (s.write < s.window.size) s.window[s.write].toInt() else -1})")
        ZlibLogger.log("[DEBUG_LOG] Initial bit buffer: 0x${s.bitb.toString(16)}, bits: ${s.bitk}")
        ZlibLogger.log("[DEBUG_LOG] tl size=${tl.size}, td size=${td.size}")
        
        // Print the first few entries of the tables for debugging
        if (tl.size >= 3) {
            ZlibLogger.log("[DEBUG_LOG] First tl entry: [${tl[tlIndex * 3]}, ${tl[tlIndex * 3 + 1]}, ${tl[tlIndex * 3 + 2]}]")
        }
        if (td.size >= 3) {
            ZlibLogger.log("[DEBUG_LOG] First td entry: [${td[tdIndex * 3]}, ${td[tdIndex * 3 + 1]}, ${td[tdIndex * 3 + 2]}]")
        }
        
        var tempPointer: Int // Temporary table index
        var tempTable: IntArray // Temporary table reference
        var tempTableIndex: Int // Temporary table starting index
        var extraBitsOrOperation: Int // Extra bits needed or operation type
        var bitBuffer: Int // Bit accumulation buffer
        var outputBytesLeft: Int // Bytes remaining in output window
        var bytesToCopy: Int // Number of bytes to copy for match
        var copyDistance: Int // Distance back to copy from
        var copySourcePointer: Int // Source pointer for copying data

        // Initialize local variables from input parameters and stream state
        var inputPointer: Int = z.nextInIndex // Current position in input data
        var bytesAvailable: Int = z.availIn // Bytes available for reading
        bitBuffer = s.bitb
        var bitsInBuffer: Int = s.bitk // Number of valid bits in buffer
        var outputWritePointer: Int = s.write // Current position in output window
        outputBytesLeft = if (outputWritePointer < s.read) s.read - outputWritePointer - 1 else s.end - outputWritePointer

        // Precompute bit masks for table lookups
        var literalLengthMask: Int = IBLK_INFLATE_MASK[bl] // Bit mask for literal/length tree lookups
        var distanceMask: Int = IBLK_INFLATE_MASK[bd] // Bit mask for distance tree lookups

        ZlibLogger.log("[DEBUG_LOG] inflateFast - Initial state: inputPointer=$inputPointer, bytesAvailable=$bytesAvailable")
        ZlibLogger.log("[DEBUG_LOG] inflateFast - bitBuffer=0x${bitBuffer.toString(16)}, bitsInBuffer=$bitsInBuffer")
        ZlibLogger.log("[DEBUG_LOG] inflateFast - outputWritePointer=$outputWritePointer, outputBytesLeft=$outputBytesLeft")
        ZlibLogger.log("[DEBUG_LOG] inflateFast - literalLengthMask=0x${literalLengthMask.toString(16)}, distanceMask=0x${distanceMask.toString(16)}")

        // Check if we have sufficient space for fast processing
        if (outputBytesLeft < 258 || bytesAvailable < 10) {
            ZlibLogger.log("[DEBUG_LOG] inflateFast - Insufficient space for fast processing: outputBytesLeft=$outputBytesLeft, bytesAvailable=$bytesAvailable. Returning Z_OK")
            return Z_OK  // Return to slow path
        }

        // Main processing loop - continue until insufficient input or output space
        do {
            ZlibLogger.log("[DEBUG_LOG] inflateFast - Starting main loop iteration: outputBytesLeft=$outputBytesLeft, bytesAvailable=$bytesAvailable")
            // Assumption: called with outputBytesLeft >= 258 && bytesAvailable >= 10
            // Get literal/length code
            while (bitsInBuffer < 20) {
                // Ensure we have enough bits for literal/length code (max 15 bits + extra)
                ZlibLogger.log("[DEBUG_LOG] inflateFast - Need more bits for literal/length code. bitsInBuffer=$bitsInBuffer")
                bytesAvailable--
                bitBuffer = bitwiseOps.or(bitBuffer.toLong(), bitwiseOps.leftShift(bitwiseOps.and(z.nextIn!![inputPointer++].toLong(), 0xffL), bitsInBuffer)).toInt()
                bitsInBuffer += 8
                ZlibLogger.log("[DEBUG_LOG] inflateFast - Fetched byte. New bitBuffer=0x${bitBuffer.toString(16)}, bitsInBuffer=$bitsInBuffer")
            }

            ZlibLogger.log("[DEBUG_LOG] inflateFast - Before literal/length table lookup. bitBuffer=0x${bitBuffer.toString(16)}, bitsInBuffer=$bitsInBuffer, literalLengthMask=0x${literalLengthMask.toString(16)}")
            tempPointer = bitwiseOps.and(bitBuffer.toLong(), literalLengthMask.toLong()).toInt()
            tempTable = tl
            tempTableIndex = tlIndex
            
            ZlibLogger.log("[DEBUG_LOG] inflateFast - Literal/length lookup: tempTableIndex=$tempTableIndex, tempPointer=$tempPointer, tempTable.size=${tempTable.size}")
            
            // Use the same table access pattern as the slow path
            val tableIndex = (tempTableIndex + tempPointer) * 3
            if (tableIndex < 0 || tableIndex + 2 >= tempTable.size) {
                ZlibLogger.log("[DEBUG_LOG] inflateFast - Fallback: Literal/length table index out of bounds: tableIndex=$tableIndex, tempTable.size=${tempTable.size}. Bailing to slow path.")
                z.msg = "Array index out of bounds"
                // Save state and return to let the caller continue with the slow path
                s.bitb = bitBuffer
                s.bitk = bitsInBuffer
                z.availIn = bytesAvailable
                z.totalIn += inputPointer - z.nextInIndex
                z.nextInIndex = inputPointer
                s.write = outputWritePointer
                return Z_OK
            }
            
            // Get the table entry: [operation, bits, value] - same as slow path
            val op = tempTable[tableIndex]
            val bits = tempTable[tableIndex + 1]
            val value = tempTable[tableIndex + 2]
            
            ZlibLogger.log("[DEBUG_LOG] inflateFast - Literal/length table entry: op=$op, bits=$bits, value=$value")
            ZlibLogger.log("[DEBUG_LOG] inflateFast - About to consume $bits bits from bitBuffer=0x${bitBuffer.toString(16)}, bitsInBuffer=$bitsInBuffer")
            
            // Consume the bits
            bitBuffer = bitwiseOps.rightShift(bitBuffer.toLong(), bits).toInt()
            bitsInBuffer -= bits
            
            ZlibLogger.log("[DEBUG_LOG] inflateFast - After consuming bits: bitBuffer=0x${bitBuffer.toString(16)}, bitsInBuffer=$bitsInBuffer")
            
            if (op == 0) {
                // Direct literal - same as slow path
                ZlibLogger.log("[DEBUG_LOG] inflateFast - Direct literal found. op=$op, bits=$bits, value=$value")
                
                // Check if outputWritePointer is within bounds
                if (outputWritePointer < 0 || outputWritePointer >= s.window.size) {
                    ZlibLogger.log("[DEBUG_LOG] inflateFast - ERROR: OutputWritePointer out of bounds: outputWritePointer=$outputWritePointer, s.window.size=${s.window.size}")
                    z.msg = "Array index out of bounds"
                    return Z_DATA_ERROR
                }
                
                // Like C: if (op == 0) { *out++ = (unsigned char)(here->val); }
                val fastLiteralByte = bitwiseOps.and(value.toLong(), 0xFFL).toByte()  // Mask to byte range
                ZlibLogger.log("[DEBUG_LOG] inflateFast - Writing literal: '${fastLiteralByte.toInt().toChar()}' (${fastLiteralByte.toInt()}) to window[$outputWritePointer]")
                s.window[outputWritePointer++] = fastLiteralByte
                if (outputWritePointer == s.end) outputWritePointer = 0
                outputBytesLeft--
                continue
            }

            do {
                ZlibLogger.log("[DEBUG_LOG] inflateFast - Inner loop for length/distance. Current op=$op")
                // Same pattern as slow path: get literal/length code
                tempPointer = bitwiseOps.and(bitBuffer.toLong(), literalLengthMask.toLong()).toInt()
                
                // Use the same table access pattern as slow path
                // Removed unused variable 'innerTableIndex'
                val innerTableIndex = (tempTableIndex + tempPointer) * 3
                if (innerTableIndex < 0 || innerTableIndex + 2 >= tl.size) {
                    ZlibLogger.log("[DEBUG_LOG] inflateFast - ERROR: Inner table access out of bounds: innerTableIndex=$innerTableIndex, tl.size=${tl.size}")
                    z.msg = "invalid literal/length code - out of bounds"
                    return Z_DATA_ERROR
                }
                
                // Get the table entry: [operation, bits, value] - same as slow path
                val innerOp = tl[innerTableIndex]
                val innerBits = tl[innerTableIndex + 1]
                val innerValue = tl[innerTableIndex + 2]
                
                ZlibLogger.log("[DEBUG_LOG] inflateFast - Huffman lookup: tempPointer=$tempPointer, op=$innerOp, bits=$innerBits, value=$innerValue")
                
                // Consume the bits - same as slow path
                bitBuffer = bitwiseOps.rightShift(bitBuffer.toLong(), innerBits).toInt()
                bitsInBuffer -= innerBits
                extraBitsOrOperation = innerOp
                
                ZlibLogger.log("[DEBUG_LOG] inflateFast - Table entry: op=$innerOp, bits=$innerBits, value=$innerValue")
                if (innerOp == 0) {  // Literal - same as slow path
                    val literalByte = bitwiseOps.and(innerValue.toLong(), 0xFFL).toByte()  // Mask to byte range
                    ZlibLogger.log("[DEBUG_LOG] inflateFast - Writing literal: '${literalByte.toInt().toChar()}' (${literalByte.toInt()}) to window[$outputWritePointer]")
                    s.window[outputWritePointer++] = literalByte
                    if (outputWritePointer == s.end) outputWritePointer = 0
                    outputBytesLeft--
                    break
                } else if (bitwiseOps.and(innerOp.toLong(), 16L).toInt() != 0) {  // Length - same as slow path
                    ZlibLogger.log("[DEBUG_LOG] inflateFast - Found length code. innerOp=$innerOp")
                    extraBitsNeeded = bitwiseOps.and(innerOp.toLong(), 15L).toInt()  // Like C: op &= 15
                    bytesToCopy = innerValue + bitwiseOps.and(bitBuffer.toLong(), IBLK_INFLATE_MASK[extraBitsNeeded].toLong()).toInt()
                    bitBuffer = bitwiseOps.rightShift(bitBuffer.toLong(), extraBitsNeeded).toInt()
                    bitsInBuffer -= extraBitsNeeded
                    ZlibLogger.log("[DEBUG_LOG] inflateFast - Length: bytesToCopy=$bytesToCopy, extraBitsNeeded=$extraBitsNeeded. New bitBuffer=0x${bitBuffer.toString(16)}, bitsInBuffer=$bitsInBuffer")

                    while (bitsInBuffer < 15) {
                        ZlibLogger.log("[DEBUG_LOG] inflateFast - Need more bits for distance code. bitsInBuffer=$bitsInBuffer")
                        bytesAvailable--
                        bitBuffer = bitwiseOps.or(bitBuffer.toLong(), bitwiseOps.leftShift(bitwiseOps.and(z.nextIn!![inputPointer++].toLong(), 0xffL), bitsInBuffer)).toInt()
                        bitsInBuffer += 8
                        ZlibLogger.log("[DEBUG_LOG] inflateFast - Fetched byte. New bitBuffer=0x${bitBuffer.toString(16)}, bitsInBuffer=$bitsInBuffer")
                    }
                    ZlibLogger.log("[DEBUG_LOG] inflateFast - Processing distance code: bitBuffer=0x${bitBuffer.toString(16)}, distanceMask=0x${distanceMask.toString(16)}")
                    
                    // Like C: here = dcode + (hold & dmask)
                    var distIndex = bitwiseOps.and(bitBuffer.toLong(), distanceMask.toLong()).toInt()
                    ZlibLogger.log("[DEBUG_LOG] inflateFast - Distance code calculation: distIndex=$distIndex")
                    
                    // Use the passed distance table (td), not the fixed DISTFIX table
                    var tp = td  // distance table 
                    var tpIndex = tdIndex  // distance table starting index
                    var t = distIndex  // current table pointer
                    
                    // Check if (tpIndex + t) * 3 is within bounds
                    if ((tpIndex + t) * 3 >= tp.size) {
                        ZlibLogger.log("[DEBUG_LOG] inflateFast - ERROR: Distance index out of bounds: index=${(tpIndex + t) * 3}, tp.size=${tp.size}")
                        z.msg = "invalid distance code - index out of bounds"
                        s.bitb = bitBuffer
                        s.bitk = bitsInBuffer
                        z.availIn = bytesAvailable
                        z.totalIn += inputPointer - z.nextInIndex
                        z.nextInIndex = inputPointer
                        s.write = outputWritePointer
                        return Z_DATA_ERROR
                    }
                    
                    // Get distance tree entry: [operation, bits, value]
                    var e = tp[(tpIndex + t) * 3]  // operation
                    var distBits = tp[(tpIndex + t) * 3 + 1]  // bits needed
                    var distValue = tp[(tpIndex + t) * 3 + 2]  // base value
                    
                    ZlibLogger.log("[DEBUG_LOG] inflateFast - Distance tree entry: op=$e, bits=$distBits, val=$distValue")
                    
                    do {
                        ZlibLogger.log("[DEBUG_LOG] inflateFast - Distance inner loop. Current e=$e, distBits=$distBits")
                        // Like C: hold >>= here->bits; bits -= here->bits; op = here->op;
                        bitBuffer = bitwiseOps.rightShift(bitBuffer.toLong(), distBits).toInt()
                        bitsInBuffer -= distBits
                        
                        if (bitwiseOps.and(e.toLong(), 16L).toInt() != 0) {  // Like C: if (op & 16) { /* distance base */ }
                            ZlibLogger.log("[DEBUG_LOG] inflateFast - Distance code with extra bits. e=$e")
                            
                            extraBitsNeeded = bitwiseOps.and(e.toLong(), 15L).toInt()  // Like C: op &= 15
                            
                            // Ensure we have enough bits for the extra bits
                            while (bitsInBuffer < extraBitsNeeded) {
                                ZlibLogger.log("[DEBUG_LOG] inflateFast - Need more bits for distance extra. bitsInBuffer=$bitsInBuffer, extraBitsNeeded=$extraBitsNeeded")
                                if (bytesAvailable <= 0) {
                                    ZlibLogger.log("[DEBUG_LOG] inflateFast - ERROR: Not enough input bytes for extra bits")
                                    z.msg = "invalid distance code - not enough input"
                                    s.bitb = bitBuffer
                                    s.bitk = bitsInBuffer
                                    z.availIn = bytesAvailable
                                    z.totalIn += inputPointer - z.nextInIndex
                                    z.nextInIndex = inputPointer
                                    s.write = outputWritePointer
                                    return Z_BUF_ERROR
                                }
                                bytesAvailable--
                                bitBuffer = bitwiseOps.or(bitBuffer.toLong(), bitwiseOps.leftShift(bitwiseOps.and(z.nextIn!![inputPointer++].toLong(), 0xffL), bitsInBuffer)).toInt()
                                bitsInBuffer += 8
                                ZlibLogger.log("[DEBUG_LOG] inflateFast - Fetched byte. New bitBuffer=0x${bitBuffer.toString(16)}, bitsInBuffer=$bitsInBuffer")
                            }
                            
                            // Like C: dist = (unsigned)(here->val) + ((unsigned)hold & ((1U << op) - 1))
                            copyDistance = distValue + bitwiseOps.and(bitBuffer.toLong(), IBLK_INFLATE_MASK[extraBitsNeeded].toLong()).toInt()
                            ZlibLogger.log("[DEBUG_LOG] inflateFast - copyDistance=$copyDistance, extraBitsNeeded=$extraBitsNeeded, mask=0x${IBLK_INFLATE_MASK[extraBitsNeeded].toString(16)}")
                            
                            // Like C: hold >>= op; bits -= op;
                            bitBuffer = bitwiseOps.rightShift(bitBuffer.toLong(), extraBitsNeeded).toInt()
                            bitsInBuffer -= extraBitsNeeded
                            
                            // In C#: m -= c;
                            outputBytesLeft -= bytesToCopy
                            
                            // In C#: r = q - d;
                            copySourcePointer = outputWritePointer - copyDistance
                            ZlibLogger.log("[DEBUG_LOG] inflateFast - Copy source pointer: $copySourcePointer, outputWritePointer=$outputWritePointer")
                            
                            // In C#: if (d > s.end) { z.msg = "invalid distance code"; ... return Z_DATA_ERROR; }
                            if (copyDistance <= 0 || copyDistance > s.end) {
                                ZlibLogger.log("[DEBUG_LOG] inflateFast - ERROR: Invalid copy distance: $copyDistance, s.end=${s.end}")
                                z.msg = "invalid distance code - distance too large"
                                s.bitb = bitBuffer
                                s.bitk = bitsInBuffer
                                z.availIn = bytesAvailable
                                z.totalIn += inputPointer - z.nextInIndex
                                z.nextInIndex = inputPointer
                                s.write = outputWritePointer
                                return Z_DATA_ERROR
                            }
                            
                            // In C#: if (q >= d) { r = q - d; ... } else { r = q - d; do { r += s.end; } while (r < 0); ... }
                            ZlibLogger.log("[DEBUG_LOG] inflateFast - Copying $bytesToCopy bytes from distance $copyDistance")
                            
                            // Directly follow the C# implementation for copy operation
                            if (outputWritePointer >= copyDistance) {
                                // Source is before destination in the buffer - can do direct copy
                                // In C#: r = q - d;
                                copySourcePointer = outputWritePointer - copyDistance
                                ZlibLogger.log("[DEBUG_LOG] inflateFast - Direct copy: copySourcePointer=$copySourcePointer")
                                
                                // In C#: if (q - r > 0 && 2 > (q - r)) { ... } else { Array.Copy(s.window, r, s.window, q, 2); ... }
                                // Check if we can optimize for small copies
                                if (outputWritePointer - copySourcePointer > 0 && 2 > (outputWritePointer - copySourcePointer)) {
                                    // In C#: s.window[q++] = s.window[r++]; c--; s.window[q++] = s.window[r++]; c--;
                                    ZlibLogger.log("[DEBUG_LOG] inflateFast - Small copy optimization")
                                    val byte1 = s.window[copySourcePointer]
                                    val byte2 = s.window[copySourcePointer + 1]
                                    ZlibLogger.log("[DEBUG_LOG] inflateFast - Small copy: copying '${byte1.toInt().toChar()}' (${byte1.toInt()}) from pos $copySourcePointer to $outputWritePointer")
                                    ZlibLogger.log("[DEBUG_LOG] inflateFast - Small copy: copying '${byte2.toInt().toChar()}' (${byte2.toInt()}) from pos ${copySourcePointer + 1} to ${outputWritePointer + 1}")
                                    s.window[outputWritePointer++] = s.window[copySourcePointer++]
                                    if (outputWritePointer == s.end) outputWritePointer = 0
                                    if (copySourcePointer == s.end) copySourcePointer = 0
                                    bytesToCopy--
                                    s.window[outputWritePointer++] = s.window[copySourcePointer++]
                                    if (outputWritePointer == s.end) outputWritePointer = 0
                                    if (copySourcePointer == s.end) copySourcePointer = 0
                                    bytesToCopy--
                                } else {
                                    // In C#: Array.Copy(s.window, r, s.window, q, e); q += e; r += e; c -= e;
                                    ZlibLogger.log("[DEBUG_LOG] inflateFast - Array copy")
                                    // Bounds check only for basic validity, copy loop handles wrap-around
                                    if (copySourcePointer < 0 || copySourcePointer >= s.window.size || 
                                        outputWritePointer < 0 || outputWritePointer >= s.window.size) {
                                        ZlibLogger.log("[DEBUG_LOG] inflateFast - ERROR: Copy pointers out of bounds")
                                        z.msg = "invalid distance code - copy pointers out of bounds"
                                        s.bitb = bitBuffer
                                        s.bitk = bitsInBuffer
                                        z.availIn = bytesAvailable
                                        z.totalIn += inputPointer - z.nextInIndex
                                        z.nextInIndex = inputPointer
                                        s.write = outputWritePointer
                                        return Z_DATA_ERROR
                                    }
                                    
                                    var count = bytesToCopy
                                    while (count-- > 0) {
                                        s.window[outputWritePointer++] = s.window[copySourcePointer++]
                                        if (copySourcePointer == s.end) copySourcePointer = 0
                                        if (outputWritePointer == s.end) outputWritePointer = 0
                                    }
                                    bytesToCopy = 0 // All bytes copied
                                }
                            } else {
                                // Source is after destination or wraps around - need special handling
                                // In C#: r = q - d; do { r += s.end; } while (r < 0);
                                copySourcePointer = outputWritePointer - copyDistance
                                do {
                                    copySourcePointer += s.end
                                } while (copySourcePointer < 0)
                                ZlibLogger.log("[DEBUG_LOG] inflateFast - Wrap-around copy: copySourcePointer=$copySourcePointer")
                                
                                // In C#: e = s.end - r; if (c > e) { ... } 
                                val endDistance = s.end - copySourcePointer
                                if (bytesToCopy > endDistance) {
                                    // Need to wrap around during copy
                                    // In C#: c -= e; if (q - r > 0 && e > (q - r)) { ... } else { Array.Copy(s.window, r, s.window, q, e); ... }
                                    bytesToCopy -= endDistance
                                    
                                    if (outputWritePointer - copySourcePointer > 0 && endDistance > (outputWritePointer - copySourcePointer)) {
                                        // In C#: do { s.window[q++] = s.window[r++]; } while (--e != 0);
                                        var tempE = endDistance
                                        while (tempE-- > 0) {
                                            s.window[outputWritePointer++] = s.window[copySourcePointer++]
                                            if (outputWritePointer == s.end) outputWritePointer = 0
                                        }
                                    } else {
                                        // In C#: Array.Copy(s.window, r, s.window, q, e); q += e; r += e; e = 0;
                                        // Basic bounds check - endDistance should not exceed remaining window space from copySourcePointer
                                        if (copySourcePointer < 0 || copySourcePointer >= s.window.size || 
                                            outputWritePointer < 0 || outputWritePointer >= s.window.size ||
                                            endDistance > s.window.size - copySourcePointer) {
                                            ZlibLogger.log("[DEBUG_LOG] inflateFast - ERROR: Copy pointers out of bounds (first part)")
                                            z.msg = "invalid distance code - copy pointers out of bounds"
                                            s.bitb = bitBuffer
                                            s.bitk = bitsInBuffer
                                            z.availIn = bytesAvailable
                                            z.totalIn += inputPointer - z.nextInIndex
                                            z.nextInIndex = inputPointer
                                            s.write = outputWritePointer
                                            return Z_DATA_ERROR
                                        }
                                        
                                        var count = endDistance
                                        while (count-- > 0) {
                                            s.window[outputWritePointer++] = s.window[copySourcePointer++]
                                            if (outputWritePointer == s.end) outputWritePointer = 0
                                        }
                                    }
                                    
                                    // In C#: r = 0;
                                    copySourcePointer = 0
                                }
                            }
                            
                            // In C#: if (q - r > 0 && c > (q - r)) { ... } else { Array.Copy(s.window, r, s.window, q, c); ... }
                            // Copy remaining bytes
                            if (outputWritePointer - copySourcePointer > 0 && bytesToCopy > (outputWritePointer - copySourcePointer)) {
                                // In C#: do { s.window[q++] = s.window[r++]; } while (--c != 0);
                                var count = bytesToCopy
                                while (count-- > 0) {
                                    s.window[outputWritePointer++] = s.window[copySourcePointer++]
                                    if (copySourcePointer == s.end) copySourcePointer = 0
                                    if (outputWritePointer == s.end) outputWritePointer = 0
                                }
                            } else {
                                // In C#: Array.Copy(s.window, r, s.window, q, c); q += c; r += c; c = 0;
                                // Basic bounds check only, copy loop handles wrap-around
                                if (copySourcePointer < 0 || copySourcePointer >= s.window.size || 
                                    outputWritePointer < 0 || outputWritePointer >= s.window.size) {
                                    ZlibLogger.log("[DEBUG_LOG] inflateFast - ERROR: Copy pointers out of bounds (final part)")
                                    z.msg = "invalid distance code - copy pointers out of bounds"
                                    s.bitb = bitBuffer
                                    s.bitk = bitsInBuffer
                                    z.availIn = bytesAvailable
                                    z.totalIn += inputPointer - z.nextInIndex
                                    z.nextInIndex = inputPointer
                                    s.write = outputWritePointer
                                    return Z_DATA_ERROR
                                }
                                
                                var count = bytesToCopy
                                while (count-- > 0) {
                                    s.window[outputWritePointer++] = s.window[copySourcePointer++]
                                    if (copySourcePointer == s.end) copySourcePointer = 0
                                    if (outputWritePointer == s.end) outputWritePointer = 0
                                }
                            }
                            break
                        } else if (bitwiseOps.and(e.toLong(), 64L).toInt() == 0) {
                            ZlibLogger.log("[DEBUG_LOG] inflateFast - Next distance table reference")
                            
                            // Handle next table reference for distance codes
                            // In C#: if ((e & 64) == 0) { t += tp[(tp_index + t) * 3 + 2]; t += (b & inflate_mask[e]); e = tp[(tp_index + t) * 3]; }
                            
                            // Check if tp[(tpIndex + t) * 3 + 2] is valid
                            if ((tpIndex + t) * 3 + 2 >= tp.size) {
                                ZlibLogger.log("[DEBUG_LOG] inflateFast - ERROR: Table reference out of bounds: index=${(tpIndex + t) * 3 + 2}, tp.size=${tp.size}")
                                z.msg = "invalid distance code - table reference out of bounds"
                                s.bitb = bitBuffer
                                s.bitk = bitsInBuffer
                                z.availIn = bytesAvailable
                                z.totalIn += inputPointer - z.nextInIndex
                                z.nextInIndex = inputPointer
                                s.write = outputWritePointer
                                return Z_DATA_ERROR
                            }
                            
                            // In C#: t += tp[(tp_index + t) * 3 + 2];
                            t += tp[(tpIndex + t) * 3 + 2]
                            
                            // In C#: t += (b & inflate_mask[e]);
                            t += bitwiseOps.and(bitBuffer.toLong(), IBLK_INFLATE_MASK[e].toLong()).toInt()
                            
                            // Check if (tpIndex + t) * 3 is valid
                            if ((tpIndex + t) * 3 >= tp.size) {
                                ZlibLogger.log("[DEBUG_LOG] inflateFast - ERROR: Next table pointer out of bounds: (tpIndex + t)=${tpIndex + t}, tp.size=${tp.size}")
                                z.msg = "invalid distance code - next table pointer out of bounds"
                                s.bitb = bitBuffer
                                s.bitk = bitsInBuffer
                                z.availIn = bytesAvailable
                                z.totalIn += inputPointer - z.nextInIndex
                                z.nextInIndex = inputPointer
                                s.write = outputWritePointer
                                return Z_DATA_ERROR
                            }
                            
                            // In C#: e = tp[(tpIndex + t) * 3];
                            e = tp[(tpIndex + t) * 3]
                            distBits = tp[(tpIndex + t) * 3 + 1]
                            distValue = tp[(tpIndex + t) * 3 + 2]
                            ZlibLogger.log("[DEBUG_LOG] inflateFast - New distance table entry: e=$e, bits=$distBits, val=$distValue")
                        } else {
                            ZlibLogger.log("[DEBUG_LOG] inflateFast - ERROR: Invalid distance code: e=$e")
                            z.msg = "invalid distance code"
                            val errBytes = z.availIn - bytesAvailable - (bitsInBuffer shr 3)
                            bytesAvailable += errBytes
                            inputPointer -= errBytes
                            bitsInBuffer -= (errBytes shl 3)
                            s.bitb = bitBuffer
                            s.bitk = bitsInBuffer
                            z.availIn = bytesAvailable
                            z.totalIn += inputPointer - z.nextInIndex
                            z.nextInIndex = inputPointer
                            s.write = outputWritePointer
                            return Z_DATA_ERROR
                        }
                    } while (true)
                    break
                } else if ((innerOp and 32) != 0) {
                    // End of block in literal/length path
                    val errBytes = z.availIn - bytesAvailable - (bitsInBuffer shr 3)
                    bytesAvailable += errBytes
                    inputPointer -= errBytes
                    bitsInBuffer -= (errBytes shl 3)
                    s.bitb = bitBuffer
                    s.bitk = bitsInBuffer
                    z.availIn = bytesAvailable
                    z.totalIn += inputPointer - z.nextInIndex
                    z.nextInIndex = inputPointer
                    s.write = outputWritePointer
                    return Z_STREAM_END
                } else if ((innerOp and 64) == 0) {
                    // Next table reference: defer to slow path
                    s.bitb = bitBuffer
                    s.bitk = bitsInBuffer
                    z.availIn = bytesAvailable
                    z.totalIn += inputPointer - z.nextInIndex
                    z.nextInIndex = inputPointer
                    s.write = outputWritePointer
                    return Z_OK
                } else {
                    ZlibLogger.log("[DEBUG_LOG] inflateFast - Invalid literal/length code in fast path. extraBitsOrOperation=$innerOp")
                    z.msg = "invalid literal/length code"
                    val errBytes = z.availIn - bytesAvailable - (bitsInBuffer shr 3)
                    bytesAvailable += errBytes
                    inputPointer -= errBytes
                    bitsInBuffer -= (errBytes shl 3)
                    s.bitb = bitBuffer
                    s.bitk = bitsInBuffer
                    z.availIn = bytesAvailable
                    z.totalIn += inputPointer - z.nextInIndex
                    z.nextInIndex = inputPointer
                    s.write = outputWritePointer
                    return Z_DATA_ERROR
                }
            } while (true)
        } while (outputBytesLeft >= 258 && bytesAvailable >= 10)

        // Not enough input or output space - restore pointers and return
        bytesToCopy = z.availIn - bytesAvailable
        bytesToCopy = bitwiseOps.rightShift(bitsInBuffer.toLong(), 3).toInt().coerceAtMost(bytesToCopy)
        bytesAvailable += bytesToCopy
        inputPointer -= bytesToCopy
        bitsInBuffer -= bitwiseOps.leftShift(bytesToCopy.toLong(), 3).toInt()

        s.bitb = bitBuffer
        s.bitk = bitsInBuffer
        z.availIn = bytesAvailable
        z.totalIn += inputPointer - z.nextInIndex
        z.nextInIndex = inputPointer
        ZlibLogger.log("[DEBUG_LOG] inflateFast ending: outputWritePointer=$outputWritePointer, setting s.write")
        s.write = outputWritePointer
        ZlibLogger.log("[DEBUG_LOG] inflateFast ended: s.write=${s.write}")

        return Z_OK
    }

    companion object {
        // Constants previously defined here are now in ai.solace.zlib.common.Constants
        // inflate_mask is IBLK_INFLATE_MASK
        // Z_OK, Z_STREAM_END, etc. are already in common
        // Mode constants (START, LEN, etc.) are now ICODES_START, ICODES_LEN, etc. in Constants.kt
    }
}