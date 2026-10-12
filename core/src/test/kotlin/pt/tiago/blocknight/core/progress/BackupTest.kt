package pt.tiago.blocknight.core.progress

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupTest {
    private fun sample(): Profile = ProgressReducer.apply(
        Profile(),
        FloorResult("s1", "floor-0001", "medium", true, 30, 4000, threeStarMaxPieces = 36, twoStarMaxPieces = 39),
    )

    @Test
    fun `export and import round-trip the profile exactly`() {
        val p = sample()
        val back = Backup.parse(Backup.export(p)).getOrThrow()
        assertEquals(p, back)
    }

    @Test
    fun `a tampered backup is refused with a checksum error`() {
        val text = Backup.export(sample()).replace("\"stars\": 3", "\"stars\": 3 ")
            .replace("\"bestScore\": 4000", "\"bestScore\": 99999")
        val result = Backup.parse(text)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("checksum"))
    }

    @Test
    fun `a truncated or foreign file is refused`() {
        assertTrue(Backup.parse("{\"format\":\"x\"").isFailure)
        assertTrue(Backup.parse("{\"format\":\"other\",\"checksumVersion\":2,\"checksum\":\"\",\"profile\":{}}").isFailure)
    }

    @Test
    fun `a file over two mebibytes is refused before parsing`() {
        val big = "a".repeat(Backup.MAX_BYTES + 1)
        assertTrue(Backup.parse(big).isFailure)
    }

    @Test
    fun `achievements follow the profile`() {
        val p = sample().copy(totals = Totals(quads = 1, maxCombo = 5))
        val found = Achievements.of(p, 50) { "floor-%04d".format(it) }
        assertTrue(Achievements.FIRST_WIN in found)
        assertTrue(Achievements.FIRST_QUAD in found)
        assertTrue(Achievements.COMBO_FIVE in found)
        assertTrue(Achievements.FIRST_DISTRICT !in found)
    }
}
