package ai.solace.zlib.inflate

import ai.solace.zlib.common.TREE_BASE_DIST
import ai.solace.zlib.common.TREE_BASE_LENGTH
import ai.solace.zlib.common.TREE_BL_ORDER
import ai.solace.zlib.common.TREE_EXTRA_DBITS
import ai.solace.zlib.common.TREE_EXTRA_LBITS
import ai.solace.zlib.common.Z_DATA_ERROR
import ai.solace.zlib.common.Z_DEFLATED
import ai.solace.zlib.common.Z_OK
import okio.BufferedSink
import okio.BufferedSource

/**
 * Streaming zlib inflate: reads from a BufferedSource and writes to a BufferedSink.
 * Maintains a 32 KiB sliding window for back-references and validates the Adler-32 trailer.
 */
object InflateStream {
    private const val WINDOW_SIZE = 32 * 1024

    private fun readZlibHeader(br: StreamingBitReader): Boolean {
        br.alignToByte()
        val cmf = br.readAlignedByte()
        val flg = br.readAlignedByte()
        val cm = cmf and 0x0F
        val cinfo = (cmf ushr 4) and 0x0F
        if (cm != Z_DEFLATED || cinfo > 7) return false
        if (((cmf shl 8) or flg) % 31 != 0) return false
        val presetDict = (flg and 0x20) != 0
        if (presetDict) {
            repeat(4) { br.readAlignedByte() } // skip DICTID
        }
        return true
    }

    private fun copyStored(br: StreamingBitReader, sink: BufferedSink, window: ByteArray, posRef: IntArray, adler: LongArray): Int {
        br.alignToByte()
        val len = br.readAlignedByte() or (br.readAlignedByte() shl 8)
        val nlen = br.readAlignedByte() or (br.readAlignedByte() shl 8)
        if ((len.inv() and 0xFFFF) != nlen) return Z_DATA_ERROR
        var pos = posRef[0]
        var s1 = adler[0] and 0xFFFF
        var s2 = (adler[0] ushr 16) and 0xFFFF
        repeat(len) {
            val b = br.readAlignedByte()
            sink.writeByte(b)
            window[pos] = b.toByte(); pos = (pos + 1) and (WINDOW_SIZE - 1)
            s1 = (s1 + (b and 0xFF)) % 65521
            s2 = (s2 + s1) % 65521
        }
        posRef[0] = pos
        adler[0] = (s2 shl 16) or s1
        return Z_OK
    }

    private val LENGTH_BASE = TREE_BASE_LENGTH
    private val LENGTH_EXTRA = TREE_EXTRA_LBITS
    private val DIST_BASE = TREE_BASE_DIST
    private val DIST_EXTRA = TREE_EXTRA_DBITS

    private fun writeByte(b: Int, sink: BufferedSink, window: ByteArray, posRef: IntArray, adler: LongArray) {
        var s1 = adler[0] and 0xFFFF
        var s2 = (adler[0] ushr 16) and 0xFFFF
        sink.writeByte(b)
        window[posRef[0]] = b.toByte()
        posRef[0] = (posRef[0] + 1) and (WINDOW_SIZE - 1)
        s1 = (s1 + (b and 0xFF)) % 65521
        s2 = (s2 + s1) % 65521
        adler[0] = (s2 shl 16) or s1
    }

    // --- Helpers to deduplicate decode logic ---
    /** Decode match length from a literal/length symbol (sym >= 257). Returns -1 on error. */
    private fun decodeLength(br: StreamingBitReader, sym: Int): Int {
        val lenCode = sym - 257
        if (lenCode !in 0..28) return -1
        val baseLen = LENGTH_BASE[lenCode]
        val extra = LENGTH_EXTRA[lenCode]
        val extraVal = if (extra > 0) br.take(extra) else 0
        return baseLen + extraVal
    }

    /** Decode match distance from a distance symbol. Returns -1 on error. */
    private fun decodeDistance(br: StreamingBitReader, distSym: Int): Int {
        if (distSym !in 0..29) return -1
        val baseDist = DIST_BASE[distSym]
        val extraD = DIST_EXTRA[distSym]
        val extraDVal = if (extraD > 0) br.take(extraD) else 0
        val dist = baseDist + extraDVal
        return if (dist <= 0) -1 else dist
    }

    /** Copy a back-reference of given length and distance using the sliding window. */
    private fun copyMatch(length: Int, dist: Int, sink: BufferedSink, window: ByteArray, posRef: IntArray, adler: LongArray) {
        var i = 0
        while (i < length) {
            val srcIndex = (posRef[0] - dist + WINDOW_SIZE) and (WINDOW_SIZE - 1)
            val b = window[srcIndex].toInt() and 0xFF
            writeByte(b, sink, window, posRef, adler)
            i++
        }
    }

    /** Read code lengths sequence (RLE encoded) using the code-length Huffman table. Returns null on error. */
    private fun readCodeLengths(br: StreamingBitReader, clTable: CanonicalHuffman.FullTable, count: Int): IntArray? {
        val out = IntArray(count)
        var i = 0
        while (i < count) {
            val sym = CanonicalHuffman.decodeOne(br, clTable)
            when (sym) {
                in 0..15 -> out[i++] = sym
                16 -> {
                    if (i == 0) return null
                    val repeat = 3 + br.take(2)
                    val prev = out[i - 1]
                    repeat(repeat) { if (i < count) out[i++] = prev else return null }
                }
                17 -> {
                    val repeat = 3 + br.take(3)
                    repeat(repeat) { if (i < count) out[i++] = 0 else return null }
                }
                18 -> {
                    val repeat = 11 + br.take(7)
                    repeat(repeat) { if (i < count) out[i++] = 0 else return null }
                }
                else -> return null
            }
        }
        return out
    }

