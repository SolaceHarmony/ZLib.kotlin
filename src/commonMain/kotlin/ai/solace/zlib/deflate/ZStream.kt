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

package componentace.compression.libs.zlib.deflate

import ai.solace.zlib.common.* // Import all constants
import kotlin.compareTo

class ZStream {

    // All constants previously defined here (MAX_WBITS, DEF_WBITS, Z_NO_FLUSH, etc., MAX_MEM_LEVEL, Z_OK, etc.)
    // are now expected to be used from ai.solace.zlib.common.Constants

    var next_in: ByteArray? = null // next input byte
    var next_in_index = 0
    var avail_in = 0 // number of bytes available at next_in
    var total_in: Long = 0 // total nb of input bytes read so far

    var next_out: ByteArray? = null // next output byte should be put there
    var next_out_index = 0
    var avail_out = 0 // remaining free space at next_out
    var total_out: Long = 0 // total nb of bytes output so far

    var msg: String? = null

    internal var dstate: Deflate? = null
    internal var istate: Inflate? = null

    internal var data_type = 0 // best guess about the data type: ascii or binary

    var adler: Long = 0
    internal var _adler: Adler32? = Adler32()

    fun inflateInit(): Int {
        return inflateInit(MAX_WBITS) // DEF_WBITS was MAX_WBITS
    }

    fun inflateInit(w: Int): Int {
        istate = Inflate()
        return istate!!.inflateInit(this, w)
    }

    fun inflate(f: Int): Int {
        if (istate == null) return Z_STREAM_ERROR
        return istate!!.inflate(this, f)
    }

    fun inflateEnd(): Int {
        if (istate == null) return Z_STREAM_ERROR
        val ret = istate!!.inflateEnd(this)
        istate = null
        return ret
    }

    fun inflateSync(): Int {
        if (istate == null) return Z_STREAM_ERROR
        return istate!!.inflateSync(this)
    }

    fun inflateSetDictionary(dictionary: ByteArray, dictLength: Int): Int {
        if (istate == null) return Z_STREAM_ERROR
        return istate!!.inflateSetDictionary(this, dictionary, dictLength)
    }

    fun deflateInit(level: Int): Int {
        return deflateInit(level, MAX_WBITS)
    }

    fun deflateInit(level: Int, bits: Int): Int {
        dstate = Deflate()
        return dstate!!.deflateInit(this, level, bits)
    }

    fun deflate(flush: Int): Int {
        if (dstate == null) {
            return Z_STREAM_ERROR
        }
        return dstate!!.deflate(this, flush)
    }

    fun deflateEnd(): Int {
        if (dstate == null) return Z_STREAM_ERROR
        val ret = dstate!!.deflateEnd()
        dstate = null
        return ret
    }

    fun deflateParams(level: Int, strategy: Int): Int {
        if (dstate == null) return Z_STREAM_ERROR
        return dstate!!.deflateParams(this, level, strategy)
    }

    fun deflateSetDictionary(dictionary: ByteArray, dictLength: Int): Int {
        if (dstate == null) return Z_STREAM_ERROR
        return dstate!!.deflateSetDictionary(this, dictionary, dictLength)
    }

    // Flush as much pending output as possible. All deflate() output goes
    // through this function so some applications may wish to modify it
    // to avoid allocating a large strm->next_out buffer and copying into it.
    // (See also read_buf()).
    internal fun flush_pending() {
        var len = dstate!!.pending

        if (len compareTo avail_out) len = avail_out
        if (len == 0) return

        if (dstate!!.pending_buf.size.compareTo(dstate!!.pending_out) || next_out!!.size <= next_out_index || dstate!!.pending_buf.size.compareTo(
                dstate!!.pending_out + len
            ) || next_out!!.size < next_out_index + len) {
            //System.Console.Out.WriteLine(dstate.pending_buf.Length + ", " + dstate.pending_out + ", " + next_out.Length + ", " + next_out_index + ", " + len);
            //System.Console.Out.WriteLine("avail_out=" + avail_out);
        }

        dstate!!.pending_buf.copyInto(next_out!!, next_out_index, dstate!!.pending_out, dstate!!.pending_out + len)

        next_out_index += len
        dstate!!.pending_out += len
        total_out += len.toLong()
        avail_out -= len
        dstate!!.pending -= len
        if (dstate!!.pending == 0) {
            dstate!!.pending_out = 0
        }
    }

    // Read a new buffer from the current input stream, update the adler32
    // and total number of bytes read.  All deflate() input goes through
    // this function so some applications may wish to modify it to avoid
    // allocating a large strm->next_in buffer and copying from it.
    // (See also flush_pending()).
    internal fun read_buf(buf: ByteArray, start: Int, size: Int): Int {
        var len = avail_in

        if (len > size) len = size
        if (len == 0) return 0

        avail_in -= len

        if (dstate!!.noheader == 0) {
            adler = _adler!!.adler32(adler, next_in, next_in_index, len)
        }
        next_in!!.copyInto(buf, start, next_in_index, next_in_index + len)
        next_in_index += len
        total_in += len.toLong()
        return len
    }

    fun free() {
        next_in = null
        next_out = null
        msg = null
        _adler = null
    }
}