package pt.tiago.blocknight.core.model

/** Os sete tetrominós. `code` é o valor guardado no tabuleiro (1..7) e é estável entre versões. */
enum class PieceType(val code: Int) {
    I(1), O(2), T(3), S(4), Z(5), J(6), L(7);

    companion object {
        fun fromLetter(letter: Char): PieceType = entries.first { it.name == letter.uppercase() }
    }
}

/**
 * Forma e kicks do Super Rotation System. Estados: 0 = nascimento, 1 = R (horário), 2 = 180°, 3 = L.
 * As coordenadas são relativas à origem da caixa (canto inferior esquerdo), com y para cima.
 */
object Srs {
    /** Caixa 3×3 com a peça de nascimento; a parte plana fica na linha y = 1 (nascimento em y0 = 19). */
    private val spawn3x3 = mapOf(
        PieceType.T to listOf(".X.", "XXX", "..."),
        PieceType.S to listOf(".XX", "XX.", "..."),
        PieceType.Z to listOf("XX.", ".XX", "..."),
        PieceType.J to listOf("X..", "XXX", "..."),
        PieceType.L to listOf("..X", "XXX", "..."),
    )

    private val shapes: Array<Array<IntArray>> = Array(PieceType.entries.size) { Array(4) { IntArray(0) } }

    init {
        for (type in PieceType.entries) {
            val cells: List<Pair<Int, Int>> = when (type) {
                PieceType.O -> listOf(0 to 0, 1 to 0, 0 to 1, 1 to 1)
                PieceType.I -> listOf(0 to 1, 1 to 1, 2 to 1, 3 to 1)
                else -> parseRows(spawn3x3.getValue(type))
            }
            var state = cells
            for (rot in 0..3) {
                shapes[type.ordinal][rot] = state.flatMap { listOf(it.first, it.second) }.toIntArray()
                state = if (type == PieceType.I) iState(rot + 1) else if (type == PieceType.O) cells else state.map { rotateCw(it, type) }
            }
        }
    }

    private fun parseRows(rows: List<String>): List<Pair<Int, Int>> {
        val out = ArrayList<Pair<Int, Int>>()
        rows.forEachIndexed { row, text ->
            text.forEachIndexed { col, ch -> if (ch == 'X') out += col to (2 - row) }
        }
        return out
    }

    /** Rotação horária em torno do centro (1, 1) da caixa 3×3. */
    private fun rotateCw(cell: Pair<Int, Int>, type: PieceType): Pair<Int, Int> {
        check(type != PieceType.I && type != PieceType.O)
        val dx = cell.first - 1
        val dy = cell.second - 1
        return (dy + 1) to (-dx + 1)
    }

    /** Estados da peça I (pivô próprio da caixa 4×4). */
    private fun iState(rot: Int): List<Pair<Int, Int>> = when (rot % 4) {
        0 -> listOf(0 to 1, 1 to 1, 2 to 1, 3 to 1)
        1 -> listOf(2 to 0, 2 to 1, 2 to 2, 2 to 3)
        2 -> listOf(0 to 2, 1 to 2, 2 to 2, 3 to 2)
        else -> listOf(1 to 0, 1 to 1, 1 to 2, 1 to 3)
    }

    /** Offsets (dx, dy) das quatro células, achatados como [dx0, dy0, dx1, dy1, …]. */
    fun cells(type: PieceType, rot: Int): IntArray = shapes[type.ordinal][rot and 3]

    /** Origem de nascimento (x0, y0): colunas 3–5 (I em 3–6, O em 4–5); linha plana na 20. */
    fun spawnX(type: PieceType): Int = if (type == PieceType.O) 4 else 3

    fun spawnY(type: PieceType): Int = if (type == PieceType.O) 20 else 19

    /**
     * Testes de kick para a transição from→to (0..3). Índice 0 é a própria posição; o primeiro teste
     * sem colisão vence. As tabelas seguem a especificação SRS (JLSTZ e I). O O não roda.
     */
    fun kicks(type: PieceType, from: Int, to: Int): Array<IntArray> {
        require(type != PieceType.O)
        val key = (from and 3) * 4 + (to and 3)
        return if (type == PieceType.I) I_KICKS[key] else JLSTZ_KICKS[key]
    }

    private val JLSTZ_KICKS: Array<Array<IntArray>> = Array(16) { emptyArray() }
    private val I_KICKS: Array<Array<IntArray>> = Array(16) { emptyArray() }

    private fun table(vararg rows: Pair<Int, Int>): Array<IntArray> = Array(rows.size) { intArrayOf(rows[it].first, rows[it].second) }

    init {
        // Pares de transição (from, to). Os valores são os de SRS, com 0→R igual a 2→R e 0→L igual a 2→L.
        fun set(table: Array<Array<IntArray>>, from: Int, to: Int, values: Array<IntArray>) {
            table[(from and 3) * 4 + (to and 3)] = values
        }
        val zeroR = table(0 to 0, -1 to 0, -1 to 1, 0 to -2, -1 to -2)
        val rZero = table(0 to 0, 1 to 0, 1 to -1, 0 to 2, 1 to 2)
        val twoL = table(0 to 0, 1 to 0, 1 to 1, 0 to -2, 1 to -2)
        val lZero = table(0 to 0, -1 to 0, -1 to -1, 0 to 2, -1 to 2)
        set(JLSTZ_KICKS, 0, 1, zeroR); set(JLSTZ_KICKS, 2, 1, zeroR)
        set(JLSTZ_KICKS, 1, 0, rZero); set(JLSTZ_KICKS, 1, 2, rZero)
        set(JLSTZ_KICKS, 2, 3, twoL); set(JLSTZ_KICKS, 0, 3, twoL)
        set(JLSTZ_KICKS, 3, 2, lZero); set(JLSTZ_KICKS, 3, 0, lZero)

        val iZeroR = table(0 to 0, -2 to 0, 1 to 0, -2 to -1, 1 to 2)
        val iRZero = table(0 to 0, 2 to 0, -1 to 0, 2 to 1, -1 to -2)
        val iROne = table(0 to 0, -1 to 0, 2 to 0, -1 to 2, 2 to -1)
        val iTwoR = table(0 to 0, 1 to 0, -2 to 0, 1 to -2, -2 to 1)
        val iTwoL = table(0 to 0, 2 to 0, -1 to 0, 2 to 1, -1 to -2)
        val iLTwo = table(0 to 0, -2 to 0, 1 to 0, -2 to -1, 1 to 2)
        val iLZero = table(0 to 0, 1 to 0, -2 to 0, 1 to -2, -2 to 1)
        val iZeroL = table(0 to 0, -1 to 0, 2 to 0, -1 to 2, 2 to -1)
        set(I_KICKS, 0, 1, iZeroR); set(I_KICKS, 1, 0, iRZero); set(I_KICKS, 1, 2, iROne); set(I_KICKS, 2, 1, iTwoR)
        set(I_KICKS, 2, 3, iTwoL); set(I_KICKS, 3, 2, iLTwo); set(I_KICKS, 3, 0, iLZero); set(I_KICKS, 0, 3, iZeroL)
    }
}
