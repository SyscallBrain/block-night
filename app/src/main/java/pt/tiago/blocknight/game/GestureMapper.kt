package pt.tiago.blocknight.game

import pt.tiago.blocknight.core.model.Command

/**
 * Converte o toque e o arrasto em comandos discretos (PLANO §7.6). Só uma direção dominante por gesto:
 * um deslize vertical rápido não move a peça na horizontal. Um segundo dedo é ignorado pela tela.
 *
 * @param cellWidthPx largura de uma célula do tabuleiro em píxeis.
 * @param densityPx píxeis por dp, para os limiares de velocidade em dp/s.
 */
class GestureMapper(
    private val cellWidthPx: Float,
    private val boardCenterX: Float,
    private val densityPx: Float,
    private val sensitivity: Float = 1f,
    private val rotateAlwaysClockwise: Boolean = false,
) {
    private enum class Axis { UNDECIDED, HORIZONTAL, VERTICAL }

    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var downTime = 0L
    private var axis = Axis.UNDECIDED
    private var carryX = 0f
    private var softDropOn = false
    private var active = false
    private var moved = false

    fun onDown(x: Float, y: Float, timeMs: Long): List<Command> {
        downX = x; downY = y; lastX = x; lastY = y
        downTime = timeMs
        axis = Axis.UNDECIDED
        carryX = 0f
        softDropOn = false
        active = true
        moved = false
        return emptyList()
    }

    fun onMove(x: Float, y: Float, timeMs: Long): List<Command> {
        if (!active) return emptyList()
        val out = ArrayList<Command>()
        if (axis == Axis.UNDECIDED) {
            val dx = x - downX
            val dy = y - downY
            if (kotlin.math.abs(dx) > SLOP_DP * densityPx || kotlin.math.abs(dy) > SLOP_DP * densityPx) {
                axis = if (kotlin.math.abs(dx) > kotlin.math.abs(dy)) Axis.HORIZONTAL else Axis.VERTICAL
                moved = true
            }
        }
        when (axis) {
            Axis.HORIZONTAL -> {
                carryX += x - lastX
                val step = STEP_CELLS * cellWidthPx * sensitivity
                while (carryX >= step) { out += Command.SHIFT_RIGHT; carryX -= step }
                while (carryX <= -step) { out += Command.SHIFT_LEFT; carryX += step }
            }
            Axis.VERTICAL -> {
                val down = y > downY
                if (down && !softDropOn && y - downY > SLOP_DP * densityPx) {
                    softDropOn = true
                    out += Command.SOFT_DROP_START
                } else if (!down && softDropOn) {
                    softDropOn = false
                    out += Command.SOFT_DROP_STOP
                }
            }
            Axis.UNDECIDED -> Unit
        }
        lastX = x; lastY = y
        return out
    }

    fun onUp(x: Float, y: Float, timeMs: Long): List<Command> {
        if (!active) return emptyList()
        active = false
        val out = ArrayList<Command>()
        val dx = x - downX
        val dy = y - downY
        val elapsed = (timeMs - downTime).coerceAtLeast(1L)
        val distanceCells = kotlin.math.hypot(dx, dy) / cellWidthPx
        val velocityDp = (kotlin.math.hypot(dx, dy) / densityPx) / (elapsed / 1000f)
        // Decide-se pelo deslocamento total do gesto: um deslize rápido não depende dos movimentos intermédios.
        val vertical = kotlin.math.abs(dy) > kotlin.math.abs(dx)
        when {
            vertical && dy > 0 && velocityDp > FAST_DOWN_DP_S && distanceCells > FAST_MIN_CELLS -> {
                if (softDropOn) out += Command.SOFT_DROP_STOP
                out += Command.HARD_DROP
            }
            vertical && dy < 0 && velocityDp > FAST_UP_DP_S && distanceCells > FAST_MIN_CELLS -> out += Command.HOLD
            softDropOn -> out += Command.SOFT_DROP_STOP
            axis == Axis.UNDECIDED && elapsed < TAP_MAX_MS && distanceCells < TAP_MAX_CELLS -> {
                val clockwise = rotateAlwaysClockwise || x >= boardCenterX
                out += if (clockwise) Command.ROTATE_CW else Command.ROTATE_CCW
            }
        }
        softDropOn = false
        return out
    }

    /** Gesto cancelado pelo sistema: a queda suave para. */
    fun onCancel(): List<Command> {
        active = false
        val stop = if (softDropOn) listOf(Command.SOFT_DROP_STOP) else emptyList()
        softDropOn = false
        return stop
    }

    companion object {
        const val SLOP_DP = 8f
        const val STEP_CELLS = 0.9f
        const val FAST_DOWN_DP_S = 1200f
        const val FAST_UP_DP_S = 800f
        const val FAST_MIN_CELLS = 1.5f
        const val TAP_MAX_MS = 200L
        const val TAP_MAX_CELLS = 0.3f
    }
}
