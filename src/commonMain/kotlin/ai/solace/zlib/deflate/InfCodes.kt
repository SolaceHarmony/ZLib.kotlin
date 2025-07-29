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
                    bitsNeeded = lbits.toInt()
                    tree = ltree
                    treeIndex = ltreeIndex
                    mode = ICODES_LEN
                    
                    // Fall through to ICODES_LEN state
                    if (outputBytesLeft >= 258 && bytesAvailable >= 10) {
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
                        if (result != Z_OK) {
                            mode = if (result == Z_STREAM_END) ICODES_WASH else ICODES_BADCODE
                            break
                        }
                    }
                    
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
                        bitBuffer = bitBuffer or ((z.nextIn!![inputPointer++].toInt() and 0xff) shl bitsInBuffer)
                        bitsInBuffer += 8
                    }
                    
                    // Get the table index
                    tableIndex = (treeIndex + (bitBuffer and IBLK_INFLATE_MASK[tempStorage])) * 3
                    
                    // Remove the bits we've used
                    bitBuffer = bitBuffer ushr tree[tableIndex + 1]
                    bitsInBuffer -= tree[tableIndex + 1]
                    
                    // Get the operation/extra bits
                    extraBitsOrOperation = tree[tableIndex]
                    
                    // Process based on the operation
                    if (extraBitsOrOperation == 0) {
                        // Literal
                        literal = tree[tableIndex + 2]
                        mode = ICODES_LIT
                        break
                    }
                    
                    if ((extraBitsOrOperation and 16) != 0) {
                        // Length
                        extraBitsNeeded = extraBitsOrOperation and 15
                        length = tree[tableIndex + 2]
                        mode = ICODES_LENEXT
                        break
                    }
                    
                    if ((extraBitsOrOperation and 64) == 0) {
                        // Next table
                        bitsNeeded = extraBitsOrOperation
                        treeIndex = tableIndex / 3 + tree[tableIndex + 2]
                        break
                    }
                    
                    if ((extraBitsOrOperation and 32) != 0) {
                        // End of block
                        mode = ICODES_WASH
                        break
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

                ICODES_LEN -> {
                    if (outputBytesLeft >= 258 && bytesAvailable >= 10) {
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
                        if (result != Z_OK) {
                            mode = if (result == Z_STREAM_END) ICODES_WASH else ICODES_BADCODE
                            break
                        }
                    }
                    
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
                        bitBuffer = bitBuffer or ((z.nextIn!![inputPointer++].toInt() and 0xff) shl bitsInBuffer)
                        bitsInBuffer += 8
                    }
                    
                    // Get the table index
                    tableIndex = (treeIndex + (bitBuffer and IBLK_INFLATE_MASK[tempStorage])) * 3
                    
                    // Remove the bits we've used
                    bitBuffer = bitBuffer ushr tree[tableIndex + 1]
                    bitsInBuffer -= tree[tableIndex + 1]
                    
                    // Get the operation/extra bits
                    extraBitsOrOperation = tree[tableIndex]
                    
                    // Process based on the operation
                    if (extraBitsOrOperation == 0) {
                        // Literal
                        literal = tree[tableIndex + 2]
                        mode = ICODES_LIT
                        break
                    }
                    
                    if ((extraBitsOrOperation and 16) != 0) {
                        // Length
                        extraBitsNeeded = extraBitsOrOperation and 15
                        length = tree[tableIndex + 2]
                        mode = ICODES_LENEXT
                        break
                    }
                    
                    if ((extraBitsOrOperation and 64) == 0) {
                        // Next table
                        bitsNeeded = extraBitsOrOperation
                        treeIndex = tableIndex / 3 + tree[tableIndex + 2]
                        break
                    }
                    
                    if ((extraBitsOrOperation and 32) != 0) {
                        // End of block
                        mode = ICODES_WASH
                        break
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
        ZlibLogger.debug("[FAST_DEBUG] inflateFast called with outputWrite=${s.write}, windowSize=${s.end}")
        ZlibLogger.debug("[FAST_DEBUG] Initial window content at write position: '${if (s.write < s.window.size) s.window[s.write].toInt().toChar() else '?'}' (${if (s.write < s.window.size) s.window[s.write].toInt() else -1})")
        ZlibLogger.debug("[FAST_DEBUG] Initial bit buffer: 0x${s.bitb.toString(16)}, bits: ${s.bitk}")
        
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

            ZlibLogger.debug("[BIT_DEBUG] bitBuffer=0x${bitBuffer.toString(16)}, bitsInBuffer=$bitsInBuffer, literalLengthMask=0x${literalLengthMask.toString(16)}")
            tempPointer = bitBuffer and literalLengthMask
            tempTable = tl
            tempTableIndex = tlIndex
            
            println("[DEBUG_LOG] InfCodes.inflateFast: tempTableIndex=$tempTableIndex, tempPointer=$tempPointer, tempTable.size=${tempTable.size}")
            
            // Check if the index is within bounds
            val index = (tempTableIndex + tempPointer) * 3
            if (index < 0 || index >= tempTable.size) {
                println("[DEBUG_LOG] ArrayIndexOutOfBoundsException prevented: index=$index, tempTable.size=${tempTable.size}")
                z.msg = "Array index out of bounds"
                return Z_DATA_ERROR
            }
            
            var temp = tempTable[index]
            if (temp == 0) {
                // Check if the indices for the other array accesses are within bounds
                val index1 = (tempTableIndex + tempPointer) * 3 + 1
                val index2 = (tempTableIndex + tempPointer) * 3 + 2
                
                if (index1 < 0 || index1 >= tempTable.size || index2 < 0 || index2 >= tempTable.size) {
                    println("[DEBUG_LOG] ArrayIndexOutOfBoundsException prevented: index1=$index1, index2=$index2, tempTable.size=${tempTable.size}")
                    z.msg = "Array index out of bounds"
                    return Z_DATA_ERROR
                }
                
                bitBuffer = bitBuffer ushr tempTable[index1]
                bitsInBuffer -= tempTable[index1]
                
                // Check if outputWritePointer is within bounds
                if (outputWritePointer < 0 || outputWritePointer >= s.window.size) {
                    println("[DEBUG_LOG] ArrayIndexOutOfBoundsException prevented: outputWritePointer=$outputWritePointer, s.window.size=${s.window.size}")
                    z.msg = "Array index out of bounds"
                    return Z_DATA_ERROR
                }
                
                s.window[outputWritePointer++] = tempTable[index2].toByte()
                outputBytesLeft--
                continue
            }

            do {
                tempPointer = tempTableIndex + (bitBuffer and literalLengthMask)
                extraBitsOrOperation = tl[tempPointer * 3]
                if (extraBitsOrOperation == 0) {
                    bitBuffer = bitBuffer ushr tl[tempPointer * 3 + 1]
                    bitsInBuffer -= tl[tempPointer * 3 + 1]
                    s.window[outputWritePointer++] = tl[tempPointer * 3 + 2].toByte()
                    outputBytesLeft--
                    break
                }
                bitBuffer = bitBuffer ushr tl[tempPointer * 3 + 1]
                bitsInBuffer -= tl[tempPointer * 3 + 1]
                if (extraBitsOrOperation and 16 != 0) {
                    extraBitsNeeded = extraBitsOrOperation and 15
                    bytesToCopy = tl[tempPointer * 3 + 2] + (bitBuffer and IBLK_INFLATE_MASK[extraBitsNeeded])
                    bitBuffer = bitBuffer ushr extraBitsNeeded
                    bitsInBuffer -= extraBitsNeeded
                    while (bitsInBuffer < 15) {
                        bytesAvailable--
                        bitBuffer = bitBuffer or ((z.nextIn!![inputPointer++].toInt() and 0xff) shl bitsInBuffer)
                        bitsInBuffer += 8
                    }
                    println("[DEBUG_LOG] Processing distance code: bitBuffer=$bitBuffer, distanceMask=$distanceMask, tdIndex=$tdIndex")
                    
                    // Check if tdIndex + (bitBuffer and distanceMask) is within bounds
                    val distancePointer = tdIndex + (bitBuffer and distanceMask)
                    println("[DEBUG_LOG] Distance pointer: $distancePointer, td.size=${td.size}")
                    
                    if (distancePointer < 0 || distancePointer * 3 >= td.size) {
                        println("[DEBUG_LOG] Distance pointer out of bounds: $distancePointer * 3 = ${distancePointer * 3}, td.size=${td.size}")
                        z.msg = "invalid distance code - pointer out of bounds"
                        s.bitb = bitBuffer
                        s.bitk = bitsInBuffer
                        z.availIn = bytesAvailable
                        z.totalIn += inputPointer - z.nextInIndex
                        z.nextInIndex = inputPointer
                        s.write = outputWritePointer
                        return Z_DATA_ERROR
                    }
                    
                    tempPointer = distancePointer
                    extraBitsOrOperation = td[tempPointer * 3]
                    println("[DEBUG_LOG] extraBitsOrOperation=$extraBitsOrOperation")
                    
                    do {
                        // Check if tempPointer * 3 + 1 and tempPointer * 3 + 2 are within bounds
                        if (tempPointer * 3 + 2 >= td.size) {
                            println("[DEBUG_LOG] tempPointer out of bounds: tempPointer=$tempPointer, index=${tempPointer * 3 + 2}, td.size=${td.size}")
                            z.msg = "invalid distance code - table index out of bounds"
                            s.bitb = bitBuffer
                            s.bitk = bitsInBuffer
                            z.availIn = bytesAvailable
                            z.totalIn += inputPointer - z.nextInIndex
                            z.nextInIndex = inputPointer
                            s.write = outputWritePointer
                            return Z_DATA_ERROR
                        }
                        
                        bitBuffer = bitBuffer ushr td[tempPointer * 3 + 1]
                        bitsInBuffer -= td[tempPointer * 3 + 1]
                        
                        if (extraBitsOrOperation and 16 != 0) {
                            println("[DEBUG_LOG] Distance code with extra bits")
                            extraBitsNeeded = extraBitsOrOperation and 15
                            while (bitsInBuffer < extraBitsNeeded) {
                                if (bytesAvailable <= 0) {
                                    println("[DEBUG_LOG] Not enough input bytes for extra bits")
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
                                bitBuffer = bitBuffer or ((z.nextIn!![inputPointer++].toInt() and 0xff) shl bitsInBuffer)
                                bitsInBuffer += 8
                            }
                            
                            copyDistance = td[tempPointer * 3 + 2] + (bitBuffer and IBLK_INFLATE_MASK[extraBitsNeeded])
                            println("[DEBUG_LOG] copyDistance=$copyDistance")
                            
                            bitBuffer = bitBuffer ushr extraBitsNeeded
                            bitsInBuffer -= extraBitsNeeded
                            outputBytesLeft -= bytesToCopy
                            copySourcePointer = outputWritePointer - copyDistance
                            
                            // Check if copyDistance is valid
                            if (copyDistance <= 0 || copyDistance > s.end) {
                                println("[DEBUG_LOG] Invalid copy distance: $copyDistance, s.end=${s.end}")
                                z.msg = "invalid distance code - distance too large"
                                s.bitb = bitBuffer
                                s.bitk = bitsInBuffer
                                z.availIn = bytesAvailable
                                z.totalIn += inputPointer - z.nextInIndex
                                z.nextInIndex = inputPointer
                                s.write = outputWritePointer
                                return Z_DATA_ERROR
                            }
                            
                            while (copySourcePointer < 0) copySourcePointer += s.end
                            
                            println("[DEBUG_LOG] Copying $bytesToCopy bytes from position $copySourcePointer to $outputWritePointer")
                            
                            while (bytesToCopy-- > 0) {
                                // Check if copySourcePointer and outputWritePointer are within bounds
                                if (copySourcePointer < 0 || copySourcePointer >= s.window.size || 
                                    outputWritePointer < 0 || outputWritePointer >= s.window.size) {
                                    println("[DEBUG_LOG] Copy pointers out of bounds: copySourcePointer=$copySourcePointer, outputWritePointer=$outputWritePointer, s.window.size=${s.window.size}")
                                    z.msg = "invalid distance code - copy pointers out of bounds"
                                    s.bitb = bitBuffer
                                    s.bitk = bitsInBuffer
                                    z.availIn = bytesAvailable
                                    z.totalIn += inputPointer - z.nextInIndex
                                    z.nextInIndex = inputPointer
                                    s.write = outputWritePointer
                                    return Z_DATA_ERROR
                                }
                                
                                s.window[outputWritePointer++] = s.window[copySourcePointer++]
                                if (copySourcePointer == s.end) copySourcePointer = 0
                            }
                            break
                        } else if (extraBitsOrOperation and 64 == 0) {
                            println("[DEBUG_LOG] Next table reference")
                            
                            // Check if td[tempPointer * 3 + 2] is valid
                            if (tempPointer * 3 + 2 >= td.size) {
                                println("[DEBUG_LOG] Table reference out of bounds: index=${tempPointer * 3 + 2}, td.size=${td.size}")
                                z.msg = "invalid distance code - table reference out of bounds"
                                s.bitb = bitBuffer
                                s.bitk = bitsInBuffer
                                z.availIn = bytesAvailable
                                z.totalIn += inputPointer - z.nextInIndex
                                z.nextInIndex = inputPointer
                                s.write = outputWritePointer
                                return Z_DATA_ERROR
                            }
                            
                            val nextTablePointer = tdIndex + td[tempPointer * 3 + 2] + (bitBuffer and IBLK_INFLATE_MASK[extraBitsOrOperation])
                            
                            // Check if nextTablePointer is valid
                            if (nextTablePointer < 0 || nextTablePointer * 3 >= td.size) {
                                println("[DEBUG_LOG] Next table pointer out of bounds: $nextTablePointer * 3 = ${nextTablePointer * 3}, td.size=${td.size}")
                                z.msg = "invalid distance code - next table pointer out of bounds"
                                s.bitb = bitBuffer
                                s.bitk = bitsInBuffer
                                z.availIn = bytesAvailable
                                z.totalIn += inputPointer - z.nextInIndex
                                z.nextInIndex = inputPointer
                                s.write = outputWritePointer
                                return Z_DATA_ERROR
                            }
                            
                            tempPointer = nextTablePointer
                            extraBitsOrOperation = td[tempPointer * 3]
                            println("[DEBUG_LOG] New extraBitsOrOperation=$extraBitsOrOperation")
                        } else {
                            println("[DEBUG_LOG] Invalid distance code: extraBitsOrOperation=$extraBitsOrOperation")
                            z.msg = "invalid distance code"
                            val errBytes = z.availIn - bytesAvailable - (bitsInBuffer shr 3)
                            bytesAvailable += errBytes
                            inputPointer -= errBytes
                            bitsInBuffer -= errBytes shl 3
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
                if (extraBitsOrOperation and 64 == 0) {
                    tempPointer = tlIndex + tl[tempPointer * 3 + 2] + (bitBuffer and IBLK_INFLATE_MASK[extraBitsOrOperation])
                    extraBitsOrOperation = tl[tempPointer * 3]
                    if (extraBitsOrOperation == 0) {
                        bitBuffer = bitBuffer ushr tl[tempPointer * 3 + 1]
                        bitsInBuffer -= tl[tempPointer * 3 + 1]
                        s.window[outputWritePointer++] = tl[tempPointer * 3 + 2].toByte()
                        outputBytesLeft--
                        break
                    }
                } else if (extraBitsOrOperation and 32 != 0) {
                    val errBytes = z.availIn - bytesAvailable - (bitsInBuffer shr 3)
                    bytesAvailable += errBytes
                    inputPointer -= errBytes
                    bitsInBuffer -= errBytes shl 3
                    s.bitb = bitBuffer
                    s.bitk = bitsInBuffer
                    z.availIn = bytesAvailable
                    z.totalIn += inputPointer - z.nextInIndex
                    z.nextInIndex = inputPointer
                    s.write = outputWritePointer
                    return Z_STREAM_END
                } else {
                    z.msg = "invalid literal/length code"
                    val errBytes = z.availIn - bytesAvailable - (bitsInBuffer shr 3)
                    bytesAvailable += errBytes
                    inputPointer -= errBytes
                    bitsInBuffer -= errBytes shl 3
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
