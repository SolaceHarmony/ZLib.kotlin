package ai.solace.zlib.deflate

import ai.solace.zlib.common.*


// This file will house utility functions previously in Deflate.kt.
// Functions will be adjusted to be top-level and accept a Deflate instance if they were member functions.

internal fun smaller(tree: ShortArray, n: Int, m: Int, depth: ByteArray): Boolean {
    return tree[n * 2].toInt() < tree[m * 2].toInt() ||
            (tree[n * 2].toInt() == tree[m * 2].toInt() && depth[n] <= depth[m])
}

internal fun putByte(d: Deflate, p: ByteArray, start: Int, len: Int) {
    p.copyInto(d.pendingBuf, d.pending, start, start + len)
    d.pending += len
}

internal fun putByte(d: Deflate, c: Byte) {
    d.pendingBuf[d.pending++] = c
}

internal fun putShort(d: Deflate, w: Int) {
    putByte(d, w.toByte())
    putByte(d, (w ushr 8).toByte())
}

internal fun putShortMSB(d: Deflate, b: Int) {
    putByte(d, (b shr 8).toByte())
    putByte(d, b.toByte())
}

internal fun sendBits(d: Deflate, value: Int, length: Int) {
    if (length == 0) return
    val len = length
    val oldBiValid = d.biValid
    if (oldBiValid > 16 - len) {  // 16 is BUF_SIZE (bits in a Short * 2)
        var biBufVal = d.biBuf.toInt() and 0xffff
        val valShifted = value shl oldBiValid
        val valShiftedMasked = valShifted and 0xffff
        val combinedValForFlush = biBufVal or valShiftedMasked
        putShort(d, combinedValForFlush)

        // Place remaining bits in the buffer
        val bitsAlreadyWritten = 16 - oldBiValid  // 16 is BUF_SIZE
        d.biBuf = (value ushr bitsAlreadyWritten).toShort()
        d.biValid = oldBiValid + len - 16  // 16 is BUF_SIZE
    } else {
        val biBufInt = (d.biBuf.toInt() and 0xffff) or (value shl oldBiValid)
        d.biBuf = biBufInt.toShort()
        d.biValid = oldBiValid + len
    }
}

internal fun sendCode(d: Deflate, c: Int, tree: ShortArray) {
    sendBits(d, (tree[c * 2].toInt() and 0xffff), (tree[c * 2 + 1].toInt() and 0xffff))
}

internal fun trAlign(d: Deflate) {
    sendBits(d, STATIC_TREES shl 1, 3)
    sendCode(d, END_BLOCK, StaticTree.static_ltree)
    biFlush(d)
    if (1 + d.lastEobLen + 10 - d.biValid < 9) {
        sendBits(d, STATIC_TREES shl 1, 3)
        sendCode(d, END_BLOCK, StaticTree.static_ltree)
        biFlush(d)
    }
    d.lastEobLen = 7
}

internal fun compressBlock(d: Deflate, ltree: ShortArray, dtree: ShortArray) {
    var dist: Int // distance of matched string
    var lc: Int // match length or unmatched char (if dist == 0)
    var lx = 0 // running index in l_buf
    var code: Int // the code to send
    var extra: Int // number of extra bits to send

    if (d.lastLit != 0) {
        do {
            dist = ((d.pendingBuf[d.dBuf + lx * 2].toInt() shl 8 and 0xff00) or (d.pendingBuf[d.dBuf + lx * 2 + 1].toInt() and 0xff))
            lc = (d.pendingBuf[d.lBuf + lx]).toInt() and 0xff
            lx++

            if (dist == 0) {
                sendCode(d, lc, ltree) // send a literal byte
            } else {
                code = TREE_LENGTH_CODE[lc].toInt()
                sendCode(d, code + LITERALS + 1, ltree) // send the length code
                extra = TREE_EXTRA_LBITS[code]
                if (extra != 0) {
                    lc -= TREE_BASE_LENGTH[code]
                    sendBits(d, lc, extra) // send the extra length bits
                }
                dist--
                code = dCode(dist) // d_code from TreeUtils
                sendCode(d, code, dtree) // send the distance code
                extra = TREE_EXTRA_DBITS[code]
                if (extra != 0) {
                    dist -= TREE_BASE_DIST[code]
                    sendBits(d, dist, extra) // send the extra distance bits
                }
            }
        } while (lx < d.lastLit)
    }
    sendCode(d, END_BLOCK, ltree)
    d.lastEobLen = ltree[END_BLOCK * 2 + 1].toInt()
}

internal fun setDataType(d: Deflate) {
    var n = 0
    var asciiFreq = 0
    var binFreq = 0
    while (n < 7) {
        binFreq += d.dynLtree[n * 2]
        n++
    }
    while (n < 128) {
        asciiFreq += d.dynLtree[n * 2]
        n++
    }
    while (n < LITERALS) {
        binFreq += d.dynLtree[n * 2]
        n++
    }
    d.dataType = if (binFreq > asciiFreq ushr 2) Z_BINARY.toByte() else Z_ASCII.toByte()
}

internal fun biFlush(d: Deflate) {
    if (d.biValid == 16) {
        putShort(d, d.biBuf.toInt())
        d.biBuf = 0
        d.biValid = 0
    } else if (d.biValid >= 8) {
        putByte(d, d.biBuf.toByte())
        d.biBuf = (d.biBuf.toInt() ushr 8).toShort()
        d.biValid -= 8
    }
}

internal fun biWindup(d: Deflate) {
    if (d.biValid > 8) {
        putShort(d, d.biBuf.toInt())
    } else if (d.biValid > 0) {
        putByte(d, d.biBuf.toByte())
    }
    d.biBuf = 0
    d.biValid = 0
}

internal fun copyBlock(d: Deflate, buf: Int, len: Int, header: Boolean) {
    biWindup(d)
    d.lastEobLen = 8
    if (header) {
        // Matching the original C# implementation:
        // put_short((short) len);
        // put_short((short) ~ len);
        putShort(d, len)
        putShort(d, len.inv() and 0xFFFF)
    }
    putByte(d, d.window, buf, len)
}

internal fun trStoredBlock(d: Deflate, buf: Int, storedLen: Int, eof: Boolean) {
    sendBits(d, (STORED_BLOCK shl 1) + if (eof) 1 else 0, 3)
    copyBlock(d, buf, storedLen, true)
}
