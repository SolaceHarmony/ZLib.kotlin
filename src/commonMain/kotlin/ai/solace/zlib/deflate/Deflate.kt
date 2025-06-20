package ai.solace.zlib.deflate // Ensure correct package

// Fix the imports to use the new package structure
// For functions moved from Deflate.kt itself to DeflateUtils.kt
import ai.solace.zlib.common.*
// For functions moved from Deflate.kt itself to DeflateUtils.kt
import ai.solace.zlib.deflate.* // Wildcard import for all DeflateUtils

class Deflate {

    // Config class moved to Config.kt

    private lateinit var config_table: Array<Config>

    internal lateinit var strm: ZStream
    internal var status: Int = 0
    internal lateinit var pending_buf: ByteArray
    internal var pending_buf_size: Int = 0
    internal var pending_out: Int = 0
    internal var pending: Int = 0
    internal var noheader: Int = 0
    internal var data_type: Byte = 0
    internal var method: Byte = 0
    internal var last_flush: Int = 0

    internal var w_size: Int = 0
    internal var w_bits: Int = 0
    internal var w_mask: Int = 0

    internal lateinit var window: ByteArray
    internal var window_size: Int = 0
    internal lateinit var prev: ShortArray
    internal lateinit var head: ShortArray

    internal var ins_h: Int = 0
    internal var hash_size: Int = 0
    internal var hash_bits: Int = 0
    internal var hash_mask: Int = 0
    internal var hash_shift: Int = 0
    internal var block_start: Int = 0
    internal var match_length: Int = 0
    internal var prev_match: Int = 0
    internal var match_available: Int = 0
    internal var strstart: Int = 0
    internal var match_start: Int = 0
    internal var lookahead: Int = 0
    internal var prev_length: Int = 0
    internal var max_chain_length: Int = 0
    internal var max_lazy_match: Int = 0
    internal var level: Int = 0
    internal var strategy: Int = 0
    internal var good_match: Int = 0
    internal var nice_match: Int = 0

    internal var dyn_ltree: ShortArray
    internal var dyn_dtree: ShortArray
    internal var bl_tree: ShortArray

    internal var l_desc = Tree()
    internal var d_desc = Tree()
    internal var bl_desc = Tree()

    internal var bl_count = ShortArray(MAX_BITS + 1)
    internal var heap = IntArray(2 * L_CODES + 1)
    internal var heap_len: Int = 0
    internal var heap_max: Int = 0
    internal var depth = ByteArray(2 * L_CODES + 1)
    internal var l_buf: Int = 0
    internal var lit_bufsize: Int = 0
    internal var last_lit: Int = 0
    internal var d_buf: Int = 0
    internal var opt_len: Int = 0
    internal var static_len: Int = 0
    internal var matches: Int = 0
    internal var last_eob_len: Int = 0
    internal var bi_buf: Short = 0
    internal var bi_valid: Int = 0

    init {
        dyn_ltree = ShortArray(HEAP_SIZE * 2)
        dyn_dtree = ShortArray((2 * D_CODES + 1) * 2)
        bl_tree = ShortArray((2 * BL_CODES + 1) * 2)
    }

    internal fun lm_init() {
        window_size = 2 * w_size
        head[hash_size - 1] = 0
        for (i in 0 until hash_size - 1) {
            head[i] = 0
        }
        max_lazy_match = config_table[level].max_lazy
        good_match = config_table[level].good_length
        nice_match = config_table[level].nice_length
        max_chain_length = config_table[level].max_chain

        strstart = 0
        block_start = 0
        lookahead = 0
        match_length = MIN_MATCH - 1
        prev_length = match_length
        match_available = 0
        ins_h = 0
    }

