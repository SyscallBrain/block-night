package pt.tiago.blocknight.leveltool

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import pt.tiago.blocknight.core.content.CertificateFile
import pt.tiago.blocknight.core.content.CertificateVerifier
import pt.tiago.blocknight.core.content.Compile
import pt.tiago.blocknight.core.content.DIFFICULTIES
import pt.tiago.blocknight.core.content.Districts
import pt.tiago.blocknight.core.content.FloorFile
import pt.tiago.blocknight.core.content.ObjectiveSpec
import pt.tiago.blocknight.core.content.RisingSpec
import pt.tiago.blocknight.core.content.SolverSpec
import pt.tiago.blocknight.core.content.Validation
import pt.tiago.blocknight.core.content.VariantSpec
import pt.tiago.blocknight.core.model.Objective
import pt.tiago.blocknight.core.random.SplitMix64

/** Resultado de construir um andar: ficheiro, certificados e motivos de falha por dificuldade. */
data class FloorBuild(
    val floor: FloorFile?,
    val certificates: Map<String, CertificateFile>,
    val failures: List<String>,
    val metrics: Map<String, Map<String, JsonElement>>,
)

/**
 * Gera, resolve, certifica e valida um andar com as três dificuldades (CONTEUDO §§2–5). Nada é aprovado
 * sem replay: o solver é só uma fonte de candidatos, e a aprovação é o certificado verificado.
 */
