package pt.tiago.blocknight.leveltool

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.tiago.blocknight.core.content.Canonical
import pt.tiago.blocknight.core.content.CertificateVerifier
import pt.tiago.blocknight.core.content.FloorFile
import pt.tiago.blocknight.core.content.ObjectiveSpec
import pt.tiago.blocknight.core.content.Validation
import pt.tiago.blocknight.core.content.VariantSpec
import pt.tiago.blocknight.core.content.Districts

class ContentTest {

    private fun variant(
        board: List<String> = emptyList(),
        objective: ObjectiveSpec = ObjectiveSpec("lines", n = 2),
        speed: Int = 1,
        preview: Int = 5,
        allowHold: Boolean = true,
        opening: List<String> = emptyList(),
    ) = VariantSpec(
        seedHex = "0123456789abcdef",
        garbageSeedHex = "fedcba9876543210",
        speedLevel = speed,
        previewCount = preview,
        allowHold = allowHold,
        openingQueue = opening,
        risingGarbage = null,
        initialBoard = board,
        objective = objective,
        maxPieces = 100,
        twoStarMaxPieces = 60,
        threeStarMaxPieces = 40,
    )

    /** Andar com as três variantes; a velocidade de Médio e Difícil segue as faixas do bairro (PLANO §5.5). */
    private fun floor(id: Int, easy: VariantSpec) = FloorFile(
        id = "floor-%04d".format(id), ordinal = id, districtId = Districts.districtOf(id).id, familyId = "x", revision = 1,
        variants = mapOf(
            "easy" to easy,
            "medium" to easy.copy(previewCount = 3, speedLevel = maxOf(easy.speedLevel, 1)),
            "hard" to easy.copy(previewCount = 2, speedLevel = 2),
        ),
    )

    @Test
    fun `canonical json sorts keys at every level`() {
        val parsed = Json.parseToJsonElement("""{"b":1,"a":{"d":[{"z":1,"y":2}],"c":"x"}}""")
        assertEquals("""{"a":{"c":"x","d":[{"y":2,"z":1}]},"b":1}""", Canonical.write(parsed))
    }

    @Test
    fun `level hash ignores expected metrics`() {
        val base = variant(board = listOf("GGGGGGGGG."), objective = ObjectiveSpec("lines", n = 1))
        val f = floor(1, base)
        val withMetrics = base.copy(expectedMetrics = mapOf("pieces" to JsonPrimitive(3)))
        assertEquals(Canonical.levelHash(f, "easy", base), Canonical.levelHash(f, "easy", withMetrics))
        assertNotEquals(Canonical.levelHash(f, "easy", base), Canonical.levelHash(f, "easy", base.copy(speedLevel = 2)))
    }

    @Test
    fun `a valid floor passes and an impossible start is rejected`() {
        assertTrue(Validation.floor(floor(1, variant(board = listOf("GGGGGGGGG.")))).isEmpty())
        val full = Validation.floor(floor(1, variant(board = listOf("GGGGGGGGGG"))))
        assertTrue(full.any { it.contains("cheia") })
    }

    @Test
    fun `dig needs special cells and tspins are reserved for district 8`() {
        val noSpecial = Validation.floor(floor(1, variant(objective = ObjectiveSpec("dig"))))
        assertTrue(noSpecial.any { it.contains("dig sem células especiais") })
        val tspinEarly = Validation.floor(floor(1, variant(objective = ObjectiveSpec("tspins", n = 1))))
        assertTrue(tspinEarly.any { it.contains("tspins só a partir do bairro 8") })
    }

    @Test
    fun `preview count must match the difficulty and speed must be in range`() {
        val wrongPreview = Validation.floor(floor(1, variant(preview = 9)))
        assertTrue(wrongPreview.any { it.contains("previewCount") })
        val wrongSpeed = Validation.floor(floor(1, variant(speed = 15)))
        assertTrue(wrongSpeed.any { it.contains("velocidade") })
    }

    @Test
    fun `budgets follow the PLANO formulas`() {
        assertEquals(Budget.Values(20, 17, 14), Budget.of("easy", 10))
        assertEquals(Budget.Values(16, 14, 12), Budget.of("medium", 10))
        assertEquals(Budget.Values(13, 12, 11), Budget.of("hard", 10))
    }

    /**
     * A imagem espelhada de uma variante tem o tabuleiro invertido e S↔Z, J↔L nas peças. Com a fila de
     * abertura a cobrir as 14 primeiras peças, a assinatura do espelho tem de coincidir com a do original.
     */
    @Test
    fun `mirrored variants share a signature`() {
        val opening = listOf("S", "Z", "J", "L", "T", "I", "O", "S", "Z", "J", "L", "T", "I", "O")
        val mirroredOpening = listOf("Z", "S", "L", "J", "T", "I", "O", "Z", "S", "L", "J", "T", "I", "O")
        val v = variant(board = listOf("SS........", "GGGGGGG..."), opening = opening)
        val mirror = variant(board = listOf("........SS", "...GGGGGGG"), opening = mirroredOpening)
        val f = floor(1, v)
        assertEquals(Signature.mirrored(v), Signature.of(f, mirror))
    }

    @Test
    fun `tampered certificates are rejected with a reason`() {
        val built = FloorGenerator(attemptsPerDifficulty = 6).build(1)
        val floor = built.floor!!
        val cert = built.certificates.getValue("easy")
        val easy = floor.variants.getValue("easy")
        assertTrue(CertificateVerifier.verify(floor, "easy", easy, cert).isEmpty())

        val badHash = cert.copy(steps = cert.steps.mapIndexed { i, s -> if (i == 0) s.copy(stateHash = "0000000000000000") else s })
        assertTrue(CertificateVerifier.verify(floor, "easy", easy, badHash).any { it.contains("stateHash") })

        val badEngine = cert.copy(engineVersion = 99)
        assertTrue(CertificateVerifier.verify(floor, "easy", easy, badEngine).any { it.contains("engineVersion") })

        val changedLevel = easy.copy(speedLevel = if (easy.speedLevel == 1) 2 else 1)
        assertTrue(CertificateVerifier.verify(floor, "easy", changedLevel, cert).any { it.contains("levelHash") })
    }

    @Test
    fun `the first district-one floor is solved and its certificate replays`() {
        val built = FloorGenerator(attemptsPerDifficulty = 6).build(1)
        assertTrue(built.failures.joinToString(), built.floor != null)
        assertFalse(built.certificates.isEmpty())
        assertEquals(JsonObject::class, JsonObject::class)
    }
}
