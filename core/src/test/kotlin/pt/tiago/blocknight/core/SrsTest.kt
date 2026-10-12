package pt.tiago.blocknight.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.tiago.blocknight.core.model.PieceType
import pt.tiago.blocknight.core.model.Srs

class SrsTest {
    private fun cellsOf(type: PieceType, rot: Int): Set<Pair<Int, Int>> {
        val flat = Srs.cells(type, rot)
        return (0 until flat.size / 2).map { flat[2 * it] to flat[2 * it + 1] }.toSet()
    }

    @Test
    fun `spawn states match the SRS reference shapes`() {
        // Coordenadas da caixa (x, y) com y para cima, a partir da especificação SRS.
        assertEquals(setOf(1 to 2, 0 to 1, 1 to 1, 2 to 1), cellsOf(PieceType.T, 0))
        assertEquals(setOf(0 to 2, 0 to 1, 1 to 1, 2 to 1), cellsOf(PieceType.J, 0))
        assertEquals(setOf(2 to 2, 0 to 1, 1 to 1, 2 to 1), cellsOf(PieceType.L, 0))
        assertEquals(setOf(1 to 2, 2 to 2, 0 to 1, 1 to 1), cellsOf(PieceType.S, 0))
        assertEquals(setOf(0 to 2, 1 to 2, 1 to 1, 2 to 1), cellsOf(PieceType.Z, 0))
        assertEquals(setOf(0 to 1, 1 to 1, 2 to 1, 3 to 1), cellsOf(PieceType.I, 0))
        assertEquals(setOf(0 to 0, 1 to 0, 0 to 1, 1 to 1), cellsOf(PieceType.O, 0))
    }

    @Test
    fun `rotation states follow the clockwise rule for JLSTZ`() {
        // R do T: coluna central com a ponta à direita (.X. / .XX / .X.), vista de cima para baixo.
        assertEquals(setOf(1 to 2, 1 to 1, 2 to 1, 1 to 0), cellsOf(PieceType.T, 1))
        // 180° do T: a ponta fica em baixo.
        assertEquals(setOf(1 to 0, 0 to 1, 1 to 1, 2 to 1), cellsOf(PieceType.T, 2))
        // Cada estado tem exatamente quatro células distintas.
        for (type in listOf(PieceType.T, PieceType.S, PieceType.Z, PieceType.J, PieceType.L, PieceType.I)) {
            for (rot in 0..3) assertEquals("${type.name} rot $rot", 4, cellsOf(type, rot).size)
        }
    }

    @Test
    fun `I rotation pivots on its own 4x4 box`() {
        assertEquals(setOf(2 to 0, 2 to 1, 2 to 2, 2 to 3), cellsOf(PieceType.I, 1))
        assertEquals(setOf(0 to 2, 1 to 2, 2 to 2, 3 to 2), cellsOf(PieceType.I, 2))
        assertEquals(setOf(1 to 0, 1 to 1, 1 to 2, 1 to 3), cellsOf(PieceType.I, 3))
    }

    @Test
    fun `kick tables have five tests and the first is the origin`() {
        for (type in listOf(PieceType.T, PieceType.I)) {
            for (from in 0..3) for (to in listOf((from + 1) % 4, (from + 3) % 4)) {
                val tests = Srs.kicks(type, from, to)
                assertEquals("${type.name} $from→$to", 5, tests.size)
                assertArrayEquals(intArrayOf(0, 0), tests[0])
            }
        }
    }

    @Test
    fun `JLSTZ and I kick values match the reference table`() {
        assertArrayEquals(arrayOf(intArrayOf(0, 0), intArrayOf(-1, 0), intArrayOf(-1, 1), intArrayOf(0, -2), intArrayOf(-1, -2)),
            Srs.kicks(PieceType.J, 0, 1))
        assertArrayEquals(arrayOf(intArrayOf(0, 0), intArrayOf(-1, 0), intArrayOf(-1, -1), intArrayOf(0, 2), intArrayOf(-1, 2)),
            Srs.kicks(PieceType.L, 3, 0))
        assertArrayEquals(arrayOf(intArrayOf(0, 0), intArrayOf(1, 0), intArrayOf(1, 1), intArrayOf(0, -2), intArrayOf(1, -2)),
            Srs.kicks(PieceType.T, 0, 3))
        assertArrayEquals(arrayOf(intArrayOf(0, 0), intArrayOf(-2, 0), intArrayOf(1, 0), intArrayOf(-2, -1), intArrayOf(1, 2)),
            Srs.kicks(PieceType.I, 0, 1))
        assertArrayEquals(arrayOf(intArrayOf(0, 0), intArrayOf(-1, 0), intArrayOf(2, 0), intArrayOf(-1, 2), intArrayOf(2, -1)),
            Srs.kicks(PieceType.I, 0, 3))
    }

    @Test
    fun `spawn origin keeps pieces in columns 3 to 5 and I and O centred`() {
        assertEquals(3, Srs.spawnX(PieceType.T))
        assertEquals(3, Srs.spawnX(PieceType.I))
        assertEquals(4, Srs.spawnX(PieceType.O))
        assertTrue(Srs.spawnY(PieceType.T) == 19)
    }
}
