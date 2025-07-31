package ai.solace.zlib.deflate

/**
 * Simple logger for ZLib operations.
 * Currently logs to console only. File logging can be added as platform-specific implementations.
 */
object ZlibLogger {
    private var enabled = false

    fun enable() {
        this.enabled = true
    }

    fun disable() {
        this.enabled = false
    }

    fun debug(message: String) {
        if (enabled) {
            log(message)
        }
    }

    fun log(message: String) {
        if (enabled) {
            println("[ZLib] $message")
        }
    }
}