package pt.tiago.blocknight.core.rules

import pt.tiago.blocknight.core.model.ActivePiece
import pt.tiago.blocknight.core.model.Board
import pt.tiago.blocknight.core.model.Cell
import pt.tiago.blocknight.core.model.ClearKind
import pt.tiago.blocknight.core.model.Command
import pt.tiago.blocknight.core.model.Event
import pt.tiago.blocknight.core.model.GameState
import pt.tiago.blocknight.core.model.LossReason
import pt.tiago.blocknight.core.model.Mode
import pt.tiago.blocknight.core.model.Objective
import pt.tiago.blocknight.core.model.Outcome
import pt.tiago.blocknight.core.model.PieceType
import pt.tiago.blocknight.core.model.Rules
import pt.tiago.blocknight.core.model.SpinKind
import pt.tiago.blocknight.core.model.Srs
import pt.tiago.blocknight.core.random.BagRandomizer

/** Resultado de um tick: estado novo e eventos ordenados. A UI anima os eventos e nunca recalcula regras. */
data class StepResult(val state: GameState, val events: List<Event>)

/**
 * Motor determinístico (PLANO §4 e §7). Não depende de Android nem de relógio: um jogo é
 * (semente, variante, lista de (tick, comando)).
 *
 * Duas formas de usar o mesmo código:
 * - tempo real: [step] avança um tick de 60 Hz com gravidade, bloqueio e ARE;
 * - modo de passos: [applyMove] e [lockAndSpawn] executam colocações sem gravidade (solver e certificados).
 */
object Engine {
    const val LOCK_DELAY_TICKS = 30
    const val MAX_LOCK_RESETS = 15
    const val ARE_TICKS = 6
    const val LINE_CLEAR_DELAY_TICKS = 15
    const val SOFT_DROP_MULTIPLIER = 20
    const val ULTRA_TICKS = 7200L
    private const val ZEN_CLEAR_ROWS = 10
    private const val VISIBLE_TOP = Board.VISIBLE

    fun newGame(rules: Rules): GameState {
        val initial = GameState(
            rules = rules,
            board = rules.initialBoard,
            active = null,
            are = 0,
            nextIndex = 0,
            hold = null,
            holdUsed = false,
            lockTimer = 0,
            resets = 0,
            lowestY = Int.MAX_VALUE,
            gravityAcc = 0,
            softDrop = false,
            lastWasRotation = false,
            lastKick = -1,
            bufferedRotation = 0,
            bufferedHold = false,
            combo = -1,
            streak = 0,
            backToBack = false,
            score = 0,
            lines = 0,
            pieces = 0,
            quads = 0,
            tspins = 0,
            perfectClears = 0,
            targetsLit = 0,
            maxStreak = 0,
            garbageRows = 0,
            zenClears = 0,
            ticks = 0,
            outcome = null,
        )
        val events = ArrayList<Event>()
        return spawn(initial, events)
    }

    /** Avança um tick com os comandos desse tick. Com o jogo terminado devolve o mesmo estado. */
    fun step(state: GameState, commands: List<Command>): StepResult {
        if (state.isFinished) return StepResult(state, emptyList())
        val events = ArrayList<Event>()
        var s = state.copy(ticks = state.ticks + 1)

        if (s.active == null) {
            s = handleAre(s, commands, events)
        } else {
            s = handleFalling(s, commands, events)
        }

        if (s.outcome == null && s.rules.mode == Mode.ULTRA && s.ticks >= ULTRA_TICKS) {
            s = s.copy(outcome = Outcome.TimeUp)
            events += Event.Ended(Outcome.TimeUp)
        }
        return StepResult(s, events)
    }

    private fun handleAre(state: GameState, commands: List<Command>, events: MutableList<Event>): GameState {
        var s = state
        for (command in commands) {
            when (command) {
                Command.ROTATE_CW -> s = s.copy(bufferedRotation = s.bufferedRotation + 1)
                Command.ROTATE_CCW -> s = s.copy(bufferedRotation = s.bufferedRotation - 1)
                Command.HOLD -> s = if (s.rules.allowHold) s.copy(bufferedHold = true) else rejectHold(s, events)
                Command.SOFT_DROP_START -> s = s.copy(softDrop = true)
                Command.SOFT_DROP_STOP -> s = s.copy(softDrop = false)
                else -> Unit
            }
        }
        if (s.are > 0) s = s.copy(are = s.are - 1)
        if (s.are == 0) s = spawn(s, events)
        return s
    }

