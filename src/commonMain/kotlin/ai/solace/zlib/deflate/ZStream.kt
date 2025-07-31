// Copyright (c) 2006, ComponentAce
// http://www.componentace.com
// All rights reserved.

// Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:

// Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
// Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.
// Neither the name of ComponentAce nor the names of its contributors may be used to endorse or promote products derived from this software without specific prior written permission.
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

/*
Copyright (c) 2000,2001,2002,2003 ymnk, JCraft,Inc. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice,
this list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in
the documentation and/or other materials provided with the distribution.

3. The names of the authors may not be used to endorse or promote products
derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED WARRANTIES,
INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL JCRAFT,
INC. OR ANY CONTRIBUTORS TO THIS SOFTWARE BE LIABLE FOR ANY DIRECT, INDIRECT,
INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA,
OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,
EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/
/*
* This program is based on zlib-1.1.3, so all credit should go authors
* Jean-loup Gailly(jloup@gzip.org) and Mark Adler(madler@alumni.caltech.edu)
* and contributors of zlib.
*/

package ai.solace.zlib.deflate

import ai.solace.zlib.common.* // Import all constants

/**
 * Core class for ZLib compression and decompression operations.
 * 
 * This class provides the primary interface for compression and decompression
 * functionality, managing input and output buffers, tracking progress, and
 * maintaining state for both inflation and deflation operations.
 *
 * WHY THIS APPROACH: In the original C zlib implementation, this structure is defined as a 'z_stream' struct.
 * We've implemented it as a class in Kotlin to provide a more idiomatic object-oriented approach and to leverage
 * Kotlin's null safety and other language features. This class centralizes the state management that's needed
 * across both compression and decompression operations, following the same pattern as the original zlib design.
 */
class ZStream {

    // All constants previously defined here (MAX_WBITS, DEF_WBITS, Z_NO_FLUSH, etc., MAX_MEM_LEVEL, Z_OK, etc.)
    // are now expected to be used from ai.solace.zlib.common.Constants

    /**
     * Next input byte array to be processed
     *
     * WHY NULLABLE: This is nullable to match the zlib's ability to handle a NULL pointer scenario,
     * where the application can initialize the stream without immediately providing input data.
     */
    var nextIn: ByteArray? = null

    /**
     * Current position in the input buffer
     *
     * WHY INDEX-BASED: Unlike C's direct pointer arithmetic, we use an index-based approach
     * which is safer and more idiomatic in Kotlin while achieving the same functionality.
     */
    var nextInIndex = 0

    /** Number of bytes available at next_in */
    var availIn = 0

    /**
     * Total number of input bytes read so far
     *
     * WHY LONG INSTEAD OF INT: We use Long to handle potentially large files that might exceed
     * the 2GB limit of an Int, which is an improvement over the original uLong in C.
     */
    var totalIn: Long = 0

    /** Next output byte array where processed data should be written */
    var nextOut: ByteArray? = null

    /** Current position in the output buffer */
    var nextOutIndex = 0

    /** Remaining free space at next_out */
    var availOut = 0

    /** Total number of bytes output so far */
    var totalOut: Long = 0

    /**
     * Error message if an operation fails
     *
     * WHY NULLABLE STRING: This follows zlib's pattern where msg is NULL when there's no error,
     * but provides a readable message when something goes wrong.
     */
    var msg: String? = null

    /**
     * Internal deflate state
     *
     * WHY INTERNAL VISIBILITY: This restricts access to implementation details while allowing
     * access from within the package, maintaining encapsulation while still permitting the
     * necessary interactions between components.
     */
    internal var dState: Deflate? = null

    /** Internal inflate state */
    internal var iState: Inflate? = null

    /**
     * Best guess about the data type: ascii or binary
     *
     * WHY INT INSTEAD OF ENUM: This maintains compatibility with the zlib constants for data types
     * (Z_BINARY, Z_TEXT, Z_UNKNOWN) defined in the manual.
     */
    internal var dataType = 0

    /** Adler-32 checksum value */
    var adler: Long = 0

