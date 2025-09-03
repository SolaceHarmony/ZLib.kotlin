package ai.solace.zlib.test

import ai.solace.zlib.common.ZlibLogger
import ai.solace.zlib.deflate.Adler32
import kotlin.test.Test

/**
 * This test is used to verify the expected value in the testLargeBuffer test in Adler32UtilsTest.
 */
class VerifyLargeBufferTest {
    @Test
    fun verifyLargeBufferValue() {
        val adler32 = Adler32()

        // Create the same buffer as in testLargeBuffer
        val buffer = ByteArray(1000) { it.toByte() }

        // Calculate the checksum using the original Adler32 class
        val result = adler32.adler32(1L, buffer, 0, 1000)

        // Print the result for comparison
        ZlibLogger.log("[DEBUG_LOG] Adler32 of large buffer: 0x${result.toString(16)} ($result)")
    }
}
