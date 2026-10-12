package pt.tiago.blocknight.leveltool

import pt.tiago.blocknight.core.model.Board
import pt.tiago.blocknight.core.model.Cell
import pt.tiago.blocknight.core.model.GameState
import pt.tiago.blocknight.core.model.Objective
import pt.tiago.blocknight.core.model.Outcome
import pt.tiago.blocknight.core.model.Rules
import pt.tiago.blocknight.core.model.StateHash
import pt.tiago.blocknight.core.rules.Engine
import pt.tiago.blocknight.core.search.Placement
import pt.tiago.blocknight.core.search.PlacementFinder
import pt.tiago.blocknight.core.search.PlacementReplay

/** Resultado do solver. `solved` só é verdadeiro com uma sequência de colocações que vence no motor. */
data class SolveResult(
    val solved: Boolean,
    val placements: List<Placement>,
    val beamWidth: Int,
    val expanded: Long,
    val ms: Long,
    val finalState: GameState?,
)

/**
 * Beam search sobre colocações do motor (CONTEUDO §5). Só dá vitória quando o motor a confirma com
 * [PlacementReplay]; um time-out ou uma largura insuficiente dá `solved = false`, nunca uma aprovação.
 */
object BeamSolver {
    /** Larguras tentadas por ordem. A primeira que resolver vence. */
    val DEFAULT_WIDTHS = listOf(64, 256)

    /** Nós expandidos por solve. Acima disto a variante é dada como não resolvida (nunca aprovada). */
    const val DEFAULT_BUDGET = 1_200_000L

    /**
     * `forbidFinalRotation`: só colocações sem rotação final (sem tucks), como exige o Fácil.
     */
    fun solve(
        rules: Rules,
        maxPieces: Int = 160,
        widths: List<Int> = DEFAULT_WIDTHS,
        expansionBudget: Long = DEFAULT_BUDGET,
        forbidFinalRotation: Boolean = false,
        maxHeight: Int? = null,
    ): SolveResult {
        val started = System.nanoTime()
        var expanded = 0L
        for (width in widths) {
            val result = search(rules, width, maxPieces, expansionBudget - expanded, forbidFinalRotation, maxHeight)
            expanded += result.expanded
            if (result.solved) return result.copy(expanded = expanded, ms = elapsedMs(started))
            if (expanded >= expansionBudget) break
        }
        return SolveResult(false, emptyList(), widths.last(), expanded, elapsedMs(started), null)
    }

    private class Node(val state: GameState, val parent: Node?, val placement: Placement?, val score: Double)

    private fun search(rules: Rules, width: Int, maxPieces: Int, budget: Long, forbidFinalRotation: Boolean, maxHeight: Int?): SolveResult {
        val started = System.nanoTime()
        var expanded = 0L
        val start = Engine.newGame(rules)
        var beam = listOf(Node(start, null, null, evaluate(start, rules)))
        var depth = 0
        while (depth < maxPieces && beam.isNotEmpty()) {
            val children = ArrayList<Node>()
            val seen = HashSet<String>()
            for (node in beam) {
                for (placement in PlacementFinder.find(node.state)) {
                    if (forbidFinalRotation && placement.endsWithRotation) continue
                    val next = PlacementReplay.apply(node.state, placement)
                    expanded++
                    if (next.outcome == Outcome.Won) {
                        val path = pathOf(Node(next, node, placement, 0.0))
                        return SolveResult(true, path, width, expanded, elapsedMs(started), next)
                    }
                    if (next.outcome != null) continue
                    // Altura máxima do certificado: o estado que a ultrapassa nunca entra no feixe (CONTEUDO §5).
                    if (maxHeight != null && next.board.highestOccupiedRow() + 1 > maxHeight) continue
                    if (!seen.add(StateHash.of(next))) continue
                    children += Node(next, node, placement, evaluate(next, rules))
                }
                if (expanded > budget) return SolveResult(false, emptyList(), width, expanded, elapsedMs(started), null)
            }
            beam = children.sortedByDescending { it.score }.take(width)
            depth++
        }
        return SolveResult(false, emptyList(), width, expanded, elapsedMs(started), null)
    }

    private fun elapsedMs(started: Long): Long = (System.nanoTime() - started) / 1_000_000L

    private fun pathOf(leaf: Node): List<Placement> {
        val out = ArrayList<Placement>()
        var cursor: Node? = leaf
        while (cursor != null) {
            cursor.placement?.let { out += it }
            cursor = cursor.parent
        }
        return out.reversed()
    }

    /**
     * Heurística de avaliação. A progressão do objetivo domina; depois vêm buracos, altura, rugosidade e
     * risco de topo. Os pesos estão aqui e são versionados com o solver (CONTEUDO §5).
     */
    fun evaluate(state: GameState, rules: Rules): Double {
        val board = state.board
        val heights = IntArray(Board.WIDTH)
        var holes = 0
        for (x in 0 until Board.WIDTH) {
            var top = -1
            for (y in Board.HEIGHT - 1 downTo 0) {
                if (board.get(x, y) != Cell.EMPTY) {
                    top = y
                    break
                }
            }
            heights[x] = top + 1
            var covered = false
            for (y in top downTo 0) {
                val filled = board.get(x, y) != Cell.EMPTY
                if (filled) covered = true else if (covered) holes++
            }
        }
        val aggregate = heights.sum()
        val maxHeight = heights.max()
        var bumpiness = 0
        for (x in 0 until Board.WIDTH - 1) bumpiness += kotlin.math.abs(heights[x] - heights[x + 1])
        val specials = board.countCells { Cell.isSpecial(it) }.toDouble()

        val progress = when (val objective = rules.objective) {
            null -> 0.0
            is Objective.Lines -> 1000.0 * minOf(state.lines, objective.n) + 2.0 * state.lines
            is Objective.Score -> 10000.0 * minOf(state.score, objective.points) / objective.points.toDouble()
            is Objective.Quads -> 2000.0 * minOf(state.quads, objective.n)
            is Objective.TSpins -> 3000.0 * minOf(state.tspins, objective.n)
            is Objective.Combo -> 1000.0 * minOf(state.maxStreak, objective.n) + 200.0 * state.streak
            Objective.Targets -> {
                val total = maxOf(1, rules.initialTargetCount)
                1500.0 * (total - board.countCells { it == Cell.TARGET }) / total
            }
            Objective.Dig -> {
                val total = maxOf(1, rules.initialSpecialCount)
                1500.0 * (total - specials) / total - 4.0 * specials
            }
        }
        val danger = maxOf(0, maxHeight - 13).toDouble()
        // Quads: um poço fundo e aberto vale mais do que o nível baixo que o preenche (que a altura premeia).
        val wellBonus = if (rules.objective is Objective.Quads && state.quads < (rules.objective as Objective.Quads).n) {
            var bonus = 0.0
            for (x in 0 until Board.WIDTH) {
                val left = if (x > 0) heights[x - 1] else Int.MAX_VALUE / 2
                val right = if (x < Board.WIDTH - 1) heights[x + 1] else Int.MAX_VALUE / 2
                val depth = minOf(left, right) - heights[x]
                if (depth >= 3) bonus += 25.0 * minOf(depth, 8)
            }
            bonus
        } else 0.0
        return progress + wellBonus -
            0.35 * aggregate -
            6.0 * holes -
            0.5 * bumpiness -
            1.5 * maxHeight -
            40.0 * danger * danger
    }
}
