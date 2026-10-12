package pt.tiago.blocknight.leveltool

import pt.tiago.blocknight.core.content.CertificateFile
import pt.tiago.blocknight.core.content.Compile
import pt.tiago.blocknight.core.content.ENGINE_VERSION
import pt.tiago.blocknight.core.content.FloorFile
import pt.tiago.blocknight.core.content.VariantSpec
import pt.tiago.blocknight.core.model.GameState
import pt.tiago.blocknight.core.model.Rules
import pt.tiago.blocknight.core.random.BagRandomizer
import pt.tiago.blocknight.core.search.Placement
import kotlin.math.ceil
import kotlin.math.max

/** Limites de exequibilidade humana (CONTEUDO §5). Pontos de partida; o piloto fixa os finais. */
object Feasibility {
    data class Limits(val maxStepsPerPlacement: Int, val maxSpinShare: Double, val minSecondsPerStep: Double, val maxHeight: Int)

    val LIMITS: Map<String, Limits> = mapOf(
        "easy" to Limits(8, 0.0, 0.25, 12),
        "medium" to Limits(10, 0.10, 0.18, 14),
        "hard" to Limits(12, 0.25, 0.12, 15),
    )

    /** Tempo de queda por linha em segundos, para o nível L (mesma fórmula da gravidade). */
    fun secondsPerRow(level: Int): Double = Math.pow(0.8 - (level - 1) * 0.007, (level - 1).toDouble())

    /** Métricas de um certificado: passos, partes com rotação no fim, tempo de queda por passo e altura. */
    data class Measured(
        val pieces: Int,
        val maxSteps: Int,
        val spinShare: Double,
        val meanSecondsPerStep: Double,
        val maxHeight: Int,
    )

    fun violations(difficulty: String, m: Measured): List<String> {
        val limit = LIMITS.getValue(difficulty)
        val out = ArrayList<String>()
        if (m.maxSteps > limit.maxStepsPerPlacement) out += "passos ${m.maxSteps} > ${limit.maxStepsPerPlacement}"
        if (m.spinShare > limit.maxSpinShare + 1e-9) out += "rotações finais %.3f > %.3f".format(m.spinShare, limit.maxSpinShare)
        if (m.meanSecondsPerStep < limit.minSecondsPerStep) out += "tempo por passo %.3f < %.3f".format(m.meanSecondsPerStep, limit.minSecondsPerStep)
        if (m.maxHeight > limit.maxHeight) out += "altura ${m.maxHeight} > ${limit.maxHeight}"
        return out
    }
}

/** Orçamentos a partir de S (PLANO §5.4). A folga é maxPieces − S. */
object Budget {
    private val FRACTIONS = mapOf("easy" to Pair(0.35, 0.7), "medium" to Pair(0.25, 0.6), "hard" to Pair(0.2, 0.5))

    data class Values(val maxPieces: Int, val twoStar: Int, val threeStar: Int)

    fun of(difficulty: String, s: Int): Values {
        val slack = when (difficulty) {
            "easy" -> max(10, ceil(0.6 * s).toInt())
            "medium" -> max(6, ceil(0.35 * s).toInt())
            "hard" -> max(3, ceil(0.15 * s).toInt())
            else -> error(difficulty)
        }
        val maxPieces = s + slack
        val (f3, f2) = FRACTIONS.getValue(difficulty)
        return Values(maxPieces, s + ceil(slack * f2).toInt(), s + ceil(slack * f3).toInt())
    }
}

/** Medidas feitas a partir da repetição do certificado com o motor. */
object Measure {
    fun of(rules: Rules, placements: List<Placement>, difficulty: String): Feasibility.Measured {
        var state: GameState = pt.tiago.blocknight.core.rules.Engine.newGame(rules)
        var maxHeight = 0
        var spins = 0
        var secondsSum = 0.0
        var maxSteps = 0
        val level = rules.speedLevel
        val rowSeconds = Feasibility.secondsPerRow(level)
        for (placement in placements) {
            val spawnY = pt.tiago.blocknight.core.model.Srs.spawnY(placement.piece.type)
            val dropRows = maxOf(0, spawnY - placement.piece.y)
            val actions = maxOf(1, placement.actions)
            secondsSum += dropRows * rowSeconds / actions
            if (placement.endsWithRotation) spins++
            maxSteps = max(maxSteps, placement.actions)
            state = pt.tiago.blocknight.core.search.PlacementReplay.apply(state, placement)
            maxHeight = max(maxHeight, state.board.highestOccupiedRow() + 1)
        }
        val n = placements.size.coerceAtLeast(1)
        return Feasibility.Measured(
            pieces = placements.size,
            maxSteps = maxSteps,
            spinShare = spins.toDouble() / n,
            meanSecondsPerStep = secondsSum / n,
            maxHeight = maxHeight,
        )
    }
}

