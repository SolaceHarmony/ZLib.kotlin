package ai.solace.zlib.cli

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import platform.posix.SEEK_END
import platform.posix.SEEK_SET
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fread
import platform.posix.fseek
import platform.posix.ftell
import platform.posix.fwrite

/**
 * Platform-specific implementation of file operations for macOS
 */

@OptIn(ExperimentalForeignApi::class)
actual fun readFile(path: String): ByteArray {
    val file = fopen(path, "rb") ?: throw IllegalStateException("Could not open file for reading: $path") // TODO(detekt): prefer IOException

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
                throw IllegalStateException("Failed to read entire file: $path (read=$bytesRead size=$fileSize)") // TODO(detekt): prefer IOException
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

@OptIn(ExperimentalForeignApi::class)
actual fun writeFile(
    path: String,
    data: ByteArray,
) {
    val file = fopen(path, "wb") ?: throw IllegalStateException("Could not open file for writing: $path") // TODO(detekt): prefer IOException

    try {
        memScoped {
            val buffer = allocArray<ByteVar>(data.size)

            // Copy from ByteArray to buffer
            for (i in data.indices) {
                buffer[i] = data[i]
            }

            val bytesWritten = fwrite(buffer, 1u, data.size.toULong(), file)

            if (bytesWritten < data.size.toULong()) {
                throw IllegalStateException("Failed to write entire file: $path (wrote=$bytesWritten size=${data.size})") // TODO(detekt): prefer IOException
            }
        }
    } finally {
        fclose(file)
    }
}
