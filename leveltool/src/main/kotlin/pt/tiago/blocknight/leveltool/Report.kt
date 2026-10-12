package pt.tiago.blocknight.leveltool

import pt.tiago.blocknight.core.content.DIFFICULTIES
import pt.tiago.blocknight.core.content.Districts
import java.io.File

/** Relatório em Markdown: contagens, S por dificuldade, exequibilidade e objetivos por bairro (CONTEUDO §§5–6). */
object Report {
    fun write(store: ContentStore, output: File): Int {
        val catalog = store.readCatalog()
        val lines = ArrayList<String>()
        lines += "# Relatório do catálogo"
        lines += ""
        lines += "- Estágio: `${catalog.buildStage}`; versão de conteúdo `${catalog.contentVersion}`; motor ${catalog.engineVersion}."
        lines += "- Andares: ${catalog.entries.size} de ${Districts.TOTAL_FLOORS}; variantes: ${catalog.entries.size * DIFFICULTIES.size}."
        lines += ""
        lines += "## Por bairro"
        lines += ""
        lines += "| Bairro | Andares | Objetivos |"
        lines += "|---|---|---|"
        for (district in Districts.ALL) {
            val floors = catalog.entries.filter { it.districtId == district.id }
            val kinds = floors.map { store.readFloor(it.path).variants.values.first().objective.type }
                .groupingBy { it }.eachCount().toSortedMap()
                .entries.joinToString(", ") { "${it.key} ${it.value}" }
            lines += "| ${district.id} ${district.name} | ${floors.size} | $kinds |"
        }
        lines += ""
        lines += "## Soluções e exequibilidade por dificuldade"
        lines += ""
        lines += "| Dificuldade | Certificados | S mín | S mediana | S máx | Passos máx | Rotação final média | Tempo/passo mín | Altura máx |"
        lines += "|---|---|---|---|---|---|---|---|---|"
        for (difficulty in DIFFICULTIES) {
            val measures = catalog.entries.mapNotNull { entry ->
                val floor = store.readFloor(entry.path)
                val cert = store.readCertificate(floor.id, difficulty) ?: return@mapNotNull null
                cert.steps.size to floor.variants.getValue(difficulty).expectedMetrics
            }
            if (measures.isEmpty()) {
                lines += "| $difficulty | 0 | — | — | — | — | — | — | — |"
                continue
            }
            val sizes = measures.map { it.first }.sorted()
            fun metric(key: String) = measures.mapNotNull { it.second?.get(key)?.toString()?.toDoubleOrNull() }
            val spins = metric("spinShare")
            val seconds = metric("meanSecondsPerStep")
            val steps = metric("maxSteps")
            val heights = metric("maxHeight")
            lines += "| $difficulty | ${measures.size} | ${sizes.first()} | ${sizes[sizes.size / 2]} | ${sizes.last()} | " +
                "${steps.maxOrNull()?.toInt() ?: "—"} | ${"%.3f".format(spins.average())} | ${"%.3f".format(seconds.minOrNull() ?: 0.0)} | " +
                "${heights.maxOrNull()?.toInt() ?: "—"} |"
        }
        lines += ""
        lines += "Duplicados: ver `leveltool validate` (falha se existir). Outliers: revisão manual, fora desta versão."
        output.parentFile?.mkdirs()
        output.writeText(lines.joinToString("\n") + "\n")
        println("relatório escrito em ${output.path}")
        return 0
    }
}
