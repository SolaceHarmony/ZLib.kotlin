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

internal class InfCodes {

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

        // process input and output based on current state
        while (true) {
            iterationCount++

            // Safety check to prevent hanging
            if (iterationCount > maxIterations) {
                z.msg = "Too many iterations in InfCodes.proc, possible corrupt data"
                return Z_DATA_ERROR
            }

            when (mode) {

                // waiting for "i:"=input, "o:"=output, "x:"=nothing
                ICODES_START -> { // x: set up for LEN
                    println("[HUFFMAN] InfCodes START mode, outputBytes=${s.write}, totalBytes processed so far")
                    println("[HUFFMAN] Window buffer at position ${s.write}: '${s.window[s.write].toInt().toChar()}' (${s.window[s.write].toInt()})")
                    if (outputBytesLeft >= 258 && bytesAvailable >= 10) {

                        s.bitb = bitBuffer
                        s.bitk = bitsInBuffer
                        z.availIn = bytesAvailable
                        z.totalIn += inputPointer - z.nextInIndex
                        z.nextInIndex = inputPointer
                        s.write = outputWritePointer
                        result = inflateFast(lbits.toInt(), dbits.toInt(), ltree, ltreeIndex, dtree, dtreeIndex, s, z)
                        println("[HUFFMAN] inflateFast returned: $result (Z_OK=$Z_OK, Z_STREAM_END=$Z_STREAM_END)")

                        inputPointer = z.nextInIndex
                        bytesAvailable = z.availIn
                        bitBuffer = s.bitb
                        bitsInBuffer = s.bitk
                        outputWritePointer = s.write
                        outputBytesLeft = if (outputWritePointer < s.read) s.read - outputWritePointer - 1 else s.end - outputWritePointer

                        if (result != Z_OK) {
                            mode = if (result == Z_STREAM_END) ICODES_WASH else ICODES_BADCODE
                            break
                        }
                    }
                    bitsNeeded = lbits.toInt()
                    tree = ltree
                    treeIndex = ltreeIndex

                    mode = ICODES_LEN
                    println("[HUFFMAN] Transitioning to ICODES_LEN mode")
                    continue
                }

                ICODES_LEN -> {  // i: get length/literal/eob next
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
                        bitBuffer = bitBuffer or ((z.nextIn!![inputPointer++].toInt() and 0xff) shl bitsInBuffer)
                        bitsInBuffer += 8
                    }

                    val maskedB = bitBuffer and IBLK_INFLATE_MASK[bitsNeeded]
                    val tableIndex = (treeIndex + maskedB) * 3

                    val tempCounter = tree[tableIndex]
                    val codeBits = tree[tableIndex + 1]
                    val codeValue = tree[tableIndex + 2]
                    
                    println("[DECODE_DEBUG] Symbol decode: maskedB=$maskedB, tableIndex=$tableIndex, tempCounter=$tempCounter, codeBits=$codeBits, codeValue=$codeValue")

                    bitBuffer = bitBuffer ushr tree[tableIndex + 1]
                    bitsInBuffer -= tree[tableIndex + 1]

                    if (tempCounter == 0) {
                        // Literal symbol
                        println("[DECODE_DEBUG] Found literal: $codeValue (char: '${codeValue.toChar()}') at position $outputWritePointer")
                        s.window[outputWritePointer++] = codeValue.toByte()
                        outputBytesLeft--
                        mode = ICODES_START
                        break
                    }
                    if (tempCounter and 16 != 0) {
                        // Length code
                        println("[DECODE_DEBUG] Found length code: $codeValue")
                        extraBitsNeeded = tempCounter and 15
                        length = codeValue
                        mode = ICODES_LENEXT
                        break
                    }
                    if (tempCounter and 64 == 0) {
                        // next table
                        bitsNeeded = tempCounter
                        treeIndex = tableIndex / 3 + tree[tableIndex + 2]
                        continue
                    }
                    if (tempCounter and 32 != 0) {
                        // End of block
                        println("[DECODE_DEBUG] Found END OF BLOCK")
                        mode = ICODES_WASH
                        break
                    }
                    println("[DECODE_DEBUG] Invalid literal/length code")
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
                        bitBuffer = bitBuffer or ((z.nextIn!![inputPointer++].toInt() and 0xff) shl bitsInBuffer)
                        bitsInBuffer += 8
                    }

                    length += bitBuffer and IBLK_INFLATE_MASK[tempStorage]

                    bitBuffer = bitBuffer ushr tempStorage
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
                        bitBuffer = bitBuffer or ((z.nextIn!![inputPointer++].toInt() and 0xff) shl bitsInBuffer)
                        bitsInBuffer += 8
                    }

