package ai.solace.zlib.deflate

import ai.solace.zlib.bitwise.checksum.Adler32Utils
import ai.solace.zlib.common.TREE_BASE_DIST
import ai.solace.zlib.common.TREE_BASE_LENGTH
import ai.solace.zlib.common.TREE_EXTRA_DBITS
import ai.solace.zlib.common.TREE_EXTRA_LBITS
import ai.solace.zlib.inflate.CanonicalHuffman
import ai.solace.zlib.inflate.StreamingBitWriter
import okio.BufferedSink
import okio.BufferedSource

/**
 * Streaming zlib compressor (stored blocks only, no Huffman) for correctness and portability.
 * - Writes zlib header (CMF/FLG) with computed FCHECK and FLEVEL from requested level.
 * - Emits a sequence of stored (BTYPE=00) blocks of size <= 65535.
 * - Appends Adler-32 trailer (big-endian).
 */
object DeflateStream {
    private const val MAX_STORED = 65535

    /** level: 1 (fast) .. 9 (best) maps to zlib FLEVEL advisory. */
    private fun flevelFor(level: Int): Int = when {
        level >= 9 -> 3
        level >= 6 -> 2
        level >= 2 -> 1
        else -> 0
    }

    private fun writeZlibHeader(sink: BufferedSink, level: Int) {
        val cm = 8 // deflate
        val cinfo = 7 // 32K window
        val cmf = (cinfo shl 4) or cm // 0x78
        val flevel = flevelFor(level) and 0x3
        val fdict = 0
        var flg = (flevel shl 6) or (fdict shl 5)
        val cmfFlg = (cmf shl 8) or flg
        val fcheck = (31 - (cmfFlg % 31)) % 31
        flg = (flg and 0xE0) or fcheck
        sink.writeByte(cmf)
        sink.writeByte(flg)
    }

    /** Compress from source to sink with zlib wrapper using fixed Huffman (with simple RLE), fallback to stored when level<=0. */
    fun compressZlib(source: BufferedSource, sink: BufferedSink, level: Int = 6): Long {
        return when {
            level <= 0 -> compressZlibStored(source, sink, level)
            else -> compressZlibFixed(source, sink, level) // temporarily route dynamic to fixed while we finish multi-block dynamic
        }
    }

    /** Stored-block compressor (no compression). */
    private fun compressZlibStored(source: BufferedSource, sink: BufferedSink, level: Int = 0): Long {
        // Header
        writeZlibHeader(sink, level)
        val bw = StreamingBitWriter(sink)

        var totalIn = 0L
        var adler = 1L
        val buf = ByteArray(64 * 1024)
        var eof = false

        while (!eof) {
            var toRead = MAX_STORED
            var filled = 0
            // Fill up to MAX_STORED
            while (filled < toRead && !source.exhausted()) {
                val n = source.read(buf, 0, minOf(buf.size, toRead - filled))
                if (n == -1) break
                if (n == 0) break
                filled += n
            }
            if (filled < MAX_STORED && source.exhausted()) eof = true

            // Write block header: BFINAL, BTYPE=00 (stored)
            val bfinal = if (eof) 1 else 0
            bw.writeBits(bfinal, 1)
            bw.writeBits(0, 2) // BTYPE=00
            bw.alignToByte()

            // LEN and NLEN (little-endian 16-bit)
            val len = filled and 0xFFFF
            val nlen = len.inv() and 0xFFFF
            sink.writeByte(len and 0xFF)
            sink.writeByte((len ushr 8) and 0xFF)
            sink.writeByte(nlen and 0xFF)
            sink.writeByte((nlen ushr 8) and 0xFF)

            // Write payload and update adler
            if (filled > 0) {
                sink.write(buf, 0, filled)
                totalIn += filled
                adler = Adler32Utils.adler32(adler, buf, 0, filled)
            }
        }

        // Trailer (big-endian)
        val a = adler.toInt()
        sink.writeByte((a ushr 24) and 0xFF)
        sink.writeByte((a ushr 16) and 0xFF)
        sink.writeByte((a ushr 8) and 0xFF)
        sink.writeByte(a and 0xFF)
        sink.flush()
        return totalIn
    }

