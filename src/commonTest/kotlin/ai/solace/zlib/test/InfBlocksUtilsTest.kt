package ai.solace.zlib.test

import ai.solace.zlib.common.*
import ai.solace.zlib.deflate.InfBlocks
// InfCodes is not directly used by inflate_flush
// import ai.solace.zlib.deflate.InfCodes
import ai.solace.zlib.deflate.Inflate // Needed for ZStream.istate initialization
import ai.solace.zlib.deflate.ZStream
import ai.solace.zlib.deflate.inflateFlush // Direct import for the utility function
import kotlin.test.*

class InfBlocksUtilsTest {

    private fun createInfBlocksState(stream: ZStream, windowSize: Int): InfBlocks {
        // To properly initialize InfBlocks, we need to simulate part of Inflate's setup.
        // Inflate.inflateInit creates the Inflate state (istate) and then InfBlocks.

        // Minimal ZStream setup if not already done by caller
        if (stream.iState == null) {
            stream.iState = Inflate() // Create the Inflate state object
            // Initialize basic fields in Inflate state that InfBlocks constructor or reset might need
            stream.iState!!.nowrap = 0 // Assuming default nowrap = 0 (zlib header present)
            stream.iState!!.wbits = windowSize.countTrailingZeroBits() // wbits is log2(window size)
                                                                    // A bit hacky; prefer passing wbits if available
                                                                    // Or use a known good default like 15 for MAX_WBITS
                                                                    // For this test, windowSize is passed, so derive wbits.
                                                                    // If windowSize is not power of 2, this is problematic.
                                                                    // Let's assume windowSize will be a power of 2 for testing.
            if (windowSize <= 0 || windowSize.countOneBits() != 1) {
                 // Default to MAX_WBITS if windowSize is not a power of 2
                 stream.iState!!.wbits = MAX_WBITS
            }
        }
        val istate = stream.iState!!

        // InfBlocks(z: ZStream, checkfn: Any?, w: Int)
        // 'checkfn' is an Adler32 instance from Inflate, or null if nowrap.
        // 'w' is the window size.
        val checkfn = if (istate.nowrap != 0) null else istate
        val blocks = InfBlocks(stream, checkfn, windowSize)

        // Assigning to istate.blocks is what Inflate.inflateInit does.
        istate.blocks = blocks

        // blocks.reset(stream, null) // InfBlocks constructor already calls reset.
                                   // Calling it again might be okay or redundant.
                                   // Let's rely on constructor's reset.
        return blocks
    }

    @Test
    fun testInflateFlush_FullFlush() {
        val stream = ZStream()
        val windowSize = 32 // Small window for easier testing
        val blocks = createInfBlocksState(stream, windowSize)

        blocks.read = 0
        val bytesToWrite = 5
        blocks.write = bytesToWrite // Number of bytes available in the window to be flushed

        // Ensure window is allocated (createInfBlocksState should handle this via InfBlocks constructor)
        assertTrue(blocks.window.size >= windowSize, "Window not allocated or too small")

        // Fill window with some data to flush
        for (i in 0 until blocks.write) { blocks.window[i] = (i + 1).toByte() }

        val outputBufferSize = 10
        stream.availOut = outputBufferSize
        stream.nextOut = ByteArray(outputBufferSize)
        stream.nextOutIndex = 0
        val originalTotalOut = stream.totalOut

        // Call the utility function (it's in ai.solace.zlib.deflate package)
        val result = inflateFlush(blocks, stream, Z_OK)

        assertEquals(Z_OK, result, "inflate_flush should return Z_OK")
        assertEquals(bytesToWrite, stream.nextOutIndex, "next_out_index should advance by flushed bytes")
        assertEquals(outputBufferSize - bytesToWrite, stream.availOut, "avail_out should decrease by flushed bytes")
        assertEquals(originalTotalOut + bytesToWrite, stream.totalOut, "total_out should increase by flushed bytes")
        assertEquals(bytesToWrite, blocks.read, "blocks.read should advance to blocks.write")

        // Verify that the correct data was flushed
        for (i in 0 until bytesToWrite) {
            assertEquals((i + 1).toByte(), stream.nextOut!![i], "Flushed byte $i is incorrect")
        }
    }

