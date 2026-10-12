package pt.tiago.blocknight.data

import android.content.Context
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import pt.tiago.blocknight.core.content.Compile
import pt.tiago.blocknight.core.content.Districts
import pt.tiago.blocknight.core.content.FloorFile
import pt.tiago.blocknight.core.content.VariantSpec
import pt.tiago.blocknight.core.model.Rules

/**
 * Catálogo empacotado no APK (assets/content/levels.json). Só leitura. A conversão para [Rules] usa o
 * mesmo código do replay, por isso o jogo e a certificação não podem divergir.
 */
class ContentRepository private constructor(private val floors: Map<Int, FloorFile>) {

    val size: Int get() = floors.size

    /** Andares jogáveis em sequência, a partir do 1: a campanha é linear, por isso um andar em falta corta o caminho. */
    val playableCount: Int by lazy {
        var n = 0
        while (floors.containsKey(n + 1)) n++
        n
    }

    fun isAvailable(ordinal: Int): Boolean = floors.containsKey(ordinal)

    fun floor(ordinal: Int): FloorFile? = floors[ordinal]

    fun variant(ordinal: Int, difficulty: String): VariantSpec? = floors[ordinal]?.variants?.get(difficulty)

    fun rules(ordinal: Int, difficulty: String): Rules {
        val floor = requireNotNull(floors[ordinal]) { "andar $ordinal não existe" }
        return Compile.rules(floor, difficulty, requireNotNull(floor.variants[difficulty]) { "variante $difficulty em falta" })
    }

    fun floorId(ordinal: Int): String = Districts.floorId(ordinal)

    companion object {
        private const val ASSET = "content/levels.json"

        fun load(context: Context): ContentRepository {
            val text = context.assets.open(ASSET).bufferedReader().use { it.readText() }
            val list = Json.decodeFromString(ListSerializer(FloorFile.serializer()), text)
            return ContentRepository(list.associateBy { it.ordinal })
        }

        /** Para testes e ferramentas que já têm os andares em memória. */
        fun of(list: List<FloorFile>): ContentRepository = ContentRepository(list.associateBy { it.ordinal })
    }
}
