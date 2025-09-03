package ai.solace.zlib.cli

import okio.FileSystem
import okio.Path.Companion.toPath

fun readFile(path: String): ByteArray {
    val p = path.toPath()
    return FileSystem.SYSTEM.read(p) { readByteArray() }
}

fun writeFile(
    path: String,
    data: ByteArray,
) {
    val p = path.toPath()
    FileSystem.SYSTEM.write(p) {
        write(data)
        flush()
    }
}
