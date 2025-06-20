package ai.solace.zlib.deflate

import ai.solace.zlib.common.*

// Utility functions for InfBlocks operations

// copy as much as possible from the sliding window to the output area
internal fun inflate_flush(s: InfBlocks, z: ZStream, r_in: Int): Int {
    var r = r_in
    var n: Int
    var p: Int
    var q: Int

    // local copies of source and destination pointers
    p = z.next_out_index
    q = s.read

    // compute number of bytes to copy as far as end of window
    n = (if (q <= s.write) s.write else s.end) - q
    if (n > z.avail_out) n = z.avail_out
    if (n != 0 && r == Z_BUF_ERROR) r = Z_OK

    // update counters
    z.avail_out -= n
    z.total_out += n.toLong()

    // update check information
    if (s.checkfn != null) z.adler = s.check.apply { s.check = z._adler!!.adler32(s.check, s.window, q, n) }


    // copy as far as end of window
    s.window.copyInto(z.next_out!!, p, q, q + n)
    p += n
    q += n

    // see if more to copy at beginning of window
    if (q == s.end) {
        // wrap pointers
        q = 0
        if (s.write == s.end) s.write = 0

        // compute bytes to copy
        n = s.write - q
        if (n > z.avail_out) n = z.avail_out
        if (n != 0 && r == Z_BUF_ERROR) r = Z_OK

        // update counters
        z.avail_out -= n
        z.total_out += n.toLong()

        // update check information
        if (s.checkfn != null) z.adler = s.check.apply { s.check = z._adler!!.adler32(s.check, s.window, q, n) }


        // copy
        s.window.copyInto(z.next_out!!, p, q, q + n)
        p += n
        q += n
    }

    // update pointers
    z.next_out_index = p
    s.read = q

    // done
    return r
}
