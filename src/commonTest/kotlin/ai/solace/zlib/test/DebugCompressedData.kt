package ai.solace.zlib.test

import ai.solace.zlib.clean.CleanDeflate
import ai.solace.zlib.common.ZlibLogger
import ai.solace.zlib.common.Z_OK
import kotlin.test.Test

class DebugCompressedData {
    
    @Test
    fun debugHelloWorldData() {
        ZlibLogger.ENABLE_LOGGING = true
        ZlibLogger.DEBUG_ENABLED = true
        
        // The compressed "Hello, World!" data from the failing test
        val compressed = byteArrayOf(
            0x78.toByte(), 0x9c.toByte(), // zlib header
            0xf3.toByte(), 0x48.toByte(), 0xcd.toByte(), 0xc9.toByte(), 0xc9.toByte(), 0xd7.toByte(),
            0x51.toByte(), 0x08.toByte(), 0xcf.toByte(), 0x2f.toByte(), 0xca.toByte(), 0x49.toByte(),
            0x51.toByte(), 0x04.toByte(), 0x00.toByte(), // deflate data
            0x1f.toByte(), 0x9e.toByte(), 0x04.toByte(), 0x6a.toByte() // adler32
        )
        
        println("Compressed data (hex): ${compressed.map { it.toUByte().toString(16).padStart(2, '0') }.joinToString(" ")}")
        
        // Use the native implementation to see what it does
        val (status, result) = CleanDeflate.inflateZlib(compressed)
        println("Native result: status=$status, data='${result.decodeToString()}'")
        
        // Let's look at the raw bits after the zlib header
        val deflateData = compressed.sliceArray(2 until compressed.size - 4)
        println("DEFLATE data: ${deflateData.map { it.toUByte().toString(16).padStart(2, '0') }.joinToString(" ")}")
        
        // Convert to binary to see block structure
        val firstByte = deflateData[0].toInt() and 0xFF
        val bfinal = firstByte and 1
        val btype = (firstByte shr 1) and 3
        println("First DEFLATE byte: 0x${firstByte.toString(16)} = 0b${firstByte.toString(2).padStart(8, '0')}")
        println("BFINAL=$bfinal, BTYPE=$btype")
        
        when (btype) {
            0 -> println("Block type: Stored (uncompressed)")
            1 -> println("Block type: Fixed Huffman")
            2 -> println("Block type: Dynamic Huffman") 
            3 -> println("Block type: Reserved (error)")
        }
    }
}