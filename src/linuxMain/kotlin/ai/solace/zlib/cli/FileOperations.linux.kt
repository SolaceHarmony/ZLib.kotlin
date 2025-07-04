package ai.solace.zlib.cli

import kotlinx.cinterop.*
import platform.posix.*

/**
 * Platform-specific implementation of file operations for Linux
 */

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
actual fun readFile(path: String): ByteArray {
    val file = fopen(path, "rb") ?: throw Exception("Could not open file for reading: $path")
    
    try {
        // Get file size
        fseek(file, 0, SEEK_END)
        val fileSize = ftell(file)
        fseek(file, 0, SEEK_SET)
        
        if (fileSize <= 0) {
            return ByteArray(0)
        }
        
        // Read file content
        val result = ByteArray(fileSize.toInt())
        memScoped {
            val buffer = allocArray<ByteVar>(fileSize.toInt())
            val bytesRead = fread(buffer, 1u, fileSize.toULong(), file)
            
            if (bytesRead < fileSize.toULong()) {
                throw Exception("Failed to read entire file: $path")
            }
            
            // Copy to ByteArray
            for (i in 0 until fileSize.toInt()) {
                result[i] = buffer[i]
            }
        }
        
        return result
    } finally {
        fclose(file)
    }
}

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
actual fun writeFile(path: String, data: ByteArray) {
    val file = fopen(path, "wb") ?: throw Exception("Could not open file for writing: $path")
    
    try {
        memScoped {
            val buffer = allocArray<ByteVar>(data.size)
            
            // Copy from ByteArray to buffer
            for (i in data.indices) {
                buffer[i] = data[i]
            }
            
            val bytesWritten = fwrite(buffer, 1u, data.size.toULong(), file)
            
            if (bytesWritten < data.size.toULong()) {
                throw Exception("Failed to write entire file: $path")
            }
        }
    } finally {
        fclose(file)
    }
}