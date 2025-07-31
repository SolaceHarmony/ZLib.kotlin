
package ai.solace.zlib.common


import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

object ZlibLogger {
    fun debug(message: String) = log(message)

    fun log(message: String) {
        val timestamp = currentTimestamp()
        val line = "[$timestamp] $message\n"
        GlobalScope.launch {
            try {
                logToFile(line)
            } catch (_: Exception) {
                // Ignore logging errors
            }
        }
    }
}

/**
 * Platform-specific file append implementation
 */
expect fun logToFile(line: String)

/**
 * Platform-specific timestamp string (e.g. yyyy-MM-dd HH:mm:ss)
 */
expect fun currentTimestamp(): String
