package pt.tiago.blocknight.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import pt.tiago.blocknight.R
import pt.tiago.blocknight.core.model.Command
import pt.tiago.blocknight.ui.theme.BlockNightColors

/** Um tick de 60 Hz em milissegundos (para DAS e ARR). */
private const val TICK_MS = 1000.0 / 60.0

/**
 * Pad de botões (PLANO §7.6, DESIGN §4): ← → ↓ ⟲ ⟳ e Guardar, Largar. Alvos de pelo menos 48 dp. Os
 * movimentos repetem com DAS (primeira pausa) e ARR (intervalo). No canhoto a ordem espelha-se.
 */
@Composable
fun ButtonPad(
    leftHanded: Boolean,
    allowHold: Boolean,
    das: Int,
    arr: Int,
    emit: (Command) -> Unit,
) {
    val top = listOf(
        Key("←", Command.SHIFT_LEFT, repeat = true, label = R.string.pad_left),
        Key("→", Command.SHIFT_RIGHT, repeat = true, label = R.string.pad_right),
        Key("↓", null, repeat = false, label = R.string.pad_down, soft = true),
        Key("⟲", Command.ROTATE_CCW, repeat = false, label = R.string.pad_rotate_ccw),
        Key("⟳", Command.ROTATE_CW, repeat = false, label = R.string.pad_rotate_cw),
    )
    // Floors without Hold do not show a dead button (PLANO §2.2: no inactive buttons).
    val bottom = listOfNotNull(
        Key(null, Command.HOLD, repeat = false, label = R.string.pad_hold, textRes = R.string.pad_hold_short, weight = 2f).takeIf { allowHold },
        Key("⤓", Command.HARD_DROP, repeat = false, label = R.string.pad_drop, textRes = R.string.pad_drop_short, weight = 3f),
    )
    val rowTop = if (leftHanded) top.reversed() else top
    val rowBottom = if (leftHanded) bottom.reversed() else bottom
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            rowTop.forEach { key ->
                KeyCell(key, das, arr, emit, Modifier.weight(1f))
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            rowBottom.forEach { key ->
                KeyCell(key, das, arr, emit, Modifier.weight(key.weight))
            }
        }
    }
}

/** A pad key: a symbol, a word from strings.xml, or both (symbol first). */
private data class Key(
    val symbol: String?,
    val command: Command?,
    val repeat: Boolean,
    val label: Int,
    val soft: Boolean = false,
    val textRes: Int? = null,
    val weight: Float = 1f,
)

@Composable
private fun KeyCell(
    key: Key,
    das: Int,
    arr: Int,
    emit: (Command) -> Unit,
    modifier: Modifier,
) {
    val currentEmit by rememberUpdatedState(emit)
    var pressed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val description = stringResource(key.label)
    val command = key.command
    Box(
        modifier = modifier
            .height(48.dp)
            .background(if (pressed) BlockNightColors.SurfaceRaised else BlockNightColors.Surface, RoundedCornerShape(12.dp))
            .border(1.dp, if (pressed) BlockNightColors.Accent else BlockNightColors.Line, RoundedCornerShape(12.dp))
            .semantics { contentDescription = description }
            .pointerInput(key, das, arr) {
                // O gesto só deteta o início e o fim; a repetição corre numa corrotina do ecrã, porque o
                // escopo do gesto é restrito e não aceita corrotinas aninhadas.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    if (key.soft) currentEmit(Command.SOFT_DROP_START)
                    command?.let { currentEmit(it) }
                    val repeater = if (key.repeat && command != null) {
                        scope.launch {
                            delay((das * TICK_MS).toLong())
                            while (true) {
                                currentEmit(command)
                                delay((arr * TICK_MS).toLong().coerceAtLeast(1L))
                            }
                        }
                    } else {
                        null
                    }
                    try {
                        waitForUpOrCancellation()
                    } finally {
                        repeater?.cancel()
                        pressed = false
                        if (key.soft) currentEmit(Command.SOFT_DROP_STOP)
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val word = key.textRes?.let { stringResource(it) }
        Text(
            text = listOfNotNull(key.symbol, word).joinToString(" "),
            style = MaterialTheme.typography.titleLarge.copy(color = BlockNightColors.TextStrong, fontSize = 16.sp),
        )
    }
}

