package pt.tiago.blocknight.ui.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import pt.tiago.blocknight.core.model.ActivePiece
import pt.tiago.blocknight.core.model.Board
import pt.tiago.blocknight.core.model.Cell
import pt.tiago.blocknight.core.model.PieceType
import pt.tiago.blocknight.core.model.Srs
import pt.tiago.blocknight.ui.theme.BlockNightColors

/** Cor de cada peça (DESIGN §3). */
fun pieceColor(type: PieceType): Color = when (type) {
    PieceType.I -> BlockNightColors.Cyan
    PieceType.O -> BlockNightColors.Accent
    PieceType.T -> BlockNightColors.Purple
    PieceType.S -> BlockNightColors.Green
    PieceType.Z -> BlockNightColors.Pink
    PieceType.J -> BlockNightColors.Blue
    PieceType.L -> BlockNightColors.Orange
}

/**
 * Tabuleiro desenhado em Canvas (DESIGN §3–4). Células quadradas de tamanho inteiro em píxeis.
 * Lê o estado só na fase de desenho, com a peça fantasma calculada pelo motor.
 */
@Composable
fun BoardCanvas(
    board: Board,
    active: ActivePiece?,
    cellSize: Dp,
    redraw: Long = 0L,
    showSymbols: Boolean = false,
    modifier: Modifier = Modifier,
) {
    // `redraw` changes every frame: drawing re-reads the logical state, which is not a Compose State.
    val width = cellSize * Board.WIDTH
    val height = cellSize * Board.VISIBLE
    Canvas(modifier = modifier.size(width, height)) {
        // The caller sizes cells in whole pixels; rounding removes float noise from the dp round trip.
        val cell = cellSize.toPx().roundToInt().toFloat()
        drawWell(cell)
        drawBoard(board, cell)
        if (active != null) {
            val ghost = ghostOf(board, active)
            drawPiece(active, ghost, cell, ghost = true)
            drawPiece(active, active, cell, ghost = false)
        }
    }
}

private fun ghostOf(board: Board, piece: ActivePiece): ActivePiece {
    var p = piece
    while (pt.tiago.blocknight.core.rules.Engine.canPlace(board, p.copy(y = p.y - 1))) p = p.copy(y = p.y - 1)
    return p
}

private fun DrawScope.drawWell(cell: Float) {
    val w = Board.WIDTH * cell
    val h = Board.VISIBLE * cell
    drawRect(color = BlockNightColors.BackgroundDeep.copy(alpha = 0.75f), size = Size(w, h))
    val grid = BlockNightColors.Line.copy(alpha = 0.12f)
    for (x in 1 until Board.WIDTH) drawLine(grid, Offset(x * cell, 0f), Offset(x * cell, h), strokeWidth = 1f)
    for (y in 1 until Board.VISIBLE) drawLine(grid, Offset(0f, y * cell), Offset(w, y * cell), strokeWidth = 1f)
    drawRect(color = BlockNightColors.Line, size = Size(w, h), style = Stroke(width = 1.dp.toPx()))
}

private fun DrawScope.drawBoard(board: Board, cell: Float) {
    for (y in 0 until Board.VISIBLE) for (x in 0 until Board.WIDTH) {
        val value = board.get(x, y)
        if (value == Cell.EMPTY) continue
        val left = x * cell
        val top = (Board.VISIBLE - 1 - y) * cell
        when {
            Cell.isMino(value) -> drawWindowBlock(pieceColor(PieceType.entries[value - 1]), left, top, cell, 1f)
            value == Cell.GARBAGE -> drawGarbage(left, top, cell)
            value == Cell.CONCRETE -> drawConcrete(left, top, cell)
            value == Cell.TARGET -> {
                drawGarbage(left, top, cell)
                drawCircle(color = BlockNightColors.Accent, radius = cell * 0.2f, center = Offset(left + cell / 2, top + cell / 2))
            }
        }
    }
}

