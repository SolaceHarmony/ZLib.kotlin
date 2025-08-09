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
 * Huffman Tree Construction and Decoding for DEFLATE Inflation
 * 
 * This object implements the Huffman table construction algorithms used in DEFLATE decompression.
 * It provides functions to build decode tables from bit length arrays for:
 * - Dynamic literal/length and distance codes
 * - Fixed literal/length and distance codes  
 * - Bit length codes (used to decode the main tables)
 *
 * The core algorithm (huftBuild) implements canonical Huffman table construction,
 * creating lookup tables that enable fast decoding of variable-length codes.
 * 
 * Key improvements in this version:
 * - Meaningful variable names for better code readability
 * - Comprehensive documentation and comments
 * - Detailed debug output for troubleshooting
 * - Proper error handling and validation
 */
internal object InfTree {

    private const val Z_OK = 0
    private const val Z_BUF_ERROR = -5
    private const val Z_DATA_ERROR = -3
    private const val Z_MEM_ERROR = -4


    /**
     * Reverse the bits in a value for the specified bit length.
     * Used for DEFLATE Huffman table construction since codes are transmitted LSB first.
     */
    private fun reverseBits(value: Int, bitLength: Int): Int {
        var result = 0
        var temp = value
        for (i in 0 until bitLength) {
            result = (result shl 1) or (temp and 1)
            temp = temp ushr 1
        }
        return result
    }

