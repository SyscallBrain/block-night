package pt.tiago.blocknight.core

import pt.tiago.blocknight.core.model.ActivePiece
import pt.tiago.blocknight.core.model.Board
import pt.tiago.blocknight.core.model.Cell
import pt.tiago.blocknight.core.model.GameState
import pt.tiago.blocknight.core.model.Mode
import pt.tiago.blocknight.core.model.Objective
import pt.tiago.blocknight.core.model.PieceType
import pt.tiago.blocknight.core.model.Rules
import pt.tiago.blocknight.core.rules.Engine

/** Constrói tabuleiros a partir de linhas de texto, de cima para baixo (`G` lixo, `R` betão, `X` alvo). */
fun boardOf(vararg rowsTopDown: String): Board {
    val cells = IntArray(Board.WIDTH * Board.HEIGHT)
    val rows = rowsTopDown.toList()
    for ((index, text) in rows.withIndex()) {
        val y = rows.size - 1 - index
        require(text.length == Board.WIDTH) { "linha com ${text.length} colunas" }
        text.forEachIndexed { x, ch ->
            cells[y * Board.WIDTH + x] = when (ch) {
                '.' -> Cell.EMPTY
                'G' -> Cell.GARBAGE
                'R' -> Cell.CONCRETE
                'X' -> Cell.TARGET
                else -> error("célula desconhecida $ch")
            }
        }
    }
    return Board.of(cells)
}

fun rules(
    mode: Mode = Mode.MARATHON,
    speed: Int = 1,
    seed: Long = 1L,
    opening: List<PieceType> = emptyList(),
    board: Board = Board.empty(),
    objective: Objective? = null,
    maxPieces: Int? = null,
    allowHold: Boolean = true,
) = Rules(
    mode = mode,
    speedLevel = speed,
    previewCount = 3,
    allowHold = allowHold,
    seed = seed,
    garbageSeed = 7L,
    opening = opening,
    initialBoard = board,
    objective = objective,
    maxPieces = maxPieces,
    risingGarbage = null,
)

/** Coloca a peça dada no estado (modo de passos), bloqueia e devolve o novo estado. */
fun placeAt(state: GameState, piece: ActivePiece): GameState =
    Engine.lockAndSpawn(state.copy(active = piece, lastWasRotation = false, lastKick = -1))

fun GameState.withBoard(board: Board): GameState = copy(board = board)
