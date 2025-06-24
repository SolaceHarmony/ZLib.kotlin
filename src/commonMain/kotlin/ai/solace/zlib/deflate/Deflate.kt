package ai.solace.zlib.deflate // Ensure correct package

// Fix the imports to use the new package structure
// For functions moved from Deflate.kt itself to DeflateUtils.kt
import ai.solace.zlib.common.*
// For functions moved from Deflate.kt itself to DeflateUtils.kt
import ai.solace.zlib.deflate.* // Wildcard import for all DeflateUtils

class Deflate {

    // Config class moved to Config.kt

    // Use the companion object's config_table
    // private lateinit var config_table: Array<Config> // This was the issue

    internal lateinit var strm: ZStream
    internal var status: Int = 0
    internal lateinit var pendingBuf: ByteArray
    internal var pendingBufSize: Int = 0
    internal var pendingOut: Int = 0
    internal var pending: Int = 0
    internal var noheader: Int = 0
    internal var dataType: Byte = 0
    internal var method: Byte = 0
    internal var lastFlush: Int = 0

    internal var wSize: Int = 0
    internal var wBits: Int = 0
    internal var wMask: Int = 0

    internal lateinit var window: ByteArray
    internal var windowSize: Int = 0
    internal lateinit var prev: ShortArray
    internal lateinit var head: ShortArray

    internal var insH: Int = 0
    internal var hashSize: Int = 0
    internal var hashBits: Int = 0
    internal var hashMask: Int = 0
    internal var hashShift: Int = 0
    internal var blockStart: Int = 0
    internal var matchLength: Int = 0
    internal var prevMatch: Int = 0
    internal var matchAvailable: Int = 0
    internal var strStart: Int = 0
    internal var matchStart: Int = 0
    internal var lookAhead: Int = 0
    internal var prevLength: Int = 0
    internal var maxChainLength: Int = 0
    internal var maxLazyMatch: Int = 0
    internal var level: Int = 0
    internal var strategy: Int = 0
    internal var goodMatch: Int = 0
    internal var niceMatch: Int = 0

    internal var dynLtree: ShortArray = ShortArray(HEAP_SIZE * 2)
    internal var dynDtree: ShortArray = ShortArray((2 * D_CODES + 1) * 2)
    internal var blTree: ShortArray = ShortArray((2 * BL_CODES + 1) * 2)

    internal var lDesc = Tree()
    internal var dDesc = Tree()
    internal var blDesc = Tree()

    internal var blCount = ShortArray(MAX_BITS + 1)
    internal var heap = IntArray(2 * L_CODES + 1)
    internal var heapLen: Int = 0
    internal var heapMax: Int = 0
    internal var depth = ByteArray(2 * L_CODES + 1)
    internal var lBuf: Int = 0
    internal var litBufsize: Int = 0
    internal var lastLit: Int = 0
    internal var dBuf: Int = 0
    internal var optLen: Long = 0
    internal var staticLen: Long = 0
    internal var matches: Int = 0
    internal var lastEobLen: Int = 0
    internal var biBuf: Short = 0
    internal var biValid: Int = 0

    internal fun lmInit() {
        windowSize = 2 * wSize
        head[hashSize - 1] = 0
        for (i in 0 until hashSize - 1) {
            head[i] = 0
        }
        maxLazyMatch = config_table[level].maxLazy
        goodMatch = config_table[level].goodLength
        niceMatch = config_table[level].niceLength
        maxChainLength = config_table[level].maxChain

        strStart = 0
        blockStart = 0
        lookAhead = 0
        matchLength = MIN_MATCH - 1
        prevLength = matchLength
        matchAvailable = 0
        insH = 0
    }

    internal fun trInit() {
        lDesc.dynTree = dynLtree
        lDesc.statDesc = StaticTree.static_l_desc

        dDesc.dynTree = dynDtree
        dDesc.statDesc = StaticTree.static_d_desc

        blDesc.dynTree = blTree
        blDesc.statDesc = StaticTree.static_bl_desc

        biBuf = 0
        biValid = 0
        lastEobLen = 8
        initBlock()
    }

    internal fun initBlock() {
        for (i in 0 until L_CODES) dynLtree[i * 2] = 0
        for (i in 0 until D_CODES) dynDtree[i * 2] = 0
        for (i in 0 until BL_CODES) blTree[i * 2] = 0

        dynLtree[END_BLOCK * 2] = 1
        optLen = 0L
        staticLen = 0L
        lastLit = 0
        matches = 0
    }

