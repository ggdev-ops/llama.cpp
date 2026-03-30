package ai.kgguf

/**
 * Pure, low-level GGUF metadata structure.
 * This class simply represents what is physically in the file.
 */
data class GgufMetadata(
    val version: GgufVersion,
    val tensorCount: Long,
    val kvCount: Long,
    val basic: BasicInfo,
    val author: AuthorInfo? = null,
    val architecture: ArchitectureInfo? = null,
    val tokenizer: TokenizerInfo? = null,
    val dimensions: DimensionsInfo? = null,
    val attention: AttentionInfo? = null,
    val rope: RopeInfo? = null,
    val experts: ExpertsInfo? = null,
    val raw: Map<String, Any> = emptyMap()
) {
    enum class GgufVersion(val code: Int, val label: String) {
        LEGACY_V1(1, "Legacy v1"),
        EXTENDED_V2(2, "Extended v2"),
        VALIDATED_V3(3, "Validated v3");

        companion object {
            fun fromCode(code: Int): GgufVersion =
                entries.firstOrNull { it.code == code }
                    ?: throw Exception("Unknown GGUF version code $code")
        }
        override fun toString(): String = "$label (code=$code)"
    }

    data class BasicInfo(
        val uuid: String? = null,
        val name: String? = null,
        val nameLabel: String? = null,
        val sizeLabel: String? = null,
    )

    data class AuthorInfo(
        val organization: String? = null,
        val author: String? = null,
        val license: String? = null,
        val url: String? = null,
        val repoUrl: String? = null
    )

    data class ArchitectureInfo(
        val architecture: String? = null,
        val fileType: Int? = null,
        val vocabSize: Int? = null,
        val finetune: String? = null,
    )

    data class TokenizerInfo(
        val model: String? = null,
        val chatTemplate: String? = null,
    )

    data class DimensionsInfo(
        val contextLength: Int? = null,
        val embeddingSize: Int? = null,
        val blockCount: Int? = null,
        val feedForwardSize: Int? = null,
    )

    data class AttentionInfo(
        val headCount: Int? = null,
        val headCountKv: Int? = null,
        val layerNormRmsEpsilon: Float? = null,
    )

    data class RopeInfo(
        val frequencyBase: Float? = null,
        val dimensionCount: Int? = null,
    )

    data class ExpertsInfo(
        val count: Int? = null,
        val usedCount: Int? = null,
    )
}
