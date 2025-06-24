package ai.solace.zlib.test

import ai.solace.zlib.common.*
import ai.solace.zlib.deflate.Deflate
import ai.solace.zlib.deflate.*
import ai.solace.zlib.deflate.ZStream
import kotlin.test.*

private val DeflateUtilsTest.PENDING_BUF_SIZE: Int
    get() = 65536

class DeflateUtilsTest {

    private fun createDeflateState(): Deflate {
        val stream = ZStream()
        // Perform a basic init for dstate to be non-null for Deflate instance methods
        // The Deflate(ZStream) constructor is not available.
        // We need to manually set up the Deflate instance as DeflateUtils expect.
        val d = Deflate()
        d.strm = stream // Deflate instance methods might use this
        stream.dState = d // Link back

        d.status = INIT_STATE // A valid initial status (typically 42)
        d.pendingBuf = ByteArray(PENDING_BUF_SIZE) // From Constants.kt or Deflate companion
        d.pending = 0
        d.biBuf = 0
        d.biValid = 0

        // Initialize other potentially accessed lateinit vars or important fields if necessary
        // For the functions being tested (put_short, putShortMSB, send_bits),
        // the above should generally suffice.
        // `send_bits` calls `put_short` which calls `put_byte`.
        // `put_byte` uses `d.pending_buf` and `d.pending`.
        // Deflate's own methods like lm_init, tr_init are not directly called by these utils.
        // However, ensure Deflate.INIT_STATE is a state where pending_buf is expected to be valid.
        // Deflate's constructor initializes dyn_ltree, dyn_dtree, bl_tree.
        // Deflate.deflateInit2 -> deflateReset -> tr_init / lm_init.
        // tr_init initializes l_desc, d_desc, bl_desc.
        // lm_init requires config_table and level.
        // For DeflateUtils, direct interaction with these deeper states is minimal.
        // The companion object of Deflate holds config_table.
        // d.level is typically set in deflateInit. For util tests, not directly needed unless a util func uses it.
        // d.noheader is also set in deflateInit.
        // d.w_bits, d.w_size etc. are set in deflateInit2.

        // For send_bits which can trigger bi_flush, which can call put_short:
        // ZStream needs next_out and avail_out if a full Deflate instance tried to use strm.flush_pending()
        // but DeflateUtils.bi_flush just calls put_short/put_byte on the Deflate instance directly.
        // So, ZStream setup for output isn't strictly needed for these isolated util tests.

        return d
    }

    @Test
    fun testPutShort() {
        val d = createDeflateState()
        d.pending = 0
        val value = 0x1234
        putShort(d, value)
        assertEquals(2, d.pending, "Pending offset should advance by 2")
        assertEquals(0x34.toByte(), d.pendingBuf[0], "Byte 0 (little-endian)")
        assertEquals(0x12.toByte(), d.pendingBuf[1], "Byte 1 (little-endian)")
    }

    @Test
    fun testPutShortMSB() {
        val d = createDeflateState()
        d.pending = 0
        val value = 0x1234
        putShortMSB(d, value)
        assertEquals(2, d.pending, "Pending offset should advance by 2")
        assertEquals(0x12.toByte(), d.pendingBuf[0], "Byte 0 (big-endian)")
        assertEquals(0x34.toByte(), d.pendingBuf[1], "Byte 1 (big-endian)")
    }

    @Test
    fun testSendBits() {
        val d = createDeflateState()
        d.biBuf = 0
        d.biValid = 0

        sendBits(d, 0b101, 3) // value, length
        assertEquals(3, d.biValid)
        assertEquals(0b101, d.biBuf.toInt()) // bi_buf is Short, compare with Int

        sendBits(d, 0b11, 2) // value, length
        assertEquals(5, d.biValid)
        // Expected: existing bi_buf OR (new_value LSL current_bi_valid)
        // 0b101 | (0b11 << 3) = 0b101 | 0b11000 = 0b11101 (29)
        assertEquals(0b11101, d.biBuf.toInt())

        // Test flushing the bit buffer
        // BUF_SIZE is 16 (bits in a Short * 2 / bytes in a Short, or 8 * 2)
        d.biBuf = 0 // Reset
        d.biValid = 0
        d.pending = 0 // Reset pending for checking flushed output

        // Send 16 bits (0xAAAA). This should fill bi_buf but not necessarily flush yet
        // if send_bits logic is bi_valid > BUF_SIZE - len for flush condition.
        // send_bits(value, length)
        // if (d.bi_valid > Deflate.BUF_SIZE - len) -> Deflate.BUF_SIZE is 16
        // 1. send_bits(d, 0xAAAA, 16)
        //    bi_valid = 0, len = 16.  0 > 16 - 16 (false).
        //    d.bi_buf = (0 | (0xAAAA << 0)) = 0xAAAA
        //    d.bi_valid = 16
        sendBits(d, 0xAAAA, 16)
        assertEquals(16, d.biValid)
        assertEquals(0xAAAA, d.biBuf.toInt() and 0xffff) // Masking to handle sign extension
        assertEquals(0, d.pending, "Pending should be 0 before explicit flush condition")

        // 2. send_bits(d, 0x05, 3)
        //    bi_valid = 16, len = 3. 16 > 16 - 3 (16 > 13) (true) -> flush occurs
        //    value_to_put_in_short = d.bi_buf (0xAAAA)
        //    put_short(d, 0xAAAA)
        //    d.pending_buf[0]=AA, d.pending_buf[1]=AA. d.pending=2
        //    d.bi_buf = (0x05 ushr (16 - 16)) = 0x05  -- Error in original reasoning, it's value ushr (BUF_SIZE - d.bi_valid)
        //    d.bi_buf = (0xAAAA ushr (BUF_SIZE - d.bi_valid)) -> this is wrong
        //    The flushed value is d.bi_buf. The new bi_buf is the *new value* shifted.
        //    Correct logic from DeflateUtils.send_bits:
        //    put_short(d, d.bi_buf) -> puts 0xAAAA
        //    d.bi_buf = (value_Renamed ushr (BUF_SIZE - d.bi_valid_old)).toShort() -> (0x05 ushr (16-16)) = 0x05
        //    d.bi_valid = d.bi_valid_old + len - BUF_SIZE = 16 + 3 - 16 = 3
        sendBits(d, 0x05, 3)

        assertEquals(3, d.biValid, "bi_valid after flush")
        assertEquals(0x05, d.biBuf.toInt(), "bi_buf after flush")
        assertEquals(0xAA.toByte(), d.pendingBuf[0], "Flushed byte 0") // LSB of 0xAAAA
        assertEquals(0xAA.toByte(), d.pendingBuf[1], "Flushed byte 1") // MSB of 0xAAAA
        assertEquals(2, d.pending, "Pending should be 2 after flush")
    }
}
