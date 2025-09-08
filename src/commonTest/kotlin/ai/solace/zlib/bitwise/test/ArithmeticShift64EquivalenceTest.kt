package ai.solace.zlib.bitwise.test

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ArithmeticShift64EquivalenceTest {
    private fun pow2(n: Int): Long {
        var r = 1L
        repeat(n) { r *= 2L }
        return r
    }

    // floor division by power of two using iterative, overflow-safe steps
    private fun floorDivPow2(
        x: Long,
        n: Int,
    ): Long {
        require(n in 1..63)
        var v = x
        repeat(n) {
            val q = v / 2
            v = if (v < 0 && v % 2L != 0L) q - 1 else q
        }
        return v
    }

    @Test
    fun leftShiftEqualsMultiply_fixedCases() {
        val values = listOf(0L, 1L, -1L, 42L, -42L, Long.MAX_VALUE / 2, Long.MIN_VALUE / 2, 0x1234_5678_9ABCDEFL)
        val shifts = listOf(1, 3, 7, 15, 31, 47, 63)
        for (v in values) {
            for (n in shifts) {
                val viaShift = v shl n
                val viaMul = v * pow2(n)
                assertEquals(viaShift, viaMul, "Left shift equivalence failed for v=$v, n=$n")
            }
        }
    }

    @Test
    fun rightShiftArithmeticEqualsFloorDiv_fixedCases() {
        val values = listOf(0L, 1L, -1L, 42L, -42L, -27L, -10L, Long.MAX_VALUE, Long.MIN_VALUE + 1)
        val shifts = listOf(1, 3, 7, 15, 31, 47, 63)
        for (v in values) {
            for (n in shifts) {
                val viaShift = v shr n
                val viaFloor = floorDivPow2(v, n)
                assertEquals(viaShift, viaFloor, "Right shift (arithmetic) equivalence failed for v=$v, n=$n")
            }
        }
    }

    @Test
    fun randomizedLeftShiftEqualsMultiply() {
        val rnd = Random(12345)
        repeat(10_000) {
            val v = rnd.nextLong()
            val n = (rnd.nextInt(63) + 1) // 1..63
            val viaShift = v shl n
            val viaMul = v * pow2(n)
            assertEquals(viaShift, viaMul)
        }
    }

    @Test
    fun randomizedRightShiftArithmeticEqualsFloorDiv() {
        val rnd = Random(54321)
        repeat(10_000) {
            val v = rnd.nextLong()
            val n = (rnd.nextInt(63) + 1) // 1..63
            val viaShift = v shr n
            val viaFloor = floorDivPow2(v, n)
            assertEquals(viaShift, viaFloor)
        }
    }

    @Test
    fun documentedExamplesMatch() {
        // -42 >> 3 == -6 and equals floor(-42 / 8)
        assertEquals(-6, (-42L) shr 3)
        assertEquals(-6, floorDivPow2(-42L, 3))

        // -27 >> 3 == -4
        assertEquals(-4, (-27L) shr 3)
        assertEquals(-4, floorDivPow2(-27L, 3))

        // -10 >> 2 == -3
        assertEquals(-3, (-10L) shr 2)
        assertEquals(-3, floorDivPow2(-10L, 2))

        // Basic sanity for positives
        assertTrue((42L shr 3) == 42L / 8L)
        assertTrue((42L shl 3) == 42L * 8L)
    }
}
