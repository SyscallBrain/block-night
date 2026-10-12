package pt.tiago.blocknight.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.tiago.blocknight.core.model.Board
import pt.tiago.blocknight.core.model.Cell
import pt.tiago.blocknight.core.model.PieceType

class GameLayoutTest {
    /** (width dp, height dp of the board area, density) for common phones and awkward densities. */
    private val screens = listOf(
        Triple(387f, 620f, 2.625f),
        Triple(387f, 420f, 2.625f),
        Triple(336f, 380f, 2.0f),
        Triple(392f, 700f, 2.75f),
        Triple(360f, 560f, 3.0f),
        Triple(411f, 640f, 1.75f),
    )

    @Test
    fun `cell size is a whole number of pixels and the board always fits its area`() {
        for ((wDp, hDp, density) in screens) {
            val wPx = wDp * density
            val hPx = hDp * density
            val cell = GameLayout.cellPx(wPx, hPx, density)
            val boardH = cell * Board.VISIBLE + 2 * GameLayout.FRAME_DP * density
            assertTrue("width $wDp×$hDp@$density", GameLayout.rowWidthPx(cell, density) <= wPx + 0.01f)
            assertTrue("height $wDp×$hDp@$density", boardH <= hPx + 0.01f)
            // Not wastefully small: one more pixel would no longer fit.
            val bigger = cell + 1
            val fitsBigger = GameLayout.rowWidthPx(bigger, density) <= wPx &&
                bigger * Board.VISIBLE + 2 * GameLayout.FRAME_DP * density <= hPx
            assertFalse("$wDp×$hDp@$density", fitsBigger)
        }
    }

    @Test
    fun `a typical tall phone gets cells of at least 33 dp`() {
        // Galaxy A26 class: 411 dp wide minus 6 dp margins each side, ~690 dp for the play area, density 2.625.
        val density = 2.625f
        val cell = GameLayout.cellPx((411f - 12f) * density, 690f * density, density)
        assertTrue("cell ${cell / density} dp", cell / density >= 33f)
    }

    @Test
    fun `the board centre follows the side column`() {
        val density = 2f
        val cell = 60
        val area = 1000f
        val right = GameLayout.boardCenterX(area, cell, density, leftHanded = false)
        val left = GameLayout.boardCenterX(area, cell, density, leftHanded = true)
        val rowLeft = (area - GameLayout.rowWidthPx(cell, density)) / 2f
        assertEquals(rowLeft + GameLayout.FRAME_DP * density + cell * 5, right, 0.01f)
        // Left-handed, the column sits before the board: the centre moves right by column plus gap.
        assertEquals(right + (GameLayout.SIDE_DP + GameLayout.GAP_DP) * density, left, 0.01f)
    }

    @Test
    fun `short screens show at most three next pieces`() {
        assertEquals(5, GameLayout.previewShown(5, 800f))
        assertEquals(3, GameLayout.previewShown(5, 600f))
        assertEquals(2, GameLayout.previewShown(2, 600f))
    }

    @Test
    fun `every mini piece is centred inside its four by two box`() {
        for (type in PieceType.entries) {
            val cells = GameLayout.miniCells(type)
            var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE
            var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
            for (i in cells.indices step 2) {
                minX = minOf(minX, cells[i]); maxX = maxOf(maxX, cells[i] + 1)
                minY = minOf(minY, cells[i + 1]); maxY = maxOf(maxY, cells[i + 1] + 1)
            }
            assertTrue("$type inside", minX >= 0f && maxX <= GameLayout.MINI_COLS && minY >= 0f && maxY <= GameLayout.MINI_ROWS)
            assertEquals("$type centred horizontally", minX, GameLayout.MINI_COLS - maxX, 1e-4f)
            assertEquals("$type centred vertically", minY, GameLayout.MINI_ROWS - maxY, 1e-4f)
        }
    }

    @Test
    fun `the T piece points up in the preview`() {
        val c = GameLayout.miniCells(PieceType.T).toList().chunked(2)
        // One cell on the top row (the bump) and three on the bottom row.
        assertEquals(1, c.count { it[1] == 0f })
        assertEquals(3, c.count { it[1] == 1f })
    }

    @Test
    fun `danger starts when a cell reaches row 17`() {
        assertFalse(GameLayout.inDanger(Board.empty()))
        assertFalse(GameLayout.inDanger(Board.empty().withCell(0, 15, Cell.GARBAGE)))
        assertTrue(GameLayout.inDanger(Board.empty().withCell(0, 16, Cell.GARBAGE)))
    }
}