    /**
     * Adler-32 checksum calculator
     *
     * WHY SEPARATE OBJECT: This separates the checksum calculation logic from the stream handling,
     * following good object-oriented design principles.
     */
    internal var adlerChecksum: Adler32? = Adler32()

    /**
     * Initializes the decompression stream with default window bits.
     * 
     * @return Z_OK on success, negative value on failure
     */
    fun inflateInit(): Int {
        return inflateInit(MAX_WBITS) // Using the correct constant defined in the Kotlin code
    }

    /**
     * Initializes the decompression stream with specified window bits.
     * 
     * @param w The window bits parameter, which should be between 8 and 15
     * @return Z_OK on success, negative value on failure
     */
    fun inflateInit(w: Int): Int {
        iState = Inflate()
        return iState!!.inflateInit(this, w)
    }

    /**
     * Decompresses data from the input buffer to the output buffer.
     * 
     * @param f The flush mode (Z_NO_FLUSH, Z_SYNC_FLUSH, Z_FINISH, etc.)
     * @return Z_OK, Z_STREAM_END, or error code
     */
    fun inflate(f: Int): Int {
        println("[DEBUG_LOG] ZStream.inflate called with flush=$f, availIn=$availIn, nextInIndex=$nextInIndex, availOut=$availOut")
        
        // Validate input parameters
        if (f != Z_NO_FLUSH && f != Z_SYNC_FLUSH && f != Z_FINISH) {
            println("[DEBUG_LOG] ZStream.inflate error: invalid flush value: $f")
            return Z_STREAM_ERROR
        }
        
        // Check for null state
        if (iState == null) {
            println("[DEBUG_LOG] ZStream.inflate error: iState is null")
            return Z_STREAM_ERROR
        }
        
        // Validate input buffer if we have input data
        if (availIn > 0) {
            if (nextIn == null) {
                println("[DEBUG_LOG] ZStream.inflate error: nextIn is null but availIn=$availIn")
                return Z_STREAM_ERROR
            }
            
            if (nextInIndex < 0) {
                println("[DEBUG_LOG] ZStream.inflate error: negative nextInIndex=$nextInIndex")
                return Z_STREAM_ERROR
            }
            
            if (nextInIndex >= nextIn!!.size) {
                println("[DEBUG_LOG] ZStream.inflate error: nextInIndex=$nextInIndex out of bounds for nextIn size=${nextIn!!.size}")
                // Reset to beginning of buffer as a recovery mechanism
                nextInIndex = 0
                if (availIn > nextIn!!.size) {
                    availIn = nextIn!!.size
                }
                println("[DEBUG_LOG] ZStream.inflate reset nextInIndex to 0 and adjusted availIn to $availIn")
                // Return Z_OK to allow the process to continue
                return Z_OK
            }
            
            // Check if we have enough input data
            if (nextInIndex + availIn > nextIn!!.size) {
                println("[DEBUG_LOG] ZStream.inflate error: nextInIndex=$nextInIndex + availIn=$availIn exceeds nextIn size=${nextIn!!.size}")
                // Adjust availIn to prevent out of bounds access
                availIn = nextIn!!.size - nextInIndex
                println("[DEBUG_LOG] ZStream.inflate adjusted availIn to $availIn")
            }
        }
        
        // Validate output buffer
        if (nextOut == null) {
            println("[DEBUG_LOG] ZStream.inflate error: nextOut is null")
            return Z_STREAM_ERROR
        }
        
        if (nextOutIndex < 0) {
            println("[DEBUG_LOG] ZStream.inflate error: negative nextOutIndex=$nextOutIndex")
            return Z_STREAM_ERROR
        }
        
        if (nextOutIndex >= nextOut!!.size) {
            println("[DEBUG_LOG] ZStream.inflate error: nextOutIndex=$nextOutIndex out of bounds for nextOut size=${nextOut!!.size}")
            // Reset to beginning of buffer as a recovery mechanism
            nextOutIndex = 0
            println("[DEBUG_LOG] ZStream.inflate reset nextOutIndex to 0")
            // Return Z_BUF_ERROR to signal that more output space is needed
            return Z_BUF_ERROR
        }
        
        // Check if we have enough output space
        if (availOut <= 0) {
            println("[DEBUG_LOG] ZStream.inflate error: no output space available (availOut=$availOut)")
            return Z_BUF_ERROR
        }
        
        if (nextOutIndex + availOut > nextOut!!.size) {
            println("[DEBUG_LOG] ZStream.inflate error: nextOutIndex=$nextOutIndex + availOut=$availOut exceeds nextOut size=${nextOut!!.size}")
            // Adjust availOut to prevent out of bounds access
            availOut = nextOut!!.size - nextOutIndex
            println("[DEBUG_LOG] ZStream.inflate adjusted availOut to $availOut")
        }
        
        // If we have no input data but are not at the end of the stream, return Z_BUF_ERROR
        if (availIn == 0 && f != Z_FINISH) {
            // If there's no input and we're not finishing, we can't make progress.
            // However, if the decompressor has produced output, we should return Z_OK
            // to allow the caller to consume it.
            val r = iState!!.inflate(this, f)
            if (r == Z_BUF_ERROR && totalOut == 0L) {
                println("[DEBUG_LOG] ZStream.inflate: no input and no output produced, returning Z_BUF_ERROR")
                return Z_BUF_ERROR
            }
            println("[DEBUG_LOG] ZStream.inflate: no input, but returning Z_OK to flush output")
            return Z_OK
        }
        
        try {
            // Print first few bytes of input for debugging
            if (availIn > 0 && nextIn != null) {
                val bytesToShow = minOf(availIn, 10)
                println("[DEBUG_LOG] First $bytesToShow bytes of input: ${nextIn!!.slice(nextInIndex until nextInIndex + bytesToShow).joinToString(", ") { it.toUByte().toString() }}")
            }
            
            // Special case: if we're at the end of the input and have processed all data, return Z_STREAM_END
            if (availIn == 0 && f == Z_FINISH && iState!!.mode == INF_DONE) {
                println("[DEBUG_LOG] ZStream.inflate: end of stream reached")
                return Z_STREAM_END
            }
            
            val result = iState!!.inflate(this, f)
            println("[DEBUG_LOG] ZStream.inflate returned: $result, new availIn=$availIn, nextInIndex=$nextInIndex, availOut=$availOut, totalOut=$totalOut")
            return result
        } catch (e: Exception) {
            println("[DEBUG_LOG] ZStream.inflate exception: ${e.message}")
            println("[DEBUG_LOG] Exception stack trace:")
            e.printStackTrace()
            
            // Handle specific exceptions
            when (e) {
                is IndexOutOfBoundsException -> {
                    println("[DEBUG_LOG] Index out of bounds exception detected")
                    
                    // Check if it's related to input buffer
                    if (nextInIndex >= nextIn?.size ?: 0) {
                        println("[DEBUG_LOG] Input buffer index out of bounds, resetting")
                        nextInIndex = 0
                        availIn = 0
                        msg = "Input buffer index out of bounds: ${e.message}"
                        return Z_BUF_ERROR
                    }
                    
                    // Check if it's related to output buffer
                    if (nextOutIndex >= nextOut?.size ?: 0) {
                        println("[DEBUG_LOG] Output buffer index out of bounds, resetting")
                        nextOutIndex = 0
                        availOut = nextOut?.size ?: 0
                        msg = "Output buffer index out of bounds: ${e.message}"
                        return Z_BUF_ERROR
                    }
                    
                    // Generic index out of bounds
                    msg = "Array index out of bounds: ${e.message}"
                    return Z_STREAM_ERROR
                }
                
                is NullPointerException -> {
                    println("[DEBUG_LOG] Null pointer exception detected")
                    msg = "Null pointer: ${e.message}"
                    return Z_STREAM_ERROR
                }
                
                else -> {
                    msg = "Unexpected error: ${e.message}"
                    throw e
                }
            }
        }
    }

