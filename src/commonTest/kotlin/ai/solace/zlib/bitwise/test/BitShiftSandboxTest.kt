package ai.solace.zlib.bitwise.test

import ai.solace.zlib.bitwise.*
import kotlin.test.*

/**
 * Comprehensive test sandbox for bit shift operations that validates both
 * native and arithmetic implementations work correctly and consistently.
 */
class BitShiftSandboxTest {
    @Test
    fun testBothModesProduceSameResults() {
        val testValues = listOf(0L, 1L, 7L, 15L, 128L, 255L, 1024L, 0x12345678L)
        val testShifts = listOf(0, 1, 2, 4, 8, 16)

        for (bitWidth in listOf(8, 16, 32)) {
            val nativeEngine = BitShiftEngine(BitShiftMode.NATIVE, bitWidth)
            val arithmeticEngine = BitShiftEngine(BitShiftMode.ARITHMETIC, bitWidth)

            for (value in testValues) {
                for (shift in testShifts) {
                    if (shift < bitWidth) {
                        // Test left shift
                        val nativeLeft = nativeEngine.leftShift(value, shift)
                        val arithmeticLeft = arithmeticEngine.leftShift(value, shift)

                        assertEquals(
                            nativeLeft.value, arithmeticLeft.value,
                            "Left shift mismatch: $bitWidth-bit value=$value, shift=$shift",
                        )

                        // Test right shift
                        val nativeRight = nativeEngine.rightShift(value, shift)
                        val arithmeticRight = arithmeticEngine.rightShift(value, shift)

                        assertEquals(
                            nativeRight.value, arithmeticRight.value,
                            "Right shift mismatch: $bitWidth-bit value=$value, shift=$shift",
                        )

                        // Test unsigned right shift
                        val nativeUnsigned = nativeEngine.unsignedRightShift(value, shift)
                        val arithmeticUnsigned = arithmeticEngine.unsignedRightShift(value, shift)

                        assertEquals(
                            nativeUnsigned.value, arithmeticUnsigned.value,
                            "Unsigned right shift mismatch: $bitWidth-bit value=$value, shift=$shift",
                        )
                    }
                }
            }
        }
    }

    @Test
    fun testCarryDetectionInLeftShift() {
        val engine8 = BitShiftEngine(BitShiftMode.ARITHMETIC, 8)
        val engine16 = BitShiftEngine(BitShiftMode.ARITHMETIC, 16)
        val engine32 = BitShiftEngine(BitShiftMode.ARITHMETIC, 32)

        // Test 8-bit overflow
        val result8 = engine8.leftShift(255, 1) // 255 << 1 should overflow 8-bit
        assertTrue(result8.overflow, "Should detect overflow for 255 << 1 in 8-bit")
        assertEquals(254L, result8.value, "8-bit overflow result should wrap")

        // Test 16-bit overflow
        val result16 = engine16.leftShift(65535, 1) // Max 16-bit << 1 should overflow
        assertTrue(result16.overflow, "Should detect overflow for 65535 << 1 in 16-bit")

        // Test 32-bit overflow
        val result32 = engine32.leftShift(0xFFFFFFFFL, 1) // Max 32-bit << 1 should overflow
        assertTrue(result32.overflow, "Should detect overflow for max 32-bit << 1")
    }

    @Test
    fun testAdler32Compatibility() {
        // Test that our engines work correctly for Adler32 operations
        val engine16 = BitShiftEngine(BitShiftMode.ARITHMETIC, 16)
        val engine32 = BitShiftEngine(BitShiftMode.NATIVE, 32)

        // Simulate Adler32 operations: combine high/low 16-bit values
        val high = 0x1234L
        val low = 0x5678L

        // Using 32-bit engine to combine
        val highShifted = engine32.leftShift(high, 16)
        assertFalse(highShifted.overflow, "High part shift should not overflow")
        assertEquals(0x12340000L, highShifted.value, "High part should be shifted correctly")

        // Combine with OR (using arithmetic engine)
        val combined = highShifted.value or low
        assertEquals(0x12345678L, combined, "Combined value should be correct")

        // Test extraction using right shift
        val extractedHigh = engine32.unsignedRightShift(combined, 16)
        assertEquals(0x1234L, extractedHigh.value, "Extracted high part should match original")

        val extractedLow = combined and 0xFFFFL
        assertEquals(0x5678L, extractedLow, "Extracted low part should match original")
    }

