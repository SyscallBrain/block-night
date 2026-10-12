package pt.tiago.blocknight.core.search

import pt.tiago.blocknight.core.model.ActivePiece
import pt.tiago.blocknight.core.model.Board
import pt.tiago.blocknight.core.model.GameState
import pt.tiago.blocknight.core.rules.Engine
import pt.tiago.blocknight.core.rules.holdNow

/**
 * Colocação final alcançável sem gravidade (PLANO §7.3). `path` usa `L`/`R` (shift), `c`/`w` (rotação
 * horária/anti-horária) e `D` (descer 1). `hold` indica que a peça foi trocada antes de caminhar.
 */
data class Placement(
    val hold: Boolean,
    val path: String,
    val piece: ActivePiece,
    val lastWasRotation: Boolean,
    val lastKick: Int,
) {
    /** Ações do jogador: shifts e rotações. As descidas `D` são a gravidade, medidas pelo tempo de queda. */
    val actions: Int get() = path.count { it != 'D' }

    /** Verdadeiro quando a última ação é uma rotação (colocação por rotação, ou tuck/spin). */
    val endsWithRotation: Boolean get() = path.lastOrNull() == 'c' || path.lastOrNull() == 'w'
}

/**
 * BFS de colocações a partir da peça atual (e, se permitido, a alternativa com Guardar). Os nós guardam o
 * índice do pai e o token que os gerou; o caminho só se reconstrói nas colocações finais. A ordem de
 * expansão é fixa, por isso os caminhos são sempre os mesmos.
 */
object PlacementFinder {

    fun find(state: GameState): List<Placement> {
        val current = state.active ?: return emptyList()
        val out = ArrayList<Placement>()
        out += search(state, hold = false, start = current, lastRot = state.lastWasRotation, lastKick = state.lastKick)
        val afterHold = Engine.holdNow(state)
        val held = afterHold?.active
        if (afterHold != null && held != null && afterHold.outcome == null) {
            out += search(afterHold, hold = true, start = held, lastRot = afterHold.lastWasRotation, lastKick = afterHold.lastKick)
        }
        return out
    }

    private fun search(
        state: GameState,
        hold: Boolean,
        start: ActivePiece,
        lastRot: Boolean,
        lastKick: Int,
    ): List<Placement> {
        val board: Board = state.board
        val visited = HashSet<Long>()
        val nodes = ArrayList<Node>()
        val results = ArrayList<Placement>()
        val seenFinal = HashSet<Long>()
        nodes += Node(start, -1, ' ', lastRot, lastKick)
        visited += key(start)
        var head = 0
        while (head < nodes.size) {
            val index = head++
            val node = nodes[index]
            val piece = node.piece
            val grounded = !Engine.canPlace(board, piece.copy(y = piece.y - 1))
            if (grounded && seenFinal.add(finalKey(piece))) {
                results += Placement(hold, pathOf(nodes, index), piece, node.lastRot, node.lastKick)
            }
            expand(board, index, nodes, visited)
        }
        return results
    }

    private fun expand(board: Board, index: Int, nodes: ArrayList<Node>, visited: MutableSet<Long>) {
        val piece = nodes[index].piece
        Engine.tryShift(board, piece, -1)?.let { push(it, index, 'L', false, -1, nodes, visited) }
        Engine.tryShift(board, piece, 1)?.let { push(it, index, 'R', false, -1, nodes, visited) }
        Engine.tryRotate(board, piece, 1)?.let { (moved, kick) -> push(moved, index, 'c', true, kick, nodes, visited) }
        Engine.tryRotate(board, piece, -1)?.let { (moved, kick) -> push(moved, index, 'w', true, kick, nodes, visited) }
        val down = piece.copy(y = piece.y - 1)
        if (Engine.canPlace(board, down)) push(down, index, 'D', false, -1, nodes, visited)
    }

    private fun push(
        piece: ActivePiece,
        parent: Int,
        token: Char,
        rotated: Boolean,
        kick: Int,
        nodes: ArrayList<Node>,
        visited: MutableSet<Long>,
    ) {
        if (visited.add(key(piece))) nodes += Node(piece, parent, token, rotated, kick)
    }

    private fun pathOf(nodes: List<Node>, index: Int): String {
        val chars = StringBuilder()
        var cursor = index
        while (cursor > 0) {
            chars.append(nodes[cursor].token)
            cursor = nodes[cursor].parent
        }
        return chars.reverse().toString()
    }

    private fun key(p: ActivePiece): Long = (((p.rot.toLong() and 3L) shl 20) or ((p.x.toLong() and 0xFFL) shl 8) or (p.y.toLong() and 0xFFL))

    private fun finalKey(p: ActivePiece): Long = ((p.rot.toLong() and 3L) shl 16) or (p.x.toLong() shl 8) or p.y.toLong()

    private class Node(val piece: ActivePiece, val parent: Int, val token: Char, val lastRot: Boolean, val lastKick: Int)
}

/** Executa uma colocação no modo de passos: caminho, bloqueio e nascimento da peça seguinte. */
object PlacementReplay {
    fun apply(state: GameState, placement: Placement): GameState = Engine.lockAndSpawn(moves(state, placement))

    /** Estado com a peça já na posição final, antes do bloqueio (usado para verificar certificados). */
    fun moves(state: GameState, placement: Placement): GameState {
        var s = if (placement.hold) {
            requireNotNull(Engine.holdNow(state)) { "hold não permitido nesta posição" }
        } else {
            state
        }
        for (token in placement.path) {
            s = when (token) {
                'L' -> Engine.shiftStep(s, -1)
                'R' -> Engine.shiftStep(s, 1)
                'c' -> Engine.rotateStep(s, 1)
                'w' -> Engine.rotateStep(s, -1)
                'D' -> Engine.downStep(s)
                else -> throw IllegalArgumentException("token de caminho desconhecido: $token")
            } ?: throw IllegalStateException("passo '$token' não é possível no caminho ${placement.path}")
        }
        return s
    }
}