    private fun handleFalling(state: GameState, commands: List<Command>, events: MutableList<Event>): GameState {
        var s = state
        for (command in commands) {
            val p = s.active ?: break
            when (command) {
                Command.SHIFT_LEFT -> s = moveShift(s, -1)
                Command.SHIFT_RIGHT -> s = moveShift(s, 1)
                Command.ROTATE_CW -> s = rotate(s, 1)
                Command.ROTATE_CCW -> s = rotate(s, -1)
                Command.SOFT_DROP_START -> s = s.copy(softDrop = true)
                Command.SOFT_DROP_STOP -> s = s.copy(softDrop = false)
                Command.SONIC_DROP -> s = dropToFloor(s, scorePerRow = 1)
                Command.HARD_DROP -> {
                    s = dropToFloor(s, scorePerRow = 2)
                    return lockPiece(s, events)
                }
                Command.HOLD -> {
                    if (!s.rules.allowHold || s.holdUsed) {
                        s = rejectHold(s, events)
                    } else {
                        s = holdPiece(s.copy(active = null), p, events)
                        if (s.outcome != null) return s
                    }
                }
            }
            if (s.outcome != null) return s
        }

        s = applyGravity(s)
        val active = s.active ?: return s
        if (isGrounded(s.board, active)) {
            s = s.copy(lockTimer = s.lockTimer + 1)
            if (s.lockTimer >= LOCK_DELAY_TICKS || s.resets >= MAX_LOCK_RESETS) {
                return lockPiece(s, events)
            }
        } else {
            s = s.copy(lockTimer = 0)
        }
        return s
    }

    private fun rejectHold(state: GameState, events: MutableList<Event>): GameState {
        val reason = if (!state.rules.allowHold) "disabled" else "already_used"
        events += Event.HoldRejected(reason)
        return state
    }

    /** Gravidade por tick em ponto fixo (linhas × 65536), multiplicada pela queda suave. */
    private fun applyGravity(state: GameState): GameState {
        var s = state
        val p0 = s.active ?: return s
        val multiplier = if (s.softDrop) SOFT_DROP_MULTIPLIER else 1
        var acc = s.gravityAcc + GravityTable.perTick(s.level) * multiplier
        var p = p0
        while (acc >= GravityTable.ONE_ROW) {
            val lower = p.copy(y = p.y - 1)
            if (canPlace(s.board, lower)) {
                p = lower
                acc -= GravityTable.ONE_ROW
                s = s.copy(lastWasRotation = false)
                if (s.softDrop) s = s.copy(score = s.score + 1)
            } else {
                acc = GravityTable.ONE_ROW
                break
            }
        }
        s = s.copy(gravityAcc = acc, active = p)
        if (p.y < s.lowestY) s = s.copy(lowestY = p.y, resets = 0, lockTimer = 0)
        return s
    }

    private fun moveShift(state: GameState, dx: Int): GameState {
        val p = state.active ?: return state
        val moved = p.copy(x = p.x + dx)
        if (!canPlace(state.board, moved)) return state
        return afterMove(state.copy(active = moved, lastWasRotation = false))
    }

    private fun rotate(state: GameState, dir: Int): GameState {
        val p = state.active ?: return state
        val result = tryRotate(state.board, p, dir) ?: return state
        val s = state.copy(active = result.first, lastWasRotation = true, lastKick = result.second)
        return afterMove(s)
    }

    /** Após um movimento ou rotação com sucesso, a peça apoiada repõe o atraso (até 15 vezes). */
    private fun afterMove(state: GameState): GameState {
        val p = state.active ?: return state
        if (!isGrounded(state.board, p)) return state
        return state.copy(lockTimer = 0, resets = state.resets + 1)
    }

    /** Queda instantânea sem bloquear (SonicDrop) ou até ao fundo antes de bloquear (HardDrop). */
    private fun dropToFloor(state: GameState, scorePerRow: Int): GameState {
        var p = state.active ?: return state
        var rows = 0
        while (canPlace(state.board, p.copy(y = p.y - 1))) {
            p = p.copy(y = p.y - 1)
            rows++
        }
        if (rows == 0) return state
        var s = state.copy(
            active = p,
            lastWasRotation = false,
            lastKick = -1,
            score = state.score + rows.toLong() * scorePerRow,
            lockTimer = 0,
            gravityAcc = 0,
        )
        if (p.y < s.lowestY) s = s.copy(lowestY = p.y, resets = 0)
        return s
    }

