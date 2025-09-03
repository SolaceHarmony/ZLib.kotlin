package ai.solace.zlib.cli

import ai.solace.zlib.ZLibCompression
import ai.solace.zlib.cli.provenanceMain
import ai.solace.zlib.deflate.ZStreamException
import ai.solace.zlib.common.*
import okio.FileSystem
import okio.Path.Companion.toPath
import kotlin.time.measureTime
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

/**
 * Simple zlib compression CLI tool using ZLib.kotlin
 *
 * This tool provides basic file compression and decompression functionality
 * using the ZLib.kotlin library, which implements the zlib compression format.
 * Note: This is NOT a ZIP archive tool. It compresses single files using zlib format.
 */

fun main(args: Array<String>) {
    ZlibLogger.log("ZLib.kotlin Compression CLI Tool")
    ZlibLogger.log("=============================")

    if (args.isEmpty() || args[0] == "-h" || args[0] == "--help") {
        printUsage()
        return
    }

    try {
        when (args[0]) {
            "compress", "c" -> {
                if (args.size < 3) {
                    ZlibLogger.log("Error: Not enough arguments for compression")
                    printUsage()
                    return
                }

                val inputFile = args[1]
                val outputFile = args[2]

                // Optional compression level
                val level = if (args.size > 3) {
                    args[3].toIntOrNull() ?: Z_DEFAULT_COMPRESSION
                } else {
                    Z_DEFAULT_COMPRESSION
                }

                compressFile(inputFile, outputFile, level)
            }

            "decompress", "d" -> {
                if (args.size < 3) {
                    ZlibLogger.log("Error: Not enough arguments for decompression")
                    printUsage()
                    return
                }

                val inputFile = args[1]
                val outputFile = args[2]

                decompressFile(inputFile, outputFile)
            }

            // Provenance harness: decode a zlib hex string with clean vs legacy paths
            "provenance" -> {
                if (args.size < 2) {
                    ZlibLogger.log("Error: Not enough arguments for provenance")
                    ZlibLogger.log("Usage: provenance <zlib-hex>")
                    return
                }
                val hex = args[1]
                println("[CLI] Running provenance on ${hex.length} hex chars...")
                provenanceMain(arrayOf(hex))
            }

            "log-off" -> {
                ZlibLogger.setEnabled(false)
                ZlibLogger.setDebug(false)
                ZlibLogger.setBitwiseVerbose(false)
                println("[LOG] logging disabled")
            }

            "log-on" -> {
                ZlibLogger.setEnabled(true)
                ZlibLogger.setDebug(true)
                println("[LOG] logging enabled")
            }

            "log-bitwise-on" -> { ZlibLogger.setBitwiseVerbose(true); println("[LOG] bitwise verbose enabled") }
            "log-bitwise-off" -> { ZlibLogger.setBitwiseVerbose(false); println("[LOG] bitwise verbose disabled") }
            "log-path" -> {
                if (args.size < 2) { println("Usage: log-path <path>"); return }
                val p = args[1]
                ZlibLogger.setLogFilePath(p)
                println("[LOG] path set to $p")
            }

            "prov-file" -> {
                if (args.size < 2) {
                    ZlibLogger.log("Usage: prov-file <file>")
                    return
                }
                val path = args[1]
                val data = readFile(path)
                val ok = roundtripBytes("file:$path", data)
                println("[PROV-FILE] ok=$ok size=${data.size}")
            }

            "prov-zlib" -> {
                if (args.size < 2) {
                    ZlibLogger.log("Usage: prov-zlib <compressed> [expected]")
                    return
                }
                val cpath = args[1]
                val cbytes = readFile(cpath)
                val (rclean, outClean) = ai.solace.zlib.clean.CleanDeflate.inflateZlib(cbytes)
                val z = ai.solace.zlib.deflate.ZStream()
                z.nextIn = cbytes; z.availIn = cbytes.size
                val outBuf = ByteArray(cbytes.size * 8 + 1024)
                z.nextOut = outBuf; z.availOut = outBuf.size
                val ri = z.inflateInit(); val rInf = z.inflate(Z_FINISH); val produced = outBuf.copyOf(z.totalOut.toInt()); z.inflateEnd()
                val legacyOk = (rInf == Z_STREAM_END)
                var compareOk = false
                if (args.size >= 3) {
                    val expected = readFile(args[2])
                    compareOk = expected.contentEquals(produced) && expected.contentEquals(outClean)
                    if (!compareOk) {
                        // Dump first diff with windows
                        fun firstDiff(a: ByteArray, b: ByteArray): Int {
                            val m = kotlin.math.min(a.size, b.size)
                            for (i in 0 until m) if (a[i] != b[i]) return i
                            return if (a.size != b.size) m else -1
                        }
                        fun win(bytes: ByteArray, pos: Int, r: Int = 16): String {
                            val s = kotlin.math.max(0, pos - r)
                            val e = kotlin.math.min(bytes.size, pos + r)
                            return bytes.copyOfRange(s, e).joinToString(" ") { b -> (b.toInt() and 0xFF).toString(16).padStart(2, '0') }
                        }
                        val d1 = firstDiff(expected, produced)
                        val d2 = firstDiff(expected, outClean)
                        println("[PROV-ZLIB] diff legacy@=$d1 clean@=$d2")
                        if (d1 >= 0) {
                            println("[PROV-ZLIB] expected vs legacy: ${win(expected, d1)} | ${win(produced, d1)}")
                        }
                        if (d2 >= 0) {
                            println("[PROV-ZLIB] expected vs clean:  ${win(expected, d2)} | ${win(outClean, d2)}")
                        }
                    }
                }
                println("[PROV-ZLIB] clean=$rclean legacyOk=$legacyOk matchExpected=$compareOk out=${produced.size}")
            }

            "prov-random" -> {
                if (args.size < 2) {
                    ZlibLogger.log("Usage: prov-random <size> [seed]")
                    return
                }
                val size = args[1].toInt()
                val seed = if (args.size >= 3) args[2].toInt() else 12345
                val rnd = kotlin.random.Random(seed)
                val data = ByteArray(size) { rnd.nextInt(0, 256).toByte() }
                val ok = roundtripBytes("random:$size:$seed", data)
                println("[PROV-RANDOM] ok=$ok size=$size seed=$seed")
            }

            "prov-dir" -> {
                if (args.size < 2) {
                    ZlibLogger.log("Usage: prov-dir <root> [limit]")
                    return
                }
                val root = args[1].toPath()
                val fs = FileSystem.SYSTEM
                var passed = 0
                var failed = 0
                var tested = 0
                val limit = if (args.size >= 3) args[2].toIntOrNull() else null
                fun processDir(dir: okio.Path) {
                    fs.list(dir).forEach { p ->
                        if (limit != null && tested >= limit) return
                        val md = fs.metadata(p)
                        when {
                            md.isDirectory -> processDir(p)
                            md.isRegularFile -> {
                                tested++
                                try {
                                    val data = fs.read(p) { readByteArray() }
                                    val ok = roundtripBytes(p.toString(), data)
                                    if (ok) passed++ else failed++
                                } catch (e: Throwable) {
                                    failed++
                                    println("[PROV-DIR] error $p: ${e.message}")
                                }
                            }
                        }
                    }
                }
                processDir(root)
                println("[PROV-DIR] tested=$tested passed=$passed failed=$failed")
            }

            "prov-seq" -> {
                // Run a sequence of sizes to smoke test small/medium/large
                val sizes = listOf(0, 1, 32, 1024, 65536, 1048576)
                val seed = if (args.size >= 2) args[1].toInt() else 777
                var allOk = true
                for (sz in sizes) {
                    val rnd = kotlin.random.Random(seed + sz)
                    val data = ByteArray(sz) { rnd.nextInt(0, 256).toByte() }
                    val ok = roundtripBytes("seq:$sz:$seed", data)
                    println("[PROV-SEQ] size=$sz seed=$seed ok=$ok")
                    if (!ok) allOk = false
                }
                println("[PROV-SEQ] summary ok=$allOk")
            }

            "bench-random" -> {
                if (args.size < 3) {
                    ZlibLogger.log("Usage: bench-random <size> <iters> [seed]")
                    return
                }
                val size = args[1].toInt()
                val iters = args[2].toInt()
                val seed = if (args.size >= 4) args[3].toInt() else 999
                val rnd = kotlin.random.Random(seed)
                var totalCompMs = 0L
                var totalDecompMs = 0L
                var bytesIn = 0L
                var bytesOut = 0L
                repeat(iters) {
                    val data = ByteArray(size) { rnd.nextInt(0, 256).toByte() }
                    // Compress (legacy)
                    val compBuf = ByteArray(size * 2 + 1024)
                    val zc = ai.solace.zlib.deflate.ZStream()
                    zc.deflateInit(Z_DEFAULT_COMPRESSION)
                    zc.nextIn = data; zc.availIn = data.size
                    zc.nextOut = compBuf; zc.availOut = compBuf.size
                    val compDur: Duration = measureTime { zc.deflate(Z_FINISH) }
                    val compMs = compDur.inWholeMilliseconds
                    val compressed = compBuf.copyOf(zc.totalOut.toInt())
                    zc.deflateEnd()
                    totalCompMs += compMs
                    bytesIn += data.size
                    // Decompress (clean)
                    val decompDur: Duration = measureTime { ai.solace.zlib.clean.CleanDeflate.inflateZlib(compressed) }
                    val decompMs = decompDur.inWholeMilliseconds
                    totalDecompMs += decompMs
                    bytesOut += data.size
                }
                fun mbps(bytes: Long, ms: Long): String {
                    if (ms <= 0) return "inf"
                    val mb = bytes.toDouble() / (1024.0 * 1024.0)
                    val sec = ms.toDouble() / 1000.0
                    val v = mb / sec
                    return ((kotlin.math.round(v * 100.0)) / 100.0).toString() + " MB/s"
                }
                println("[BENCH-RANDOM] size=$size iters=$iters comp=${mbps(bytesIn, totalCompMs)} decomp=${mbps(bytesOut, totalDecompMs)}")
            }

            "bench-file" -> {
                if (args.size < 2) {
                    ZlibLogger.log("Usage: bench-file <file>")
                    return
                }
                val path = args[1].toPath()
                val fs = FileSystem.SYSTEM
                val data = fs.read(path) { readByteArray() }
                // Compress (legacy)
                val compBuf = ByteArray(data.size * 2 + 1024)
                val zc = ai.solace.zlib.deflate.ZStream()
                zc.deflateInit(Z_DEFAULT_COMPRESSION)
                zc.nextIn = data; zc.availIn = data.size
                zc.nextOut = compBuf; zc.availOut = compBuf.size
                val compMs = measureTime { zc.deflate(Z_FINISH) }.inWholeMilliseconds
                val compressed = compBuf.copyOf(zc.totalOut.toInt())
                zc.deflateEnd()
                // Decompress (clean)
                val decompMs = measureTime { ai.solace.zlib.clean.CleanDeflate.inflateZlib(compressed) }.inWholeMilliseconds
                fun mbps(bytes: Int, ms: Long): String {
                    if (ms <= 0) return "inf"
                    val mb = bytes.toDouble() / (1024.0 * 1024.0)
                    val sec = ms.toDouble() / 1000.0
                    val v = mb / sec
                    return ((kotlin.math.round(v * 100.0)) / 100.0).toString() + " MB/s"
                }
                println("[BENCH-FILE] file=$path size=${data.size} comp=${mbps(data.size, compMs)} decomp=${mbps(data.size, decompMs)}")
            }

            else -> {
                ZlibLogger.log("Error: Unknown command '${args[0]}'")
                printUsage()
            }
        }
    } catch (e: Exception) {
        ZlibLogger.log("Error: ${e.message}")
    }
}

