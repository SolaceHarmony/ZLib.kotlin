package ai.solace.zlib.cli

import java.io.File

actual fun readFile(path: String): ByteArray = File(path).readBytes()

actual fun writeFile(path: String, data: ByteArray) {
    File(path).writeBytes(data)
}
