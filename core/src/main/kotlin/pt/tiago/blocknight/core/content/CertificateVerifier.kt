package pt.tiago.blocknight.core.content

import pt.tiago.blocknight.core.model.ActivePiece
import pt.tiago.blocknight.core.model.Outcome
import pt.tiago.blocknight.core.model.PieceType
import pt.tiago.blocknight.core.model.StateHash
import pt.tiago.blocknight.core.rules.Engine
import pt.tiago.blocknight.core.search.Placement
import pt.tiago.blocknight.core.search.PlacementReplay

/**
 * Replay de um certificado com o mesmo motor (CONTEUDO §4). Confere a identidade do nível, o motor,
 * a revisão, cada passo e o resultado. Devolve os erros; vazia significa certificado válido.
 */
object CertificateVerifier {

    fun verify(floor: FloorFile, difficulty: String, variant: VariantSpec, cert: CertificateFile): List<String> {
        val errors = ArrayList<String>()
        if (cert.floorId != floor.id) errors += "floorId ${cert.floorId} ≠ ${floor.id}"
        if (cert.difficulty != difficulty) errors += "dificuldade ${cert.difficulty} ≠ $difficulty"
        if (cert.engineVersion != ENGINE_VERSION) errors += "engineVersion ${cert.engineVersion} ≠ $ENGINE_VERSION"
        if (cert.levelRevision != floor.revision) errors += "revisão ${cert.levelRevision} ≠ ${floor.revision}"
        val expectedHash = Canonical.levelHash(floor, difficulty, variant)
        if (cert.levelHash != expectedHash) errors += "levelHash não coincide (nível alterado sem recertificar)"
        if (errors.isNotEmpty()) return errors

        val rules = Compile.rules(floor, difficulty, variant)
        var state = Engine.newGame(rules)
        for ((index, step) in cert.steps.withIndex()) {
            val where = "passo ${index + 1}"
            if (state.isFinished) return errors + "$where depois do fim"
            val expectedType = PieceType.fromLetter(step.piece.single())
            val placement = Placement(
                hold = step.hold,
                path = step.path,
                piece = ActivePiece(expectedType, step.final.rot, step.final.x, step.final.y),
                lastWasRotation = false,
                lastKick = -1,
            )
            val beforeLock = try {
                PlacementReplay.moves(state, placement)
            } catch (e: RuntimeException) {
                return errors + "$where: ${e.message}"
            }
            val piece = beforeLock.active
            if (piece == null || piece.type != expectedType) {
                return errors + "$where: peça ${piece?.type} em vez de $expectedType"
            }
            if (piece.x != step.final.x || piece.y != step.final.y || piece.rot != step.final.rot) {
                return errors + "$where: posição final ${piece.x},${piece.y},${piece.rot} ≠ ${step.final.x},${step.final.y},${step.final.rot}"
            }
            state = Engine.lockAndSpawn(beforeLock)
            val hash = StateHash.of(state)
            if (hash != step.stateHash) return errors + "$where: stateHash $hash ≠ ${step.stateHash}"
        }

        val result = cert.result
        if (result.pieces != cert.steps.size) errors += "pieces ${result.pieces} ≠ passos ${cert.steps.size}"
        val won = state.outcome == Outcome.Won
        if (!won) errors += "o replay não vence (${state.outcome})"
        if (result.won != won) errors += "won declarado ${result.won} ≠ replay $won"
        if (result.linesCleared != state.lines) errors += "linhas ${result.linesCleared} ≠ replay ${state.lines}"
        if (result.score != state.score) errors += "pontos ${result.score} ≠ replay ${state.score}"
        if (result.finalStateHash != StateHash.of(state)) errors += "finalStateHash não coincide"
        val budget = variant.maxPieces
        if (state.pieces > budget) errors += "peças ${state.pieces} acima do orçamento $budget"
        return errors
    }
}
