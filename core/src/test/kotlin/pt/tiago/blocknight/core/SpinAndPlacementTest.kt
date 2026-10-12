package pt.tiago.blocknight.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.tiago.blocknight.core.model.ActivePiece
import pt.tiago.blocknight.core.model.Board
import pt.tiago.blocknight.core.model.Cell
import pt.tiago.blocknight.core.model.Mode
import pt.tiago.blocknight.core.model.Srs
import pt.tiago.blocknight.core.model.PieceType
import pt.tiago.blocknight.core.model.SpinKind
import pt.tiago.blocknight.core.model.StateHash
import pt.tiago.blocknight.core.random.SplitMix64
import pt.tiago.blocknight.core.rules.Engine
import pt.tiago.blocknight.core.search.PlacementFinder
import pt.tiago.blocknight.core.search.PlacementReplay

/** Os cantos do T com centro em (cx, cy) = (x+1, y+1): T com origem (3,5) tem centro (4,6). */
class SpinTest {
    private fun boardWith(vararg cells: Pair<Int, Int>): Board {
        val arr = IntArray(400)
        for ((x, y) in cells) arr[y * 10 + x] = Cell.GARBAGE
        return Board.of(arr)
    }

    private val t0 = ActivePiece(PieceType.T, 0, 3, 5)

    @Test
    fun `three corners with both front corners is a full T-spin`() {
        // Cantos (3,5), (5,5), (3,7), (5,7). Estado 0 aponta para cima: frente = (3,7) e (5,7).
        val board = boardWith(3 to 5, 3 to 7, 5 to 7)
        assertEquals(SpinKind.FULL, Engine.detectSpin(board, t0, lastWasRotation = true, lastKick = 0))
    }

    @Test
    fun `three corners with one front corner is a mini unless the fifth kick was used`() {
        val board = boardWith(3 to 5, 5 to 5, 3 to 7)
        assertEquals(SpinKind.MINI, Engine.detectSpin(board, t0, lastWasRotation = true, lastKick = 0))
        assertEquals(SpinKind.FULL, Engine.detectSpin(board, t0, lastWasRotation = true, lastKick = 4))
    }

    @Test
    fun `a move instead of a rotation never counts as a spin`() {
        val board = boardWith(3 to 5, 3 to 7, 5 to 7)
        assertEquals(SpinKind.NONE, Engine.detectSpin(board, t0, lastWasRotation = false, lastKick = -1))
    }

    @Test
    fun `fewer than three occupied corners is not a spin`() {
        val board = boardWith(3 to 7, 5 to 7)
        assertEquals(SpinKind.NONE, Engine.detectSpin(board, t0, lastWasRotation = true, lastKick = 0))
    }

    @Test
    fun `walls and floor count as occupied corners`() {
        // T em rotação R com origem x = -1: o centro (0, 6) tem o canto (-1, ·) fora da parede.
        // Frente = colunas 1 (cantos (1,5) e (1,7)), ambos ocupados.
        val atWall = ActivePiece(PieceType.T, 1, -1, 5)
        val board = boardWith(1 to 5, 1 to 7)
        assertEquals(SpinKind.FULL, Engine.detectSpin(board, atWall, lastWasRotation = true, lastKick = 0))
    }

    @Test
    fun `non T pieces never spin`() {
        val board = boardWith(3 to 5, 3 to 7, 5 to 7)
        assertEquals(SpinKind.NONE, Engine.detectSpin(board, t0.copy(type = PieceType.S), true, 0))
    }
}

class PlacementTest {

    @Test
    fun `every placement replays to the exact final cells`() {
        val state = Engine.newGame(rules(opening = listOf(PieceType.T), seed = 9L))
        val placements = PlacementFinder.find(state)
        assertTrue(placements.isNotEmpty())
        for (placement in placements) {
            val replayed = PlacementReplay.apply(state, placement)
            val p = placement.piece
            val cells = pieceCells(p)
            for ((x, y) in cells) {
                assertEquals("cell $x,$y for ${placement.path}", p.type.code, replayed.board.get(x, y))
            }
        }
    }

    @Test
    fun `placements exist for both flat and vertical I on an empty board`() {
        val state = Engine.newGame(rules(opening = listOf(PieceType.I), seed = 1L))
        val placements = PlacementFinder.find(state).filter { !it.hold }
        // A linha plana da I em rotação 0 está em y+1; no chão, y = -1.
        val flatBottom = placements.any { it.piece.rot % 2 == 0 && it.piece.y == -1 }
        val vertical = placements.any { it.piece.rot % 2 == 1 }
        assertTrue(flatBottom)
        assertTrue(vertical)
    }

    @Test
    fun `hold alternatives appear only when hold is allowed`() {
        val withHold = PlacementFinder.find(Engine.newGame(rules(opening = listOf(PieceType.T, PieceType.S))))
        assertTrue(withHold.any { it.hold })
        val noHold = PlacementFinder.find(Engine.newGame(rules(opening = listOf(PieceType.T, PieceType.S), allowHold = false)))
        assertFalse(noHold.any { it.hold })
    }

    @Test
    fun `random legal play is deterministic`() {
        fun play(choiceSeed: Long): List<String> {
            val rng = SplitMix64(choiceSeed)
            var state = Engine.newGame(rules(mode = Mode.MARATHON, seed = 123L, speed = 1))
            val hashes = ArrayList<String>()
            var guard = 0
            while (state.outcome == null && guard < 300) {
                val options = PlacementFinder.find(state)
                if (options.isEmpty()) break
                state = PlacementReplay.apply(state, options[rng.nextBounded(options.size)])
                hashes += StateHash.of(state)
                guard++
            }
            return hashes
        }
        val first = play(31L)
        assertTrue(first.size > 20)
        assertEquals(first, play(31L))
    }

    @Test
    fun `replaying a found path twice gives the same state`() {
        val state = Engine.newGame(rules(opening = listOf(PieceType.S, PieceType.Z, PieceType.O), seed = 4L))
        val placement = PlacementFinder.find(state).first()
        val a = PlacementReplay.apply(state, placement)
        val b = PlacementReplay.apply(state, placement)
        assertEquals(StateHash.of(a), StateHash.of(b))
    }

    private fun pieceCells(p: ActivePiece): List<Pair<Int, Int>> {
        val flat = Srs.cells(p.type, p.rot)
        return (0 until flat.size / 2).map { (p.x + flat[2 * it]) to (p.y + flat[2 * it + 1]) }
    }
}
