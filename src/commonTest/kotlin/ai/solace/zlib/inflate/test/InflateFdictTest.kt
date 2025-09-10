package ai.solace.zlib.inflate.test

import ai.solace.zlib.common.Z_NEED_DICT
import ai.solace.zlib.inflate.InflateStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import okio.Buffer

class InflateFdictTest {
    @Test
    fun fdictSet_returnsZNeedDict_andNoOutput() {
        val z = Buffer()

        val cmf = 0x78 // CM=8 (deflate), CINFO=7 (32K window)
        var flg = 0
        val flevel = 2 // advisory; any 0..3 is fine
        val fdictBit = 1 shl 5
        flg = (flevel shl 6) or fdictBit
        val cmfFlg = (cmf shl 8) or flg
        val fcheck = (31 - (cmfFlg % 31)) % 31
        flg = (flg and 0xE0) or fcheck

        // Write header
        z.writeByte(cmf)
        z.writeByte(flg)
        // Write DICTID (4 bytes) as required when FDICT is set
        z.writeByte(0x12)
        z.writeByte(0x34)
        z.writeByte(0x56)
        z.writeByte(0x78)

        val out = Buffer()
        val (rc, bytesOut) = InflateStream.inflateZlib(z, out)
        assertEquals(Z_NEED_DICT, rc, "Expected Z_NEED_DICT when FDICT is set in the zlib header")
        assertEquals(0L, bytesOut, "No output should be produced before dictionary is supplied")
        assertTrue(out.size == 0L)
    }
}
