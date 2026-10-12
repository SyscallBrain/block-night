package pt.tiago.blocknight.core.content

import pt.tiago.blocknight.core.model.Board
import pt.tiago.blocknight.core.model.Cell
import pt.tiago.blocknight.core.model.Mode
import pt.tiago.blocknight.core.model.Objective
import pt.tiago.blocknight.core.model.PieceType
import pt.tiago.blocknight.core.model.RisingGarbage
import pt.tiago.blocknight.core.model.Rules
import java.util.Locale

/** Os 12 bairros e os intervalos de velocidade por dificuldade (PLANO §5.5). Os nomes são próprios. */
data class District(val id: Int, val name: String, val speeds: Map<String, IntRange>)

object Districts {
    const val FLOORS_PER_DISTRICT = 50
    const val COUNT = 12
    const val TOTAL_FLOORS = FLOORS_PER_DISTRICT * COUNT

    val ALL: List<District> = listOf(
        District(1, "Alfama", mapOf("easy" to 1..3, "medium" to 1..4, "hard" to 2..5)),
        District(2, "Mouraria", mapOf("easy" to 1..3, "medium" to 2..5, "hard" to 3..6)),
        District(3, "Ribeira", mapOf("easy" to 2..4, "medium" to 3..6, "hard" to 4..7)),
        District(4, "Miragaia", mapOf("easy" to 2..4, "medium" to 3..6, "hard" to 4..8)),
        District(5, "Bairro Alto", mapOf("easy" to 2..5, "medium" to 4..7, "hard" to 5..8)),
        District(6, "Chiado", mapOf("easy" to 3..5, "medium" to 4..7, "hard" to 5..9)),
        District(7, "Baixa", mapOf("easy" to 3..6, "medium" to 5..8, "hard" to 6..10)),
        District(8, "Bonfim", mapOf("easy" to 3..6, "medium" to 5..8, "hard" to 6..10)),
        District(9, "Foz", mapOf("easy" to 4..6, "medium" to 5..9, "hard" to 7..11)),
        District(10, "Belém", mapOf("easy" to 4..7, "medium" to 6..9, "hard" to 7..12)),
        District(11, "Avenidas Novas", mapOf("easy" to 5..7, "medium" to 6..10, "hard" to 8..13)),
        District(12, "Parque das Nações", mapOf("easy" to 5..8, "medium" to 7..11, "hard" to 9..14)),
    )

    fun districtOf(ordinal: Int): District {
        require(ordinal in 1..TOTAL_FLOORS) { "andar $ordinal fora de 1..$TOTAL_FLOORS" }
        return ALL[(ordinal - 1) / FLOORS_PER_DISTRICT]
    }

    fun floorId(ordinal: Int): String = "floor-%04d".format(Locale.ROOT, ordinal)

    /** Número de andar a partir do id, ou null se o formato for inválido. */
    fun ordinalOf(id: String): Int? = Regex("""floor-(\d{4})""").matchEntire(id)?.groupValues?.get(1)?.toInt()
}

/** Conversões do formato de ficheiro para o motor. */
object Compile {
    val PREVIEW_BY_DIFFICULTY = mapOf("easy" to 5, "medium" to 3, "hard" to 2)
    const val TUTORIAL_LAST_FLOOR = 5

    fun objective(spec: ObjectiveSpec): Objective = when (spec.type) {
        "lines" -> Objective.Lines(requireNotNull(spec.n) { "lines sem n" })
        "dig" -> Objective.Dig
        "targets" -> Objective.Targets
        "score" -> Objective.Score(requireNotNull(spec.points) { "score sem points" })
        "quads" -> Objective.Quads(requireNotNull(spec.n) { "quads sem n" })
        "tspins" -> Objective.TSpins(requireNotNull(spec.n) { "tspins sem n" })
        "combo" -> Objective.Combo(requireNotNull(spec.n) { "combo sem n" })
        else -> throw IllegalArgumentException("objetivo desconhecido: ${spec.type}")
    }

    fun board(rows: List<String>): Board {
        val cells = IntArray(Board.WIDTH * Board.HEIGHT)
        rows.forEachIndexed { index, text ->
            val y = rows.size - 1 - index
            text.forEachIndexed { x, ch -> cells[y * Board.WIDTH + x] = cellValue(ch) }
        }
        return Board.of(cells)
    }

    fun cellValue(ch: Char): Int = when (ch) {
        '.' -> Cell.EMPTY
        'G' -> Cell.GARBAGE
        'R' -> Cell.CONCRETE
        'X' -> Cell.TARGET
        else -> PieceType.fromLetter(ch).code
    }

    fun seed(hex: String): Long {
        require(hex.length == 16 && hex.all { it in '0'..'9' || it in 'a'..'f' }) { "seed inválida: $hex" }
        return java.lang.Long.parseUnsignedLong(hex, 16)
    }

    fun rules(floor: FloorFile, difficulty: String, variant: VariantSpec): Rules = Rules(
        mode = Mode.CAMPAIGN,
        speedLevel = variant.speedLevel,
        previewCount = variant.previewCount,
        allowHold = variant.allowHold,
        seed = seed(variant.seedHex),
        garbageSeed = seed(variant.garbageSeedHex),
        opening = variant.openingQueue.map { PieceType.fromLetter(it.single()) },
        initialBoard = board(variant.initialBoard),
        objective = objective(variant.objective),
        maxPieces = variant.maxPieces,
        risingGarbage = variant.risingGarbage?.let { RisingGarbage(it.everyPieces, it.startAfter) },
    ).also {
        check(difficulty in DIFFICULTIES)
        check(floor.ordinal >= 1)
    }
}
