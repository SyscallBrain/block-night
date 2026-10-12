package pt.tiago.blocknight.leveltool

import pt.tiago.blocknight.core.content.CertificateVerifier
import pt.tiago.blocknight.core.content.DIFFICULTIES
import pt.tiago.blocknight.core.content.Districts
import pt.tiago.blocknight.core.content.Validation
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.system.exitProcess

/** Opções de linha de comandos no formato `--chave valor` e sinalizadores `--flag`. */
private class Args(argv: Array<String>) {
    val command: String = argv.firstOrNull() ?: usage()
    private val values = HashMap<String, String>()
    private val flags = HashSet<String>()

    init {
        var i = 1
        while (i < argv.size) {
            val key = argv[i]
            require(key.startsWith("--")) { "argumento inesperado: $key" }
            val name = key.removePrefix("--")
            if (i + 1 < argv.size && !argv[i + 1].startsWith("--")) {
                values[name] = argv[i + 1]
                i += 2
            } else {
                flags += name
                i++
            }
        }
    }

    fun string(name: String, default: String? = null): String =
        values[name] ?: default ?: throw IllegalArgumentException("falta --$name")

    fun int(name: String, default: Int? = null): Int =
        values[name]?.toInt() ?: default ?: throw IllegalArgumentException("falta --$name")

    fun flag(name: String): Boolean = name in flags
}

private fun usage(): Nothing {
    System.err.println(
        """
        leveltool — geração e certificação de andares de Block Night
          build --root content [--district N] [--from F --to T] [--only a,b,c] [--jobs J] [--attempts A] [--budget N] [--stage S]
          validate --root content [--strict]
          verify-certificates --root content
          rebalance --root content [--district N]
          report --root content --output docs/evidence/report.md
          export-assets --root content --output app/build/generated/blockNightAssets/content
        """.trimIndent(),
    )
    exitProcess(2)
}

fun main(argv: Array<String>) {
    val args = Args(argv)
    val store = ContentStore(File(args.string("root", "content")))
    val code = when (args.command) {
        "build" -> build(args, store)
        "validate" -> validate(args, store)
        "verify-certificates" -> verifyCertificates(store)
        "rebalance" -> rebalance(args, store)
        "report" -> Report.write(store, File(args.string("output")))
        "export-assets" -> ExportAssets.write(store, File(args.string("output")))
        else -> usage()
    }
    exitProcess(code)
}

private fun ordinalRange(args: Args): IntRange {
    // A lista explícita é tratada à parte em build().
    if (args.string("district", "").isNotEmpty()) {
        val d = args.int("district")
        require(d in 1..Districts.COUNT) { "bairro fora de 1..${Districts.COUNT}" }
        val first = (d - 1) * Districts.FLOORS_PER_DISTRICT + 1
        return first until first + Districts.FLOORS_PER_DISTRICT
    }
    val from = args.int("from", 1)
    val to = args.int("to", Districts.TOTAL_FLOORS)
    return from..to
}

private fun build(args: Args, store: ContentStore): Int {
    val only = args.string("only", "").takeIf { it.isNotEmpty() }?.split(",")?.map { it.trim().toInt() }
    val ordinals = only ?: ordinalRange(args).toList()
    val jobs = args.int("jobs", 1).coerceIn(1, 4)
    val attempts = args.int("attempts", 6)
    val generator = FloorGenerator(
        attemptsPerDifficulty = attempts,
        budget = args.int("budget", BeamSolver.DEFAULT_BUDGET.toInt()).toLong(),
        forbidTucks = !args.flag("no-forbid"),
        hardSpeedMin = args.flag("hard-speed-min"),
    )
    val pool = Executors.newFixedThreadPool(jobs)
    val started = System.currentTimeMillis()
    var built = 0
    val failed = ArrayList<String>()
    try {
        val futures = ordinals.map { ordinal -> pool.submit(Callable { ordinal to generator.build(ordinal) }) }
        for (future in futures) {
            val (ordinal, result) = future.get()
            val floor = result.floor
            if (floor == null) {
                failed += Districts.floorId(ordinal)
                println("FALHA ${Districts.floorId(ordinal)}: ${result.failures.joinToString(" | ")}")
                continue
            }
            store.writeFloor(floor)
            for (difficulty in DIFFICULTIES) store.writeCertificate(result.certificates.getValue(difficulty))
            built++
            val s = DIFFICULTIES.joinToString(" ") { d -> "$d=S${result.certificates.getValue(d).steps.size}" }
            println("OK ${floor.id} ${floor.variants.values.first().objective.type} $s")
        }
    } finally {
        pool.shutdown()
    }
    val existing = (1..Districts.TOTAL_FLOORS).filter { File(store.root, "levels/" + store.floorPath(it)).exists() }
    val stage = args.string("stage", stageFor(existing.size))
    store.writeCatalog(store.catalogFor(stage, existing))
    val seconds = (System.currentTimeMillis() - started) / 1000
    println("construídos=$built falhados=${failed.size} no catálogo=${existing.size} estágio=$stage em ${seconds}s")
    return if (failed.isEmpty()) 0 else 1
}