    internal fun pqdownheap(tree: ShortArray, kIn: Int) {
        var k = kIn
        val v = heap[k]
        var j = k shl 1
        while (j <= heapLen) {
            if (j < heapLen && smaller(tree, heap[j + 1], heap[j], depth)) {
                j++
            }
            if (smaller(tree, v, heap[j], depth)) break
            heap[k] = heap[j]
            k = j
            j = j shl 1
        }
        heap[k] = v
    }

    internal fun scanTree(tree: ShortArray, maxCode: Int) {
        var prevlen = -1
        var curlen: Int
        var nextlen = tree[0 * 2 + 1].toInt()
        var count = 0
        var maxCount = 7
        var minCount = 4

        if (nextlen == 0) {
            maxCount = 138
            minCount = 3
        }
        tree[(maxCode + 1) * 2 + 1] = 0xffff.toShort()

        var n = 0
        while(n <= maxCode) {
            curlen = nextlen
            nextlen = tree[(n + 1) * 2 + 1].toInt()
            if (++count < maxCount && curlen == nextlen) {
                // continue
            } else if (count < minCount) {
                blTree[curlen * 2] = (blTree[curlen * 2] + count).toShort()
            } else if (curlen != 0) {
                if (curlen != prevlen) blTree[curlen * 2]++
                blTree[REP_3_6 * 2]++
            } else if (count <= 10) {
                blTree[REPZ_3_10 * 2]++
            } else {
                blTree[REPZ_11_138 * 2]++
            }
            count = 0
            prevlen = curlen
            if (nextlen == 0) {
                maxCount = 138
                minCount = 3
            } else if (curlen == nextlen) {
                maxCount = 6
                minCount = 3
            } else {
                maxCount = 7
                minCount = 4
            }
            n++
        }
    }

    internal fun buildBlTree(): Int {
        scanTree(dynLtree, lDesc.maxCode)
        scanTree(dynDtree, dDesc.maxCode)
        blDesc.buildTree(this)
        var maxBlindex: Int = BL_CODES - 1
        while(maxBlindex >= 3) {
            if (blTree[TREE_BL_ORDER[maxBlindex] * 2 + 1].toInt() != 0) break
            maxBlindex--
        }
        optLen += 3 * (maxBlindex + 1) + 5 + 5 + 4
        return maxBlindex
    }

    internal fun sendAllTrees(lcodes: Int, dcodes: Int, blcodes: Int) {
        sendBits(this, lcodes - 257, 5)
        sendBits(this, dcodes - 1, 5)
        sendBits(this, blcodes - 4, 4)
        var rank = 0
        while(rank < blcodes) {
            sendBits(this, blTree[TREE_BL_ORDER[rank] * 2 + 1].toInt(), 3)
            rank++
        }
        sendTree(dynLtree, lcodes - 1)
        sendTree(dynDtree, dcodes - 1)
    }

    internal fun sendTree(tree: ShortArray, maxCode: Int) {
        var prevlen = -1
        var curlen: Int
        var nextlen = tree[0 * 2 + 1].toInt()
        var count = 0
        var maxCount = 7
        var minCount = 4

        if (nextlen == 0) {
            maxCount = 138
            minCount = 3
        }
        var n = 0
        while(n <= maxCode) {
            curlen = nextlen
            nextlen = tree[(n + 1) * 2 + 1].toInt()
            if (++count < maxCount && curlen == nextlen) {
                // continue
            } else if (count < minCount) {
                do {
                    sendCode(this, curlen, blTree)
                } while (--count != 0)
            } else if (curlen != 0) {
                if (curlen != prevlen) {
                    sendCode(this, curlen, blTree)
                    count--
                }
                sendCode(this, REP_3_6, blTree)
                sendBits(this, count - 3, 2)
            } else if (count <= 10) {
                sendCode(this, REPZ_3_10, blTree)
                sendBits(this, count - 3, 3)
            } else {
                sendCode(this, REPZ_11_138, blTree)
                sendBits(this, count - 11, 7)
            }
            count = 0
            prevlen = curlen
            if (nextlen == 0) {
                maxCount = 138
                minCount = 3
            } else if (curlen == nextlen) {
                maxCount = 6
                minCount = 3
            } else {
                maxCount = 7
                minCount = 4
            }
            n++
        }
    }