/** Assinatura canónica para deteção de duplicados (CONTEUDO §6): tabuleiro, objetivo, modificadores e 14 peças. */
object Signature {
    fun of(floor: FloorFile, variant: VariantSpec): String = fingerprint(variant, mirror = false)

    fun mirrored(variant: VariantSpec): String = fingerprint(variant, mirror = true)

    private fun fingerprint(v: VariantSpec, mirror: Boolean): String {
        val rows = v.initialBoard.map { if (mirror) mirrorRow(it) else it }.joinToString("/")
        val opening = v.openingQueue.map { if (mirror) mirrorPiece(it) else it.toString() }.joinToString("")
        val rules = Compile.rules(FloorFile("floor-0000", 1, 1, "x", revision = 1, variants = emptyMap()), "medium", v)
        val pieces = (0 until 14).map { BagRandomizer.pieceAt(rules, it).name }
        val mirroredPieces = if (mirror) pieces.map { mirrorPiece(it) }.joinToString("") else pieces.joinToString("")
        val objective = "${v.objective.type}:${v.objective.n ?: v.objective.points}"
        val modifiers = "s${v.speedLevel}|h${v.allowHold}|r${v.risingGarbage?.everyPieces}-${v.risingGarbage?.startAfter}"
        return listOf(rows, opening, objective, modifiers, mirroredPieces).joinToString("#")
    }

    private fun mirrorRow(row: String): String = row.reversed()

    private fun mirrorPiece(p: String): String = when (p) {
        "S" -> "Z"; "Z" -> "S"; "J" -> "L"; "L" -> "J"; else -> p
    }
}

/** Ligação de um certificado a um andar: cria o certificado a partir da lista de colocações. */
object CertificateBuilder {
    /** Atualiza só o hash de nível (ex.: depois de um reequilíbrio de orçamentos). Os passos não mudam. */
    fun rebuildHash(cert: CertificateFile, floor: FloorFile, difficulty: String, variant: VariantSpec): CertificateFile =
        cert.copy(levelHash = pt.tiago.blocknight.core.content.Canonical.levelHash(floor, difficulty, variant))

    fun build(floor: FloorFile, difficulty: String, variant: VariantSpec, placements: List<Placement>, solver: pt.tiago.blocknight.core.content.SolverSpec?): CertificateFile {
        val rules = Compile.rules(floor, difficulty, variant)
        var state = pt.tiago.blocknight.core.rules.Engine.newGame(rules)
        val steps = ArrayList<pt.tiago.blocknight.core.content.StepSpec>()
        for (placement in placements) {
            state = pt.tiago.blocknight.core.search.PlacementReplay.apply(state, placement)
            steps += pt.tiago.blocknight.core.content.StepSpec(
                hold = placement.hold,
                piece = placement.piece.type.name,
                path = placement.path,
                final = pt.tiago.blocknight.core.content.PositionSpec(placement.piece.x, placement.piece.y, placement.piece.rot),
                stateHash = pt.tiago.blocknight.core.model.StateHash.of(state),
            )
        }
        return CertificateFile(
            floorId = floor.id,
            difficulty = difficulty,
            levelRevision = floor.revision,
            engineVersion = ENGINE_VERSION,
            levelHash = pt.tiago.blocknight.core.content.Canonical.levelHash(floor, difficulty, variant),
            steps = steps,
            result = pt.tiago.blocknight.core.content.ResultSpec(
                pieces = steps.size,
                won = state.outcome == pt.tiago.blocknight.core.model.Outcome.Won,
                linesCleared = state.lines,
                score = state.score,
                finalStateHash = pt.tiago.blocknight.core.model.StateHash.of(state),
            ),
            solver = solver,
        )
    }
}
