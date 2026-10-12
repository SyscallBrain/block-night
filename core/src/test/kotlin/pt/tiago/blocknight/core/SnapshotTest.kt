package pt.tiago.blocknight.core

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import pt.tiago.blocknight.core.model.Command
import pt.tiago.blocknight.core.model.GameSnapshot
import pt.tiago.blocknight.core.model.GameState
import pt.tiago.blocknight.core.model.Mode
import pt.tiago.blocknight.core.model.PieceType
import pt.tiago.blocknight.core.model.StateHash
import pt.tiago.blocknight.core.rules.Engine
import pt.tiago.blocknight.core.random.SplitMix64

class SnapshotTest {
    /** Sem queda forte nem Guardar: o teste precisa de uma partida que ainda decorre no instante do instantâneo. */
    private val commands = Command.entries.filter { it != Command.HARD_DROP && it != Command.SONIC_DROP && it != Command.HOLD }

    private fun run(state: GameState, rng: SplitMix64, ticks: Int): GameState {
        var s = state
        repeat(ticks) {
            val list = if (rng.nextBounded(4) == 0) listOf(commands[rng.nextBounded(commands.size)]) else emptyList()
            s = Engine.step(s, list).state
        }
        return s
    }

    @Test
    fun `a restored game continues exactly like the original`() {
        val r = rules(mode = Mode.CAMPAIGN, speed = 5, seed = 2024L, opening = listOf(PieceType.T))
        val live = run(Engine.newGame(r), SplitMix64(9L), 700)
        assumeTrue(!live.isFinished)
        val json = Json.encodeToString(GameSnapshot.of(live))
        val restored = Json.decodeFromString<GameSnapshot>(json).restore(r)
        assertEquals(StateHash.of(live), StateHash.of(restored))
        val a = run(live, SplitMix64(10L), 400)
        val b = run(restored, SplitMix64(10L), 400)
        assertEquals(StateHash.of(a), StateHash.of(b))
    }

    @Test
    fun `a snapshot is refused for a different level`() {
        val r = rules(mode = Mode.CAMPAIGN, speed = 5, seed = 1L)
        val snapshot = GameSnapshot.of(Engine.newGame(r))
        val other = rules(mode = Mode.CAMPAIGN, speed = 6, seed = 1L)
        val refused = runCatching { snapshot.restore(other) }.exceptionOrNull()
        assertTrue(refused is IllegalArgumentException)
    }

    @Test
    fun `hashes of the rules change when the level changes`() {
        val a = GameSnapshot.rulesHashOf(rules(seed = 1L))
        val b = GameSnapshot.rulesHashOf(rules(seed = 2L))
        assertEquals(false, a == b)
    }
}