    /** Rotação SRS: testa os kicks da transição e aceita o primeiro sem colisão. Devolve (peça, índice do kick). */
    fun tryRotate(board: Board, piece: ActivePiece, dir: Int): Pair<ActivePiece, Int>? {
        if (piece.type == PieceType.O) return null
        val to = (piece.rot + dir) and 3
        val tests = Srs.kicks(piece.type, piece.rot, to)
        for ((index, test) in tests.withIndex()) {
            val candidate = piece.copy(rot = to, x = piece.x + test[0], y = piece.y + test[1])
            if (canPlace(board, candidate)) return candidate to index
        }
        return null
    }

    fun tryShift(board: Board, piece: ActivePiece, dx: Int): ActivePiece? {
        val moved = piece.copy(x = piece.x + dx)
        return if (canPlace(board, moved)) moved else null
    }

    fun canPlace(board: Board, piece: ActivePiece): Boolean = canPlace(board, piece.type, piece.rot, piece.x, piece.y)

    fun canPlace(board: Board, type: PieceType, rot: Int, x: Int, y: Int): Boolean {
        val cells = Srs.cells(type, rot)
        var i = 0
        while (i < cells.size) {
            val cx = x + cells[i]
            val cy = y + cells[i + 1]
            if (cx < 0 || cx >= Board.WIDTH || cy < 0 || cy >= Board.HEIGHT) return false
            if (board.get(cx, cy) != Cell.EMPTY) return false
            i += 2
        }
        return true
    }

    fun isGrounded(board: Board, piece: ActivePiece): Boolean = !canPlace(board, piece.copy(y = piece.y - 1))

    /** Nascimento da peça seguinte. Colisão no nascimento é block out; Zen remove as 10 linhas de baixo. */
    internal fun spawn(state: GameState, events: MutableList<Event>): GameState {
        var s = state
        var attempts = 0
        while (true) {
            val type = BagRandomizer.pieceAt(s.rules, s.nextIndex)
            s = s.copy(nextIndex = s.nextIndex + 1)
            val base = ActivePiece(type, 0, Srs.spawnX(type), Srs.spawnY(type))
            if (canPlace(s.board, base)) {
                return placeNewPiece(s, base, events)
            }
            attempts++
            if (s.rules.mode == Mode.ZEN && attempts <= 4) {
                s = clearBottomRowsForZen(s, events)
                s = s.copy(nextIndex = s.nextIndex - 1)
                continue
            }
            return end(s.copy(active = null), Outcome.Lost(LossReason.BLOCK_OUT), events)
        }
    }

    /** Coloca a peça no nascimento, aplica IRS e desce 1 linha se houver espaço (PLANO §5.4). */
    private fun placeNewPiece(state: GameState, base: ActivePiece, events: MutableList<Event>): GameState {
        var p = base
        var rotated = false
        val net = state.bufferedRotation
        if (net != 0 && p.type != PieceType.O) {
            val dir = if (net > 0) 1 else -1
            repeat(kotlin.math.abs(net) % 4) {
                val next = tryRotate(state.board, p, dir)
                if (next != null) { p = next.first; rotated = true }
            }
        }
        val lower = p.copy(y = p.y - 1)
        val dropped = canPlace(state.board, lower)
        if (dropped) p = lower
        var s = state.copy(
            active = p,
            are = 0,
            lockTimer = 0,
            resets = 0,
            lowestY = p.y,
            gravityAcc = 0,
            lastWasRotation = rotated && !dropped,
            lastKick = -1,
            bufferedRotation = 0,
        )
        events += Event.Spawned(p.type)
        if (state.bufferedHold) {
            s = s.copy(bufferedHold = false)
            if (s.rules.allowHold && !s.holdUsed) s = holdPiece(s.copy(active = null), p, events)
            if (s.outcome != null) return s
        }
        return s
    }

    /**
     * Troca com a caixa de Guardar (PLANO §4.5). Sem peça guardada, a peça atual vai para a caixa e a
     * seguinte nasce da fila. Com peça guardada, troca-se e a guardada nasce na orientação inicial.
     */
    internal fun holdFromPlacement(state: GameState, current: ActivePiece): GameState =
        holdPiece(state.copy(active = null), current, ArrayList())

