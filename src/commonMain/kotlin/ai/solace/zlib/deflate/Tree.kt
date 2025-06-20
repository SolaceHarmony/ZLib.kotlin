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

THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESS OR IMPLIED WARRANTIES,
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

class Tree internal constructor() {
    companion object {
        // Constants previously defined here are now in ai.solace.zlib.common.Constants
        // MAX_BITS, BL_CODES, D_CODES, LITERALS, LENGTH_CODES, L_CODES, HEAP_SIZE,
        // MAX_BL_BITS (now TREE_MAX_BL_BITS), END_BLOCK, REP_3_6, REPZ_3_10, REPZ_11_138,
        // Buf_size (now BUF_SIZE), DIST_CODE_LEN (now TREE_DIST_CODE_LEN)
        // Arrays: extra_lbits (TREE_EXTRA_LBITS), extra_dbits (TREE_EXTRA_DBITS),
        // extra_blbits (TREE_EXTRA_BLBITS), bl_order (TREE_BL_ORDER),
        // _dist_code (TREE_DIST_CODE), _length_code (TREE_LENGTH_CODE),
        // base_length (TREE_BASE_LENGTH), base_dist (TREE_BASE_DIST)


        // Mapping from a distance to a distance code. dist is the distance - 1 and
        // must not have side effects. _dist_code[256] and _dist_code[257] are never
        // used.
        // d_code, gen_codes, and bi_reverse were moved to TreeUtils.kt
    }

    lateinit var dyn_tree: ShortArray // the dynamic tree
    var max_code: Int = 0 // largest code with non zero frequency
    internal lateinit var stat_desc: StaticTree // the corresponding static tree

    // Compute the optimal bit lengths for a tree and update the total bit length
    // for the current block.
    // IN assertion: the fields freq and dad are set, heap[heap_max] and
    //    above are the tree nodes sorted by increasing frequency.
    // OUT assertions: the field len is set to the optimal bit length, the
    //     array bl_count contains the frequencies for each bit length.
    //     The length opt_len is updated; static_len is also updated if stree is
    //     not null.
    // gen_bitlen moved to TreeUtils.kt; called via wrapper below
    // Call wrapper:
    internal fun gen_bitlen(s: Deflate) {
        // Calls TreeUtils.gen_bitlen (same package)
        gen_bitlen(this, s)
    }

    // Construct one Huffman tree and assigns the code bit strings and lengths.
    // Update the total bit length for the current block.
    // IN assertion: the field freq is set for all tree elements.
    // OUT assertions: the fields len and code are set to the optimal bit length
    //     and corresponding code. The length opt_len is updated; static_len is
    //     also updated if stree is not null. The field max_code is set.
    // build_tree moved to TreeUtils.kt; called via wrapper below
    // Call wrapper:
    internal fun build_tree(s: Deflate) {
        // Calls TreeUtils.build_tree (same package)
        build_tree(this, s)
    }
}