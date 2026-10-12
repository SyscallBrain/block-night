package pt.tiago.blocknight.core.random

import pt.tiago.blocknight.core.model.PieceType
import pt.tiago.blocknight.core.model.Rules

/**
 * Sequência de peças: `openingQueue` e depois sacos de 7 baralhados (Fisher–Yates) com SplitMix64.
 * A peça de índice i depende só da semente e de i (BAG-03), por isso é O(1) e não há estado escondido.
 */
object BagRandomizer {
    private val ORDER = PieceType.entries.toList()

    fun pieceAt(rules: Rules, index: Int): PieceType {
        val opening = rules.opening
        if (index < opening.size) return opening[index]
        val bagIndex = index - opening.size
        return bagPermutation(rules.seed, bagIndex / 7)[bagIndex % 7]
    }

    /** Um saco de 7 peças para o número `bag` (começa em 0) de uma semente. */
    fun bagPermutation(seed: Long, bag: Int): List<PieceType> {
        val rng = SplitMix64(SplitMix64.mix(seed + bag.toLong() * SplitMix64.GOLDEN_GAMMA))
        val pieces = ORDER.toTypedArray()
        for (i in pieces.size - 1 downTo 1) {
            val j = rng.nextBounded(i + 1)
            val tmp = pieces[i]
            pieces[i] = pieces[j]
            pieces[j] = tmp
        }
        return pieces.toList()
    }

    /** Buraco do lixo que sobe, indexado pelo número de linhas de lixo já inseridas (independente das peças). */
    fun garbageHole(garbageSeed: Long, index: Int): Int =
        SplitMix64(SplitMix64.mix(garbageSeed xor (index.toLong() * SplitMix64.GOLDEN_GAMMA))).nextBounded(10)
}
