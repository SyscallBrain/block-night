package pt.tiago.blocknight.core.random

/**
 * SplitMix64 (Steele, Lea e Flood). É o único gerador do motor: não se usa `Random`, relógio nem
 * ordem de coleções. As saídas são exatas, por isso os testes de vetores fixam o comportamento.
 */
class SplitMix64(seed: Long) {
    private var state: Long = seed

    fun nextLong(): Long {
        state += GOLDEN_GAMMA
        var z = state
        z = (z xor (z ushr 30)) * MIX_1
        z = (z xor (z ushr 27)) * MIX_2
        return z xor (z ushr 31)
    }

    /** Inteiro uniforme em [0, bound) por amostragem por rejeição, sem viés de módulo. */
    fun nextBounded(bound: Int): Int {
        require(bound > 0)
        val b = bound.toLong()
        val limit = (Long.MAX_VALUE / b) * b
        while (true) {
            val r = nextLong() ushr 1
            if (r < limit) return (r % b).toInt()
        }
    }

    companion object {
        const val GOLDEN_GAMMA = -7046029254386353131L // 0x9E3779B97F4A7C15
        const val MIX_1 = -4658895280553007687L // 0xBF58476D1CE4E5B9
        const val MIX_2 = -7723592293110705685L // 0x94D049BB133111EB

        /** Mistura um valor de 64 bits (um passo do SplitMix sem estado). */
        fun mix(value: Long): Long = SplitMix64(value).nextLong()
    }
}
