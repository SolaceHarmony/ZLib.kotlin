package ai.solace.zlib.common

/**
 * Simple logging utility for optional debug output.
 * Set [enabled] to `true` to print debug messages.
 */
object ZlibLogger {
    var enabled: Boolean = false

    fun debug(message: String) {
        if (enabled) println(message)
    }
}
