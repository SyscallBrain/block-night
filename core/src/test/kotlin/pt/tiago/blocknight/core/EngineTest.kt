package pt.tiago.blocknight.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.tiago.blocknight.core.model.ActivePiece
import pt.tiago.blocknight.core.model.Board
import pt.tiago.blocknight.core.model.Cell
import pt.tiago.blocknight.core.model.Command
import pt.tiago.blocknight.core.model.Event
import pt.tiago.blocknight.core.model.GameState
import pt.tiago.blocknight.core.model.LossReason
import pt.tiago.blocknight.core.model.Mode
import pt.tiago.blocknight.core.model.Objective
import pt.tiago.blocknight.core.model.Outcome
import pt.tiago.blocknight.core.model.PieceType
import pt.tiago.blocknight.core.model.RisingGarbage
import pt.tiago.blocknight.core.model.Rules
import pt.tiago.blocknight.core.model.SpinKind
import pt.tiago.blocknight.core.model.StateHash
import pt.tiago.blocknight.core.random.BagRandomizer
import pt.tiago.blocknight.core.random.SplitMix64
import pt.tiago.blocknight.core.rules.Engine
import pt.tiago.blocknight.core.rules.GravityTable

/** Tabuleiro com linhas cheias de lixo nas colunas 0..8 (coluna 9 livre), para testes de limpeza. */
private fun wellRows(count: Int): Board = boardOf(*Array(count) { "GGGGGGGGG." }.reversedArray())

private fun step(state: GameState, vararg commands: Command): GameState = Engine.step(state, commands.toList()).state

class EngineTest {

    @Test
    fun `hard drop of O lands on the floor and scores two points per row`() {
        val s0 = Engine.newGame(rules(opening = listOf(PieceType.O)))
        assertEquals(ActivePiece(PieceType.O, 0, 4, 19), s0.active)
        val s1 = step(s0, Command.HARD_DROP)
        // Queda de 19 linhas a 2 pontos por linha, sem limpeza.
        assertEquals(38L, s1.score)
        assertEquals(1, s1.pieces)
        assertEquals(PieceType.O.code, s1.board.get(4, 0))
        assertEquals(PieceType.O.code, s1.board.get(5, 1))
        assertNull(s1.active)
        assertEquals(6, s1.are)
    }

    @Test
    fun `vertical I in the free column clears one line for 100 points at level 1`() {
        val start = Engine.newGame(rules(opening = listOf(PieceType.I), board = wellRows(1)))
        // Nascimento em (3,18), rotação horária para vertical em x=3 e 4 passos à direita: coluna 9.
        var s = step(start, Command.ROTATE_CW)
        s = step(s, Command.SHIFT_RIGHT, Command.SHIFT_RIGHT, Command.SHIFT_RIGHT, Command.SHIFT_RIGHT)
        val result = Engine.step(s, listOf(Command.HARD_DROP))
        val after = result.state
        // Queda de 18 linhas a 2 pontos = 36; linha simples a nível 1 = 100.
        assertEquals(136L, after.score)
        assertEquals(1, after.lines)
        assertEquals(PieceType.I.code, after.board.get(9, 0))
        assertEquals(0, after.board.get(0, 0))
        assertTrue(result.events.any { it is Event.LinesCleared && it.lines == 1 })
        // Limpeza de linha: ARE de 6 + 15 ticks.
        assertEquals(21, after.are)
    }

    @Test
    fun `piece on the floor locks after exactly thirty grounded ticks`() {
        val board = boardOf(*Array(18) { "GGGGGGGG.." }.reversedArray())
        var s = Engine.newGame(rules(speed = 15, opening = listOf(PieceType.O), board = board))
        var lockedAt = -1
        for (tick in 1..40) {
            s = step(s)
            if (s.pieces == 1 && lockedAt < 0) lockedAt = tick
        }
        assertEquals(30, lockedAt)
    }

    @Test
    fun `a successful move while grounded resets the lock delay`() {
        val board = boardOf(*Array(18) { "GGGGGGGG.." }.reversedArray())
        var s = Engine.newGame(rules(speed = 15, opening = listOf(PieceType.O), board = board))
        repeat(20) { s = step(s) }
        assertEquals(0, s.pieces)
        s = step(s, Command.SHIFT_LEFT)
        assertEquals(0, s.pieces)
        // 29 ticks mais sem bloquear; o 30.º bloqueia.
        repeat(28) { s = step(s) }
        assertEquals(0, s.pieces)
        s = step(s)
        assertEquals(1, s.pieces)
    }

