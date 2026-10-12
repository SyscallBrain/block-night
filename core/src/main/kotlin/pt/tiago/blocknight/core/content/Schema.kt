package pt.tiago.blocknight.core.content

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** Formato dos ficheiros de conteúdo (CONTEUDO §1–§4). Os campos são estáveis: mudá-los obriga a revalidar. */
const val SCHEMA_VERSION = 1
const val ENGINE_VERSION = 1

@Serializable
data class FloorFile(
    val id: String,
    val ordinal: Int,
    val districtId: Int,
    val familyId: String,
    val learningTags: List<String> = emptyList(),
    val revision: Int,
    val variants: Map<String, VariantSpec>,
    val titleNote: String? = null,
)

@Serializable
data class VariantSpec(
    val seedHex: String,
    val garbageSeedHex: String,
    val speedLevel: Int,
    val previewCount: Int,
    val allowHold: Boolean,
    val openingQueue: List<String> = emptyList(),
    val risingGarbage: RisingSpec? = null,
    val initialBoard: List<String>,
    val objective: ObjectiveSpec,
    val maxPieces: Int,
    val twoStarMaxPieces: Int,
    val threeStarMaxPieces: Int,
    val expectedMetrics: Map<String, JsonElement>? = null,
)

@Serializable
data class RisingSpec(val everyPieces: Int, val startAfter: Int)

/** `type` é lines | dig | targets | score | quads | tspins | combo. `n` ou `points` conforme o tipo. */
@Serializable
data class ObjectiveSpec(val type: String, val n: Int? = null, val points: Long? = null)

@Serializable
data class CatalogFile(
    val schemaVersion: Int,
    val engineVersion: Int,
    val contentVersion: String,
    val buildStage: String,
    val districts: List<DistrictSpec>,
    val entries: List<CatalogEntry>,
)

@Serializable
data class DistrictSpec(val id: Int, val name: String, val floorStart: Int, val floorEnd: Int)

@Serializable
data class CatalogEntry(
    val id: String,
    val ordinal: Int,
    val districtId: Int,
    val path: String,
    val revision: Int,
    val sha256: String,
)

@Serializable
data class CertificateFile(
    val floorId: String,
    val difficulty: String,
    val levelRevision: Int,
    val engineVersion: Int,
    val levelHash: String,
    val steps: List<StepSpec>,
    val result: ResultSpec,
    val solver: SolverSpec? = null,
)

@Serializable
data class StepSpec(
    val hold: Boolean,
    val piece: String,
    val path: String,
    val final: PositionSpec,
    val stateHash: String,
)

@Serializable
data class PositionSpec(val x: Int, val y: Int, val rot: Int)

@Serializable
data class ResultSpec(
    val pieces: Int,
    val won: Boolean,
    val linesCleared: Int,
    val score: Long,
    val finalStateHash: String,
)

@Serializable
data class SolverSpec(val beamWidth: Int, val expanded: Long, val ms: Long)

/** Dificuldades canónicas, pela ordem de escrita. */
val DIFFICULTIES = listOf("easy", "medium", "hard")