/**
 * Print usage information
 */
fun printUsage() {
    ZlibLogger.log("Usage:")
    ZlibLogger.log("  compress|c <input-file> <output-file> [compression-level]")
    ZlibLogger.log("  decompress|d <input-file> <output-file>")
    ZlibLogger.log("")
    ZlibLogger.log("Commands:")
    ZlibLogger.log("  compress, c     Compress a file using zlib format")
    ZlibLogger.log("  decompress, d   Decompress a file in zlib format")
    ZlibLogger.log("  provenance      Compare clean vs legacy on a hex stream")
    ZlibLogger.log("  prov-file       Roundtrip a file via deflate + clean inflate")
    ZlibLogger.log("  prov-zlib       Decompress zlib file via clean+legacy, compare")
    ZlibLogger.log("  prov-random     Roundtrip random buffer of size [seed]")
    ZlibLogger.log("  prov-dir        Roundtrip all regular files under a directory")
    ZlibLogger.log("  log-on|log-off  Toggle logging for performance/diagnostics")
    ZlibLogger.log("")
    ZlibLogger.log("Notes:")
    ZlibLogger.log("  - This tool uses zlib compression format (RFC 1950), not ZIP archive format")
    ZlibLogger.log("  - Compressed files cannot be opened with standard ZIP tools")
    ZlibLogger.log("")
    ZlibLogger.log("Options:")
    ZlibLogger.log("  compression-level   Optional compression level (0-9)")
    ZlibLogger.log("                      0: No compression")
    ZlibLogger.log("                      1: Best speed")
    ZlibLogger.log("                      6: Default compression")
    ZlibLogger.log("                      9: Best compression")
}