    internal fun trTally(dist: Int, lc: Int): Boolean {
        pendingBuf[dBuf + lastLit * 2] = (dist ushr 8).toByte()
        pendingBuf[dBuf + lastLit * 2 + 1] = dist.toByte()
        pendingBuf[lBuf + lastLit] = lc.toByte()
        lastLit++

        if (dist == 0) {
            dynLtree[lc * 2]++
        } else {
            matches++
            val distVal = dist - 1
            dynLtree[(TREE_LENGTH_CODE[lc].toInt() + LITERALS + 1) * 2]++
            dynDtree[dCode(distVal) * 2]++
        }

        if ((lastLit and 0x1fff) == 0 && level > 2) {
            var outLength = (lastLit * 8).toLong()
            val inLength = strStart - blockStart
            for (dcodeVal in 0 until D_CODES) {
                outLength += dynDtree[dcodeVal * 2] * (5L + TREE_EXTRA_DBITS[dcodeVal])
            }
            outLength = outLength ushr 3
            if (matches < lastLit / 2 && outLength < inLength / 2) return true
        }
        return lastLit == litBufsize - 1
    }

    internal fun flushBlockOnly(eof: Boolean) {
        trFlushBlock(if (blockStart >= 0) blockStart else -1, strStart - blockStart, eof)
        blockStart = strStart
        strm.flushPending()
    }

    internal fun deflateStored(flush: Int): Int {
        var maxBlockSize = 0xffff
        var maxStart: Int

        if (maxBlockSize > pendingBufSize - 5) {
            maxBlockSize = pendingBufSize - 5
        }

        while (true) {
            if (lookAhead <= 1) {
                fillWindow()
                if (lookAhead == 0 && flush == Z_NO_FLUSH) return NEED_MORE
                if (lookAhead == 0) break
            }
            strStart += lookAhead
            lookAhead = 0
            maxStart = blockStart + maxBlockSize
            if (strStart == 0 || strStart >= maxStart) {
                lookAhead = (strStart - maxStart)
                strStart = maxStart
                flushBlockOnly(false)
                if (strm.availOut == 0) return NEED_MORE
            }
            if (strStart - blockStart >= wSize - MIN_LOOKAHEAD) {
                flushBlockOnly(false)
                if (strm.availOut == 0) return NEED_MORE
            }
        }
        flushBlockOnly(flush == Z_FINISH)
        if (strm.availOut == 0) return if (flush == Z_FINISH) FINISH_STARTED else NEED_MORE
        return if (flush == Z_FINISH) FINISH_DONE else BLOCK_DONE
    }

    internal fun trFlushBlock(buf: Int, storedLen: Int, eof: Boolean) {
        var optLenb: Long
        var staticLenb: Long
        var maxBlindex = 0

        if (level > 0) {
            if (dataType == Z_UNKNOWN.toByte()) setDataType(this)
            lDesc.buildTree(this)
            dDesc.buildTree(this)
            maxBlindex = buildBlTree()
            optLenb = (optLen + 3 + 7) ushr 3
            staticLenb = (staticLen + 3 + 7) ushr 3
            if (staticLenb <= optLenb) optLenb = staticLenb
        } else {
            optLenb = (storedLen + 5).toLong()
            staticLenb = optLenb
        }

        if (storedLen + 4 <= optLenb && buf != -1) {
            trStoredBlock(this, buf, storedLen, eof)
        } else if (staticLenb == optLenb) {
            sendBits(this, (STATIC_TREES shl 1) + if (eof) 1 else 0, 3)
            compressBlock(this, StaticTree.static_ltree, StaticTree.static_dtree)
        } else {
            sendBits(this, (DYN_TREES shl 1) + if (eof) 1 else 0, 3)
            sendAllTrees(lDesc.maxCode + 1, dDesc.maxCode + 1, maxBlindex + 1)
            compressBlock(this, dynLtree, dynDtree)
        }
        initBlock()
        if (eof) {
            biWindup(this)
        }
    }

