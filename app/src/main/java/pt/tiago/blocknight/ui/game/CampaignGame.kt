package pt.tiago.blocknight.ui.game

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.UUID
import pt.tiago.blocknight.App
import pt.tiago.blocknight.R
import pt.tiago.blocknight.ui.components.countText
import pt.tiago.blocknight.core.content.Districts
import pt.tiago.blocknight.core.model.Cell
import pt.tiago.blocknight.core.model.GameSnapshot
import pt.tiago.blocknight.core.model.GameState
import pt.tiago.blocknight.core.model.LossReason
import pt.tiago.blocknight.core.model.Objective
import pt.tiago.blocknight.core.model.Outcome
import pt.tiago.blocknight.core.model.Rules
import pt.tiago.blocknight.core.progress.DailyResult
import pt.tiago.blocknight.core.progress.FloorResult
import pt.tiago.blocknight.core.progress.ProgressReducer
import pt.tiago.blocknight.core.progress.Settings
import pt.tiago.blocknight.data.CampaignSession
import pt.tiago.blocknight.game.CampaignHud
import pt.tiago.blocknight.game.Feedback
import pt.tiago.blocknight.ui.components.ProgressBar
import pt.tiago.blocknight.ui.components.StarRow
import pt.tiago.blocknight.ui.components.card
import pt.tiago.blocknight.ui.components.difficultyName
import pt.tiago.blocknight.ui.district.objectiveText
import pt.tiago.blocknight.ui.theme.BlockNightColors

/**
 * A campaign floor or the daily challenge (`daily = true`). The campaign saves its result with [onFinished],
 * the daily with [onDailyFinished]; both return false when saving fails. Each one keeps its saved session
 * in its own file, so playing the daily never replaces a saved campaign match.
 */
@Composable
fun CampaignGame(
    app: App,
    feedback: Feedback,
    ordinal: Int,
    difficulty: String,
    attempt: Int,
    daily: Boolean = false,
    date: String = "",
    settings: Settings,
    onFinished: (FloorResult) -> Boolean = { true },
    onDailyFinished: (DailyResult) -> Boolean = { true },
    onExit: () -> Unit,
    onNext: () -> Unit,
    onRetry: () -> Unit,
) {
    val container = app.container
    val store = if (daily) container.daily else container.campaign
    val rules = remember(ordinal, difficulty) { container.content.rules(ordinal, difficulty) }
    val variant = remember(ordinal, difficulty) { container.content.variant(ordinal, difficulty)!! }
    val saved = remember(ordinal, difficulty, attempt, daily) {
        store.read()?.takeIf { it.ordinal == ordinal && it.difficulty == difficulty && it.daily == daily }
    }
    val sessionId = remember(ordinal, difficulty, attempt, daily) { saved?.sessionId ?: UUID.randomUUID().toString() }
    val restored = remember(ordinal, difficulty, attempt, daily) { saved?.let { runCatching { it.snapshot.restore(rules) }.getOrNull() } }

    val districtName = Districts.districtOf(ordinal).name
    val objectiveLine = objectiveText(container.content, ordinal, difficulty)
    val title = if (daily) stringResource(R.string.daily_title) else stringResource(R.string.hud_floor, ordinal)
    val subtitle = if (daily) {
        stringResource(R.string.hud_floor, ordinal) + " · " + difficultyName(difficulty)
    } else {
        "$districtName · " + difficultyName(difficulty)
    }
    val config = HostConfig(
        rules = rules,
        sessionKey = listOf(ordinal, difficulty, attempt, daily),
        restored = restored,
        title = title,
        subtitle = { subtitle },
        counter = { state ->
            CampaignHud.piecesLeft(rules.maxPieces, state.pieces)?.let { left ->
                Counter(left.toString(), countText(R.string.hud_pieces_left_one, R.string.hud_pieces_left_other, left))
            }
        },
        showHint = ordinal <= 3 && !daily,
        save = { state ->
            runCatching { store.write(CampaignSession(sessionId, ordinal, difficulty, GameSnapshot.of(state), daily)) }
        },
        clear = { store.clear() },
        settings = settings,
        onFinished = { state ->
            val won = state.outcome == Outcome.Won
            if (daily) {
                onDailyFinished(
                    DailyResult(
                        sessionId = sessionId,
                        date = date,
                        floorId = container.content.floorId(ordinal),
                        difficulty = difficulty,
                        won = won,
                        pieces = state.pieces,
                        score = state.score,
                    ),
                )
            } else {
                onFinished(
                    FloorResult(
                        sessionId = sessionId,
                        floorId = container.content.floorId(ordinal),
                        difficulty = difficulty,
                        won = won,
                        pieces = state.pieces,
                        score = state.score,
                        threeStarMaxPieces = variant.threeStarMaxPieces,
                        twoStarMaxPieces = variant.twoStarMaxPieces,
                    ),
                )
            }
        },
    )

    GameHost(
        config = config,
        feedback = feedback,
        hud = { state, compact -> ObjectiveCard(objectiveLine, state, rules, variant.threeStarMaxPieces, variant.twoStarMaxPieces, compact) },
        result = { state, saveFailed, retrySave ->
            val won = state.outcome == Outcome.Won
            val stars = ProgressReducer.stars(
                FloorResult(sessionId, "", difficulty, won, state.pieces, state.score, variant.threeStarMaxPieces, variant.twoStarMaxPieces),
            )
            ResultFrame(
                title = stringResource(if (won) R.string.result_won else R.string.result_lost),
                saveFailed = saveFailed,
                primary = if (won && !daily) stringResource(R.string.result_next) else stringResource(R.string.result_retry),
                onPrimary = if (won && !daily) onNext else onRetry,
                exitLabel = stringResource(if (daily) R.string.cd_back else R.string.result_district),
                onExit = onExit,
                onRetrySave = retrySave,
            ) {
                if (!won) {
                    BodyLine(
                        if ((state.outcome as? Outcome.Lost)?.reason == LossReason.OUT_OF_PIECES) {
                            stringResource(R.string.loss_out_of_pieces)
                        } else {
                            stringResource(R.string.loss_top)
                        },
                    )
                }
                if (!daily && won) StarRow(stars = stars, size = 36.sp)
                BodyLine("${stringResource(R.string.result_pieces)}: ${state.pieces}")
                BodyLine("${stringResource(R.string.result_lines)}: ${state.lines}")
                BodyLine("${stringResource(R.string.result_score)}: ${state.score}")
            }
        },
        onExit = { onExit() },
    )
}

