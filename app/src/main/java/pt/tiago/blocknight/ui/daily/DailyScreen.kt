package pt.tiago.blocknight.ui.daily

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background as bg
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import pt.tiago.blocknight.R
import pt.tiago.blocknight.core.content.Districts
import pt.tiago.blocknight.core.progress.Profile
import pt.tiago.blocknight.ui.components.PrimaryButton
import pt.tiago.blocknight.ui.components.SecondaryButton
import pt.tiago.blocknight.ui.components.card
import pt.tiago.blocknight.ui.components.difficultyName
import pt.tiago.blocknight.ui.district.objectiveText
import pt.tiago.blocknight.data.ContentRepository
import pt.tiago.blocknight.ui.theme.BlockNightColors

/** Um andar por dia e dificuldade, escolhido pela data local (PLANO §6.2). Não depende de servidor. */
object DailyPick {
    /** Andar do dia: a data local dividida pelos andares jogáveis (só entram andares com replay). */
    fun ordinalFor(date: LocalDate, playable: Int): Int = (date.toEpochDay() % playable).toInt() + 1
}

@Composable
fun DailyScreen(
    content: ContentRepository,
    profile: Profile,
    today: LocalDate,
    difficulty: String,
    onDifficulty: (String) -> Unit,
    onPlay: (Int, String) -> Unit,
    onBack: () -> Unit,
) {
    val ordinal = DailyPick.ordinalFor(today, content.playableCount)
    val dateKey = today.toString()
    val todays = profile.daily.filter { it.date == dateKey && it.difficulty == difficulty }
    val recent = profile.daily.takeLast(10)
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
        Text(text = stringResource(R.string.daily_title), style = MaterialTheme.typography.headlineLarge.copy(color = BlockNightColors.TextStrong, fontSize = 28.sp))
        Column(modifier = Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.daily_floor, ordinal, Districts.districtOf(ordinal).name),
                style = MaterialTheme.typography.titleLarge.copy(color = BlockNightColors.TextStrong),
            )
            Text(text = objectiveText(content, ordinal, difficulty), style = MaterialTheme.typography.bodyLarge.copy(color = BlockNightColors.Text))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("easy", "medium", "hard").forEach { id ->
                    val on = id == difficulty
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .card(radius = 12.dp)
                            .clickable { onDifficulty(id) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = difficultyName(id),
                            style = MaterialTheme.typography.bodySmall.copy(color = if (on) BlockNightColors.Accent else BlockNightColors.TextMuted),
                        )
                    }
                }
            }
            if (todays.isNotEmpty()) {
                val last = todays.last()
                Text(
                    text = stringResource(if (last.won) R.string.daily_done_won else R.string.daily_done_lost, last.pieces),
                    style = MaterialTheme.typography.bodySmall.copy(color = BlockNightColors.TextMuted),
                )
            }
            PrimaryButton(text = stringResource(R.string.setup_play), onClick = { onPlay(ordinal, difficulty) })
        }
        Column(modifier = Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (recent.isEmpty()) {
                Text(text = stringResource(R.string.daily_no_history), style = MaterialTheme.typography.bodySmall.copy(color = BlockNightColors.TextMuted))
            } else {
                Text(
                    text = stringResource(R.string.daily_history, recent.count { it.won }, recent.size),
                    style = MaterialTheme.typography.bodySmall.copy(color = BlockNightColors.Text),
                )
            }
        }
    }
}
