package pt.tiago.blocknight.data

import java.io.File
import java.io.IOException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import pt.tiago.blocknight.core.model.GameSnapshot

/** Sessão de campanha guardada: andar, dificuldade e instantâneo do estado (PLANO §7.5). */
@Serializable
data class CampaignSession(
    val sessionId: String,
    val ordinal: Int,
    val difficulty: String,
    val snapshot: GameSnapshot,
    /** Verdadeiro para o desafio diário, que partilha este ficheiro mas conta à parte. */
    val daily: Boolean = false,
)

/** Uma sessão por vez; gravada na pausa, ao bloquear peças e ao sair. Apagada depois de o resultado ficar gravado. */
class CampaignStore(private val file: File) {
    private val json = Json { prettyPrint = false; ignoreUnknownKeys = true }
    private val lock = Any()

    fun read(): CampaignSession? = synchronized(lock) {
        if (!file.exists()) return null
        return runCatching { json.decodeFromString(CampaignSession.serializer(), file.readText()) }.getOrNull()
    }

    fun write(session: CampaignSession) = synchronized(lock) {
        val temp = File(file.parentFile, "${file.name}.tmp")
        temp.writeText(json.encodeToString(CampaignSession.serializer(), session))
        if (!temp.renameTo(file)) {
            file.delete()
            if (!temp.renameTo(file)) throw IOException("não foi possível gravar a sessão")
        }
    }

    fun clear() = synchronized(lock) { file.delete() }
}
