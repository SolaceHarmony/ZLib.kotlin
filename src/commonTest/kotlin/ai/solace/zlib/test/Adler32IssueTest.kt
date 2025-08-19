package ai.solace.zlib.test

import ai.solace.zlib.deflate.Adler32
import ai.solace.zlib.common.*
import kotlin.test.*

/**
 * Focused test to understand the Adler32 checksum issue
 */
class Adler32IssueTest {

    @Test
    fun testBasicAdler32() {
        val adler = Adler32()
        
        println("=== Basic Adler32 Test ===")
        
        // Test single byte
        val singleByte = "A".encodeToByteArray()
        val singleResult = adler.adler32(1L, singleByte, 0, singleByte.size)
        println("Single 'A': checksum = $singleResult (0x${singleResult.toString(16)})")
        
        // Test two bytes  
        val twoBytes = "AB".encodeToByteArray()
        val twoResult = adler.adler32(1L, twoBytes, 0, twoBytes.size)
        println("Two 'AB': checksum = $twoResult (0x${twoResult.toString(16)})")
        
        // Manual calculation for verification using standard Adler32 algorithm
        // The standard algorithm is:
        // a = 1 + D1 + D2 + ...
        // b = n×(1) + (n-0)×D1 + (n-1)×D2 + ... where n is number of bytes
        //
        // For "A" (65): a = 1 + 65 = 66
        //               b = 1×1 + 1×65 = 1 + 65 = 66  ← This was wrong in my calc
        // Actually: b starts at 0, then: b = 0 + (1 + 65) = 66, not 67
        
        // Let me check with a reference implementation approach:
        // Start with a=1, b=0
        // For each byte: a += byte, b += a
        // For "A": a = 1 + 65 = 66, b = 0 + 66 = 66 ✅
        // For "AB": 
        //   After A: a = 66, b = 66
        //   After B: a = 66 + 66 = 132, b = 66 + 132 = 198 ✅
        
        val expectedSingle = 66L * 65536L + 66L // Should match actual
        val expectedTwo = 198L * 65536L + 132L // Should match actual
        
        println("Expected single 'A': $expectedSingle (0x${expectedSingle.toString(16)})")  
        println("Expected two 'AB': $expectedTwo (0x${expectedTwo.toString(16)})")
        
        assertEquals(expectedSingle, singleResult, "Single byte checksum should match expected")
        assertEquals(expectedTwo, twoResult, "Two byte checksum should match expected")
    }
    
    @Test
    fun testIncrementalAdler32() {
        val adler = Adler32()
        
        println("=== Incremental Adler32 Test ===")
        
        // Calculate "AB" in one go
        val twoBytes = "AB".encodeToByteArray()
        val allAtOnce = adler.adler32(1L, twoBytes, 0, twoBytes.size)
        println("All at once 'AB': $allAtOnce")
        
        // Calculate "AB" incrementally
        val firstByte = "A".encodeToByteArray()
        val secondByte = "B".encodeToByteArray()
        
        val afterFirst = adler.adler32(1L, firstByte, 0, firstByte.size)
        println("After first 'A': $afterFirst")
        
        val afterSecond = adler.adler32(afterFirst, secondByte, 0, secondByte.size)
        println("After second 'B': $afterSecond")
        
        assertEquals(allAtOnce, afterSecond, "Incremental calculation should match all-at-once")
    }
}