    @Test
    fun `entry delay is six ticks when nothing clears`() {
        val board = boardOf(*Array(18) { "GGGGGGGG.." }.reversedArray())
        var s = Engine.newGame(rules(speed = 15, opening = listOf(PieceType.O, PieceType.T), board = board))
        while (s.pieces == 0) s = step(s)
        assertNull(s.active)
        assertEquals(6, s.are)
        repeat(5) {
            s = step(s)
            assertNull(s.active)
        }
        s = step(s)
        assertNotNull(s.active)
        assertEquals(PieceType.T, s.active!!.type)
    }

    @Test
    fun `block out when the spawn area is occupied`() {
        val blocked = Board.of(IntArray(400).also { cells -> for (x in 3..5) cells[20 * 10 + x] = Cell.GARBAGE })
        val s = Engine.newGame(rules(opening = listOf(PieceType.T), board = blocked))
        assertEquals(Outcome.Lost(LossReason.BLOCK_OUT), s.outcome)
    }

    @Test
    fun `lock out when the whole piece locks above row 19`() {
        val top = arrayOf("...GGG....") + Array(19) { ".GGGGGGGGG" }
        val s0 = Engine.newGame(rules(opening = listOf(PieceType.T), board = boardOf(*top)))
        assertNull(s0.outcome)
        val s1 = step(s0, Command.HARD_DROP)
        assertEquals(Outcome.Lost(LossReason.LOCK_OUT), s1.outcome)
    }

    @Test
    fun `campaign objective of one line is won by the same clear`() {
        val s0 = Engine.newGame(rules(mode = Mode.CAMPAIGN, opening = listOf(PieceType.I), board = wellRows(1), objective = Objective.Lines(1)))
        var s = step(s0, Command.ROTATE_CW)
        s = step(s, Command.SHIFT_RIGHT, Command.SHIFT_RIGHT, Command.SHIFT_RIGHT, Command.SHIFT_RIGHT)
        s = step(s, Command.HARD_DROP)
        assertEquals(Outcome.Won, s.outcome)
    }

    @Test
    fun `out of pieces is a loss when the objective is not met`() {
        val s0 = Engine.newGame(rules(mode = Mode.CAMPAIGN, opening = listOf(PieceType.I), board = wellRows(1), objective = Objective.Lines(5), maxPieces = 1))
        var s = step(s0, Command.ROTATE_CW)
        s = step(s, Command.SHIFT_RIGHT, Command.SHIFT_RIGHT, Command.SHIFT_RIGHT, Command.SHIFT_RIGHT)
        s = step(s, Command.HARD_DROP)
        assertEquals(Outcome.Lost(LossReason.OUT_OF_PIECES), s.outcome)
    }

    @Test
    fun `objective met on the last budgeted piece wins`() {
        val s0 = Engine.newGame(rules(mode = Mode.CAMPAIGN, opening = listOf(PieceType.I), board = wellRows(1), objective = Objective.Lines(1), maxPieces = 1))
        var s = step(s0, Command.ROTATE_CW)
        s = step(s, Command.SHIFT_RIGHT, Command.SHIFT_RIGHT, Command.SHIFT_RIGHT, Command.SHIFT_RIGHT)
        s = step(s, Command.HARD_DROP)
        assertEquals(Outcome.Won, s.outcome)
    }

    @Test
    fun `quad scores 800 and the next quad is back-to-back with combo`() {
        // Uma célula de lixo acima impede a limpeza total, que tem bónus próprio.
        val quadBoard = wellRows(4).withCell(0, 6, Cell.GARBAGE)
        val first = Engine.newGame(rules(opening = listOf(PieceType.I), board = quadBoard))
        val s1 = placeAt(first, ActivePiece(PieceType.I, 1, 7, 0))
        assertEquals(800L, s1.score)
        assertEquals(1, s1.quads)
        assertTrue(s1.backToBack)

        val s2 = placeAt(s1.copy(board = quadBoard), ActivePiece(PieceType.I, 1, 7, 0))
        // 800 × 1,5 de back-to-back = 1200, mais combo 50 × 1 × nível 1.
        assertEquals(800L + 1200L + 50L, s2.score)
        assertEquals(2, s2.quads)
    }

