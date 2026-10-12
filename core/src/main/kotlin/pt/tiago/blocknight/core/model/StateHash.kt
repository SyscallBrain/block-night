package pt.tiago.blocknight.core.model

/**
 * Impressão digital canónica do estado de jogo (FNV-1a de 64 bits), usada pelos certificados.
 * Ignora ticks e campos de pré-visualização: só conta o que muda as regras.
 */
object StateHash {
    fun of(state: GameState): String {
        var h = FNV_OFFSET
        fun mix(v: Long) {
            h = (h xor v) * FNV_PRIME
        }
        for (cell in state.board.toIntArray()) mix(cell.toLong())
        mix(state.nextIndex.toLong())
        mix((state.hold?.code ?: 0).toLong())
        mix(if (state.holdUsed) 1L else 0L)
        mix(state.combo.toLong())
        mix(if (state.backToBack) 1L else 0L)
        mix(state.score)
        mix(state.lines.toLong())
        mix(state.pieces.toLong())
        mix(outcomeCode(state.outcome))
        return java.lang.Long.toHexString(h).padStart(16, '0')
    }

    private fun outcomeCode(outcome: Outcome?): Long = when (outcome) {
        null -> 0L
        Outcome.Won -> 1L
        Outcome.TimeUp -> 2L
        is Outcome.Lost -> 10L + outcome.reason.ordinal
    }

    private const val FNV_OFFSET = -3750763034362895579L // 0xCBF29CE484222325
    private const val FNV_PRIME = 1099511628211L // 0x100000001B3
}
