@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)

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

    val bufSize = 16  // Bits in a Short * 2
    val oldBiValid = d.biValid

    // If there's not enough room in the current buffer for all the bits
    if (oldBiValid > bufSize - length) {
        // First, fill the current buffer with as many bits as will fit
        val bitsInCurrentBuf = bufSize - oldBiValid
        val bitsForNextBuf = length - bitsInCurrentBuf

        // Fill the current buffer with the low-order bits that fit
        // Fix: Better handling of potential overflow when shifting
        val lowBits = if (bitsInCurrentBuf >= 30) {
            value and ((1 shl 30) - 1)  // Limit to 30 bits to prevent overflow
        } else {
            value and ((1 shl bitsInCurrentBuf) - 1)
        }

        val biBufVal = d.biBuf.toInt() and 0xffff
        val combinedVal = biBufVal or (lowBits shl oldBiValid)

        // Flush the full buffer
        putShort(d, combinedVal)

        // Store remaining high-order bits in the new buffer
        // Fix: Safer handling of bit shifting for the next buffer
        d.biBuf = if (bitsInCurrentBuf == 0) {
            value.toShort()
        } else {
            (value ushr bitsInCurrentBuf).toShort()
        }
        d.biValid = bitsForNextBuf
    } else {
        // Enough room in the current buffer, just add the bits
        // Fix: Better handling of potential overflow when shifting
        val mask = if (length >= 30) {
            0x3FFFFFFF  // Use explicit 30-bit mask (2^30-1) to prevent overflow
        } else {
            (1 shl length) - 1
        }

        val biBufInt = (d.biBuf.toInt() and 0xffff) or ((value and mask) shl oldBiValid)
        d.biBuf = biBufInt.toShort()
        d.biValid = oldBiValid + length
    }
}

internal fun sendCode(d: Deflate, c: Int, tree: ShortArray) {
    val code = tree[c * 2].toInt() and 0xffff
    val bits = tree[c * 2 + 1].toInt() and 0xffff
    println("[DEBUG_SEND] sendCode: symbol=$c (char='${if (c in 32..126) c.toChar() else "?"}'), code=$code, bits=$bits")
    sendBits(d, code, bits)
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

/**
 * Writes a stored block length and its one's complement in the format expected by the
 * Pascal ZLib implementation for validation. This ensures all stored blocks in the
 * compressed stream have consistent and correct length formatting.
 *
 * @param d The Deflate object to write bytes to
 * @param len The length to write (will be limited to 16 bits)
 */
internal fun writeStoredBlockLength(d: Deflate, len: Int) {
    val lenLimited = len and 0xFFFF
    val complement = lenLimited.inv() and 0xFFFF

    // Write len and its complement directly in little-endian order
    // 1. Low byte of len
    // 2. High byte of len
    // 3. Low byte of complement
    // 4. High byte of complement
    putByte(d, lenLimited.toByte())
    putByte(d, (lenLimited ushr 8).toByte())
    putByte(d, complement.toByte())
    putByte(d, (complement ushr 8).toByte())
}

internal fun copyBlock(d: Deflate, buf: Int, len: Int, header: Boolean) {
    biWindup(d)
    d.lastEobLen = 8
    if (header) {
        // Use the common function for writing a stored block length header
        writeStoredBlockLength(d, len)
    }
    putByte(d, d.window, buf, len)
}

internal fun trStoredBlock(d: Deflate, buf: Int, storedLen: Int, eof: Boolean) {
    sendBits(d, (STORED_BLOCK shl 1) + if (eof) 1 else 0, 3)
    copyBlock(d, buf, storedLen, true)
}