/**
 * Objective card: text, progress, and the star limit still reachable with the points (R8, DESIGN §5).
 * On short screens it collapses to one line.
 */
@Composable
fun ObjectiveCard(label: String, state: GameState, rules: Rules, starsThree: Int, starsTwo: Int, compact: Boolean) {
    val objective = rules.objective ?: return
    val progress = objectiveProgress(state, rules, objective)
    Column(modifier = Modifier.fillMaxWidth().card(radius = 14.dp).padding(horizontal = 12.dp, vertical = if (compact) 6.dp else 9.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall.copy(color = BlockNightColors.TextStrong, fontSize = 13.sp, fontWeight = FontWeight.Bold),
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(text = progress.value, style = MaterialTheme.typography.bodySmall.copy(color = BlockNightColors.TextStrong, fontSize = 12.sp))
        }
        ProgressBar(fraction = progress.fraction, modifier = Modifier.padding(top = if (compact) 4.dp else 6.dp))
        if (!compact) {
            val reachable = CampaignHud.reachableStars(state.pieces, state.active != null, starsThree, starsTwo)
            val limit = when (reachable) {
                3 -> stringResource(R.string.hud_star_limit, "★★★", starsThree)
                2 -> stringResource(R.string.hud_star_limit, "★★", starsTwo)
                else -> stringResource(R.string.hud_star_any)
            }
            Text(
                text = limit + " · " + stringResource(R.string.hud_points, state.score.toString()),
                style = MaterialTheme.typography.bodySmall.copy(color = BlockNightColors.TextMuted, fontSize = 11.sp),
                modifier = Modifier.padding(top = 4.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Progress of the objective: bar fraction and the `done / total` text. */
data class ObjectiveProgress(val fraction: Float, val value: String)

fun objectiveProgress(state: GameState, rules: Rules, objective: Objective): ObjectiveProgress = when (objective) {
    is Objective.Lines -> ObjectiveProgress(state.lines.toFloat() / objective.n, "${minOf(state.lines, objective.n)} / ${objective.n}")
    is Objective.Score -> ObjectiveProgress(state.score.toFloat() / objective.points, "${minOf(state.score, objective.points)} / ${objective.points}")
    is Objective.Quads -> ObjectiveProgress(state.quads.toFloat() / objective.n, "${minOf(state.quads, objective.n)} / ${objective.n}")
    is Objective.TSpins -> ObjectiveProgress(state.tspins.toFloat() / objective.n, "${minOf(state.tspins, objective.n)} / ${objective.n}")
    is Objective.Combo -> ObjectiveProgress(state.maxStreak.toFloat() / objective.n, "${minOf(state.maxStreak, objective.n)} / ${objective.n}")
    Objective.Targets -> {
        val total = maxOf(1, rules.initialTargetCount)
        val left = state.board.countCells { it == Cell.TARGET }
        ObjectiveProgress((total - left).toFloat() / total, "${total - left} / $total")
    }
    Objective.Dig -> {
        val total = maxOf(1, rules.initialSpecialCount)
        val left = state.board.countCells { Cell.isSpecial(it) }
        ObjectiveProgress((total - left).toFloat() / total, "${total - left} / $total")
    }
}
