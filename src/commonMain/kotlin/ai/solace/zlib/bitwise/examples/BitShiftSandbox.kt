package ai.solace.zlib.bitwise.examples

import ai.solace.zlib.bitwise.*
import ai.solace.zlib.bitwise.checksum.Adler32Utils
import ai.solace.zlib.common.ZlibLogger

/**
 * BitShiftSandbox - Comprehensive demonstration of arithmetic vs native bit operations
 * 
 * This sandbox demonstrates:
 * 1. Both arithmetic and native bit shift operations working correctly
 * 2. Carry detection and overflow handling
 * 3. Integration with real-world algorithms like Adler32
 * 4. Performance comparisons between approaches
 * 5. Cross-platform consistency validation
 */
object BitShiftSandbox {
    
    /**
     * Demonstrates basic bit shift operations in both modes
     */
    fun demonstrateBasicOperations() {
        ZlibLogger.log("=== Basic Bit Shift Operations Demo ===")
        
        val testValue = 0x12345678L
        val shiftAmount = 4
        
        // Native operations
        val nativeEngine = BitShiftEngine(BitShiftMode.NATIVE, 32)
        val nativeLeft = nativeEngine.leftShift(testValue, shiftAmount)
        val nativeRight = nativeEngine.unsignedRightShift(testValue, shiftAmount)
        
        // Arithmetic operations  
        val arithmeticEngine = BitShiftEngine(BitShiftMode.ARITHMETIC, 32)
        val arithmeticLeft = arithmeticEngine.leftShift(testValue, shiftAmount)
        val arithmeticRight = arithmeticEngine.unsignedRightShift(testValue, shiftAmount)
        
        ZlibLogger.log("Original value: 0x${testValue.toString(16)}")
        ZlibLogger.log("Left shift by $shiftAmount:")
        ZlibLogger.log("  Native:     0x${nativeLeft.value.toString(16)} (overflow: ${nativeLeft.overflow}, carry: ${nativeLeft.carry})")
        ZlibLogger.log("  Arithmetic: 0x${arithmeticLeft.value.toString(16)} (overflow: ${arithmeticLeft.overflow}, carry: ${arithmeticLeft.carry})")
        ZlibLogger.log("Right shift by $shiftAmount:")
        ZlibLogger.log("  Native:     0x${nativeRight.value.toString(16)}")
        ZlibLogger.log("  Arithmetic: 0x${arithmeticRight.value.toString(16)}")
        
        val consistent = nativeLeft.value == arithmeticLeft.value && nativeRight.value == arithmeticRight.value
        ZlibLogger.log("Results consistent: $consistent")
    }
    
    /**
     * Demonstrates overflow and carry detection
     */
    fun demonstrateCarryAndOverflow() {
        ZlibLogger.log("\n=== Carry and Overflow Detection Demo ===")
        
        val engine8 = BitShiftEngine(BitShiftMode.ARITHMETIC, 8)
        val engine16 = BitShiftEngine(BitShiftMode.ARITHMETIC, 16)
        val engine32 = BitShiftEngine(BitShiftMode.ARITHMETIC, 32)
        
        // 8-bit overflow examples
        ZlibLogger.log("8-bit operations:")
        val testValues8 = listOf(0x80L, 0xFFL, 0x7FL)
        for (value in testValues8) {
            val result = engine8.leftShift(value, 1)
            ZlibLogger.log("  0x${value.toString(16).padStart(2, '0')} << 1 = 0x${result.value.toString(16).padStart(2, '0')} " +
                          "(overflow: ${result.overflow}, carry: 0x${result.carry.toString(16)})")
        }
        
        // 16-bit overflow examples  
        ZlibLogger.log("16-bit operations:")
        val testValues16 = listOf(0x8000L, 0xFFFFL, 0x7FFFL)
        for (value in testValues16) {
            val result = engine16.leftShift(value, 1)
            ZlibLogger.log("  0x${value.toString(16).padStart(4, '0')} << 1 = 0x${result.value.toString(16).padStart(4, '0')} " +
                          "(overflow: ${result.overflow}, carry: 0x${result.carry.toString(16)})")
        }
    }
    
