import ai.solace.zlib.deflate.ZStream
import ai.solace.zlib.common.*

fun main() {
    println("Testing basic compression/decompression...")
    
    // Test with minimal input - a single character
    val originalString = "A"
    val originalData = originalString.encodeToByteArray()
    
    println("Original data: '$originalString' (${originalData.size} bytes)")
    
    // Compress
    val deflatedData = deflateData(originalData)
    println("Compressed data size: ${deflatedData.size} bytes")
    
    // Print the compressed data in hex format
    println("Compressed data (hex): ${deflatedData.joinToString("") { 
        val hex = (it.toInt() and 0xFF).toString(16).uppercase()
        if (hex.length == 1) "0$hex" else hex 
    }}")
    
    // Decompress
    val inflatedData = inflateData(deflatedData, originalData.size)
    val inflatedString = inflatedData.decodeToString()
    println("Decompressed data: '$inflatedString' (${inflatedData.size} bytes)")
    
    if (originalString == inflatedString) {
        println("SUCCESS: Round-trip works!")
    } else {
        println("FAILURE: Expected '$originalString', got '$inflatedString'")
    }
}

private fun deflateData(input: ByteArray): ByteArray {
    val stream = ZStream()
    val err = stream.deflateInit(Z_DEFAULT_COMPRESSION)
    if (err != Z_OK) error("deflateInit failed: $err")
    
    stream.nextIn = input
    stream.availIn = input.size
    
    val outputBuffer = ByteArray(input.size * 2 + 20)
    stream.nextOut = outputBuffer
    stream.availOut = outputBuffer.size
    
    val result = stream.deflate(Z_FINISH)
    if (result != Z_STREAM_END) error("deflate failed: $result")
    
    val compressed = outputBuffer.copyOf(stream.totalOut.toInt())
    stream.deflateEnd()
    return compressed
}

private fun inflateData(input: ByteArray, originalSize: Int): ByteArray {
    val stream = ZStream()
    val err = stream.inflateInit()
    if (err != Z_OK) error("inflateInit failed: $err")
    
    stream.nextIn = input
    stream.availIn = input.size
    stream.nextInIndex = 0
    
    val outputBuffer = ByteArray(originalSize * 4 + 200)
    stream.nextOut = outputBuffer
    stream.availOut = outputBuffer.size
    stream.nextOutIndex = 0
    
    val result = stream.inflate(Z_NO_FLUSH)
    if (result != Z_STREAM_END) error("inflate failed: $result")
    
    val decompressed = outputBuffer.copyOf(stream.totalOut.toInt())
    stream.inflateEnd()
    return decompressed
}