                    tableIndex = (treeIndex + (bitBuffer and IBLK_INFLATE_MASK[tempStorage])) * 3

                    bitBuffer = bitBuffer ushr tree[tableIndex + 1]
                    bitsInBuffer -= tree[tableIndex + 1]

                    extraBitsOrOperation = tree[tableIndex]
                    if (extraBitsOrOperation and 16 != 0) {
                        // distance
                        extraBitsNeeded = extraBitsOrOperation and 15
                        distance = tree[tableIndex + 2]
                        mode = ICODES_DISTEXT
                        break
                    }
                    if (extraBitsOrOperation and 64 == 0) {
                        // next table
                        bitsNeeded = extraBitsOrOperation
                        treeIndex = tableIndex / 3 + tree[tableIndex + 2]
                        break
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
                        bitBuffer = bitBuffer or ((z.nextIn!![inputPointer++].toInt() and 0xff) shl bitsInBuffer)
                        bitsInBuffer += 8
                    }

                    distance += bitBuffer and IBLK_INFLATE_MASK[tempStorage]

                    bitBuffer = bitBuffer ushr tempStorage
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
        return Z_OK // Should be unreachable
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
        println("[FAST_DEBUG] inflateFast called with outputWrite=${s.write}, windowSize=${s.end}")
        println("[FAST_DEBUG] Initial window content at write position: '${if (s.write < s.window.size) s.window[s.write].toInt().toChar() else '?'}' (${if (s.write < s.window.size) s.window[s.write].toInt() else -1})")
        println("[FAST_DEBUG] Initial bit buffer: 0x${s.bitb.toString(16)}, bits: ${s.bitk}")
        
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

