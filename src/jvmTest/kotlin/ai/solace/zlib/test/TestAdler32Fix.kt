import ai.solace.zlib.deflate.ZStream
import ai.solace.zlib.common.*

/**
 * A simple test program to verify the Adler32 fix.
 * This is based on the SimpleTest.kt file.
 */
fun main() {
    println("Starting Adler32 fix verification test...")
    
    // Test with minimal input - a single character
    val originalString = "A"
    val originalData = originalString.encodeToByteArray()
    
    println("Original data: '$originalString' (${originalData.size} bytes)")
    
    // Compress the data
    val deflatedData = deflateData(originalData)
    println("Compressed data size: ${deflatedData.size} bytes")
    
    // Print the compressed data in hex format
    println("Compressed data (hex): ${deflatedData.joinToString("") { 
        val hex = (it.toInt() and 0xFF).toString(16).uppercase()
        if (hex.length == 1) "0$hex" else hex 
    }}")
    
    // Decompress the data
    val inflatedData = inflateData(deflatedData, originalData.size)
    
    // Check if decompression was successful
    if (inflatedData.isNotEmpty()) {
        val inflatedString = inflatedData.decodeToString()
        println("Decompressed data: '$inflatedString' (${inflatedData.size} bytes)")
        
        if (originalString == inflatedString) {
            println("SUCCESS: Decompressed data matches original")
        } else {
            println("FAILURE: Decompressed data does not match original")
            println("Original: '$originalString'")
            println("Decompressed: '$inflatedString'")
        }
    } else {
        println("FAILURE: Decompression failed, no data returned")
    }
    
    // Test with a longer string
    val longerString = "Hello, World! This is a test of the Adler32 fix."
    val longerData = longerString.encodeToByteArray()
    
    println("\nTesting with longer string: '$longerString'")
    
    // Compress the longer data
    val deflatedLongerData = deflateData(longerData)
    println("Compressed data size: ${deflatedLongerData.size} bytes")
    
    // Decompress the longer data
    val inflatedLongerData = inflateData(deflatedLongerData, longerData.size)
    
    // Check if decompression was successful
    if (inflatedLongerData.isNotEmpty()) {
        val inflatedLongerString = inflatedLongerData.decodeToString()
        println("Decompressed data: '$inflatedLongerString'")
        
        if (longerString == inflatedLongerString) {
            println("SUCCESS: Decompressed longer data matches original")
        } else {
            println("FAILURE: Decompressed longer data does not match original")
        }
    } else {
        println("FAILURE: Decompression of longer data failed, no data returned")
    }
}

/**
 * Compress the given data using ZStream.
 */
fun deflateData(input: ByteArray): ByteArray {
    println("Deflating data...")
    
    val stream = ZStream()
    var err = stream.deflateInit(Z_DEFAULT_COMPRESSION)
    println("deflateInit returned: $err, msg=${stream.msg}")
    
    stream.nextIn = input
    stream.availIn = input.size
    
    val outputBuffer = ByteArray(input.size * 2 + 20) // Ensure buffer is large enough
    stream.nextOut = outputBuffer
    stream.availOut = outputBuffer.size
    
    println("Before deflate: availIn=${stream.availIn}, nextInIndex=${stream.nextInIndex}, availOut=${stream.availOut}")
    err = stream.deflate(Z_FINISH)
    println("deflate returned: $err, msg=${stream.msg}, totalOut=${stream.totalOut}")
    
    if (err != Z_STREAM_END) {
        println("deflate did not return Z_STREAM_END, checking stream state")
        println("Stream state: availIn=${stream.availIn}, nextInIndex=${stream.nextInIndex}, availOut=${stream.availOut}, nextOutIndex=${stream.nextOutIndex}")
    }
    
    val result = outputBuffer.copyOf(stream.totalOut.toInt())
    
    err = stream.deflateEnd()
    println("deflateEnd returned: $err, msg=${stream.msg}")
    
    return result
}

/**
 * Decompress the given data using ZStream.
 */
fun inflateData(input: ByteArray, originalSize: Int): ByteArray {
    println("Inflating data...")
    
    val stream = ZStream()
    var err = stream.inflateInit()
    println("inflateInit returned: $err, msg=${stream.msg}")
    
    stream.nextIn = input
    stream.availIn = input.size
    stream.nextInIndex = 0
    
    val outputBuffer = ByteArray(originalSize * 4 + 200) // Ensure buffer is large enough
    stream.nextOut = outputBuffer
    stream.availOut = outputBuffer.size
    stream.nextOutIndex = 0
    
    println("Before inflate: availIn=${stream.availIn}, nextInIndex=${stream.nextInIndex}, availOut=${stream.availOut}")
    
    // Print the input data in hex format
    println("Input data (hex): ${input.joinToString("") { 
        val hex = (it.toInt() and 0xFF).toString(16).uppercase()
        if (hex.length == 1) "0$hex" else hex 
    }}")
    
    err = stream.inflate(Z_FINISH)
    println("inflate returned: $err, msg=${stream.msg}, totalOut=${stream.totalOut}")
    
    if (err != Z_STREAM_END) {
        println("inflate did not return Z_STREAM_END, checking stream state")
        println("Stream state: availIn=${stream.availIn}, nextInIndex=${stream.nextInIndex}, availOut=${stream.availOut}, nextOutIndex=${stream.nextOutIndex}")
        println("Stream internal state: iState=${stream.iState}, dState=${stream.dState}, adler=${stream.adler}")
        
        // If there was an error, return an empty array
        return ByteArray(0)
    }
    
    val result = outputBuffer.copyOf(stream.totalOut.toInt())
    
    err = stream.inflateEnd()
    println("inflateEnd returned: $err, msg=${stream.msg}")
    
    return result
}