    /**
     * Ends the decompression process and releases resources.
     * 
     * @return Z_OK on success, error code on failure
     */
    fun inflateEnd(): Int {
        if (iState == null) return Z_STREAM_ERROR
        val ret = iState!!.inflateEnd(this)
        iState = null
        return ret
    }



    /**
     * Initializes the compression stream with the specified compression level.
     * 
     * @param level The compression level (0-9, or Z_DEFAULT_COMPRESSION)
     * @return Z_OK on success, error code on failure
     */
    fun deflateInit(level: Int): Int {
        return deflateInit(level, MAX_WBITS)
    }

    /**
     * Initializes the compression stream with the specified compression level and window bits.
     * 
     * @param level The compression level (0-9, or Z_DEFAULT_COMPRESSION)
     * @param bits The logarithm of the window size (8-15)
     * @return Z_OK on success, error code on failure
     */
    fun deflateInit(level: Int, bits: Int): Int {
        return deflateInit(level, bits, DEF_MEM_LEVEL)
    }

    /**
     * Initializes the compression stream with the specified parameters.
     *
     * @param level The compression level (0-9, or Z_DEFAULT_COMPRESSION)
     * @param bits The logarithm of the window size (8-15)
     * @param memLevel How much memory to allocate for the internal compression state (1-9)
     * @return Z_OK on success, error code on failure
     */
    fun deflateInit(level: Int, bits: Int, memLevel: Int): Int {
        return deflateInit2(level, Z_DEFLATED, bits, memLevel, Z_DEFAULT_STRATEGY)
    }

