package pt.tiago.blocknight.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pt.tiago.blocknight.core.model.ClearKind
import pt.tiago.blocknight.core.model.Event
import pt.tiago.blocknight.core.model.PieceType
import pt.tiago.blocknight.core.model.SpinKind

class LabelTimerTest {
    @Test
    fun `a label disappears after 900 ms`() {
        val t = LabelTimer()
        t.onEvents(listOf(Event.LinesCleared(4, ClearKind.QUAD, 800)), 1_000L)
        assertEquals(ClearLabel.Quad, t.visible(1_000L + 899_000_000L))
        assertNull(t.visible(1_000L + 900_000_000L))
        assertNull(t.visible(5_000_000_000L))
    }

    @Test
    fun `events without a label keep the current one`() {
        val t = LabelTimer()
        t.onEvents(listOf(Event.PerfectClear), 0L)
        t.onEvents(listOf(Event.Locked(PieceType.O, SpinKind.NONE)), 100L)
        assertEquals(ClearLabel.Perfect, t.visible(200L))
    }

    @Test
    fun `the spin label follows the kind and the lines`() {
        assertEquals(ClearLabel.Spin(mini = false, lines = 1), LabelTimer.pick(listOf(Event.SpinScored(SpinKind.FULL, 1))))
        assertEquals(ClearLabel.Spin(mini = true, lines = 0), LabelTimer.pick(listOf(Event.SpinScored(SpinKind.MINI, 0))))
        assertNull(LabelTimer.pick(listOf(Event.SpinScored(SpinKind.NONE, 1))))
    }

    @Test
    fun `the rarest label of one lock wins`() {
        val events = listOf(
            Event.LinesCleared(4, ClearKind.QUAD, 800),
            Event.ComboReached(3),
            Event.BackToBack(400),
            Event.PerfectClear,
        )
        assertEquals(ClearLabel.Perfect, LabelTimer.pick(events))
        assertEquals(ClearLabel.Quad, LabelTimer.pick(events.take(3)))
        assertNull(LabelTimer.pick(listOf(Event.ComboReached(1), Event.LinesCleared(1, ClearKind.SINGLE, 100))))
    }
}
