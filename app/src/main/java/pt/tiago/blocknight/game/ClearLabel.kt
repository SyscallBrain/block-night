package pt.tiago.blocknight.game

import pt.tiago.blocknight.core.model.ClearKind
import pt.tiago.blocknight.core.model.Event
import pt.tiago.blocknight.core.model.SpinKind

/** Clear labels shown over the board (DESIGN §5). The UI maps each one to a string resource. */
sealed interface ClearLabel {
    data object Quad : ClearLabel
    data object Perfect : ClearLabel
    data object BackToBack : ClearLabel
    data class Spin(val mini: Boolean, val lines: Int) : ClearLabel
    data class Combo(val combo: Int) : ClearLabel
}

/**
 * Picks the label for a batch of events and hides it after [DURATION_NANOS] (900 ms, DESIGN §5).
 * When one lock yields several labels, the rarest wins: perfect clear, spin, Quad, back-to-back, combo.
 */
class LabelTimer {
    private var label: ClearLabel? = null
    private var shownAt = 0L

    fun onEvents(events: List<Event>, nowNanos: Long) {
        val picked = pick(events) ?: return
        label = picked
        shownAt = nowNanos
    }

    fun visible(nowNanos: Long): ClearLabel? {
        val current = label ?: return null
        if (nowNanos - shownAt >= DURATION_NANOS) {
            label = null
            return null
        }
        return current
    }

    fun clear() {
        label = null
    }

    companion object {
        const val DURATION_NANOS = 900_000_000L

        fun pick(events: List<Event>): ClearLabel? {
            var best: ClearLabel? = null
            var bestRank = Int.MAX_VALUE
            for (event in events) {
                val candidate: ClearLabel = when (event) {
                    is Event.PerfectClear -> ClearLabel.Perfect
                    is Event.SpinScored -> if (event.kind == SpinKind.NONE) continue else ClearLabel.Spin(event.kind == SpinKind.MINI, event.lines)
                    is Event.LinesCleared -> if (event.kind == ClearKind.QUAD) ClearLabel.Quad else continue
                    is Event.BackToBack -> ClearLabel.BackToBack
                    is Event.ComboReached -> if (event.combo >= 2) ClearLabel.Combo(event.combo) else continue
                    else -> continue
                }
                val rank = rank(candidate)
                if (rank < bestRank) {
                    best = candidate
                    bestRank = rank
                }
            }
            return best
        }

        private fun rank(label: ClearLabel): Int = when (label) {
            ClearLabel.Perfect -> 0
            is ClearLabel.Spin -> 1
            ClearLabel.Quad -> 2
            ClearLabel.BackToBack -> 3
            is ClearLabel.Combo -> 4
        }
    }
}

/** Campaign HUD numbers (DESIGN §5), kept out of Compose for tests. */
object CampaignHud {
    /**
     * Best star count still reachable. The active piece will count when it locks, so a win right now uses
     * `piecesLocked + 1` pieces (`piecesLocked` when no piece is in play).
     */
    fun reachableStars(piecesLocked: Int, hasActive: Boolean, threeStarMax: Int, twoStarMax: Int): Int {
        val atWin = piecesLocked + if (hasActive) 1 else 0
        return when {
            atWin <= threeStarMax -> 3
            atWin <= twoStarMax -> 2
            else -> 1
        }
    }

    /** Pieces left in the budget, counting the active piece; null when the floor has no budget. */
    fun piecesLeft(maxPieces: Int?, piecesLocked: Int): Int? = maxPieces?.let { (it - piecesLocked).coerceAtLeast(0) }
}
