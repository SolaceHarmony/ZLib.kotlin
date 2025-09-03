package ai.solace.zlib.cli

expect fun readFile(path: String): ByteArray
expect fun writeFile(path: String, data: ByteArray)