class FloorGenerator(
    private val attemptsPerDifficulty: Int = 6,
    private val widths: List<Int> = BeamSolver.DEFAULT_WIDTHS,
    private val budget: Long = BeamSolver.DEFAULT_BUDGET,
    /** Diagnóstico: quando falso, o solver não proíbe rotações finais (a feasibilidade continua a ser verificada). */
    private val forbidTucks: Boolean = true,
    /** Só para os andares sem Difícil válido: o Difícil usa o mínimo da faixa do bairro (PLANO §5.5). */
    private val hardSpeedMin: Boolean = false,
) {
    /** A partir desta tentativa, o tabuleiro fica mais leve e a família muda para uma mais aberta. */
    private val relaxFromAttempt = 3
    private val openFromAttempt = 4


    fun build(ordinal: Int): FloorBuild {
        val district = Districts.districtOf(ordinal)
        val id = Districts.floorId(ordinal)
        val index = (ordinal - 1) % Districts.FLOORS_PER_DISTRICT
        val kind = ObjectivePlan.kindFor(ordinal)
        // Aula: andar de T-Spin, com tabuleiro preparado e fila de abertura (CONTEUDO §3).
        val lesson = kind == "tspins"
        val (_, objective) = ObjectivePlan.objective(kind, ordinal)
        val variants = LinkedHashMap<String, VariantSpec>()
        val certificates = LinkedHashMap<String, CertificateFile>()
        val metrics = LinkedHashMap<String, Map<String, JsonElement>>()
        val failures = ArrayList<String>()

        for ((diffIndex, difficulty) in DIFFICULTIES.withIndex()) {
            var done = false
            for (attempt in 0 until attemptsPerDifficulty) {
                val draft = draftVariant(ordinal, district.id, index, kind, objective, difficulty, diffIndex, attempt)
                val floorDraft = FloorFile(id, ordinal, district.id, family(kind, district.id, difficulty), revision = 1, variants = mapOf(difficulty to draft))
                val rules = Compile.rules(floorDraft, difficulty, draft).copy(maxPieces = null)
                val solved = BeamSolver.solve(
                    rules,
                    maxPieces = 160,
                    widths = widths,
                    expansionBudget = budget,
                    // Aulas de T-Spin precisam de rotação final por definição; ficam isentas do limite de tucks.
                    forbidFinalRotation = forbidTucks && difficulty != "hard" && !lesson,
                    maxHeight = Feasibility.LIMITS.getValue(difficulty).maxHeight,
                )
                if (!solved.solved) {
                    failures += "$difficulty tentativa $attempt: não resolvido (largura ${solved.beamWidth}, ${solved.expanded} nós)"
                    continue
                }
                val s = solved.placements.size
                val budget = Budget.of(difficulty, s)
                val withBudget = draft.copy(maxPieces = budget.maxPieces, twoStarMaxPieces = budget.twoStar, threeStarMaxPieces = budget.threeStar)
                val floor = FloorFile(id, ordinal, district.id, floorDraft.familyId, revision = 1, variants = mapOf(difficulty to withBudget))
                val solver = SolverSpec(beamWidth = solved.beamWidth, expanded = solved.expanded, ms = solved.ms)
                val certificate = CertificateBuilder.build(floor, difficulty, withBudget, solved.placements, solver)
                val measured = Measure.of(rules, solved.placements, difficulty)
                // Nas aulas de T-Spin o limite de rotações finais não se aplica (CONTEUDO §5, "exceto aulas").
                val violations = Feasibility.violations(difficulty, measured).filter { !lesson || !it.startsWith("rotações") }
                val verdict = CertificateVerifier.verify(floor, difficulty, withBudget, certificate)
                if (violations.isNotEmpty() || verdict.isNotEmpty()) {
                    failures += "$difficulty tentativa $attempt: " + (violations + verdict).joinToString("; ")
                    continue
                }
                val expected = mapOf(
                    "pieces" to JsonPrimitive(measured.pieces),
                    "maxSteps" to JsonPrimitive(measured.maxSteps),
                    "spinShare" to JsonPrimitive(round(measured.spinShare)),
                    "meanSecondsPerStep" to JsonPrimitive(round(measured.meanSecondsPerStep)),
                    "maxHeight" to JsonPrimitive(measured.maxHeight),
                    "beamWidth" to JsonPrimitive(solved.beamWidth),
                    "budgetSlack" to JsonPrimitive(budget.maxPieces - s),
                )
                variants[difficulty] = withBudget.copy(expectedMetrics = expected)
                certificates[difficulty] = certificate
                metrics[difficulty] = expected
                done = true
                break
            }
            if (!done) {
                return FloorBuild(null, emptyMap(), failures + "$difficulty: sem certificado após $attemptsPerDifficulty tentativas", emptyMap())
            }
        }
        val floor = FloorFile(id, ordinal, district.id, family(kind, district.id, "medium"), revision = 1, variants = variants)
        // Os hashes de certificado já foram calculados com estes orçamentos; a validação final confirma a estrutura.
        val errors = Validation.floor(floor)
        if (errors.isNotEmpty()) return FloorBuild(null, emptyMap(), failures + errors, metrics)
        return FloorBuild(floor, certificates, failures, metrics)
    }

    private fun draftVariant(
        ordinal: Int,
        district: Int,
        index: Int,
        kind: String,
        objective: Objective,
        difficulty: String,
        diffIndex: Int,
        attempt: Int,
    ): VariantSpec {
        val base = ordinal.toLong() * 1_000_003L + diffIndex * 7919L + attempt * 104_729L
        val seed = SplitMix64.mix(base + 11)
        val garbageSeed = SplitMix64.mix(base + 23)
        val boardRng = SplitMix64(SplitMix64.mix(base + 37))
        val baseDensity = when (difficulty) { "easy" -> 0; "medium" -> 1; else -> 2 }
        val density = if (attempt >= relaxFromAttempt) maxOf(0, baseDensity - 1) else baseDensity
        val family = if (attempt >= openFromAttempt && kind in setOf("lines", "score", "dig", "targets", "quads")) {
            when (kind) {
                "targets" -> "targets-deep"
                "quads" -> "empty"
                else -> "garbage-cheese"
            }
        } else {
            family(kind, district, difficulty)
        }
        val rows = if (family == "empty") emptyList() else Families.build(family, boardRng, if (district == 1) 0 else density)
        val lesson = family == "tspin-lesson"
        val range = Districts.ALL[district - 1].speeds.getValue(difficulty)
        val t = index / 49.0
        val speed = if (hardSpeedMin && difficulty == "hard") range.first else range.first + Math.round((range.last - range.first) * t).toInt()
        val rising = if (district >= 5 && kind in setOf("lines", "score", "quads")) {
            RisingSpec(everyPieces = maxOf(4, 9 - (district - 5)), startAfter = 6)
        } else null
        val allowHold = !(district == 7 && index % 2 == 0)
        return VariantSpec(
            seedHex = hex(seed),
            garbageSeedHex = hex(garbageSeed),
            speedLevel = speed,
            previewCount = Compile.PREVIEW_BY_DIFFICULTY.getValue(difficulty),
            allowHold = allowHold,
            openingQueue = if (lesson) Families.TSPIN_OPENING.map { it.name } else emptyList(),
            risingGarbage = rising,
            initialBoard = rows,
            objective = objectiveSpec(objective),
            maxPieces = 200,
            twoStarMaxPieces = 200,
            threeStarMaxPieces = 200,
        ).also { check(ordinal >= 1) }
    }

    /** Família de tabuleiro por tipo de objetivo e bairro. */
    private fun family(kind: String, district: Int, difficulty: String): String {
        if (district == 1 && difficulty == "easy") return "empty"
        return when (kind) {
            "lines" -> if (district == 1) "garbage-cheese" else "garbage-stack"
            "score" -> "garbage-cheese"
            "dig" -> "concrete-bands"
            "targets" -> "targets-scatter"
            "quads" -> "well-setup"
            "combo" -> "combo-ladder"
            "tspins" -> "tspin-lesson"
            else -> throw IllegalArgumentException(kind)
        }
    }

    private fun objectiveSpec(objective: Objective): ObjectiveSpec = when (objective) {
        is Objective.Lines -> ObjectiveSpec("lines", n = objective.n)
        is Objective.Score -> ObjectiveSpec("score", points = objective.points)
        Objective.Dig -> ObjectiveSpec("dig")
        Objective.Targets -> ObjectiveSpec("targets")
        is Objective.Quads -> ObjectiveSpec("quads", n = objective.n)
        is Objective.TSpins -> ObjectiveSpec("tspins", n = objective.n)
        is Objective.Combo -> ObjectiveSpec("combo", n = objective.n)
    }

    private fun hex(value: Long): String = java.lang.Long.toHexString(value).padStart(16, '0')

    private fun round(value: Double): Double = Math.round(value * 10_000.0) / 10_000.0
}
