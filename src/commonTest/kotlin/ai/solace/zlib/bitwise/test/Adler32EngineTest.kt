package ai.solace.zlib.bitwise.test

import ai.solace.zlib.bitwise.checksum.Adler32Utils
import ai.solace.zlib.bitwise.BitShiftEngine
import ai.solace.zlib.bitwise.BitShiftMode
import kotlin.test.*

/**
 * Test that validates Adler32 works correctly with both arithmetic and native bit shift engines
 */
class Adler32EngineTest {
    
    @Test
    fun testAdler32ConsistencyBetweenEngines() {
        val testData = "Hello, World! This is a test string for Adler32 checksum calculation.".encodeToByteArray()
        
        // Calculate using default (native) engine
        val nativeResult = Adler32Utils.adler32(1L, testData, 0, testData.size)
        
        // Calculate using arithmetic engine
        val arithmeticEngine = BitShiftEngine(BitShiftMode.ARITHMETIC, 32)
        val arithmeticResult = Adler32Utils.adler32(1L, testData, 0, testData.size, arithmeticEngine)
        
        assertEquals(nativeResult, arithmeticResult,
            "Adler32 results should be identical between native and arithmetic engines")
    }
    
    @Test
    fun testAdler32WithFactoryFunctions() {
        val testData = "Factory function test".encodeToByteArray()
        
        val nativeFunction = Adler32Utils.withNativeEngine()
        val arithmeticFunction = Adler32Utils.withArithmeticEngine()
        
        val nativeResult = nativeFunction(1L, testData, 0, testData.size)
        val arithmeticResult = arithmeticFunction(1L, testData, 0, testData.size)
        
        assertEquals(nativeResult, arithmeticResult,
            "Factory function results should be identical")
    }
    
    @Test
    fun testAdler32KnownValues() {
        // Test with known values to ensure correctness
        val testCases = listOf(
            Pair("", 0x00000001L),  // Empty string should return 1
            Pair("a", 0x00620062L), // Single character
            Pair("abc", 0x024d0127L), // Short string
            Pair("message digest", 0x29750586L), // Medium string
        )
        
        val nativeEngine = BitShiftEngine(BitShiftMode.NATIVE, 32)
        val arithmeticEngine = BitShiftEngine(BitShiftMode.ARITHMETIC, 32)
        
        for ((input, expected) in testCases) {
            val data = input.encodeToByteArray()
            
            val nativeResult = Adler32Utils.adler32(1L, data, 0, data.size, nativeEngine)
            val arithmeticResult = Adler32Utils.adler32(1L, data, 0, data.size, arithmeticEngine)
            
            assertEquals(expected, nativeResult, "Native engine failed for input: '$input'")
            assertEquals(expected, arithmeticResult, "Arithmetic engine failed for input: '$input'")
        }
    }
    
    @Test
    fun testAdler32Incremental() {
        val fullData = "This is a long test string that we will process incrementally".encodeToByteArray()
        
        // Calculate in one go
        val fullResult = Adler32Utils.adler32(1L, fullData, 0, fullData.size)
        
        // Calculate incrementally using arithmetic engine
        val arithmeticEngine = BitShiftEngine(BitShiftMode.ARITHMETIC, 32)
        var incrementalResult = 1L
        val chunkSize = 10
        
        var offset = 0
        while (offset < fullData.size) {
            val len = minOf(chunkSize, fullData.size - offset)
            incrementalResult = Adler32Utils.adler32(incrementalResult, fullData, offset, len, arithmeticEngine)
            offset += len
        }
        
        assertEquals(fullResult, incrementalResult,
            "Incremental calculation should match full calculation")
    }
    
    @Test
    fun testAdler32EdgeCases() {
        val arithmeticEngine = BitShiftEngine(BitShiftMode.ARITHMETIC, 32)
        
        // Test null buffer
        val nullResult = Adler32Utils.adler32(1L, null, 0, 0, arithmeticEngine)
        assertEquals(1L, nullResult, "Null buffer should return 1")
        
        // Test empty buffer
        val emptyData = ByteArray(0)
        val emptyResult = Adler32Utils.adler32(1L, emptyData, 0, 0, arithmeticEngine)
        assertEquals(1L, emptyResult, "Empty buffer should return 1")
        
        // Test large values (ensure no overflow issues)
        val largeInitial = 0x7FFFFFFFL
        val testData = ByteArray(1000) { it.toByte() }
        val largeResult = Adler32Utils.adler32(largeInitial, testData, 0, testData.size, arithmeticEngine)
        
        assertTrue(largeResult > 0, "Result should be positive even with large initial value")
    }
    
    @Test
    fun testBitShiftEngineConsistency() {
        // Test that the bit operations used in Adler32 work consistently
        val testValue = 0x12345678L
        
        val nativeEngine = BitShiftEngine(BitShiftMode.NATIVE, 32)
        val arithmeticEngine = BitShiftEngine(BitShiftMode.ARITHMETIC, 32)
        
        // Test left shift by 16 (used in Adler32 to combine high/low parts)
        val nativeShift = nativeEngine.leftShift(testValue and 0xFFFF, 16)
        val arithmeticShift = arithmeticEngine.leftShift(testValue and 0xFFFF, 16)
        
        assertEquals(nativeShift.value, arithmeticShift.value,
            "16-bit left shift should be consistent between engines")
        
        // Test right shift by 16 (used in Adler32 to extract high part)
        val nativeRight = nativeEngine.unsignedRightShift(testValue, 16)
        val arithmeticRight = arithmeticEngine.unsignedRightShift(testValue, 16)
        
        assertEquals(nativeRight.value, arithmeticRight.value,
            "16-bit right shift should be consistent between engines")
        
        // Test that we can round-trip a value
        val high = (testValue ushr 16) and 0xFFFF
        val low = testValue and 0xFFFF
        val recombined = nativeEngine.leftShift(high, 16).value or low
        
        assertEquals(testValue, recombined, "Should be able to round-trip a 32-bit value")
    }
}