    @Test
    fun testInflateFlush_PartialFlush_NotEnoughOutputSpace() {
        val stream = ZStream()
        val windowSize = 32
        val blocks = createInfBlocksState(stream, windowSize)

        blocks.read = 0
        val bytesInWindow = 5
        blocks.write = bytesInWindow
        for (i in 0 until blocks.write) { blocks.window[i] = (i + 1).toByte() }

        val outputBufferAvailable = 2 // Not enough space for all 5 bytes
        stream.availOut = outputBufferAvailable
        stream.nextOut = ByteArray(outputBufferAvailable) // Output buffer exactly size of avail_out for this test
        stream.nextOutIndex = 0
        val originalTotalOut = stream.totalOut
        val originalAvailIn = stream.availIn // Should be unchanged by inflate_flush

        val result = inflateFlush(blocks, stream, Z_OK)

        // Z_OK is returned even if not all data could be flushed, as long as some progress was made
        // or if it was a Z_BUF_ERROR that got converted to Z_OK because n != 0.
        assertEquals(Z_OK, result, "inflate_flush should return Z_OK with partial flush if space available")

        assertEquals(outputBufferAvailable, stream.nextOutIndex, "next_out_index should advance by available space")
        assertEquals(0, stream.availOut, "avail_out should be 0 as all provided space was used")
        assertEquals(originalTotalOut + outputBufferAvailable, stream.totalOut, "total_out should increase by flushed bytes")
        assertEquals(outputBufferAvailable, blocks.read, "blocks.read should advance by bytes actually flushed")
        assertEquals(originalAvailIn, stream.availIn, "avail_in should be unchanged by inflate_flush")


        // Verify that the correct data was flushed
        for (i in 0 until outputBufferAvailable) {
            assertEquals((i + 1).toByte(), stream.nextOut!![i], "Flushed byte $i is incorrect in partial flush")
        }
    }

     @Test
    fun testInflateFlush_WrappedWindow() {
        val stream = ZStream()
        val windowSize = 32
        val blocks = createInfBlocksState(stream, windowSize)

        // Simulate data wrapped around the window
        // Example: read = 30, write = 3. Data at window[30], window[31], window[0], window[1], window[2]
        blocks.read = windowSize - 2 // e.g., 30
        blocks.write = 3          // e.g., 3

        val dataPart1 = byteArrayOf(10.toByte(), 20.toByte()) // Goes at window[30], window[31]
        val dataPart2 = byteArrayOf(30.toByte(), 40.toByte(), 50.toByte()) // Goes at window[0], window[1], window[2]

        dataPart1.copyInto(blocks.window, blocks.read)
        dataPart2.copyInto(blocks.window, 0)

        val outputBufferSize = 10
        stream.availOut = outputBufferSize
        stream.nextOut = ByteArray(outputBufferSize)
        stream.nextOutIndex = 0
        val originalTotalOut = stream.totalOut

        val result = inflateFlush(blocks, stream, Z_OK)
        assertEquals(Z_OK, result, "inflate_flush with wrapped window should return Z_OK")

        val totalBytesCopied = dataPart1.size + dataPart2.size
        assertEquals(totalBytesCopied, stream.nextOutIndex, "next_out_index (wrapped) incorrect")
        assertEquals(outputBufferSize - totalBytesCopied, stream.availOut, "avail_out (wrapped) incorrect")
        assertEquals(originalTotalOut + totalBytesCopied, stream.totalOut, "total_out (wrapped) incorrect")
        assertEquals(blocks.write, blocks.read, "blocks.read should be equal to blocks.write after full flush (wrapped)")

        val expectedFlushedData = dataPart1 + dataPart2
        for (i in 0 until totalBytesCopied) {
            assertEquals(expectedFlushedData[i], stream.nextOut!![i], "Flushed byte $i (wrapped) is incorrect")
        }
    }
}
