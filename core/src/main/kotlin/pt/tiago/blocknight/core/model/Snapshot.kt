package pt.tiago.blocknight.core.model

import kotlinx.serialization.Serializable

/**
 * Instantâneo do estado lógico de uma partida em curso (PLANO §7.5). As regras não entram: reconstroem-se
 * do andar e da dificuldade, por isso um instantâneo só é aceite com o mesmo `rulesHash`.
 */
@Serializable
data class GameSnapshot(
    val rulesHash: String,
    val cells: List<Int>,
    val active: ActiveSnapshot?,
    val are: Int,
    val nextIndex: Int,
    val hold: Int?,
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
    val perfectClears: Int = 0,
    val targetsLit: Int,
    val maxStreak: Int,
    val garbageRows: Int,
    val zenClears: Int,
    val ticks: Long,
) {
    /** Reconstrói o estado com as regras dadas. Falha se o instantâneo for de outro nível ou revisão. */
    fun restore(rules: pt.tiago.blocknight.core.model.Rules): GameState {
        require(rulesHash == GameSnapshot.rulesHashOf(rules)) { "instantâneo de outro nível" }
        require(cells.size == Board.WIDTH * Board.HEIGHT) { "tabuleiro com ${cells.size} células" }
        return GameState(
            rules = rules,
            board = Board.of(cells.toIntArray()),
            active = active?.let { ActivePiece(PieceType.entries[it.type], it.rot, it.x, it.y) },
            are = are,
            nextIndex = nextIndex,
            hold = hold?.let { PieceType.entries[it] },
            holdUsed = holdUsed,
            lockTimer = lockTimer,
            resets = resets,
            lowestY = lowestY,
            gravityAcc = gravityAcc,
            softDrop = softDrop,
            lastWasRotation = lastWasRotation,
            lastKick = lastKick,
            bufferedRotation = bufferedRotation,
            bufferedHold = bufferedHold,
            combo = combo,
            streak = streak,
            backToBack = backToBack,
            score = score,
            lines = lines,
            pieces = pieces,
            quads = quads,
            tspins = tspins,
            perfectClears = perfectClears,
            targetsLit = targetsLit,
            maxStreak = maxStreak,
            garbageRows = garbageRows,
            zenClears = zenClears,
            ticks = ticks,
            outcome = null,
        )
    }

    companion object {
        fun of(state: GameState): GameSnapshot {
            require(state.outcome == null) { "só se guardam partidas em curso" }
            return GameSnapshot(
                rulesHash = rulesHashOf(state.rules),
                cells = state.board.toIntArray().toList(),
                active = state.active?.let { ActiveSnapshot(it.type.ordinal, it.rot, it.x, it.y) },
                are = state.are,
                nextIndex = state.nextIndex,
                hold = state.hold?.ordinal,
                holdUsed = state.holdUsed,
                lockTimer = state.lockTimer,
                resets = state.resets,
                lowestY = state.lowestY,
                gravityAcc = state.gravityAcc,
                softDrop = state.softDrop,
                lastWasRotation = state.lastWasRotation,
                lastKick = state.lastKick,
                bufferedRotation = state.bufferedRotation,
                bufferedHold = state.bufferedHold,
                combo = state.combo,
                streak = state.streak,
                backToBack = state.backToBack,
                score = state.score,
                lines = state.lines,
                pieces = state.pieces,
                quads = state.quads,
                tspins = state.tspins,
                perfectClears = state.perfectClears,
                targetsLit = state.targetsLit,
                maxStreak = state.maxStreak,
                garbageRows = state.garbageRows,
                zenClears = state.zenClears,
                ticks = state.ticks,
            )
        }

        /** Impressão digital das regras: qualquer alteração ao nível invalida o instantâneo. */
        fun rulesHashOf(rules: Rules): String {
            val text = listOf(
                rules.mode.name, rules.speedLevel, rules.previewCount, rules.allowHold, rules.seed, rules.garbageSeed,
                rules.opening.joinToString("") { it.name }, rules.initialBoard.toIntArray().joinToString(","),
                rules.objective.toString(), rules.maxPieces, rules.risingGarbage.toString(),
            ).joinToString("|")
            var h = -3750763034362895579L
            for (c in text) h = (h xor c.code.toLong()) * 1099511628211L
            return java.lang.Long.toHexString(h).padStart(16, '0')
        }
    }
}

@Serializable
data class ActiveSnapshot(val type: Int, val rot: Int, val x: Int, val y: Int)
