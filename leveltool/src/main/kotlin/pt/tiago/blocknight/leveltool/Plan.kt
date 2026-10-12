package pt.tiago.blocknight.leveltool

import pt.tiago.blocknight.core.model.Objective
import pt.tiago.blocknight.core.random.SplitMix64

/** Distribuição alvo de objetivos por bairro (CONTEUDO §3). Cada linha soma 50 andares. */
object ObjectivePlan {
    private val COUNTS: List<Map<String, Int>> = listOf(
        mapOf("lines" to 38, "score" to 12),
        mapOf("lines" to 20, "dig" to 22, "score" to 8),
        mapOf("lines" to 14, "dig" to 14, "targets" to 16, "score" to 6),
        mapOf("lines" to 12, "dig" to 10, "targets" to 10, "score" to 6, "quads" to 12),
        mapOf("lines" to 12, "dig" to 8, "targets" to 12, "score" to 6, "quads" to 12),
        mapOf("lines" to 10, "dig" to 14, "targets" to 12, "score" to 4, "quads" to 10),
        mapOf("lines" to 10, "dig" to 10, "targets" to 10, "score" to 8, "quads" to 12),
        mapOf("lines" to 8, "dig" to 8, "targets" to 8, "score" to 4, "quads" to 8, "tspins" to 14),
        mapOf("lines" to 8, "dig" to 8, "targets" to 8, "score" to 4, "quads" to 8, "tspins" to 6, "combo" to 8),
        mapOf("lines" to 8, "dig" to 8, "targets" to 10, "score" to 4, "quads" to 8, "tspins" to 6, "combo" to 6),
        mapOf("lines" to 6, "dig" to 10, "targets" to 10, "score" to 4, "quads" to 8, "tspins" to 6, "combo" to 6),
        mapOf("lines" to 6, "dig" to 10, "targets" to 10, "score" to 4, "quads" to 8, "tspins" to 6, "combo" to 6),
    )

    /** Tipos de objetivo do andar `ordinal`, em ordem fixa e determinística por semente do bairro. */
    fun kindFor(ordinal: Int): String {
        val district = (ordinal - 1) / 50
        val index = (ordinal - 1) % 50
        return plan(district)[index]
    }

    private val cache = HashMap<Int, List<String>>()

    private fun plan(district: Int): List<String> = cache.getOrPut(district) {
        val counts = COUNTS[district]
        check(counts.values.sum() == 50) { "bairro ${district + 1}: ${counts.values.sum()} ≠ 50" }
        val bag = counts.flatMap { (kind, n) -> List(n) { kind } }.toMutableList()
        // Tutoriais (andares 1–10): só objetivos simples, trocados com os de fora do grupo quando possível.
        val simple = setOf("lines", "score", "dig", "targets")
        val rng = SplitMix64(SplitMix64.mix(0xB10C0 + district.toLong()))
        for (i in bag.size - 1 downTo 1) {
            val j = rng.nextBounded(i + 1)
            val t = bag[i]; bag[i] = bag[j]; bag[j] = t
        }
        val ordered = bag.toMutableList()
        for (i in 0 until 10) {
            if (ordered[i] !in simple) {
                val swap = (10 until 50).firstOrNull { ordered[it] in simple } ?: break
                val t = ordered[i]; ordered[i] = ordered[swap]; ordered[swap] = t
            }
        }
        ordered
    }

    /** Parâmetros dos objetivos, dependentes do bairro e do índice do andar (valores de partida; o piloto ajusta). */
    fun objective(kind: String, ordinal: Int): Pair<String, Objective> {
        val district = (ordinal - 1) / 50 + 1
        val index = (ordinal - 1) % 50
        return when (kind) {
            "lines" -> kind to Objective.Lines(minOf(40, 2 + index / 2 + (district - 1) * 2 + if (index == 49) 6 else 0))
            "score" -> kind to Objective.Score(minOf(60_000L, 1_500L + 500L * index + 1_000L * (district - 1)))
            "dig" -> kind to Objective.Dig
            "targets" -> kind to Objective.Targets
            // Calibração: um Quad por andar. Com 2 ou mais, o Fácil não cabe em 12 linhas sem tucks (ver DECISOES D033).
            "quads" -> kind to Objective.Quads(1)
            // Calibração: um T-Spin por aula (ver DECISOES D044). Com 3, a aula de um só tabuleiro não cabe no limite de altura.
            "tspins" -> kind to Objective.TSpins(1)
            "combo" -> kind to Objective.Combo(2 + index / 25)
            else -> throw IllegalArgumentException(kind)
        }
    }
}
