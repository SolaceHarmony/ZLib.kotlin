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
        
        if (m[0] < minBits) m[0] = minBits
        if (m[0] > maxBits) m[0] = maxBits
        
        // Create starting code values for each bit length (canonical Huffman)
        val x = IntArray(MAX_BITS + 1)
        var code = 0
        for (bits in minBits..maxBits) {
            x[bits] = code
            code += c[bits]
            ZlibLogger.logInfTree("Bit length $bits: starting index=${x[bits]}, count=${c[bits]}", "huftBuild")
        }
        
        // Order symbols by increasing bit length
        for (i in 0 until n) {
            val len = b[bIndex + i]
            if (len != 0) {
                v[x[len]++] = i
                ZlibLogger.logInfTree("Symbol $i assigned bit length $len", "huftBuild")
            }
        }
        x[0] = 0
        j = 0
        var k = 0
        var p = -m[0]
        val hq = IntArray(MAX_BITS + 1)
        var r = 0
        var u: Int
        for (bits in minBits..maxBits) {
            var a = c[bits]
            while (a-- > 0) {
                while (bits > p + m[0]) {
                    p += m[0]
                    k = (if (k >= hn[0]) -1 else hq[r] + (code and ((1 shl m[0]) - 1)))
                    r = k
                    while (k < (1 shl (p - m[0]))) {
                        ZlibLogger.log("[DEBUG_LOG] huftBuild: Before hp[hn[0]++] = 0. hn[0]=${hn[0]}, hp.size=${hp.size}")
                        if (hn[0] >= hp.size) {
                            ZlibLogger.log("[DEBUG_LOG] huftBuild: Z_MEM_ERROR - hn[0] (${hn[0]}) >= hp.size (${hp.size})")
                            return Z_MEM_ERROR
                        }
                        hp[hn[0]++] = 0
                        k++
                    }
                    k = (code ushr p) and ((1 shl m[0]) - 1)
                    u = hn[0] + k
                    ZlibLogger.log("[DEBUG_LOG] huftBuild: Before hp[u] = p. u=$u, hn[0]=${hn[0]}, k=$k, hp.size=${hp.size}")
                    if (u >= hp.size) {
                        ZlibLogger.log("[DEBUG_LOG] huftBuild: Z_MEM_ERROR - u ($u) >= hp.size (${hp.size})")
                        return Z_MEM_ERROR
                    }
                    hp[u] = p
                    ZlibLogger.log("[DEBUG_LOG] huftBuild: Before hp[u + 1] = .... u=$u, hp.size=${hp.size}")
                    if (u + 1 >= hp.size) {
                        ZlibLogger.log("[DEBUG_LOG] huftBuild: Z_MEM_ERROR - u+1 (${u + 1}) >= hp.size (${hp.size})")
                        return Z_MEM_ERROR
                    }
                    hp[u + 1] = if (maxBits > p + m[0]) m[0] else maxBits - p
                    ZlibLogger.log("[DEBUG_LOG] huftBuild: Before hp[u + 2] = 0. u=$u, hp.size=${hp.size}")
                    if (u + 2 >= hp.size) {
                        ZlibLogger.log("[DEBUG_LOG] huftBuild: Z_MEM_ERROR - u+2 (${u + 2}) >= hp.size (${hp.size})")
                        return Z_MEM_ERROR
                    }
                    hp[u + 2] = 0
                }
                r = code and ((1 shl p) - 1)
                val baseIndex = hq[r] + (code ushr p)
                ZlibLogger.log("[DEBUG_LOG] huftBuild: Before hp[baseIndex] = bits. baseIndex=$baseIndex, hp.size=${hp.size}")
                if (baseIndex >= hp.size) {
                    ZlibLogger.log("[DEBUG_LOG] huftBuild: Z_MEM_ERROR - baseIndex ($baseIndex) >= hp.size (${hp.size})")
                    return Z_MEM_ERROR
                }
                hp[baseIndex] = bits
                if (v[j] < s) {
                    ZlibLogger.log("[DEBUG_LOG] huftBuild: Before hp[baseIndex + 2] = v[j]. baseIndex=$baseIndex, hp.size=${hp.size}")
                    if (baseIndex + 2 >= hp.size) {
                        ZlibLogger.log("[DEBUG_LOG] huftBuild: Z_MEM_ERROR - baseIndex+2 (${baseIndex + 2}) >= hp.size (${hp.size})")
                        return Z_MEM_ERROR
                    }
                    hp[baseIndex + 2] = v[j]
                } else {
                    ZlibLogger.log("[DEBUG_LOG] huftBuild: Before hp[baseIndex] = hp[baseIndex] or 64. baseIndex=$baseIndex, hp.size=${hp.size}")
                    if (baseIndex >= hp.size) {
                        ZlibLogger.log("[DEBUG_LOG] huftBuild: Z_MEM_ERROR - baseIndex ($baseIndex) >= hp.size (${hp.size})")
                        return Z_MEM_ERROR
                    }
                    hp[baseIndex] = hp[baseIndex] or 64
                    if (e != null) {
                        ZlibLogger.log("[DEBUG_LOG] huftBuild: Before hp[baseIndex + 1] = e[v[j] - s]. baseIndex=$baseIndex, hp.size=${hp.size}, v[j]-s=${v[j]-s}, e.size=${e.size}")
                        if (baseIndex + 1 >= hp.size) {
                            ZlibLogger.log("[DEBUG_LOG] huftBuild: Z_MEM_ERROR - baseIndex+1 (${baseIndex + 1}) >= hp.size (${hp.size})")
                            return Z_MEM_ERROR
                        }
                        if (v[j] - s >= e.size || v[j] - s < 0) {
                            ZlibLogger.log("[DEBUG_LOG] huftBuild: Z_DATA_ERROR - v[j]-s (${v[j]-s}) out of bounds for e.size (${e.size})")
                            return Z_DATA_ERROR
                        }
                        hp[baseIndex + 1] = e[v[j] - s]
                    }
                    if (d != null) {
                        ZlibLogger.log("[DEBUG_LOG] huftBuild: Before hp[baseIndex + 2] = d[v[j] - s]. baseIndex=$baseIndex, hp.size=${hp.size}, v[j]-s=${v[j]-s}, d.size=${d.size}")
                        if (baseIndex + 2 >= hp.size) {
                            ZlibLogger.log("[DEBUG_LOG] huftBuild: Z_MEM_ERROR - baseIndex+2 (${baseIndex + 2}) >= hp.size (${hp.size})")
                            return Z_MEM_ERROR
                        }
                        if (v[j] - s >= d.size || v[j] - s < 0) {
                            ZlibLogger.log("[DEBUG_LOG] huftBuild: Z_DATA_ERROR - v[j]-s (${v[j]-s}) out of bounds for d.size (${d.size})")
                            return Z_DATA_ERROR
                        }
                        hp[baseIndex + 2] = d[v[j] - s]
                    }
                }
                j++
                var y = 1 shl (bits - 1)
                while ((code and y) != 0) {
                    code = code xor y
                    y = y ushr 1
                }
                code = code xor y
            }
        }
        t[0] = hp
        m[0] = p
        ZlibLogger.log("[DEBUG_LOG] huftBuild finished. t[0].size=${t[0].size}, m[0]=${m[0]}")
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
        val hn = intArrayOf(0)
        val v = IntArray(288)

        // build literal/length tree
        result = huftBuild(c, 0, nl, L_CODES, TREE_EXTRA_LBITS, TREE_BASE_LENGTH, tl, bl, hp, hn, v)
        if (result != Z_OK || bl[0] == 0) {
            if (result == Z_DATA_ERROR) {
                z.msg = "oversubscribed literal/length tree"
            }
            // Removed else if (result != Z_MEM_ERROR) { ... } as it was redundant
            else if (result == Z_BUF_ERROR) {
                z.msg = "incomplete literal/length tree"
                result = Z_DATA_ERROR
            }
            return result
        }

        // build distance tree
        result = huftBuild(c, nl, nd, 0, TREE_EXTRA_DBITS, TREE_BASE_DIST, td, bd, hp, hn, v)
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

        // build fixed tables
        val hn = intArrayOf(0) // huft_build() scratch area
        val v = IntArray(288) // work area for huft_build()
        val hp = IntArray(IBLK_MANY * 3) // shared workspace for Huffman construction

        var result = huftBuild(literalLengths, 0, 288, L_CODES, TREE_EXTRA_LBITS, TREE_BASE_LENGTH, tl, bl, hp, hn, v)
        if (result != Z_OK) {
            z.msg = "invalid literal/length code table"
            return result
        }

        result = huftBuild(distanceLengths, 0, 32, 0, TREE_EXTRA_DBITS, TREE_BASE_DIST, td, bd, hp, hn, v)
        if (result != Z_OK) {
            z.msg = "invalid distance code table"
            return result
        }
        
        return Z_OK
    }

}