    private fun holdPiece(state: GameState, current: ActivePiece, events: MutableList<Event>): GameState {
        val previous = state.hold
        val s0 = state.copy(hold = current.type, holdUsed = true)
        events += Event.Held(current.type)
        if (previous == null) {
            return spawn(s0.copy(active = null), events)
        }
        val base = ActivePiece(previous, 0, Srs.spawnX(previous), Srs.spawnY(previous))
        if (!canPlace(s0.board, base)) return end(s0.copy(active = null), Outcome.Lost(LossReason.BLOCK_OUT), events)
        return placeNewPiece(s0, base, events)
    }

    /** Modo de passos: bloqueia e nasce a peça seguinte já, sem ARE. Usado pelo solver e pelos certificados. */
    fun lockAndSpawn(state: GameState): GameState {
        val events = ArrayList<Event>()
        val locked = lockPiece(state, events)
        if (locked.outcome != null) return locked
        return spawn(locked, events)
    }

    /** Bloqueio, limpeza, pontuação, lixo e fim. O ARE fica definido aqui para o tempo real. */
    private fun lockPiece(state: GameState, events: MutableList<Event>): GameState {
        val piece = state.active ?: return state
        val level = state.level
        val mode = state.rules.mode
        val spin = detectSpin(state.board, piece, state.lastWasRotation, state.lastKick)
        val placedCells = placeCells(state.board, piece)

        val topReached = pieceCellsAll(piece) { _, y -> y >= VISIBLE_TOP }
        var board = placedCells
        val fullRows = (0 until Board.HEIGHT).filter { board.isRowFull(it) }
        val crackRows = fullRows.filter { board.rowContains(it, Cell.CONCRETE) }
        val clearRows = fullRows.filter { !board.rowContains(it, Cell.CONCRETE) }
        var crackedCells = 0
        if (crackRows.isNotEmpty()) {
            crackedCells = crackRows.sumOf { y -> (0 until Board.WIDTH).count { x -> board.get(x, y) == Cell.CONCRETE } }
            board = board.edit { cells ->
                for (y in crackRows) for (x in 0 until Board.WIDTH) {
                    val i = y * Board.WIDTH + x
                    cells[i] = when {
                        Cell.isMino(cells[i]) -> Cell.EMPTY
                        cells[i] == Cell.CONCRETE -> Cell.GARBAGE
                        else -> cells[i]
                    }
                }
            }
            events += Event.Cracked(crackRows.size)
        }
        val targetsInCleared = clearRows.sumOf { y -> (0 until Board.WIDTH).count { x -> board.get(x, y) == Cell.TARGET } }
        val n = clearRows.size
        if (n > 0) board = removeRows(board, clearRows)

        val wasBackToBack = state.backToBack
        val difficult = n == 4 || (spin != SpinKind.NONE && n > 0)
        var points = 0L
        if (spin != SpinKind.NONE || n > 0) {
            points = actionPoints(spin, n, level)
        }
        if (difficult && wasBackToBack && points > 0) {
            points = points * 3 / 2
            events += Event.BackToBack(points)
        }
        val newCombo = if (n > 0) state.combo + 1 else -1
        val newStreak = if (n > 0) state.streak + 1 else 0
        if (n > 0 && newCombo >= 1) {
            points += 50L * newCombo * level
            events += Event.ComboReached(newCombo + 1)
        }
        val newBackToBack = when {
            n == 0 -> wasBackToBack
            difficult -> true
            else -> false
        }
        val perfect = n > 0 && board.isEmpty()
        if (perfect) {
            points += perfectBonus(n, level) + if (n == 4 && wasBackToBack) 3200L else 0L
            events += Event.PerfectClear
        }
        if (targetsInCleared > 0) {
            points += 150L * targetsInCleared
            events += Event.TargetsLit(targetsInCleared)
        }
        if (crackedCells > 0 && mode == Mode.CAMPAIGN) points += 50L * crackedCells
        if (n > 0) events += Event.LinesCleared(n, clearKind(n), points)
        if (spin == SpinKind.FULL && n > 0 || spin != SpinKind.NONE && n == 0) {
            events += Event.SpinScored(spin, n)
        }

        var s = state.copy(
            board = board,
            active = null,
            are = ARE_TICKS + if (n > 0) LINE_CLEAR_DELAY_TICKS else 0,
            lockTimer = 0,
            resets = 0,
            gravityAcc = 0,
            lastWasRotation = false,
            lastKick = -1,
            holdUsed = false,
            combo = newCombo,
            streak = newStreak,
            maxStreak = maxOf(state.maxStreak, newStreak),
            backToBack = newBackToBack,
            score = state.score + points,
            lines = state.lines + n,
            pieces = state.pieces + 1,
            quads = state.quads + if (n == 4) 1 else 0,
            tspins = state.tspins + if (spin == SpinKind.FULL && n > 0) 1 else 0,
            perfectClears = state.perfectClears + if (perfect) 1 else 0,
            targetsLit = state.targetsLit + targetsInCleared,
        )
        events += Event.Locked(piece.type, spin)

        if (topReached && mode == Mode.ZEN) {
            s = clearBottomRowsForZen(s, events)
        }
        var overflow = false
        val rising = s.rules.risingGarbage
        if (rising != null && s.pieces > rising.startAfter && (s.pieces - rising.startAfter) % rising.everyPieces == 0) {
            val raised = raiseGarbage(s, events)
            if (raised == null) overflow = true else s = raised
        }
        if (objectiveMet(s)) {
            return end(s, Outcome.Won, events)
        }
        if (mode == Mode.ZEN) return s
        if (topReached) return end(s, Outcome.Lost(LossReason.LOCK_OUT), events)
        if (overflow) return end(s, Outcome.Lost(LossReason.GARBAGE_OVERFLOW), events)
        val budget = s.rules.maxPieces
        if (budget != null && s.pieces >= budget) return end(s, Outcome.Lost(LossReason.OUT_OF_PIECES), events)
        return s
    }

