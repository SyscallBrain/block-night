package pt.tiago.blocknight.core.progress

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import pt.tiago.blocknight.core.content.Canonical

/**
 * Cópia do progresso (PLANO §10.3). O checksum (versão 2) é o SHA-256 do JSON canónico do perfil, calculado
 * antes de qualquer migração, para que uma cópia antiga continue a ser aceite (lição R1).
 */
object Backup {
    const val FORMAT = "blocknight-backup"
    const val CHECKSUM_VERSION = 2
    const val MAX_BYTES = 2 * 1024 * 1024

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = false; encodeDefaults = true }

    fun export(profile: Profile): String {
        val profileJson = json.encodeToJsonElement(Profile.serializer(), profile)
        val checksum = Canonical.sha256(Canonical.write(profileJson))
        val wrapper = JsonObject(
            mapOf(
                "format" to JsonPrimitive(FORMAT),
                "checksumVersion" to JsonPrimitive(CHECKSUM_VERSION),
                "checksum" to JsonPrimitive(checksum),
                "profile" to profileJson,
            ),
        )
        return json.encodeToString(JsonElement.serializer(), wrapper)
    }

    /** Valida tamanho, formato, checksum e schema. Não altera nada: quem chama decide se substitui o perfil. */
    fun parse(text: String): Result<Profile> = runCatching {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "ficheiro maior que 2 MiB" }
        val root = Json.parseToJsonElement(text) as JsonObject
        require(root["format"]?.let { (it as JsonPrimitive).content } == FORMAT) { "não é uma cópia do Block Night" }
        val version = (root["checksumVersion"] as JsonPrimitive).content.toInt()
        require(version == CHECKSUM_VERSION) { "versão de checksum $version não suportada" }
        val profileJson = root["profile"] ?: error("falta o perfil")
        val expected = (root["checksum"] as JsonPrimitive).content
        require(Canonical.sha256(Canonical.write(profileJson)) == expected) { "checksum não coincide" }
        val profile = Json { ignoreUnknownKeys = true }.decodeFromJsonElement(Profile.serializer(), profileJson)
        require(profile.schemaVersion <= Profile.SCHEMA) { "cópia de uma versão mais recente" }
        profile
    }
}