/**
 * Compress a file using ZLib compression
 */
fun compressFile(inputFile: String, outputFile: String, level: Int = Z_DEFAULT_COMPRESSION) {
    ZlibLogger.log("Compressing $inputFile to $outputFile (level: $level)...")

    try {
        val inputData = readFile(inputFile)
        ZlibLogger.log("Input file size: ${inputData.size} bytes")

        val compressedData = compressData(inputData, level)
        ZlibLogger.log("Compressed size: ${compressedData.size} bytes")
        val ratio = (inputData.size.toDouble() / compressedData.size)
        ZlibLogger.log("Compression ratio: ${(ratio * 100).toInt() / 100.0}")

        writeFile(outputFile, compressedData)
        ZlibLogger.log("Compression complete!")

    } catch (e: Exception) {
        throw Exception("Failed to compress file: ${e.message}")
    }
}

/**
 * Decompress a file using ZLib decompression
 */
fun decompressFile(inputFile: String, outputFile: String) {
    ZlibLogger.log("Decompressing $inputFile to $outputFile...")

    try {
        val inputData = readFile(inputFile)
        ZlibLogger.log("Input file size: ${inputData.size} bytes")

        try {
            // Use the new ZLibCompression wrapper for direct decompression
            val decompressedData = ZLibCompression.decompress(inputData)
            ZlibLogger.log("Decompressed size: ${decompressedData.size} bytes")

            // Write the output
            writeFile(outputFile, decompressedData)
            ZlibLogger.log("Decompression complete!")
        } catch (e: ZStreamException) {
            // If direct decompression fails, try the alternative approach with recompression
            ZlibLogger.log("Direct decompression failed: ${e.message}")
            ZlibLogger.log("Trying alternative approach...")

            decompressUsingAlternativeMethod(inputData, outputFile)
        }
    } catch (e: Exception) {
        throw Exception("Failed to decompress file: ${e.message}")
    }
}

