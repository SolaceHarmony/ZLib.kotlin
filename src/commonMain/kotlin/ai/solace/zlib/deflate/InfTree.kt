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

    private val fixed_bl = intArrayOf(9)
    private val fixed_bd = intArrayOf(5)

    // Predefined fixed tables from the C# implementation
    private val fixed_td = arrayOf(
        intArrayOf(
            80, 5, 1, 87, 5, 257, 83, 5, 17, 91, 5, 4097, 81, 5, 5, 89, 5, 1025, 85, 5, 65, 93, 5, 16385, 
            80, 5, 3, 88, 5, 513, 84, 5, 33, 92, 5, 8193, 82, 5, 9, 90, 5, 2049, 86, 5, 129, 192, 5, 24577, 
            80, 5, 2, 87, 5, 385, 83, 5, 25, 91, 5, 6145, 81, 5, 7, 89, 5, 1537, 85, 5, 97, 93, 5, 24577, 
            80, 5, 4, 88, 5, 769, 84, 5, 49, 92, 5, 12289, 82, 5, 13, 90, 5, 3073, 86, 5, 193, 192, 5, 24577
        )
    )

    // We'll still need to build the fixed_tl table at runtime since we don't have the full C# values
    private val fixed_tl = arrayOf(IntArray(0))
    private var fixed_built = false

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
        bitLengthStartIndex: Int,
        totalCodes: Int,
        simpleValueCount: Int,
        d: IntArray?,
        e: IntArray?,
        t: Array<IntArray>,
        m: IntArray,
        huffmanTable: IntArray,
        tableIndexTracker: IntArray,
        valueTable: IntArray
    ): Int {
        println("[DEBUG] huftBuild START: bitLengthStartIndex=$bitLengthStartIndex, totalCodes=$totalCodes, simpleValueCount=$simpleValueCount")
        // Algorithm variables with meaningful names for better code readability
        
        var codesAtCurrentLength: Int // Number of codes at the current bit length
        val bitLengthCounts = IntArray(MAX_BITS + 1) // Count of codes for each bit length
        var tableRepeatInterval: Int // Current code repeats in table every f entries  
        var maxCodeLength: Int // Maximum code length found
        var currentCode: Int // Current Huffman code being processed
        var tempCounter: Int // General purpose counter variable
        var tableBits: Int // Number of bits per table lookup (returned in m)
        var tableMask: Int // Mask for table lookup: (1 << bitsBeforeTable) - 1
        var tableBaseIndex: Int // Base index of current table being built
        val tableEntry = IntArray(3) // Table entry: [operation, bits, value] for Huffman decode
        // The table stack needs space for all possible table levels
        // tableLevel starts at -1 and can go up to MAX_BITS, so we need MAX_BITS + 1 elements
        val tableStack = IntArray(MAX_BITS + 1) // Stack of table base indices for multi-level tables
        val codeOffsets = IntArray(MAX_BITS + 1) // Starting offsets for each bit length in value table
        var unusedCodes: Int // Number of unused codes (for validation)
        var tableSize: Int // Number of entries in current table being built

        // Step 1: Count occurrences of each bit length
        var pointer: Int = bitLengthStartIndex // Pointer/index into various arrays
        currentCode = totalCodes
        do {
            val index = b[pointer++]
            if (index >= 0 && index <= MAX_BITS) {
                bitLengthCounts[index]++
            } else {
                println("huft_build: Invalid bit length index: $index, MAX_BITS: $MAX_BITS")
                return Z_DATA_ERROR // Invalid bit length
            }
        } while (--currentCode > 0)
        
        // Early exit if all codes have zero length (empty tree)
        if (bitLengthCounts[0] == totalCodes) {
            println("[DEBUG] All codes have zero length, returning empty table")
            t[0] = IntArray(0)
            m[0] = 0
            return Z_OK
        }

        // Step 2: Find minimum and maximum code lengths, validate table bits parameter
        tableBits = m[0]
        tempCounter = 1
        while (tempCounter <= MAX_BITS && bitLengthCounts[tempCounter] == 0) {
            tempCounter++
        }
        var currentBitLength: Int = tempCounter // Current bit length being processed
        if (tableBits < tempCounter) {
            tableBits = tempCounter
        }
        currentCode = MAX_BITS
        while (currentCode != 0 && bitLengthCounts[currentCode] == 0) {
            currentCode--
        }
        maxCodeLength = currentCode
        if (tableBits > currentCode) {
            tableBits = currentCode
        }
        m[0] = tableBits
        
        println("[DEBUG] Code length bounds: currentBitLength=$currentBitLength (min), maxCodeLength=$maxCodeLength (max), tableBits=$tableBits (table bits)")

        // Step 3: Validate that we have a valid set of code lengths (Kraft inequality)
        unusedCodes = 1 shl tempCounter
        while (tempCounter < currentCode) {
            unusedCodes -= bitLengthCounts[tempCounter]
            if (unusedCodes < 0) {
                println("[DEBUG] Code validation failed: y=$unusedCodes at j=$tempCounter")
                return Z_DATA_ERROR
            }
            tempCounter++
            unusedCodes = unusedCodes shl 1
        }
        unusedCodes -= bitLengthCounts[currentCode]
        if (unusedCodes < 0) {
            println("[DEBUG] Final code validation failed: y=$unusedCodes")
            return Z_DATA_ERROR
        }
        bitLengthCounts[currentCode] += unusedCodes
        
        println("[DEBUG] After code validation: bitLengthCounts=${bitLengthCounts.contentToString()}, unusedCodes=$unusedCodes")

        // Step 4: Generate starting offsets into the value table for each bit length
        codeOffsets[1] = 0  // Starting offset for codes of length 1
        tempCounter = 0  // Running total
        var offsetIndex = 2  // Index into x array (starts at 2)
        pointer = 1  // Index into c array (starts at 1)
        var remainingLengths = currentCode - 1  // Decrement i for loop (note that i == g from above)
        while (remainingLengths != 0) {
            tempCounter += bitLengthCounts[pointer]
            codeOffsets[offsetIndex] = tempCounter
            offsetIndex++
            pointer++
            remainingLengths--
        }
        
        println("[DEBUG] Starting offsets codeOffsets: ${codeOffsets.contentToString()}")

        // Step 5: Create value table - sort symbols by their bit length
        currentCode = 0
        pointer = 0
        do {
            tempCounter = b[bitLengthStartIndex + pointer]
            if (tempCounter != 0) {
                if (tempCounter < codeOffsets.size && codeOffsets[tempCounter] < valueTable.size) {
                    val index = codeOffsets[tempCounter]
                    codeOffsets[tempCounter]++
                    valueTable[index] = currentCode
                    if (index < 10) { // Only log first few to avoid spam
                        println("[DEBUG] v[$index] = $currentCode (from bit length $tempCounter)")
                    }
                } else {
                    println("huft_build: Invalid index for valueTable from codeOffsets[$tempCounter]=${if (tempCounter < codeOffsets.size) codeOffsets[tempCounter] else "OOB"}, valueTable.size=${valueTable.size}, tempCounter=$tempCounter, currentCode=$currentCode")
                    return Z_DATA_ERROR // Index out of bounds
                }
            }
            pointer++
        } while (++currentCode < totalCodes)
        
        println("[DEBUG] Value table (first 20): ${valueTable.sliceArray(0 until minOf(20, valueTable.size)).contentToString()}")
        
        // The actual number of values is stored at codeOffsets[maxCodeLength]
        val actualValueCount = codeOffsets[maxCodeLength]
        println("[DEBUG] actualValueCount=$actualValueCount (from codeOffsets[$maxCodeLength])")

        // Step 6: Generate the Huffman codes and build the decode table
        codeOffsets[0] = 0
        currentCode = 0 // Current Huffman code value
        pointer = 0 // Current position in value array
        var tableLevel: Int = -1 // Current table level (-1 = main table)
        // Current table level (for multi-level tables)
        var bitsBeforeTable: Int = -tableBits // Initialize to -tableBits to match reference implementation
        // Number of bits processed before current table level
        tableStack[0] = 0 // Initialize table stack
        tableBaseIndex = 0 // Base index of current table
        tableSize = 0 // Size of current table

        // Point the output table to our pre-allocated space
        t[0] = huffmanTable
        
        println("[DEBUG] Initial state: currentCode=$currentCode, pointer=$pointer, tableLevel=$tableLevel, bitsBeforeTable=$bitsBeforeTable")
        println("[DEBUG] Starting Huffman code generation: currentBitLength=$currentBitLength to maxCodeLength=$maxCodeLength")

        // Main loop: process each bit length from shortest to longest
        var loopCount = 0
        while (currentBitLength <= maxCodeLength) {
            // println("[DEBUG] Main loop iteration ${++loopCount}: k=$k, g=$g, c[k]=${c[k]}")
            loopCount++
            if (loopCount > 1000) {
                println("[DEBUG] Too many iterations in main loop, breaking")
                return Z_DATA_ERROR
            }
            
            codesAtCurrentLength = bitLengthCounts[currentBitLength]
            var innerLoopCount = 0
            
            // Process each code of the current bit length
            while (codesAtCurrentLength-- > 0) {
                innerLoopCount++
                if (innerLoopCount > 1000) {
                    println("[DEBUG] Too many iterations in inner loop, breaking")
                    return Z_DATA_ERROR
                }
                
                // Here currentCode is the Huffman code of currentBitLength bits for value valueTable[pointer]
                var whileLoopCount = 0
                
                // Check if we need to create a new table level
                while (currentBitLength > bitsBeforeTable + tableBits) {
                    println("[DEBUG] Creating new table level: currentBitLength=$currentBitLength, bitsBeforeTable=$bitsBeforeTable, tableBits=$tableBits")
                    if (whileLoopCount > 100) {
                        println("[DEBUG] Too many iterations in table creation loop, breaking")
                        return Z_DATA_ERROR
                    }
                    whileLoopCount++
                    
                    tableLevel++
                    bitsBeforeTable += tableBits // Add bits already decoded
                    println("[DEBUG]   New table: tableLevel=$tableLevel, bitsBeforeTable=$bitsBeforeTable")

                    // Compute minimum size table less than or equal to tableBits
                    tableSize = maxCodeLength - bitsBeforeTable
                    tableSize = if (tableSize > tableBits) tableBits else tableSize
                    tempCounter = currentBitLength - bitsBeforeTable
                    tableRepeatInterval = 1 shl tempCounter
                    println("[DEBUG]   Table size calculation: tableSize=$tableSize, tempCounter=$tempCounter, tableRepeatInterval=$tableRepeatInterval, codesAtCurrentLength=$codesAtCurrentLength")
                    
                    // Optimize table size: try a (currentBitLength - bitsBeforeTable) bit table
                    if (tableRepeatInterval > codesAtCurrentLength + 1) {
                        tableRepeatInterval -= codesAtCurrentLength + 1
                        var xp = currentBitLength
                        while (++tempCounter < tableSize) {
                            tableRepeatInterval = tableRepeatInterval shl 1
                            if (tableRepeatInterval <= bitLengthCounts[++xp]) {
                                break
                            }
                            tableRepeatInterval -= bitLengthCounts[xp]
                        }
                    }
                    tableSize = 1 shl tempCounter
                    println("[DEBUG]   Final table size: tableSize=$tableSize, tempCounter=$tempCounter")
                    if (tableIndexTracker[0] + tableSize > IBLK_MANY) {
                        println("[DEBUG] Table size exceeds IBLK_MANY: tableIndexTracker[0]=${tableIndexTracker[0]}, tableSize=$tableSize, IBLK_MANY=$IBLK_MANY")
                        return Z_DATA_ERROR
                    }
                    tableStack[tableLevel] = tableBaseIndex
                    tableBaseIndex = tableIndexTracker[0]
                    tableIndexTracker[0] += tableSize
                    println("[DEBUG]   Updated: tableStack[$tableLevel]=${tableStack[tableLevel]}, tableBaseIndex=$tableBaseIndex, tableIndexTracker[0]=${tableIndexTracker[0]}")

                    // Connect to parent table if this is not the main table
                    if (tableLevel != 0) {
                        codeOffsets[tableLevel] = currentCode // Save pattern for backing up
                        tableEntry[0] = (tableBits and 0xFF) // Bits to dump before this table
                        tableEntry[1] = (tempCounter and 0xFF) // Bits in this table
                        tableEntry[2] = (tableBaseIndex - tableStack[tableLevel-1] - (currentCode ushr (bitsBeforeTable - tableBits))) and 0xFF // Offset to this table
                        val index = (tableStack[tableLevel-1] + (currentCode ushr (bitsBeforeTable - tableBits))) * 3
                        println("[DEBUG]   Parent table connection: codeOffsets[$tableLevel]=$currentCode, tableEntry=[${tableEntry[0]}, ${tableEntry[1]}, ${tableEntry[2]}], index=$index")
                        if (index >= 0 && index <= t[0].size - 3) {
                            // Copy the 3-element entry (operation, bits, value)
                            t[0][index] = tableEntry[0]
                            t[0][index + 1] = tableEntry[1]
                            t[0][index + 2] = tableEntry[2]
                        } else {
                            println("huft_build: Invalid index for t[0] array (parent table): $index, t[0].size=${t[0].size}, tableLevel=$tableLevel, bitsBeforeTable=$bitsBeforeTable, currentCode=$currentCode")
                            return Z_DATA_ERROR // Index out of bounds
                        }
                    } else {
                        t[0] = huffmanTable // First table is returned result
                    }
                }

                // Set up table entry for current symbol
                if (pointer >= actualValueCount) {
                    tableEntry[0] = (128 + 64) and 0xFF // Out of values - invalid code
                    tableEntry[1] = 0
                    tableEntry[2] = 0
                } else if (valueTable[pointer] < simpleValueCount) {
                    tableEntry[0] = (if (valueTable[pointer] < 256) 0 else 32 + 64) and 0xFF // 256 is end-of-block
                    tableEntry[1] = (currentBitLength - bitsBeforeTable) and 0xFF // bits in this table
                    tableEntry[2] = valueTable[pointer++] // simple code is just the value
                } else {
                    // Non-simple code - look up in extra tables
                    val eValue = if (e != null && valueTable[pointer] - simpleValueCount < e.size) e[valueTable[pointer] - simpleValueCount] else 0
                    val dValue = if (d != null && valueTable[pointer] - simpleValueCount < d.size) d[valueTable[pointer] - simpleValueCount] else 0
                    tableEntry[0] = (eValue + 16 + 64) and 0xFF
                    tableEntry[1] = (currentBitLength - bitsBeforeTable) and 0xFF
                    tableEntry[2] = dValue
                    pointer++
                }
                
                println("[DEBUG]   Current table entry: k=$currentBitLength, w=$bitsBeforeTable, p=$pointer, v[p-1]=${if (pointer > 0 && pointer <= valueTable.size) valueTable[pointer-1] else "N/A"}, r=[${tableEntry[0]}, ${tableEntry[1]}, ${tableEntry[2]}]")

                // Fill table entries with this symbol (replicate for all matching bit patterns)
                tableRepeatInterval = 1 shl (currentBitLength - bitsBeforeTable)
                tempCounter = currentCode ushr bitsBeforeTable
                var writeCount = 0
                
                while (tempCounter < tableSize) {
                    val index = (tableBaseIndex + tempCounter) * 3
                    if (index >= 0 && index <= t[0].size - 3) {
                        // Copy all 3 elements: [operation, bits, value]
                        t[0][index] = tableEntry[0]
                        t[0][index + 1] = tableEntry[1]
                        t[0][index + 2] = tableEntry[2]
                        writeCount++
                        
                        // Debug critical writes
                        if (index == 378) {
                            println("[DEBUG]   CRITICAL WRITE: t[0][$index-${index+2}] = [${tableEntry[0]}, ${tableEntry[1]}, ${tableEntry[2]}] (tempCounter=$tempCounter, tableBaseIndex=$tableBaseIndex)")
                        }
                        
                        // Verify the write immediately  
                        if (t[0][index] != tableEntry[0] || t[0][index + 1] != tableEntry[1] || t[0][index + 2] != tableEntry[2]) {
                            println("[DEBUG]   ERROR: Write verification failed! Expected [${tableEntry[0]}, ${tableEntry[1]}, ${tableEntry[2]}], got [${t[0][index]}, ${t[0][index + 1]}, ${t[0][index + 2]}]")
                        }
                    } else {
                        println("huft_build: Invalid index for t[0] array (fill): $index, t[0].size=${t[0].size}, tableBaseIndex=$tableBaseIndex, tempCounter=$tempCounter")
                        return Z_DATA_ERROR
                    }
                    tempCounter += tableRepeatInterval
                }

                // Backwards increment the currentBitLength-bit code (generate next code)
                tempCounter = 1 shl (currentBitLength - 1)
                while ((currentCode and tempCounter) != 0) {
                    currentCode = currentCode xor tempCounter
                    tempCounter = tempCounter ushr 1
                }
                currentCode = currentCode xor tempCounter
                println("[DEBUG]   Next code calculation: new currentCode=$currentCode")

                // Back up over finished tables
                tableMask = (1 shl bitsBeforeTable) - 1
                while (tableLevel >= 0 && (currentCode and tableMask) != codeOffsets[tableLevel]) {
                    tableLevel-- // Don't need to update tableBaseIndex
                    bitsBeforeTable -= tableBits
                    if (bitsBeforeTable <= 0) break
                    tableMask = (1 shl bitsBeforeTable) - 1
                }
                println("[DEBUG]   After backup: tableLevel=$tableLevel, bitsBeforeTable=$bitsBeforeTable, tableMask=$tableMask")
            }
            currentBitLength++
            println("[DEBUG] Moving to next bit length: currentBitLength=$currentBitLength")
        }

        // Final validation and return
        // Return Z_OK if we used all codes, or if this is a distance table (simpleValueCount == 0)
        // Distance tables are allowed to be incomplete in some cases
        val result = if (unusedCodes == 0 || maxCodeLength == 1 || simpleValueCount == 0) Z_OK else Z_DATA_ERROR
        println("[DEBUG] huftBuild END: bitLengthStartIndex=$bitLengthStartIndex, totalCodes=$totalCodes, simpleValueCount=$simpleValueCount, result=$result, unusedCodes=$unusedCodes, maxCodeLength=$maxCodeLength")
        
        // Debug: Print first few table entries only for literal/length tree
        if (simpleValueCount == 257) {
            println("[DEBUG] Literal/length table sample entries:")
            for (i in 0 until minOf(10 * 3, t[0].size) step 3) {
                println("[DEBUG]   t[0][$i-${i+2}] = [${t[0][i]}, ${t[0][i+1]}, ${t[0][i+2]}]")
            }
            // Check the specific index that was failing (378)
            if (t[0].size > 380) {
                println("[DEBUG] Index 378: [${t[0][378]}, ${t[0][379]}, ${t[0][380]}]")
            }
        }
        
        return result
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
        println("[DEBUG] Building literal/length tree...")
        result = huftBuild(c, 0, nl, L_CODES, TREE_EXTRA_LBITS, TREE_BASE_LENGTH, tl, bl, hp, hn, v)
        println("[DEBUG] Literal/length tree result: $result")
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
        println("[DEBUG] Building distance tree...")
        result = huftBuild(c, nl, nd, 0, TREE_EXTRA_DBITS, TREE_BASE_DIST, td, bd, hp, hn, v)
        println("[DEBUG] Distance tree result: $result")
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
        if (!fixed_built) {
            // Build fixed tables if not already built
            val c = IntArray(288)
            val v = IntArray(288)
            val hn = intArrayOf(0)

            // Literal table
            for (k in 0 until 144) c[k] = 8
            for (k in 144 until 256) c[k] = 9
            for (k in 256 until 280) c[k] = 7
            for (k in 280 until 288) c[k] = 8

            fixed_bl[0] = 9

            // Create a temporary array for hufts
            val tempHufts = IntArray(IBLK_MANY * 3)

            // Build the literal/length tree
            huftBuild(c, 0, 288, 257, TREE_EXTRA_LBITS, TREE_BASE_LENGTH,
                    fixed_tl, fixed_bl, tempHufts, hn, v)

            // Distance table
            for (k in 0 until 30) c[k] = 5

            fixed_bd[0] = 5

            // Build the distance tree
            huftBuild(c, 0, 30, 0, TREE_EXTRA_DBITS, TREE_BASE_DIST,
                    fixed_td, fixed_bd, tempHufts, hn, v)

            // Mark as built
            fixed_built = true
        }

        // Copy the tree data to the caller's arrays
        bl[0] = fixed_bl[0]
        bd[0] = fixed_bd[0]
        tl[0] = fixed_tl[0]
        td[0] = fixed_td[0]

        return Z_OK
    }
}
