import ai.solace.zlib.deflate.ZStream
import ai.solace.zlib.common.*

fun main() {
    val originalString = "A"
    val originalData = originalString.encodeToByteArray()
    
    println("Original: $originalString")
    println("Original bytes: ${originalData.joinToString { it.toString() }}")
    
    val stream = ZStream()
    val err = stream.deflateInit(Z_NO_COMPRESSION)
    println("deflateInit result: $err")
    
    stream.nextIn = originalData
    stream.availIn = originalData.size
    stream.nextInIndex = 0
    
    val outputBuffer = ByteArray(100)
    stream.nextOut = outputBuffer
    stream.availOut = outputBuffer.size
    stream.nextOutIndex = 0
    
    val deflateErr = stream.deflate(Z_FINISH)
    println("deflate result: $deflateErr")
    println("totalOut: ${stream.totalOut}")
    println("adler: ${stream.adler}")
    
    val deflatedData = outputBuffer.copyOf(stream.totalOut.toInt())
    println("Deflated bytes (${deflatedData.size}): ${deflatedData.joinToString { "0x%02x".format(it.toInt() and 0xFF) }}")
    
    stream.deflateEnd()
    
    // Now try to inflate
    val inflateStream = ZStream()
    val inflateErr = inflateStream.inflateInit(MAX_WBITS)
    println("\ninflateInit result: $inflateErr")
    
    inflateStream.nextIn = deflatedData
    inflateStream.availIn = deflatedData.size
    inflateStream.nextInIndex = 0
    
    val inflateBuffer = ByteArray(100)
    inflateStream.nextOut = inflateBuffer
    inflateStream.availOut = inflateBuffer.size
    inflateStream.nextOutIndex = 0
    
    val infErr = inflateStream.inflate(Z_FINISH)
    println("inflate result: $infErr")
    println("inflate msg: ${inflateStream.msg}")
    println("inflate adler: ${inflateStream.adler}")
    
    if (infErr == Z_STREAM_END) {
        val result = inflateBuffer.copyOf(inflateStream.totalOut.toInt())
        println("Inflated: ${result.decodeToString()}")
    }
    
    inflateStream.inflateEnd()
}

main()