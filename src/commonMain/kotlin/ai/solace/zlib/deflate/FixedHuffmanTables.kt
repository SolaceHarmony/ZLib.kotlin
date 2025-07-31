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
        ZlibLogger.log("[DEBUG_STATIC] Creating static literal tree")
        val tree = ShortArray(576) // 288 symbols * 2 (code + length)
        
        // Literals 0-143: 8 bits
        for (i in 0..143) {
            tree[i * 2] = i.toShort()     // code (will be calculated properly)
            tree[i * 2 + 1] = 8          // length
        }
        
        // Literals 144-255: 9 bits
        for (i in 144..255) {
            tree[i * 2] = i.toShort()     // code (will be calculated properly)
            tree[i * 2 + 1] = 9          // length
        }
        
        // Length codes 256-279: 7 bits
        for (i in 256..279) {
            tree[i * 2] = i.toShort()     // code (will be calculated properly)
            tree[i * 2 + 1] = 7          // length
        }
        
        // Length codes 280-287: 8 bits
        for (i in 280..287) {
            tree[i * 2] = i.toShort()     // code (will be calculated properly)
            tree[i * 2 + 1] = 8          // length
        }
        
        // Now calculate the actual codes according to RFC 1951 algorithm
        calculateCodes(tree, 288)
        
        ZlibLogger.log("[DEBUG_STATIC] Sample calculated codes: 'A'(65): code=${tree[65*2]}, bits=${tree[65*2+1]}")
        ZlibLogger.log("[DEBUG_STATIC] Sample calculated codes: 'F'(70): code=${tree[70*2]}, bits=${tree[70*2+1]}")
        
        return tree
    }
    
    /**
     * Creates the correct static distance tree according to RFC 1951
     */
    fun createStaticDistanceTree(): ShortArray {
        val tree = ShortArray(64) // 32 symbols * 2 (code + length)
        
        // Distance codes 0-31: 5 bits each
        for (i in 0..31) {
            tree[i * 2] = i.toShort()     // code (will be calculated properly)
            tree[i * 2 + 1] = 5          // length
        }
        
        // Calculate the actual codes
        calculateCodes(tree, 32)
        
        return tree
    }
    
    /**
     * Calculate Huffman codes from bit lengths according to RFC 1951 algorithm
     */
    private fun calculateCodes(tree: ShortArray, maxCode: Int) {
        ZlibLogger.log("[DEBUG_STATIC] Calculating codes for $maxCode symbols")
        val maxBits = 15
        val blCount = IntArray(maxBits + 1)
        val nextCode = IntArray(maxBits + 1)
        
        // Step 1: Count the number of codes for each code length
        for (i in 0 until maxCode) {
            val len = tree[i * 2 + 1].toInt()
            if (len > 0) blCount[len]++
        }
        
        ZlibLogger.log("[DEBUG_STATIC] Code length counts: ${blCount.mapIndexed { i, v -> "$i:$v" }.filter { !it.endsWith(":0") }}")
        
        // Step 2: Find the numerical value of the smallest code for each code length
        var code = 0
        blCount[0] = 0
        for (bits in 1..maxBits) {
            code = (code + blCount[bits - 1]) shl 1
            nextCode[bits] = code
        }
        
        ZlibLogger.log("[DEBUG_STATIC] Starting codes: ${nextCode.mapIndexed { i, v -> "$i:$v" }.filter { !it.endsWith(":0") }}")
        
        // Step 3: Assign numerical values to all codes
        for (n in 0 until maxCode) {
            val len = tree[n * 2 + 1].toInt()
            if (len != 0) {
                tree[n * 2] = nextCode[len].toShort()
                nextCode[len]++
            }
        }
        
        // Debug: Print codes for characters around 'A' and 'F'
        for (char in 65..75) {
            if (char < maxCode) {
                ZlibLogger.log("[DEBUG_STATIC] Char ${char.toChar()}($char): code=${tree[char*2]}, bits=${tree[char*2+1]}")
            }
        }
    }
}