    internal fun tr_init() {
        l_desc.dyn_tree = dyn_ltree
        l_desc.stat_desc = StaticTree.static_l_desc

        d_desc.dyn_tree = dyn_dtree
        d_desc.stat_desc = StaticTree.static_d_desc

        bl_desc.dyn_tree = bl_tree
        bl_desc.stat_desc = StaticTree.static_bl_desc

        bi_buf = 0
        bi_valid = 0
        last_eob_len = 8
        init_block()
    }

    internal fun init_block() {
        for (i in 0 until L_CODES) dyn_ltree[i * 2] = 0
        for (i in 0 until D_CODES) dyn_dtree[i * 2] = 0
        for (i in 0 until BL_CODES) bl_tree[i * 2] = 0

        dyn_ltree[END_BLOCK * 2] = 1
        opt_len = 0
        static_len = 0
        last_lit = 0
        matches = 0
    }

    internal fun pqdownheap(tree: ShortArray, k_in: Int) {
        var k = k_in
        val v = heap[k]
        var j = k shl 1
        while (j <= heap_len) {
            if (j < heap_len && smaller(tree, heap[j + 1], heap[j], depth)) {
                j++
            }
            if (smaller(tree, v, heap[j], depth)) break
            heap[k] = heap[j]
            k = j
            j = j shl 1
        }
        heap[k] = v
    }

    internal fun scan_tree(tree: ShortArray, max_code: Int) {
        var n: Int
        var prevlen = -1
        var curlen: Int
        var nextlen = tree[0 * 2 + 1].toInt()
        var count = 0
        var max_count = 7
        var min_count = 4

        if (nextlen == 0) {
            max_count = 138
            min_count = 3
        }
        tree[(max_code + 1) * 2 + 1] = 0xffff.toShort()

        n = 0
        while(n <= max_code) {
            curlen = nextlen
            nextlen = tree[(n + 1) * 2 + 1].toInt()
            if (++count < max_count && curlen == nextlen) {
                // continue
            } else if (count < min_count) {
                bl_tree[curlen * 2] = (bl_tree[curlen * 2] + count).toShort()
            } else if (curlen != 0) {
                if (curlen != prevlen) bl_tree[curlen * 2]++
                bl_tree[REP_3_6 * 2]++
            } else if (count <= 10) {
                bl_tree[REPZ_3_10 * 2]++
            } else {
                bl_tree[REPZ_11_138 * 2]++
            }
            count = 0
            prevlen = curlen
            if (nextlen == 0) {
                max_count = 138
                min_count = 3
            } else if (curlen == nextlen) {
                max_count = 6
                min_count = 3
            } else {
                max_count = 7
                min_count = 4
            }
            n++
        }
    }

    internal fun build_bl_tree(): Int {
        var max_blindex: Int
        scan_tree(dyn_ltree, l_desc.max_code)
        scan_tree(dyn_dtree, d_desc.max_code)
        bl_desc.build_tree(this)
        max_blindex = BL_CODES - 1
        while(max_blindex >= 3) {
            if (bl_tree[TREE_BL_ORDER[max_blindex] * 2 + 1].toInt() != 0) break
            max_blindex--
        }
        opt_len += 3 * (max_blindex + 1) + 5 + 5 + 4
        return max_blindex
    }

    internal fun send_all_trees(lcodes: Int, dcodes: Int, blcodes: Int) {
        var rank: Int
        send_bits(this, lcodes - 257, 5)
        send_bits(this, dcodes - 1, 5)
        send_bits(this, blcodes - 4, 4)
        rank = 0
        while(rank < blcodes) {
            send_bits(this, bl_tree[TREE_BL_ORDER[rank] * 2 + 1].toInt(), 3)
            rank++
        }
        send_tree(dyn_ltree, lcodes - 1)
        send_tree(dyn_dtree, dcodes - 1)
    }