private fun DrawScope.drawPiece(piece: ActivePiece, at: ActivePiece, cell: Float, ghost: Boolean) {
    val cells = Srs.cells(piece.type, at.rot)
    val color = pieceColor(piece.type)
    var i = 0
    while (i < cells.size) {
        val x = at.x + cells[i]
        val y = at.y + cells[i + 1]
        if (y in 0 until Board.VISIBLE && x in 0 until Board.WIDTH) {
            val left = x * cell
            val top = (Board.VISIBLE - 1 - y) * cell
            if (ghost) {
                drawRoundRect(
                    color = color.copy(alpha = 0.08f),
                    topLeft = Offset(left + 1f, top + 1f),
                    size = Size(cell - 2f, cell - 2f),
                    cornerRadius = CornerRadius(cell * 0.12f),
                )
                drawRoundRect(
                    color = color.copy(alpha = 0.75f),
                    topLeft = Offset(left + 1f, top + 1f),
                    size = Size(cell - 2f, cell - 2f),
                    cornerRadius = CornerRadius(cell * 0.12f),
                    style = Stroke(width = 1.5f.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 3f))),
                )
            } else {
                drawWindowBlock(color, left, top, cell, 1f)
            }
        }
        i += 2
    }
}

/** Bloco “janela acesa”: gradiente vertical, caixilho escuro e travessa central (DESIGN §3). */
private fun DrawScope.drawWindowBlock(color: Color, left: Float, top: Float, cell: Float, alpha: Float) {
    val bg = BlockNightColors.BackgroundDeep
    val brush = Brush.verticalGradient(
        colors = listOf(
            lerp(color, Color.White, 0.08f),
            color,
            lerp(color, bg, 0.35f),
        ),
        startY = top,
        endY = top + cell,
    )
    val radius = CornerRadius(cell * 0.12f)
    drawRoundRect(brush = brush, topLeft = Offset(left, top), size = Size(cell, cell), cornerRadius = radius, alpha = alpha)
    drawRoundRect(
        color = lerp(color, bg, 0.6f),
        topLeft = Offset(left + 1f, top + 1f),
        size = Size(cell - 2f, cell - 2f),
        cornerRadius = radius,
        style = Stroke(width = 2.dp.toPx()),
    )
    val mullion = cell * 0.08f
    drawRect(
        color = bg.copy(alpha = 0.55f),
        topLeft = Offset(left + cell / 2 - mullion / 2, top + cell * 0.12f),
        size = Size(mullion, cell * 0.76f),
    )
}

private fun DrawScope.drawGarbage(left: Float, top: Float, cell: Float) {
    val brush = Brush.verticalGradient(listOf(Color(0xFF2C3150), Color(0xFF23273D)), startY = top, endY = top + cell)
    drawRoundRect(brush = brush, topLeft = Offset(left, top), size = Size(cell, cell), cornerRadius = CornerRadius(cell * 0.12f))
    drawRoundRect(
        color = Color(0xFF1B1E2E),
        topLeft = Offset(left + 1f, top + 1f),
        size = Size(cell - 2f, cell - 2f),
        cornerRadius = CornerRadius(cell * 0.12f),
        style = Stroke(width = 2.dp.toPx()),
    )
}

/** Betão: tracejado diagonal a 135°, para ser distinguível sem cor (DESIGN §3). */
private fun DrawScope.drawConcrete(left: Float, top: Float, cell: Float) {
    drawRect(color = Color(0xFF2E3350), topLeft = Offset(left, top), size = Size(cell, cell))
    val step = cell / 3f
    var offset = -cell
    while (offset < cell) {
        drawLine(
            color = Color(0xFF3A3F5C),
            start = Offset(left + offset, top + cell),
            end = Offset(left + offset + cell, top),
            strokeWidth = 2.dp.toPx(),
        )
        offset += step
    }
}

