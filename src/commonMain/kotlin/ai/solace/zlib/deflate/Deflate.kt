// Copyright (c) 2006, ComponentAce
// http://www.componentace.com
// All rights reserved.

// Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:

// Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
// Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.
// Neither the name of ComponentAce nor the names of its contributors may be used to endorse or promote products derived from this software without specific prior written permission.
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

/*
Copyright (c) 2000,2001,2002,2003 ymnk, JCraft,Inc. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice,
this list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in
the documentation and/or other materials provided with the distribution.

3. The names of the authors may not be used to endorse or promote products
derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED WARRANTIES,
INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL JCRAFT,
INC. OR ANY CONTRIBUTORS TO THIS SOFTWARE BE LIABLE FOR ANY DIRECT, INDIRECT,
INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA,
OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,
EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/
/*
* This program is based on zlib-1.1.3, so all credit should go authors
* Jean-loup Gailly(jloup@gzip.org) and Mark Adler(madler@alumni.caltech.edu)
* and contributors of zlib.
*/
package componentace.compression.libs.zlib.deflate

import componentace.compression.libs.zlib.deflate.Tree

class Deflate {

    private val MAX_MEM_LEVEL = 9
    private val Z_DEFAULT_COMPRESSION = -1
    private val MAX_WBITS = 15 // 32K LZ77 window
    private val DEF_MEM_LEVEL = 8

    internal class Config(
        internal var good_length: Int,
        internal var max_lazy: Int,
        internal var nice_length: Int,
        internal var max_chain: Int,
        internal var func: Int
    )

    private val STORED = 0
    private val FAST = 1
    private val SLOW = 2
    private lateinit var config_table: Array<Config>

    private val z_errmsg = arrayOf(
        "need dictionary",
        "stream end",
        "",
        "file error",
        "stream error",
        "data error",
        "insufficient memory",
        "buffer error",
        "incompatible version",
        ""
    )

    // block not completed, need more input or more output
    private val NeedMore = 0

    // block flush performed
    private val BlockDone = 1

    // finish started, need only more output at next deflate
    private val FinishStarted = 2

    // finish done, accept no more input or output
    private val FinishDone = 3

    // preset dictionary flag in zlib header
    private val PRESET_DICT = 0x20

    private val Z_FILTERED = 1
    private val Z_HUFFMAN_ONLY = 2
    private val Z_DEFAULT_STRATEGY = 0

    private val Z_NO_FLUSH = 0
    private val Z_PARTIAL_FLUSH = 1
    private val Z_SYNC_FLUSH = 2
    private val Z_FULL_FLUSH = 3
    private val Z_FINISH = 4

    private val Z_OK = 0
    private val Z_STREAM_END = 1
    private val Z_NEED_DICT = 2
    private val Z_ERRNO = -1
    private val Z_STREAM_ERROR = -2
    private val Z_DATA_ERROR = -3
    private val Z_MEM_ERROR = -4
    private val Z_BUF_ERROR = -5
    private val Z_VERSION_ERROR = -6

    private val INIT_STATE = 42
    private val BUSY_STATE = 113
    private val FINISH_STATE = 666

    // The deflate compression method
    private val Z_DEFLATED = 8

    private val STORED_BLOCK = 0
    private val STATIC_TREES = 1
    private val DYN_TREES = 2

    // The three kinds of block type
    private val Z_BINARY = 0
    private val Z_ASCII = 1
    private val Z_UNKNOWN = 2

    private val Buf_size = 8 * 2

    // repeat previous bit length 3-6 times (2 bits of repeat count)
    private val REP_3_6 = 16

    // repeat a zero length 3-10 times  (3 bits of repeat count)
    private val REPZ_3_10 = 17

    // repeat a zero length 11-138 times  (7 bits of repeat count)
    private val REPZ_11_138 = 18

    private val MIN_MATCH = 3
    private val MAX_MATCH = 258
    private val MIN_LOOKAHEAD = MAX_MATCH + MIN_MATCH + 1

    private val MAX_BITS = 15
    private val D_CODES = 30
    private val BL_CODES = 19
    private val LENGTH_CODES = 29
    private val LITERALS = 256
    private val L_CODES = LITERALS + 1 + LENGTH_CODES
    private val HEAP_SIZE = 2 * L_CODES + 1

    private val END_BLOCK = 256

