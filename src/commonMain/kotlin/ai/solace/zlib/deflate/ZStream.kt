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
        ZlibLogger.debug("=== ZStream.inflateInit CALLED with w=$w ===")
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
        ZlibLogger.debug("=== ZStream.inflate CALLED with f=$f ===")
        if (iState == null) {
            ZlibLogger.debug("ZStream.inflate: iState is null, returning Z_STREAM_ERROR")
            return Z_STREAM_ERROR
        }
        ZlibLogger.debug("ZStream.inflate: Delegating to iState.inflate")
        return iState!!.inflate(this, f)
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
        error("BROKEN: The deflate compression function is known to be broken due to arithmetic bitwise operations. Use external compression tools like pigz instead.")
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
        error("BROKEN: The deflate compression function is known to be broken due to arithmetic bitwise operations. Use external compression tools like pigz instead.")
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
        error("BROKEN: The deflate compression function is known to be broken due to arithmetic bitwise operations. Use external compression tools like pigz instead.")
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
        error("BROKEN: The deflate compression function is known to be broken due to arithmetic bitwise operations. Use external compression tools like pigz instead.")
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
        error("BROKEN: The deflate compression function is known to be broken due to arithmetic bitwise operations. Use external compression tools like pigz instead.")
        if (dState == null) {
            return Z_STREAM_ERROR
        }
        return dState!!.deflate(this, flush)
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
            ZlibLogger.log("[DEBUG_ADLER] Updated Adler32: $adler (processed $len bytes)")
        } else {
            ZlibLogger.log("[DEBUG_ADLER] Skipping Adler32 update: noheader=${dState?.noheader}")
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
