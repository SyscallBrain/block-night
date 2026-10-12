@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package pt.tiago.blocknight.ui.game

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.delay
import pt.tiago.blocknight.R
import pt.tiago.blocknight.core.model.Board
import pt.tiago.blocknight.core.model.Command
import pt.tiago.blocknight.core.model.Event
import pt.tiago.blocknight.core.model.GameState
import pt.tiago.blocknight.core.model.PieceType
import pt.tiago.blocknight.core.model.Rules
import pt.tiago.blocknight.core.progress.Settings
import pt.tiago.blocknight.core.random.BagRandomizer
import pt.tiago.blocknight.core.rules.Engine
import pt.tiago.blocknight.game.ClearLabel
import pt.tiago.blocknight.game.Feedback
import pt.tiago.blocknight.game.GameLayout
import pt.tiago.blocknight.game.GameSession
import pt.tiago.blocknight.game.GestureMapper
import pt.tiago.blocknight.game.LabelTimer
import pt.tiago.blocknight.ui.components.PrimaryButton
import pt.tiago.blocknight.ui.components.SecondaryButton
import pt.tiago.blocknight.ui.components.card
import pt.tiago.blocknight.ui.theme.BlockNightColors

/** A value with its caption, shown at the right of the match header (DESIGN §5). */
data class Counter(val value: String, val caption: String)

/** Match parameters: rules, persistence and texts. Campaign, daily and free modes share the same host. */
data class HostConfig(
    val rules: Rules,
    val sessionKey: Any,
    val restored: GameState?,
    val title: String,
    val subtitle: @Composable (GameState) -> String,
    val counter: @Composable (GameState) -> Counter?,
    val showHint: Boolean,
    val save: (GameState) -> Unit,
    val clear: () -> Unit,
    val onFinished: (GameState) -> Boolean,
    val settings: Settings,
)

/**
 * Host of any match (PLANO §3.3, §7). Ticks run on the frame clock; the UI draws the state and sends
 * commands. A restored match starts paused; a new one starts with the 3-2-1 countdown.
 *
 * All match state is keyed by [HostConfig.sessionKey]: a retry or the next floor is a fresh composition, so
 * nothing (result sheet, labels, pause) leaks from the previous session.
 */
@Composable
fun GameHost(
    config: HostConfig,
    feedback: Feedback,
    hud: @Composable (GameState, Boolean) -> Unit,
    result: @Composable (GameState, Boolean, () -> Unit) -> Unit,
    onExit: (GameState) -> Unit,
) {
    key(config.sessionKey) {
        SessionHost(config, feedback, hud, result, onExit)
    }
}

