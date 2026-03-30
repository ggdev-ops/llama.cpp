package ai.kgguf

/**
 * High-level model analyzer.
 * Wraps raw [GgufMetadata] to provide inferred capabilities and human-readable insights.
 */
class GgufModel(val metadata: GgufMetadata) {

    /**
     * Common GGUF Quantization formats.
     */
    enum class Quantization(val code: Int, val label: String) {
        F32(0, "F32"), F16(1, "F16"), Q4_0(2, "Q4_0"), Q4_1(3, "Q4_1"), 
        Q5_0(6, "Q5_0"), Q5_1(7, "Q5_1"), Q8_0(8, "Q8_0"), 
        Q3_K_M(12, "Q3_K_M"), Q4_K_M(15, "Q4_K_M"), Q6_K(18, "Q6_K");

        companion object {
            fun fromCode(code: Int): Quantization? = entries.firstOrNull { it.code == code }
        }
    }

    /**
     * Inferred model features.
     */
    data class Capabilities(
        val isInstruct: Boolean,
        val hasChatTemplate: Boolean,
        val hasToolSupport: Boolean,
        val hasJsonSupport: Boolean,
        val isMoE: Boolean,
        val isVision: Boolean,
        val contextLength: Int,
        val quantization: Quantization?,
        val parameterCount: String?
    )

    /**
     * Calculates the capabilities of this model by analyzing its metadata.
     */
    fun inferCapabilities(): Capabilities {
        val arch = metadata.architecture?.architecture ?: "llama"
        val chatTemplate = metadata.tokenizer?.chatTemplate ?: ""
        val nameLabel = metadata.basic.nameLabel ?: ""
        val sizeLabel = metadata.basic.sizeLabel ?: ""

        val hasToolSupport = chatTemplate.contains("tool_calls", ignoreCase = true) || 
                             chatTemplate.contains("available_tools", ignoreCase = true) ||
                             nameLabel.contains("Llama-3.1", ignoreCase = true) ||
                             nameLabel.contains("Llama-3.2", ignoreCase = true)

        val fileTypeCode = metadata.architecture?.fileType ?: -1
        val quantization = Quantization.fromCode(fileTypeCode)

        return Capabilities(
            isInstruct = metadata.architecture?.finetune?.contains("instruct", ignoreCase = true) == true || 
                         metadata.architecture?.finetune?.contains("it", ignoreCase = true) == true ||
                         chatTemplate.isNotEmpty(),
            hasChatTemplate = chatTemplate.isNotEmpty(),
            hasToolSupport = hasToolSupport,
            hasJsonSupport = hasToolSupport || chatTemplate.contains("json_schema", ignoreCase = true),
            isMoE = (metadata.experts?.count ?: 0) > 0,
            isVision = arch.contains("clip", ignoreCase = true),
            contextLength = metadata.dimensions?.contextLength ?: 0,
            quantization = quantization,
            parameterCount = sizeLabel.takeUnless { it.isEmpty() } ?: nameLabel.let { n ->
                val match = Regex("(\\d+[BbMm])").find(n)
                match?.groupValues?.get(1)?.uppercase()
            }
        )
    }

    override fun toString(): String {
        val cap = inferCapabilities()
        return "Model: ${metadata.basic.nameLabel ?: metadata.basic.name} " + 
               "(${cap.parameterCount ?: "??"}, ${cap.quantization?.label ?: "Unknown"}) " + 
               "| Context: ${cap.contextLength}"
    }
}
