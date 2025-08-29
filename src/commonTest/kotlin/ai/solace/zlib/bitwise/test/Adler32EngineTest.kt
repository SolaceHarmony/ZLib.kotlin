package ai.solace.zlib.bitwise.test

import ai.solace.zlib.bitwise.checksum.Adler32Utils
import kotlin.test.*

class Adler32EngineTest {
    @Test
    fun testAdler32KnownValues() {
        val testCases = listOf(
            "" to 0x00000001L,
            "a" to 0x00620062L,
            "abc" to 0x024d0127L,
            "message digest" to 0x29750586L,
        )
        for ((input, expected) in testCases) {
            val data = input.encodeToByteArray()
            val result = Adler32Utils.adler32(1L, data, 0, data.size)
            assertEquals(expected, result, "Adler32 failed for input: '$input'")
        }
    }

    @Test
    fun testAdler32Incremental() {
        val fullData = "This is a long test string that we will process incrementally".encodeToByteArray()
        val fullResult = Adler32Utils.adler32(1L, fullData, 0, fullData.size)
        var incremental = 1L
        val chunkSize = 10
        var offset = 0
        while (offset < fullData.size) {
            val len = minOf(chunkSize, fullData.size - offset)
            incremental = Adler32Utils.adler32(incremental, fullData, offset, len)
            offset += len
        }
        assertEquals(fullResult, incremental, "Incremental calculation should match full calculation")
    }

    @Test
    fun testAdler32EdgeCases() {
        val nullResult = Adler32Utils.adler32(1L, null, 0, 0)
        assertEquals(1L, nullResult, "Null buffer should return 1")

        val emptyData = ByteArray(0)
        val emptyResult = Adler32Utils.adler32(1L, emptyData, 0, 0)
        assertEquals(1L, emptyResult, "Empty buffer should return 1")

        val largeInitial = 0x7FFFFFFFL
        val data = ByteArray(1000) { it.toByte() }
        val largeResult = Adler32Utils.adler32(largeInitial, data, 0, data.size)
        assertTrue(largeResult > 0, "Result should be positive even with large initial value")
    }
}