    internal lateinit var strm: ZStream // pointer back to this zlib stream
    internal var status: Int = 0 // as the name implies
    internal lateinit var pending_buf: ByteArray // output still pending
    internal var pending_buf_size: Int = 0 // size of pending_buf
    internal var pending_out: Int = 0 // next pending byte to output to the stream
    internal var pending: Int = 0 // nb of bytes in the pending buffer
    internal var noheader: Int = 0 // suppress zlib header and adler32
    internal var data_type: Byte = 0 // UNKNOWN, BINARY or ASCII
    internal var method: Byte = 0 // STORED (for zip only) or DEFLATED
    internal var last_flush: Int = 0 // value of flush param for previous deflate call

    internal var w_size: Int = 0 // LZ77 window size (32K by default)
    internal var w_bits: Int = 0 // log2(w_size)  (8..16)
    internal var w_mask: Int = 0 // w_size - 1

    internal lateinit var window: ByteArray
    // Sliding window. Input bytes are read into the second half of the window,
    // and move to the first half later to keep a dictionary of at least wSize
    // bytes. With this organization, matches are limited to a distance of
    // wSize-MAX_MATCH bytes, but this ensures that IO is always
    // performed with a length multiple of the block size. Also, it limits
    // the window size to 64K, which is quite useful on MSDOS.
    // To do: use the user input buffer as sliding window.

    internal var window_size: Int = 0
    // Actual size of window: 2*wSize, except when the user input buffer
    // is directly used as sliding window.

    internal lateinit var prev: ShortArray
    // Link to older string with same hash index. To limit the size of this
    // array to 64K, this link is maintained only for the last 32K strings.
    // An index in this array is thus a window index modulo 32K.

    internal lateinit var head: ShortArray // Heads of the hash chains or NIL.

    internal var ins_h: Int = 0 // hash index of string to be inserted
    internal var hash_size: Int = 0 // number of elements in hash table
    internal var hash_bits: Int = 0 // log2(hash_size)
    internal var hash_mask: Int = 0 // hash_size-1

    // Number of bits by which ins_h must be shifted at each input
    // step. It must be such that after MIN_MATCH steps, the oldest
    // byte no longer takes part in the hash key, that is:
    // hash_shift * MIN_MATCH >= hash_bits
    internal var hash_shift: Int = 0

    // Window position at the beginning of the current output block. Gets
    // negative when the window is moved backwards.

    internal var block_start: Int = 0

    internal var match_length: Int = 0 // length of best match
    internal var prev_match: Int = 0 // previous match
    internal var match_available: Int = 0 // set if previous match exists
    internal var strstart: Int = 0 // start of string to insert
    internal var match_start: Int = 0 // start of matching string
    internal var lookahead: Int = 0 // number of valid bytes ahead in window

    // Length of the best match at previous step. Matches not greater than this
    // are discarded. This is used in the lazy match evaluation.
    internal var prev_length: Int = 0

    // To speed up deflation, hash chains are never searched beyond this
    // length.  A higher limit improves compression ratio but degrades the speed.
    internal var max_chain_length: Int = 0

    // Attempt to find a better match only when the current match is strictly
    // smaller than this value. This mechanism is used only for compression
    // levels >= 4.
    internal var max_lazy_match: Int = 0

    // Insert new strings in the hash table only if the match length is not
    // greater than this length. This saves time but degrades compression.
    // max_insert_length is used only for compression levels <= 3.

    internal var level: Int = 0 // compression level (1..9)
    internal var strategy: Int = 0 // favor or force Huffman coding

    // Use a faster search when the previous match is longer than this
    internal var good_match: Int = 0

    // Stop searching when current match exceeds this
    internal var nice_match: Int = 0

    internal lateinit var dyn_ltree: ShortArray // literal and length tree
    internal lateinit var dyn_dtree: ShortArray // distance tree
    internal lateinit var bl_tree: ShortArray // Huffman tree for bit lengths

    internal var l_desc = Tree() // desc for literal tree
    internal var d_desc = Tree() // desc for distance tree
    internal var bl_desc = Tree() // desc for bit length tree

    // number of codes at each bit length for an optimal tree
    internal var bl_count = ShortArray(MAX_BITS + 1)

    // heap used to build the Huffman trees
    internal var heap = IntArray(2 * L_CODES + 1)

    internal var heap_len: Int = 0 // number of elements in the heap
    internal var heap_max: Int = 0 // element of largest frequency
    // The sons of heap[n] are heap[2*n] and heap[2*n+1]. heap[0] is not used.
    // The same heap array is used to build all trees.

    // Depth of each subtree used as tie breaker for trees of equal frequency
    internal var depth = ByteArray(2 * L_CODES + 1)