    internal fun send_tree(tree: ShortArray, max_code: Int) {
        var n: Int
        var prevlen = -1
        var curlen: Int
        var nextlen = tree[0 * 2 + 1].toInt()
        var count = 0
        var max_count = 7
        var min_count = 4

        if (nextlen == 0) {
            max_count = 138
            min_count = 3
        }
        n = 0
        while(n <= max_code) {
            curlen = nextlen
            nextlen = tree[(n + 1) * 2 + 1].toInt()
            if (++count < max_count && curlen == nextlen) {
                // continue
            } else if (count < min_count) {
                do {
                    send_code(this, curlen, bl_tree)
                } while (--count != 0)
            } else if (curlen != 0) {
                if (curlen != prevlen) {
                    send_code(this, curlen, bl_tree)
                    count--
                }
                send_code(this, REP_3_6, bl_tree)
                send_bits(this, count - 3, 2)
            } else if (count <= 10) {
                send_code(this, REPZ_3_10, bl_tree)
                send_bits(this, count - 3, 3)
            } else {
                send_code(this, REPZ_11_138, bl_tree)
                send_bits(this, count - 11, 7)
            }
            count = 0
            prevlen = curlen
            if (nextlen == 0) {
                max_count = 138
                min_count = 3
            } else if (curlen == nextlen) {
                max_count = 6
                min_count = 3
            } else {
                max_count = 7
                min_count = 4
            }
            n++
        }
    }

    internal fun _tr_tally(dist: Int, lc: Int): Boolean {
        pending_buf[d_buf + last_lit * 2] = (dist ushr 8).toByte()
        pending_buf[d_buf + last_lit * 2 + 1] = dist.toByte()
        pending_buf[l_buf + last_lit] = lc.toByte()
        last_lit++

        if (dist == 0) {
            dyn_ltree[lc * 2]++
        } else {
            matches++
            val dist_val = dist - 1
            dyn_ltree[(TREE_LENGTH_CODE[lc].toInt() + LITERALS + 1) * 2]++
            dyn_dtree[d_code(dist_val) * 2]++
        }

        if ((last_lit and 0x1fff) == 0 && level > 2) {
            var out_length = last_lit * 8
            val in_length = strstart - block_start
            for (dcode_val in 0 until D_CODES) {
                out_length = (out_length + dyn_dtree[dcode_val * 2] * (5L + TREE_EXTRA_DBITS[dcode_val])).toInt()
            }
            out_length = out_length ushr 3
            if (matches < last_lit / 2 && out_length < in_length / 2) return true
        }
        return last_lit == lit_bufsize - 1
    }

    internal fun flush_block_only(eof: Boolean) {
        _tr_flush_block(if (block_start >= 0) block_start else -1, strstart - block_start, eof)
        block_start = strstart
        strm.flush_pending()
    }

    internal fun deflate_stored(flush: Int): Int {
        var max_block_size = 0xffff
        var max_start: Int

        if (max_block_size > pending_buf_size - 5) {
            max_block_size = pending_buf_size - 5
        }

        while (true) {
            if (lookahead <= 1) {
                fill_window()
                if (lookahead == 0 && flush == Z_NO_FLUSH) return NEED_MORE
                if (lookahead == 0) break
            }
            strstart += lookahead
            lookahead = 0
            max_start = block_start + max_block_size
            if (strstart == 0 || strstart >= max_start) {
                lookahead = (strstart - max_start)
                strstart = max_start
                flush_block_only(false)
                if (strm.avail_out == 0) return NEED_MORE
            }
            if (strstart - block_start >= w_size - MIN_LOOKAHEAD) {
                flush_block_only(false)
                if (strm.avail_out == 0) return NEED_MORE
            }
        }
        flush_block_only(flush == Z_FINISH)
        if (strm.avail_out == 0) return if (flush == Z_FINISH) FINISH_STARTED else NEED_MORE
        return if (flush == Z_FINISH) FINISH_DONE else BLOCK_DONE
    }