        // Main processing loop - continue until insufficient input or output space
        do {
            // Assumption: called with outputBytesLeft >= 258 && bytesAvailable >= 10
            // Get literal/length code
            while (bitsInBuffer < 20) {
                // Ensure we have enough bits for literal/length code (max 15 bits + extra)
                bytesAvailable--
                bitBuffer = bitBuffer or ((z.nextIn!![inputPointer++].toInt() and 0xff) shl bitsInBuffer)
                bitsInBuffer += 8
            }

            println("[BIT_DEBUG] bitBuffer=0x${bitBuffer.toString(16)}, bitsInBuffer=$bitsInBuffer, literalLengthMask=0x${literalLengthMask.toString(16)}")
            tempPointer = bitBuffer and literalLengthMask
            tempTable = tl
            tempTableIndex = tlIndex
            println("[BIT_DEBUG] tempPointer=$tempPointer (0x${tempPointer.toString(16)}), tlIndex=$tempTableIndex")
            if (tempTable[(tempTableIndex + tempPointer) * 3] == 0) {
                // Direct literal - no extra table lookup needed
                val tableBits = tempTable[(tempTableIndex + tempPointer) * 3 + 1]
                val literalValue = tempTable[(tempTableIndex + tempPointer) * 3 + 2]
                
                println("[FAST_TABLE] tempPointer=$tempPointer, tableIndex=${(tempTableIndex + tempPointer) * 3}, exop=${tempTable[(tempTableIndex + tempPointer) * 3]}, bits=$tableBits, base=$literalValue")
                
                bitBuffer = bitBuffer ushr tableBits
                bitsInBuffer -= tableBits

                println("[FAST_LITERAL] Writing literal: $literalValue ('${literalValue.toChar()}') at position $outputWritePointer")
                s.window[outputWritePointer++] = literalValue.toByte()
                outputBytesLeft--
                continue
            }

            do {
                bitBuffer = bitBuffer ushr tempTable[(tempTableIndex + tempPointer) * 3 + 1]
                bitsInBuffer -= tempTable[(tempTableIndex + tempPointer) * 3 + 1]

                if (tempTable[(tempTableIndex + tempPointer) * 3] and 16 != 0) {
                    // Length code - extract extra bits for length calculation
                    extraBitsOrOperation = tempTable[(tempTableIndex + tempPointer) * 3] and 15
                    bytesToCopy = tempTable[(tempTableIndex + tempPointer) * 3 + 2] + (bitBuffer and IBLK_INFLATE_MASK[extraBitsOrOperation])

                    bitBuffer = bitBuffer ushr extraBitsOrOperation
                    bitsInBuffer -= extraBitsOrOperation

                    // Decode distance base of block to copy
                    while (bitsInBuffer < 15) {
                        // Ensure we have enough bits for distance code (max 15 bits)
                        bytesAvailable--
                        bitBuffer = bitBuffer or ((z.nextIn!![inputPointer++].toInt() and 0xff) shl bitsInBuffer)
                        bitsInBuffer += 8
                    }

                    tempPointer = bitBuffer and distanceMask
                    tempTable = td
                    tempTableIndex = tdIndex
                    extraBitsOrOperation = tempTable[(tempTableIndex + tempPointer) * 3]

                    do {
                        bitBuffer = bitBuffer ushr tempTable[(tempTableIndex + tempPointer) * 3 + 1]
                        bitsInBuffer -= tempTable[(tempTableIndex + tempPointer) * 3 + 1]

                        if ((extraBitsOrOperation and 16) != 0) {
                            // Get extra bits to add to distance base
                            extraBitsOrOperation = extraBitsOrOperation and 15
                            while (bitsInBuffer < extraBitsOrOperation) {
                                // Get extra bits for distance (up to 13)
                                bytesAvailable--
                                bitBuffer = bitBuffer or ((z.nextIn!![inputPointer++].toInt() and 0xff) shl bitsInBuffer)
                                bitsInBuffer += 8
                            }

                            copyDistance = tempTable[(tempTableIndex + tempPointer) * 3 + 2] + (bitBuffer and IBLK_INFLATE_MASK[extraBitsOrOperation])

                            bitBuffer = bitBuffer ushr extraBitsOrOperation
                            bitsInBuffer -= extraBitsOrOperation

                            // Perform the copy operation
                            outputBytesLeft -= bytesToCopy
                            if (outputWritePointer >= copyDistance) {
                                // Source offset is before destination - straightforward copy
                                copySourcePointer = outputWritePointer - copyDistance
                                if (outputWritePointer - copySourcePointer > 0 && 2 > (outputWritePointer - copySourcePointer)) {
                                    s.window[outputWritePointer++] = s.window[copySourcePointer++]
                                    bytesToCopy-- // minimum count is three,
                                    s.window[outputWritePointer++] = s.window[copySourcePointer++]
                                    bytesToCopy-- // so unroll loop a little
                                } else {
                                    s.window.copyInto(s.window, outputWritePointer, copySourcePointer, copySourcePointer + 2)
                                    outputWritePointer += 2
                                    copySourcePointer += 2
                                    bytesToCopy -= 2
                                }
                            } else {
                                // Source offset crosses window boundary - handle wraparound
                                copySourcePointer = outputWritePointer - copyDistance
                                do {
                                    copySourcePointer += s.end // Force pointer within window
                                } while (copySourcePointer < 0) // Covers invalid distances
                                extraBitsOrOperation = s.end - copySourcePointer
                                if (bytesToCopy > extraBitsOrOperation) {
                                    // Source crosses window boundary - wrapped copy
                                    bytesToCopy -= extraBitsOrOperation
                                    if (outputWritePointer - copySourcePointer > 0 && extraBitsOrOperation > (outputWritePointer - copySourcePointer)) {
                                        do {
                                            s.window[outputWritePointer++] = s.window[copySourcePointer++]
                                        } while (--extraBitsOrOperation != 0)
                                    } else {
                                        s.window.copyInto(s.window, outputWritePointer, copySourcePointer, copySourcePointer + extraBitsOrOperation)
                                        outputWritePointer += extraBitsOrOperation
                                    }
                                    copySourcePointer = 0 // Copy rest from start of window
                                }
                            }

                            // Copy all remaining bytes or what's left
                            if (outputWritePointer - copySourcePointer > 0 && bytesToCopy > (outputWritePointer - copySourcePointer)) {
                                do {
                                    s.window[outputWritePointer++] = s.window[copySourcePointer++]
                                } while (--bytesToCopy != 0)
                            } else {
                                s.window.copyInto(s.window, outputWritePointer, copySourcePointer, copySourcePointer + bytesToCopy)
                                outputWritePointer += bytesToCopy
                            }
                            break
                        } else if ((extraBitsOrOperation and 64) == 0) {
                            // Another level of indirection needed for distance
                            tempPointer += tempTable[(tempTableIndex + tempPointer) * 3 + 2]
                            tempPointer += (bitBuffer and IBLK_INFLATE_MASK[extraBitsOrOperation])
                            extraBitsOrOperation = tempTable[(tempTableIndex + tempPointer) * 3]
                        } else {
                            // Invalid distance code
                            z.msg = "invalid distance code"

                            bytesToCopy = z.availIn - bytesAvailable
                            bytesToCopy = (bitsInBuffer ushr 3).coerceAtMost(bytesToCopy)
                            bytesAvailable += bytesToCopy
                            inputPointer -= bytesToCopy
                            bitsInBuffer -= bytesToCopy shl 3

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
                }

                if ((tempTable[(tempTableIndex + tempPointer) * 3] and 64) == 0) {
                    // Another level of indirection needed for literal/length
                    tempPointer += tempTable[(tempTableIndex + tempPointer) * 3 + 2]
                    tempPointer += (bitBuffer and IBLK_INFLATE_MASK[tempTable[(tempTableIndex + tempPointer) * 3]])
                    if (tempTable[(tempTableIndex + tempPointer) * 3] == 0) {
                        // Direct literal after indirection
                        val tableBits = tempTable[(tempTableIndex + tempPointer) * 3 + 1]
                        val literalValue = tempTable[(tempTableIndex + tempPointer) * 3 + 2]
                        
                        println("[FAST_INDIRECT] tempPointer=$tempPointer, tableIndex=${(tempTableIndex + tempPointer) * 3}, exop=${tempTable[(tempTableIndex + tempPointer) * 3]}, bits=$tableBits, base=$literalValue")
                        
                        bitBuffer = bitBuffer ushr tableBits
                        bitsInBuffer -= tableBits

                        println("[FAST_LITERAL_INDIRECT] Writing literal: $literalValue ('${literalValue.toChar()}') at position $outputWritePointer")
                        s.window[outputWritePointer++] = literalValue.toByte()
                        outputBytesLeft--
                        break
                    }
                } else if (tempTable[(tempTableIndex + tempPointer) * 3] and 32 != 0) {
                    // End of block marker found
                    bytesToCopy = z.availIn - bytesAvailable
                    bytesToCopy = (bitsInBuffer ushr 3).coerceAtMost(bytesToCopy)
                    bytesAvailable += bytesToCopy
                    inputPointer -= bytesToCopy
                    bitsInBuffer -= bytesToCopy shl 3

                    s.bitb = bitBuffer
                    s.bitk = bitsInBuffer
                    z.availIn = bytesAvailable
                    z.totalIn += inputPointer - z.nextInIndex
                    z.nextInIndex = inputPointer
                    s.write = outputWritePointer

                    return Z_STREAM_END
                } else {
                    // Invalid literal/length code
                    z.msg = "invalid literal/length code"

                    bytesToCopy = z.availIn - bytesAvailable
                    bytesToCopy = (bitsInBuffer ushr 3).coerceAtMost(bytesToCopy)
                    bytesAvailable += bytesToCopy
                    inputPointer -= bytesToCopy
                    bitsInBuffer -= bytesToCopy shl 3

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
        bytesToCopy = (bitsInBuffer ushr 3).coerceAtMost(bytesToCopy)
        bytesAvailable += bytesToCopy
        inputPointer -= bytesToCopy
        bitsInBuffer -= bytesToCopy shl 3

        s.bitb = bitBuffer
        s.bitk = bitsInBuffer
        z.availIn = bytesAvailable
        z.totalIn += inputPointer - z.nextInIndex
        z.nextInIndex = inputPointer
        s.write = outputWritePointer

        return Z_OK
    }

    companion object {
        // Constants previously defined here are now in ai.solace.zlib.common.Constants
        // inflate_mask is IBLK_INFLATE_MASK
        // Z_OK, Z_STREAM_END, etc. are already in common
        // Mode constants (START, LEN, etc.) are now ICODES_START, ICODES_LEN, etc. in Constants.kt
    }
}
