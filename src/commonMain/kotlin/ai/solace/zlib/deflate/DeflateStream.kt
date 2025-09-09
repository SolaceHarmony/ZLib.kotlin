@file:Suppress("EmptyElseBlock")

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
    private fun flevelFor(level: Int): Int =
        when {
            level >= 9 -> 3
            level >= 6 -> 2
            level >= 2 -> 1
            else -> 0
        }

    private fun writeZlibHeader(
        sink: BufferedSink,
        level: Int,
    ) {
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

    // Shared constant for 32 KiB deflate window/buffer
    private const val DEFAULT_WINDOW: Int = 1 shl 15

    // ---- Small internal helpers to reduce duplicated fragments ----

    /** Write a 32-bit big-endian integer to sink. */
    private fun writeBe32(
        sink: BufferedSink,
        value: Int,
    ) {
        sink.writeByte((value ushr 24) and 0xFF)
        sink.writeByte((value ushr 16) and 0xFF)
        sink.writeByte((value ushr 8) and 0xFF)
        sink.writeByte(value and 0xFF)
    }

    /** Write zlib Adler-32 trailer (big-endian 4 bytes). */
    private fun writeAdler32Trailer(
        sink: BufferedSink,
        adler: Long,
    ) {
        writeBe32(sink, adler.toInt())
    }

    /**
     * Emit a stored block header: BFINAL bit, BTYPE=00, align to byte, then LEN and NLEN.
     * Does not write the payload.
     */
    private fun writeStoredBlockHeader(
        bw: StreamingBitWriter,
        sink: BufferedSink,
        length: Int,
        bfinal: Int,
    ) {
        bw.writeBits(bfinal, 1)
        bw.writeBits(0, 2) // BTYPE=00
        bw.alignToByte()
        val len = length and 0xFFFF
        val nlen = len.inv() and 0xFFFF
        sink.writeByte(len and 0xFF)
        sink.writeByte((len ushr 8) and 0xFF)
        sink.writeByte(nlen and 0xFF)
        sink.writeByte((nlen ushr 8) and 0xFF)
    }

    /** Write a literal symbol using provided code tables. */
    private fun writeLiteral(
        bw: StreamingBitWriter,
        codes: IntArray,
        bits: IntArray,
        sym: Int,
    ) {
        bw.writeBits(codes[sym], bits[sym])
    }

    /** Read a byte from a circular window using mask. */
    private fun windowByteAt(
        window: ByteArray,
        mask: Int,
        p: Int,
    ): Int = window[p and mask].toInt() and 0xFF

    /** Compute hash for the three-byte sequence at index in the lookahead buffer. */
    private fun computeHashAt(
        la: ByteArray,
        idx: Int,
        hash3: (Int, Int, Int) -> Int,
    ): Int {
        val a = la[idx].toInt() and 0xFF
        val b = la[idx + 1].toInt() and 0xFF
        val c = la[idx + 2].toInt() and 0xFF
        return hash3(a, b, c)
    }

    /** Shared insert into hash chain and window head for a given absolute position. */
    private fun insertTripletAt(
        absPos: Int,
        la: ByteArray,
        laOff: Int,
        laLen: Int,
        pos: Int,
        head: IntArray,
        prev: IntArray,
        hash3: (Int, Int, Int) -> Int,
        mask: Int,
    ) {
        if (laLen - (absPos - pos + laOff) < 3) return
        val rel = absPos - pos + laOff
        val h = computeHashAt(la, rel, hash3)
        val widx = absPos and mask
        prev[widx] = head[h]
        head[h] = absPos
    }

    private data class LengthMap(
        val codeSymbol: Int,
        val extraBits: Int,
        val extraVal: Int,
    )

    private data class DistMap(
        val codeSymbol: Int,
        val extraBits: Int,
        val extraVal: Int,
    )

    // Small mutable state holder for LZ77 advancing to deduplicate loops
    private data class LzState(
        var pos: Int,
        var laOff: Int,
        var laLen: Int,
    )

    private fun advanceMatch(
        window: ByteArray,
        la: ByteArray,
        state: LzState,
        mask: Int,
        matchLen: Int,
        available: Int,
        insertAt: (Int) -> Unit,
    ) {
        var k = 0
        while (k < matchLen) {
            val widx = state.pos and mask
            window[widx] = la[state.laOff]
            if (available - k >= 3) insertAt(state.pos)
            state.pos++
            state.laOff++
            state.laLen--
            k++
        }
    }

    /** Compute length code mapping (code symbol and extra bits/value) for a given match length. */
    private fun mapLengthToSymbol(length: Int): LengthMap {
        val base = TREE_BASE_LENGTH
        val extra = TREE_EXTRA_LBITS
        var code = -1
        var extraBits = 0
        var extraVal = 0
        for (i in base.indices) {
            val b = base[i]
            val e = extra[i]
            val maxLen = b + (if (e > 0) (1 shl e) - 1 else 0)
            if (length in b..maxLen) {
                code = 257 + i
                extraBits = e
                extraVal = length - b
                break
            }
        }
        require(code != -1) { "Invalid length $length" }
        return LengthMap(code, extraBits, extraVal)
    }

    /** Compute distance code mapping (code symbol and extra bits/value) for a given match distance. */
    private fun mapDistToSymbol(dist: Int): DistMap {
        val base = TREE_BASE_DIST
        val extra = TREE_EXTRA_DBITS
        var code = -1
        var extraBits = 0
        var extraVal = 0
        for (i in base.indices) {
            val b = base[i]
            val e = extra[i]
            val maxD = b + (if (e > 0) (1 shl e) - 1 else 0)
            if (dist in b..maxD) {
                code = i
                extraBits = e
                extraVal = dist - b
                break
            }
        }
        require(code != -1) { "Invalid distance $dist" }
        return DistMap(code, extraBits, extraVal)
    }

    /** Map a match length to its Huffman code and write it, including extra bits. */
    private fun writeLengthEncoded(
        bw: StreamingBitWriter,
        litCodes: IntArray,
        litBits: IntArray,
        length: Int,
    ) {
        val m = mapLengthToSymbol(length)
        bw.writeBits(litCodes[m.codeSymbol], litBits[m.codeSymbol])
        if (m.extraBits > 0) bw.writeBits(m.extraVal, m.extraBits)
    }

    /** Map a distance to its Huffman code and write it, including extra bits. */
    private fun writeDistanceEncoded(
        bw: StreamingBitWriter,
        distCodes: IntArray,
        distBits: IntArray,
        dist: Int,
    ) {
        val m = mapDistToSymbol(dist)
        bw.writeBits(distCodes[m.codeSymbol], distBits[m.codeSymbol])
        if (m.extraBits > 0) bw.writeBits(m.extraVal, m.extraBits)
    }

    /** Encoders for fixed Huffman coding (RFC 1951, BTYPE=01). */
    private data class FixedEnc(
        val litCodes: IntArray,
        val litBits: IntArray,
        val distCodes: IntArray,
        val distBits: IntArray,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false

            other as FixedEnc

            if (!litCodes.contentEquals(other.litCodes)) return false
            if (!litBits.contentEquals(other.litBits)) return false
            if (!distCodes.contentEquals(other.distCodes)) return false
            if (!distBits.contentEquals(other.distBits)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = litCodes.contentHashCode()
            result = 31 * result + litBits.contentHashCode()
            result = 31 * result + distCodes.contentHashCode()
            result = 31 * result + distBits.contentHashCode()
            return result
        }
    }

    /** Build and return the fixed Huffman encoders (literal/length and distance). */
    private fun buildFixedEncoders(): FixedEnc {
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
        return FixedEnc(litCodes, litBits, distCodes, distBits)
    }

    /** Compress from source to sink with zlib wrapper using fixed Huffman (with simple RLE), fallback to stored when level<=0. */
    fun compressZlib(
        source: BufferedSource,
        sink: BufferedSink,
        level: Int = 6,
    ): Long =
        when {
            level <= 0 -> compressZlibStored(source, sink, level)
            level == 1 -> compressZlibFixed(source, sink, level)
            else -> compressZlibDynamic(source, sink, level)
        }

    /** Stored-block compressor (no compression). */
    private fun compressZlibStored(
        source: BufferedSource,
        sink: BufferedSink,
        level: Int = 0,
    ): Long {
        // Header
        writeZlibHeader(sink, level)
        val bw = StreamingBitWriter(sink)

        var totalIn = 0L
        var adler = 1L
        val buf = ByteArray(64 * 1024)
        var eof = false

        while (!eof) {
            val toRead = MAX_STORED
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
            writeStoredBlockHeader(bw, sink, filled, bfinal)

            // Write payload and update adler
            if (filled > 0) {
                sink.write(buf, 0, filled)
                totalIn += filled
                adler = Adler32Utils.adler32(adler, buf, 0, filled)
            }
        }

        // Trailer (big-endian)
        writeAdler32Trailer(sink, adler)
        sink.flush()
        return totalIn
    }

    /** Fixed-Huffman compressor with streaming LZ77 (greedy+lazy), limited matcher, arithmetic bit writing. */
    private fun compressZlibFixed(
        source: BufferedSource,
        sink: BufferedSink,
        level: Int = 6,
    ): Long {
        writeZlibHeader(sink, level)
        val bw = StreamingBitWriter(sink)

        // Build fixed Huffman encoders via helper to avoid duplication
        val fixed = buildFixedEncoders()
        val litCodes = fixed.litCodes
        val litBits = fixed.litBits
        val distCodes = fixed.distCodes
        val distBits = fixed.distBits

        fun writeLength(length: Int) {
            writeLengthEncoded(bw, litCodes, litBits, length)
        }

        fun writeDistance(dist: Int) {
            writeDistanceEncoded(bw, distCodes, distBits, dist)
        }

        // Emit single fixed block header (BFINAL=1, BTYPE=01)
        bw.writeBits(1, 1)
        bw.writeBits(1, 2)

        // Streaming LZ77 with small lookahead buffer and 32K sliding window
        val maxBufferCapacity = DEFAULT_WINDOW // 32KB buffer
        val size = DEFAULT_WINDOW // 32KB window
        val la = ByteArray(maxBufferCapacity)
        val window = ByteArray(size)
        val bufferSize = DEFAULT_WINDOW
        val winMask = size - 1
        val head = IntArray(bufferSize) { -1 }
        val prev = IntArray(size) { -1 }

        var adler = 1L
        var totalIn = 0L
        var pos = 0 // absolute position
        var laLen = 0
        var laOff = 0

        fun hash3(
            a: Int,
            b: Int,
            c: Int,
        ): Int {
            var h = a * 251 + b * 271 + c * 277
            h = h and (bufferSize - 1)
            return h
        }

        fun insertAt(absPos: Int) {
            insertTripletAt(absPos, la, laOff, laLen, pos, head, prev, { x, y, z -> hash3(x, y, z) }, size - 1)
        }

        fun emitLiteral(b: Int) {
            writeLiteral(bw, litCodes, litBits, b)
        }

        fun emitMatch(
            len: Int,
            dist: Int,
        ) {
            var remaining = len
            while (remaining > 0) {
                val l = minOf(remaining, 258)
                writeLength(l)
                writeDistance(dist)
                remaining -= l
            }
        }

        // Fill initial lookahead
        while (laLen < maxBufferCapacity && !source.exhausted()) {
            val n = source.read(la, laLen, minOf(maxBufferCapacity - laLen, 64 * 1024))
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

        val maxChainLength = 32
        while (laLen > 0) {
            val rel = laOff
            val available = laLen
            val b0 = la[rel].toInt() and 0xFF

            var bestLen = 0
            var bestDist = 0
            if (available >= 3) {
                val h = computeHashAt(la, rel) { x, y, z -> hash3(x, y, z) }
                var m = head[h]
                var chain = 0
                while (m != -1 && chain < maxChainLength) {
                    val dist = pos - m
                    if (dist in 1..size) {
                        // Compare
                        var matchLength = 0
                        while (matchLength < 258 && matchLength < available) {
                            val w = windowByteAt(window, winMask, m + matchLength)
                            val v = la[rel + matchLength].toInt() and 0xFF
                            if (w != v) break
                            matchLength++
                        }
                        if (matchLength >= 3 && matchLength > bestLen) {
                            bestLen = matchLength
                            bestDist = dist
                            if (matchLength >= 258) break
                        }
                    }
                    m = prev[m and (size - 1)]
                    chain++
                }
            }

            if (bestLen >= 3) {
                emitMatch(bestLen, bestDist)
                // Insert each position in match into hash/window (deduped)
                run {
                    val st = LzState(pos, laOff, laLen)
                    advanceMatch(window, la, st, size - 1, bestLen, available, ::insertAt)
                    pos = st.pos
                    laOff = st.laOff
                    laLen = st.laLen
                }
            } else {
                emitLiteral(b0)
                val widx = pos and (size - 1)
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
                val n = source.read(la, laLen, minOf(maxBufferCapacity - laLen, 64 * 1024))
                if (n > 0) {
                    adler = Adler32Utils.adler32(adler, la, laLen, n)
                    laLen += n
                    totalIn += n
                    // Insert new triplets starting from (pos + laLen - n - 2) to end
                    val start = pos + laLen - n
                    var insertPos = start
                    while (insertPos + 2 < pos + laLen) {
                        insertAt(insertPos)
                        insertPos++
                    }
                }
            }
        }

        // End of block
        writeLiteral(bw, litCodes, litBits, 256)
        bw.flush()

        // Zlib trailer
        writeAdler32Trailer(sink, adler)
        sink.flush()
        return totalIn
    }

    /** Dynamic-Huffman compressor using frequency-based code lengths (≤15). Chooses stored vs fixed vs dynamic per block. */
    private fun compressZlibDynamic(
        source: BufferedSource,
        sink: BufferedSink,
        level: Int = 6,
    ): Long {
        writeZlibHeader(sink, level)

        data class TokLit(
            val b: Int,
        )

        data class TokMatch(
            val len: Int,
            val dist: Int,
        )

        // Sliding LZ77 state (persists across blocks)
        val lookaheadBufferSize = 1 shl 15 // 32 KiB lookahead buffer
        val windowSize = 1 shl 15 // 32 KiB window
        val la = ByteArray(lookaheadBufferSize)
        val window = ByteArray(windowSize)
        val capacity = 1 shl 15
        val head = IntArray(capacity) { -1 }
        val prev = IntArray(windowSize) { -1 }
        val windowMask = windowSize - 1

        var adler = 1L
        var totalIn = 0L
        var pos = 0
        var laLen = 0
        var laOff = 0

        fun hash3(
            a: Int,
            b: Int,
            c: Int,
        ): Int {
            val h = a * 251 + b * 271 + c * 277
            return h and (capacity - 1)
        }

        fun insertAt(absPos: Int) {
            insertTripletAt(absPos, la, laOff, laLen, pos, head, prev, { x, y, z -> hash3(x, y, z) }, windowMask)
        }

        val bw = StreamingBitWriter(sink)
        val maxStoredSize = MAX_STORED // bound block size so stored is always an option
        val maxChain =
            when {
                level <= 2 -> 8
                level <= 4 -> 16
                level <= 6 -> 32
                else -> 64
            }
        val doLazy = false // temporarily disable lazy parsing for stability

        while (true) {
            // Ensure lookahead buffer is compacted at block start so new reads append contiguously
            if (laOff > 0) {
                if (laLen > 0) {
                    var t = 0
                    while (t < laLen) {
                        la[t] = la[laOff + t]
                        t++
                    }
                }
                laOff = 0
            }
            // Reset per-block structures
            val tokens = ArrayList<Any>(1 shl 14)
            val litFreq = IntArray(286)
            val distFreq = IntArray(30)

            var blockRead = 0
            val rawBuf = ByteArray(maxStoredSize)
            var rawLen = 0

            fun appendRaw(n: Int) {
                if (rawLen + n <= rawBuf.size) {
                    for (i in 0 until n) rawBuf[rawLen + i] = la[laLen + i]
                    rawLen += n
                }
            }

            // Initial fill for this block (limited by MAX_BLOCK)
            while (laLen < lookaheadBufferSize && blockRead < maxStoredSize && !source.exhausted()) {
                val toRead = minOf(lookaheadBufferSize - laLen, maxStoredSize - blockRead, 64 * 1024)
                val n = source.read(la, laLen, toRead)
                if (n <= 0) break
                // Save a copy for possible stored block
                appendRaw(n)
                adler = Adler32Utils.adler32(adler, la, laLen, n)
                laLen += n
                totalIn += n
                blockRead += n
            }

            // Seed or update hash for current lookahead
            var cur = pos
            while (cur + 2 < pos + laLen) {
                insertAt(cur)
                cur++
            }

            // Tokenize only the bytes read for this block
            while (laLen > 0) {
                val rel = laOff
                val available = minOf(laLen, lookaheadBufferSize - laOff)
                if (rel >= lookaheadBufferSize) break
                val b0 = la[rel].toInt() and 0xFF
                var bestLen = 0
                var bestDist = 0
                if (available >= 3 && rel + 2 < lookaheadBufferSize) {
                    val h = computeHashAt(la, rel) { x, y, z -> hash3(x, y, z) }
                    var m = head[h]
                    var chain = 0
                    while (m != -1 && chain < maxChain) {
                        val dist = pos - m
                        if (dist in 1..windowSize) {
                            var runLength = 0
                            while (runLength < 258 && runLength < available) {
                                val w = windowByteAt(window, windowMask, m + runLength)
                                if (rel + runLength >= lookaheadBufferSize) break
                                val v = la[rel + runLength].toInt() and 0xFF
                                if (w != v) break
                                runLength++
                            }
                            if (runLength >= 3 && runLength > bestLen) {
                                bestLen = runLength
                                bestDist = dist
                                if (runLength >= 258) break
                            }
                        }
                        m = prev[m and (windowSize - 1)]
                        chain++
                    }
                }

                // Optional lazy evaluation for better ratios
                if (doLazy && bestLen >= 3 && available > 3 && rel + 3 < lookaheadBufferSize) {
                    val rel2 = rel + 1
                    val a2 = la[rel2].toInt() and 0xFF
                    val b2 = la[rel2 + 1].toInt() and 0xFF
                    val c2 = la[rel2 + 2].toInt() and 0xFF
                    val h2 = hash3(a2, b2, c2)
                    var m2 = head[h2]
                    var chain2 = 0
                    var best2 = 0
                    while (m2 != -1 && chain2 < maxChain) {
                        val dist = (pos + 1) - m2
                        if (dist in 1..windowSize) {
                            var offset = 0
                            while (offset < 258 && (rel2 + offset) < (laOff + laLen)) {
                                val w = windowByteAt(window, windowMask, m2 + offset)
                                if (rel2 + offset >= lookaheadBufferSize) break
                                val v = la[rel2 + offset].toInt() and 0xFF
                                if (w != v) break
                                offset++
                            }
                            if (offset >= 3 && offset > best2) {
                                best2 = offset
                                if (offset >= 258) break
                            }
                        }
                        m2 = prev[m2 and (windowSize - 1)]
                        chain2++
                    }
                    if (best2 >= bestLen + 1) {
                        // Prefer literal; advance by 1 and continue
                        tokens.add(TokLit(b0))
                        litFreq[b0]++
                        val widx = pos and (windowSize - 1)
                        window[widx] = la[laOff]
                        pos++
                        laOff++
                        laLen--
                        continue
                    }
                }
                if (bestLen >= 3) {
                    tokens.add(TokMatch(bestLen, bestDist))
                    // account frequencies using shared mappers
                    run {
                        val lm = mapLengthToSymbol(bestLen)
                        litFreq[lm.codeSymbol]++
                    }
                    run {
                        val dm = mapDistToSymbol(bestDist)
                        distFreq[dm.codeSymbol]++
                    }
                    run {
                        val st = LzState(pos, laOff, laLen)
                        advanceMatch(window, la, st, windowMask, bestLen, available, ::insertAt)
                        pos = st.pos
                        laOff = st.laOff
                        laLen = st.laLen
                    }
                } else {
                    tokens.add(TokLit(b0))
                    litFreq[b0]++
                    val widx = pos and (windowSize - 1)
                    window[widx] = la[laOff]
                    if (available >= 3) insertAt(pos)
                    pos++
                    laOff++
                    laLen--
                }

                // Refill within this block if budget remains
                if (laLen < 1024 && blockRead < maxStoredSize && !source.exhausted()) {
                    if (laOff > 0 && laLen > 0) {
                        for (t in 0 until laLen) la[t] = la[laOff + t]
                        laOff = 0
                    } else if (laLen == 0) {
                        laOff = 0
                    }
                    val toRead = minOf(lookaheadBufferSize - laLen, maxStoredSize - blockRead, 64 * 1024)
                    if (toRead > 0) {
                        val n = source.read(la, laLen, toRead)
                        if (n > 0) {
                            // Save raw
                            appendRaw(n)
                            adler = Adler32Utils.adler32(adler, la, laLen, n)
                            laLen += n
                            totalIn += n
                            blockRead += n
                            var insertPos = pos + laLen - n
                            while (insertPos + 2 < pos + laLen) {
                                insertAt(insertPos)
                                insertPos++
                            }
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
                for (v in distFreq) {
                    if (v != 0) {
                        any = true
                        break
                    }
                }
                if (!any) distFreq[0] = 1
            }

            // Build dynamic code lengths (<=15)
            val dynLitLens = HuffmanBuilder.buildLengths(litFreq, 15, ensureSymbol = 256)
            val dynDistLens = HuffmanBuilder.buildLengths(distFreq, 15, ensureSymbol = 0)

            fun lastNonZero(a: IntArray): Int {
                var i = a.size - 1
                while (i >= 0 && a[i] == 0) i--
                return i
            }
            val lastLit = maxOf(lastNonZero(dynLitLens), 256)
            val lastDist = maxOf(lastNonZero(dynDistLens), 0)
            val lastLiteralIndex = (lastLit + 1) - 257
            val writeLength = (lastDist + 1) - 1

            data class ClSym(
                val sym: Int,
                val extraBits: Int = 0,
                val extraCount: Int = 0,
            )

            fun rleLengths(
                lengths: IntArray,
                count: Int,
            ): List<ClSym> {
                val out = ArrayList<ClSym>()
                var i = 0
                var prev = -1
                while (i < count) {
                    val l = lengths[i]
                    if (l == 0) {
                        var run = 1
                        var j = i + 1
                        while (j < count && lengths[j] == 0 && run < 138) {
                            run++
                            j++
                        }
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
                        while (j < count && lengths[j] == l && run < 6) {
                            run++
                            j++
                        }
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
            val clSeq =
                ArrayList<ClSym>(clLit.size + clDist.size).apply {
                    addAll(clLit)
                    addAll(clDist)
                }

            // BL lens (<=7) from CL frequencies
            val clFreq = IntArray(19)
            for (c in clSeq) clFreq[c.sym]++
            val blLens = HuffmanBuilder.buildLengths(clFreq, 7, ensureSymbol = 0)
            val bitLengthOrder = ai.solace.zlib.common.TREE_BL_ORDER
            var hclen = 19
            while (hclen > 4 && blLens[bitLengthOrder[hclen - 1]] == 0) hclen--
            val hclenOffset = hclen - 4

            // Build encoders and estimate costs
            val (dynLitCodes, dynLitBits) = CanonicalHuffman.buildEncoder(dynLitLens)
            val (dynDistCodes, dynDistBits) = CanonicalHuffman.buildEncoder(dynDistLens)
            val (blCodes, blBits) = CanonicalHuffman.buildEncoder(blLens)

            fun tokenCost(
                litBits: IntArray,
                distBits: IntArray,
            ): Long {
                var bits = 0L
                for (t in tokens) {
                    when (t) {
                        is TokLit -> bits += litBits[t.b]
                        is TokMatch -> {
                            val lm = mapLengthToSymbol(t.len)
                            bits += litBits[lm.codeSymbol]
                            bits += lm.extraBits
                            val dm = mapDistToSymbol(t.dist)
                            bits += distBits[dm.codeSymbol]
                            bits += dm.extraBits
                        }
                    }
                }
                // EOB
                bits += litBits[256]
                return bits
            }

            var headerDyn = 0L
            headerDyn += 1 + 2 + 5 + 5 + 4
            headerDyn += 3L * (hclenOffset + 4)
            for (c in clSeq) {
                headerDyn += blBits[c.sym]
                when (c.sym) {
                    16 -> headerDyn += 2
                    17 -> headerDyn += 3
                    18 -> headerDyn += 7
                }
            }
            val costDynamic = headerDyn + tokenCost(dynLitBits, dynDistBits)

            // Use shared fixed encoder builder to avoid duplication
            val fixedEnc = buildFixedEncoders()
            val fixedLitCodes = fixedEnc.litCodes
            val fixedLitBits = fixedEnc.litBits
            val fixedDistCodes = fixedEnc.distCodes
            val fixedDistBits = fixedEnc.distBits
            val costFixed = 1L + 2L + tokenCost(fixedLitBits, fixedDistBits)

            val padStored = (8 - (bw.bitMod8() % 8)) % 8
            val costStored = padStored + 1L + 2L + 16L + 16L + (rawLen.toLong() shl 3)

            // Choose encoding for this block
            val choice =
                when {
                    costStored <= costDynamic && costStored <= costFixed -> 0 // stored
                    costDynamic <= costFixed -> 2 // dynamic
                    else -> 1 // fixed
                }

            // Helper to emit tokens using provided writers (deduplicated loop)
            fun emitTokens(
                writeSymbol: (Int) -> Unit,
                writeLength: (Int) -> Unit,
                writeDistance: (Int) -> Unit,
            ) {
                for (t in tokens) {
                    when (t) {
                        is TokLit -> writeSymbol(t.b)
                        is TokMatch -> {
                            var remaining = t.len
                            val d = t.dist
                            while (remaining > 0) {
                                val l = minOf(remaining, 258)
                                writeLength(l)
                                writeDistance(d)
                                remaining -= l
                            }
                        }
                    }
                }
                writeSymbol(256)
            }

            // Emit tokens using specific code tables without redefining lambdas at each call site
            fun emitWithTables(
                litCodesT: IntArray,
                litBitsT: IntArray,
                distCodesT: IntArray,
                distBitsT: IntArray,
            ) {
                fun writeSymbol(sym: Int) {
                    bw.writeBits(litCodesT[sym], litBitsT[sym])
                }

                fun writeLength(length: Int) {
                    writeLengthEncoded(bw, litCodesT, litBitsT, length)
                }

                fun writeDistance(dist: Int) {
                    writeDistanceEncoded(bw, distCodesT, distBitsT, dist)
                }
                emitTokens(::writeSymbol, ::writeLength, ::writeDistance)
            }

            // Emit block
            if (choice == 0) {
                // Stored block
                writeStoredBlockHeader(bw, sink, rawLen, if (isLast) 1 else 0)
                if (rawLen > 0) sink.write(rawBuf, 0, rawLen)
                // aligned now; writer is byte-aligned due to alignToByte()
            } else if (choice == 2) {
                // Dynamic block
                bw.writeBits(if (isLast) 1 else 0, 1)
                bw.writeBits(2, 2)
                bw.writeBits(lastLiteralIndex, 5)
                bw.writeBits(writeLength, 5)
                bw.writeBits(hclenOffset, 4)
                for (i in 0 until hclen) bw.writeBits(blLens[bitLengthOrder[i]], 3)

                fun writeBL(sym: Int) {
                    if (blBits[sym] == 0) error("BL code missing for sym=$sym")
                    bw.writeBits(blCodes[sym], blBits[sym])
                }
                for (c in clSeq) {
                    writeBL(c.sym)
                    when (c.sym) {
                        16 -> bw.writeBits(c.extraCount, 2)
                        17 -> bw.writeBits(c.extraCount, 3)
                        18 -> bw.writeBits(c.extraCount, 7)
                    }
                }
                emitWithTables(dynLitCodes, dynLitBits, dynDistCodes, dynDistBits)
            } else {
                // Fixed block
                bw.writeBits(if (isLast) 1 else 0, 1)
                bw.writeBits(1, 2)
                emitWithTables(fixedLitCodes, fixedLitBits, fixedDistCodes, fixedDistBits)
            }

            if (isLast) break
        }

        bw.flush()

        // Zlib trailer
        writeAdler32Trailer(sink, adler)
        sink.flush()
        return totalIn
    }
}
