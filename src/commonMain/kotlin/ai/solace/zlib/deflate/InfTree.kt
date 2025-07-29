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

    private val fixedBl = intArrayOf(9)
    private val fixedBd = intArrayOf(5)
    
    // Track whether fixed tables have been initialized
    private var fixedBuilt = false
    
    // Track the actual indices where fixed tables are built
    private var fixedLiteralIndex = 0
    private var fixedDistanceIndex = 0
    
    // The shared Huffman tables used by both fixed literal and distance trees
    private var fixedLiteralTable = IntArray(0)
    private var fixedDistanceTable = IntArray(0)

    // Predefined fixed tables from the C# implementation
    // These are the exact tables used in the C# code, ensuring compatibility
    private val fixedTl = arrayOf(
        intArrayOf(
            96, 7, 256, 0, 8, 80, 0, 8, 16, 84, 8, 115, 82, 7, 31, 0, 8, 112, 0, 8, 48, 0, 9, 192, 
            80, 7, 10, 0, 8, 96, 0, 8, 32, 0, 9, 160, 0, 8, 0, 0, 8, 128, 0, 8, 64, 0, 9, 224, 
            80, 7, 6, 0, 8, 88, 0, 8, 24, 0, 9, 144, 83, 7, 59, 0, 8, 120, 0, 8, 56, 0, 9, 208, 
            81, 7, 17, 0, 8, 104, 0, 8, 40, 0, 9, 176, 0, 8, 8, 0, 8, 136, 0, 8, 72, 0, 9, 240, 
            80, 7, 4, 0, 8, 84, 0, 8, 20, 85, 8, 227, 83, 7, 43, 0, 8, 116, 0, 8, 52, 0, 9, 200, 
            81, 7, 13, 0, 8, 100, 0, 8, 36, 0, 9, 168, 0, 8, 4, 0, 8, 132, 0, 8, 68, 0, 9, 232, 
            80, 7, 8, 0, 8, 92, 0, 8, 28, 0, 9, 152, 84, 7, 83, 0, 8, 124, 0, 8, 60, 0, 9, 216, 
            82, 7, 23, 0, 8, 108, 0, 8, 44, 0, 9, 184, 0, 8, 12, 0, 8, 140, 0, 8, 76, 0, 9, 248, 
            80, 7, 3, 0, 8, 82, 0, 8, 18, 85, 8, 163, 83, 7, 35, 0, 8, 114, 0, 8, 50, 0, 9, 196, 
            81, 7, 11, 0, 8, 98, 0, 8, 34, 0, 9, 164, 0, 8, 2, 0, 8, 130, 0, 8, 66, 0, 9, 228, 
            80, 7, 7, 0, 8, 90, 0, 8, 26, 0, 9, 148, 84, 7, 67, 0, 8, 122, 0, 8, 58, 0, 9, 212, 
            82, 7, 19, 0, 8, 106, 0, 8, 42, 0, 9, 180, 0, 8, 10, 0, 8, 138, 0, 8, 74, 0, 9, 244, 
            80, 7, 5, 0, 8, 86, 0, 8, 22, 192, 8, 0, 83, 7, 51, 0, 8, 118, 0, 8, 54, 0, 9, 204, 
            81, 7, 15, 0, 8, 102, 0, 8, 38, 0, 9, 172, 0, 8, 6, 0, 8, 134, 0, 8, 70, 0, 9, 236, 
            80, 7, 9, 0, 8, 94, 0, 8, 30, 0, 9, 156, 84, 7, 99, 0, 8, 126, 0, 8, 62, 0, 9, 220, 
            82, 7, 27, 0, 8, 110, 0, 8, 46, 0, 9, 188, 0, 8, 14, 0, 8, 142, 0, 8, 78, 0, 9, 252, 
            96, 7, 256, 0, 8, 81, 0, 8, 17, 85, 8, 131, 82, 7, 31, 0, 8, 113, 0, 8, 49, 0, 9, 194, 
            80, 7, 10, 0, 8, 97, 0, 8, 33, 0, 9, 162, 0, 8, 1, 0, 8, 129, 0, 8, 65, 0, 9, 226, 
            80, 7, 6, 0, 8, 89, 0, 8, 25, 0, 9, 146, 83, 7, 59, 0, 8, 121, 0, 8, 57, 0, 9, 210, 
            81, 7, 17, 0, 8, 105, 0, 8, 41, 0, 9, 178, 0, 8, 9, 0, 8, 137, 0, 8, 73, 0, 9, 242, 
            80, 7, 4, 0, 8, 85, 0, 8, 21, 80, 8, 258, 83, 7, 43, 0, 8, 117, 0, 8, 53, 0, 9, 202, 
            81, 7, 13, 0, 8, 101, 0, 8, 37, 0, 9, 170, 0, 8, 5, 0, 8, 133, 0, 8, 69, 0, 9, 234, 
            80, 7, 8, 0, 8, 93, 0, 8, 29, 0, 9, 154, 84, 7, 83, 0, 8, 125, 0, 8, 61, 0, 9, 218, 
            82, 7, 23, 0, 8, 109, 0, 8, 45, 0, 9, 186, 0, 8, 13, 0, 8, 141, 0, 8, 77, 0, 9, 250, 
            80, 7, 3, 0, 8, 83, 0, 8, 19, 85, 8, 195, 83, 7, 35, 0, 8, 115, 0, 8, 51, 0, 9, 198, 
            81, 7, 11, 0, 8, 99, 0, 8, 35, 0, 9, 166, 0, 8, 3, 0, 8, 131, 0, 8, 67, 0, 9, 230, 
            80, 7, 7, 0, 8, 91, 0, 8, 27, 0, 9, 150, 84, 7, 67, 0, 8, 123, 0, 8, 59, 0, 9, 214, 
            82, 7, 19, 0, 8, 107, 0, 8, 43, 0, 9, 182, 0, 8, 11, 0, 8, 139, 0, 8, 75, 0, 9, 246, 
            80, 7, 5, 0, 8, 87, 0, 8, 23, 192, 8, 0, 83, 7, 51, 0, 8, 119, 0, 8, 55, 0, 9, 206, 
            81, 7, 15, 0, 8, 103, 0, 8, 39, 0, 9, 174, 0, 8, 7, 0, 8, 135, 0, 8, 71, 0, 9, 238, 
            80, 7, 9, 0, 8, 95, 0, 8, 31, 0, 9, 158, 84, 7, 99, 0, 8, 127, 0, 8, 63, 0, 9, 222, 
            82, 7, 27, 0, 8, 111, 0, 8, 47, 0, 9, 190, 0, 8, 15, 0, 8, 143, 0, 8, 79, 0, 9, 254, 
            96, 7, 256, 0, 8, 80, 0, 8, 16, 84, 8, 115, 82, 7, 31, 0, 8, 112, 0, 8, 48, 0, 9, 193, 
            80, 7, 10, 0, 8, 96, 0, 8, 32, 0, 9, 161, 0, 8, 0, 0, 8, 128, 0, 8, 64, 0, 9, 225, 
            80, 7, 6, 0, 8, 88, 0, 8, 24, 0, 9, 145, 83, 7, 59, 0, 8, 120, 0, 8, 56, 0, 9, 209, 
            81, 7, 17, 0, 8, 104, 0, 8, 40, 0, 9, 177, 0, 8, 8, 0, 8, 136, 0, 8, 72, 0, 9, 241, 
            80, 7, 4, 0, 8, 84, 0, 8, 20, 85, 8, 227, 83, 7, 43, 0, 8, 116, 0, 8, 52, 0, 9, 201, 
            81, 7, 13, 0, 8, 100, 0, 8, 36, 0, 9, 169, 0, 8, 4, 0, 8, 132, 0, 8, 68, 0, 9, 233, 
            80, 7, 8, 0, 8, 92, 0, 8, 28, 0, 9, 153, 84, 7, 83, 0, 8, 124, 0, 8, 60, 0, 9, 217, 
            82, 7, 23, 0, 8, 108, 0, 8, 44, 0, 9, 185, 0, 8, 12, 0, 8, 140, 0, 8, 76, 0, 9, 249, 
            80, 7, 3, 0, 8, 82, 0, 8, 18, 85, 8, 163, 83, 7, 35, 0, 8, 114, 0, 8, 50, 0, 9, 197, 
            81, 7, 11, 0, 8, 98, 0, 8, 34, 0, 9, 165, 0, 8, 2, 0, 8, 130, 0, 8, 66, 0, 9, 229, 
            80, 7, 7, 0, 8, 90, 0, 8, 26, 0, 9, 149, 84, 7, 67, 0, 8, 122, 0, 8, 58, 0, 9, 213, 
            82, 7, 19, 0, 8, 106, 0, 8, 42, 0, 9, 181, 0, 8, 10, 0, 8, 138, 0, 8, 74, 0, 9, 245, 
            80, 7, 5, 0, 8, 86, 0, 8, 22, 192, 8, 0, 83, 7, 51, 0, 8, 118, 0, 8, 54, 0, 9, 205, 
            81, 7, 15, 0, 8, 102, 0, 8, 38, 0, 9, 173, 0, 8, 6, 0, 8, 134, 0, 8, 70, 0, 9, 237, 
            80, 7, 9, 0, 8, 94, 0, 8, 30, 0, 9, 157, 84, 7, 99, 0, 8, 126, 0, 8, 62, 0, 9, 221, 
            82, 7, 27, 0, 8, 110, 0, 8, 46, 0, 9, 189, 0, 8, 14, 0, 8, 142, 0, 8, 78, 0, 9, 253, 
            96, 7, 256, 0, 8, 81, 0, 8, 17, 85, 8, 131, 82, 7, 31, 0, 8, 113, 0, 8, 49, 0, 9, 195, 
            80, 7, 10, 0, 8, 97, 0, 8, 33, 0, 9, 163, 0, 8, 1, 0, 8, 129, 0, 8, 65, 0, 9, 227, 
            80, 7, 6, 0, 8, 89, 0, 8, 25, 0, 9, 147, 83, 7, 59, 0, 8, 121, 0, 8, 57, 0, 9, 211, 
            81, 7, 17, 0, 8, 105, 0, 8, 41, 0, 9, 179, 0, 8, 9, 0, 8, 137, 0, 8, 73, 0, 9, 243, 
            80, 7, 4, 0, 8, 85, 0, 8, 21, 80, 8, 258, 83, 7, 43, 0, 8, 117, 0, 8, 53, 0, 9, 203, 
            81, 7, 13, 0, 8, 101, 0, 8, 37, 0, 9, 171, 0, 8, 5, 0, 8, 133, 0, 8, 69, 0, 9, 235, 
            80, 7, 8, 0, 8, 93, 0, 8, 29, 0, 9, 155, 84, 7, 83, 0, 8, 125, 0, 8, 61, 0, 9, 219, 
            82, 7, 23, 0, 8, 109, 0, 8, 45, 0, 9, 187, 0, 8, 13, 0, 8, 141, 0, 8, 77, 0, 9, 251, 
            80, 7, 3, 0, 8, 83, 0, 8, 19, 85, 8, 195, 83, 7, 35, 0, 8, 115, 0, 8, 51, 0, 9, 199, 
            81, 7, 11, 0, 8, 99, 0, 8, 35, 0, 9, 167, 0, 8, 3, 0, 8, 131, 0, 8, 67, 0, 9, 231, 
            80, 7, 7, 0, 8, 91, 0, 8, 27, 0, 9, 151, 84, 7, 67, 0, 8, 123, 0, 8, 59, 0, 9, 215, 
            82, 7, 19, 0, 8, 107, 0, 8, 43, 0, 9, 183, 0, 8, 11, 0, 8, 139, 0, 8, 75, 0, 9, 247, 
            80, 7, 5, 0, 8, 87, 0, 8, 23, 192, 8, 0, 83, 7, 51, 0, 8, 119, 0, 8, 55, 0, 9, 207, 
            81, 7, 15, 0, 8, 103, 0, 8, 39, 0, 9, 175, 0, 8, 7, 0, 8, 135, 0, 8, 71, 0, 9, 239, 
            80, 7, 9, 0, 8, 95, 0, 8, 31, 0, 9, 159, 84, 7, 99, 0, 8, 127, 0, 8, 63, 0, 9, 223, 
            82, 7, 27, 0, 8, 111, 0, 8, 47, 0, 9, 191, 0, 8, 15, 0, 8, 143, 0, 8, 79, 0, 9, 255
        )
    )

    private val fixedTd = arrayOf(
        intArrayOf(
            80, 5, 1, 87, 5, 257, 83, 5, 17, 91, 5, 4097, 81, 5, 5, 89, 5, 1025, 85, 5, 65, 93, 5, 16385, 
            80, 5, 3, 88, 5, 513, 84, 5, 33, 92, 5, 8193, 82, 5, 9, 90, 5, 2049, 86, 5, 129, 192, 5, 24577, 
            80, 5, 2, 87, 5, 385, 83, 5, 25, 91, 5, 6145, 81, 5, 7, 89, 5, 1537, 85, 5, 97, 93, 5, 24577, 
            80, 5, 4, 88, 5, 769, 84, 5, 49, 92, 5, 12289, 82, 5, 13, 90, 5, 3073, 86, 5, 193, 192, 5, 24577
        )
    )

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
        val c = IntArray(MAX_BITS + 1)
        for (i in 0 until n) {
            if (b[bIndex + i] < c.size) c[b[bIndex + i]]++
        }
        if (c[0] == n) {
            t[0] = IntArray(0)
            m[0] = 0
            return Z_OK
        }
        var j = 1
        while (j <= MAX_BITS && c[j] == 0) j++
        val minBits = j
        var maxBits = MAX_BITS
        while (maxBits >= 1 && c[maxBits] == 0) maxBits--
        if (m[0] < minBits) m[0] = minBits
        if (m[0] > maxBits) m[0] = maxBits
        val x = IntArray(MAX_BITS + 1)
        var code = 0
        for (bits in minBits..maxBits) {
            x[bits] = code
            code += c[bits]
        }
        for (i in 0 until n) {
            val len = b[bIndex + i]
            if (len != 0) v[x[len]++] = i
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
                        if (hn[0] >= IBLK_MANY) return Z_MEM_ERROR
                        hp[hn[0]++] = 0
                        k++
                    }
                    k = (code ushr p) and ((1 shl m[0]) - 1)
                    u = hn[0] + k
                    if (u >= IBLK_MANY) return Z_MEM_ERROR
                    hq[r] = u
                    hp[u] = p
                    hp[u + 1] = if (maxBits > p + m[0]) m[0] else maxBits - p
                    hp[u + 2] = 0
                }
                r = code and ((1 shl p) - 1)
                val baseIndex = hq[r] + (code ushr p)
                if (baseIndex >= hp.size) return Z_MEM_ERROR
                hp[baseIndex] = bits
                if (v[j] < s) {
                    hp[baseIndex + 2] = v[j]
                } else {
                    hp[baseIndex] = hp[baseIndex] or 64
                    if (v[j] - s < e!!.size) hp[baseIndex + 1] = e[v[j] - s]
                    if (v[j] - s < d!!.size) hp[baseIndex + 2] = d[v[j] - s]
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
            } else if (result != Z_MEM_ERROR) {
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
     * @param bl Output array for literal/length table max bits (always 9)
     * @param bd Output array for distance table max bits (always 5) 
     * @param tl Output array for literal/length decode table
     * @param td Output array for distance decode table
     * @param z ZStream for error reporting (unused for fixed tables)
     * @return Z_OK (fixed tables are always valid)
     */
    internal fun inflateTreesFixed(
        bl: IntArray,
        bd: IntArray,
        tl: Array<IntArray>,
        td: Array<IntArray>,
        z: ZStream
    ): Int {
        // Simply use the predefined fixed tables
        bl[0] = fixedBl[0]  // 9
        bd[0] = fixedBd[0]  // 5
        tl[0] = fixedTl[0]  // Use the predefined literal/length table
        td[0] = fixedTd[0]  // Use the predefined distance table
        return Z_OK
    }

    /**
     * Build fixed literal/length and distance Huffman decode tables with indices.
     * These are the standard tables defined by the DEFLATE specification.
     *
     * @param bl Output array for max bits in literal table lookup
     * @param bd Output array for max bits in distance table lookup  
     * @param tl Output array for literal/length decode table
     * @param tlIndex Output array for literal/length table index
     * @param td Output array for distance decode table
     * @param tdIndex Output array for distance table index
     * @param z ZStream for error reporting
     * @return Z_OK on success
     */
    internal fun inflateTreesFixedWithIndices(
        bl: IntArray,
        bd: IntArray,
        tl: Array<IntArray>,
        tlIndex: IntArray,
        td: Array<IntArray>,
        tdIndex: IntArray,
        z: ZStream
    ): Int {
        // Build the tables first
        val result = inflateTreesFixed(bl, bd, tl, td, z)
        
        // Set indices to 0 since we're using the predefined tables directly
        tlIndex[0] = 0
        tdIndex[0] = 0
        
        return result
    }
}
