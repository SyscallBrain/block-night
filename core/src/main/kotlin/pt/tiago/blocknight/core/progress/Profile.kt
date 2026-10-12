package pt.tiago.blocknight.core.progress

import kotlinx.serialization.Serializable

/** Progresso de um andar numa dificuldade. Guarda sempre o melhor resultado, nunca o último. */
@Serializable
data class FloorRecord(
    val stars: Int = 0,
    val bestPieces: Int? = null,
    val bestScore: Long = 0,
) {
    val completed: Boolean get() = stars > 0
}

/** Perfil do jogador. Progresso separado por dificuldade (PROG-04) e um registo dos resultados aplicados (PROG-02). */
@Serializable
data class Profile(
    val schemaVersion: Int = SCHEMA,
    val progress: Map<String, Map<String, FloorRecord>> = emptyMap(),
    val appliedResults: List<String> = emptyList(),
    val lastDifficulty: String = "medium",
    val modes: Map<String, ModeRecord> = emptyMap(),
    val totals: Totals = Totals(),
    val daily: List<DailyEntry> = emptyList(),
    val settings: Settings = Settings(),
) {
    fun record(difficulty: String, floorId: String): FloorRecord = progress[difficulty]?.get(floorId) ?: FloorRecord()

    /** Um andar está aberto se for o primeiro da dificuldade ou se o anterior tiver estrelas. */
    fun isUnlocked(difficulty: String, ordinal: Int, floorIdOf: (Int) -> String): Boolean =
        ordinal == 1 || record(difficulty, floorIdOf(ordinal - 1)).completed

    companion object {
        const val SCHEMA = 1
        /** Limite do registo de resultados aplicados, para o ficheiro não crescer sem fim. */
        const val APPLIED_LIMIT = 500
    }
}

/** Resultado de uma sessão de campanha, já com a classificação calculada pelo motor. */
data class FloorResult(
    val sessionId: String,
    val floorId: String,
    val difficulty: String,
    val won: Boolean,
    val pieces: Int,
    val score: Long,
    val threeStarMaxPieces: Int,
    val twoStarMaxPieces: Int,
)

object ProgressReducer {

    /** 3 estrelas até ao limite de 3★, 2 até ao de 2★, 1 nos restantes casos; derrota dá 0 (PLANO §5.4). */
    fun stars(result: FloorResult): Int = when {
        !result.won -> 0
        result.pieces <= result.threeStarMaxPieces -> 3
        result.pieces <= result.twoStarMaxPieces -> 2
        else -> 1
    }

    /**
     * Aplica um resultado uma só vez. O mesmo `sessionId` não conta duas vezes (PROG-02). O melhor resultado
     * guarda o máximo de estrelas, o menor número de peças numa vitória e a melhor pontuação (PROG-01).
     */
    fun apply(profile: Profile, result: FloorResult): Profile {
        if (result.sessionId in profile.appliedResults) return profile
        val current = profile.record(result.difficulty, result.floorId)
        val earned = stars(result)
        val updated = FloorRecord(
            stars = maxOf(current.stars, earned),
            bestPieces = when {
                !result.won -> current.bestPieces
                current.bestPieces == null -> result.pieces
                else -> minOf(current.bestPieces, result.pieces)
            },
            bestScore = maxOf(current.bestScore, if (result.won) result.score else 0L),
        )
        val byFloor = profile.progress[result.difficulty].orEmpty() + (result.floorId to updated)
        val applied = (profile.appliedResults + result.sessionId).takeLast(Profile.APPLIED_LIMIT)
        return profile.copy(
            progress = profile.progress + (result.difficulty to byFloor),
            appliedResults = applied,
        )
    }
}

