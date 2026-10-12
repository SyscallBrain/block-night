package pt.tiago.blocknight.core.model

/**
 * Tabuleiro imutável de 10 colunas × 40 linhas. y = 0 é a linha de baixo; 20..39 é o buffer invisível.
 * Índice = y * 10 + x. Os valores são estáveis: 0 vazio, 1..7 minos (ver [PieceType.code]),
 * 8 lixo, 9 betão, 10 janela-alvo.
 */
class Board private constructor(private val data: IntArray) {

    fun get(x: Int, y: Int): Int = data[y * WIDTH + x]

    /** Cópia com uma célula alterada. */
    fun withCell(x: Int, y: Int, value: Int): Board {
        val copy = data.copyOf()
        copy[y * WIDTH + x] = value
        return Board(copy)
    }

    /** Cópia construída a partir de um bloco mutável. Usado pelo motor para várias edições seguidas. */
    fun edit(block: (IntArray) -> Unit): Board {
        val copy = data.copyOf()
        block(copy)
        return Board(copy)
    }

    fun isRowFull(y: Int): Boolean {
        for (x in 0 until WIDTH) if (data[y * WIDTH + x] == Cell.EMPTY) return false
        return true
    }

    fun rowContains(y: Int, value: Int): Boolean {
        for (x in 0 until WIDTH) if (data[y * WIDTH + x] == value) return true
        return false
    }

    fun isEmpty(): Boolean = data.all { it == Cell.EMPTY }

    fun countCells(predicate: (Int) -> Boolean): Int = data.count(predicate)

    /** Linhas com pelo menos uma célula ocupada (usado para o topo e para diagnósticos). */
    fun highestOccupiedRow(): Int {
        for (y in HEIGHT - 1 downTo 0) {
            for (x in 0 until WIDTH) if (data[y * WIDTH + x] != Cell.EMPTY) return y
        }
        return -1
    }

    fun toIntArray(): IntArray = data.copyOf()

    override fun equals(other: Any?): Boolean = other is Board && other.data.contentEquals(data)

    override fun hashCode(): Int = data.contentHashCode()

    companion object {
        const val WIDTH = 10
        const val HEIGHT = 40
        /** Linhas visíveis: 0..19. Uma peça bloqueada inteiramente a partir da linha 20 é lock out. */
        const val VISIBLE = 20

        fun empty(): Board = Board(IntArray(WIDTH * HEIGHT))

        fun of(values: IntArray): Board {
            require(values.size == WIDTH * HEIGHT) { "Board needs ${WIDTH * HEIGHT} cells, got ${values.size}" }
            return Board(values.copyOf())
        }
    }
}

object Cell {
    const val EMPTY = 0
    const val GARBAGE = 8
    const val CONCRETE = 9
    const val TARGET = 10

    fun isMino(value: Int): Boolean = value in 1..7

    fun isSpecial(value: Int): Boolean = value == GARBAGE || value == CONCRETE || value == TARGET
}