    internal fun fillWindow() {
        var n: Int
        var m: Int
        var p: Int
        var more: Int
        do {
            more = windowSize - lookAhead - strStart
            if (more == 0 && strStart == 0 && lookAhead == 0) {
                more = wSize
            } else if (more == -1) {
                more--
            } else if (strStart >= wSize + wSize - MIN_LOOKAHEAD) {
                window.copyInto(window, 0, wSize, wSize)
                matchStart -= wSize
                strStart -= wSize
                blockStart -= wSize
                n = hashSize
                p = n
                do {
                    m = (head[--p].toInt() and 0xffff)
                    head[p] = if (m >= wSize) (m - wSize).toShort() else 0.toShort()
                } while (--n != 0)
                n = wSize
                p = n
                do {
                    m = (prev[--p].toInt() and 0xffff)
                    prev[p] = if (m >= wSize) (m - wSize).toShort() else 0.toShort()
                } while (--n != 0)
                more += wSize
            }
            if (strm.availIn == 0) return
            n = strm.readBuf(window, strStart + lookAhead, more)
            lookAhead += n
            if (lookAhead >= MIN_MATCH) {
                insH = window[strStart].toInt() and 0xff
                insH = (((insH shl hashShift) xor (window[strStart + 1].toInt() and 0xff)) and hashMask)
            }
        } while (lookAhead < MIN_LOOKAHEAD && strm.availIn != 0)
    }

    internal fun deflateFast(flush: Int): Int {
        var hashHead = 0
        var bflush: Boolean
        while (true) {
            if (lookAhead < MIN_LOOKAHEAD) {
                fillWindow()
                if (lookAhead < MIN_LOOKAHEAD && flush == Z_NO_FLUSH) return NEED_MORE
                if (lookAhead == 0) break
            }
            if (lookAhead >= MIN_MATCH) {
                insH = (((insH shl hashShift) xor (window[(strStart) + (MIN_MATCH - 1)].toInt() and 0xff)) and hashMask)
                hashHead = (head[insH].toInt() and 0xffff)
                prev[strStart and wMask] = head[insH]
                head[insH] = strStart.toShort()
            }
            if (hashHead != 0 && ((strStart - hashHead) and 0xffff) <= wSize - MIN_LOOKAHEAD) {
                if (strategy != Z_HUFFMAN_ONLY) {
                    matchLength = longestMatch(hashHead)
                }
            }
            if (matchLength >= MIN_MATCH) {
                bflush = trTally(strStart - matchStart, matchLength - MIN_MATCH)
                lookAhead -= matchLength
                if (matchLength <= maxLazyMatch && lookAhead >= MIN_MATCH) {
                    matchLength--
                    do {
                        strStart++
                        insH = (((insH shl hashShift) xor (window[(strStart) + (MIN_MATCH - 1)].toInt() and 0xff)) and hashMask)
                        hashHead = (head[insH].toInt() and 0xffff)
                        prev[strStart and wMask] = head[insH]
                        head[insH] = strStart.toShort()
                    } while (--matchLength != 0)
                    strStart++
                } else {
                    strStart += matchLength
                    matchLength = 0
                    insH = window[strStart].toInt() and 0xff
                    insH = (((insH shl hashShift) xor (window[strStart + 1].toInt() and 0xff)) and hashMask)
                }
            } else {
                bflush = trTally(0, window[strStart].toInt() and 0xff)
                lookAhead--
                strStart++
            }
            if (bflush) {
                flushBlockOnly(false)
                if (strm.availOut == 0) return NEED_MORE
            }
        }
        flushBlockOnly(flush == Z_FINISH)
        if (strm.availOut == 0) {
            return if (flush == Z_FINISH) FINISH_STARTED else NEED_MORE
        }
        return if (flush == Z_FINISH) FINISH_DONE else BLOCK_DONE
    }