    private fun end(state: GameState, outcome: Outcome, events: MutableList<Event>): GameState {
        events += Event.Ended(outcome)
        return state.copy(outcome = outcome, active = null, are = 0)
    }

    private fun clearKind(n: Int): ClearKind = when (n) {
        1 -> ClearKind.SINGLE
        2 -> ClearKind.DOUBLE
        3 -> ClearKind.TRIPLE
        else -> ClearKind.QUAD
    }

    private fun actionPoints(spin: SpinKind, n: Int, level: Int): Long {
        val base = when (spin) {
            SpinKind.NONE -> LINE_POINTS[n.coerceIn(0, 4)]
            SpinKind.FULL -> if (n >= 4) 800L else SPIN_FULL_POINTS[n.coerceIn(0, 3)]
            SpinKind.MINI -> SPIN_MINI_POINTS[n.coerceIn(0, 2)]
        }
        return base * level
    }

    private fun perfectBonus(n: Int, level: Int): Long = PERFECT_POINTS[(n - 1).coerceIn(0, 3)] * level

    /** Deteção de T-spin (PLANO §4.7). Só conta se a última ação foi uma rotação. */
    internal fun detectSpin(board: Board, piece: ActivePiece, lastWasRotation: Boolean, lastKick: Int): SpinKind {
        if (piece.type != PieceType.T || !lastWasRotation) return SpinKind.NONE
        val cx = piece.x + 1
        val cy = piece.y + 1
        fun occupied(x: Int, y: Int): Boolean =
            x < 0 || x >= Board.WIDTH || y < 0 || (y < Board.HEIGHT && board.get(x, y) != Cell.EMPTY)
        val corners = listOf(cx - 1 to cy - 1, cx + 1 to cy - 1, cx - 1 to cy + 1, cx + 1 to cy + 1)
        val occupiedCorners = corners.count { (x, y) -> occupied(x, y) }
        if (occupiedCorners < 3) return SpinKind.NONE
        val front = when (piece.rot and 3) {
            0 -> listOf(cx - 1 to cy + 1, cx + 1 to cy + 1)
            1 -> listOf(cx + 1 to cy - 1, cx + 1 to cy + 1)
            2 -> listOf(cx - 1 to cy - 1, cx + 1 to cy - 1)
            else -> listOf(cx - 1 to cy - 1, cx - 1 to cy + 1)
        }
        val frontOccupied = front.count { (x, y) -> occupied(x, y) }
        return when {
            frontOccupied == 2 -> SpinKind.FULL
            lastKick == 4 -> SpinKind.FULL
            else -> SpinKind.MINI
        }
    }

    private fun placeCells(board: Board, piece: ActivePiece): Board {
        val cells = Srs.cells(piece.type, piece.rot)
        return board.edit { arr ->
            var i = 0
            while (i < cells.size) {
                val x = piece.x + cells[i]
                val y = piece.y + cells[i + 1]
                arr[y * Board.WIDTH + x] = piece.type.code
                i += 2
            }
        }
    }

