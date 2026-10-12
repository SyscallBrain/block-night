package pt.tiago.blocknight.core.rules

/**
 * Gravidade por nível de velocidade (1..15), em linhas × 65536 por tick de 60 Hz.
 * Gerada uma vez pela fórmula de PLANO §4.4: tempo por linha = (0,8 − (L−1)·0,007)^(L−1) segundos.
 * Valores congelados no código: não se recalculam com floats em runtime (GRAV-01).
 */
object GravityTable {
    const val ONE_ROW = 65536

    private val PER_TICK = intArrayOf(
        0,
        1092, 1377, 1768, 2311, 3075, 4169, 5759, 8107,
        11634, 17026, 25416, 38709, 60169, 95483, 154742,
    )

    fun perTick(level: Int): Int {
        require(level in 1..15) { "level $level fora de 1..15" }
        return PER_TICK[level]
    }
}
