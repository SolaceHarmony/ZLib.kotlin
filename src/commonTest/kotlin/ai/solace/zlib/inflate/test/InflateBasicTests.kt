package ai.solace.zlib.inflate.test

import ai.solace.zlib.common.Z_BUF_ERROR
import ai.solace.zlib.common.Z_STREAM_END
import ai.solace.zlib.deflate.DeflateStream
import ai.solace.zlib.inflate.InflateStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import okio.Buffer

class InflateBasicTests {
    @Test
    fun inflate_returnsZStreamEnd_andRestoresOriginal() {
        val original = "hello world"
        val src = Buffer().writeUtf8(original)
        val compressed = Buffer()

        // Compress using default level
        val bytesIn = DeflateStream.compressZlib(src, compressed)
        assertEquals(original.length.toLong(), bytesIn)

        // Now inflate
        val out = Buffer()
        val (rc, bytesOut) = InflateStream.inflateZlib(compressed, out)
        assertEquals(Z_STREAM_END, rc)
        assertEquals(original.length.toLong(), bytesOut)
        assertEquals(original, out.readUtf8())
    }

    @Test
    fun inflate_truncated_after_header_returnsZBufError() {
        // Create a minimal valid zlib stream by compressing empty input
        val empty = Buffer()
        val full = Buffer()
        DeflateStream.compressZlib(empty, full)
        // Truncate to just the header (2 bytes)
        val truncated = Buffer()
        val headerByte1 = full.readByte()
        val headerByte2 = full.readByte()
        truncated.writeByte(headerByte1.toInt())
        truncated.writeByte(headerByte2.toInt())

        val out = Buffer()
        val (rc, _) = InflateStream.inflateZlib(truncated, out)
        assertEquals(Z_BUF_ERROR, rc, "Expected Z_BUF_ERROR for truncated stream after header")
        assertTrue(out.size == 0L)
    }
}
