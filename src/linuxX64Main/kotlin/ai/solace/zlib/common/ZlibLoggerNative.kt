@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package ai.solace.zlib.common

import kotlinx.cinterop.*
import platform.posix.*

actual fun currentTimestamp(): String {
    memScoped {
        val buf = allocArray<ByteVar>(24)
        val t = alloc<time_tVar>()
        time(t.ptr)
        strftime(buf, 24UL, "%Y-%m-%d %H:%M:%S", localtime(t.ptr))
        return buf.toKString()
    }
}