    internal fun deflateSlow(flush: Int): Int {
        var hashHead = 0
        var bflush: Boolean
        while (true) {
            if (lookAhead < MIN_LOOKAHEAD) {
                fillWindow()
                if (lookAhead < MIN_LOOKAHEAD && flush == Z_NO_FLUSH) return NEED_MORE
                if (lookAhead == 0) break
            }
            if (lookAhead >= MIN_MATCH) {
                insH = (((insH shl hashShift) xor (window[(strStart) + (MIN_MATCH - 1)].toInt() and 0xff)) and hashMask)
                hashHead = (head[insH].toInt() and 0xffff)
                prev[strStart and wMask] = head[insH]
                head[insH] = strStart.toShort()
            }
            prevLength = matchLength
            prevMatch = matchStart
            matchLength = MIN_MATCH - 1
            if (hashHead != 0 && prevLength < maxLazyMatch && ((strStart - hashHead) and 0xffff) <= wSize - MIN_LOOKAHEAD) {
                if (strategy != Z_HUFFMAN_ONLY) {
                    matchLength = longestMatch(hashHead)
                }
                if (matchLength <= 5 && (strategy == Z_FILTERED || (matchLength == MIN_MATCH && strStart - matchStart > 4096))) {
                    matchLength = MIN_MATCH - 1
                }
            }
            if (prevLength >= MIN_MATCH && matchLength <= prevLength) {
                val maxInsert = strStart + lookAhead - MIN_MATCH
                bflush = trTally(strStart - 1 - prevMatch, prevLength - MIN_MATCH)
                lookAhead -= prevLength - 1
                prevLength -= 2
                do {
                    if (++strStart <= maxInsert) {
                        insH = (((insH shl hashShift) xor (window[(strStart) + (MIN_MATCH - 1)].toInt() and 0xff)) and hashMask)
                        hashHead = (head[insH].toInt() and 0xffff)
                        prev[strStart and wMask] = head[insH]
                        head[insH] = strStart.toShort()
                    }
                } while (--prevLength != 0)
                matchAvailable = 0
                matchLength = MIN_MATCH - 1
                strStart++
                if (bflush) {
                    flushBlockOnly(false)
                    if (strm.availOut == 0) return NEED_MORE
                }
            } else if (matchAvailable != 0) {
                bflush = trTally(0, window[strStart - 1].toInt() and 0xff)
                if (bflush) {
                    flushBlockOnly(false)
                }
                strStart++
                lookAhead--
                if (strm.availOut == 0) return NEED_MORE
            } else {
                matchAvailable = 1
                strStart++
                lookAhead--
            }
        }
        if (matchAvailable != 0) {
            trTally(0, window[strStart - 1].toInt() and 0xff)
            matchAvailable = 0
        }
        flushBlockOnly(flush == Z_FINISH)
        if (strm.availOut == 0) {
            return if (flush == Z_FINISH) FINISH_STARTED else NEED_MORE
        }
        return if (flush == Z_FINISH) FINISH_DONE else BLOCK_DONE
    }

    internal fun longestMatch(curMatchIn: Int): Int {
        var curMatch = curMatchIn
        var chainLength = maxChainLength
        var scan = strStart
        var match: Int
        var len: Int
        var bestLen = prevLength
        val limit = if (strStart > wSize - MIN_LOOKAHEAD) strStart - (wSize - MIN_LOOKAHEAD) else 0
        var localNiceMatch = niceMatch
        val wmask = wMask
        val strend = strStart + MAX_MATCH
        var scanEnd1 = window[scan + bestLen - 1]
        var scanEnd = window[scan + bestLen]

        if (prevLength >= goodMatch) {
            chainLength = chainLength shr 2
        }
        if (localNiceMatch > lookAhead) {
            localNiceMatch = lookAhead
        }

        do {
            match = curMatch
            if (window[match + bestLen] != scanEnd || window[match + bestLen - 1] != scanEnd1 || window[match] != window[scan] || window[++match] != window[scan + 1]) {
                curMatch = prev[curMatch and wmask].toInt() and 0xffff
                continue
            }
            scan += 2
            match++
            while (scan < strend && window[scan] == window[match]) {
                scan++
                match++
            }
            len = scan - strStart
            scan = strStart

            if (len > bestLen) {
                matchStart = curMatchIn
                bestLen = len
                if (len >= localNiceMatch) break
                scanEnd1 = window[scan + bestLen - 1]
                scanEnd = window[scan + bestLen]
            }
            curMatch = prev[curMatch and wmask].toInt() and 0xffff
        } while (curMatch > limit && --chainLength != 0)
        return if (bestLen <= lookAhead) bestLen else lookAhead
    }

    internal fun deflateInit(strm: ZStream, level: Int, bits: Int): Int {
        return deflateInit2(strm, level, Z_DEFLATED, bits, DEF_MEM_LEVEL, Z_DEFAULT_STRATEGY)
    }

    internal fun deflateInit(strm: ZStream, level: Int): Int {
        return deflateInit(strm, level, MAX_WBITS)
    }