    private inline fun pieceCellsAll(piece: ActivePiece, predicate: (Int, Int) -> Boolean): Boolean {
        val cells = Srs.cells(piece.type, piece.rot)
        var i = 0
        while (i < cells.size) {
            if (!predicate(piece.x + cells[i], piece.y + cells[i + 1])) return false
            i += 2
        }
        return true
    }

    /** Remove as linhas indicadas e faz descer as de cima. `rows` tem de estar ordenado. */
    internal fun removeRows(board: Board, rows: List<Int>): Board {
        val keep = (0 until Board.HEIGHT).filter { it !in rows }
        return board.edit { arr ->
            val source = board.toIntArray()
            java.util.Arrays.fill(arr, Cell.EMPTY)
            keep.forEachIndexed { newY, oldY ->
                System.arraycopy(source, oldY * Board.WIDTH, arr, newY * Board.WIDTH, Board.WIDTH)
            }
        }
    }

    private fun clearBottomRowsForZen(state: GameState, events: MutableList<Event>): GameState {
        val rows = (0 until ZEN_CLEAR_ROWS).toList()
        events += Event.ZenCleared(ZEN_CLEAR_ROWS)
        return state.copy(
            board = removeRows(state.board, rows),
            zenClears = state.zenClears + 1,
        )
    }

    /** Lixo que sobe: devolve null se o topo chegou à linha 39 (derrota por transbordo). */
    private fun raiseGarbage(state: GameState, events: MutableList<Event>): GameState? {
        val top = Board.HEIGHT - 1
        for (x in 0 until Board.WIDTH) if (state.board.get(x, top) != Cell.EMPTY) return null
        val hole = BagRandomizer.garbageHole(state.rules.garbageSeed, state.garbageRows)
        val raised = state.board.edit { arr ->
            System.arraycopy(arr, 0, arr, Board.WIDTH, arr.size - Board.WIDTH)
            for (x in 0 until Board.WIDTH) arr[x] = if (x == hole) Cell.EMPTY else Cell.GARBAGE
        }
        events += Event.GarbageRaised(hole)
        return state.copy(board = raised, garbageRows = state.garbageRows + 1)
    }

    /** Verdadeiro quando o objetivo da campanha está cumprido (PLANO §5.2). Sem objetivo, nunca. */
    fun objectiveMet(state: GameState): Boolean = when (val objective = state.rules.objective) {
        null -> false
        is Objective.Lines -> state.lines >= objective.n
        is Objective.Score -> state.score >= objective.points
        is Objective.Quads -> state.quads >= objective.n
        is Objective.TSpins -> state.tspins >= objective.n
        is Objective.Combo -> state.maxStreak >= objective.n
        Objective.Targets -> state.rules.initialTargetCount > 0 && state.board.countCells { it == Cell.TARGET } == 0
        Objective.Dig -> state.board.countCells { Cell.isSpecial(it) } == 0
    }

    private val LINE_POINTS = longArrayOf(0, 100, 300, 500, 800)
    private val SPIN_FULL_POINTS = longArrayOf(400, 800, 1200, 1600)
    private val SPIN_MINI_POINTS = longArrayOf(100, 200, 400)
    private val PERFECT_POINTS = longArrayOf(800, 1200, 1800, 2000)

    /** Passos do modo de passos (sem gravidade): mesmas primitivas de movimento que o tempo real. */
    internal fun shiftStep(state: GameState, dx: Int): GameState? {
        val p = state.active ?: return null
        val moved = tryShift(state.board, p, dx) ?: return null
        return state.copy(active = moved, lastWasRotation = false, lastKick = -1)
    }

    internal fun rotateStep(state: GameState, dir: Int): GameState? {
        val p = state.active ?: return null
        val (moved, kick) = tryRotate(state.board, p, dir) ?: return null
        return state.copy(active = moved, lastWasRotation = true, lastKick = kick)
    }

    internal fun downStep(state: GameState): GameState? {
        val p = state.active ?: return null
        val moved = p.copy(y = p.y - 1)
        if (!canPlace(state.board, moved)) return null
        return state.copy(active = moved, lastWasRotation = false, lastKick = -1)
    }
}

/** Guardar no modo de passos. Devolve null se a troca não for permitida nesta peça. */
fun Engine.holdNow(state: GameState): GameState? {
    val piece = state.active ?: return null
    if (!state.rules.allowHold || state.holdUsed) return null
    return Engine.holdFromPlacement(state, piece)
}