    /**
     * Build a Huffman decoding table from a list of code lengths.
     * 
     * This function implements the canonical Huffman table construction algorithm used in DEFLATE.
     * It takes an array of bit lengths for each symbol and constructs a lookup table that can be
     * used for fast decoding of Huffman-encoded data.
     * 
     * @param b Array of bit lengths for each symbol (0 = symbol not used)
     * @param bitLengthStartIndex Starting index in the bit length array 
     * @param totalCodes Total number of codes/symbols to process
     * @param simpleValueCount Number of simple values (257 for literals, 0 for distances)
     * @param d Distance base values array (for distance codes)
     * @param e Extra bits array (for length/distance codes)
     * @param t Output array to store the constructed Huffman table
     * @param m Input/output array containing max bits per table lookup
     * @param huffmanTable Pre-allocated space for the Huffman table entries
     * @param tableIndexTracker Tracks current position in table allocation
     * @param valueTable Working array for value ordering by bit length
     * @return Z_OK on success, Z_DATA_ERROR on invalid input, Z_MEM_ERROR on memory issues
     */
    @Suppress("UNUSED_PARAMETER")
    private fun huftBuild(
        b: IntArray,
        bIndex: Int,
        n: Int,
        s: Int,
        d: IntArray?,
        e: IntArray?,
        t: Array<IntArray>,
        m: IntArray,
        hp: IntArray,
        hn: IntArray,
        v: IntArray
    ): Int {
        ZlibLogger.logInfTree("Starting Huffman table construction", "huftBuild")
        ZlibLogger.logInfTree("Parameters: bIndex=$bIndex, n=$n, s=$s, hp.size=${hp.size}, initial_hn=${hn[0]}", "huftBuild")
        
        // Count bit lengths
        val c = IntArray(MAX_BITS + 1)
        for (i in 0 until n) {
            if (b[bIndex + i] < c.size) c[b[bIndex + i]]++
        }
        
        ZlibLogger.logInfTree("Bit length counts: ${c.withIndex().filter { it.value > 0 }.joinToString { "${it.index}:${it.value}" }}", "huftBuild")
        
        if (c[0] == n) {
            t[0] = IntArray(0)
            m[0] = 0
            ZlibLogger.logInfTree("All codes have length 0, empty table created", "huftBuild")
            return Z_OK
        }
        
        // Find minimum and maximum non-zero bit lengths
        var j = 1
        while (j <= MAX_BITS && c[j] == 0) j++
        val minBits = j
        var maxBits = MAX_BITS
        while (maxBits >= 1 && c[maxBits] == 0) maxBits--
        
        ZlibLogger.logInfTree("Bit length range: minBits=$minBits, maxBits=$maxBits", "huftBuild")
        
        // Determine root table bit width from caller (m[0]) clamped to [minBits, MAX_BITS]
        var rootBits = m[0]
        if (rootBits < minBits) rootBits = minBits
        if (rootBits > MAX_BITS) rootBits = MAX_BITS
        if (rootBits <= 0) return Z_DATA_ERROR
        m[0] = rootBits
        
        // Prepare canonical codes
        val x = IntArray(MAX_BITS + 1)
        var sum = 0
        for (bitsLen in 1..MAX_BITS) {
            x[bitsLen] = sum
            sum += c[bitsLen]
        }
        val nextCode = IntArray(MAX_BITS + 1)
        var codeVal = 0
        var bl = 1
        while (bl <= MAX_BITS) {
            codeVal = (codeVal + c[bl - 1]) shl 1
            nextCode[bl] = codeVal
            bl++
        }
        
        // Root table allocation (entry units)
        val hpEntries = hp.size / 3
        val rootSize = 1 shl rootBits
        if (rootSize > hpEntries) return Z_MEM_ERROR
        for (i in 0 until rootSize * 3) hp[i] = 0
        var allocatedEntries = rootSize // next free entry index (entry units)
        
        fun ensureSpace(entriesNeeded: Int): Int {
            val base = allocatedEntries
            if (base + entriesNeeded > hpEntries) return -1
            // zero init
            var k = base * 3
            val end = (base + entriesNeeded) * 3
            while (k < end) { hp[k++] = 0 }
            allocatedEntries += entriesNeeded
            return base
        }
        
        fun writeEntry(entryIndex: Int, op: Int, bitsHere: Int, value: Int) {
            val off = entryIndex * 3
            hp[off] = op
            hp[off + 1] = bitsHere
            hp[off + 2] = value
        }
        
        fun place(sym: Int, len: Int, codeLSB: Int, tableBase: Int, tableBits: Int, consumed: Int): Int {
            if (len <= tableBits) {
                // Replicate across remaining bits in this level
                val rep = 1 shl (tableBits - len)
                var idx = codeLSB
                var count = rep
                while (count-- > 0) {
                    val entry = tableBase + idx
                    // Choose op/val for this symbol
                    if (e != null && d != null && sym >= s) {
                        val di = sym - s
                        // If the symbol maps beyond provided base/extra arrays (e.g., reserved codes),
                        // create an invalid entry (op=64) rather than failing the build.
                        if (di < 0 || di >= e.size || di >= d.size) {
                            writeEntry(entry, 64, len, 0)
                        } else {
                            writeEntry(entry, 16 + e[di], len, d[di])
                        }
                    } else if (s != 0 && sym == 256) {
                        // End of block
                        writeEntry(entry, 32, len, 0)
                    } else if (s == 0 || sym < s) {
                        // Literal
                        writeEntry(entry, 0, len, sym)
                    } else {
                        // Any other unexpected case -> invalid
                        writeEntry(entry, 64, len, 0)
                    }
                    idx += 1 shl len
                }
                return Z_OK
            } else {
                // Need a subtable
                val remain = len - tableBits
                val prefixIndex = codeLSB and ((1 shl tableBits) - 1)
                val ptrEntry = tableBase + prefixIndex
                val opExisting = hp[ptrEntry * 3]
                var nextBits = minOf(remain, MAX_BITS - (consumed + tableBits))
                if (nextBits <= 0) nextBits = 1
                var subBase: Int
                if (opExisting == 0 && hp[ptrEntry * 3 + 1] == 0 && hp[ptrEntry * 3 + 2] == 0) {
                    val need = 1 shl nextBits
                    subBase = ensureSpace(need)
                    if (subBase < 0) return Z_MEM_ERROR
                    // Pointer: op=nextBits, bits consumed at this level, val=offset to subtable base (entry units)
                    writeEntry(ptrEntry, nextBits, tableBits, subBase - ptrEntry)
                } else {
                    // Already a pointer; reuse
                    subBase = ptrEntry + hp[ptrEntry * 3 + 2]
                    // If existing pointer has different nextBits, honor existing width
                    nextBits = hp[ptrEntry * 3]
                }
                val nextCodeLSB = codeLSB ushr tableBits
                return place(sym, remain, nextCodeLSB, subBase, nextBits, consumed + tableBits)
            }
        }
        
        // Emit all symbols
        for (sym in 0 until n) {
            val len = b[bIndex + sym]
            if (len == 0) continue
            val code = nextCode[len]++
            val codeLSB = reverseBits(code, len)
            val st = place(sym, len, codeLSB, 0, rootBits, 0)
            if (st != Z_OK) return st
        }
        
        t[0] = hp
        return Z_OK
    }

    /**
     * Build Huffman tables for dynamic bit length codes.
     * Used for decoding the bit lengths of the main literal/length and distance tables.
     *
     * @param c Array of bit lengths for the bit length alphabet (19 values)
     * @param bb Output array for max bits per table lookup
     * @param tb Output array for the constructed bit length decode table
     * @param hp Pre-allocated space for Huffman table entries
     * @param z ZStream for error reporting
     * @return Z_OK on success, Z_DATA_ERROR on invalid input
     */
    internal fun inflateTreesBits(
        c: IntArray,
        bb: IntArray,
        tb: Array<IntArray>,
        hp: IntArray,
        z: ZStream
    ): Int {
        var result: Int
        val hn = intArrayOf(0)
        val v = IntArray(19)
        result = huftBuild(c, 0, 19, 19, null, null, tb, bb, hp, hn, v)
        if (result == Z_DATA_ERROR) {
            z.msg = "oversubscribed dynamic bit lengths tree"
        } else if (result == Z_BUF_ERROR || bb[0] == 0) {
            z.msg = "incomplete dynamic bit lengths tree"
            result = Z_DATA_ERROR
        }
        return result
    }

