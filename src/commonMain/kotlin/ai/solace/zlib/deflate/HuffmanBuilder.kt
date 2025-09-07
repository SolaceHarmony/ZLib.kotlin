package ai.solace.zlib.deflate

/**
 * Length-limited code-length generator using a Shannon–Fano style partitioning.
 * Produces valid prefix code lengths (<= maxBits) for canonical coding.
 * Not strictly optimal like package-merge, but RFC-compliant and deterministic.
 */
object HuffmanBuilder {
    /** Build code lengths for [freq] with maximum length [maxBits]. */
    fun buildLengths(
        freq: IntArray,
        maxBits: Int,
        ensureSymbol: Int? = null,
    ): IntArray {
        val n = freq.size
        val totalFreq = freq.sum()
        val out = IntArray(n)

        // Ensure at least one symbol if required or if all zero
        if (totalFreq == 0) {
            if (ensureSymbol != null && ensureSymbol in 0 until n) {
                out[ensureSymbol] = 1
            }
            return out
        }

        data class Item(val sym: Int, val f: Long)

        // Collect used symbols
        val items = ArrayList<Item>()
        for (i in 0 until n) if (freq[i] > 0) items.add(Item(i, freq[i].toLong()))

        // If only one symbol, assign length 1
        if (items.size == 1) {
            out[items[0].sym] = 1
            return out
        }

        // Sort by descending frequency, tie by symbol id for determinism
        items.sortWith(compareByDescending<Item> { it.f }.thenBy { it.sym })

        val fprefix = LongArray(items.size + 1)
        for (i in items.indices) fprefix[i + 1] = fprefix[i] + items[i].f

        fun assign(
            start: Int,
            end: Int,
            depth: Int,
        ) {
            if (start >= end) return
            if (end - start == 1) {
                out[items[start].sym] = maxOf(1, depth)
                return
            }
            if (depth >= maxBits) {
                // Assign all remaining at maxBits; with our N constraints, this remains valid.
                for (i in start until end) out[items[i].sym] = maxBits
                return
            }
            val total = fprefix[end] - fprefix[start]
            val target = total / 2
            var split = start + 1
            var bestDelta = Long.MAX_VALUE
            var i = start + 1
            while (i < end) {
                val left = fprefix[i] - fprefix[start]
                val delta = kotlin.math.abs(left - target)
                if (delta < bestDelta) {
                    bestDelta = delta
                    split = i
                }
                i++
            }
            assign(start, split, depth + 1)
            assign(split, end, depth + 1)
        }

        assign(0, items.size, 0)
        return out
    }
}
