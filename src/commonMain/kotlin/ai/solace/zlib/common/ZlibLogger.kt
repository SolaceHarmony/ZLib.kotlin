
package ai.solace.zlib.common


object ZlibLogger {
    // Logging flags to control verbosity, especially for Kotlin/Native performance
    // - DEBUG_ENABLED=false will suppress messages prefixed with [DEBUG_LOG]
    // - ENABLE_LOGGING=false will suppress all logging
    var ENABLE_LOGGING: Boolean = true  // TEMPORARILY ENABLED FOR DEBUGGING
    var DEBUG_ENABLED: Boolean = true   // TEMPORARILY ENABLED FOR DEBUGGING
    
    fun debug(message: String, className: String = "", functionName: String = "") = 
        log(message, className, functionName)

    fun log(message: String, className: String = "", functionName: String = "") {
        // Global logging gate
        if (!ENABLE_LOGGING) return
        // Suppress verbose debug logs unless explicitly enabled
        if (!DEBUG_ENABLED && message.startsWith("[DEBUG_LOG]")) return

        val timestamp = currentTimestamp()
        val location = if (className.isNotEmpty() || functionName.isNotEmpty()) {
            val cls = if (className.isNotEmpty()) className else "Unknown"
            val func = if (functionName.isNotEmpty()) functionName else "unknown"
            "[$cls::$func] "
        } else ""
        val line = "[$timestamp] $location$message\n"
        try {
            logToFile(line)
        } catch (_: Exception) {
            // Ignore logging errors
        }
    }
    
    // Convenience methods for specific classes to make logging easier
    fun logInflate(message: String, functionName: String = "") = 
        log(message, "Inflate", functionName)
        
    fun logDeflate(message: String, functionName: String = "") = 
        log(message, "Deflate", functionName)
        
    fun logZStream(message: String, functionName: String = "") = 
        log(message, "ZStream", functionName)
        
    fun logInfBlocks(message: String, functionName: String = "") = 
        log(message, "InfBlocks", functionName)
        
    fun logInfCodes(message: String, functionName: String = "") = 
        log(message, "InfCodes", functionName)
        
    fun logInfTree(message: String, functionName: String = "") = 
        log(message, "InfTree", functionName)
        
    // Mathematical algorithm specific loggers
    fun logBitwise(message: String, functionName: String = "") = 
        log(message, "BitwiseOps", functionName)
        
    fun logAdler32(message: String, functionName: String = "") = 
        log(message, "Adler32", functionName)
        
    fun logHuffman(message: String, functionName: String = "") = 
        log(message, "Huffman", functionName)
        
    fun logTree(message: String, functionName: String = "") = 
        log(message, "Tree", functionName)
        
    fun logCRC32(message: String, functionName: String = "") = 
        log(message, "CRC32", functionName)
        
    // Detailed mathematical operation loggers
    fun logBitwiseOp(operation: String, input: Any, shift: Int? = null, result: Any, functionName: String = "") {
        val shiftStr = if (shift != null) ", shift=$shift" else ""
        logBitwise("$operation(input=$input$shiftStr) -> $result", functionName)
    }
    
    fun logAdler32Calc(s1: Long, s2: Long, byte: Int? = null, index: Int? = null, functionName: String = "") {
        val byteStr = if (byte != null) ", byte=$byte" else ""
        val indexStr = if (index != null) ", index=$index" else ""
        logAdler32("s1=$s1, s2=$s2$byteStr$indexStr", functionName)
    }
    
    fun logHuffmanCode(symbol: Int, code: Int, bits: Int, functionName: String = "") {
        logHuffman("symbol=$symbol -> code=$code (${bits} bits) [0x${code.toString(16)}]", functionName)
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

// Allow enabling logs in CI by setting ZLIB_LOGGING=1
// platformEnable via env was removed to keep native interop simple
