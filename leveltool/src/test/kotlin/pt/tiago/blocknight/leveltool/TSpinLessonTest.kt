package pt.tiago.blocknight.leveltool

import org.junit.Assert.assertTrue
import org.junit.Test
import pt.tiago.blocknight.core.random.SplitMix64

class TSpinLessonDiagnosticTest {
    @Test
    fun `a generated T-spin lesson is solved by the beam search`() {
        val rows = Families.build("tspin-lesson", SplitMix64(7L), 0)
        println("LESSON rows=$rows hasTSpin=${Families.hasTSpin(rows)}")
        assertTrue("lesson board must exist", rows.isNotEmpty())
        val rules = pt.tiago.blocknight.core.model.Rules(
            mode = pt.tiago.blocknight.core.model.Mode.CAMPAIGN,
            speedLevel = 1,
            previewCount = 3,
            allowHold = true,
            seed = 1L,
            garbageSeed = 2L,
            opening = Families.TSPIN_OPENING,
            initialBoard = pt.tiago.blocknight.core.content.Compile.board(rows),
            objective = pt.tiago.blocknight.core.model.Objective.TSpins(1),
            maxPieces = null,
            risingGarbage = null,
        )
        val result = BeamSolver.solve(rules, maxPieces = 20, widths = listOf(64), forbidFinalRotation = false, maxHeight = 12)
        println("LESSON solved=${result.solved} pieces=${result.placements.size} expanded=${result.expanded}")
        assertTrue(result.solved)
    }
}