/** Aplica os resultados livres e diários, também de forma idempotente por `sessionId`. */
object FreeReducer {
    fun apply(profile: Profile, result: FreeResult): Profile {
        if (result.sessionId in profile.appliedResults) return profile
        val key = if (result.mode == "marathon") ModeKeys.marathon(result.startLevel) else result.mode
        val current = profile.modes[key] ?: ModeRecord()
        val updated = current.copy(
            plays = current.plays + 1,
            bestScore = maxOf(current.bestScore, result.score),
            bestLines = maxOf(current.bestLines, result.lines),
            bestLevel = maxOf(current.bestLevel, result.level),
            bestTicks = when {
                result.mode != ModeKeys.SPRINT || !result.completedSprint -> current.bestTicks
                current.bestTicks == null -> result.ticks
                else -> minOf(current.bestTicks, result.ticks)
            },
        )
        val totals = profile.totals.copy(
            pieces = profile.totals.pieces + result.pieces,
            lines = profile.totals.lines + result.lines,
            quads = profile.totals.quads + result.quads,
            tspins = profile.totals.tspins + result.tspins,
            perfectClears = profile.totals.perfectClears + result.perfectClears,
            maxCombo = maxOf(profile.totals.maxCombo, result.maxCombo),
        )
        return profile.copy(
            modes = profile.modes + (key to updated),
            totals = totals,
            appliedResults = (profile.appliedResults + result.sessionId).takeLast(Profile.APPLIED_LIMIT),
        )
    }

    fun applyDaily(profile: Profile, result: DailyResult): Profile {
        if (result.sessionId in profile.appliedResults) return profile
        val entry = DailyEntry(result.sessionId, result.date, result.difficulty, result.won, result.pieces, result.score)
        return profile.copy(
            daily = (profile.daily + entry).takeLast(ModeKeys.DAILY_HISTORY_LIMIT),
            appliedResults = (profile.appliedResults + result.sessionId).takeLast(Profile.APPLIED_LIMIT),
        )
    }
}

/** Definições: som, vibração e controlos (gestos ou botões, DAS/ARR, canhoto). Valores por omissão do PLANO §3.5. */
@Serializable
data class Settings(
    val sound: Boolean = true,
    val vibration: Boolean = true,
    val controls: String = CONTROLS_GESTURES,
    val leftHanded: Boolean = false,
    /** DAS e ARR em ticks de 60 Hz (PLANO §7.6: 10 e 2 por omissão). */
    val das: Int = 10,
    val arr: Int = 2,
) {
    companion object {
        const val CONTROLS_GESTURES = "gestures"
        const val CONTROLS_BUTTONS = "buttons"
    }
}

/** Conquistas locais e simples (PLANO §3.5). São calculadas a partir do perfil: não há economia de recompensas. */
object Achievements {
    const val FIRST_WIN = "first_win"
    const val FIRST_QUAD = "first_quad"
    const val FIRST_TSPIN = "first_tspin"
    const val COMBO_FIVE = "combo_5"
    const val FIRST_PERFECT = "first_perfect"
    const val FIRST_DISTRICT = "first_district"
    const val HUNDRED_FLOORS = "floors_100"

    /** Ordem fixa para a lista na interface. */
    val ALL = listOf(FIRST_WIN, FIRST_QUAD, FIRST_TSPIN, COMBO_FIVE, FIRST_PERFECT, FIRST_DISTRICT, HUNDRED_FLOORS)

    fun of(profile: Profile, floorsPerDistrict: Int, floorIdOf: (Int) -> String): Set<String> {
        val out = HashSet<String>()
        val t = profile.totals
        if (profile.progress.values.any { byFloor -> byFloor.values.any { it.completed } }) out += FIRST_WIN
        if (t.quads > 0) out += FIRST_QUAD
        if (t.tspins > 0) out += FIRST_TSPIN
        if (t.maxCombo >= 5) out += COMBO_FIVE
        if (t.perfectClears > 0) out += FIRST_PERFECT
        val firstDistrict = (1..floorsPerDistrict).map { floorIdOf(it) }
        if (profile.progress.values.any { byFloor -> firstDistrict.all { byFloor[it]?.completed == true } }) out += FIRST_DISTRICT
        if (profile.progress.values.any { byFloor -> byFloor.values.count { it.completed } >= 100 }) out += HUNDRED_FLOORS
        return out
    }
}