    internal fun _tr_flush_block(buf: Int, stored_len: Int, eof: Boolean) {
        var opt_lenb: Int
        var static_lenb: Int
        var max_blindex = 0

        if (level > 0) {
            if (data_type == Z_UNKNOWN.toByte()) set_data_type(this)
            l_desc.build_tree(this)
            d_desc.build_tree(this)
            max_blindex = build_bl_tree()
            opt_lenb = (opt_len + 3 + 7) ushr 3
            static_lenb = (static_len + 3 + 7) ushr 3
            if (static_lenb <= opt_lenb) opt_lenb = static_lenb
        } else {
            opt_lenb = stored_len + 5
            static_lenb = opt_lenb
        }

        if (stored_len + 4 <= opt_lenb && buf != -1) {
            _tr_stored_block(this, buf, stored_len, eof)
        } else if (static_lenb == opt_lenb) {
            send_bits(this, (STATIC_TREES shl 1) + if (eof) 1 else 0, 3)
            compress_block(this, StaticTree.static_ltree, StaticTree.static_dtree)
        } else {
            send_bits(this, (DYN_TREES shl 1) + if (eof) 1 else 0, 3)
            send_all_trees(l_desc.max_code + 1, d_desc.max_code + 1, max_blindex + 1)
            compress_block(this, dyn_ltree, dyn_dtree)
        }
        init_block()
        if (eof) {
            bi_windup(this)
        }
    }

    internal fun fill_window() {
        var n: Int
        var m: Int
        var p: Int
        var more: Int
        do {
            more = window_size - lookahead - strstart
            if (more == 0 && strstart == 0 && lookahead == 0) {
                more = w_size
            } else if (more == -1) {
                more--
            } else if (strstart >= w_size + w_size - MIN_LOOKAHEAD) {
                window.copyInto(window, 0, w_size, w_size)
                match_start -= w_size
                strstart -= w_size
                block_start -= w_size
                n = hash_size
                p = n
                do {
                    m = (head[--p].toInt() and 0xffff)
                    head[p] = if (m >= w_size) (m - w_size).toShort() else 0.toShort()
                } while (--n != 0)
                n = w_size
                p = n
                do {
                    m = (prev[--p].toInt() and 0xffff)
                    prev[p] = if (m >= w_size) (m - w_size).toShort() else 0.toShort()
                } while (--n != 0)
                more += w_size
            }
            if (strm.avail_in == 0) return
            n = strm.read_buf(window, strstart + lookahead, more)
            lookahead += n
            if (lookahead >= MIN_MATCH) {
                ins_h = window[strstart].toInt() and 0xff
                ins_h = (((ins_h shl hash_shift) xor (window[strstart + 1].toInt() and 0xff)) and hash_mask)
            }
        } while (lookahead < MIN_LOOKAHEAD && strm.avail_in != 0)
    }

    internal fun deflate_fast(flush: Int): Int {
        var hash_head = 0
        var bflush: Boolean
        while (true) {
            if (lookahead < MIN_LOOKAHEAD) {
                fill_window()
                if (lookahead < MIN_LOOKAHEAD && flush == Z_NO_FLUSH) return NEED_MORE
                if (lookahead == 0) break
            }
            if (lookahead >= MIN_MATCH) {
                ins_h = (((ins_h shl hash_shift) xor (window[(strstart) + (MIN_MATCH - 1)].toInt() and 0xff)) and hash_mask)
                hash_head = (head[ins_h].toInt() and 0xffff)
                prev[strstart and w_mask] = head[ins_h]
                head[ins_h] = strstart.toShort()
            }
            if (hash_head != 0 && ((strstart - hash_head) and 0xffff) <= w_size - MIN_LOOKAHEAD) {
                if (strategy != Z_HUFFMAN_ONLY) {
                    match_length = longest_match(hash_head)
                }
            }
            if (match_length >= MIN_MATCH) {
                bflush = _tr_tally(strstart - match_start, match_length - MIN_MATCH)
                lookahead -= match_length
                if (match_length <= max_lazy_match && lookahead >= MIN_MATCH) {
                    match_length--
                    do {
                        strstart++
                        ins_h = (((ins_h shl hash_shift) xor (window[(strstart) + (MIN_MATCH - 1)].toInt() and 0xff)) and hash_mask)
                        hash_head = (head[ins_h].toInt() and 0xffff)
                        prev[strstart and w_mask] = head[ins_h]
                        head[ins_h] = strstart.toShort()
                    } while (--match_length != 0)
                    strstart++
                } else {
                    strstart += match_length
                    match_length = 0
                    ins_h = window[strstart].toInt() and 0xff
                    ins_h = (((ins_h shl hash_shift) xor (window[strstart + 1].toInt() and 0xff)) and hash_mask)
                }
            } else {
                bflush = _tr_tally(0, window[strstart].toInt() and 0xff)
                lookahead--
                strstart++
            }
            if (bflush) {
                flush_block_only(false)
                if (strm.avail_out == 0) return NEED_MORE
            }
        }
        flush_block_only(flush == Z_FINISH)
        if (strm.avail_out == 0) {
            return if (flush == Z_FINISH) FINISH_STARTED else NEED_MORE
        }
        return if (flush == Z_FINISH) FINISH_DONE else BLOCK_DONE
    }

