package pt.tiago.blocknight

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NavigationTest {
    private val all = listOf(
        Screen.Home, Screen.District(7), Screen.Free, Screen.FreeGame("marathon", 4, 12, resume = true),
        Screen.Daily, Screen.DailyGame(321, "hard", 3, "2026-10-12"), Screen.Game(125, "medium", 9),
        Screen.Stats, Screen.Settings,
    )

    @Test
    fun `every screen survives a save and restore`() {
        for (screen in all) assertEquals(screen, ScreenCodec.decode(ScreenCodec.encode(screen)))
    }

    @Test
    fun `damaged saved text falls back to the home`() {
        assertEquals(Screen.Home, ScreenCodec.decode(""))
        assertEquals(Screen.Home, ScreenCodec.decode("game|x"))
        assertEquals(Screen.Home, ScreenCodec.decode("nonsense"))
    }

    @Test
    fun `back from a sub-screen returns home and never leaves a match`() {
        assertNull(backTarget(Screen.Home))
        assertEquals(Screen.Home, backTarget(Screen.District(3)))
        assertEquals(Screen.Home, backTarget(Screen.Settings))
        assertEquals(Screen.Home, backTarget(Screen.Stats))
        assertEquals(Screen.Home, backTarget(Screen.Free))
        assertEquals(Screen.Home, backTarget(Screen.Daily))
        for (match in all.filter { it.isMatch }) assertNull(backTarget(match))
    }
}