    internal var l_buf: Int = 0 // index for literals or lengths */

    // Size of match buffer for literals/lengths.  There are 4 reasons for
    // limiting lit_bufsize to 64K:
    //   - frequencies can be kept in 16 bit counters
    //   - if compression is not successful for the first block, all input
    //     data is still in the window so we can still emit a stored block even
    //     when input comes from standard input.  (This can also be done for
    //     all blocks if lit_bufsize is not greater than 32K.)
    //   - if compression is not successful for a file smaller than 64K, we can
    //     even emit a stored file instead of a stored block (saving 5 bytes).
    //     This is applicable only for zip (not gzip or zlib).
    //   - creating new Huffman trees less frequently may not provide fast
    //     adaptation to changes in the input data statistics. (Take for
    //     example a binary file with poorly compressible code followed by
    //     a highly compressible string table.) Smaller buffer sizes give
    //     fast adaptation but have of course the overhead of transmitting
    //     trees more frequently.
    //   - I can't count above 4
    internal var lit_bufsize: Int = 0

    internal var last_lit: Int = 0 // running index in l_buf

    // Buffer for distances. To simplify the code, d_buf and l_buf have
    // the same number of elements. To use different lengths, an extra flag
    // array would be necessary.

    internal var d_buf: Int = 0 // index of pendig_buf

    internal var opt_len: Int = 0 // bit length of current block with optimal trees
    internal var static_len: Int = 0 // bit length of current block with static trees
    internal var matches: Int = 0 // number of string matches in current block
    internal var last_eob_len: Int = 0 // bit length of EOB code for last block

    // Output buffer. bits are inserted starting at the bottom (least
    // significant bits).
    internal var bi_buf: Short = 0

    // Number of valid bits in bi_buf.  All bits above the last valid bit
    // are always zero.
    internal var bi_valid: Int = 0

    init {
        dyn_ltree = ShortArray(HEAP_SIZE * 2)
        dyn_dtree = ShortArray((2 * D_CODES + 1) * 2) // distance tree
        bl_tree = ShortArray((2 * BL_CODES + 1) * 2) // Huffman tree for bit lengths
    }

    internal fun lm_init() {
        window_size = 2 * w_size
        head[hash_size - 1] = 0
        for (i in 0 until hash_size - 1) {
            head[i] = 0
        }
        // Set the default configuration parameters:
        max_lazy_match = config_table[level].max_lazy
        good_match = config_table[level].good_length
        nice_match = config_table[level].nice_length
        max_chain_length = config_table[level].max_chain

        strstart = 0
        block_start = 0
        lookahead = 0
        match_length = prev_length = MIN_MATCH - 1
        match_available = 0
        ins_h = 0
    }

    // Initialize the tree data structures for a new zlib stream.
    internal fun tr_init() {
        l_desc.dyn_tree = dyn_ltree
        l_desc.stat_desc = StaticTree.static_l_desc

        d_desc.dyn_tree = dyn_dtree
        d_desc.stat_desc = StaticTree.static_d_desc

        bl_desc.dyn_tree = bl_tree
        bl_desc.stat_desc = StaticTree.static_bl_desc

        bi_buf = 0
        bi_valid = 0
        last_eob_len = 8 // enough lookahead for inflate

        // Initialize the first block of the first file:
        init_block()
    }

    internal fun init_block() {
        // Initialize the trees.
        for (i in 0 until L_CODES)
            dyn_ltree[i * 2] = 0
        for (i in 0 until D_CODES)
            dyn_dtree[i * 2] = 0
        for (i in 0 until BL_CODES)
            bl_tree[i * 2] = 0

        dyn_ltree[END_BLOCK * 2] = 1
        opt_len = static_len = 0
        last_lit = matches = 0
    }

    // Restore the heap property by moving down the tree starting at node k,
    // exchanging a node with the smallest of its two sons if necessary, stopping
    // when the heap property is re-established (each father smaller than its
    // two sons).
    internal fun pqdownheap(tree: ShortArray, k: Int) {
        var v = heap[k]
        var j = k shl 1 // left son of k
        while (j <= heap_len) {
            // Set j to the smallest of the two sons:
            if (j < heap_len && smaller(tree, heap[j + 1], heap[j], depth)) {
                j++
            }
            // Exit if v is smaller than both sons
            if (smaller(tree, v, heap[j], depth)) break

            // Exchange v with the smallest son
            heap[k] = heap[j]
            k = j
            // And continue down the tree, setting j to the left son of k
            j = j shl 1
        }
        heap[k] = v
    }