    internal fun deflate_slow(flush: Int): Int {
        var hash_head = 0
        var bflush: Boolean
        while (true) {
            if (lookahead < MIN_LOOKAHEAD) {
                fill_window()
                if (lookahead < MIN_LOOKAHEAD && flush == Z_NO_FLUSH) return NEED_MORE
                if (lookahead == 0) break
            }
            if (lookahead >= MIN_MATCH) {
                ins_h = (((ins_h shl hash_shift) xor (window[(strstart) + (MIN_MATCH - 1)].toInt() and 0xff)) and hash_mask)
                hash_head = (head[ins_h].toInt() and 0xffff)
                prev[strstart and w_mask] = head[ins_h]
                head[ins_h] = strstart.toShort()
            }
            prev_length = match_length
            prev_match = match_start
            match_length = MIN_MATCH - 1
            if (hash_head != 0 && prev_length < max_lazy_match && ((strstart - hash_head) and 0xffff) <= w_size - MIN_LOOKAHEAD) {
                if (strategy != Z_HUFFMAN_ONLY) {
                    match_length = longest_match(hash_head)
                }
                if (match_length <= 5 && (strategy == Z_FILTERED || (match_length == MIN_MATCH && strstart - match_start > 4096))) {
                    match_length = MIN_MATCH - 1
                }
            }
            if (prev_length >= MIN_MATCH && match_length <= prev_length) {
                val max_insert = strstart + lookahead - MIN_MATCH
                bflush = _tr_tally(strstart - 1 - prev_match, prev_length - MIN_MATCH)
                lookahead -= prev_length - 1
                prev_length -= 2
                do {
                    if (++strstart <= max_insert) {
                        ins_h = (((ins_h shl hash_shift) xor (window[(strstart) + (MIN_MATCH - 1)].toInt() and 0xff)) and hash_mask)
                        hash_head = (head[ins_h].toInt() and 0xffff)
                        prev[strstart and w_mask] = head[ins_h]
                        head[ins_h] = strstart.toShort()
                    }
                } while (--prev_length != 0)
                match_available = 0
                match_length = MIN_MATCH - 1
                strstart++
                if (bflush) {
                    flush_block_only(false)
                    if (strm.avail_out == 0) return NEED_MORE
                }
            } else if (match_available != 0) {
                bflush = _tr_tally(0, window[strstart - 1].toInt() and 0xff)
                if (bflush) {
                    flush_block_only(false)
                }
                strstart++
                lookahead--
                if (strm.avail_out == 0) return NEED_MORE
            } else {
                match_available = 1
                strstart++
                lookahead--
            }
        }
        if (match_available != 0) {
            _tr_tally(0, window[strstart - 1].toInt() and 0xff)
            match_available = 0
        }
        flush_block_only(flush == Z_FINISH)
        if (strm.avail_out == 0) {
            return if (flush == Z_FINISH) FINISH_STARTED else NEED_MORE
        }
        return if (flush == Z_FINISH) FINISH_DONE else BLOCK_DONE
    }