    /** Fixed-Huffman compressor with streaming LZ77 (greedy+lazy), limited matcher, arithmetic bit writing. */
    private fun compressZlibFixed(source: BufferedSource, sink: BufferedSink, level: Int = 6): Long {
        writeZlibHeader(sink, level)
        val bw = StreamingBitWriter(sink)

        // Fixed literal/length code lengths
        val litLenLens = IntArray(288)
        for (i in 0..143) litLenLens[i] = 8
        for (i in 144..255) litLenLens[i] = 9
        litLenLens[256] = 7
        for (i in 257..279) litLenLens[i] = 7
        for (i in 280..287) litLenLens[i] = 8
        val (litCodes, litBits) = CanonicalHuffman.buildEncoder(litLenLens)

        // Fixed distance codes: 32 symbols, all length 5
        val distLens = IntArray(32) { 5 }
        val (distCodes, distBits) = CanonicalHuffman.buildEncoder(distLens)

        fun writeSymbol(sym: Int) {
            bw.writeBits(litCodes[sym], litBits[sym])
        }
        fun writeLength(length: Int) {
            // Map length to code and extra bits
            val base = TREE_BASE_LENGTH
            val extra = TREE_EXTRA_LBITS
            var code = -1
            var extraBits = 0
            var extraVal = 0
            for (i in base.indices) {
                val b = base[i]
                val e = extra[i]
                val maxLen = b + ((if (e > 0) (1 shl e) - 1 else 0))
                if (length in b..maxLen) {
                    code = 257 + i
                    extraBits = e
                    extraVal = length - b
                    break
                }
            }
            require(code != -1) { "Invalid length $length" }
            bw.writeBits(litCodes[code], litBits[code])
            if (extraBits > 0) bw.writeBits(extraVal, extraBits)
        }
        fun writeDistance(dist: Int) {
            // Map distance to code and extra bits
            val base = TREE_BASE_DIST
            val extra = TREE_EXTRA_DBITS
            var code = -1
            var extraBits = 0
            var extraVal = 0
            for (i in base.indices) {
                val b = base[i]
                val e = extra[i]
                val maxD = b + ((if (e > 0) (1 shl e) - 1 else 0))
                if (dist in b..maxD) {
                    code = i
                    extraBits = e
                    extraVal = dist - b
                    break
                }
            }
            require(code != -1) { "Invalid distance $dist" }
            bw.writeBits(distCodes[code], distBits[code])
            if (extraBits > 0) bw.writeBits(extraVal, extraBits)
        }

        // Emit single fixed block header (BFINAL=1, BTYPE=01)
        bw.writeBits(1, 1)
        bw.writeBits(1, 2)

        // Streaming LZ77 with small lookahead buffer and 32K sliding window
        val LOOKAHEAD = 1 shl 15 // 32KB buffer
        val WINDOW = 1 shl 15    // 32KB window
        val la = ByteArray(LOOKAHEAD)
        val window = ByteArray(WINDOW)
        val HEAD_SIZE = 1 shl 15
        val head = IntArray(HEAD_SIZE) { -1 }
        val prev = IntArray(WINDOW) { -1 }

        var adler = 1L
        var totalIn = 0L
        var pos = 0 // absolute position
        var laLen = 0
        var laOff = 0

        fun hash3(a: Int, b: Int, c: Int): Int {
            var h = a * 251 + b * 271 + c * 277
            h = h and (HEAD_SIZE - 1)
            return h
        }

        fun windowByte(p: Int): Int = window[p and (WINDOW - 1)].toInt() and 0xFF

        fun insertAt(absPos: Int) {
            if (laLen - (absPos - pos + laOff) < 3) return
            val rel = absPos - pos + laOff
            val a = la[rel].toInt() and 0xFF
            val b = la[rel + 1].toInt() and 0xFF
            val c = la[rel + 2].toInt() and 0xFF
            val h = hash3(a, b, c)
            val widx = absPos and (WINDOW - 1)
            prev[widx] = head[h]
            head[h] = absPos
        }

        fun emitLiteral(b: Int) {
            writeSymbol(b)
        }

        fun emitMatch(len: Int, dist: Int) {
            var remaining = len
            while (remaining > 0) {
                val l = minOf(remaining, 258)
                writeLength(l)
                writeDistance(dist)
                remaining -= l
            }
        }

        // Fill initial lookahead
        while (laLen < LOOKAHEAD && !source.exhausted()) {
            val n = source.read(la, laLen, minOf(LOOKAHEAD - laLen, 64 * 1024))
            if (n <= 0) break
            adler = Adler32Utils.adler32(adler, la, laLen, n)
            laLen += n
            totalIn += n
        }

        // Initialize first few inserts
        var cur = pos
        while (cur + 2 < pos + laLen) {
            insertAt(cur)
            cur++
        }

        val MAX_CHAIN = 32
        while (laLen > 0) {
            val rel = laOff
            val available = laLen
            val b0 = la[rel].toInt() and 0xFF

            var bestLen = 0
            var bestDist = 0
            if (available >= 3) {
                val a = la[rel].toInt() and 0xFF
                val b = la[rel + 1].toInt() and 0xFF
                val c = la[rel + 2].toInt() and 0xFF
                val h = hash3(a, b, c)
                var m = head[h]
                var chain = 0
                while (m != -1 && chain < MAX_CHAIN) {
                    val dist = pos - m
                    if (dist in 1..WINDOW) {
                        // Compare
                        var L = 0
                        while (L < 258 && L < available) {
                            val w = windowByte(m + L)
                            val v = la[rel + L].toInt() and 0xFF
                            if (w != v) break
                            L++
                        }
                        if (L >= 3 && L > bestLen) {
                            bestLen = L
                            bestDist = dist
                            if (L >= 258) break
                        }
                    }
                    m = prev[m and (WINDOW - 1)]
                    chain++
                }
            }

            if (bestLen >= 3) {
                emitMatch(bestLen, bestDist)
                // Insert each position in match into hash/window
                var k = 0
                while (k < bestLen) {
                    val widx = pos and (WINDOW - 1)
                    window[widx] = la[laOff]
                    if (available - k >= 3) insertAt(pos)
                    pos++
                    laOff++
                    laLen--
                    k++
                }
            } else {
                emitLiteral(b0)
                val widx = pos and (WINDOW - 1)
                window[widx] = la[laOff]
                if (available >= 3) insertAt(pos)
                pos++
                laOff++
                laLen--
            }

            // Refill lookahead when low
            if (laLen < 1024 && !source.exhausted()) {
                // Compact remaining to start
                if (laOff > 0 && laLen > 0) {
                    for (t in 0 until laLen) la[t] = la[laOff + t]
                    laOff = 0
                } else if (laLen == 0) {
                    laOff = 0
                }
                val n = source.read(la, laLen, minOf(LOOKAHEAD - laLen, 64 * 1024))
                if (n > 0) {
                    adler = Adler32Utils.adler32(adler, la, laLen, n)
                    laLen += n
                    totalIn += n
                    // Insert new triplets starting from (pos + laLen - n - 2) to end
                    var start = pos + laLen - n
                    var insertPos = start
                    while (insertPos + 2 < pos + laLen) {
                        insertAt(insertPos)
                        insertPos++
                    }
                }
            }
        }

        // End of block
        writeSymbol(256)
        bw.flush()

        // Zlib trailer
        val a = adler.toInt()
        sink.writeByte((a ushr 24) and 0xFF)
        sink.writeByte((a ushr 16) and 0xFF)
        sink.writeByte((a ushr 8) and 0xFF)
        sink.writeByte(a and 0xFF)
        sink.flush()
        return totalIn
    }

