import ai.solace.zlib.deflate.ZStream
import ai.solace.zlib.common.*

fun main() {
    println("=== Huffman Fix Validation Test ===")
    
    // Test 1: Single character with default compression (uses Huffman)
    println("\n--- Test 1: Single character with Huffman compression ---")
    testSingleCharacter("A", Z_DEFAULT_COMPRESSION)
    
    // Test 2: Single character with no compression (stored blocks, no Huffman)
    println("\n--- Test 2: Single character with no compression ---") 
    testSingleCharacter("A", Z_NO_COMPRESSION)
    
    // Test 3: Multiple characters with default compression
    println("\n--- Test 3: Multiple characters with Huffman compression ---")
    testSingleCharacter("Hello", Z_DEFAULT_COMPRESSION)
    
    // Test 4: Multiple characters with no compression
    println("\n--- Test 4: Multiple characters with no compression ---")
    testSingleCharacter("Hello", Z_NO_COMPRESSION)
}

fun testSingleCharacter(input: String, level: Int) {
    try {
        val inputBytes = input.encodeToByteArray()
        println("Input: '$input' (bytes: ${inputBytes.joinToString(", ") { (it.toInt() and 0xFF).toString() }})")
        
        // Compress
        val compressed = compress(inputBytes, level)
        println("Compressed (${compressed.size} bytes): ${compressed.joinToString(" ") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }}")
        
        // Decompress
        val decompressed = decompress(compressed, inputBytes.size * 4 + 100)
        val result = decompressed.decodeToString()
        
        println("Decompressed: '$result' (bytes: ${decompressed.joinToString(", ") { (it.toInt() and 0xFF).toString() }})")
        
        if (input == result) {
            println("✅ SUCCESS: Compression/decompression worked correctly")
        } else {
            println("❌ FAILURE: Expected '$input', got '$result'")
        }
    } catch (e: Exception) {
        println("❌ ERROR: ${e.message}")
        e.printStackTrace()
    }
}

fun compress(input: ByteArray, level: Int): ByteArray {
    val stream = ZStream()
    
    val result = stream.deflateInit(level)
    if (result != Z_OK) throw RuntimeException("deflateInit failed: $result, ${stream.msg}")
    
    stream.nextIn = input
    stream.availIn = input.size
    stream.nextInIndex = 0
    
    val output = ByteArray(input.size * 2 + 100)
    stream.nextOut = output
    stream.availOut = output.size
    stream.nextOutIndex = 0
    
    val deflateResult = stream.deflate(Z_FINISH)
    if (deflateResult != Z_STREAM_END) throw RuntimeException("deflate failed: $deflateResult, ${stream.msg}")
    
    val compressed = output.copyOf(stream.totalOut.toInt())
    
    val endResult = stream.deflateEnd()
    if (endResult != Z_OK) throw RuntimeException("deflateEnd failed: $endResult, ${stream.msg}")
    
    return compressed
}

fun decompress(input: ByteArray, maxOutputSize: Int): ByteArray {
    val stream = ZStream()
    
    val result = stream.inflateInit()
    if (result != Z_OK) throw RuntimeException("inflateInit failed: $result, ${stream.msg}")
    
    stream.nextIn = input
    stream.availIn = input.size
    stream.nextInIndex = 0
    
    val output = ByteArray(maxOutputSize)
    stream.nextOut = output
    stream.availOut = output.size
    stream.nextOutIndex = 0
    
    val inflateResult = stream.inflate(Z_FINISH)
    if (inflateResult != Z_STREAM_END) throw RuntimeException("inflate failed: $inflateResult, ${stream.msg}")
    
    val decompressed = output.copyOf(stream.totalOut.toInt())
    
    val endResult = stream.inflateEnd()
    if (endResult != Z_OK) throw RuntimeException("inflateEnd failed: $endResult, ${stream.msg}")
    
    return decompressed
}