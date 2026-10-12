package pt.tiago.blocknight.game

import pt.tiago.blocknight.core.model.Command
import pt.tiago.blocknight.core.model.Event
import pt.tiago.blocknight.core.model.GameState
import pt.tiago.blocknight.core.model.Outcome
import pt.tiago.blocknight.core.rules.Engine

/**
 * Sessão de uma partida na app: converte tempo real em ticks de 60 Hz, guarda os comandos pendentes e a
 * pausa. Não conhece a UI: a tela desenha o estado e os eventos (PLANO §7.1, §7.4).
 */
class GameSession(initial: GameState, startPhase: Phase = Phase.COUNTDOWN) {
    var state: GameState = initial
        private set

    var phase: Phase = startPhase
        private set

    private val pending = ArrayList<Command>()
    private var accumulatorNanos = 0L
    private var lastFrameNanos: Long? = null
    private var countdownNanosLeft = if (startPhase == Phase.COUNTDOWN) COUNTDOWN_NANOS else 0L

    /** Eventos produzidos desde a última leitura; a UI anima e nunca recalcula regras. */
    private val outbox = ArrayList<Event>()

    enum class Phase { PLAYING, PAUSED, COUNTDOWN, RESULT }

    /** Queues [command] for the next tick. Returns false when the match is not running (countdown, pause, result). */
    fun command(command: Command): Boolean {
        if (phase != Phase.PLAYING) return false
        pending += command
        return true
    }

    fun pause() {
        if (phase == Phase.PLAYING || phase == Phase.COUNTDOWN) {
            phase = Phase.PAUSED
            lastFrameNanos = null
            accumulatorNanos = 0L
            pending.clear()
            // Soft drop não pode continuar durante a pausa.
            if (state.softDrop) state = state.copy(softDrop = false)
        }
    }

    /** Retoma com a contagem 3-2-1 (PLANO §3.4): sem gravidade até ao fim da contagem. */
    fun resume() {
        if (phase == Phase.PAUSED) {
            phase = Phase.COUNTDOWN
            countdownNanosLeft = COUNTDOWN_NANOS
            lastFrameNanos = null
        }
    }

    /** Substitui a partida por uma nova, a começar com a contagem (recomeçar). */
    fun restart(newState: GameState) {
        state = newState
        pending.clear()
        outbox.clear()
        accumulatorNanos = 0L
        lastFrameNanos = null
        countdownNanosLeft = COUNTDOWN_NANOS
        phase = Phase.COUNTDOWN
    }

    /** Número a mostrar na contagem (3, 2, 1) ou null se não há contagem. */
    fun countdownDigit(): Int? = if (phase == Phase.COUNTDOWN) ((countdownNanosLeft - 1) / 1_000_000_000L + 1).toInt() else null

    /**
     * Avança com o tempo decorrido desde o último quadro. Devolve os eventos novos.
     * No máximo [MAX_TICKS_PER_FRAME] ticks por quadro: se ficar atrasada, o jogo abranda em vez de saltar.
     */
    fun frame(nowNanos: Long): List<Event> {
        val last = lastFrameNanos
        lastFrameNanos = nowNanos
        if (last == null) return drain()
        val elapsed = (nowNanos - last).coerceAtLeast(0L)

        when (phase) {
            Phase.COUNTDOWN -> {
                countdownNanosLeft -= elapsed
                if (countdownNanosLeft <= 0L) {
                    phase = Phase.PLAYING
                    accumulatorNanos = 0L
                }
            }
            Phase.PLAYING -> {
                accumulatorNanos += elapsed
                var ticks = 0
                while (accumulatorNanos >= TICK_NANOS && ticks < MAX_TICKS_PER_FRAME && phase == Phase.PLAYING) {
                    accumulatorNanos -= TICK_NANOS
                    tick()
                    ticks++
                }
                if (accumulatorNanos > TICK_NANOS * MAX_TICKS_PER_FRAME) accumulatorNanos = TICK_NANOS
            }
            else -> Unit
        }
        return drain()
    }

    /** Um tick com os comandos pendentes. */
    internal fun tick() {
        val commands = if (pending.isEmpty()) emptyList() else ArrayList(pending).also { pending.clear() }
        val result = Engine.step(state, commands)
        state = result.state
        outbox += result.events
        if (state.isFinished) {
            phase = Phase.RESULT
        }
    }

    private fun drain(): List<Event> {
        if (outbox.isEmpty()) return emptyList()
        val out = ArrayList(outbox)
        outbox.clear()
        return out
    }

    fun outcome(): Outcome? = state.outcome

    companion object {
        const val TICK_NANOS = 1_000_000_000L / 60L
        const val MAX_TICKS_PER_FRAME = 5
        const val COUNTDOWN_NANOS = 3_000_000_000L
    }
}