    /**
     * Demonstrates Adler32 checksum calculation with both engines
     */
    fun demonstrateAdler32Integration() {
        ZlibLogger.log("\n=== Adler32 Integration Demo ===")
        
        val testData = "Hello, BitShift Sandbox! This demonstrates Adler32 with configurable engines.".encodeToByteArray()
        
        // Calculate with different engines
        val nativeEngine = BitShiftEngine(BitShiftMode.NATIVE, 32)
        val arithmeticEngine = BitShiftEngine(BitShiftMode.ARITHMETIC, 32)
        
        val nativeResult = Adler32Utils.adler32(1L, testData, 0, testData.size, nativeEngine)
        val arithmeticResult = Adler32Utils.adler32(1L, testData, 0, testData.size, arithmeticEngine)
        
        ZlibLogger.log("Test data: \"${testData.decodeToString()}\"")
        ZlibLogger.log("Adler32 with native engine:     0x${nativeResult.toString(16)}")
        ZlibLogger.log("Adler32 with arithmetic engine: 0x${arithmeticResult.toString(16)}")
        ZlibLogger.log("Results match: ${nativeResult == arithmeticResult}")
        
        // Demonstrate factory functions
        val nativeFunction = Adler32Utils.withNativeEngine()
        val arithmeticFunction = Adler32Utils.withArithmeticEngine()
        
        val factoryNative = nativeFunction(1L, testData, 0, testData.size)
        val factoryArithmetic = arithmeticFunction(1L, testData, 0, testData.size)
        
        ZlibLogger.log("Factory function results match: ${factoryNative == factoryArithmetic}")
    }
    
    /**
     * Demonstrates performance comparison (timing) between engines
     */
    fun demonstratePerformanceComparison() {
        ZlibLogger.log("\n=== Performance Comparison Demo ===")
        
        val testData = ByteArray(10000) { it.toByte() }
        val iterations = 100
        
        // Warm up
        repeat(10) {
            Adler32Utils.adler32(1L, testData, 0, testData.size)
        }
        
        // Native engine timing
        val nativeStart = kotlin.system.getTimeMillis()
        repeat(iterations) {
            val nativeEngine = BitShiftEngine(BitShiftMode.NATIVE, 32)
            Adler32Utils.adler32(1L, testData, 0, testData.size, nativeEngine)
        }
        val nativeTime = kotlin.system.getTimeMillis() - nativeStart
        
        // Arithmetic engine timing  
        val arithmeticStart = kotlin.system.getTimeMillis()
        repeat(iterations) {
            val arithmeticEngine = BitShiftEngine(BitShiftMode.ARITHMETIC, 32)
            Adler32Utils.adler32(1L, testData, 0, testData.size, arithmeticEngine)
        }
        val arithmeticTime = kotlin.system.getTimeMillis() - arithmeticStart
        
        ZlibLogger.log("Performance test ($iterations iterations, ${testData.size} bytes):")
        ZlibLogger.log("  Native engine:     ${nativeTime}ms")
        ZlibLogger.log("  Arithmetic engine: ${arithmeticTime}ms")
        if (arithmeticTime > 0) {
            ZlibLogger.log("  Native is ${(arithmeticTime.toDouble() / nativeTime).format(2)}x faster")
        }
    }
    
