package pt.tiago.blocknight.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pt.tiago.blocknight.R
import pt.tiago.blocknight.ui.theme.BlockNightColors

/** Cartão com a superfície e o contorno do tema (DESIGN §2, raio 20 dp). */
fun Modifier.card(radius: Dp = 20.dp): Modifier =
    this.background(BlockNightColors.Surface, RoundedCornerShape(radius)).border(1.dp, BlockNightColors.Line, RoundedCornerShape(radius))

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(
                if (enabled) BlockNightColors.Accent else BlockNightColors.Line,
                RoundedCornerShape(16.dp),
            )
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge.copy(color = BlockNightColors.OnAccent))
    }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(48.dp)
            .card(radius = 16.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge.copy(color = BlockNightColors.TextStrong))
    }
}

/** Estrelas com texto para leitores de ecrã (a cor nunca é a única informação). */
@Composable
fun StarRow(stars: Int, modifier: Modifier = Modifier, size: TextUnit = 14.sp) {
    val description = countText(R.string.cd_stars_one, R.string.cd_stars_other, stars, stars)
    Row(modifier = modifier.semantics { contentDescription = description }) {
        for (i in 1..3) {
            Text(
                text = if (i <= stars) "★" else "☆",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = if (i <= stars) BlockNightColors.Accent else BlockNightColors.Line,
                    fontSize = size,
                    lineHeight = size * 1.2f,
                ),
            )
        }
    }
}

/**
 * Text that depends on a count: [one] for exactly 1, [other] for everything else, including 0. Android's
 * `values-pt` plural rules treat 0 as "one", which would read "0 estrela"; this keeps pt-PT correct.
 */
@Composable
fun countText(one: Int, other: Int, count: Int, vararg args: Any): String =
    stringResource(if (count == 1) one else other, *args)

/** Visible name of a difficulty key (`easy`, `medium`, `hard`). */
@Composable
fun difficultyName(difficulty: String): String = when (difficulty) {
    "easy" -> stringResource(R.string.difficulty_easy)
    "hard" -> stringResource(R.string.difficulty_hard)
    else -> stringResource(R.string.difficulty_medium)
}

/** Chip de dificuldade com nome escrito (DESIGN §2). */
@Composable
fun DifficultyChip(name: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(999.dp))
            .padding(horizontal = 8.dp, vertical = 1.dp),
    ) {
        Text(text = name, style = MaterialTheme.typography.bodySmall.copy(color = color, fontSize = 11.sp), textAlign = TextAlign.Center)
    }
}

/** Barra de progresso de 5 dp (DESIGN §5). */
@Composable
fun ProgressBar(fraction: Float, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().height(5.dp).background(BlockNightColors.SurfaceRaised, RoundedCornerShape(5.dp))) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(5.dp)
                .background(BlockNightColors.Accent, RoundedCornerShape(5.dp)),
        )
    }
}
