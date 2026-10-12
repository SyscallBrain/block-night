package pt.tiago.blocknight.game

import kotlin.math.floor
import kotlin.math.min
import pt.tiago.blocknight.core.model.Board
import pt.tiago.blocknight.core.model.Cell
import pt.tiago.blocknight.core.model.PieceType
import pt.tiago.blocknight.core.model.Srs

/**
 * Geometry of the match screen (DESIGN §4), kept free of Compose so it can be tested on the JVM.
 * Every size that reaches the canvas is a whole number of pixels, so grid lines never blur.
 */
object GameLayout {
    /** One side column (Hold, Next and stats together), on the right or, left-handed, on the left. */
    const val SIDE_DP = 52f
    const val GAP_DP = 6f
    const val FRAME_DP = 4f
    const val COMPACT_HEIGHT_DP = 640f
    const val COMPACT_MAX_PREVIEW = 3

    /** Mini-piece cell sizes in the side column: Hold and the first Next, then the following ones. */
    const val MINI_CELL_DP = 11f
    const val MINI_NEXT_CELL_DP = 9.5f

    /**
     * Board cell size in whole pixels for the play area (side column, gap and frame included in the width).
     * Never below 1 px; the result always fits the area.
     */
    fun cellPx(areaWidthPx: Float, areaHeightPx: Float, density: Float): Int {
        val byWidth = (areaWidthPx - (SIDE_DP + GAP_DP + 2 * FRAME_DP) * density) / Board.WIDTH
        val byHeight = (areaHeightPx - 2 * FRAME_DP * density) / Board.VISIBLE
        return floor(min(byWidth, byHeight)).toInt().coerceAtLeast(1)
    }

    /** Width of the board row (framed board, gap and side column) in pixels. */
    fun rowWidthPx(cellPx: Int, density: Float): Float =
        cellPx * Board.WIDTH + (2 * FRAME_DP + GAP_DP + SIDE_DP) * density

    /**
     * Horizontal centre of the board in play-area coordinates, with the row centred in the area. The whole
     * play area takes gestures, so a tap is read against the board's centre, not the area's.
     */
    fun boardCenterX(areaWidthPx: Float, cellPx: Int, density: Float, leftHanded: Boolean): Float {
        val rowLeft = (areaWidthPx - rowWidthPx(cellPx, density)) / 2f
        val boardLeft = rowLeft + (if (leftHanded) (SIDE_DP + GAP_DP) * density else 0f) + FRAME_DP * density
        return boardLeft + cellPx * Board.WIDTH / 2f
    }

    /** Next pieces actually shown: at most 3 on screens shorter than 640 dp (DESIGN §4). */
    fun previewShown(previewCount: Int, screenHeightDp: Float): Int =
        if (screenHeightDp < COMPACT_HEIGHT_DP) min(previewCount, COMPACT_MAX_PREVIEW) else previewCount

    fun isCompact(screenHeightDp: Float): Boolean = screenHeightDp < COMPACT_HEIGHT_DP

    /** Mini-piece box: 4 cells wide and 2 tall, enough for any piece in its spawn rotation. */
    const val MINI_COLS = 4
    const val MINI_ROWS = 2

    /**
     * Top-left corners (in mini-cell units, y down) of the four cells of [type], centred in the
     * [MINI_COLS]×[MINI_ROWS] box. Returned as x0, y0, x1, y1…
     */
    fun miniCells(type: PieceType): FloatArray {
        val cells = Srs.cells(type, 0)
        var minX = Int.MAX_VALUE
        var maxX = Int.MIN_VALUE
        var minY = Int.MAX_VALUE
        var maxY = Int.MIN_VALUE
        for (i in cells.indices step 2) {
            minX = minOf(minX, cells[i]); maxX = maxOf(maxX, cells[i])
            minY = minOf(minY, cells[i + 1]); maxY = maxOf(maxY, cells[i + 1])
        }
        val w = maxX - minX + 1
        val h = maxY - minY + 1
        val offsetX = (MINI_COLS - w) / 2f
        val offsetY = (MINI_ROWS - h) / 2f
        val out = FloatArray(cells.size)
        for (i in cells.indices step 2) {
            out[i] = offsetX + (cells[i] - minX)
            // The engine's y grows upwards; the canvas grows downwards.
            out[i + 1] = offsetY + (maxY - cells[i + 1])
        }
        return out
    }

    /** Danger warning (DESIGN §5): some locked cell on row 17 (1-based) or above. */
    const val DANGER_ROW = 16

    fun inDanger(board: Board): Boolean {
        for (y in DANGER_ROW until Board.VISIBLE) for (x in 0 until Board.WIDTH) {
            if (board.get(x, y) != Cell.EMPTY) return true
        }
        return false
    }
}
