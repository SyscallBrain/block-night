package pt.tiago.blocknight.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Typography
import pt.tiago.blocknight.R

/** Tokens de docs/DESIGN.md §2. Tema único noturno na v1. */
object BlockNightColors {
    val Background = Color(0xFF1A1B26)
    val BackgroundDeep = Color(0xFF16161E)
    val Surface = Color(0xFF24283B)
    val SurfaceRaised = Color(0xFF292E42)
    val Line = Color(0xFF414868)
    val Text = Color(0xFFC0CAF5)
    val TextStrong = Color(0xFFE2E8FF)
    val TextMuted = Color(0xFF8C94BE)
    val Accent = Color(0xFFE0AF68)
    val OnAccent = Color(0xFF16161E)
    val Blue = Color(0xFF7AA2F7)
    val Purple = Color(0xFFBB9AF7)
    val Green = Color(0xFF9ECE6A)
    val Orange = Color(0xFFFF9E64)
    val Pink = Color(0xFFF7768E)
    val Cyan = Color(0xFF7DCFFF)
    val BuildingA = Color(0xFF1F2235)
    val BuildingB = Color(0xFF232741)
}

private val Display = FontFamily(Font(R.font.bricolage_grotesque))
private val Body = FontFamily(Font(R.font.figtree))

/** Bricolage Grotesque para títulos e números; Figtree para corpo e controlos (DESIGN §2). */
val BlockNightTypography = Typography(
    displayLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 46.sp, lineHeight = 44.sp),
    headlineLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 32.sp),
    headlineMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    bodyLarge = TextStyle(fontFamily = Body, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 22.sp),
    labelLarge = TextStyle(fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontFamily = Body, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
)

private val DarkScheme = darkColorScheme(
    primary = BlockNightColors.Accent,
    onPrimary = BlockNightColors.OnAccent,
    background = BlockNightColors.Background,
    onBackground = BlockNightColors.Text,
    surface = BlockNightColors.Surface,
    onSurface = BlockNightColors.Text,
    surfaceVariant = BlockNightColors.SurfaceRaised,
    outline = BlockNightColors.Line,
)

@Composable
fun BlockNightTheme(content: @Composable () -> Unit) {
    // Tema único escuro: a identidade do jogo não segue o sistema (DESIGN §2).
    isSystemInDarkTheme()
    MaterialTheme(colorScheme = DarkScheme, typography = BlockNightTypography, content = content)
}