    internal fun longest_match(cur_match_in: Int): Int {
        var cur_match = cur_match_in
        var chain_length = max_chain_length
        var scan = strstart
        var match: Int
        var len: Int
        var best_len = prev_length
        val limit = if (strstart > w_size - MIN_LOOKAHEAD) strstart - (w_size - MIN_LOOKAHEAD) else 0
        var local_nice_match = nice_match
        val wmask = w_mask
        val strend = strstart + MAX_MATCH
        var scan_end1 = window[scan + best_len - 1]
        var scan_end = window[scan + best_len]

        if (prev_length >= good_match) {
            chain_length = chain_length shr 2
        }
        if (local_nice_match > lookahead) {
            local_nice_match = lookahead
        }

        do {
            match = cur_match
            if (window[match + best_len] != scan_end || window[match + best_len - 1] != scan_end1 || window[match] != window[scan] || window[++match] != window[scan + 1]) {
                cur_match = prev[cur_match and wmask].toInt() and 0xffff
                continue
            }
            scan += 2
            match++
            do {
            } while (window[++scan] == window[++match] && window[++scan] == window[++match] && window[++scan] == window[++match] && window[++scan] == window[++match] && window[++scan] == window[++match] && window[++scan] == window[++match] && window[++scan] == window[++match] && window[++scan] == window[++match] && scan < strend)
            len = MAX_MATCH - (strend - scan)
            scan = strend - MAX_MATCH
            if (len > best_len) {
                match_start = cur_match_in
                best_len = len
                if (len >= local_nice_match) break
                scan_end1 = window[scan + best_len - 1]
                scan_end = window[scan + best_len]
            }
            cur_match = prev[cur_match and wmask].toInt() and 0xffff
        } while (cur_match > limit && --chain_length != 0)
        return if (best_len <= lookahead) best_len else lookahead
    }

    internal fun deflateInit(strm: ZStream, level: Int, bits: Int): Int {
        return deflateInit2(strm, level, Z_DEFLATED, bits, DEF_MEM_LEVEL, Z_DEFAULT_STRATEGY)
    }

    internal fun deflateInit(strm: ZStream, level: Int): Int {
        return deflateInit(strm, level, MAX_WBITS)
    }

    internal fun deflateReset(strm: ZStream): Int {
        strm.total_in = 0
        strm.total_out = 0
        strm.msg = null
        strm.data_type = Z_UNKNOWN
        pending = 0
        pending_out = 0
        if (noheader < 0) {
            noheader = 0
        }
        status = if (noheader != 0) BUSY_STATE else INIT_STATE
        strm.adler = strm._adler!!.adler32(0, null, 0, 0)
        last_flush = Z_NO_FLUSH
        tr_init()
        lm_init()
        return Z_OK
    }

    internal fun deflateEnd(): Int {
        if (status != INIT_STATE && status != BUSY_STATE && status != FINISH_STATE) {
            return Z_STREAM_ERROR
        }
        pending_buf = ByteArray(0)
        head = ShortArray(0)
        prev = ShortArray(0)
        window = ByteArray(0)
        return if (status == BUSY_STATE) Z_DATA_ERROR else Z_OK
    }

