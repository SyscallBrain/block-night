package pt.tiago.blocknight.ui.free

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pt.tiago.blocknight.R
import pt.tiago.blocknight.core.progress.ModeKeys
import pt.tiago.blocknight.core.progress.ModeRecord
import pt.tiago.blocknight.core.progress.Profile
import pt.tiago.blocknight.data.FreeSession
import pt.tiago.blocknight.ui.components.PrimaryButton
import pt.tiago.blocknight.ui.components.SecondaryButton
import pt.tiago.blocknight.ui.components.card
import pt.tiago.blocknight.ui.game.FreeRules
import pt.tiago.blocknight.ui.theme.BlockNightColors

/** Escolha do modo livre (PLANO §3.1, §6.3). Só mostra modos com ação real; retoma a sessão guardada. */
@Composable
fun FreeScreen(
    profile: Profile,
    saved: FreeSession?,
    onPlay: (mode: String, startLevel: Int, resume: Boolean) -> Unit,
    onBack: () -> Unit,
) {
    var marathonLevel by rememberSaveable { mutableIntStateOf(1) }
    var zenSpeed by rememberSaveable { mutableIntStateOf(1) }
    val marathonBest = profile.modes[ModeKeys.marathon(marathonLevel)] ?: ModeRecord()
    val sprint = profile.modes[ModeKeys.SPRINT] ?: ModeRecord()
    val ultra = profile.modes[ModeKeys.ULTRA] ?: ModeRecord()

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
        saved?.let {
            PrimaryButton(text = stringResource(R.string.free_resume) + " · " + modeName(it.mode), onClick = { onPlay(it.mode, it.startLevel, true) })
        }
        ModeCard(
            title = stringResource(R.string.free_marathon),
            caption = stringResource(R.string.free_marathon_caption, marathonLevel),
            best = if (marathonBest.plays > 0) stringResource(R.string.free_best, "${marathonBest.bestScore}") else stringResource(R.string.free_no_best),
            stepper = StepperValue(stringResource(R.string.free_level), marathonLevel, 1, 10) { marathonLevel = it },
            onPlay = { onPlay("marathon", marathonLevel, false) },
        )
        ModeCard(
            title = stringResource(R.string.free_sprint),
            caption = stringResource(R.string.free_sprint_caption),
            best = sprint.bestTicks?.let { stringResource(R.string.free_best, FreeRules.clock(it)) } ?: stringResource(R.string.free_no_best),
            onPlay = { onPlay(ModeKeys.SPRINT, 1, false) },
        )
        ModeCard(
            title = stringResource(R.string.free_ultra),
            caption = stringResource(R.string.free_ultra_caption),
            best = if (ultra.plays > 0) stringResource(R.string.free_best, "${ultra.bestScore}") else stringResource(R.string.free_no_best),
            onPlay = { onPlay(ModeKeys.ULTRA, 1, false) },
        )
        ModeCard(
            title = stringResource(R.string.free_zen),
            caption = stringResource(R.string.free_zen_caption) + "\n" + stringResource(R.string.free_zen_note),
            best = "",
            stepper = StepperValue(stringResource(R.string.free_speed), zenSpeed, 1, 10) { zenSpeed = it },
            onPlay = { onPlay(ModeKeys.ZEN, zenSpeed, false) },
        )
    }
}

private data class StepperValue(val label: String, val value: Int, val min: Int, val max: Int, val onChange: (Int) -> Unit)

@Composable
private fun ModeCard(
    title: String,
    caption: String,
    best: String,
    stepper: StepperValue? = null,
    onPlay: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = title, style = MaterialTheme.typography.headlineMedium.copy(color = BlockNightColors.TextStrong, fontSize = 22.sp))
        Text(text = caption, style = MaterialTheme.typography.bodySmall.copy(color = BlockNightColors.TextMuted))
        if (best.isNotEmpty()) {
            Text(text = best, style = MaterialTheme.typography.bodySmall.copy(color = BlockNightColors.Accent))
        }
        stepper?.let { s ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = s.label, style = MaterialTheme.typography.bodySmall.copy(color = BlockNightColors.TextStrong))
                Box(
                    modifier = Modifier.size(44.dp).card(radius = 12.dp).clickable(enabled = s.value > s.min) { s.onChange(s.value - 1) },
                    contentAlignment = Alignment.Center,
                ) { Text(text = "−", style = MaterialTheme.typography.titleLarge.copy(color = BlockNightColors.TextStrong)) }
                Text(text = s.value.toString(), style = MaterialTheme.typography.titleLarge.copy(color = BlockNightColors.TextStrong))
                Box(
                    modifier = Modifier.size(44.dp).card(radius = 12.dp).clickable(enabled = s.value < s.max) { s.onChange(s.value + 1) },
                    contentAlignment = Alignment.Center,
                ) { Text(text = "+", style = MaterialTheme.typography.titleLarge.copy(color = BlockNightColors.TextStrong)) }
            }
        }
        PrimaryButton(text = stringResource(R.string.setup_play), onClick = onPlay)
    }
}

@Composable
private fun modeName(mode: String): String = when {
    mode == "marathon" -> stringResource(R.string.free_marathon)
    mode == ModeKeys.SPRINT -> stringResource(R.string.free_sprint)
    mode == ModeKeys.ULTRA -> stringResource(R.string.free_ultra)
    else -> stringResource(R.string.free_zen)
}
