package ai.solace.zlib.cli

import ai.solace.zlib.common.Z_OK
import ai.solace.zlib.inflate.Inflate

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("ZLib.kotlin - Pure arithmetic zlib implementation")
        println("Commands:")
        println("  decompress <input.zz> <output.txt> - Decompress a zlib file")
        println("  test-inflate <input.zz> - Test decompression with Inflate")
        return
    }

    when (args[0]) {
        "decompress" -> {
            if (args.size < 3) {
                println("Usage: decompress <input.zz> <output.txt>")
                return
            }
            val input = readFile(args[1])
            val (result, output) = Inflate.inflateZlib(input)
            if (result == Z_OK) {
                writeFile(args[2], output)
                println("Decompressed ${input.size} bytes to ${output.size} bytes")
            } else {
                println("Decompression failed: $result")
            }
        }

        "test-inflate" -> {
            if (args.size < 2) {
                println("Usage: test-inflate <input.zz>")
                return
            }
            val input = readFile(args[1])
            val (result, output) = Inflate.inflateZlib(input)
            if (result == Z_OK) {
                println("Success! Decompressed to ${output.size} bytes")
                val preview = output.take(100).toByteArray().decodeToString()
                println("First 100 chars: $preview")
            } else {
                println("Decompression failed: $result")
            }
        }

        else -> {
            println("Unknown command: ${args[0]}")
        }
    }
}

// File operations are defined in FileOperations.macos.kt
