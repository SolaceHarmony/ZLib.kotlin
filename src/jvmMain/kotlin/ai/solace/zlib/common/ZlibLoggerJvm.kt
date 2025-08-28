package ai.solace.zlib.common

import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

actual fun logToFile(line: String) {
    File("zlib.log").appendText(line)
}

actual fun currentTimestamp(): String {
    return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
}