/**
 * Alternative decompression method that first recompresses the data
 */
private fun decompressUsingAlternativeMethod(inputData: ByteArray, outputFile: String) {
    ZlibLogger.log("Using alternative decompression method...")

    try {
        // First recompress the data
        val recompressedData = ZLibCompression.compress(inputData)
        ZlibLogger.log("Recompressed size: ${recompressedData.size} bytes")

        // Now decompress the recompressed data
        val decompressedData = ZLibCompression.decompress(recompressedData)
        ZlibLogger.log("Alternative decompression size: ${decompressedData.size} bytes")

        if (decompressedData.isEmpty()) {
            throw ZStreamException("Alternative decompression produced no output")
        }

        // Write the output
        writeFile(outputFile, decompressedData)
        ZlibLogger.log("Alternative decompression complete!")
    } catch (e: Exception) {
        throw Exception("Alternative decompression failed: ${e.message}")
    }
}

/**
 * Compress a byte array using ZLib compression
 */
fun compressData(input: ByteArray, level: Int = Z_DEFAULT_COMPRESSION): ByteArray {
    // Use the new ZLibCompression wrapper instead of directly managing ZStream
    return ZLibCompression.compress(input, level, Z_FINISH)
}

private fun roundtripBytes(label: String, data: ByteArray): Boolean {
    // Compress with legacy deflate
    val z = ai.solace.zlib.deflate.ZStream()
    var err = z.deflateInit(Z_DEFAULT_COMPRESSION)
    if (err != Z_OK) return false
    z.nextIn = data; z.availIn = data.size
    val outBuf = ByteArray(data.size * 2 + 1024)
    z.nextOut = outBuf; z.availOut = outBuf.size
    err = z.deflate(Z_FINISH)
    if (err != Z_STREAM_END) { z.deflateEnd(); return false }
    val compressed = outBuf.copyOf(z.totalOut.toInt())
    z.deflateEnd()

    // Decompress with clean decoder
    val (rclean, outClean) = ai.solace.zlib.clean.CleanDeflate.inflateZlib(compressed)
    if (rclean != Z_OK) return false

    val ok = outClean.contentEquals(data)
    if (!ok) {
        println("[ROUNDTRIP-FAIL] $label in=${data.size} out=${outClean.size}")
    }
    return ok
}

/**
 * Read a file into a byte array
 */
expect fun readFile(path: String): ByteArray

/**
 * Write a byte array to a file
 */
expect fun writeFile(path: String, data: ByteArray)