    internal fun deflateParams(strm: ZStream, level_in: Int, strategy_in: Int): Int {
        var current_level = level_in
        var err = Z_OK
        if (current_level == Z_DEFAULT_COMPRESSION) {
            current_level = 6
        }
        if (current_level < 0 || current_level > 9 || strategy_in < 0 || strategy_in > Z_HUFFMAN_ONLY) {
            return Z_STREAM_ERROR
        }
        if (config_table[this.level].func != config_table[current_level].func && strm.total_in != 0L) {
            err = strm.deflate(Z_PARTIAL_FLUSH)
        }
        if (this.level != current_level) {
            this.level = current_level
            max_lazy_match = config_table[this.level].max_lazy
            good_match = config_table[this.level].good_length
            nice_match = config_table[this.level].nice_length
            max_chain_length = config_table[this.level].max_chain
        }
        this.strategy = strategy_in
        return err
    }

    internal fun deflateSetDictionary(strm: ZStream, dictionary: ByteArray, dictLength: Int): Int {
        var length = dictLength
        var index = 0
        if (status != INIT_STATE) return Z_STREAM_ERROR
        strm.adler = strm._adler!!.adler32(strm.adler, dictionary, 0, dictLength)
        if (length < MIN_MATCH) return Z_OK
        if (length > w_size - MIN_LOOKAHEAD) {
            length = w_size - MIN_LOOKAHEAD
            index = dictLength - length
        }
        dictionary.copyInto(window, 0, index, length)
        strstart = length
        block_start = length
        ins_h = window[0].toInt() and 0xff
        ins_h = (((ins_h shl hash_shift) xor (window[1].toInt() and 0xff)) and hash_mask)
        for (n in 0..length - MIN_MATCH) {
            ins_h = (((ins_h shl hash_shift) xor (window[(n) + (MIN_MATCH - 1)].toInt() and 0xff)) and hash_mask)
            prev[n and w_mask] = head[ins_h]
            head[ins_h] = n.toShort()
        }
        return Z_OK
    }

    internal fun deflateInit2(strm: ZStream, level_param: Int, method_param: Int, windowBits_param: Int, memLevel_param: Int, strategy_param: Int): Int {
        var level_val = level_param
        val method_val = method_param
        var windowBits_val = windowBits_param
        val memLevel_val = memLevel_param
        val strategy_val = strategy_param

        var noheader_local = 0
        strm.msg = null
        if (level_val == Z_DEFAULT_COMPRESSION) level_val = 6
        if (windowBits_val < 0) {
            noheader_local = 1
            windowBits_val = -windowBits_val
        }
        if (memLevel_val < 1 || memLevel_val > MAX_MEM_LEVEL || method_val != Z_DEFLATED || windowBits_val < 9 || windowBits_val > 15 || level_val < 0 || level_val > 9 || strategy_val < 0 || strategy_val > Z_HUFFMAN_ONLY) {
            return Z_STREAM_ERROR
        }
        strm.dstate = this
        this.noheader = noheader_local
        w_bits = windowBits_val
        w_size = 1 shl w_bits
        w_mask = w_size - 1
        hash_bits = memLevel_val + 7
        hash_size = 1 shl hash_bits
        hash_mask = hash_size - 1
        hash_shift = (hash_bits + MIN_MATCH - 1) / MIN_MATCH
        window = ByteArray(w_size * 2)
        prev = ShortArray(w_size)
        head = ShortArray(hash_size)
        lit_bufsize = 1 shl (memLevel_val + 6)
        pending_buf = ByteArray(lit_bufsize * 4)
        pending_buf_size = lit_bufsize * 4
        d_buf = lit_bufsize
        l_buf = (1 + 2) * lit_bufsize
        this.level = level_val
        this.strategy = strategy_val
        this.method = method_val.toByte()
        return deflateReset(strm)
    }

