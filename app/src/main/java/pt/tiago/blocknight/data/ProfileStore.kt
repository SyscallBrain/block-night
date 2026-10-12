package pt.tiago.blocknight.data

import java.io.File
import java.io.IOException
import kotlinx.serialization.json.Json
import pt.tiago.blocknight.core.progress.Profile

/** Estado de leitura do perfil. Um ficheiro inválido nunca é substituído em silêncio (PLANO §10.1, R4). */
sealed interface ProfileLoad {
    data class Ok(val profile: Profile) : ProfileLoad
    data object Missing : ProfileLoad
    data class Unreadable(val preservedAt: String) : ProfileLoad
    data class FutureVersion(val version: Int) : ProfileLoad
}

/**
 * Perfil num ficheiro JSON com escrita atómica (ficheiro temporário e renomeação). As escritas são
 * serializadas por [lock], para que duas atualizações não se cruzem.
 */
class ProfileStore(private val file: File) {
    private val json = Json { prettyPrint = false; ignoreUnknownKeys = true; encodeDefaults = true }
    private val lock = Any()

    fun load(): ProfileLoad = synchronized(lock) {
        if (!file.exists()) return ProfileLoad.Missing
        return try {
            val profile = json.decodeFromString(Profile.serializer(), file.readText())
            when {
                profile.schemaVersion > Profile.SCHEMA -> ProfileLoad.FutureVersion(profile.schemaVersion)
                else -> ProfileLoad.Ok(profile)
            }
        } catch (e: Exception) {
            // Preserva o ficheiro original para recuperação manual; nunca o apaga.
            val preserved = File(file.parentFile, "${file.name}.unreadable-${System.currentTimeMillis()}")
            runCatching { file.copyTo(preserved, overwrite = false) }
            ProfileLoad.Unreadable(preserved.name)
        }
    }

    /** Copia o ficheiro atual para `.bak` antes de uma importação. Não faz nada se não existir. */
    fun backupCurrent() = synchronized(lock) {
        if (file.exists()) file.copyTo(File(file.parentFile, "${file.name}.bak"), overwrite = true)
    }

    fun save(profile: Profile) = synchronized(lock) {
        val temp = File(file.parentFile, "${file.name}.tmp")
        temp.writeText(json.encodeToString(Profile.serializer(), profile))
        if (!temp.renameTo(file)) {
            file.delete()
            if (!temp.renameTo(file)) throw IOException("não foi possível gravar o perfil")
        }
    }
}
