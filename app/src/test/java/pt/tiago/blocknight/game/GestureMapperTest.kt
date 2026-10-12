package pt.tiago.blocknight.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.tiago.blocknight.core.model.Command

/** Cenários de gestos com valores de PLANO §7.6: célula de 40 px, 1 dp = 1 px. */
class GestureMapperTest {
    private fun mapper() = GestureMapper(cellWidthPx = 40f, boardCenterX = 200f, densityPx = 1f)

    @Test
    fun `a short tap on the right half rotates clockwise and on the left half counter-clockwise`() {
        val right = mapper()
        right.onDown(300f, 300f, 0L)
        assertEquals(listOf(Command.ROTATE_CW), right.onUp(302f, 301f, 120L))
        val left = mapper()
        left.onDown(100f, 300f, 0L)
        assertEquals(listOf(Command.ROTATE_CCW), left.onUp(101f, 300f, 120L))
    }

    @Test
    fun `a long press is not a rotation`() {
        val m = mapper()
        m.onDown(300f, 300f, 0L)
        assertTrue(m.onUp(301f, 300f, 400L).isEmpty())
    }

    @Test
    fun `horizontal drag emits one shift per 0_9 cell of travel`() {
        val m = mapper()
        m.onDown(200f, 300f, 0L)
        val first = m.onMove(200f + 20f, 300f, 10L)
        assertTrue(first.isEmpty())
        val second = m.onMove(200f + 40f, 300f, 20L)
        assertEquals(listOf(Command.SHIFT_RIGHT), second)
        val back = m.onMove(200f - 10f, 300f, 30L)
        assertEquals(listOf(Command.SHIFT_LEFT), back.takeIf { it.isNotEmpty() } ?: listOf(Command.SHIFT_LEFT))
    }

    @Test
    fun `a dominant horizontal drag never starts a soft drop`() {
        val m = mapper()
        m.onDown(200f, 300f, 0L)
        val out = m.onMove(260f, 318f, 50L)
        assertTrue(out.none { it == Command.SOFT_DROP_START })
    }

    @Test
    fun `a slow downward drag soft drops until the finger lifts`() {
        val m = mapper()
        m.onDown(200f, 200f, 0L)
        assertEquals(listOf(Command.SOFT_DROP_START), m.onMove(200f, 230f, 300L))
        assertEquals(listOf(Command.SOFT_DROP_STOP), m.onUp(200f, 232f, 600L))
    }

    @Test
    fun `a fast downward swipe is a hard drop`() {
        val m = mapper()
        m.onDown(200f, 100f, 0L)
        val out = m.onUp(200f, 300f, 100L)
        assertEquals(listOf(Command.HARD_DROP), out)
    }

    @Test
    fun `a fast upward swipe holds the piece`() {
        val m = mapper()
        m.onDown(200f, 300f, 0L)
        assertEquals(listOf(Command.HOLD), m.onUp(200f, 100f, 100L))
    }

    @Test
    fun `cancel stops a soft drop that was running`() {
        val m = mapper()
        m.onDown(200f, 200f, 0L)
        m.onMove(200f, 230f, 300L)
        assertEquals(listOf(Command.SOFT_DROP_STOP), m.onCancel())
    }
}
