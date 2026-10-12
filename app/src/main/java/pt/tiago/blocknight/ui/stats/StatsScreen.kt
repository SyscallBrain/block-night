package pt.tiago.blocknight.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pt.tiago.blocknight.R
import pt.tiago.blocknight.core.content.DIFFICULTIES
import pt.tiago.blocknight.core.progress.Achievements
import pt.tiago.blocknight.core.progress.ModeKeys
import pt.tiago.blocknight.core.progress.Profile
import pt.tiago.blocknight.ui.components.SecondaryButton
import pt.tiago.blocknight.ui.components.card
import pt.tiago.blocknight.ui.game.FreeRules
import pt.tiago.blocknight.ui.theme.BlockNightColors

/** Estatísticas (PLANO §3.5): totais, andares por dificuldade e recordes livres. */
@Composable
fun StatsScreen(profile: Profile, totalFloors: Int, onBack: () -> Unit) {
    val t = profile.totals
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BlockNightColors.Background)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .widthIn(max = 520.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SecondaryButton(text = stringResource(R.string.cd_back), onClick = onBack)
        Text(text = stringResource(R.string.stats_title), style = MaterialTheme.typography.headlineLarge.copy(color = BlockNightColors.TextStrong, fontSize = 28.sp))
        Column(modifier = Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (difficulty in DIFFICULTIES) {
                val done = profile.progress[difficulty]?.values?.count { it.completed } ?: 0
                val stars = profile.progress[difficulty]?.values?.sumOf { it.stars } ?: 0
                val name = when (difficulty) {
                    "easy" -> stringResource(R.string.difficulty_easy)
                    "hard" -> stringResource(R.string.difficulty_hard)
                    else -> stringResource(R.string.difficulty_medium)
                }
                Text(
                    text = "$name · " + stringResource(R.string.stats_floors, "$done / $totalFloors · ★ $stars / ${totalFloors * 3}"),
                    style = MaterialTheme.typography.bodyLarge.copy(color = BlockNightColors.Text),
                )
            }
        }
        Column(modifier = Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Line(stringResource(R.string.stats_pieces), t.pieces.toString())
            Line(stringResource(R.string.stats_lines), t.lines.toString())
            Line(stringResource(R.string.stats_quads), t.quads.toString())
            Line(stringResource(R.string.stats_tspins), t.tspins.toString())
            Line(stringResource(R.string.stats_perfect), t.perfectClears.toString())
            Line(stringResource(R.string.stats_combo), t.maxCombo.toString())
        }
        Column(modifier = Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(text = stringResource(R.string.achievements_title), style = MaterialTheme.typography.titleLarge.copy(color = BlockNightColors.TextStrong))
            val earned = Achievements.of(profile, 50) { pt.tiago.blocknight.core.content.Districts.floorId(it) }
            Achievements.ALL.forEach { id ->
                val label = when (id) {
                    Achievements.FIRST_WIN -> stringResource(R.string.ach_first_win)
                    Achievements.FIRST_QUAD -> stringResource(R.string.ach_first_quad)
                    Achievements.FIRST_TSPIN -> stringResource(R.string.ach_first_tspin)
                    Achievements.COMBO_FIVE -> stringResource(R.string.ach_combo_5)
                    Achievements.FIRST_PERFECT -> stringResource(R.string.ach_first_perfect)
                    Achievements.FIRST_DISTRICT -> stringResource(R.string.ach_first_district)
                    else -> stringResource(R.string.ach_floors_100)
                }
                Text(
                    text = (if (id in earned) "✓ " else "○ ") + label,
                    style = MaterialTheme.typography.bodySmall.copy(color = if (id in earned) BlockNightColors.Accent else BlockNightColors.TextMuted),
                )
            }
        }
        Column(modifier = Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(text = stringResource(R.string.stats_records), style = MaterialTheme.typography.titleLarge.copy(color = BlockNightColors.TextStrong))
            val marathon = profile.modes.filterKeys { it.startsWith("marathon-") }.toSortedMap()
            marathon.forEach { (key, record) ->
                val level = key.substringAfter("-").toInt()
                Text(
                    text = stringResource(R.string.stats_marathon_record, level, record.bestScore.toString(), record.bestLines, record.bestLevel),
                    style = MaterialTheme.typography.bodySmall.copy(color = BlockNightColors.Text),
                )
            }
            profile.modes[ModeKeys.SPRINT]?.bestTicks?.let {
                Text(text = stringResource(R.string.stats_sprint_record, FreeRules.clock(it)), style = MaterialTheme.typography.bodySmall.copy(color = BlockNightColors.Text))
            }
            profile.modes[ModeKeys.ULTRA]?.takeIf { it.plays > 0 }?.let {
                Text(text = stringResource(R.string.stats_ultra_record, it.bestScore.toString()), style = MaterialTheme.typography.bodySmall.copy(color = BlockNightColors.Text))
            }
            if (marathon.isEmpty() && profile.modes[ModeKeys.SPRINT]?.bestTicks == null && profile.modes[ModeKeys.ULTRA]?.plays == null) {
                Text(text = stringResource(R.string.stats_no_records), style = MaterialTheme.typography.bodySmall.copy(color = BlockNightColors.TextMuted))
            }
        }
    }
}

@Composable
private fun Line(label: String, value: String) {
    Text(text = "$label: $value", style = MaterialTheme.typography.bodyLarge.copy(color = BlockNightColors.Text))
}
