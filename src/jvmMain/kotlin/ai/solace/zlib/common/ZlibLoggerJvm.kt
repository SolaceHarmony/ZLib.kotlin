@file:Suppress("ktlint:standard:property-naming")

package ai.solace.zlib.common

import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

actual var LOG_FILE_PATH: String? = null

actual fun logToFile(line: String) {
    val path = LOG_FILE_PATH ?: "zlib.log"
    File(path).appendText(line)
}

actual fun getEnv(name: String): String? = System.getenv(name)

actual fun currentTimestamp(): String = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