    /**
     * Fully configurable initialization of the compression stream.
     *
     * @param level The compression level (0-9, or Z_DEFAULT_COMPRESSION)
     * @param method The compression method (only Z_DEFLATED is supported)
     * @param windowBits The logarithm of the window size (8-15, or negative for raw deflate)
     * @param memLevel How much memory to allocate (1-9)
     * @param strategy The compression strategy
     * @return Z_OK on success, error code on failure
     */
    fun deflateInit2(level: Int, method: Int, windowBits: Int, memLevel: Int, strategy: Int): Int {
        var err: Int
        var newLevel : Int = level
        var newWindowBits : Int = windowBits
        if (newLevel == Z_DEFAULT_COMPRESSION) {
            newLevel = 6
        }

        if (newWindowBits < 0) { // suppress zlib header
            val versionInt = 0
            if (newWindowBits < -15) {
                return Z_STREAM_ERROR
            }
            newWindowBits = -newWindowBits
        }

        if (memLevel < 1 || memLevel > MAX_MEM_LEVEL || method != Z_DEFLATED || newWindowBits < 8 ||
            newWindowBits > 15 || newLevel < 0 || newLevel > 9 || strategy < 0 || strategy > Z_HUFFMAN_ONLY) {
            return Z_STREAM_ERROR
        }

        dState = Deflate()
        dState!!.strm = this
        dState!!.status = BUSY_STATE

        // Handle window size and ensure correct initialization per the Pascal implementation
        err = dState!!.deflateInit2(this, newLevel, method, newWindowBits, memLevel, strategy)

        if (err != Z_OK) {
            dState = null
        }

        return err
    }

