package pt.tiago.blocknight.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CampaignHudTest {
    @Test
    fun `reachable stars drop as pieces are used, counting the active piece`() {
        assertEquals(3, CampaignHud.reachableStars(piecesLocked = 35, hasActive = true, threeStarMax = 36, twoStarMax = 39))
        assertEquals(2, CampaignHud.reachableStars(piecesLocked = 36, hasActive = true, threeStarMax = 36, twoStarMax = 39))
        assertEquals(1, CampaignHud.reachableStars(piecesLocked = 39, hasActive = true, threeStarMax = 36, twoStarMax = 39))
        assertEquals(3, CampaignHud.reachableStars(piecesLocked = 36, hasActive = false, threeStarMax = 36, twoStarMax = 39))
    }

    @Test
    fun `pieces left include the active piece and never go negative`() {
        assertEquals(42, CampaignHud.piecesLeft(42, 0))
        assertEquals(1, CampaignHud.piecesLeft(42, 41))
        assertEquals(0, CampaignHud.piecesLeft(42, 50))
        assertNull(CampaignHud.piecesLeft(null, 3))
    }
}
