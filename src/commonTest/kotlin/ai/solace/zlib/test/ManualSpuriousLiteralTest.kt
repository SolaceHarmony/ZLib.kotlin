package ai.solace.zlib.test

import ai.solace.zlib.common.*
import ai.solace.zlib.deflate.ZStream
import kotlin.test.*

class ManualSpuriousLiteralTest {

    @Test
    fun testManualDecompressionAnalysis() {
        println("=== Manual Test for Spurious Literal Issue ===")
        
        val input = "A".encodeToByteArray()
        println("Input: ${input[0]} (${input[0].toChar()})")
        
        // Compress
        val compressed = compressData(input)
        println("Compressed bytes: ${compressed.joinToString(" ") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }}")
        
        // Decompress  
        val decompressed = decompressData(compressed, 10)
        println("Decompressed: [${decompressed.joinToString(", ")}] -> \"${decompressed.decodeToString()}\"")
        
        if (decompressed.size == 1 && decompressed[0] == 65.toByte()) {
            println("✓ SUCCESS: Got correct single byte 'A'")
            // This should pass but currently fails
            assertTrue(true)
        } else if (decompressed.size > 1) {
            println("✗ SPURIOUS LITERAL: Got ${decompressed.size} bytes instead of 1:")
            decompressed.forEachIndexed { i, byte ->
                println("  [$i] = $byte (${byte.toChar()})")
            }
            fail("Expected 1 byte 'A', but got ${decompressed.size} bytes with spurious literals")
        } else if (decompressed.size == 1) {
            println("✗ WRONG LITERAL: Got '${decompressed[0].toChar()}' (${decompressed[0]}) instead of 'A' (65)")
            fail("Expected 'A' (65), but got '${decompressed[0].toChar()}' (${decompressed[0]})")
        } else {
            println("✗ EMPTY: No data decompressed")
            fail("No data was decompressed")
        }
    }

    private fun compressData(input: ByteArray): ByteArray {
        val stream = ZStream()
        try {
            val result = stream.deflateInit(Z_DEFAULT_COMPRESSION)
            if (result != Z_OK) throw RuntimeException("deflateInit failed: $result")
            
            stream.nextIn = input
            stream.availIn = input.size
            stream.nextInIndex = 0
            
            val output = ByteArray(input.size * 2 + 50)
            stream.nextOut = output
            stream.availOut = output.size  
            stream.nextOutIndex = 0
            
            val deflateResult = stream.deflate(Z_FINISH)
            if (deflateResult != Z_STREAM_END) throw RuntimeException("deflate failed: $deflateResult")
            
            return output.copyOf(stream.totalOut.toInt())
        } finally {
            stream.deflateEnd()
        }
    }

    private fun decompressData(input: ByteArray, maxOutputSize: Int): ByteArray {
        val stream = ZStream()
        try {
            val result = stream.inflateInit()
            if (result != Z_OK) throw RuntimeException("inflateInit failed: $result")
            
            stream.nextIn = input
            stream.availIn = input.size
            stream.nextInIndex = 0
            
            val output = ByteArray(maxOutputSize)
            stream.nextOut = output
            stream.availOut = output.size
            stream.nextOutIndex = 0
            
            val inflateResult = stream.inflate(Z_FINISH)
            if (inflateResult != Z_STREAM_END) throw RuntimeException("inflate failed: $inflateResult, msg: ${stream.msg}")
            
            return output.copyOf(stream.totalOut.toInt())
        } finally {
            stream.inflateEnd()
        }
    }
}