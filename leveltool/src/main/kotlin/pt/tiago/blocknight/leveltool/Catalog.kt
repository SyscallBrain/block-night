package pt.tiago.blocknight.leveltool

import kotlinx.serialization.json.Json
import pt.tiago.blocknight.core.content.Canonical
import pt.tiago.blocknight.core.content.CatalogEntry
import pt.tiago.blocknight.core.content.CatalogFile
import pt.tiago.blocknight.core.content.CertificateFile
import pt.tiago.blocknight.core.content.DistrictSpec
import pt.tiago.blocknight.core.content.Districts
import pt.tiago.blocknight.core.content.ENGINE_VERSION
import pt.tiago.blocknight.core.content.FloorFile
import pt.tiago.blocknight.core.content.SCHEMA_VERSION
import java.io.File
import java.security.MessageDigest

/** Leitura e escrita do catálogo e dos ficheiros de andar e certificado (CONTEUDO §1). */
class ContentStore(val root: File) {
    private val json = Json { prettyPrint = true; encodeDefaults = true; explicitNulls = false }

    val catalogFile: File get() = File(root, "catalog.json")

    /** Caminho relativo a `content/levels` (CONTEUDO §1). */
    fun floorPath(ordinal: Int): String =
        "district-%02d/%s.json".format(Districts.districtOf(ordinal).id, Districts.floorId(ordinal))

    fun certificatePath(floorId: String, difficulty: String): String = "certificates/$floorId/$difficulty.json"

    fun readCatalog(): CatalogFile = json.decodeFromString(CatalogFile.serializer(), catalogFile.readText())

    fun writeCatalog(catalog: CatalogFile) {
        catalogFile.parentFile.mkdirs()
        catalogFile.writeText(json.encodeToString(CatalogFile.serializer(), catalog) + "\n")
    }

    fun readFloor(path: String): FloorFile = json.decodeFromString(FloorFile.serializer(), File(root, "levels/$path").readText())

    fun readCertificate(floorId: String, difficulty: String): CertificateFile? {
        val file = File(root, "certificates/$floorId/$difficulty.json")
        return if (file.exists()) json.decodeFromString(CertificateFile.serializer(), file.readText()) else null
    }

    fun writeFloor(floor: FloorFile): String {
        val path = floorPath(floor.ordinal)
        val file = File(root, "levels/$path")
        file.parentFile.mkdirs()
        file.writeText(json.encodeToString(FloorFile.serializer(), floor) + "\n")
        return path
    }

    fun writeCertificate(certificate: CertificateFile) {
        val file = File(root, certificatePath(certificate.floorId, certificate.difficulty))
        file.parentFile.mkdirs()
        file.writeText(json.encodeToString(CertificateFile.serializer(), certificate) + "\n")
    }

    fun sha256(file: File): String =
        MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }

    /** Catálogo novo com as entradas dos andares que existem em disco, ordenadas por ordinal. */
    fun catalogFor(stage: String, ordinals: Collection<Int>): CatalogFile {
        val entries = ordinals.sorted().map { ordinal ->
            val path = floorPath(ordinal)
            val file = File(root, "levels/$path")
            val floor = json.decodeFromString(FloorFile.serializer(), file.readText())
            CatalogEntry(
                id = floor.id,
                ordinal = ordinal,
                districtId = floor.districtId,
                path = path,
                revision = floor.revision,
                sha256 = sha256(file),
            )
        }
        return CatalogFile(
            schemaVersion = SCHEMA_VERSION,
            engineVersion = ENGINE_VERSION,
            contentVersion = "0.1.0",
            buildStage = stage,
            districts = Districts.ALL.map { d ->
                DistrictSpec(d.id, d.name, (d.id - 1) * Districts.FLOORS_PER_DISTRICT + 1, d.id * Districts.FLOORS_PER_DISTRICT)
            },
            entries = entries,
        )
    }

    fun canonicalText(floor: FloorFile): String = Canonical.write(json.encodeToJsonElement(FloorFile.serializer(), floor))
}
