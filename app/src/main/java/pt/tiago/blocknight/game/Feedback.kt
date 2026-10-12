package pt.tiago.blocknight.game

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import pt.tiago.blocknight.R
import pt.tiago.blocknight.core.model.ClearKind
import pt.tiago.blocknight.core.model.Command
import pt.tiago.blocknight.core.model.Event
import pt.tiago.blocknight.core.model.Outcome

/**
 * Som e vibração da partida (DESIGN §7). No máximo quatro vozes; sem sons em pausa, porque a sessão não avança.
 * A vibração é uma por limpeza de linhas, nunca por movimento ou bloqueio.
 */
class Feedback(context: Context) {
    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(MAX_VOICES)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()
    private val ids: Map<Sound, Int> = Sound.entries.associateWith { pool.load(context, it.resId, 1) }
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
    private var lastMoveMs = 0L

    var soundOn: Boolean = true
    var vibrationOn: Boolean = true

    /** Sound for a command the session accepted. Rejected commands stay silent. */
    fun onCommand(command: Command) {
        val now = System.currentTimeMillis()
        when (command) {
            Command.SHIFT_LEFT, Command.SHIFT_RIGHT -> {
                // Os movimentos são frequentes: um som a cada 50 ms no máximo (DESIGN §7).
                if (now - lastMoveMs >= 50) {
                    lastMoveMs = now
                    play(Sound.MOVE)
                }
            }
            Command.ROTATE_CW, Command.ROTATE_CCW -> play(Sound.ROTATE)
            Command.HARD_DROP -> play(Sound.DROP)
            else -> Unit
        }
    }

    fun onEvent(event: Event) {
        when (event) {
            is Event.Locked -> play(Sound.LOCK)
            // Hold sounds only when the engine accepts it, never on a rejected hold.
            is Event.Held -> play(Sound.HOLD)
            is Event.LinesCleared -> {
                when (event.kind) {
                    ClearKind.SINGLE -> play(Sound.CLEAR1)
                    ClearKind.DOUBLE -> play(Sound.CLEAR2)
                    ClearKind.TRIPLE -> play(Sound.CLEAR3)
                    ClearKind.QUAD -> play(Sound.QUAD)
                }
                vibrate(if (event.kind == ClearKind.QUAD) 60L else 25L)
            }
            is Event.SpinScored -> play(Sound.SPIN)
            is Event.ComboReached -> if (event.combo >= 2) play(Sound.COMBO)
            is Event.Ended -> when (event.outcome) {
                Outcome.Won -> play(Sound.WIN)
                is Outcome.Lost -> play(Sound.LOSE)
                else -> Unit
            }
            else -> Unit
        }
    }

    private fun play(sound: Sound) {
        if (!soundOn) return
        val id = ids[sound] ?: return
        pool.play(id, VOLUME, VOLUME, 1, 0, 1f)
    }

    private fun vibrate(ms: Long) {
        if (!vibrationOn) return
        val v = vibrator ?: return
        if (v.hasVibrator()) v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    fun release() = pool.release()

    private enum class Sound(val resId: Int) {
        MOVE(R.raw.sfx_move), ROTATE(R.raw.sfx_rotate), LOCK(R.raw.sfx_lock), DROP(R.raw.sfx_drop), HOLD(R.raw.sfx_hold),
        CLEAR1(R.raw.sfx_clear1), CLEAR2(R.raw.sfx_clear2), CLEAR3(R.raw.sfx_clear3), QUAD(R.raw.sfx_quad),
        SPIN(R.raw.sfx_spin), COMBO(R.raw.sfx_combo), WIN(R.raw.sfx_win), LOSE(R.raw.sfx_lose),
    }

    private companion object {
        const val MAX_VOICES = 4
        const val VOLUME = 0.8f
    }
}