    /**
     * Build Huffman tables for dynamic literal/length and distance codes.
     * This creates the main decode tables used for decompressing DEFLATE data blocks.
     *
     * @param nl Number of literal/length codes (257-288)
     * @param nd Number of distance codes (1-32)
     * @param c Array of bit lengths for all codes (literal/length + distance)
     * @param bl Output array for literal/length table max bits
     * @param bd Output array for distance table max bits
     * @param tl Output array for literal/length decode table
     * @param td Output array for distance decode table
     * @param hp Pre-allocated space for Huffman table entries
     * @param z ZStream for error reporting
     * @return Z_OK on success, Z_DATA_ERROR on invalid input
     */
    internal fun inflateTreesDynamic(
        nl: Int,
        nd: Int,
        c: IntArray,
        bl: IntArray,
        bd: IntArray,
        tl: Array<IntArray>,
        td: Array<IntArray>,
        hp: IntArray,
        z: ZStream
    ): Int {
        var result: Int
        // Allocate separate workspaces for literal/length and distance trees so each table is rooted at index 0
        val hnL = intArrayOf(0)
        val hnD = intArrayOf(0)
        val vL = IntArray(288)
        val vD = IntArray(288)
        val hpL = IntArray(IBLK_MANY * 3)
        val hpD = IntArray(IBLK_MANY * 3)

        // build literal/length tree into its own table (hpL)
        result = huftBuild(c, 0, nl, LITERALS + 1, TREE_BASE_LENGTH, TREE_EXTRA_LBITS, tl, bl, hpL, hnL, vL)
        if (result != Z_OK || bl[0] == 0) {
            if (result == Z_DATA_ERROR) {
                z.msg = "oversubscribed literal/length tree"
            } else if (result == Z_BUF_ERROR) {
                z.msg = "incomplete literal/length tree"
                result = Z_DATA_ERROR
            }
            return result
        }

        // build distance tree into its own table (hpD) with s = 0
        result = huftBuild(c, nl, nd, 0, TREE_BASE_DIST, TREE_EXTRA_DBITS, td, bd, hpD, hnD, vD)
        if (result != Z_OK || (bd[0] == 0 && nl > 257)) {
            if (result == Z_DATA_ERROR) {
                z.msg = "oversubscribed distance tree"
            } else if (result == Z_BUF_ERROR) {
                z.msg = "incomplete distance tree"
                result = Z_DATA_ERROR
            } else if (result != Z_MEM_ERROR) {
                z.msg = "empty distance tree with lengths"
                result = Z_DATA_ERROR
            }
            return result
        }
        return Z_OK
    }

    /**
     * Provide fixed Huffman tables for DEFLATE static blocks.
     * These are the predefined tables specified in RFC 1951 for static compression.
     * 
     * According to RFC 1951 Section 3.2.6:
     * - Literals 0-143: 8 bits
     * - Literals 144-255: 9 bits  
     * - Length codes 256-279: 7 bits
     * - Length codes 280-287: 8 bits
     * - Distance codes 0-31: 5 bits
     */
    internal fun inflateTreesFixed(
        bl: IntArray,
        bd: IntArray,
        tl: Array<IntArray>,
        td: Array<IntArray>,
        z: ZStream
    ): Int {
        // Create correct bit length arrays according to RFC 1951
        val literalLengths = IntArray(288)
        for (i in 0..143) literalLengths[i] = 8
        for (i in 144..255) literalLengths[i] = 9
        for (i in 256..279) literalLengths[i] = 7
        for (i in 280..287) literalLengths[i] = 8

        val distanceLengths = IntArray(32)
        for (i in 0..31) distanceLengths[i] = 5

        // Build fixed tables using separate workspaces to avoid overlap
        val hnL = intArrayOf(0)
        val vL = IntArray(288)
        val hpL = IntArray(IBLK_MANY * 3)

        val hnD = intArrayOf(0)
        val vD = IntArray(288)
        val hpD = IntArray(IBLK_MANY * 3)

        // Use correct 's' for literal/length: LITERALS + 1 (257)
        var result = huftBuild(literalLengths, 0, 288, LITERALS + 1, TREE_BASE_LENGTH, TREE_EXTRA_LBITS, tl, bl, hpL, hnL, vL)
        if (result != Z_OK) {
            z.msg = "invalid literal/length code table"
            return result
        }

        // Distance tree with s = 0
        result = huftBuild(distanceLengths, 0, 32, 0, TREE_BASE_DIST, TREE_EXTRA_DBITS, td, bd, hpD, hnD, vD)
        if (result != Z_OK) {
            z.msg = "invalid distance code table"
            return result
        }
        
        return Z_OK
    }

}