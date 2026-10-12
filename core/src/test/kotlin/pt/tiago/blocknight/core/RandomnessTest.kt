package pt.tiago.blocknight.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import pt.tiago.blocknight.core.model.PieceType
import pt.tiago.blocknight.core.random.BagRandomizer
import pt.tiago.blocknight.core.random.SplitMix64

class RandomnessTest {
    @Test
    fun `splitmix64 matches the reference output for seed zero`() {
        val rng = SplitMix64(0L)
        assertEquals(0xE220A8397B1DCDAFuL.toLong(), rng.nextLong())
        assertEquals(0x6E789E6AA1B965F4uL.toLong(), rng.nextLong())
    }

    @Test
    fun `bounded draws stay inside the range and cover it`() {
        val rng = SplitMix64(42L)
        val seen = BooleanArray(7)
        repeat(2000) { seen[rng.nextBounded(7)] = true }
        assertEquals(true, seen.all { it })
    }

    @Test
    fun `every bag is a permutation of the seven pieces`() {
        for (seed in listOf(1L, 99L, -5L)) {
            for (bag in 0 until 1000) {
                val piece = BagRandomizer.bagPermutation(seed, bag)
                assertEquals(PieceType.entries.toSet(), piece.toSet())
                assertEquals(7, piece.size)
            }
        }
    }

    @Test
    fun `sequence is a pure function of seed and index`() {
        val rules = rules(seed = 123L, opening = listOf(PieceType.T, PieceType.S))
        val first = (0 until 700).map { BagRandomizer.pieceAt(rules, it) }
        val second = (0 until 700).map { BagRandomizer.pieceAt(rules, it) }
        assertEquals(first, second)
        assertEquals(PieceType.T, first[0])
        assertEquals(PieceType.S, first[1])
    }

    @Test
    fun `different seeds give different sequences`() {
        val a = (0 until 70).map { BagRandomizer.pieceAt(rules(seed = 1L), it) }
        val b = (0 until 70).map { BagRandomizer.pieceAt(rules(seed = 2L), it) }
        assertNotEquals(a, b)
    }
}
