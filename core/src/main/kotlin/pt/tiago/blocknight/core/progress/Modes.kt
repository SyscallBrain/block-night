package pt.tiago.blocknight.core.progress

import kotlinx.serialization.Serializable

/** Recordes de um modo livre. A Maratona guarda um recorde por nível inicial (PLANO §6.3). */
@Serializable
data class ModeRecord(
    val plays: Int = 0,
    val bestScore: Long = 0,
    val bestLines: Int = 0,
    val bestLevel: Int = 0,
    /** Sprint: menor número de ticks para 40 linhas (tempo exato, 60 Hz). Null enquanto não houver resultado. */
    val bestTicks: Long? = null,
)

/** Totais de jogo para as estatísticas (PLANO §3.5). */
@Serializable
data class Totals(
    val pieces: Long = 0,
    val lines: Long = 0,
    val quads: Long = 0,
    val tspins: Long = 0,
    val perfectClears: Long = 0,
    val maxCombo: Int = 0,
    val floorsCleared: Int = 0,
)

/** Uma entrada do histórico do Desafio diário (últimos 270 resultados). */
@Serializable
data class DailyEntry(
    val sessionId: String,
    val date: String,
    val difficulty: String,
    val won: Boolean,
    val pieces: Int,
    val score: Long,
)

/** Resultado de um modo livre, já medido pelo motor. Sem recordes de Zen (só estatísticas). */
data class FreeResult(
    val sessionId: String,
    val mode: String,
    val startLevel: Int,
    val score: Long,
    val lines: Int,
    val level: Int,
    val ticks: Long,
    val pieces: Int,
    val quads: Int,
    val tspins: Int,
    val perfectClears: Int,
    val maxCombo: Int,
    val completedSprint: Boolean = false,
)

/** Resultado de um Desafio diário. */
data class DailyResult(
    val sessionId: String,
    val date: String,
    val floorId: String,
    val difficulty: String,
    val won: Boolean,
    val pieces: Int,
    val score: Long,
)

object ModeKeys {
    fun marathon(startLevel: Int): String = "marathon-$startLevel"
    const val SPRINT = "sprint"
    const val ULTRA = "ultra"
    const val ZEN = "zen"
    const val DAILY_HISTORY_LIMIT = 270
}
