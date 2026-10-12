package pt.tiago.blocknight.ui.game

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Random
import pt.tiago.blocknight.App
import pt.tiago.blocknight.R
import pt.tiago.blocknight.ui.components.countText
import pt.tiago.blocknight.core.model.Board
import pt.tiago.blocknight.core.model.GameSnapshot
import pt.tiago.blocknight.core.model.GameState
import pt.tiago.blocknight.core.model.Mode
import pt.tiago.blocknight.core.model.Objective
import pt.tiago.blocknight.core.model.Outcome
import pt.tiago.blocknight.core.model.Rules
import pt.tiago.blocknight.core.progress.FreeResult
import pt.tiago.blocknight.core.progress.ModeKeys
import pt.tiago.blocknight.core.progress.Settings
import pt.tiago.blocknight.data.FreeSession
import pt.tiago.blocknight.game.Feedback
import pt.tiago.blocknight.ui.theme.BlockNightColors
import java.util.UUID

/** Regras dos modos livres (PLANO §6.3). Sem objetivo de campanha; Sprint é 40 linhas, Ultra é tempo. */
object FreeRules {
    fun of(mode: String, startLevel: Int, seed: Long): Rules {
        val base = Rules(
            mode = Mode.ZEN,
            speedLevel = startLevel,
            previewCount = 3,
            allowHold = true,
            seed = seed,
            garbageSeed = seed xor 0x5DEECE66DL,
            opening = emptyList(),
            initialBoard = Board.empty(),
            objective = null,
            maxPieces = null,
            risingGarbage = null,
        )
        return when (mode) {
            "marathon" -> base.copy(mode = Mode.MARATHON)
            ModeKeys.SPRINT -> base.copy(mode = Mode.SPRINT, speedLevel = 1, objective = Objective.Lines(40))
            ModeKeys.ULTRA -> base.copy(mode = Mode.ULTRA, speedLevel = 1)
            else -> base
        }
    }

    fun newSeed(): Long = Random().nextLong()

    /** Formata ticks como m:ss.cc (60 Hz). */
    fun clock(ticks: Long): String {
        val centis = ticks * 100 / 60
        val minutes = centis / 6000
        val seconds = (centis / 100) % 60
        val rest = centis % 100
        return "%d:%02d.%02d".format(minutes, seconds, rest)
    }
}

/**
 * Free mode: Marathon, Sprint, Ultra or Zen. The result is saved once per session. [resume] restores the
 * saved run of this mode; otherwise a fresh run starts and replaces it on the first lock.
 */
@Composable
fun FreeGame(
    app: App,
    feedback: Feedback,
    mode: String,
    startLevel: Int,
    attempt: Int,
    resume: Boolean,
    settings: Settings,
    onFinished: (FreeResult) -> Boolean,
    onExit: () -> Unit,
    onRetry: () -> Unit,
) {
    val store = app.container.free
    val saved = remember(mode, startLevel, attempt) {
        if (resume) store.read()?.takeIf { it.mode == mode && it.startLevel == startLevel } else null
    }
    val seed = remember(mode, startLevel, attempt) { saved?.seed ?: FreeRules.newSeed() }
    val sessionId = remember(mode, startLevel, attempt) { saved?.sessionId ?: UUID.randomUUID().toString() }
    val rules = remember(mode, startLevel, attempt) { FreeRules.of(mode, startLevel, seed) }
    val restored = remember(mode, startLevel, attempt) { saved?.let { runCatching { it.snapshot.restore(rules) }.getOrNull() } }

    fun toResult(state: GameState): FreeResult = FreeResult(
        sessionId = sessionId,
        mode = mode,
        startLevel = startLevel,
        score = state.score,
        lines = state.lines,
        level = state.level,
        ticks = state.ticks,
        pieces = state.pieces,
        quads = state.quads,
        tspins = state.tspins,
        perfectClears = state.perfectClears,
        maxCombo = state.maxStreak,
        completedSprint = mode == ModeKeys.SPRINT && state.outcome == Outcome.Won,
    )

    val title = when (mode) {
        "marathon" -> stringResource(R.string.free_marathon)
        ModeKeys.SPRINT -> stringResource(R.string.free_sprint)
        ModeKeys.ULTRA -> stringResource(R.string.free_ultra)
        else -> stringResource(R.string.free_zen)
    }
    val config = HostConfig(
        rules = rules,
        sessionKey = listOf(mode, startLevel, attempt, resume),
        restored = restored,
        title = title,
        subtitle = { state ->
            when (mode) {
                "marathon" -> stringResource(R.string.free_hud_level, state.level)
                ModeKeys.ZEN -> stringResource(R.string.free_hud_speed, state.level)
                else -> ""
            }
        },
        counter = { state ->
            when (mode) {
                "marathon" -> Counter(state.score.toString(), stringResource(R.string.hud_points_caption))
                ModeKeys.SPRINT -> Counter(FreeRules.clock(state.ticks), stringResource(R.string.hud_time_caption))
                ModeKeys.ULTRA -> Counter(FreeRules.clock(maxOf(0L, ULTRA_TICKS - state.ticks)), stringResource(R.string.hud_time_left_caption))
                else -> Counter(state.lines.toString(), stringResource(R.string.hud_lines_caption))
            }
        },
        showHint = false,
        save = { state ->
            runCatching { store.write(FreeSession(sessionId, mode, startLevel, seed, GameSnapshot.of(state))) }
        },
        clear = { store.clear() },
        settings = settings,
        onFinished = { state -> onFinished(toResult(state)) },
    )

    GameHost(
        config = config,
        feedback = feedback,
        hud = { state, _ -> FreeStats(mode, state) },
        result = { state, saveFailed, retrySave ->
            val won = state.outcome == Outcome.Won
            ResultFrame(
                title = stringResource(if (won && mode == ModeKeys.SPRINT) R.string.free_sprint_done else R.string.free_over),
                saveFailed = saveFailed,
                primary = stringResource(R.string.result_retry),
                onPrimary = onRetry,
                exitLabel = stringResource(R.string.home_free),
                onExit = onExit,
                onRetrySave = retrySave,
            ) {
                if (mode == ModeKeys.SPRINT && won) BodyLine("${stringResource(R.string.result_time)}: ${FreeRules.clock(state.ticks)}")
                BodyLine("${stringResource(R.string.result_score)}: ${state.score}")
                BodyLine("${stringResource(R.string.result_lines)}: ${state.lines}")
                BodyLine("${stringResource(R.string.result_pieces)}: ${state.pieces}")
            }
        },
        onExit = { state ->
            // Zen has no end: its statistics count on leaving (D055). Other modes stay saved to resume.
            if (mode == ModeKeys.ZEN && state.pieces > 0 && state.outcome == null) {
                onFinished(toResult(state))
            }
            onExit()
        },
    )
}

/** Ultra lasts two minutes of 60 Hz ticks. */
private const val ULTRA_TICKS = 7200L

@Composable
private fun FreeStats(mode: String, state: GameState) {
    val line = when (mode) {
        "marathon" -> stringResource(R.string.free_hud_marathon, state.lines, state.level)
        ModeKeys.SPRINT -> countText(R.string.free_hud_sprint_one, R.string.free_hud_sprint_other, maxOf(0, 40 - state.lines), maxOf(0, 40 - state.lines))
        ModeKeys.ULTRA -> stringResource(R.string.free_hud_ultra, state.score.toString(), state.lines)
        else -> stringResource(R.string.free_hud_zen, state.pieces)
    }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = line, style = MaterialTheme.typography.bodySmall.copy(color = BlockNightColors.TextStrong, fontSize = 13.sp))
    }
}
