package pt.tiago.blocknight.data

import java.io.File
import java.io.IOException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import pt.tiago.blocknight.core.model.GameSnapshot

/** Sessão de modo livre guardada. A semente fica no ficheiro, por isso a retoma é exata. */
@Serializable
data class FreeSession(
    val sessionId: String,
    val mode: String,
    val startLevel: Int,
    val seed: Long,
    val snapshot: GameSnapshot,
)

/** Uma sessão livre por vez, como a campanha. */
class FreeStore(private val file: File) {
    private val json = Json { prettyPrint = false; ignoreUnknownKeys = true }
    private val lock = Any()

    fun read(): FreeSession? = synchronized(lock) {
        if (!file.exists()) return null
        return runCatching { json.decodeFromString(FreeSession.serializer(), file.readText()) }.getOrNull()
    }

    fun write(session: FreeSession) = synchronized(lock) {
        val temp = File(file.parentFile, "${file.name}.tmp")
        temp.writeText(json.encodeToString(FreeSession.serializer(), session))
        if (!temp.renameTo(file)) {
            file.delete()
            if (!temp.renameTo(file)) throw IOException("não foi possível gravar a sessão livre")
        }
    }

    fun clear() = synchronized(lock) { file.delete() }
}
