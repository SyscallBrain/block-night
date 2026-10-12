package pt.tiago.blocknight.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.tiago.blocknight.core.model.Board
import pt.tiago.blocknight.core.model.Command
import pt.tiago.blocknight.core.model.Mode
import pt.tiago.blocknight.core.model.PieceType
import pt.tiago.blocknight.core.model.Rules
import pt.tiago.blocknight.core.rules.Engine

class GameSessionTest {
    private val rules = Rules(
        mode = Mode.CAMPAIGN, speedLevel = 1, previewCount = 3, allowHold = true, seed = 3L, garbageSeed = 4L,
        opening = listOf(PieceType.O), initialBoard = Board.empty(), objective = null, maxPieces = null, risingGarbage = null,
    )

    private val tick = GameSession.TICK_NANOS

    @Test
    fun `a new session counts down before the first tick`() {
        val s = GameSession(Engine.newGame(rules))
        s.frame(0L)
        assertEquals(3, s.countdownDigit())
        s.frame(GameSession.COUNTDOWN_NANOS)
        assertEquals(GameSession.Phase.PLAYING, s.phase)
        assertEquals(null, s.countdownDigit())
    }

    @Test
    fun `a frame never runs more than five ticks even after a long stall`() {
        val s = GameSession(Engine.newGame(rules), GameSession.Phase.PLAYING)
        s.frame(0L)
        val before = s.state.ticks
        s.frame(10_000_000_000L)
        assertEquals(GameSession.MAX_TICKS_PER_FRAME.toLong(), s.state.ticks - before)
    }

    @Test
    fun `ticks follow elapsed time at 60 Hz`() {
        val s = GameSession(Engine.newGame(rules), GameSession.Phase.PLAYING)
        s.frame(0L)
        // Quadros de 1 tick: o tempo decorrido converte-se em ticks sem perda nem avanço extra.
        for (frame in 1..10) s.frame(tick * frame + 1)
        assertEquals(10L, s.state.ticks)
    }

    @Test
    fun `paused sessions do not advance the game`() {
        val s = GameSession(Engine.newGame(rules), GameSession.Phase.PLAYING)
        s.frame(0L)
        s.frame(tick * 3)
        val ticks = s.state.ticks
        s.pause()
        s.frame(tick * 30)
        assertEquals(ticks, s.state.ticks)
        assertEquals(GameSession.Phase.PAUSED, s.phase)
    }

    @Test
    fun `resuming starts a three second countdown`() {
        val s = GameSession(Engine.newGame(rules), GameSession.Phase.PAUSED)
        s.resume()
        assertEquals(GameSession.Phase.COUNTDOWN, s.phase)
        s.frame(0L)
        s.frame(GameSession.COUNTDOWN_NANOS / 2)
        assertEquals(GameSession.Phase.COUNTDOWN, s.phase)
    }

    @Test
    fun `commands are applied on the next tick and events are returned once`() {
        val s = GameSession(Engine.newGame(rules), GameSession.Phase.PLAYING)
        s.frame(0L)
        s.command(Command.HARD_DROP)
        val events = s.frame(tick)
        assertTrue(events.isNotEmpty())
        assertEquals(1, s.state.pieces)
        assertTrue(s.frame(tick * 2).none { it.toString().contains("Locked") })
    }
}
