package ai.solace.zlib.cli

import ai.solace.zlib.clean.CleanDeflate
import ai.solace.zlib.common.Z_DATA_ERROR
import ai.solace.zlib.common.Z_OK
import ai.solace.zlib.common.ZlibLogger
import ai.solace.zlib.deflate.ZStream

/**
 * Provenance harness:
 * - Takes a hex string of a zlib stream and decodes via:
 *   1) Clean fixed/stored-only decoder
 *   2) Existing Inflate via ZStream
 * - Prints outcomes and whether bytes match.
 *
 * Usage:
 *   provenance <hex>
 */
fun provenanceMain(args: Array<String>) {
    println("[PROVENANCE] start with args=${args.size}")
    if (args.isEmpty()) {
        println("Usage: provenance <zlib-hex>")
        return
    }

    val hex = args[0].trim()
    val input = hexToBytes(hex)

    ZlibLogger.log("[PROV] start: hexLen=${hex.length} bytes=${input.size}")

    println("[PROVENANCE] invoking CleanDeflate")
    ZlibLogger.log("[PROV][CLEAN] begin")
    val (rClean, outClean) = CleanDeflate.inflateZlib(input)
    println("CleanDecoder: result=$rClean len=${outClean.size}")
    ZlibLogger.log("[PROV][CLEAN] result=$rClean len=${outClean.size}")

    val z = ZStream()
    z.nextIn = input
    z.availIn = input.size
    val outBuf = ByteArray(input.size * 8 + 1024)
    z.nextOut = outBuf
    z.availOut = outBuf.size
    println("[PROVENANCE] invoking legacy ZStream.inflate")
    ZlibLogger.log("[PROV][LEGACY] begin")
    val rInit = z.inflateInit()
    val rInf = z.inflate(4)
    val produced = outBuf.copyOf(z.totalOut.toInt())
    val rEnd = z.inflateEnd()
    println("ZStream: init=$rInit inflate=$rInf end=$rEnd len=${produced.size}")
    ZlibLogger.log("[PROV][LEGACY] init=$rInit inflate=$rInf end=$rEnd len=${produced.size}")

    if (rClean == Z_OK && rInf == 1 /* Z_STREAM_END */) {
        val match = produced.contentEquals(outClean)
        println("Match: $match")
        ZlibLogger.log("[PROV] match=$match")
        if (!match) {
            val min = minOf(produced.size, outClean.size)
            var diffAt = -1
            for (i in 0 until min) {
                if (produced[i] != outClean[i]) { diffAt = i; break }
            }
            println("First diff at: $diffAt")
            ZlibLogger.log("[PROV] firstDiff=$diffAt")
            if (diffAt >= 0) {
                fun windowHex(bytes: ByteArray, pos: Int, radius: Int = 16): String {
                    val start = maxOf(0, pos - radius)
                    val end = minOf(bytes.size, pos + radius)
                    return bytes.copyOfRange(start, end).joinToString(" ") { b -> (b.toInt() and 0xFF).toString(16).padStart(2, '0') }
                }
                val cWin = windowHex(outClean, diffAt)
                val lWin = windowHex(produced, diffAt)
                println("Clean window:   $cWin")
                println("Legacy window:  $lWin")
                ZlibLogger.log("[PROV] cleanWin=$cWin")
                ZlibLogger.log("[PROV] legacyWin=$lWin")
            }
        }
    } else if (rClean == Z_DATA_ERROR) {
        println("CleanDecoder does not support dynamic blocks or encountered error.")
        ZlibLogger.log("[PROV] cleanError=Z_DATA_ERROR")
    }
}

private fun hexToBytes(hex: String): ByteArray {
    val s = hex.filter { !it.isWhitespace() }
    require(s.length % 2 == 0) { "hex length must be even" }
    val out = ByteArray(s.length / 2)
    var i = 0
    var j = 0
    while (i < s.length) {
        out[j++] = s.substring(i, i + 2).toInt(16).toByte()
        i += 2
    }
    return out
}
