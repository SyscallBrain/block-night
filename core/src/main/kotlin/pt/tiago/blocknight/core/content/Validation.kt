package pt.tiago.blocknight.core.content

import pt.tiago.blocknight.core.model.Board
import pt.tiago.blocknight.core.model.Cell
import pt.tiago.blocknight.core.model.Objective
import pt.tiago.blocknight.core.rules.Engine

/**
 * Regras estáticas de um andar (CONTEUDO §2.1). Devolve a lista de erros; vazia significa válido.
 * A prova de solução e os limites de exequibilidade são verificados à parte, com o certificado.
 */
object Validation {
    private val PIECE_LETTERS = "IOTSZJL"
    private val CELL_CHARS = ".GRX$PIECE_LETTERS"

    fun floor(file: FloorFile): List<String> {
        val errors = ArrayList<String>()
        val ordinal = Districts.ordinalOf(file.id)
        if (ordinal == null) {
            errors += "id inválido: ${file.id}"
            return errors
        }
        if (file.ordinal != ordinal) errors += "${file.id}: ordinal ${file.ordinal} não coincide com o id"
        val district = Districts.districtOf(ordinal)
        if (file.districtId != district.id) errors += "${file.id}: bairro ${file.districtId}, esperado ${district.id}"
        if (file.revision < 1) errors += "${file.id}: revisão < 1"
        if (file.variants.keys != DIFFICULTIES.toSet()) errors += "${file.id}: variantes ${file.variants.keys} ≠ $DIFFICULTIES"
        for (difficulty in DIFFICULTIES) {
            val variant = file.variants[difficulty] ?: continue
            errors += variant(file, district, difficulty, variant).map { "${file.id}/$difficulty: $it" }
        }
        return errors
    }

    private fun variant(file: FloorFile, district: District, difficulty: String, v: VariantSpec): List<String> {
        val errors = ArrayList<String>()
        val ordinal = file.ordinal
        if (!hex16(v.seedHex)) errors += "seedHex inválida"
        if (!hex16(v.garbageSeedHex)) errors += "garbageSeedHex inválida"
        val range = district.speeds.getValue(difficulty)
        if (v.speedLevel !in range) errors += "velocidade ${v.speedLevel} fora de ${range.first}..${range.last} do bairro"
        if (v.previewCount != Compile.PREVIEW_BY_DIFFICULTY.getValue(difficulty)) errors += "previewCount ${v.previewCount} não coincide com a dificuldade"
        if (!v.allowHold && district.id < 7 && ordinal > Compile.TUTORIAL_LAST_FLOOR) errors += "allowHold=false só a partir do bairro 7"
        if (v.openingQueue.size > 14 || v.openingQueue.any { it.length != 1 || it[0] !in PIECE_LETTERS }) errors += "openingQueue inválida"

        val rowsOk = v.initialBoard.size <= 14 && v.initialBoard.all { it.length == Board.WIDTH && it.all { c -> c in CELL_CHARS } }
        if (!rowsOk) {
            errors += "initialBoard com linhas inválidas (máximo 14, 10 colunas)"
            return errors
        }
        val board = Compile.board(v.initialBoard)
        for (y in 0 until Board.HEIGHT) {
            if (y >= 14 && (0 until Board.WIDTH).any { board.get(it, y) != Cell.EMPTY }) {
                errors += "conteúdo inicial acima da linha 13"
                break
            }
            if (board.isRowFull(y)) errors += "linha $y cheia no início"
        }
        val special = board.countCells { Cell.isSpecial(it) }
        val targets = board.countCells { it == Cell.TARGET }

        val rising = v.risingGarbage
        if (rising != null) {
            if (district.id < 5) errors += "lixo que sobe só a partir do bairro 5"
            if (rising.everyPieces !in 4..20 || rising.startAfter !in 0..30) errors += "risingGarbage fora dos limites"
        }
        when (val type = v.objective.type) {
            "lines", "quads", "tspins", "combo" -> if ((v.objective.n ?: 0) < 1) errors += "objetivo $type sem n ≥ 1"
            "score" -> if ((v.objective.points ?: 0) < 1) errors += "objetivo score sem pontos ≥ 1"
            "dig" -> {
                if (special == 0) errors += "dig sem células especiais"
                if (rising != null) errors += "dig é incompatível com lixo que sobe"
            }
            "targets" -> if (targets == 0) errors += "targets sem janelas-alvo"
            else -> errors += "tipo de objetivo desconhecido: $type"
        }
        if (v.objective.type == "tspins" && district.id < 8) errors += "tspins só a partir do bairro 8"
        if (v.objective.type == "combo" && district.id < 9) errors += "combo só a partir do bairro 9"
        if (v.maxPieces < v.twoStarMaxPieces || v.twoStarMaxPieces < v.threeStarMaxPieces) errors += "orçamentos fora de ordem"
        if (v.threeStarMaxPieces < 1) errors += "orçamento de 3 estrelas inválido"

        if (errors.isEmpty()) {
            val rules = Compile.rules(file, difficulty, v)
            val start = Engine.newGame(rules)
            if (start.outcome != null) errors += "variante começa já terminada: ${start.outcome}"
            else if (Engine.objectiveMet(start)) errors += "variante começa já vencida"
            val objective = rules.objective
            if (objective is Objective.Lines && objective.n < 1) errors += "lines com n < 1"
        }
        return errors
    }

    private fun hex16(text: String): Boolean = text.length == 16 && text.all { it in '0'..'9' || it in 'a'..'f' }

}