    internal fun deflate(strm: ZStream, flush: Int): Int {
        val old_flush: Int
        if (flush > Z_FINISH || flush < 0) {
            return Z_STREAM_ERROR
        }
        if (strm.next_out == null || (strm.next_in == null && strm.avail_in != 0) || (status == FINISH_STATE && flush != Z_FINISH)) {
            strm.msg = Z_ERRMSG[Z_NEED_DICT - (Z_STREAM_ERROR)]
            return Z_STREAM_ERROR
        }
        if (strm.avail_out == 0) {
            strm.msg = Z_ERRMSG[Z_NEED_DICT - (Z_BUF_ERROR)]
            return Z_BUF_ERROR
        }
        this.strm = strm
        old_flush = last_flush
        last_flush = flush
        if (status == INIT_STATE) {
            var header: Int = (Z_DEFLATED + ((w_bits - 8) shl 4)) shl 8
            var level_flags_local: Int = ((this.level - 1) and 0xff) shr 1
            if (level_flags_local > 3) level_flags_local = 3
            header = header or (level_flags_local shl 6)
            if (strstart != 0) header = header or PRESET_DICT
            header += 31 - (header % 31)
            status = BUSY_STATE
            putShortMSB(this, header)
            if (strstart != 0) {
                putShortMSB(this, (strm.adler ushr 16).toInt())
                putShortMSB(this, (strm.adler and 0xffff).toInt())
            }
            strm.adler = strm._adler!!.adler32(0, null, 0, 0)
        }
        if (pending != 0) {
            strm.flush_pending()
            if (strm.avail_out == 0) {
                last_flush = -1
                return Z_OK
            }
        } else if (strm.avail_in == 0 && flush <= old_flush && flush != Z_FINISH) {
            strm.msg = Z_ERRMSG[Z_NEED_DICT - (Z_BUF_ERROR)]
            return Z_BUF_ERROR
        }
        if (status == FINISH_STATE && strm.avail_in != 0) {
            strm.msg = Z_ERRMSG[Z_NEED_DICT - (Z_BUF_ERROR)]
            return Z_BUF_ERROR
        }
        if (strm.avail_in != 0 || lookahead != 0 || (flush != Z_NO_FLUSH && status != FINISH_STATE)) {
            var bstate = -1
            when (config_table[level].func) {
                STORED -> bstate = deflate_stored(flush)
                FAST -> bstate = deflate_fast(flush)
                SLOW -> bstate = deflate_slow(flush)
                else -> { }
            }
            if (bstate == FINISH_STARTED || bstate == FINISH_DONE) {
                status = FINISH_STATE
            }
            if (bstate == NEED_MORE || bstate == FINISH_STARTED) {
                if (strm.avail_out == 0) {
                    last_flush = -1
                }
                return Z_OK
            }
            if (bstate == BLOCK_DONE) {
                if (flush == Z_PARTIAL_FLUSH) {
                    _tr_align(this)
                } else {
                    _tr_stored_block(this, 0, 0, false)
                    if (flush == Z_FULL_FLUSH) {
                        for (i in 0 until hash_size) head[i] = 0
                    }
                }
                strm.flush_pending()
                if (strm.avail_out == 0) {
                    last_flush = -1
                    return Z_OK
                }
            }
        }
        if (flush != Z_FINISH) return Z_OK
        if (noheader != 0) return Z_STREAM_END
        putShortMSB(this, (strm.adler ushr 16).toInt())
        putShortMSB(this, (strm.adler and 0xffff).toInt())
        strm.flush_pending()
        noheader = -1
        return if (pending != 0) Z_OK else Z_STREAM_END
    }

    companion object {
        private const val STORED = 0
        private const val FAST = 1
        private const val SLOW = 2
        private val config_table: Array<Config>
        init {
            config_table = arrayOf(
                Config(0, 0, 0, 0, STORED),
                Config(4, 4, 8, 4, FAST),
                Config(4, 5, 16, 8, FAST),
                Config(4, 6, 32, 32, FAST),
                Config(4, 4, 16, 16, SLOW),
                Config(8, 16, 32, 32, SLOW),
                Config(8, 16, 128, 128, SLOW),
                Config(8, 32, 128, 256, SLOW),
                Config(32, 128, 258, 1024, SLOW),
                Config(32, 258, 258, 4096, SLOW)
            )
        }

        // smaller function moved to DeflateUtils.kt
    }
}