    /**
     * Demonstrates cross-platform consistency validation
     */
    fun demonstrateCrossPlatformConsistency() {
        ZlibLogger.log("\n=== Cross-Platform Consistency Demo ===")
        
        val testPatterns = listOf(
            0x00000000L, 0x00000001L, 0x7FFFFFFFL, 0x80000000L, 0xFFFFFFFFL,
            0x12345678L, 0x87654321L, 0xDEADBEEFL, 0xCAFEBABEL, 0xFEEDFACEL
        )
        
        val shifts = listOf(1, 4, 8, 16, 24, 31)
        
        ZlibLogger.log("Validating consistency across engines for various patterns...")
        
        var allConsistent = true
        for (pattern in testPatterns) {
            for (shift in shifts) {
                val nativeEngine = BitShiftEngine(BitShiftMode.NATIVE, 32)
                val arithmeticEngine = BitShiftEngine(BitShiftMode.ARITHMETIC, 32)
                
                val nativeLeft = nativeEngine.leftShift(pattern, shift)
                val arithmeticLeft = arithmeticEngine.leftShift(pattern, shift)
                
                val nativeRight = nativeEngine.unsignedRightShift(pattern, shift)
                val arithmeticRight = arithmeticEngine.unsignedRightShift(pattern, shift)
                
                if (nativeLeft.value != arithmeticLeft.value || nativeRight.value != arithmeticRight.value) {
                    ZlibLogger.log("  INCONSISTENCY: 0x${pattern.toString(16)} shift $shift")
                    allConsistent = false
                }
            }
        }
        
        if (allConsistent) {
            ZlibLogger.log("✓ All patterns consistent between engines")
        } else {
            ZlibLogger.log("✗ Found inconsistencies - further investigation needed")
        }
    }
    
    /**
     * Demonstrates historical bit patterns common in legacy algorithms
     */
    fun demonstrateLegacyPatterns() {
        ZlibLogger.log("\n=== Legacy Algorithm Patterns Demo ===")
        
        // Simulate common patterns from 8-bit and 16-bit era algorithms
        val engine8 = BitShiftEngine(BitShiftMode.ARITHMETIC, 8)
        val engine16 = BitShiftEngine(BitShiftMode.ARITHMETIC, 16)
        
        // 8-bit CRC-like operations
        ZlibLogger.log("8-bit CRC-style operations:")
        var crc = 0L
        val polynomial = 0x07L // Simple polynomial
        val data = byteArrayOf(0x41, 0x42, 0x43) // "ABC"
        
        for (byte in data) {
            val unsigned = (byte.toInt() and 0xFF).toLong()
            crc = engine8.leftShift(crc, 1).value
            crc = crc xor unsigned
            if (crc > 0x7F) { // If high bit is set in 8-bit context
                crc = crc xor polynomial
            }
        }
        ZlibLogger.log("  8-bit CRC result: 0x${crc.toString(16)}")
        
        // 16-bit address calculation (segment:offset style)
        ZlibLogger.log("16-bit segment:offset addressing:")
        val segment = 0x1000L
        val offset = 0x0200L
        
        // Calculate physical address: segment * 16 + offset
        val segmentShifted = engine16.leftShift(segment, 4) // Multiply by 16
        val physicalAddress = (segmentShifted.value + offset) and 0xFFFF
        
        ZlibLogger.log("  Segment: 0x${segment.toString(16)}")
        ZlibLogger.log("  Offset:  0x${offset.toString(16)}")
        ZlibLogger.log("  Physical: 0x${physicalAddress.toString(16)}")
    }
    
    /**
     * Runs all demonstrations
     */
    fun runAllDemonstrations() {
        ZlibLogger.log("Starting BitShift Sandbox Demonstrations...")
        ZlibLogger.log("=" * 60)
        
        demonstrateBasicOperations()
        demonstrateCarryAndOverflow() 
        demonstrateAdler32Integration()
        demonstratePerformanceComparison()
        demonstrateCrossPlatformConsistency()
        demonstrateLegacyPatterns()
        
        ZlibLogger.log("\n" + "=" * 60)
        ZlibLogger.log("BitShift Sandbox Demonstrations Complete!")
    }
    
    private fun Double.format(decimals: Int): String {
        return "%.${decimals}f".format(this)
    }
}