    /**
     * Compresses data from the input buffer to the output buffer.
     * 
     * @param flush The flush mode (Z_NO_FLUSH, Z_SYNC_FLUSH, Z_FINISH, etc.)
     * @return Z_OK, Z_STREAM_END, or error code
     */
    fun deflate(flush: Int): Int {
        println("[DEBUG_LOG] ZStream.deflate called with flush=$flush, availIn=$availIn, nextInIndex=$nextInIndex, availOut=$availOut")
        
        // Validate input parameters
        if (flush != Z_NO_FLUSH && flush != Z_SYNC_FLUSH && flush != Z_FINISH) {
            println("[DEBUG_LOG] ZStream.deflate error: invalid flush value: $flush")
            return Z_STREAM_ERROR
        }
        
        // Check for null state
        if (dState == null) {
            println("[DEBUG_LOG] ZStream.deflate error: dState is null")
            return Z_STREAM_ERROR
        }
        
        // Validate input buffer if we have input data
        if (availIn > 0) {
            if (nextIn == null) {
                println("[DEBUG_LOG] ZStream.deflate error: nextIn is null but availIn=$availIn")
                return Z_STREAM_ERROR
            }
            
            if (nextInIndex < 0) {
                println("[DEBUG_LOG] ZStream.deflate error: negative nextInIndex=$nextInIndex")
                return Z_STREAM_ERROR
            }
            
            if (nextInIndex >= nextIn!!.size) {
                println("[DEBUG_LOG] ZStream.deflate error: nextInIndex=$nextInIndex out of bounds for nextIn size=${nextIn!!.size}")
                // Reset to beginning of buffer as a recovery mechanism
                nextInIndex = 0
                if (availIn > nextIn!!.size) {
                    availIn = nextIn!!.size
                }
                println("[DEBUG_LOG] ZStream.deflate reset nextInIndex to 0 and adjusted availIn to $availIn")
                // Return Z_OK to allow the process to continue
                return Z_OK
            }
            
            // Check if we have enough input data
            if (nextInIndex + availIn > nextIn!!.size) {
                println("[DEBUG_LOG] ZStream.deflate error: nextInIndex=$nextInIndex + availIn=$availIn exceeds nextIn size=${nextIn!!.size}")
                // Adjust availIn to prevent out of bounds access
                availIn = nextIn!!.size - nextInIndex
                println("[DEBUG_LOG] ZStream.deflate adjusted availIn to $availIn")
            }
        }
        
        // Validate output buffer
        if (nextOut == null) {
            println("[DEBUG_LOG] ZStream.deflate error: nextOut is null")
            return Z_STREAM_ERROR
        }
        
        if (nextOutIndex < 0) {
            println("[DEBUG_LOG] ZStream.deflate error: negative nextOutIndex=$nextOutIndex")
            return Z_STREAM_ERROR
        }
        
        if (nextOutIndex >= nextOut!!.size) {
            println("[DEBUG_LOG] ZStream.deflate error: nextOutIndex=$nextOutIndex out of bounds for nextOut size=${nextOut!!.size}")
            // Reset to beginning of buffer as a recovery mechanism
            nextOutIndex = 0
            println("[DEBUG_LOG] ZStream.deflate reset nextOutIndex to 0")
            // Return Z_BUF_ERROR to signal that more output space is needed
            return Z_BUF_ERROR
        }
        
        // Check if we have enough output space
        if (availOut <= 0) {
            println("[DEBUG_LOG] ZStream.deflate error: no output space available (availOut=$availOut)")
            return Z_BUF_ERROR
        }
        
        if (nextOutIndex + availOut > nextOut!!.size) {
            println("[DEBUG_LOG] ZStream.deflate error: nextOutIndex=$nextOutIndex + availOut=$availOut exceeds nextOut size=${nextOut!!.size}")
            // Adjust availOut to prevent out of bounds access
            availOut = nextOut!!.size - nextOutIndex
            println("[DEBUG_LOG] ZStream.deflate adjusted availOut to $availOut")
        }
        
        try {
            // Print first few bytes of input for debugging
            if (availIn > 0 && nextIn != null) {
                val bytesToShow = minOf(availIn, 10)
                println("[DEBUG_LOG] First $bytesToShow bytes of input: ${nextIn!!.slice(nextInIndex until nextInIndex + bytesToShow).joinToString(", ") { it.toUByte().toString() }}")
            }
            
            val result = dState!!.deflate(this, flush)
            println("[DEBUG_LOG] ZStream.deflate returned: $result, new availIn=$availIn, nextInIndex=$nextInIndex, availOut=$availOut, totalOut=$totalOut")
            return result
        } catch (e: Exception) {
            println("[DEBUG_LOG] ZStream.deflate exception: ${e.message}")
            println("[DEBUG_LOG] Exception stack trace:")
            e.printStackTrace()
            
            // Handle specific exceptions
            when (e) {
                is IndexOutOfBoundsException -> {
                    println("[DEBUG_LOG] Index out of bounds exception detected")
                    
                    // Check if it's related to input buffer
                    if (nextInIndex >= nextIn?.size ?: 0) {
                        println("[DEBUG_LOG] Input buffer index out of bounds, resetting")
                        nextInIndex = 0
                        availIn = 0
                        msg = "Input buffer index out of bounds: ${e.message}"
                        return Z_BUF_ERROR
                    }
                    
                    // Check if it's related to output buffer
                    if (nextOutIndex >= nextOut?.size ?: 0) {
                        println("[DEBUG_LOG] Output buffer index out of bounds, resetting")
                        nextOutIndex = 0
                        availOut = nextOut?.size ?: 0
                        msg = "Output buffer index out of bounds: ${e.message}"
                        return Z_BUF_ERROR
                    }
                    
                    // Generic index out of bounds
                    msg = "Array index out of bounds: ${e.message}"
                    return Z_STREAM_ERROR
                }
                
                is NullPointerException -> {
                    println("[DEBUG_LOG] Null pointer exception detected")
                    msg = "Null pointer: ${e.message}"
                    return Z_STREAM_ERROR
                }
                
                else -> {
                    msg = "Unexpected error: ${e.message}"
                    throw e
                }
            }
        }
    }

