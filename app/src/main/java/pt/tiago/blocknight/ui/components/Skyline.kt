package pt.tiago.blocknight.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import pt.tiago.blocknight.ui.theme.BlockNightColors

/**
 * Horizonte em silhueta gerado por código (DESIGN §1, §4). A semente escolhe as alturas e as janelas
 * acesas, por isso cada bairro tem o seu perfil sem depender de imagens. Os edifícios assentam no fundo.
 */
@Composable
fun Skyline(seed: Int, height: Dp, litFraction: Float, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxWidth().height(height)) {
        drawSkyline(seed = seed, maxHeight = size.height, litFraction = litFraction)
    }
}

internal fun DrawScope.drawSkyline(seed: Int, maxHeight: Float, litFraction: Float) {
    val random = SeededRandom(seed)
    val windowColors = listOf(BlockNightColors.Accent, BlockNightColors.Orange, BlockNightColors.Cyan, BlockNightColors.Blue)
    val unit = 1.dp.toPx()
    var x = -6f * unit
    while (x < size.width) {
        val width = (22 + random.nextInt(30)) * unit
        val buildingHeight = (30 + random.nextInt(160)) / 160f * maxHeight
        val top = size.height - buildingHeight
        drawRect(
            color = if (random.nextFloat() > 0.5f) BlockNightColors.BuildingA else BlockNightColors.BuildingB,
            topLeft = Offset(x, top),
            size = Size(width, buildingHeight),
        )
        var wy = 8f * unit
        while (wy < buildingHeight - 6f * unit) {
            var wx = 5f * unit
            while (wx < width - 5f * unit) {
                if (random.nextFloat() < 0.44f * litFraction) {
                    val color: Color = windowColors[random.nextInt(windowColors.size)]
                    drawRect(
                        color = color.copy(alpha = 0.35f + random.nextFloat() * 0.5f),
                        topLeft = Offset(x + wx, top + wy),
                        size = Size(3f * unit, 4f * unit),
                    )
                }
                wx += 7f * unit
            }
            wy += 9f * unit
        }
        x += width + 2f * unit
    }
}

/** Gerador linear determinístico, só para desenho (nunca entra na lógica de jogo). */
internal class SeededRandom(seed: Int) {
    private var state: Long = if (seed <= 0) 1L else seed.toLong()

    fun nextFloat(): Float {
        state = (state * 16807L) % 2147483647L
        return state.toFloat() / 2147483647f
    }

    fun nextInt(bound: Int): Int = (nextFloat() * bound).toInt().coerceIn(0, bound - 1)
}
