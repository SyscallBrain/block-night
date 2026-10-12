package pt.tiago.blocknight.core.content

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.security.MessageDigest

/** JSON canónico: chaves ordenadas recursivamente, sem espaços. Base dos hashes de conteúdo. */
object Canonical {
    private val json = Json { prettyPrint = false; encodeDefaults = true }

    fun write(element: JsonElement): String = buildString { append(encode(element)) }

    fun encode(element: JsonElement): String = when (element) {
        is JsonObject -> element.toSortedMap().entries.joinToString(prefix = "{", postfix = "}", separator = ",") { (k, v) ->
            "\"${escape(k)}\":${encode(v)}"
        }
        is JsonArray -> element.joinToString(prefix = "[", postfix = "]", separator = ",") { encode(it) }
        is JsonPrimitive -> if (element.isString) "\"${escape(element.content)}\"" else element.content
    }

    fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    /** Hash de uma variante: exclui `expectedMetrics`, que é preenchido depois da certificação. */
    fun levelHash(floor: FloorFile, difficulty: String, variant: VariantSpec): String {
        val element = json.encodeToJsonElement(VariantSpec.serializer(), variant.copy(expectedMetrics = null))
        val wrapper = JsonObject(
            mapOf(
                "floorId" to JsonPrimitive(floor.id),
                "difficulty" to JsonPrimitive(difficulty),
                "revision" to JsonPrimitive(floor.revision),
                "variant" to element,
            ),
        )
        return sha256(write(wrapper))
    }

    private fun escape(text: String): String = text.replace("\\", "\\\\").replace("\"", "\\\"")
}
