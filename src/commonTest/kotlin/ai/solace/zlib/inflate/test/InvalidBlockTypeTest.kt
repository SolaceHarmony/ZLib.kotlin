package ai.solace.zlib.inflate.test

import ai.solace.zlib.common.Z_DATA_ERROR
import ai.solace.zlib.inflate.InflateStream
import ai.solace.zlib.inflate.StreamingBitWriter
import kotlin.test.Test
import kotlin.test.assertEquals
import okio.Buffer

class InvalidBlockTypeTest {
    @Test
    fun reservedBlockType_returnsZDataError() {
        val z = Buffer()
        // Valid zlib header
        z.writeByte(0x78)
        z.writeByte(0x9C)

        val bw = StreamingBitWriter(z)
        // BFINAL=1
        bw.writeBits(1, 1)
        // BTYPE=11 (reserved/invalid)
        bw.writeBits(0b11, 2)
        bw.alignToByte()
        bw.flush()

        val out = Buffer()
        val (rc, _) = InflateStream.inflateZlib(z, out)
        assertEquals(Z_DATA_ERROR, rc, "Expected Z_DATA_ERROR for reserved BTYPE=11 block")
    }
}