@Composable
private fun SessionHost(
    config: HostConfig,
    feedback: Feedback,
    hud: @Composable (GameState, Boolean) -> Unit,
    result: @Composable (GameState, Boolean, () -> Unit) -> Unit,
    onExit: (GameState) -> Unit,
) {
    val rules = config.rules
    val buttons = config.settings.controls == Settings.CONTROLS_BUTTONS
    val leftHanded = config.settings.leftHanded
    val session = remember {
        val restored = config.restored
        if (restored != null) GameSession(restored, GameSession.Phase.PAUSED) else GameSession(Engine.newGame(rules))
    }
    val labels = remember { LabelTimer() }

    // Changes every drawn frame while the match runs; reading it recomposes the board.
    var frameNanos by remember { mutableLongStateOf(0L) }
    var pauseOpen by remember { mutableStateOf(session.phase == GameSession.Phase.PAUSED) }
    var restartConfirm by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf<GameState?>(null) }
    var saveFailed by remember { mutableStateOf(false) }

    fun saveSnapshot() {
        val state = session.state
        if (state.outcome == null) config.save(state)
    }

    fun pauseNow() {
        session.pause()
        saveSnapshot()
        pauseOpen = true
    }

    LaunchedEffect(Unit) {
        while (true) {
            val phase = session.phase
            if (phase == GameSession.Phase.PAUSED || phase == GameSession.Phase.RESULT) {
                // Nothing moves: no frames are requested, so a paused match does not redraw 60 times a second.
                if (phase == GameSession.Phase.PAUSED && !pauseOpen && finished == null) pauseOpen = true
                delay(PAUSED_POLL_MS)
                continue
            }
            withFrameNanos { now ->
                val events = session.frame(now)
                events.forEach { event ->
                    feedback.onEvent(event)
                    if (event is Event.Locked) saveSnapshot()
                }
                labels.onEvents(events, now)
                if (session.phase == GameSession.Phase.RESULT && finished == null && session.state.outcome != null) {
                    finished = session.state
                    saveFailed = !config.onFinished(session.state)
                }
                frameNanos = now
            }
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) {
        session.pause()
        saveSnapshot()
    }

    // Back during the match pauses and never leaves it (PLANO §3). Open sheets handle Back themselves.
    BackHandler(enabled = finished == null && !pauseOpen && !restartConfirm) { pauseNow() }

    val now = frameNanos
    val state = session.state
    val density = LocalDensity.current
    Box(modifier = Modifier.fillMaxSize().background(BlockNightColors.Background).safeDrawingPadding()) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 8.dp)) {
            val compact = GameLayout.isCompact(maxHeight.value)
            val previewShown = GameLayout.previewShown(rules.previewCount, maxHeight.value)
            Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Header(
                    title = config.title,
                    subtitle = config.subtitle(state),
                    counter = config.counter(state),
                    onPause = { pauseNow() },
                )
                hud(state, compact)
                // The whole play area takes gestures (not just the board), so a thumb can drag below or beside
                // the board without covering the pieces. Taps are read against the board's centre.
                BoxWithConstraints(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    val areaWidthPx = constraints.maxWidth.toFloat()
                    val cellPx = GameLayout.cellPx(areaWidthPx, constraints.maxHeight.toFloat(), density.density)
                    val cellDp: Dp = with(density) { cellPx.toDp() }
                    val boardHeight = cellDp * Board.VISIBLE + (GameLayout.FRAME_DP * 2).dp
                    val gestures = remember(cellPx, areaWidthPx, leftHanded) {
                        GestureMapper(
                            cellWidthPx = cellPx.toFloat(),
                            boardCenterX = GameLayout.boardCenterX(areaWidthPx, cellPx, density.density, leftHanded),
                            densityPx = density.density,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(if (!buttons) Modifier.gestureInput(gestures, session, feedback) else Modifier),
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(GameLayout.GAP_DP.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.Top,
                    ) {
                        // Left-handed: the side column moves to the left (PLANO §7.6).
                        val side: @Composable () -> Unit = {
                            SidePanel(state, rules, previewShown, boardHeight) {
                                if (session.command(Command.HOLD)) feedback.onCommand(Command.HOLD)
                            }
                        }
                        if (leftHanded) side()
                        val danger = GameLayout.inDanger(state.board)
                        Box(
                            modifier = Modifier
                                .background(BlockNightColors.Surface, RoundedCornerShape(10.dp))
                                .border(
                                    1.dp,
                                    if (danger) BlockNightColors.Pink.copy(alpha = 0.6f) else BlockNightColors.Line,
                                    RoundedCornerShape(10.dp),
                                )
                                .padding(GameLayout.FRAME_DP.dp),
                        ) {
                            Box(modifier = Modifier.size(cellDp * Board.WIDTH, cellDp * Board.VISIBLE)) {
                                BoardCanvas(board = state.board, active = state.active, cellSize = cellDp, redraw = now)
                                LabelOverlay(labels.visible(now), state, cellDp)
                                session.countdownDigit()?.let { digit ->
                                    Text(
                                        text = digit.toString(),
                                        style = MaterialTheme.typography.displayLarge.copy(
                                            color = BlockNightColors.Accent,
                                            fontSize = 96.sp,
                                            lineHeight = 104.sp,
                                        ),
                                        modifier = Modifier.align(Alignment.Center),
                                    )
                                }
                            }
                        }
                        if (!leftHanded) side()
                    }
                }
                if (buttons) {
                    ButtonPad(
                        leftHanded = leftHanded,
                        allowHold = rules.allowHold,
                        das = config.settings.das,
                        arr = config.settings.arr,
                        emit = { command -> if (session.command(command)) feedback.onCommand(command) },
                    )
                } else if (config.showHint) {
                    Text(
                        text = stringResource(R.string.gesture_hint),
                        style = MaterialTheme.typography.bodySmall.copy(color = BlockNightColors.TextMuted, fontSize = 11.sp),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }

    if (pauseOpen && session.phase == GameSession.Phase.PAUSED && !restartConfirm) {
        PauseSheet(
            onResume = {
                pauseOpen = false
                session.resume()
            },
            onRestart = { restartConfirm = true },
            onSaveAndExit = {
                saveSnapshot()
                onExit(session.state)
            },
        )
    }
    if (restartConfirm) {
        ConfirmSheet(
            text = stringResource(R.string.pause_restart_confirm),
            confirm = stringResource(R.string.confirm_yes),
            cancel = stringResource(R.string.confirm_no),
            onConfirm = {
                config.clear()
                labels.clear()
                restartConfirm = false
                pauseOpen = false
                session.restart(Engine.newGame(rules))
            },
            onCancel = { restartConfirm = false },
        )
    }
    finished?.let { end ->
        // The last argument saves the result again without replaying the match.
        result(end, saveFailed) { saveFailed = !config.onFinished(end) }
    }
}

private const val PAUSED_POLL_MS = 100L

/**
 * Touch on the board becomes discrete commands. If the system cancels the gesture (another window, a
 * recomposition), soft drop stops instead of staying on.
 */
private fun Modifier.gestureInput(gestures: GestureMapper, session: GameSession, feedback: Feedback): Modifier =
    pointerInput(gestures, session, feedback) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            send(gestures.onDown(down.position.x, down.position.y, down.uptimeMillis), session, feedback)
            var ended = false
            try {
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) {
                        send(gestures.onUp(change.position.x, change.position.y, change.uptimeMillis), session, feedback)
                        ended = true
                        break
                    }
                    send(gestures.onMove(change.position.x, change.position.y, change.uptimeMillis), session, feedback)
                }
            } finally {
                if (!ended) send(gestures.onCancel(), session, feedback)
            }
        }
    }