    @Test
    fun testHistoricalBitPatterns() {
        // Test patterns that are common in legacy code and Adler32
        val testCases =
            listOf(
                // value, shift, expected (for 32-bit operations)
                Triple(0x01234567L, 4, 0x12345670L), // Hex digit shift
                Triple(0x80000000L, 1, 0x00000000L), // Sign bit shift (overflow)
                Triple(0x12345678L, 8, 0x34567800L), // Byte boundary shift
                Triple(0x0000FFFFL, 16, 0xFFFF0000L), // Half-word shift
            )

        val nativeEngine = BitShiftEngine(BitShiftMode.NATIVE, 32)
        val arithmeticEngine = BitShiftEngine(BitShiftMode.ARITHMETIC, 32)

        for ((value, shift, expected) in testCases) {
            val nativeResult = nativeEngine.leftShift(value, shift)
            val arithmeticResult = arithmeticEngine.leftShift(value, shift)

            assertEquals(
                expected, nativeResult.value,
                "Native: Failed for 0x${value.toString(16)} << $shift",
            )
            assertEquals(
                expected, arithmeticResult.value,
                "Arithmetic: Failed for 0x${value.toString(16)} << $shift",
            )
            assertEquals(
                nativeResult.value, arithmeticResult.value,
                "Native and arithmetic results should match for 0x${value.toString(16)} << $shift",
            )
        }
    }

    @Test
    fun testEdgeCases() {
        val engine = BitShiftEngine(BitShiftMode.ARITHMETIC, 32)

        // Test shift by 0
        val result0 = engine.leftShift(0x12345678L, 0)
        assertEquals(0x12345678L, result0.value, "Shift by 0 should return original value")
        assertFalse(result0.overflow, "Shift by 0 should not overflow")
        assertEquals(0L, result0.carry, "Shift by 0 should have no carry")

        // Test shift beyond bit width
        val resultBeyond = engine.leftShift(0x12345678L, 33)
        assertEquals(0L, resultBeyond.value, "Shift beyond bit width should return 0")
        assertTrue(resultBeyond.overflow, "Shift beyond bit width should be considered overflow")

        // Test negative shift
        val resultNegative = engine.leftShift(0x12345678L, -1)
        assertEquals(0L, resultNegative.value, "Negative shift should return 0")
        assertTrue(resultNegative.overflow, "Negative shift should be considered overflow")
    }

    @Test
    fun testEngineConfiguration() {
        // Test engine creation and configuration
        val originalEngine = BitShiftEngine(BitShiftMode.NATIVE, 16)

        // Test mode switching
        val arithmeticEngine = originalEngine.withMode(BitShiftMode.ARITHMETIC)
        assertEquals(BitShiftMode.ARITHMETIC, arithmeticEngine.mode)

        // Test bit width switching
        val widerEngine = originalEngine.withBitWidth(32)
        assertEquals(32, widerEngine.bitWidth)

        // Test that results change appropriately with different bit widths
        val value = 0x1FFFF // Value larger than 16-bit max
        val shift = 1

        val result16 = originalEngine.leftShift(value.toLong(), shift)
        val result32 = widerEngine.leftShift(value.toLong(), shift)

        // 16-bit should wrap the input value first
        assertTrue(
            result16.value < result32.value,
            "16-bit engine should produce different result than 32-bit for large values",
        )
    }

    @Test
    fun testBitShiftEngineIntegration() {
        // Test that the engine can be used in place of direct bit operations
        val engine = BitShiftEngine(BitShiftMode.ARITHMETIC, 32)

        // Simulate a simple hash function that uses bit operations
        fun simpleHash(
            data: ByteArray,
            useEngine: Boolean = true,
        ): Long {
            var hash = 1L
            for (byte in data) {
                val unsigned = (byte.toInt() and 0xFF).toLong()
                if (useEngine) {
                    val shifted = engine.leftShift(hash, 5)
                    hash = shifted.value + unsigned
                } else {
                    hash = ((hash shl 5) and 0xFFFFFFFFL) + unsigned
                }
                hash = hash and 0xFFFFFFFFL // Keep within 32-bit bounds
            }
            return hash
        }

        val testData = "Hello, World!".encodeToByteArray()
        val hashWithEngine = simpleHash(testData, true)
        val hashWithNative = simpleHash(testData, false)

        assertEquals(
            hashWithNative, hashWithEngine,
            "Engine-based hash should match native implementation",
        )
    }
}