    // Build both decode tables together to avoid duplicated code at call sites
    private fun buildDecodeTables(
        litLens: IntArray,
        distLens: IntArray,
    ): Pair<CanonicalHuffman.FullTable, CanonicalHuffman.FullTable> {
        val litTable = CanonicalHuffman.buildFull(litLens)
        val distTable = CanonicalHuffman.buildFull(distLens)
        return litTable to distTable
    }

    private fun decodeFixed(br: StreamingBitReader, sink: BufferedSink, window: ByteArray, posRef: IntArray, adler: LongArray): Int {
        val litLens = IntArray(288).also {
            for (i in 0..143) it[i] = 8
            for (i in 144..255) it[i] = 9
            for (i in 256..279) it[i] = 7
            for (i in 280..287) it[i] = 8
        }
        val distLens = IntArray(32) { 5 }
        val (litTable, distTable) = buildDecodeTables(litLens, distLens)

        loop@ while (true) {
            val sym = CanonicalHuffman.decodeOne(br, litTable)
            when {
                sym < 256 -> writeByte(sym, sink, window, posRef, adler)
                sym == 256 -> break@loop
                else -> {
                    val length = decodeLength(br, sym)
                    if (length < 0) return Z_DATA_ERROR
                    val distSym = CanonicalHuffman.decodeOne(br, distTable)
                    val dist = decodeDistance(br, distSym)
                    if (dist < 0) return Z_DATA_ERROR
                    copyMatch(length, dist, sink, window, posRef, adler)
                }
            }
        }
        return Z_OK
    }

    private fun decodeDynamic(br: StreamingBitReader, sink: BufferedSink, window: ByteArray, posRef: IntArray, adler: LongArray): Int {
        val hlit = br.take(5) + 257
        val hdist = br.take(5) + 1
        val hclen = br.take(4) + 4

        val order = TREE_BL_ORDER
        val clen = IntArray(19)
        for (i in 0 until hclen) clen[order[i]] = br.take(3)

        val clTable = CanonicalHuffman.buildFull(clen)
        val litLenLens = readCodeLengths(br, clTable, hlit) ?: return Z_DATA_ERROR
        val distLens = readCodeLengths(br, clTable, hdist) ?: return Z_DATA_ERROR

        val (litTable, distTable) = buildDecodeTables(litLenLens, distLens)

        loop@ while (true) {
            val sym = CanonicalHuffman.decodeOne(br, litTable)
            when {
                sym < 256 -> writeByte(sym, sink, window, posRef, adler)
                sym == 256 -> break@loop
                else -> {
                    val length = decodeLength(br, sym)
                    if (length < 0) return Z_DATA_ERROR
                    val distSym = CanonicalHuffman.decodeOne(br, distTable)
                    val dist = decodeDistance(br, distSym)
                    if (dist < 0) return Z_DATA_ERROR
                    copyMatch(length, dist, sink, window, posRef, adler)
                }
            }
        }
        return Z_OK
    }

    /**
     * Inflate a zlib-wrapped stream from [source] to [sink]. Returns Pair(resultCode, bytesOut).
     */
    fun inflateZlib(source: BufferedSource, sink: BufferedSink): Pair<Int, Long> {
        val br = StreamingBitReader(source)
        if (!readZlibHeader(br)) return Z_DATA_ERROR to 0L

        val window = ByteArray(WINDOW_SIZE)
        val posRef = intArrayOf(0)
        val adler = longArrayOf(1L) // initial Adler-32 value
        var totalOut = 0L

        while (true) {
            val last = br.take(1)
            when (br.take(2)) {
                0 -> {
                    val r = copyStored(br, sink, window, posRef, adler)
                    if (r != Z_OK) return r to totalOut
                }
                1 -> {
                    val r = decodeFixed(br, sink, window, posRef, adler)
                    if (r != Z_OK) return r to totalOut
                }
                2 -> {
                    val r = decodeDynamic(br, sink, window, posRef, adler)
                    if (r != Z_OK) return r to totalOut
                }
                else -> return Z_DATA_ERROR to totalOut
            }
            // We don't know bytesOut per-block; flush sink's buffer length heuristically
            totalOut = sink.buffer.size
            if (last == 1) break
        }

        // Read and validate Adler-32 trailer (big-endian)
        br.alignToByte()
        val a3 = br.readAlignedByte()
        val a2 = br.readAlignedByte()
        val a1 = br.readAlignedByte()
        val a0 = br.readAlignedByte()
        val trailer = ((a3 and 0xFF) shl 24) or ((a2 and 0xFF) shl 16) or ((a1 and 0xFF) shl 8) or (a0 and 0xFF)
        val current = adler[0].toInt()
        if (current != trailer) return Z_DATA_ERROR to totalOut

        return Z_OK to totalOut
    }
}