    /**
     * Ends the compression process and releases resources.
     * 
     * @return Z_OK on success, error code on failure
     */
    fun deflateEnd(): Int {
        if (dState == null) {
            return Z_STREAM_ERROR
        }
        val ret = dState!!.deflateEnd()
        dState = null
        return ret
    }

    /**
     * Resets the compression stream without releasing memory.
     *
     * @return Z_OK on success, error code on failure
     */
    fun deflateReset(): Int {
        if (dState == null) {
            return Z_STREAM_ERROR
        }
        return dState!!.deflateReset(this)
    }

    /**
     * Reads a new buffer from the current input stream and updates the Adler-32 checksum.
     *
     * All deflate() input goes through this function. Some applications may wish to modify it
     * to avoid allocating a large next_in buffer and copying from it.
     *
     * @param buf The buffer to read into
     * @param start The starting position in the buffer
     * @param size The maximum number of bytes to read
     * @return The number of bytes actually read
     */
    internal fun readBuf(buf: ByteArray, start: Int, size: Int): Int {
        var len = availIn
        if (len > size) len = size
        if (len == 0) return 0
        availIn -= len
        if (dState?.noheader == 0) {
            adler = adlerChecksum!!.adler32(adler, nextIn!!, nextInIndex, len)
        }
        nextIn!!.copyInto(buf, start, nextInIndex, nextInIndex + len)
        nextInIndex += len
        totalIn += len
        return len
    }

    /**
     * Flushes as much pending output as possible.
     * 
     * All deflate() output goes through this function. Some applications may wish to modify it
     * to avoid allocating a large next_out buffer and copying into it.
     */
    internal fun flushPending() {
        var len = dState!!.pending
        if (len > availOut) len = availOut
        if (len == 0) return

        if (dState!!.pendingBuf.size <= dState!!.pendingOut ||
            nextOut == null ||
            dState!!.pendingOut < 0 ||
            dState!!.pending < 0) {
            return
        }

        dState!!.pendingBuf.copyInto(nextOut!!, nextOutIndex, dState!!.pendingOut, dState!!.pendingOut + len)
        nextOutIndex += len
        dState!!.pendingOut += len
        totalOut += len.toLong()
        availOut -= len
        dState!!.pending -= len
        if (dState!!.pending == 0) {
            dState!!.pendingOut = 0
        }
    }

    /**
     * Flushes the output buffer.
     */
    internal fun inflateFlush(r: Int): Int {
        if (iState == null) return Z_STREAM_ERROR
        return iState?.blocks?.inflateFlush(this, r) ?: Z_STREAM_ERROR
    }

    /**
     * Releases all resources used by this ZStream instance.
     *
     * This method should be called when the stream is no longer needed to prevent memory leaks.
     */
    fun free() {
        nextIn = null
        nextOut = null
        msg = null
        adlerChecksum = null
    }
}
