package pt.tiago.blocknight.core.model

/** Modos do motor. Campanha, Sprint e Ultra usam objetivo ou tempo; Zen e Maratona não perdem por topo. */
enum class Mode { CAMPAIGN, MARATHON, SPRINT, ULTRA, ZEN }

/** Objetivos de vitória (PLANO §5.2). Os valores são os contratos do conteúdo certificado. */
sealed interface Objective {
    data class Lines(val n: Int) : Objective
    data object Dig : Objective
    data object Targets : Objective
    data class Score(val points: Long) : Objective
    data class Quads(val n: Int) : Objective
    data class TSpins(val n: Int) : Objective
    data class Combo(val n: Int) : Objective
}

/** Lixo que sobe: uma linha com um buraco a cada `everyPieces` bloqueios, depois de `startAfter` peças. */
data class RisingGarbage(val everyPieces: Int, val startAfter: Int)

enum class LossReason { BLOCK_OUT, LOCK_OUT, GARBAGE_OVERFLOW, OUT_OF_PIECES }

sealed interface Outcome {
    data object Won : Outcome
    data class Lost(val reason: LossReason) : Outcome
    /** Fim por tempo (Ultra). Não é derrota nem vitória de objetivo. */
    data object TimeUp : Outcome
}

/** Configuração imutável de uma partida. É tudo o que o motor precisa, além das entradas por tick. */
data class Rules(
    val mode: Mode,
    val speedLevel: Int,
    val previewCount: Int,
    val allowHold: Boolean,
    val seed: Long,
    val garbageSeed: Long,
    val opening: List<PieceType>,
    val initialBoard: Board,
    val objective: Objective?,
    val maxPieces: Int?,
    val risingGarbage: RisingGarbage?,
) {
    init {
        require(speedLevel in 1..15) { "speedLevel $speedLevel fora de 1..15" }
        require(opening.size <= 14) { "openingQueue tem no máximo 14 peças" }
    }

    /** Número de células especiais no início: base do progresso de Dig e Targets. */
    val initialSpecialCount: Int = initialBoard.countCells { Cell.isSpecial(it) }
    val initialTargetCount: Int = initialBoard.countCells { it == Cell.TARGET }
}

/** Peça em jogo. (x, y) é a origem da caixa SRS; ver [Srs]. */
data class ActivePiece(val type: PieceType, val rot: Int, val x: Int, val y: Int)

/** Comandos discretos por tick (PLANO §7.2). A repetição DAS/ARR fica fora do motor. */
enum class Command { SHIFT_LEFT, SHIFT_RIGHT, ROTATE_CW, ROTATE_CCW, SOFT_DROP_START, SOFT_DROP_STOP, HARD_DROP, HOLD, SONIC_DROP }

enum class ClearKind { SINGLE, DOUBLE, TRIPLE, QUAD }

enum class SpinKind { NONE, MINI, FULL }

sealed interface Event {
    data class Spawned(val type: PieceType) : Event
    data class Held(val type: PieceType) : Event
    data class HoldRejected(val reason: String) : Event
    data class Locked(val type: PieceType, val spin: SpinKind) : Event
    data class LinesCleared(val lines: Int, val kind: ClearKind, val points: Long) : Event
    data class Cracked(val rows: Int) : Event
    data class TargetsLit(val count: Int) : Event
    data class SpinScored(val kind: SpinKind, val lines: Int) : Event
    data class ComboReached(val combo: Int) : Event
    data class BackToBack(val points: Long) : Event
    data object PerfectClear : Event
    data class GarbageRaised(val holeX: Int) : Event
    data class ZenCleared(val rows: Int) : Event
    data class Ended(val outcome: Outcome) : Event
}

/**
 * Estado completo da partida. Imutável: cada passo devolve um novo estado.
 * `score` é a pontuação base, antes do multiplicador de dificuldade da campanha.
 */
data class GameState(
    val rules: Rules,
    val board: Board,
    val active: ActivePiece?,
    val are: Int,
    val nextIndex: Int,
    val hold: PieceType?,
    val holdUsed: Boolean,
    val lockTimer: Int,
    val resets: Int,
    val lowestY: Int,
    val gravityAcc: Int,
    val softDrop: Boolean,
    val lastWasRotation: Boolean,
    val lastKick: Int,
    val bufferedRotation: Int,
    val bufferedHold: Boolean,
    val combo: Int,
    val streak: Int,
    val backToBack: Boolean,
    val score: Long,
    val lines: Int,
    val pieces: Int,
    val quads: Int,
    val tspins: Int,
    val perfectClears: Int,
    val targetsLit: Int,
    val maxStreak: Int,
    val garbageRows: Int,
    val zenClears: Int,
    val ticks: Long,
    val outcome: Outcome?,
) {
    val isFinished: Boolean get() = outcome != null

    /** Nível de velocidade efetivo. A Maratona sobe 1 nível a cada 10 linhas até 15. */
    val level: Int
        get() = if (rules.mode == Mode.MARATHON) minOf(15, rules.speedLevel + lines / 10) else rules.speedLevel
}
