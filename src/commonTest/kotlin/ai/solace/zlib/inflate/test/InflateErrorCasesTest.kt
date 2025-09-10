package ai.solace.zlib.inflate.test

import ai.solace.zlib.common.Z_DATA_ERROR
import ai.solace.zlib.inflate.InflateStream
import ai.solace.zlib.inflate.StreamingBitWriter
import ai.solace.zlib.inflate.CanonicalHuffman
import kotlin.test.Test
import kotlin.test.assertEquals
import okio.Buffer

class InflateErrorCasesTest {
    /**
     * Craft a zlib stream with a single fixed-Huffman block that starts with a length/distance pair
     * before any literal output. This forces a "distance too far back" error because no bytes have
     * been written to the sliding window yet (available == 0 but dist == 1).
     */
    @Test
    fun distanceTooFarBack_returnsZDataError() {
        // Prepare sink buffer to hold our synthetic zlib stream
        val z = Buffer()

        // Write a valid zlib header: 0x78, 0x9C (CMF/FLG with FCHECK correct)
        z.writeByte(0x78)
        z.writeByte(0x9C)

        // We'll write a single fixed-Huffman DEFLATE block using our bit writer
        val bw = StreamingBitWriter(z)

        // BFINAL=1, BTYPE=01 (fixed Huffman)
        bw.writeBits(1, 1)
        bw.writeBits(1, 2)

        // Build fixed encoders (same rules as RFC1951)
        val litLenLens = IntArray(288)
        for (i in 0..143) litLenLens[i] = 8
        for (i in 144..255) litLenLens[i] = 9
        litLenLens[256] = 7
        for (i in 257..279) litLenLens[i] = 7
        for (i in 280..287) litLenLens[i] = 8
        val (litCodes, litBits) = CanonicalHuffman.buildEncoder(litLenLens)

        val distLens = IntArray(32) { 5 }
        val (distCodes, distBits) = CanonicalHuffman.buildEncoder(distLens)

        // Emit a length symbol first (257 => length 3, no extra bits)
        val lengthSym = 257 // maps to length 3
        bw.writeBits(litCodes[lengthSym], litBits[lengthSym])
        // Emit a distance of 1 (symbol 0 => base 1, no extra bits)
        val distSym = 0
        bw.writeBits(distCodes[distSym], distBits[distSym])

        // We don't need to emit EOB; the inflater should error during the copy operation
        bw.alignToByte()
        bw.flush()

        // Attempt to inflate; expect a Z_DATA_ERROR (distance too far back)
        val out = Buffer()
        val (rc, _) = InflateStream.inflateZlib(z, out)
        assertEquals(Z_DATA_ERROR, rc, "Expected Z_DATA_ERROR for distance-too-far-back condition")
    }
}
