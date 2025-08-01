package ai.solace.zlib.deflate

import ai.solace.zlib.common.ZlibLogger

/**
 * Correct Fixed Huffman tables according to RFC 1951 Section 3.2.6
 * 
 * The Huffman code lengths for the literal/length alphabet are:
 * - Lit Value 0-143: 8 bits
 * - Lit Value 144-255: 9 bits  
 * - Lit Value 256-279: 7 bits
 * - Lit Value 280-287: 8 bits
 *
 * Distance codes 0-31 are represented by (fixed-length) 5-bit codes.
 */
internal object FixedHuffmanTables {
    
    /**
     * Creates the correct static literal/length tree according to RFC 1951
     */
    fun createStaticLiteralTree(): ShortArray {
        ZlibLogger.logHuffman("Creating static literal tree per RFC 1951 Section 3.2.6", "createStaticLiteralTree")
        val tree = ShortArray(576) // 288 symbols * 2 (code + length)
        
        // Literals 0-143: 8 bits
        ZlibLogger.logHuffman("Setting literals 0-143 to 8 bits", "createStaticLiteralTree")
        for (i in 0..143) {
            tree[i * 2] = i.toShort()     // code (will be calculated properly)
            tree[i * 2 + 1] = 8          // length
        }
        
        // Literals 144-255: 9 bits
        ZlibLogger.logHuffman("Setting literals 144-255 to 9 bits", "createStaticLiteralTree")
        for (i in 144..255) {
            tree[i * 2] = i.toShort()     // code (will be calculated properly)
            tree[i * 2 + 1] = 9          // length
        }
        
        // Length codes 256-279: 7 bits
        ZlibLogger.logHuffman("Setting length codes 256-279 to 7 bits", "createStaticLiteralTree")
        for (i in 256..279) {
            tree[i * 2] = i.toShort()     // code (will be calculated properly)
            tree[i * 2 + 1] = 7          // length
        }
        
        // Length codes 280-287: 8 bits
        ZlibLogger.logHuffman("Setting length codes 280-287 to 8 bits", "createStaticLiteralTree")
        for (i in 280..287) {
            tree[i * 2] = i.toShort()     // code (will be calculated properly)
            tree[i * 2 + 1] = 8          // length
        }
        
        // Now calculate the actual codes according to RFC 1951 algorithm
        calculateCodes(tree, 288)
        
        // Log some sample codes for verification
        ZlibLogger.logHuffmanCode(65, tree[65*2].toInt(), tree[65*2+1].toInt(), "createStaticLiteralTree") // 'A'
        ZlibLogger.logHuffmanCode(70, tree[70*2].toInt(), tree[70*2+1].toInt(), "createStaticLiteralTree") // 'F'
        
        return tree
    }
    
    /**
     * Creates the correct static distance tree according to RFC 1951
     */
    fun createStaticDistanceTree(): ShortArray {
        ZlibLogger.logHuffman("Creating static distance tree with 32 symbols, 5 bits each", "createStaticDistanceTree")
        val tree = ShortArray(64) // 32 symbols * 2 (code + length)
        
        // Distance codes 0-31: 5 bits each
        for (i in 0..31) {
            tree[i * 2] = i.toShort()     // code (will be calculated properly)
            tree[i * 2 + 1] = 5          // length
        }
        
        // Calculate the actual codes
        calculateCodes(tree, 32)
        
        ZlibLogger.logHuffman("Distance tree created with codes 0-31", "createStaticDistanceTree")
        return tree
    }
    
    /**
     * Calculate Huffman codes from bit lengths according to RFC 1951 algorithm
     */
    private fun calculateCodes(tree: ShortArray, maxCode: Int) {
        ZlibLogger.logHuffman("Starting RFC 1951 canonical Huffman code calculation for $maxCode symbols", "calculateCodes")
        val maxBits = 15
        val blCount = IntArray(maxBits + 1)
        val nextCode = IntArray(maxBits + 1)
        
        // Step 1: Count the number of codes for each code length
        ZlibLogger.logHuffman("Step 1: Counting codes per bit length", "calculateCodes")
        for (i in 0 until maxCode) {
            val len = tree[i * 2 + 1].toInt()
            if (len > 0) blCount[len]++
        }
        
        val nonZeroCounts = blCount.mapIndexed { i, v -> "$i:$v" }.filter { !it.endsWith(":0") }
        ZlibLogger.logHuffman("Code length counts: $nonZeroCounts", "calculateCodes")
        
        // Step 2: Find the numerical value of the smallest code for each code length
        ZlibLogger.logHuffman("Step 2: Calculating starting codes for each bit length", "calculateCodes")
        var code = 0
        blCount[0] = 0
        for (bits in 1..maxBits) {
            code = (code + blCount[bits - 1]) shl 1
            nextCode[bits] = code
            if (blCount[bits] > 0) {
                ZlibLogger.logHuffman("Bit length $bits: starting code=$code (${blCount[bits]} symbols)", "calculateCodes")
            }
        }
        
        val nonZeroStarts = nextCode.mapIndexed { i, v -> "$i:$v" }.filter { !it.endsWith(":0") }
        ZlibLogger.logHuffman("Starting codes: $nonZeroStarts", "calculateCodes")
        
        // Step 3: Assign numerical values to all codes
        ZlibLogger.logHuffman("Step 3: Assigning codes to symbols", "calculateCodes")
        var assignedCodes = 0
        for (n in 0 until maxCode) {
            val len = tree[n * 2 + 1].toInt()
            if (len != 0) {
                val assignedCode = nextCode[len]
                tree[n * 2] = assignedCode.toShort()
                nextCode[len]++
                assignedCodes++
                
                // Log important character codes for debugging
                if (n in 65..90 || n in 97..122) { // A-Z, a-z
                    ZlibLogger.logHuffmanCode(n, assignedCode, len, "calculateCodes")
                }
            }
        }
        
        ZlibLogger.logHuffman("Code assignment complete: $assignedCodes codes assigned", "calculateCodes")
        
        // Debug: Print codes for characters around 'A' and 'F'
        for (char in 65..75) {
            if (char < maxCode) {
                ZlibLogger.log("[DEBUG_STATIC] Char ${char.toChar()}($char): code=${tree[char*2]}, bits=${tree[char*2+1]}")
            }
        }
    }
}