    internal fun scan_tree(tree: ShortArray, max_code: Int) {
        var n: Int // iterates over all tree elements
        var prevlen = -1 // last emitted length
        var curlen: Int // length of current code
        var nextlen = tree[0 * 2 + 1].toInt() // length of next code
        var count = 0 // repeat count of the current code
        var max_count = 7 // max repeat count
        var min_count = 4 // min repeat count

        if (nextlen == 0) {
            max_count = 138
            min_count = 3
        }
        tree[(max_code + 1) * 2 + 1] = 0xffff.toShort() // guard

        for (n in 0..max_code) {
            curlen = nextlen
            nextlen = tree[(n + 1) * 2 + 1].toInt()
            if (++count < max_count && curlen == nextlen) {
                continue
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
        }
    }

    internal fun build_bl_tree(): Int {
        var max_blindex: Int // index of last bit length code of non zero freq

        // Determine the bit length frequencies for literal and distance trees
        scan_tree(dyn_ltree, l_desc.max_code)
        scan_tree(dyn_dtree, d_desc.max_code)

        // Build the bit length tree:
        bl_desc.build_tree(this)
        // opt_len now includes the length of the tree representations, except
        // the lengths of the bit lengths codes and the 5+5+4 bits for the counts.

        // Determine the number of bit length codes to send. The pkzip format
        // requires that at least 4 bit length codes be sent. (appnote.txt says
        // 3 but the actual value used is 4.)
        for (max_blindex in BL_CODES - 1 downTo 3) {
            if (bl_tree[Tree.bl_order[max_blindex] * 2 + 1] != 0) break
        }
        // Update opt_len to include the bit length tree and counts
        opt_len += 3 * (max_blindex + 1) + 5 + 5 + 4

        return max_blindex
    }

    internal fun send_all_trees(lcodes: Int, dcodes: Int, blcodes: Int) {
        var rank: Int // index in bl_order

        send_bits(lcodes - 257, 5) // not +255 as stated in appnote.txt
        send_bits(dcodes - 1, 5)
        send_bits(blcodes - 4, 4) // not -3 as stated in appnote.txt
        for (rank in 0 until blcodes) {
            send_bits(bl_tree[Tree.bl_order[rank] * 2 + 1].toInt(), 3)
        }
        send_tree(dyn_ltree, lcodes - 1) // literal tree
        send_tree(dyn_dtree, dcodes - 1) // distance tree
    }

    internal fun send_tree(tree: ShortArray, max_code: Int) {
        var n: Int // iterates over all tree elements
        var prevlen = -1 // last emitted length
        var curlen: Int // length of current code
        var nextlen = tree[0 * 2 + 1].toInt() // length of next code
        var count = 0 // repeat count of the current code
        var max_count = 7 // max repeat count
        var min_count = 4 // min repeat count

        if (nextlen == 0) {
            max_count = 138
            min_count = 3
        }

        for (n in 0..max_code) {
            curlen = nextlen
            nextlen = tree[(n + 1) * 2 + 1].toInt()
            if (++count < max_count && curlen == nextlen) {
                continue
            } else if (count < min_count) {
                do {
                    send_code(curlen, bl_tree)
                } while (--count != 0)
            } else if (curlen != 0) {
                if (curlen != prevlen) {
                    send_code(curlen, bl_tree)
                    count--
                }
                send_code(REP_3_6, bl_tree)
                send_bits(count - 3, 2)
            } else if (count <= 10) {
                send_code(REPZ_3_10, bl_tree)
                send_bits(count - 3, 3)
            } else {
                send_code(REPZ_11_138, bl_tree)
                send_bits(count - 11, 7)
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
        }
    }

    // Output a byte on the stream.
    // IN assertion: there is enough room in pending_buf.
    internal fun put_byte(p: ByteArray, start: Int, len: Int) {
        System.arraycopy(p, start, pending_buf, pending, len)
        pending += len
    }

    internal fun put_byte(c: Byte) {
        pending_buf[pending++] = c
    }

    internal fun put_short(w: Int) {
        put_byte(w.toByte())
        put_byte((w ushr 8).toByte())
    }

    internal fun putShortMSB(b: Int) {
        put_byte((b shr 8).toByte())
        put_byte(b.toByte())
    }

    internal fun send_code(c: Int, tree: ShortArray) {
        send_bits((tree[c * 2].toInt() and 0xffff), (tree[c * 2 + 1].toInt() and 0xffff))
    }

    internal fun send_bits(value_Renamed: Int, length: Int) {
        val len = length
        if (bi_valid > Buf_size - len) {
            val value = value_Renamed
            // bi_buf |= (val << bi_valid);
            bi_buf = (bi_buf.toInt() or ((value shl bi_valid) and 0xffff).toShort().toInt()).toShort()
            put_short(bi_buf.toInt())
            bi_buf = (value ushr (Buf_size - bi_valid)).toShort()
            bi_valid += len - Buf_size
        } else {
            // bi_buf |= (value) << bi_valid;
            bi_buf = (bi_buf.toInt() or ((value_Renamed shl bi_valid) and 0xffff).toShort().toInt()).toShort()
            bi_valid += len
        }
    }

    internal fun _tr_align() {
        send_bits(STATIC_TREES shl 1, 3)
        send_code(END_BLOCK, StaticTree.static_ltree)

        bi_flush()

        if (1 + last_eob_len + 10 - bi_valid < 9) {
            send_bits(STATIC_TREES shl 1, 3)
            send_code(END_BLOCK, StaticTree.static_ltree)
            bi_flush()
        }
        last_eob_len = 7
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
            dist--
            dyn_ltree[(Tree._length_code[lc] + LITERALS + 1) * 2]++
            dyn_dtree[Tree.d_code(dist) * 2]++
        }

        if ((last_lit and 0x1fff) == 0 && level > 2) {
            var out_length = last_lit * 8
            val in_length = strstart - block_start
            for (dcode in 0 until D_CODES) {
                out_length = (out_length + dyn_dtree[dcode * 2] * (5L + Tree.extra_dbits[dcode])).toInt()
            }
            out_length = out_length ushr 3
            if (matches < last_lit / 2 && out_length < in_length / 2)
                return true
        }

        return last_lit == lit_bufsize - 1
    }

    internal fun compress_block(ltree: ShortArray, dtree: ShortArray) {
        var dist: Int // distance of matched string
        var lc: Int // match length or unmatched char (if dist == 0)
        var lx = 0 // running index in l_buf
        var code: Int // the code to send
        var extra: Int // number of extra bits to send

        if (last_lit != 0) {
            do {
                dist =
                    ((pending_buf[d_buf + lx * 2].toInt() shl 8 and 0xff00) or (pending_buf[d_buf + lx * 2 + 1].toInt() and 0xff))
                lc = (pending_buf[l_buf + lx]).toInt() and 0xff
                lx++

                if (dist == 0) {
                    send_code(lc, ltree) // send a literal byte
                } else {
                    code = Tree._length_code[lc]

                    send_code(code + LITERALS + 1, ltree) // send the length code
                    extra = Tree.extra_lbits[code]
                    if (extra != 0) {
                        lc -= Tree.base_length[code]
                        send_bits(lc, extra) // send the extra length bits
                    }
                    dist--
                    code = Tree.d_code(dist)

                    send_code(code, dtree) // send the distance code
                    extra = Tree.extra_dbits[code]
                    if (extra != 0) {
                        dist -= Tree.base_dist[code]
                        send_bits(dist, extra) // send the extra distance bits
                    }
                }
            } while (lx < last_lit)
        }

        send_code(END_BLOCK, ltree)
        last_eob_len = ltree[END_BLOCK * 2 + 1].toInt()
    }

    internal fun set_data_type() {
        var n = 0
        var ascii_freq = 0
        var bin_freq = 0
        while (n < 7) {
            bin_freq += dyn_ltree[n * 2]
            n++
        }
        while (n < 128) {
            ascii_freq += dyn_ltree[n * 2]
            n++
        }
        while (n < LITERALS) {
            bin_freq += dyn_ltree[n * 2]
            n++
        }
        data_type = if (bin_freq > ascii_freq ushr 2) Z_BINARY.toByte() else Z_ASCII.toByte()
    }

    internal fun bi_flush() {
        if (bi_valid == 16) {
            put_short(bi_buf.toInt())
            bi_buf = 0
            bi_valid = 0
        } else if (bi_valid >= 8) {
            put_byte(bi_buf.toByte())
            bi_buf = (bi_buf.toInt() ushr 8).toShort()
            bi_valid -= 8
        }
    }

    internal fun bi_windup() {
        if (bi_valid > 8) {
            put_short(bi_buf.toInt())
        } else if (bi_valid > 0) {
            put_byte(bi_buf.toByte())
        }
        bi_buf = 0
        bi_valid = 0
    }

    internal fun copy_block(buf: Int, len: Int, header: Boolean) {
        bi_windup()
        last_eob_len = 8

        if (header) {
            put_short(len.toShort().toInt())
            put_short(len.inv().toShort().toInt())
        }

        put_byte(window, buf, len)
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
                if (lookahead == 0 && flush == Z_NO_FLUSH)
                    return NeedMore
                if (lookahead == 0)
                    break
            }

            strstart += lookahead
            lookahead = 0

            max_start = block_start + max_block_size
            if (strstart == 0 || strstart >= max_start) {
                lookahead = (strstart - max_start).toInt()
                strstart = max_start.toInt()

                flush_block_only(false)
                if (strm.avail_out == 0)
                    return NeedMore
            }

            if (strstart - block_start >= w_size - MIN_LOOKAHEAD) {
                flush_block_only(false)
                if (strm.avail_out == 0)
                    return NeedMore
            }
        }

