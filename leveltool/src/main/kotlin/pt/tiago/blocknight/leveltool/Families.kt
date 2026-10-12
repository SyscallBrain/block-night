package pt.tiago.blocknight.leveltool

import pt.tiago.blocknight.core.random.SplitMix64

/**
 * Famílias de tabuleiros iniciais (CONTEUDO §3). Cada família é determinística para uma semente e devolve
 * linhas de cima para baixo, com no máximo 14 linhas e sem linhas cheias.
 */
object Families {
    /** Fila de abertura das aulas de T-Spin: a primeira peça é a T que faz o spin. */
    val TSPIN_OPENING = listOf(pt.tiago.blocknight.core.model.PieceType.T)


    /** Gera as linhas da família. `density` (0..2) escolhe a quantidade de lixo: easy, medium, hard. */
    fun build(family: String, rng: SplitMix64, density: Int): List<String> {
        val rows = when (family) {
            "empty" -> emptyList()
            "garbage-stack" -> garbageStack(rng, density)
            "garbage-cheese" -> garbageCheese(rng, density)
            "targets-scatter" -> targetsScatter(rng, density)
            "targets-deep" -> targetsDeep(rng, density)
            "concrete-bands" -> concreteBands(rng, density)
            "well-setup" -> wellSetup(rng, density)
            "combo-ladder" -> comboLadder(rng, density)
            "tspin-lesson" -> tspinLesson(rng)
            else -> throw IllegalArgumentException("família desconhecida: $family")
        }
        require(rows.size <= 14) { "$family excede 14 linhas" }
        return rows
    }

    private fun rowsOf(count: Int, cells: (Int) -> CharArray): List<String> = List(count) { String(cells(it)) }

    private fun blank(): CharArray = CharArray(10) { '.' }

    /** Linhas de lixo com um buraco; em easy os buracos alinham-se numa coluna, em hard são dispersos. */
    private fun garbageStack(rng: SplitMix64, density: Int): List<String> {
        val count = listOf(2, 4, 6)[density]
        val aligned = density == 0
        val column = rng.nextBounded(10)
        return rowsOf(count) { _ ->
            val row = CharArray(10) { 'G' }
            row[if (aligned) column else rng.nextBounded(10)] = '.'
            row
        }
    }

    private fun garbageCheese(rng: SplitMix64, density: Int): List<String> {
        val count = listOf(2, 4, 5)[density]
        return rowsOf(count) { _ ->
            val row = CharArray(10) { 'G' }
            val holes = 1 + rng.nextBounded(3)
            repeat(holes) { row[rng.nextBounded(10)] = '.' }
            row
        }
    }

    /** Janelas-alvo espalhadas; o buraco é escolhido primeiro e nunca fica numa janela. */
    private fun targetsScatter(rng: SplitMix64, density: Int): List<String> {
        val count = listOf(3, 5, 7)[density]
        return rowsOf(count) { _ ->
            val row = CharArray(10) { 'G' }
            row[rng.nextBounded(10)] = '.'
            val lit = if (density > 0) 2 else 1
            repeat(lit) {
                val x = rng.nextBounded(10)
                if (row[x] == 'G') row[x] = 'X'
            }
            row
        }
    }

    private fun targetsDeep(rng: SplitMix64, density: Int): List<String> {
        val count = listOf(3, 4, 6)[density]
        return rowsOf(count) { index ->
            val row = CharArray(10) { if (index == 0) 'R' else 'G' }
            row[rng.nextBounded(10)] = 'X'
            val hole = rng.nextBounded(10)
            row[hole] = '.'
            row
        }
    }

    private fun concreteBands(rng: SplitMix64, density: Int): List<String> {
        val count = listOf(2, 4, 6)[density]
        return rowsOf(count) { index ->
            val row = CharArray(10) { if (index % 2 == 0) 'R' else 'G' }
            row[rng.nextBounded(10)] = '.'
            row
        }
    }

    /** Lixo com um poço de largura 1 em todas as linhas (preparado para Quads). Nenhuma linha fica cheia. */
    private fun wellSetup(rng: SplitMix64, density: Int): List<String> {
        val count = listOf(4, 6, 7)[density]
        val well = 9 - rng.nextBounded(2)
        return rowsOf(count) {
            val row = CharArray(10) { 'G' }
            row[well] = '.'
            row
        }
    }

    /**
     * Aula de T-Spin (CONTEUDO §3): uma ranhura com um overhang, onde uma T entra por rotação. Procura
     * tabuleiros de 2 a 4 linhas com 1 a 3 buracos por linha numa janela de 4 colunas, em ordem fixada pela
     * semente, e aceita o primeiro em que uma T colocada por rotação faz um T-Spin com linhas (verificado
     * com o próprio motor). Sem candidato em 5000 tentativas: lista vazia.
     */
    private fun tspinLesson(rng: SplitMix64): List<String> {
        repeat(5000) {
            val rowCount = 2 + rng.nextBounded(3)
            val window = rng.nextBounded(7)
            val rows = List(rowCount) {
                val row = CharArray(10) { 'G' }
                val holes = 1 + rng.nextBounded(3)
                repeat(holes) { row[window + rng.nextBounded(4)] = '.' }
                row
            }
            val lines = rows.reversed().map { String(it) }
            if (lines.any { line -> line.all { it != '.' } }) return@repeat
            if (hasTSpin(lines)) return lines
        }
        return emptyList()
    }

    /** Verdadeiro se uma T colocada a partir de [TSPIN_OPENING] faz um T-Spin com linhas. */
    fun hasTSpin(lines: List<String>): Boolean {
        val board = pt.tiago.blocknight.core.content.Compile.board(lines)
        val rules = pt.tiago.blocknight.core.model.Rules(
            mode = pt.tiago.blocknight.core.model.Mode.CAMPAIGN,
            speedLevel = 1,
            previewCount = 3,
            allowHold = false,
            seed = 1L,
            garbageSeed = 2L,
            opening = listOf(pt.tiago.blocknight.core.model.PieceType.T),
            initialBoard = board,
            objective = null,
            maxPieces = null,
            risingGarbage = null,
        )
        val start = pt.tiago.blocknight.core.rules.Engine.newGame(rules)
        return pt.tiago.blocknight.core.search.PlacementFinder.find(start).any { p ->
            val after = pt.tiago.blocknight.core.search.PlacementReplay.apply(start, p)
            after.tspins > 0
        }
    }

    /** Escada de altura decrescente com um poço de largura 2 para combos. */
    private fun comboLadder(rng: SplitMix64, density: Int): List<String> {
        val count = listOf(2, 3, 5)[density]
        val left = rng.nextBounded(4)
        return rowsOf(count) { index ->
            val row = CharArray(10) { 'G' }
            row[left] = '.'
            row[left + 1] = '.'
            if (index == count - 1 && density > 0) row[9] = '.'
            row
        }
    }
}
