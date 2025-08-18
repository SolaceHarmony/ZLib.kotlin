#!/usr/bin/env kotlin

import ai.solace.zlib.deflate.ZStream
import ai.solace.zlib.common.*

fun main() {
    println("Starting simple compression test...")
    
    val input = "A".toByteArray()
    println("Input: ${input.map { it.toInt() and 0xff }}")
    
    val stream = ZStream()
    var err = stream.deflateInit(Z_DEFAULT_COMPRESSION)
    if (err != Z_OK) {
        println("deflateInit failed: $err, ${stream.msg}")
        return
    }
    
    stream.nextIn = input
    stream.availIn = input.size
    stream.nextInIndex = 0
    
    val outputBuffer = ByteArray(input.size * 2 + 100)
    stream.nextOut = outputBuffer
    stream.availOut = outputBuffer.size
    stream.nextOutIndex = 0
    
    err = stream.deflate(Z_FINISH)
    if (err != Z_STREAM_END) {
        println("deflate failed: $err, ${stream.msg}")
        return
    }
    
    val compressed = outputBuffer.copyOf(stream.totalOut.toInt())
    println("Compressed: ${compressed.map { it.toInt() and 0xff }}")
    println("Compressed hex: ${compressed.map { "%02x".format(it.toInt() and 0xff) }.joinToString("")}")
    
    err = stream.deflateEnd()
    if (err != Z_OK) {
        println("deflateEnd failed: $err, ${stream.msg}")
        return
    }
    
    // Now try to decompress
    println("Attempting decompression...")
    
    val stream2 = ZStream()
    err = stream2.inflateInit(MAX_WBITS)
    if (err != Z_OK) {
        println("inflateInit failed: $err, ${stream2.msg}")
        return
    }
    
    stream2.nextIn = compressed
    stream2.availIn = compressed.size
    stream2.nextInIndex = 0
    
    val decompressedBuffer = ByteArray(input.size * 4 + 100)
    stream2.nextOut = decompressedBuffer
    stream2.availOut = decompressedBuffer.size
    stream2.nextOutIndex = 0
    
    err = stream2.inflate(Z_FINISH)
    println("inflate result: $err, msg: ${stream2.msg}")
    
    if (err == Z_STREAM_END) {
        val decompressed = decompressedBuffer.copyOf(stream2.totalOut.toInt())
        println("Decompressed: ${decompressed.map { it.toInt() and 0xff }}")
        println("Decompressed string: '${String(decompressed)}'")
    }
    
    stream2.inflateEnd()
}