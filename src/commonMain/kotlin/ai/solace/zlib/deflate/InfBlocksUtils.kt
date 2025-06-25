package ai.solace.zlib.deflate

import ai.solace.zlib.common.*

// Utility functions for InfBlocks operations

// copy as much as possible from the sliding window to the output area
internal fun inflateFlush(s: InfBlocks, z: ZStream, rIn: Int): Int {
    var r = rIn
    var n: Int
    var q: Int

    // local copies of source and destination pointers
    var p: Int = z.nextOutIndex
    q = s.read

    // compute number of bytes to copy as far as end of window
    n = (if (q <= s.write) s.write else s.end) - q
    if (n > z.availOut) n = z.availOut
    if (n != 0 && r == Z_BUF_ERROR) r = Z_OK

    // update counters
    z.availOut -= n
    z.totalOut += n.toLong()

    // update check information
    if (s.checkfn != null) {
        s.check = z.adlerChecksum!!.adler32(s.check, s.window, q, n)
        z.adler = s.check
    }


    // copy as far as end of window
    s.window.copyInto(z.nextOut!!, p, q, q + n)
    p += n
    q += n

    // see if more to copy at beginning of window
    if (q == s.end) {
        // wrap pointers
        q = 0
        if (s.write == s.end) s.write = 0

        // compute bytes to copy
        n = s.write - q
        if (n > z.availOut) n = z.availOut
        if (n != 0 && r == Z_BUF_ERROR) r = Z_OK

        // update counters
        z.availOut -= n
        z.totalOut += n.toLong()

        // update check information
        if (s.checkfn != null) {
            s.check = z.adlerChecksum!!.adler32(s.check, s.window, q, n)
            z.adler = s.check
        }


        // copy
        s.window.copyInto(z.nextOut!!, p, q, q + n)
        p += n
        q += n
    }

    // update pointers
    z.nextOutIndex = p
    s.read = q

    // done
    return r
}