    @Test
    fun `clearing the whole board adds the perfect clear bonus`() {
        val first = Engine.newGame(rules(opening = listOf(PieceType.I), board = wellRows(4)))
        val s1 = placeAt(first, ActivePiece(PieceType.I, 1, 7, 0))
        // Quad 800 + limpeza total de 4 linhas a nível 1 (2000).
        assertEquals(800L + 2000L, s1.score)
        assertTrue(s1.board.isEmpty())
    }

    @Test
    fun `hold swaps once per piece and rejects a second request`() {
        val s0 = Engine.newGame(rules(opening = listOf(PieceType.T, PieceType.S, PieceType.Z)))
        val s1 = step(s0, Command.HOLD)
        assertEquals(PieceType.T, s1.hold)
        assertEquals(PieceType.S, s1.active!!.type)
        val s2 = Engine.step(s1, listOf(Command.HOLD)).let { result ->
            assertTrue(result.events.any { it is Event.HoldRejected })
            result.state
        }
        assertEquals(PieceType.S, s2.active!!.type)
    }

    @Test
    fun `hold is disabled when the variant forbids it`() {
        val s0 = Engine.newGame(rules(opening = listOf(PieceType.T, PieceType.S), allowHold = false))
        val result = Engine.step(s0, listOf(Command.HOLD))
        assertNull(result.state.hold)
        assertEquals(PieceType.T, result.state.active!!.type)
        assertTrue(result.events.any { it is Event.HoldRejected })
    }

    @Test
    fun `zen clears the bottom ten rows on block out instead of losing`() {
        val cells = IntArray(400)
        for (y in 0..9) for (x in 0..9) cells[y * 10 + x] = Cell.GARBAGE
        cells[20 * 10 + 4] = Cell.GARBAGE
        val s = Engine.newGame(rules(mode = Mode.ZEN, opening = listOf(PieceType.T), board = Board.of(cells)))
        assertNull(s.outcome)
        assertEquals(1, s.zenClears)
        assertEquals(Cell.GARBAGE, s.board.get(4, 10))
    }

    @Test
    fun `rising garbage inserts a row with one hole at the bottom and keeps the piece count`() {
        val rising = RisingGarbage(everyPieces = 1, startAfter = 0)
        val r = rules(mode = Mode.MARATHON, opening = listOf(PieceType.O)).copy(risingGarbage = rising)
        val s0 = Engine.newGame(r)
        val s1 = step(s0, Command.HARD_DROP)
        val hole = BagRandomizer.garbageHole(r.garbageSeed, 0)
        for (x in 0..9) {
            val expected = if (x == hole) Cell.EMPTY else Cell.GARBAGE
            assertEquals("x=$x", expected, s1.board.get(x, 0))
        }
        assertEquals(1, s1.garbageRows)
        assertFalse(s1.board.isEmpty())
    }

    @Test
    fun `ultra ends with time up on tick 7200`() {
        val s0 = Engine.newGame(rules(mode = Mode.ULTRA, opening = listOf(PieceType.O)))
        val s = step(s0.copy(ticks = Engine.ULTRA_TICKS - 1))
        assertEquals(Outcome.TimeUp, s.outcome)
        assertEquals(Engine.ULTRA_TICKS, s.ticks)
    }

    @Test
    fun `gravity table is frozen at the reference values`() {
        assertEquals(1092, GravityTable.perTick(1))
        assertEquals(1377, GravityTable.perTick(2))
        assertEquals(17026, GravityTable.perTick(10))
        assertEquals(154742, GravityTable.perTick(15))
    }

    @Test
    fun `identical command streams give identical state hashes on every tick`() {
        val r = rules(seed = 77L, speed = 4)
        fun run(): List<String> {
            val rng = SplitMix64(5L)
            var s = Engine.newGame(r)
            val hashes = ArrayList<String>()
            repeat(3000) {
                val commands = ArrayList<Command>()
                if (rng.nextBounded(5) == 0) commands += Command.entries[rng.nextBounded(Command.entries.size)]
                s = Engine.step(s, commands).state
                hashes += StateHash.of(s)
            }
            return hashes
        }
        assertEquals(run(), run())
    }

    @Test
    fun `lock out and the rest of the commands are ignored after the game ends`() {
        val top = arrayOf("...GGG....") + Array(19) { ".GGGGGGGGG" }
        val s0 = Engine.newGame(rules(opening = listOf(PieceType.T), board = boardOf(*top)))
        val finished = step(s0, Command.HARD_DROP)
        assertTrue(finished.isFinished)
        assertEquals(finished, step(finished, Command.SHIFT_LEFT))
    }
}