    internal fun deflateReset(strm: ZStream): Int {
        strm.totalIn = 0
        strm.totalOut = 0
        strm.msg = null
        strm.dataType = Z_UNKNOWN
        pending = 0
        pendingOut = 0
        if (noheader < 0) {
            noheader = 0
        }
        status = if (noheader != 0) BUSY_STATE else INIT_STATE
        strm.adler = strm.adlerChecksum!!.adler32(0, null, 0, 0)
        lastFlush = Z_NO_FLUSH
        trInit()
        lmInit()
        return Z_OK
    }

    internal fun deflateEnd(): Int {
        if (status != INIT_STATE && status != BUSY_STATE && status != FINISH_STATE) {
            return Z_STREAM_ERROR
        }
        pendingBuf = ByteArray(0)
        head = ShortArray(0)
        prev = ShortArray(0)
        window = ByteArray(0)
        return if (status == BUSY_STATE) Z_DATA_ERROR else Z_OK
    }

    internal fun deflateParams(strm: ZStream, levelIn: Int, strategyIn: Int): Int {
        var currentLevel = levelIn
        var err = Z_OK
        if (currentLevel == Z_DEFAULT_COMPRESSION) {
            currentLevel = 6
        }
        if (currentLevel < 0 || currentLevel > 9 || strategyIn < 0 || strategyIn > Z_HUFFMAN_ONLY) {
            return Z_STREAM_ERROR
        }
        if (config_table[this.level].func != config_table[currentLevel].func && strm.totalIn != 0L) {
            err = strm.deflate(Z_PARTIAL_FLUSH)
        }
        if (this.level != currentLevel) {
            this.level = currentLevel
            maxLazyMatch = config_table[this.level].maxLazy
            goodMatch = config_table[this.level].goodLength
            niceMatch = config_table[this.level].niceLength
            maxChainLength = config_table[this.level].maxChain
        }
        this.strategy = strategyIn
        return err
    }

    internal fun deflateSetDictionary(strm: ZStream, dictionary: ByteArray, dictLength: Int): Int {
        var length = dictLength
        var index = 0
        if (status != INIT_STATE) return Z_STREAM_ERROR
        strm.adler = strm.adlerChecksum!!.adler32(strm.adler, dictionary, 0, dictLength)
        if (length < MIN_MATCH) return Z_OK
        if (length > wSize - MIN_LOOKAHEAD) {
            length = wSize - MIN_LOOKAHEAD
            index = dictLength - length
        }
        dictionary.copyInto(window, 0, index, length)
        strStart = length
        blockStart = length
        insH = window[0].toInt() and 0xff
        insH = (((insH shl hashShift) xor (window[1].toInt() and 0xff)) and hashMask)
        for (n in 0..length - MIN_MATCH) {
            insH = (((insH shl hashShift) xor (window[(n) + (MIN_MATCH - 1)].toInt() and 0xff)) and hashMask)
            prev[n and wMask] = head[insH]
            head[insH] = n.toShort()
        }
        return Z_OK
    }

    internal fun deflateInit2(strm: ZStream, levelParam: Int, methodParam: Int, windowbitsParam: Int, memlevelParam: Int, strategyParam: Int): Int {
        var levelVal = levelParam
        val methodVal = methodParam
        var windowbitsVal = windowbitsParam
        val memlevelVal = memlevelParam
        val strategyVal = strategyParam

        var noheaderLocal = 0
        strm.msg = null
        if (levelVal == Z_DEFAULT_COMPRESSION) levelVal = 6
        if (windowbitsVal < 0) {
            noheaderLocal = 1
            windowbitsVal = -windowbitsVal
        }
        if (memlevelVal < 1 || memlevelVal > MAX_MEM_LEVEL || methodVal != Z_DEFLATED || windowbitsVal < 9 || windowbitsVal > 15 || levelVal < 0 || levelVal > 9 || strategyVal < 0 || strategyVal > Z_HUFFMAN_ONLY) {
            return Z_STREAM_ERROR
        }
        strm.dState = this
        this.noheader = noheaderLocal
        wBits = windowbitsVal
        wSize = 1 shl wBits
        wMask = wSize - 1
        hashBits = memlevelVal + 7
        hashSize = 1 shl hashBits
        hashMask = hashSize - 1
        hashShift = (hashBits + MIN_MATCH - 1) / MIN_MATCH
        window = ByteArray(wSize * 2)
        prev = ShortArray(wSize)
        head = ShortArray(hashSize)
        litBufsize = 1 shl (memlevelVal + 6)
        pendingBuf = ByteArray(litBufsize * 4)
        pendingBufSize = litBufsize * 4
        dBuf = litBufsize
        lBuf = (1 + 2) * litBufsize
        this.level = levelVal
        this.strategy = strategyVal
        this.method = methodVal.toByte()
        return deflateReset(strm)
    }

