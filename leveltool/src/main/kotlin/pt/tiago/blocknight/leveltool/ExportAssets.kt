package pt.tiago.blocknight.leveltool

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import pt.tiago.blocknight.core.content.FloorFile
import pt.tiago.blocknight.core.content.Validation
import java.io.File

/**
 * Exporta o catálogo validado para os assets do APK (PLANO §8). Usa o mesmo formato de andar do conteúdo,
 * sem métricas nem certificados, para que o jogo converta as variantes com o mesmo código do replay.
 * Nunca escreve em `content/`.
 */
object ExportAssets {
    private val json = Json { prettyPrint = false; encodeDefaults = true; explicitNulls = false }

    fun write(store: ContentStore, outputDir: File): Int {
        val catalog = store.readCatalog()
        val errors = ArrayList<String>()
        val floors: List<FloorFile> = catalog.entries.map { entry ->
            val floor = store.readFloor(entry.path)
            errors += Validation.floor(floor)
            floor.copy(variants = floor.variants.mapValues { (_, v) -> v.copy(expectedMetrics = null) })
        }
        if (errors.isNotEmpty()) {
            errors.forEach { System.err.println("ERRO $it") }
            return 1
        }
        outputDir.mkdirs()
        File(outputDir, "levels.json").writeText(json.encodeToString(ListSerializer(FloorFile.serializer()), floors))
        File(outputDir, "catalog.json").writeText(store.catalogFile.readText())
        println("exportados ${floors.size} andares para ${outputDir.path}")
        return 0
    }
}
