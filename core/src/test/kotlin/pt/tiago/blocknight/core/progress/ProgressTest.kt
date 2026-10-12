package pt.tiago.blocknight.core.progress

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressTest {
    private fun result(
        id: String,
        pieces: Int,
        won: Boolean = true,
        score: Long = 1000,
        difficulty: String = "medium",
        floor: String = "floor-0001",
    ) = FloorResult(id, floor, difficulty, won, pieces, score, threeStarMaxPieces = 36, twoStarMaxPieces = 39)

    @Test
    fun `stars follow the piece thresholds at their exact boundaries`() {
        assertEquals(3, ProgressReducer.stars(result("a", 36)))
        assertEquals(2, ProgressReducer.stars(result("b", 37)))
        assertEquals(2, ProgressReducer.stars(result("c", 39)))
        assertEquals(1, ProgressReducer.stars(result("d", 40)))
        assertEquals(0, ProgressReducer.stars(result("e", 10, won = false)))
    }

    @Test
    fun `the same session never counts twice`() {
        val once = ProgressReducer.apply(Profile(), result("s1", 30))
        val twice = ProgressReducer.apply(once, result("s1", 30))
        assertEquals(once, twice)
    }

    @Test
    fun `a worse replay keeps the best stars, pieces and score`() {
        var p = ProgressReducer.apply(Profile(), result("s1", 30, score = 5000))
        p = ProgressReducer.apply(p, result("s2", 39, score = 9000))
        val record = p.record("medium", "floor-0001")
        assertEquals(3, record.stars)
        assertEquals(30, record.bestPieces)
        assertEquals(9000L, record.bestScore)
    }

    @Test
    fun `losing never removes stars`() {
        var p = ProgressReducer.apply(Profile(), result("s1", 30))
        p = ProgressReducer.apply(p, result("s2", 5, won = false))
        assertEquals(3, p.record("medium", "floor-0001").stars)
    }

    @Test
    fun `difficulties keep separate progress`() {
        var p = ProgressReducer.apply(Profile(), result("s1", 30, difficulty = "easy"))
        assertEquals(0, p.record("hard", "floor-0001").stars)
        assertTrue(p.record("easy", "floor-0001").completed)
        p = ProgressReducer.apply(p, result("s2", 30, difficulty = "hard"))
        assertEquals(3, p.record("hard", "floor-0001").stars)
        assertEquals(3, p.record("easy", "floor-0001").stars)
    }

    @Test
    fun `floors unlock in order inside a difficulty`() {
        val id: (Int) -> String = { "floor-%04d".format(it) }
        var p = Profile()
        assertTrue(p.isUnlocked("medium", 1, id))
        assertFalse(p.isUnlocked("medium", 2, id))
        p = ProgressReducer.apply(p, result("s1", 30))
        assertTrue(p.isUnlocked("medium", 2, id))
        assertFalse(p.isUnlocked("easy", 2, id))
    }

    @Test
    fun `applied results are bounded`() {
        var p = Profile()
        repeat(Profile.APPLIED_LIMIT + 10) { p = ProgressReducer.apply(p, result("s$it", 30, floor = "floor-0002")) }
        assertEquals(Profile.APPLIED_LIMIT, p.appliedResults.size)
    }
}
