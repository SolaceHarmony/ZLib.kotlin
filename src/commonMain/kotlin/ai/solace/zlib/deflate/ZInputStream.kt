// Copyright (c) 2006, ComponentAce
// http://www.componentace.com
// All rights reserved.

// Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:

// Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
// Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.
// Neither the name of ComponentAce nor the names of its contributors may be used to endorse or promote products derived from this software without specific prior written permission.
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

/*
Copyright (c) 2001 Lapo Luchini.

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
FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE AUTHORS
OR ANY CONTRIBUTORS TO THIS SOFTWARE BE LIABLE FOR ANY DIRECT, INDIRECT,
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
import ai.solace.zlib.common.Z_BUF_ERROR
import ai.solace.zlib.common.Z_NO_FLUSH
import ai.solace.zlib.common.Z_OK
import ai.solace.zlib.common.Z_STREAM_END
import ai.solace.zlib.deflate.ZStream
import ai.solace.zlib.streams.InputStream
import ai.solace.zlib.deflate.ZStreamException

class ZInputStream(input: InputStream) : DataInputStream(input) {
    private val z = ZStream()
    private val bufsize = 512
    private var flush = Z_NO_FLUSH // Use common constant
    private val buf = ByteArray(bufsize)
    private val buf1 = ByteArray(1)
    private var compress: Boolean
    private var nomoreinput = false

    var flushMode: Int
        get() = flush
        set(value) {
            flush = value
        }

    val totalIn: Long
        get() = z.total_in

    val totalOut: Long
        get() = z.total_out

    init {
        z.inflateInit()
        compress = false
        z.next_in = buf
        z.next_in_index = 0
        z.avail_in = 0
    }

    constructor(input: InputStream, level: Int) : this(input) {
        z.deflateInit(level)
        compress = true
        z.next_in = buf
        z.next_in_index = 0
        z.avail_in = 0
    }

    fun read(): Int {
        return if (read(buf1, 0, 1) == -1) -1 else (buf1[0].toInt() and 0xFF)
    }

    fun read(b: ByteArray, off: Int, len: Int): Int {
        if (len == 0) return 0
        var err: Int
        z.next_out = b
        z.next_out_index = off
        z.avail_out = len
        do {
            if (z.avail_in == 0 && !nomoreinput) {
                z.next_in_index = 0
                z.avail_in = SupportClass.readInput(`in`, buf, 0, bufsize)
                if (z.avail_in == -1) {
                    z.avail_in = 0
                    nomoreinput = true
                }
            }
            err = if (compress) z.deflate(flush) else z.inflate(flush)
            if (nomoreinput && err == Z_BUF_ERROR) return -1 // Use common constant
            if (err != Z_OK && err != Z_STREAM_END) // Use common constants
                throw ZStreamException((if (compress) "de" else "in") + "flating: " + z.msg)
            if (nomoreinput && z.avail_out == len) return -1
        } while (z.avail_out == len && err == Z_OK) // Use common constant
        return len - z.avail_out
    }

    fun skip(n: Long): Long {
        val len = 512.coerceAtMost(n.toInt())
        val tmp = ByteArray(len)
        return SupportClass.readInput(input, tmp, 0, tmp.size).toLong()
    }

    fun close() {
        `in`.close()
    }
}