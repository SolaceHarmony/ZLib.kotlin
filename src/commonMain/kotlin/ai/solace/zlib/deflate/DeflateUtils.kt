package ai.solace.zlib.deflate

import ai.solace.zlib.common.*


// This file will house utility functions previously in Deflate.kt.
// Functions will be adjusted to be top-level and accept a Deflate instance if they were member functions.

internal fun smaller(tree: ShortArray, n: Int, m: Int, depth: ByteArray): Boolean {
    return tree[n * 2].toInt() < tree[m * 2].toInt() ||
            (tree[n * 2].toInt() == tree[m * 2].toInt() && depth[n] <= depth[m])
}

internal fun put_byte(d: Deflate, p: ByteArray, start: Int, len: Int) {
    p.copyInto(d.pending_buf, d.pending, start, start + len)
    d.pending += len
}

internal fun put_byte(d: Deflate, c: Byte) {
    d.pending_buf[d.pending++] = c
}

internal fun put_short(d: Deflate, w: Int) {
    put_byte(d, w.toByte())
    put_byte(d, (w ushr 8).toByte())
}

internal fun putShortMSB(d: Deflate, b: Int) {
    put_byte(d, (b shr 8).toByte())
    put_byte(d, b.toByte())
}

internal fun send_bits(d: Deflate, value_Renamed: Int, length: Int) {
    val len = length
    if (d.bi_valid > BUF_SIZE - len) {
        val value = value_Renamed
        d.bi_buf = ((d.bi_buf.toInt() and 0xFFFF) or (value shl d.bi_valid)).toShort()
        put_short(d, d.bi_buf.toInt())
        d.bi_buf = (value ushr (BUF_SIZE - d.bi_valid)).toShort()
        d.bi_valid += len - BUF_SIZE
    } else {
        d.bi_buf = ((d.bi_buf.toInt() and 0xFFFF) or (value_Renamed shl d.bi_valid)).toShort()
        d.bi_valid += len
    }
}

internal fun send_code(d: Deflate, c: Int, tree: ShortArray) {
    send_bits(d, (tree[c * 2].toInt() and 0xffff), (tree[c * 2 + 1].toInt() and 0xffff))
}

internal fun _tr_align(d: Deflate) {
    send_bits(d, STATIC_TREES shl 1, 3)
    send_code(d, END_BLOCK, StaticTree.static_ltree)
    bi_flush(d)
    if (1 + d.last_eob_len + 10 - d.bi_valid < 9) {
        send_bits(d, STATIC_TREES shl 1, 3)
        send_code(d, END_BLOCK, StaticTree.static_ltree)
        bi_flush(d)
    }
    d.last_eob_len = 7
}

internal fun compress_block(d: Deflate, ltree: ShortArray, dtree: ShortArray) {
    var dist: Int // distance of matched string
    var lc: Int // match length or unmatched char (if dist == 0)
    var lx = 0 // running index in l_buf
    var code: Int // the code to send
    var extra: Int // number of extra bits to send

    if (d.last_lit != 0) {
        do {
            dist = ((d.pending_buf[d.d_buf + lx * 2].toInt() shl 8 and 0xff00) or (d.pending_buf[d.d_buf + lx * 2 + 1].toInt() and 0xff))
            lc = (d.pending_buf[d.l_buf + lx]).toInt() and 0xff
            lx++

            if (dist == 0) {
                send_code(d, lc, ltree) // send a literal byte
            } else {
                code = TREE_LENGTH_CODE[lc].toInt()
                send_code(d, code + LITERALS + 1, ltree) // send the length code
                extra = TREE_EXTRA_LBITS[code]
                if (extra != 0) {
                    lc -= TREE_BASE_LENGTH[code]
                    send_bits(d, lc, extra) // send the extra length bits
                }
                dist--
                code = d_code(dist) // d_code from TreeUtils
                send_code(d, code, dtree) // send the distance code
                extra = TREE_EXTRA_DBITS[code]
                if (extra != 0) {
                    dist -= TREE_BASE_DIST[code]
                    send_bits(d, dist, extra) // send the extra distance bits
                }
            }
        } while (lx < d.last_lit)
    }
    send_code(d, END_BLOCK, ltree)
    d.last_eob_len = ltree[END_BLOCK * 2 + 1].toInt()
}

internal fun set_data_type(d: Deflate) {
    var n = 0
    var ascii_freq = 0
    var bin_freq = 0
    while (n < 7) {
        bin_freq += d.dyn_ltree[n * 2]
        n++
    }
    while (n < 128) {
        ascii_freq += d.dyn_ltree[n * 2]
        n++
    }
    while (n < LITERALS) {
        bin_freq += d.dyn_ltree[n * 2]
        n++
    }
    d.data_type = if (bin_freq > ascii_freq ushr 2) Z_BINARY.toByte() else Z_ASCII.toByte()
}

internal fun bi_flush(d: Deflate) {
    if (d.bi_valid == 16) {
        put_short(d, d.bi_buf.toInt())
        d.bi_buf = 0
        d.bi_valid = 0
    } else if (d.bi_valid >= 8) {
        put_byte(d, d.bi_buf.toByte())
        d.bi_buf = (d.bi_buf.toInt() ushr 8).toShort()
        d.bi_valid -= 8
    }
}

internal fun bi_windup(d: Deflate) {
    if (d.bi_valid > 8) {
        put_short(d, d.bi_buf.toInt())
    } else if (d.bi_valid > 0) {
        put_byte(d, d.bi_buf.toByte())
    }
    d.bi_buf = 0
    d.bi_valid = 0
}

internal fun copy_block(d: Deflate, buf: Int, len: Int, header: Boolean) {
    bi_windup(d)
    d.last_eob_len = 8
    if (header) {
        put_short(d, len.toShort().toInt())
        put_short(d, len.inv().toShort().toInt())
    }
    put_byte(d, d.window, buf, len)
}

internal fun _tr_stored_block(d: Deflate, buf: Int, stored_len: Int, eof: Boolean) {
    send_bits(d, (STORED_BLOCK shl 1) + if (eof) 1 else 0, 3)
    copy_block(d, buf, stored_len, true)
}