        flush_block_only(flush == Z_FINISH)
        if (strm.avail_out == 0)
            return if (flush == Z_FINISH) FinishStarted else NeedMore

        return if (flush == Z_FINISH) FinishDone else BlockDone
    }

    internal fun _tr_stored_block(buf: Int, stored_len: Int, eof: Boolean) {
        send_bits((STORED_BLOCK shl 1) + if (eof) 1 else 0, 3)
        copy_block(buf, stored_len, true)
    }

    internal fun _tr_flush_block(buf: Int, stored_len: Int, eof: Boolean) {
        var opt_lenb: Int
        var static_lenb: Int
        val max_blindex: Int

        if (level > 0) {
            if (data_type == Z_UNKNOWN)
                set_data_type()

            l_desc.build_tree(this)
            d_desc.build_tree(this)

            max_blindex = build_bl_tree()

            opt_lenb = (opt_len + 3 + 7) ushr 3
            static_lenb = (static_len + 3 + 7) ushr 3

            if (static_lenb <= opt_lenb)
                opt_lenb = static_lenb
        } else {
            opt_lenb = static_lenb = stored_len + 5
        }

        if (stored_len + 4 <= opt_lenb && buf != -1) {
            _tr_stored_block(buf, stored_len, eof)
        } else if (static_lenb.toFloat() == opt_lenb.toFloat()) {
            send_bits((STATIC_TREES shl 1) + if (eof) 1 else 0, 3)
            compress_block(StaticTree.static_ltree, StaticTree.static_dtree)
        } else {
            send_bits((DYN_TREES shl 1) + if (eof) 1 else 0, 3)
            send_all_trees(l_desc.max_code + 1, d_desc.max_code + 1, max_blindex + 1)
            compress_block(dyn_ltree, dyn_dtree)
        }

        init_block()

        if (eof) {
            bi_windup()
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
                System.arraycopy(window, w_size, window, 0, w_size)
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

            if (strm.avail_in == 0)
                return

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
                if (lookahead < MIN_LOOKAHEAD && flush == Z_NO_FLUSH) {
                    return NeedMore
                }
                if (lookahead == 0) break
            }

            if (lookahead >= MIN_MATCH) {
                ins_h =
                    (((ins_h shl hash_shift) xor (window[(strstart) + (MIN_MATCH - 1)].toInt() and 0xff)) and hash_mask)

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

                        ins_h =
                            (((ins_h shl hash_shift) xor (window[(strstart) + (MIN_MATCH - 1)].toInt() and 0xff)) and hash_mask)
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
                if (strm.avail_out == 0)
                    return NeedMore
            }
        }

        flush_block_only(flush == Z_FINISH)
        if (strm.avail_out == 0) {
            return if (flush == Z_FINISH) FinishStarted else NeedMore
        }
        return if (flush == Z_FINISH) FinishDone else BlockDone
    }

    internal fun deflate_slow(flush: Int): Int {
        var hash_head = 0
        var bflush: Boolean

        while (true) {
            if (lookahead < MIN_LOOKAHEAD) {
                fill_window()
                if (lookahead < MIN_LOOKAHEAD && flush == Z_NO_FLUSH) {
                    return NeedMore
                }
                if (lookahead == 0) break
            }

            if (lookahead >= MIN_MATCH) {
                ins_h =
                    (((ins_h shl hash_shift) xor (window[(strstart) + (MIN_MATCH - 1)].toInt() and 0xff)) and hash_mask)
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
                        ins_h =
                            (((ins_h shl hash_shift) xor (window[(strstart) + (MIN_MATCH - 1)].toInt() and 0xff)) and hash_mask)
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
                    if (strm.avail_out == 0)
                        return NeedMore
                }
            } else if (match_available != 0) {
                bflush = _tr_tally(0, window[strstart - 1].toInt() and 0xff)

                if (bflush) {
                    flush_block_only(false)
                }
                strstart++
                lookahead--
                if (strm.avail_out == 0)
                    return NeedMore
            } else {
                match_available = 1
                strstart++
                lookahead--
            }
        }

        if (match_available != 0) {
            bflush = _tr_tally(0, window[strstart - 1].toInt() and 0xff)
            match_available = 0
        }
        flush_block_only(flush == Z_FINISH)

        if (strm.avail_out == 0) {
            return if (flush == Z_FINISH) FinishStarted else NeedMore
        }

        return if (flush == Z_FINISH) FinishDone else BlockDone
    }

    internal fun longest_match(cur_match: Int): Int {
        var chain_length = max_chain_length
        var scan = strstart
        var match: Int
        var len: Int
        var best_len = prev_length
        val limit = if (strstart > w_size - MIN_LOOKAHEAD) strstart - w_size + MIN_LOOKAHEAD else 0
        var nice_match = this.nice_match

        var wmask = w_mask

        val strend = strstart + MAX_MATCH
        var scan_end1 = window[scan + best_len - 1]
        var scan_end = window[scan + best_len]

        if (prev_length >= good_match) {
            chain_length = chain_length shr 2
        }

        if (nice_match > lookahead)
            nice_match = lookahead

        do {
            match = cur_match

            if (window[match + best_len] != scan_end || window[match + best_len - 1] != scan_end1 || window[match] != window[scan] || window[++match] != window[scan + 1])
                continue

            scan += 2
            match++

            do {
            } while (window[++scan] == window[++match] && window[++scan] == window[++match] && window[++scan] == window[++match] && window[++scan] == window[++match] && window[++scan] == window[++match] && window[++scan] == window[++match] && window[++scan] == window[++match] && window[++scan] == window[++match] && scan < strend)

            len = MAX_MATCH - (strend - scan)
            scan = strend - MAX_MATCH

            if (len > best_len) {
                match_start = cur_match
                best_len = len
                if (len >= nice_match) break
                scan_end1 = window[scan + best_len - 1]
                scan_end = window[scan + best_len]
            }
        } while (prev[cur_match and wmask].toInt().also { cur_match = it and 0xffff } > limit && --chain_length != 0)

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
        strm.adler = strm._adler.adler32(0, null, 0, 0)

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

    internal fun deflateParams(strm: ZStream, _level: Int, _strategy: Int): Int {
        var level = _level
        var err = Z_OK

        if (level == Z_DEFAULT_COMPRESSION) {
            level = 6
        }
        if (level < 0 || level > 9 || _strategy < 0 || _strategy > Z_HUFFMAN_ONLY) {
            return Z_STREAM_ERROR
        }

        if (config_table[level].func != config_table[level].func && strm.total_in != 0) {
            err = strm.deflate(Z_PARTIAL_FLUSH)
        }

        if (this.level != level) {
            this.level = level
            max_lazy_match = config_table[level].max_lazy
            good_match = config_table[level].good_length
            nice_match = config_table[level].nice_length
            max_chain_length = config_table[level].max_chain
        }
        strategy = _strategy
        return err
    }

    internal fun deflateSetDictionary(strm: ZStream, dictionary: ByteArray, dictLength: Int): Int {
        var length = dictLength
        var index = 0

        if (dictionary == null || status != INIT_STATE) return Z_STREAM_ERROR

        strm.adler = strm._adler.adler32(strm.adler, dictionary, 0, dictLength)

        if (length < MIN_MATCH) return Z_OK
        if (length > w_size - MIN_LOOKAHEAD) {
            length = w_size - MIN_LOOKAHEAD
            index = dictLength - length
        }
        System.arraycopy(dictionary, index, window, 0, length)
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

    internal fun deflateInit2(
        strm: ZStream,
        level: Int,
        method: Int,
        windowBits: Int,
        memLevel: Int,
        strategy: Int
    ): Int {
        var noheader = 0

        strm.msg = null

        var level = level
        if (level == Z_DEFAULT_COMPRESSION) level = 6

        var windowBits = windowBits
        if (windowBits < 0) {
            noheader = 1
            windowBits = -windowBits
        }

        if (memLevel < 1 || memLevel > MAX_MEM_LEVEL || method != Z_DEFLATED || windowBits < 9 || windowBits > 15 || level < 0 || level > 9 || strategy < 0 || strategy > Z_HUFFMAN_ONLY) {
            return Z_STREAM_ERROR
        }

        strm.dstate = this

        this.noheader = noheader
        w_bits = windowBits
        w_size = 1 shl w_bits
        w_mask = w_size - 1

        hash_bits = memLevel + 7
        hash_size = 1 shl hash_bits
        hash_mask = hash_size - 1
        hash_shift = (hash_bits + MIN_MATCH - 1) / MIN_MATCH

        window = ByteArray(w_size * 2)
        prev = ShortArray(w_size)
        head = ShortArray(hash_size)

        lit_bufsize = 1 shl (memLevel + 6)

        pending_buf = ByteArray(lit_bufsize * 4)
        pending_buf_size = lit_bufsize * 4

        d_buf = lit_bufsize
        l_buf = (1 + 2) * lit_bufsize

        this.level = level
        this.strategy = strategy
        this.method = method.toByte()

        return deflateReset(strm)
    }

    internal fun deflate(strm: ZStream, flush: Int): Int {
        val old_flush: Int

        if (flush > Z_FINISH || flush < 0) {
            return Z_STREAM_ERROR
        }

        if (strm.next_out == null || (strm.next_in == null && strm.avail_in != 0) || (status == FINISH_STATE && flush != Z_FINISH)) {
            strm.msg = z_errmsg[Z_NEED_DICT - (Z_STREAM_ERROR)]
            return Z_STREAM_ERROR
        }
        if (strm.avail_out == 0) {
            strm.msg = z_errmsg[Z_NEED_DICT - (Z_BUF_ERROR)]
            return Z_BUF_ERROR
        }

        this.strm = strm
        old_flush = last_flush
        last_flush = flush

        if (status == INIT_STATE) {
            var header: Int = (Z_DEFLATED + ((w_bits - 8) shl 4)) shl 8
            var level_flags: Int = ((level - 1) and 0xff) shr 1

            if (level_flags > 3) level_flags = 3
            header = header or (level_flags shl 6)
            if (strstart != 0) header = header or PRESET_DICT
            header += 31 - (header % 31)

            status = BUSY_STATE
            putShortMSB(header)

            if (strstart != 0) {
                putShortMSB((strm.adler ushr 16).toInt())
                putShortMSB((strm.adler and 0xffff).toInt())
            }
            strm.adler = strm._adler.adler32(0, null, 0, 0)
        }

        if (pending != 0) {
            strm.flush_pending()
            if (strm.avail_out == 0) {
                last_flush = -1
                return Z_OK
            }
        } else if (strm.avail_in == 0 && flush <= old_flush && flush != Z_FINISH) {
            strm.msg = z_errmsg[Z_NEED_DICT - (Z_BUF_ERROR)]
            return Z_BUF_ERROR
        }

        if (status == FINISH_STATE && strm.avail_in != 0) {
            strm.msg = z_errmsg[Z_NEED_DICT - (Z_BUF_ERROR)]
            return Z_BUF_ERROR
        }

        if (strm.avail_in != 0 || lookahead != 0 || (flush != Z_NO_FLUSH && status != FINISH_STATE)) {
            var bstate = -1
            when (config_table[level].func) {
                STORED -> bstate = deflate_stored(flush)
                FAST -> bstate = deflate_fast(flush)
                SLOW -> bstate = deflate_slow(flush)
                else -> {
                }
            }

            if (bstate == FinishStarted || bstate == FinishDone) {
                status = FINISH_STATE
            }
            if (bstate == NeedMore || bstate == FinishStarted) {
                if (strm.avail_out == 0) {
                    last_flush = -1
                }
                return Z_OK
            }

            if (bstate == BlockDone) {
                if (flush == Z_PARTIAL_FLUSH) {
                    _tr_align()
                } else {
                    _tr_stored_block(0, 0, false)
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

        if (flush != Z_FINISH)
            return Z_OK
        if (noheader != 0)
            return Z_STREAM_END

        putShortMSB((strm.adler ushr 16).toInt())
        putShortMSB((strm.adler and 0xffff).toInt())
        strm.flush_pending()

        noheader = -1
        return if (pending != 0) Z_OK else Z_STREAM_END
    }

    companion object {
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

        internal fun smaller(tree: ShortArray, n: Int, m: Int, depth: ByteArray): Boolean {
            return tree[n * 2].toInt() < tree[m * 2].toInt() ||
                    (tree[n * 2].toInt() == tree[m * 2].toInt() && depth[n] <= depth[m])
        }
    }
}