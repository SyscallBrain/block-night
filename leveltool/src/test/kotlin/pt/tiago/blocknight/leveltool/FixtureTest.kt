package pt.tiago.blocknight.leveltool

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.tiago.blocknight.core.content.Compile
import pt.tiago.blocknight.core.model.Mode
import pt.tiago.blocknight.core.model.Objective
import pt.tiago.blocknight.core.model.Outcome
import pt.tiago.blocknight.core.model.PieceType
import pt.tiago.blocknight.core.model.Rules
import pt.tiago.blocknight.core.model.Board
import pt.tiago.blocknight.core.model.Cell
import pt.tiago.blocknight.core.rules.Engine
import pt.tiago.blocknight.core.search.PlacementReplay

/**
 * Fixtures de M1 (DOCS CONTEUDO §8): tabuleiros pequenos com a solução calculada à mão. O número de peças
 * esperado é o S da fixture; o solver tem de o encontrar e o replay tem de vencer.
 */
class FixtureTest {
    private fun rules(
        opening: List<PieceType>,
        rows: List<String>,
        objective: Objective,
        allowHold: Boolean = true,
    ): Rules {
        val cells = IntArray(Board.WIDTH * Board.HEIGHT)
        rows.forEachIndexed { index, text ->
            val y = rows.size - 1 - index
            text.forEachIndexed { x, ch -> cells[y * Board.WIDTH + x] = Compile.cellValue(ch) }
        }
        return Rules(
            mode = Mode.CAMPAIGN,
            speedLevel = 1,
            previewCount = 3,
            allowHold = allowHold,
            seed = 1L,
            garbageSeed = 2L,
            opening = opening,
            initialBoard = Board.of(cells),
            objective = objective,
            maxPieces = null,
            risingGarbage = null,
        )
    }

    private fun solveWins(rules: Rules, expectedPieces: Int) {
        val result = BeamSolver.solve(rules, maxPieces = 40, widths = listOf(64))
        assertTrue("fixture não resolvida", result.solved)
        assertEquals("S da fixture", expectedPieces, result.placements.size)
        val replay = result.placements.fold(Engine.newGame(rules)) { s, p -> PlacementReplay.apply(s, p) }
        assertEquals(Outcome.Won, replay.outcome)
    }

    // 1. Uma I vertical na coluna 9 limpa a única linha de lixo. S = 1.
    @Test fun fixture01_singleLineWithVerticalI() = solveWins(rules(listOf(PieceType.I), listOf("GGGGGGGGG."), Objective.Lines(1)), 1)

    // 2. A mesma I limpa duas linhas de uma vez. S = 1 para lines(2).
    @Test fun fixture02_twoLinesInOnePiece() = solveWins(rules(listOf(PieceType.I), listOf("GGGGGGGGG.", "GGGGGGGGG."), Objective.Lines(2)), 1)

    // 3. Quad de uma I vertical em quatro linhas de lixo. S = 1 para quads(1).
    @Test fun fixture03_quadWithVerticalI() = solveWins(rules(listOf(PieceType.I), List(4) { "GGGGGGGGG." }, Objective.Quads(1)), 1)

    // 4. Uma janela-alvo na linha de baixo sai com a mesma I. S = 1 para targets.
    @Test fun fixture04_targetLitByOnePiece() = solveWins(rules(listOf(PieceType.I), listOf("XGGGGGGGG."), Objective.Targets), 1)

    // 5. Dig com lixo na linha de baixo: uma peça basta. S = 1.
    @Test fun fixture05_digOneRow() = solveWins(rules(listOf(PieceType.I), listOf("GGGGGGGGG."), Objective.Dig), 1)

    // 6. O quadrado O enche as duas últimas colunas de uma linha com lixo. S = 1.
    @Test fun fixture06_oFillsTheWell() = solveWins(rules(listOf(PieceType.O), listOf("GGGGGGGG.."), Objective.Lines(1)), 1)

    // 7. Betão: a primeira limpeza racha a linha (conta como racha, não como linha). A I da linha de cima
    //    limpa a linha de baixo e a rachada fica com lixo. Com lines(1) basta uma peça.
    @Test fun fixture07_concreteCracksOnceAndLinesStillCount() = solveWins(
        rules(listOf(PieceType.I), listOf("GGGGGGGGG.", "RRRRRRRRR."), Objective.Lines(1)), 1,
    )

    // 8. Sem Guardar: a peça T não serve e a S chega a seguir. Com lines(1) e uma linha de S/T, S = 2.
    @Test fun fixture08_twoPiecesWithoutHold() {
        val r = rules(listOf(PieceType.S, PieceType.I), listOf("GGGGGGG..."), Objective.Lines(1), allowHold = false)
        val result = BeamSolver.solve(r, maxPieces = 40, widths = listOf(64))
        assertTrue(result.solved)
        assertTrue(result.placements.none { it.hold })
    }

    // 9. Replay de uma fixture com uma colocação já descoberta: o estado final é reproduzível.
    @Test fun fixture09_replayIsDeterministic() {
        val r = rules(listOf(PieceType.I), listOf("GGGGGGGGG."), Objective.Lines(1))
        val placements = BeamSolver.solve(r, maxPieces = 10, widths = listOf(16)).placements
        val a = placements.fold(Engine.newGame(r)) { s, p -> PlacementReplay.apply(s, p) }
        val b = placements.fold(Engine.newGame(r)) { s, p -> PlacementReplay.apply(s, p) }
        assertEquals(a.score, b.score)
        assertEquals(a.board, b.board)
    }

    // 10. Limpeza total de um quad isolado dá 800 + 2000 (PLANO §4.8).
    @Test fun fixture10_perfectQuadScores2800() {
        val r = rules(listOf(PieceType.I), List(4) { "GGGGGGGGG." }, Objective.Quads(1))
        val s = BeamSolver.solve(r, maxPieces = 4, widths = listOf(16)).placements
            .fold(Engine.newGame(r)) { st, p -> PlacementReplay.apply(st, p) }
        assertEquals(800L + 2000L, s.score)
        assertTrue(s.board.countCells { it == Cell.GARBAGE } == 0)
    }
}
