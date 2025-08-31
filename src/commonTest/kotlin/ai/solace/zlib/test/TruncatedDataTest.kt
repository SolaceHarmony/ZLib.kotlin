package ai.solace.zlib.test

import ai.solace.zlib.ZLibCompression
import ai.solace.zlib.deflate.ZStreamException
import kotlin.test.Test
import kotlin.test.assertFailsWith

class TruncatedDataTest {
    @Test
    fun truncatedInputThrowsException() {
        val original = "Hello truncated".encodeToByteArray()
        val compressed = ZLibCompression.compress(original)
        val truncated = compressed.copyOf(compressed.size - 1)

        assertFailsWith<ZStreamException> {
            ZLibCompression.decompress(truncated)
        }
    }
}
