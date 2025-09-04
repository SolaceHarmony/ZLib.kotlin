package ai.solace.zlib.cli

import ai.solace.zlib.common.Z_OK
import ai.solace.zlib.inflate.InflateStream
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.buffer

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("ZLib.kotlin - Pure arithmetic zlib implementation")
        println("Commands:")
        println("  decompress <input.zz> <output.txt> - Decompress a zlib file")
        return
    }

    when (args[0]) {
        "decompress" -> {
            if (args.size < 3) {
                println("Usage: decompress <input.zz> <output.txt>")
                return
            }
            val inPath = args[1].toPath()
            val outPath = args[2].toPath()
            val src = FileSystem.SYSTEM.source(inPath).buffer()
            val snk = FileSystem.SYSTEM.sink(outPath).buffer()
            try {
                val (result, bytesOut) = InflateStream.inflateZlib(src, snk)
                snk.flush()
                if (result == Z_OK) {
                    val inSize = FileSystem.SYSTEM.metadata(inPath).size ?: -1L
                    println("Decompressed ${inSize} bytes to ${bytesOut} bytes")
                } else {
                    println("Decompression failed: $result")
                }
            } finally {
                try { src.close() } catch (_: Throwable) {}
                try { snk.close() } catch (_: Throwable) {}
            }
        }

        else -> {
            println("Unknown command: ${args[0]}")
        }
    }
}

// File operations are defined in FileOperations.macos.kt
