package ai.solace.zlib.deflate.test

import ai.solace.zlib.common.Z_OK
import ai.solace.zlib.common.Z_STREAM_ERROR
import ai.solace.zlib.deflate.DeflateStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import okio.Buffer

class DeflateParameterValidationTest {
    @Test
    fun levelAboveMax_returnsStreamError_andWritesNothing() {
        val src = Buffer() // empty input
        val snk = Buffer()

        val (rc, bytesIn) = DeflateStream.compressZlibResult(src, snk, level = 10)
        assertEquals(Z_STREAM_ERROR, rc, "Expected Z_STREAM_ERROR for invalid level > 9")
        assertEquals(0L, bytesIn, "Bytes in should be 0 when validation fails")
        assertEquals(0L, snk.size, "Sink should remain empty on parameter validation failure")
    }

    @Test
    fun levelNine_ok_forEmptyInput_writesHeaderAndTrailer() {
        val src = Buffer() // empty input
        val snk = Buffer()

        val (rc, bytesIn) = DeflateStream.compressZlibResult(src, snk, level = 9)
        assertEquals(Z_OK, rc)
        assertEquals(0L, bytesIn)
        // Zlib header is 2 bytes, Adler32 trailer is 4 bytes -> at least 6 bytes written
        assertTrue(snk.size >= 6L, "Expected at least zlib header+trailer written for empty input")
    }

    @Test
    fun negativeLevel_treatedAsStored_ok() {
        val src = Buffer().writeUtf8("")
        val snk = Buffer()

        val (rc, bytesIn) = DeflateStream.compressZlibResult(src, snk, level = -1)
        assertEquals(Z_OK, rc)
        assertEquals(0L, bytesIn)
        assertTrue(snk.size >= 6L, "Expected header+trailer for stored mode with empty input")
    }
}