    internal fun deflate(strm: ZStream, flush: Int): Int {
        if (flush > Z_FINISH || flush < 0) {
            return Z_STREAM_ERROR
        }
        if (strm.nextOut == null || (strm.nextIn == null && strm.availIn != 0) || (status == FINISH_STATE && flush != Z_FINISH)) {
            strm.msg = Z_ERRMSG[Z_NEED_DICT - (Z_STREAM_ERROR)]
            return Z_STREAM_ERROR
        }
        if (strm.availOut == 0) {
            strm.msg = Z_ERRMSG[Z_NEED_DICT - (Z_BUF_ERROR)]
            return Z_BUF_ERROR
        }
        this.strm = strm
        val oldFlush: Int = lastFlush
        lastFlush = flush
        if (status == INIT_STATE) {
            var header: Int = (Z_DEFLATED + ((wBits - 8) shl 4)) shl 8
            var levelFlagsLocal: Int = ((this.level - 1) and 0xff) shr 1
            if (levelFlagsLocal > 3) levelFlagsLocal = 3
            header = header or (levelFlagsLocal shl 6)
            if (strStart != 0) header = header or PRESET_DICT
            header += 31 - (header % 31)
            status = BUSY_STATE
            putShortMSB(this, header)
            if (strStart != 0) {
                putShortMSB(this, (strm.adler ushr 16).toInt())
                putShortMSB(this, (strm.adler and 0xffff).toInt())
            }
            strm.adler = strm.adlerChecksum!!.adler32(0, null, 0, 0)
        }
        if (pending != 0) {
            strm.flushPending()
            if (strm.availOut == 0) {
                lastFlush = -1
                return Z_OK
            }
        } else if (strm.availIn == 0 && flush <= oldFlush && flush != Z_FINISH) {
            strm.msg = Z_ERRMSG[Z_NEED_DICT - (Z_BUF_ERROR)]
            return Z_BUF_ERROR
        }
        if (status == FINISH_STATE && strm.availIn != 0) {
            strm.msg = Z_ERRMSG[Z_NEED_DICT - (Z_BUF_ERROR)]
            return Z_BUF_ERROR
        }
        if (strm.availIn != 0 || lookAhead != 0 || (flush != Z_NO_FLUSH && status != FINISH_STATE)) {
            var bstate = -1
            when (config_table[level].func) {
                STORED -> bstate = deflateStored(flush)
                FAST -> bstate = deflateFast(flush)
                SLOW -> bstate = deflateSlow(flush)
                else -> { }
            }
            if (bstate == FINISH_STARTED || bstate == FINISH_DONE) {
                status = FINISH_STATE
            }
            if (bstate == NEED_MORE || bstate == FINISH_STARTED) {
                if (strm.availOut == 0) {
                    lastFlush = -1
                }
                return Z_OK
            }
            if (bstate == BLOCK_DONE) {
                if (flush == Z_PARTIAL_FLUSH) {
                    trAlign(this)
                } else {
                    trStoredBlock(this, 0, 0, false)
                    if (flush == Z_FULL_FLUSH) {
                        for (i in 0 until hashSize) head[i] = 0
                    }
                }
                strm.flushPending()
                if (strm.availOut == 0) {
                    lastFlush = -1
                    return Z_OK
                }
            }
        }
        if (flush != Z_FINISH) return Z_OK
        if (noheader != 0) return Z_STREAM_END
        putShortMSB(this, (strm.adler ushr 16).toInt())
        putShortMSB(this, (strm.adler and 0xffff).toInt())
        strm.flushPending()
        noheader = -1
        return if (pending != 0) Z_OK else Z_STREAM_END
    }

    companion object {
        private const val STORED = 0
        private const val FAST = 1
        private const val SLOW = 2
        private val config_table: Array<Config> = arrayOf(
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

        // smaller function moved to DeflateUtils.kt
    }
}
