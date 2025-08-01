@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package ai.solace.zlib.common

import kotlinx.cinterop.*
import platform.posix.*

actual fun logToFile(line: String) {
    memScoped {
        val file = fopen("zlib.log", "a")
        if (file != null) {
            fputs(line, file)
            fclose(file)
        }
    }
}

actual fun currentTimestamp(): String {
    memScoped {
        val buf = allocArray<ByteVar>(24)
        val t = alloc<time_tVar>()
        time(t.ptr)
        strftime(buf, 24UL, "%Y-%m-%d %H:%M:%S", localtime(t.ptr))
        return buf.toKString()
    }
}