/** Sends the gesture commands to the session, which applies them on the next tick. Only accepted ones make a sound. */
private fun send(commands: List<Command>, session: GameSession, feedback: Feedback) {
    commands.forEach { if (session.command(it)) feedback.onCommand(it) }
}

@Composable
private fun Header(title: String, subtitle: String, counter: Counter?, onPause: () -> Unit) {
    val pauseLabel = stringResource(R.string.pause_title)
    Row(
        modifier = Modifier.fillMaxWidth().height(52.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clickable(role = Role.Button, onClick = onPause)
                .semantics { contentDescription = pauseLabel },
            contentAlignment = Alignment.Center,
        ) {
            Box(modifier = Modifier.size(40.dp).card(radius = 20.dp), contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.size(14.dp)) {
                    drawRect(BlockNightColors.TextStrong, size = Size(4.dp.toPx(), 14.dp.toPx()))
                    drawRect(BlockNightColors.TextStrong, topLeft = Offset(8.dp.toPx(), 0f), size = Size(4.dp.toPx(), 14.dp.toPx()))
                }
            }
        }
        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(color = BlockNightColors.TextStrong, fontSize = 18.sp, lineHeight = 22.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(color = BlockNightColors.TextMuted, fontSize = 12.sp, lineHeight = 16.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Column(modifier = Modifier.width(64.dp), horizontalAlignment = Alignment.End) {
            if (counter != null) {
                Text(
                    text = counter.value,
                    style = MaterialTheme.typography.titleLarge.copy(color = BlockNightColors.TextStrong, fontSize = 20.sp, lineHeight = 24.sp),
                    maxLines = 1,
                )
                Text(
                    text = counter.caption,
                    style = MaterialTheme.typography.bodySmall.copy(color = BlockNightColors.TextMuted, fontSize = 11.sp, lineHeight = 14.sp),
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * The single side column, as tall as the board: Hold and Next on top, speed, combo and back-to-back at the
 * bottom. Tapping the Hold slot holds the piece (it sits above the gesture area, so it never rotates).
 */
@Composable
private fun SidePanel(state: GameState, rules: Rules, previewShown: Int, height: Dp, onHold: () -> Unit) {
    Column(
        modifier = Modifier.width(GameLayout.SIDE_DP.dp).height(height),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val holdLabel = stringResource(R.string.pad_hold)
        SideBox(
            title = stringResource(R.string.hud_hold),
            modifier = if (rules.allowHold) {
                Modifier
                    .clickable(role = Role.Button, onClick = onHold)
                    .semantics { contentDescription = holdLabel }
            } else {
                Modifier
            },
        ) {
            MiniPiece(state.hold, GameLayout.MINI_CELL_DP.dp, dimmed = state.holdUsed || !rules.allowHold, placeholder = true)
        }
        SideBox(title = stringResource(R.string.hud_next)) {
            for (i in 0 until previewShown) {
                MiniPiece(
                    BagRandomizer.pieceAt(rules, state.nextIndex + i),
                    (if (i == 0) GameLayout.MINI_CELL_DP else GameLayout.MINI_NEXT_CELL_DP).dp,
                )
            }
        }
        Spacer(Modifier.weight(1f))
        SideBox(title = null) {
            Stat(stringResource(R.string.hud_speed), state.level.toString())
            Stat(stringResource(R.string.hud_combo), if (state.combo > 0) "×${state.combo}" else "–")
            Stat(stringResource(R.string.hud_b2b), if (state.backToBack) "✓" else "–")
        }
    }
}

@Composable
private fun SideBox(title: String?, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().card(radius = 12.dp).clip(RoundedCornerShape(12.dp)).then(modifier)
            .padding(horizontal = 3.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (title != null) ColumnLabel(title)
        content()
    }
}

/** Column caption: shrinks until it fits the 52 dp column (“PRÓXIMAS” is wide in pt). */
@Composable
private fun ColumnLabel(text: String) {
    BasicText(
        text = text.uppercase(),
        style = MaterialTheme.typography.bodySmall.copy(color = BlockNightColors.TextMuted, lineHeight = 12.sp),
        maxLines = 1,
        autoSize = TextAutoSize.StepBased(minFontSize = 7.sp, maxFontSize = 10.sp, stepSize = 0.5.sp),
    )
}

@Composable
private fun Stat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        ColumnLabel(label)
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge.copy(color = BlockNightColors.TextStrong, fontSize = 15.sp, lineHeight = 18.sp),
        )
    }
}

/** A piece in its spawn rotation, centred in a 4×2 box of [cell]-sized cells. */
@Composable
internal fun MiniPiece(type: PieceType?, cell: Dp, dimmed: Boolean = false, placeholder: Boolean = false) {
    Canvas(modifier = Modifier.size(cell * GameLayout.MINI_COLS, cell * GameLayout.MINI_ROWS)) {
        if (type == null) {
            // An empty Hold slot shows a quiet dashed outline, so it reads as "nothing held yet", not as a gap.
            if (placeholder) {
                val inset = cell.toPx() * 0.5f
                drawRoundRect(
                    color = BlockNightColors.Line,
                    topLeft = Offset(inset, 1.dp.toPx()),
                    size = Size(size.width - 2 * inset, size.height - 2.dp.toPx()),
                    cornerRadius = CornerRadius(4.dp.toPx()),
                    style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
                )
            }
            return@Canvas
        }
        val c = cell.toPx()
        val cells = GameLayout.miniCells(type)
        val color = pieceColor(type).copy(alpha = if (dimmed) 0.35f else 1f)
        for (i in cells.indices step 2) {
            drawRoundRect(
                color = color,
                topLeft = Offset(cells[i] * c + c * 0.04f, cells[i + 1] * c + c * 0.04f),
                size = Size(c * 0.92f, c * 0.92f),
                cornerRadius = CornerRadius(c * 0.11f),
            )
        }
    }
}

/**
 * Clear label (DESIGN §5): upper third of the board, or the lower third when the active piece is up there,
 * so it never covers the piece. It disappears on its own after 900 ms.
 */
@Composable
private fun LabelOverlay(label: ClearLabel?, state: GameState, cell: Dp) {
    if (label == null) return
    val text = when (label) {
        ClearLabel.Quad -> stringResource(R.string.label_quad)
        ClearLabel.Perfect -> stringResource(R.string.label_perfect)
        ClearLabel.BackToBack -> stringResource(R.string.label_b2b)
        is ClearLabel.Combo -> stringResource(R.string.label_combo, label.combo)
        is ClearLabel.Spin -> when {
            label.mini -> stringResource(R.string.label_tspin_mini)
            label.lines == 1 -> stringResource(R.string.label_tspin_single)
            label.lines == 2 -> stringResource(R.string.label_tspin_double)
            label.lines >= 3 -> stringResource(R.string.label_tspin_triple)
            else -> stringResource(R.string.label_tspin)
        }
    }
    val pieceHigh = (state.active?.y ?: 0) >= Board.VISIBLE * 2 / 3
    Box(
        modifier = Modifier.size(cell * Board.WIDTH, cell * Board.VISIBLE),
        contentAlignment = if (pieceHigh) Alignment.BottomCenter else Alignment.TopCenter,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.displayLarge.copy(color = BlockNightColors.Accent, fontSize = 18.sp, lineHeight = 24.sp),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(vertical = cell * 3)
                .background(BlockNightColors.BackgroundDeep.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun PauseSheet(onResume: () -> Unit, onRestart: () -> Unit, onSaveAndExit: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // Dismissing the sheet (Back or a tap outside) resumes, with the countdown.
    ModalBottomSheet(
        onDismissRequest = onResume,
        sheetState = sheetState,
        containerColor = BlockNightColors.Surface,
        scrimColor = BlockNightColors.BackgroundDeep.copy(alpha = 0.72f),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(text = stringResource(R.string.pause_title), style = MaterialTheme.typography.headlineMedium.copy(color = BlockNightColors.TextStrong))
            PrimaryButton(text = stringResource(R.string.pause_resume), onClick = onResume)
            SecondaryButton(text = stringResource(R.string.pause_restart), onClick = onRestart, modifier = Modifier.fillMaxWidth())
            SecondaryButton(text = stringResource(R.string.pause_save_exit), onClick = onSaveAndExit, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun ConfirmSheet(text: String, confirm: String, cancel: String, onConfirm: () -> Unit, onCancel: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onCancel,
        sheetState = sheetState,
        containerColor = BlockNightColors.Surface,
        scrimColor = BlockNightColors.BackgroundDeep.copy(alpha = 0.72f),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(text = text, style = MaterialTheme.typography.bodyLarge.copy(color = BlockNightColors.Text))
            PrimaryButton(text = confirm, onClick = onConfirm)
            SecondaryButton(text = cancel, onClick = onCancel, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** Shared result sheet: the body (stars or statistics) comes from the caller. */
@Composable
fun ResultFrame(
    title: String,
    saveFailed: Boolean,
    primary: String?,
    onPrimary: () -> Unit,
    exitLabel: String,
    onExit: () -> Unit,
    onRetrySave: () -> Unit = {},
    body: @Composable () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onExit,
        sheetState = sheetState,
        containerColor = BlockNightColors.Surface,
        scrimColor = BlockNightColors.BackgroundDeep.copy(alpha = 0.72f),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(text = title, style = MaterialTheme.typography.headlineMedium.copy(color = BlockNightColors.TextStrong))
            body()
            if (saveFailed) {
                Text(text = stringResource(R.string.result_save_failed), style = MaterialTheme.typography.bodyLarge.copy(color = BlockNightColors.Pink))
                PrimaryButton(text = stringResource(R.string.result_retry_save), onClick = onRetrySave)
            }
            if (primary != null) PrimaryButton(text = primary, onClick = onPrimary)
            SecondaryButton(text = exitLabel, onClick = onExit, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
fun BodyLine(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodyLarge.copy(color = BlockNightColors.Text))
}