private fun stageFor(count: Int): String = when {
    count >= Districts.TOTAL_FLOORS -> "full"
    count > Districts.FLOORS_PER_DISTRICT -> "expanding"
    count >= Districts.FLOORS_PER_DISTRICT -> "pilot"
    else -> "slice"
}

private fun validate(args: Args, store: ContentStore): Int {
    val catalog = store.readCatalog()
    val errors = ArrayList<String>()
    val seen = HashMap<String, String>()
    for (entry in catalog.entries) {
        val file = File(store.root, "levels/${entry.path}")
        if (!file.exists()) {
            errors += "${entry.id}: ficheiro em falta ${entry.path}"
            continue
        }
        if (store.sha256(file) != entry.sha256) errors += "${entry.id}: sha256 não coincide"
        val floor = store.readFloor(entry.path)
        errors += Validation.floor(floor)
        for (difficulty in DIFFICULTIES) {
            val variant = floor.variants[difficulty] ?: continue
            val sig = Signature.of(floor, variant)
            val mirror = Signature.mirrored(variant)
            val prior = seen[sig] ?: seen[mirror]
            if (prior != null) errors += "${floor.id}/$difficulty: duplicado de $prior"
            seen.putIfAbsent(sig, "${floor.id}/$difficulty")
            if (store.readCertificate(floor.id, difficulty) == null) errors += "${floor.id}/$difficulty: sem certificado"
        }
    }
    if (args.flag("strict")) {
        val expectedFloors = when (catalog.buildStage) {
            "slice" -> 10
            "pilot" -> 50
            "full" -> Districts.TOTAL_FLOORS
            else -> catalog.entries.size
        }
        if (catalog.entries.size != expectedFloors) errors += "estágio ${catalog.buildStage}: ${catalog.entries.size} andares, esperado $expectedFloors"
    }
    errors.forEach { println("ERRO $it") }
    println("andares=${catalog.entries.size} estágio=${catalog.buildStage} erros=${errors.size}")
    return if (errors.isEmpty()) 0 else 1
}

private fun verifyCertificates(store: ContentStore): Int {
    val catalog = store.readCatalog()
    var checked = 0
    val failures = ArrayList<String>()
    for (entry in catalog.entries) {
        val floor = store.readFloor(entry.path)
        for (difficulty in DIFFICULTIES) {
            val variant = floor.variants[difficulty] ?: continue
            val cert = store.readCertificate(floor.id, difficulty)
            if (cert == null) {
                failures += "${floor.id}/$difficulty: sem certificado"
                continue
            }
            checked++
            val errors = CertificateVerifier.verify(floor, difficulty, variant, cert)
            if (errors.isNotEmpty()) failures += "${floor.id}/$difficulty: ${errors.joinToString("; ")}"
        }
    }
    failures.forEach { println("FALHA $it") }
    println("certificados verificados=$checked falhas=${failures.size}")
    return if (failures.isEmpty()) 0 else 1
}

private fun rebalance(args: Args, store: ContentStore): Int {
    val catalog = store.readCatalog()
    val only = if (args.string("district", "").isNotEmpty()) args.int("district") else null
    var changed = 0
    for (entry in catalog.entries) {
        if (only != null && entry.districtId != only) continue
        var floor = store.readFloor(entry.path)
        for (difficulty in DIFFICULTIES) {
            val cert = store.readCertificate(floor.id, difficulty) ?: continue
            val s = cert.steps.size
            val budget = Budget.of(difficulty, s)
            val variant = floor.variants.getValue(difficulty)
            val updated = variant.copy(maxPieces = budget.maxPieces, twoStarMaxPieces = budget.twoStar, threeStarMaxPieces = budget.threeStar)
            floor = floor.copy(variants = floor.variants + (difficulty to updated))
            val newCert = CertificateBuilder.rebuildHash(cert, floor, difficulty, updated)
            store.writeCertificate(newCert)
            changed++
        }
        store.writeFloor(floor)
    }
    val existing = catalog.entries.map { it.ordinal }
    store.writeCatalog(store.catalogFor(catalog.buildStage, existing))
    println("variantes reequilibradas=$changed")
    return 0
}