    /** Dynamic-Huffman compressor using frequency-based code lengths (≤15). Chooses stored vs fixed vs dynamic per block. */
    private fun compressZlibDynamic(source: BufferedSource, sink: BufferedSink, level: Int = 6): Long {
        writeZlibHeader(sink, level)

        data class TokLit(val b: Int)
        data class TokMatch(val len: Int, val dist: Int)

        // Sliding LZ77 state (persists across blocks)
        val LOOKAHEAD = 1 shl 15 // 32 KiB lookahead buffer
        val WINDOW = 1 shl 15    // 32 KiB window
        val la = ByteArray(LOOKAHEAD)
        val window = ByteArray(WINDOW)
        val HEAD_SIZE = 1 shl 15
        val head = IntArray(HEAD_SIZE) { -1 }
        val prev = IntArray(WINDOW) { -1 }

        var adler = 1L
        var totalIn = 0L
        var pos = 0
        var laLen = 0
        var laOff = 0

        fun hash3(a: Int, b: Int, c: Int): Int { var h = a * 251 + b * 271 + c * 277; return h and (HEAD_SIZE - 1) }
        fun windowByte(p: Int): Int = window[p and (WINDOW - 1)].toInt() and 0xFF
        fun insertAt(absPos: Int) { if (laLen - (absPos - pos + laOff) < 3) return; val rel = absPos - pos + laOff; val a = la[rel].toInt() and 0xFF; val b = la[rel + 1].toInt() and 0xFF; val c = la[rel + 2].toInt() and 0xFF; val h = hash3(a, b, c); val widx = absPos and (WINDOW - 1); prev[widx] = head[h]; head[h] = absPos }

        val bw = StreamingBitWriter(sink)
        val MAX_BLOCK = MAX_STORED // bound block size so stored is always an option
        val maxChain = when {
            level <= 2 -> 8
            level <= 4 -> 16
            level <= 6 -> 32
            else -> 64
        }
        val doLazy = false // temporarily disable lazy parsing for stability

        var firstBlock = true
        while (true) {
            // Ensure lookahead buffer is compacted at block start so new reads append contiguously
            if (laOff > 0) {
                if (laLen > 0) {
                    var t = 0
                    while (t < laLen) { la[t] = la[laOff + t]; t++ }
                }
                laOff = 0
            }
            // Reset per-block structures
            val tokens = ArrayList<Any>(1 shl 14)
            val litFreq = IntArray(286)
            val distFreq = IntArray(30)

            var blockRead = 0
            val rawBuf = ByteArray(MAX_BLOCK)
            var rawLen = 0

            // Initial fill for this block (limited by MAX_BLOCK)
            while (laLen < LOOKAHEAD && blockRead < MAX_BLOCK && !source.exhausted()) {
                val toRead = minOf(LOOKAHEAD - laLen, MAX_BLOCK - blockRead, 64 * 1024)
                val n = source.read(la, laLen, toRead)
                if (n <= 0) break
                // Save a copy for possible stored block
                if (rawLen + n <= rawBuf.size) {
                    for (i in 0 until n) rawBuf[rawLen + i] = la[laLen + i]
                    rawLen += n
                }
                adler = Adler32Utils.adler32(adler, la, laLen, n)
                laLen += n
                totalIn += n
                blockRead += n
            }

            // Seed or update hash for current lookahead
            var cur = pos
            while (cur + 2 < pos + laLen) { insertAt(cur); cur++ }

            // Tokenize only the bytes read for this block
            while (laLen > 0) {
                val rel = laOff
                val available = minOf(laLen, LOOKAHEAD - laOff)
                if (rel >= LOOKAHEAD) break
                val b0 = la[rel].toInt() and 0xFF
                var bestLen = 0
                var bestDist = 0
                if (available >= 3 && rel + 2 < LOOKAHEAD) {
                    val a = la[rel].toInt() and 0xFF
                    val b = la[rel + 1].toInt() and 0xFF
                    val c = la[rel + 2].toInt() and 0xFF
                    val h = hash3(a, b, c)
                    var m = head[h]
                    var chain = 0
                    while (m != -1 && chain < maxChain) {
                        val dist = pos - m
                        if (dist in 1..WINDOW) {
                            var L = 0
                            while (L < 258 && L < available) {
                                val w = windowByte(m + L)
                                if (rel + L >= LOOKAHEAD) break
                                val v = la[rel + L].toInt() and 0xFF
                                if (w != v) break
                                L++
                            }
                            if (L >= 3 && L > bestLen) {
                                bestLen = L
                                bestDist = dist
                                if (L >= 258) break
                            }
                        }
                        m = prev[m and (WINDOW - 1)]
                        chain++
                    }
                }

                // Optional lazy evaluation for better ratios
                if (doLazy && bestLen >= 3 && available > 3 && rel + 3 < LOOKAHEAD) {
                    val rel2 = rel + 1
                    val a2 = la[rel2].toInt() and 0xFF
                    val b2 = la[rel2 + 1].toInt() and 0xFF
                    val c2 = la[rel2 + 2].toInt() and 0xFF
                    val h2 = hash3(a2, b2, c2)
                    var m2 = head[h2]
                    var chain2 = 0
                    var best2 = 0
                    var dist2 = 0
                    while (m2 != -1 && chain2 < maxChain) {
                        val dist = (pos + 1) - m2
                        if (dist in 1..WINDOW) {
                            var L = 0
                            while (L < 258 && (rel2 + L) < (laOff + laLen)) {
                                val w = windowByte(m2 + L)
                                if (rel2 + L >= LOOKAHEAD) break
                                val v = la[rel2 + L].toInt() and 0xFF
                                if (w != v) break
                                L++
                            }
                            if (L >= 3 && L > best2) {
                                best2 = L
                                dist2 = dist
                                if (L >= 258) break
                            }
                        }
                        m2 = prev[m2 and (WINDOW - 1)]
                        chain2++
                    }
                    if (best2 >= bestLen + 1) {
                        // Prefer literal; advance by 1 and continue
                        tokens.add(TokLit(b0))
                        litFreq[b0]++
                        val widx = pos and (WINDOW - 1)
                        window[widx] = la[laOff]
                        pos++
                        laOff++
                        laLen--
                        continue
                    }
                }
                if (bestLen >= 3) {
                    tokens.add(TokMatch(bestLen, bestDist))
                    // account frequencies
                    val base = TREE_BASE_LENGTH
                    val extra = TREE_EXTRA_LBITS
                    run {
                        for (i in base.indices) {
                            val b = base[i]
                            val e = extra[i]
                            val maxLen = b + ((if (e > 0) (1 shl e) - 1 else 0))
                            if (bestLen in b..maxLen) { litFreq[257 + i]++; break }
                        }
                    }
                    run {
                        val baseD = TREE_BASE_DIST
                        val extraD = TREE_EXTRA_DBITS
                        for (i in baseD.indices) {
                            val b = baseD[i]
                            val e = extraD[i]
                            val maxD = b + ((if (e > 0) (1 shl e) - 1 else 0))
                            if (bestDist in b..maxD) { distFreq[i]++; break }
                        }
                    }
                    var k = 0
                    while (k < bestLen) {
                        val widx = pos and (WINDOW - 1)
                        window[widx] = la[laOff]
                        if (available - k >= 3) insertAt(pos)
                        pos++
                        laOff++
                        laLen--
                        k++
                    }
                } else {
                    tokens.add(TokLit(b0))
                    litFreq[b0]++
                    val widx = pos and (WINDOW - 1)
                    window[widx] = la[laOff]
                    if (available >= 3) insertAt(pos)
                    pos++
                    laOff++
                    laLen--
                }

                // Refill within this block if budget remains
                if (laLen < 1024 && blockRead < MAX_BLOCK && !source.exhausted()) {
                    if (laOff > 0 && laLen > 0) {
                        for (t in 0 until laLen) la[t] = la[laOff + t]
                        laOff = 0
                    } else if (laLen == 0) {
                        laOff = 0
                    }
                    val toRead = minOf(LOOKAHEAD - laLen, MAX_BLOCK - blockRead, 64 * 1024)
                    if (toRead > 0) {
                        val n = source.read(la, laLen, toRead)
                        if (n > 0) {
                            // Save raw
                            if (rawLen + n <= rawBuf.size) {
                                for (i in 0 until n) rawBuf[rawLen + i] = la[laLen + i]
                                rawLen += n
                            }
                            adler = Adler32Utils.adler32(adler, la, laLen, n)
                            laLen += n
                            totalIn += n
                            blockRead += n
                            var insertPos = pos + laLen - n
                            while (insertPos + 2 < pos + laLen) { insertAt(insertPos); insertPos++ }
                        }
                    }
                }
            }

            // Determine if this is the last block
            val isLast = source.exhausted() && laLen == 0

            // If no input and it's the very first block, still emit an empty block (EOB only)
            if (tokens.isEmpty()) {
                litFreq[256]++
            } else {
                // Always ensure EOB present
                litFreq[256]++
            }
            // Ensure at least one distance code exists
            run {
                var any = false
                for (v in distFreq) if (v != 0) { any = true; break }
                if (!any) distFreq[0] = 1
            }

            // Build dynamic code lengths (<=15)
            val dynLitLens = HuffmanBuilder.buildLengths(litFreq, 15, ensureSymbol = 256)
            val dynDistLens = HuffmanBuilder.buildLengths(distFreq, 15, ensureSymbol = 0)

            fun lastNonZero(a: IntArray): Int { var i = a.size - 1; while (i >= 0 && a[i] == 0) i--; return i }
            val lastLit = maxOf(lastNonZero(dynLitLens), 256)
            val lastDist = maxOf(lastNonZero(dynDistLens), 0)
            val HLIT = (lastLit + 1) - 257
            val HDIST = (lastDist + 1) - 1

            data class ClSym(val sym: Int, val extraBits: Int = 0, val extraCount: Int = 0)
            fun rleLengths(lengths: IntArray, count: Int): List<ClSym> {
                val out = ArrayList<ClSym>()
                var i = 0
                var prev = -1
                while (i < count) {
                    val l = lengths[i]
                    if (l == 0) {
                        var run = 1
                        var j = i + 1
                        while (j < count && lengths[j] == 0 && run < 138) { run++; j++ }
                        when {
                            run >= 11 -> out.add(ClSym(18, 7, run - 11))
                            run >= 3 -> out.add(ClSym(17, 3, run - 3))
                            else -> repeat(run) { out.add(ClSym(0)) }
                        }
                        i += run
                        prev = 0
                    } else {
                        var run = 1
                        var j = i + 1
                        while (j < count && lengths[j] == l && run < 6) { run++; j++ }
                        if (prev == l && run >= 3) {
                            out.add(ClSym(16, 2, run - 3))
                        } else {
                            out.add(ClSym(l))
                            if (run >= 2) {
                                val rem = run - 1
                                if (rem >= 3) out.add(ClSym(16, 2, rem - 3)) else repeat(rem) { out.add(ClSym(l)) }
                            }
                        }
                        i += run
                        prev = l
                    }
                }
                return out
            }

            val clLit = rleLengths(dynLitLens, lastLit + 1)
            val clDist = rleLengths(dynDistLens, lastDist + 1)
            val clSeq = ArrayList<ClSym>(clLit.size + clDist.size).apply { addAll(clLit); addAll(clDist) }

            // BL lens (<=7) from CL frequencies
            val clFreq = IntArray(19)
            for (c in clSeq) clFreq[c.sym]++
            val blLens = HuffmanBuilder.buildLengths(clFreq, 7, ensureSymbol = 0)
            val BL_ORDER = ai.solace.zlib.common.TREE_BL_ORDER
            var hclen = 19
            while (hclen > 4 && blLens[BL_ORDER[hclen - 1]] == 0) hclen--
            val HCLEN = hclen - 4

            // Build encoders and estimate costs
            val (dynLitCodes, dynLitBits) = CanonicalHuffman.buildEncoder(dynLitLens)
            val (dynDistCodes, dynDistBits) = CanonicalHuffman.buildEncoder(dynDistLens)
            val (blCodes, blBits) = CanonicalHuffman.buildEncoder(blLens)

            fun tokenCost(litBits: IntArray, distBits: IntArray): Long {
                var bits = 0L
                for (t in tokens) {
                    when (t) {
                        is TokLit -> bits += litBits[t.b]
                        is TokMatch -> {
                            val base = TREE_BASE_LENGTH
                            val extra = TREE_EXTRA_LBITS
                            var code = -1
                            var e = 0
                            val length = t.len
                            for (i in base.indices) {
                                val b = base[i]
                                val ee = extra[i]
                                val maxL = b + ((if (ee > 0) (1 shl ee) - 1 else 0))
                                if (length in b..maxL) { code = 257 + i; e = ee; break }
                            }
                            bits += litBits[code]
                            bits += e
                            val dbase = TREE_BASE_DIST
                            val dex = TREE_EXTRA_DBITS
                            var dcode = -1
                            var de = 0
                            val dist = t.dist
                            for (i in dbase.indices) {
                                val b = dbase[i]
                                val ee = dex[i]
                                val maxD = b + ((if (ee > 0) (1 shl ee) - 1 else 0))
                                if (dist in b..maxD) { dcode = i; de = ee; break }
                            }
                            bits += distBits[dcode]
                            bits += de
                        }
                    }
                }
                // EOB
                bits += litBits[256]
                return bits
            }

            var headerDyn = 0L
            headerDyn += 1 + 2 + 5 + 5 + 4
            headerDyn += 3L * (HCLEN + 4)
            for (c in clSeq) {
                headerDyn += blBits[c.sym]
                when (c.sym) {
                    16 -> headerDyn += 2
                    17 -> headerDyn += 3
                    18 -> headerDyn += 7
                }
            }
            val costDynamic = headerDyn + tokenCost(dynLitBits, dynDistBits)

            val fixedLitLens = IntArray(288).also {
                for (i in 0..143) it[i] = 8
                for (i in 144..255) it[i] = 9
                it[256] = 7
                for (i in 257..279) it[i] = 7
                for (i in 280..287) it[i] = 8
            }
            val fixedDistLens = IntArray(32) { 5 }
            val (fixedLitCodes, fixedLitBits) = CanonicalHuffman.buildEncoder(fixedLitLens)
            val (fixedDistCodes, fixedDistBits) = CanonicalHuffman.buildEncoder(fixedDistLens)
            val costFixed = 1L + 2L + tokenCost(fixedLitBits, fixedDistBits)

            val padStored = (8 - (bw.bitMod8() % 8)) % 8
            val costStored = padStored + 1L + 2L + 16L + 16L + (rawLen.toLong() shl 3)

            // Choose encoding for this block
            val choice = when {
                costStored <= costDynamic && costStored <= costFixed -> 0 // stored
                costDynamic <= costFixed -> 2 // dynamic
                else -> 1 // fixed
            }

            // Emit block
            if (choice == 0) {
                // Stored block
                bw.writeBits(if (isLast) 1 else 0, 1)
                bw.writeBits(0, 2)
                bw.alignToByte()
                // LEN and NLEN little-endian
                val len = rawLen and 0xFFFF
                val nlen = len.inv() and 0xFFFF
                sink.writeByte(len and 0xFF)
                sink.writeByte((len ushr 8) and 0xFF)
                sink.writeByte(nlen and 0xFF)
                sink.writeByte((nlen ushr 8) and 0xFF)
                if (rawLen > 0) sink.write(rawBuf, 0, rawLen)
                // aligned now; writer is byte-aligned due to alignToByte()
            } else if (choice == 2) {
                // Dynamic block
                bw.writeBits(if (isLast) 1 else 0, 1)
                bw.writeBits(2, 2)
                bw.writeBits(HLIT, 5)
                bw.writeBits(HDIST, 5)
                bw.writeBits(HCLEN, 4)
                for (i in 0 until hclen) bw.writeBits(blLens[BL_ORDER[i]], 3)
                fun writeBL(sym: Int) { if (blBits[sym] == 0) error("BL code missing for sym=$sym"); bw.writeBits(blCodes[sym], blBits[sym]) }
                for (c in clSeq) {
                    writeBL(c.sym)
                    when (c.sym) {
                        16 -> bw.writeBits(c.extraCount, 2)
                        17 -> bw.writeBits(c.extraCount, 3)
                        18 -> bw.writeBits(c.extraCount, 7)
                    }
                }
                fun writeSymbol(sym: Int) { bw.writeBits(dynLitCodes[sym], dynLitBits[sym]) }
                fun writeLength(length: Int) {
                    val base = TREE_BASE_LENGTH
                    val extra = TREE_EXTRA_LBITS
                    var code = -1
                    var extraBits = 0
                    var extraVal = 0
                    for (i in base.indices) {
                        val b = base[i]
                        val e = extra[i]
                        val maxLen = b + ((if (e > 0) (1 shl e) - 1 else 0))
                        if (length in b..maxLen) { code = 257 + i; extraBits = e; extraVal = length - b; break }
                    }
                    require(code != -1) { "Invalid length $length" }
                    bw.writeBits(dynLitCodes[code], dynLitBits[code])
                    if (extraBits > 0) bw.writeBits(extraVal, extraBits)
                }
                fun writeDistance(dist: Int) {
                    val base = TREE_BASE_DIST
                    val extra = TREE_EXTRA_DBITS
                    var code = -1
                    var extraBits = 0
                    var extraVal = 0
                    for (i in base.indices) {
                        val b = base[i]
                        val e = extra[i]
                        val maxD = b + ((if (e > 0) (1 shl e) - 1 else 0))
                        if (dist in b..maxD) { code = i; extraBits = e; extraVal = dist - b; break }
                    }
                    require(code != -1) { "Invalid distance $dist" }
                    bw.writeBits(dynDistCodes[code], dynDistBits[code])
                    if (extraBits > 0) bw.writeBits(extraVal, extraBits)
                }
                for (t in tokens) {
                    when (t) {
                        is TokLit -> writeSymbol(t.b)
                        is TokMatch -> { var remaining = t.len; val d = t.dist; while (remaining > 0) { val l = minOf(remaining, 258); writeLength(l); writeDistance(d); remaining -= l } }
                    }
                }
                writeSymbol(256)
            } else {
                // Fixed block
                bw.writeBits(if (isLast) 1 else 0, 1)
                bw.writeBits(1, 2)
                fun writeSymbol(sym: Int) { bw.writeBits(fixedLitCodes[sym], fixedLitBits[sym]) }
                fun writeLength(length: Int) {
                    val base = TREE_BASE_LENGTH
                    val extra = TREE_EXTRA_LBITS
                    var code = -1
                    var extraBits = 0
                    var extraVal = 0
                    for (i in base.indices) {
                        val b = base[i]
                        val e = extra[i]
                        val maxLen = b + ((if (e > 0) (1 shl e) - 1 else 0))
                        if (length in b..maxLen) { code = 257 + i; extraBits = e; extraVal = length - b; break }
                    }
                    require(code != -1) { "Invalid length $length" }
                    bw.writeBits(fixedLitCodes[code], fixedLitBits[code])
                    if (extraBits > 0) bw.writeBits(extraVal, extraBits)
                }
                fun writeDistance(dist: Int) {
                    val base = TREE_BASE_DIST
                    val extra = TREE_EXTRA_DBITS
                    var code = -1
                    var extraBits = 0
                    var extraVal = 0
                    for (i in base.indices) {
                        val b = base[i]
                        val e = extra[i]
                        val maxD = b + ((if (e > 0) (1 shl e) - 1 else 0))
                        if (dist in b..maxD) { code = i; extraBits = e; extraVal = dist - b; break }
                    }
                    require(code != -1) { "Invalid distance $dist" }
                    bw.writeBits(fixedDistCodes[code], fixedDistBits[code])
                    if (extraBits > 0) bw.writeBits(extraVal, extraBits)
                }
                for (t in tokens) {
                    when (t) {
                        is TokLit -> writeSymbol(t.b)
                        is TokMatch -> { var remaining = t.len; val d = t.dist; while (remaining > 0) { val l = minOf(remaining, 258); writeLength(l); writeDistance(d); remaining -= l } }
                    }
                }
                writeSymbol(256)
            }

            if (isLast) break
            firstBlock = false
        }

        bw.flush()

        // Zlib trailer
        val a = adler.toInt()
        sink.writeByte((a ushr 24) and 0xFF)
        sink.writeByte((a ushr 16) and 0xFF)
        sink.writeByte((a ushr 8) and 0xFF)
        sink.writeByte(a and 0xFF)
        sink.flush()
        return totalIn
    }
}
