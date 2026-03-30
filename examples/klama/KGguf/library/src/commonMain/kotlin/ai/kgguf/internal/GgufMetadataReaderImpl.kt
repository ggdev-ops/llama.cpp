package ai.kgguf.internal

import ai.kgguf.*
import okio.BufferedSource
import okio.FileSystem
import okio.Path

/**
 * Optimized GGUF parser using Okio for unified multi-platform I/O.
 */
class GgufMetadataReaderImpl(
    private val skipKeys: Set<String>,
    private val arraySummariseThreshold: Int,
) : GgufMetadataReader {
    
    companion object {
        private const val ARCH_LLAMA = "llama"
        private val GGUF_MAGIC = byteArrayOf(0x47, 0x47, 0x55, 0x46) // "GGUF"
    }

    enum class MetadataType(val code: Int) {
        UINT8(0), INT8(1), UINT16(2), INT16(3),
        UINT32(4), INT32(5), FLOAT32(6), BOOL(7),
        STRING(8), ARRAY(9), UINT64(10), INT64(11), FLOAT64(12);
        companion object {
            private val codeMap = entries.associateBy(MetadataType::code)
            fun fromCode(code: Int): MetadataType = codeMap[code]
                ?: throw KlamaIOException("Unknown metadata value type code: $code")
        }
    }

    sealed class MetadataValue {
        data class UInt8(val value: UByte) : MetadataValue()
        data class Int8(val value: Byte) : MetadataValue()
        data class UInt16(val value: UShort) : MetadataValue()
        data class Int16(val value: Short) : MetadataValue()
        data class UInt32(val value: UInt) : MetadataValue()
        data class Int32(val value: Int) : MetadataValue()
        data class Float32(val value: Float) : MetadataValue()
        data class Bool(val value: Boolean) : MetadataValue()
        data class StringVal(val value: String) : MetadataValue()
        data class ArrayVal(val elementType: MetadataType, val elements: List<MetadataValue>) : MetadataValue()
        data class UInt64(val value: ULong) : MetadataValue()
        data class Int64(val value: Long) : MetadataValue()
        data class Float64(val value: Double) : MetadataValue()
    }

    override suspend fun ensureSourceFileFormat(fileSystem: FileSystem, path: Path): Boolean {
        return try {
            fileSystem.read(path) {
                ensureSourceFileFormat(this)
            }
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun ensureSourceFileFormat(source: BufferedSource): Boolean {
        return try {
            val magic = source.peek().readByteArray(4L)
            magic.contentEquals(GGUF_MAGIC)
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun readStructuredMetadata(source: BufferedSource): GgufMetadata {
        // 1. Header
        val magic = try { source.readByteArray(4L) } catch (e: Exception) { throw KlamaIOException("Failed to read GGUF magic") }
        if (!magic.contentEquals(GGUF_MAGIC)) throw InvalidFileFormatException()
        
        val version = GgufMetadata.GgufVersion.fromCode(source.readIntLe())
        val tensorCount = source.readLongLe()
        val kvCount = source.readLongLe()

        // 2. Metadata Map
        val meta = mutableMapOf<String, MetadataValue>()
        repeat(kvCount.toInt()) {
            val key = readString(source)
            val valueType = MetadataType.fromCode(source.readIntLe())
            
            if (key in skipKeys) {
                skipValue(source, valueType)
            } else {
                meta[key] = parseValue(source, valueType)
            }
        }

        return buildStructured(meta, version, tensorCount, kvCount)
    }

    private fun parseValue(source: BufferedSource, type: MetadataType): MetadataValue = when (type) {
        MetadataType.UINT8 -> MetadataValue.UInt8(source.readByte().toUByte())
        MetadataType.INT8 -> MetadataValue.Int8(source.readByte())
        MetadataType.UINT16 -> MetadataValue.UInt16(source.readShortLe().toUShort())
        MetadataType.INT16 -> MetadataValue.Int16(source.readShortLe())
        MetadataType.UINT32 -> MetadataValue.UInt32(source.readIntLe().toUInt())
        MetadataType.INT32 -> MetadataValue.Int32(source.readIntLe())
        MetadataType.FLOAT32 -> MetadataValue.Float32(Float.fromBits(source.readIntLe()))
        MetadataType.BOOL -> MetadataValue.Bool(source.readByte() != 0.toByte())
        MetadataType.STRING -> MetadataValue.StringVal(readString(source))
        MetadataType.ARRAY -> {
            val elemType = MetadataType.fromCode(source.readIntLe())
            val count = source.readLongLe().toInt()

            if (arraySummariseThreshold >= 0 && count > arraySummariseThreshold) {
                repeat(count) { skipValue(source, elemType) }
                MetadataValue.StringVal("Array($elemType, $count items) /* summarised */")
            } else {
                val list = ArrayList<MetadataValue>(count)
                repeat(count) { list += parseValue(source, elemType) }
                MetadataValue.ArrayVal(elemType, list)
            }
        }
        MetadataType.UINT64 -> MetadataValue.UInt64(source.readLongLe().toULong())
        MetadataType.INT64 -> MetadataValue.Int64(source.readLongLe())
        MetadataType.FLOAT64 -> MetadataValue.Float64(Double.fromBits(source.readLongLe()))
    }

    private fun readString(source: BufferedSource): String {
        val len = source.readLongLe()
        return source.readUtf8(len)
    }

    private fun skipValue(source: BufferedSource, type: MetadataType) {
        when (type) {
            MetadataType.UINT8, MetadataType.INT8, MetadataType.BOOL -> source.skip(1)
            MetadataType.UINT16, MetadataType.INT16 -> source.skip(2)
            MetadataType.UINT32, MetadataType.INT32, MetadataType.FLOAT32 -> source.skip(4)
            MetadataType.UINT64, MetadataType.INT64, MetadataType.FLOAT64 -> source.skip(8)
            MetadataType.STRING -> {
                val len = source.readLongLe()
                source.skip(len)
            }
            MetadataType.ARRAY -> {
                val elemType = MetadataType.fromCode(source.readIntLe())
                val count = source.readLongLe()
                repeat(count.toInt()) { skipValue(source, elemType) }
            }
        }
    }

    private fun buildStructured(
        m: Map<String, MetadataValue>,
        version: GgufMetadata.GgufVersion,
        tensorCnt: Long,
        kvCnt: Long
    ): GgufMetadata {
        fun String.str()  = (m[this] as? MetadataValue.StringVal)?.value
        fun String.u32()  = (m[this] as? MetadataValue.UInt32)?.value?.toInt()
        fun String.f32()  = (m[this] as? MetadataValue.Float32)?.value
        
        val arch = "general.architecture".str() ?: ARCH_LLAMA

        // Clean raw map
        val rawValues = m.mapValues { (_, value) -> 
            when (value) {
                is MetadataValue.StringVal -> value.value
                is MetadataValue.Bool -> value.value
                is MetadataValue.Int32 -> value.value
                is MetadataValue.UInt32 -> value.value
                is MetadataValue.Float32 -> value.value
                is MetadataValue.Int64 -> value.value
                is MetadataValue.UInt64 -> value.value
                else -> value.toString()
            }
        }

        return GgufMetadata(
            version = version,
            tensorCount = tensorCnt,
            kvCount = kvCnt,
            raw = rawValues,
            basic = GgufMetadata.BasicInfo(
                uuid      = "general.uuid".str(),
                name      = "general.basename".str(),
                nameLabel = "general.name".str(),
                sizeLabel = "general.size_label".str()
            ),
            author = GgufMetadata.AuthorInfo(
                organization = "general.organization".str(),
                author       = "general.author".str(),
                license      = "general.license".str(),
                url          = "general.url".str(),
                repoUrl      = "general.repository_url".str()
            ).takeUnless { it.author == null && it.license == null },
            tokenizer = GgufMetadata.TokenizerInfo(
                model           = "tokenizer.ggml.model".str(),
                chatTemplate    = "tokenizer.chat_template".str()?.takeUnless { it.isEmpty() }
            ),
            architecture = GgufMetadata.ArchitectureInfo(
                architecture        = arch,
                fileType            = "general.file_type".u32(),
                vocabSize           = "$arch.vocab_size".u32(),
                finetune            = "general.finetune".str()
            ).takeUnless { it.fileType == null && it.vocabSize == null },
            dimensions = GgufMetadata.DimensionsInfo(
                contextLength    = "$arch.context_length".u32(),
                embeddingSize    = "$arch.embedding_length".u32(),
                blockCount       = "$arch.block_count".u32(),
                feedForwardSize  = "$arch.feed_forward_length".u32()
            ).takeUnless { it.contextLength == null && it.embeddingSize == null },
            attention = GgufMetadata.AttentionInfo(
                headCount           = "$arch.attention.head_count".u32(),
                headCountKv         = "$arch.attention.head_count_kv".u32(),
                layerNormRmsEpsilon = "$arch.attention.layer_norm_rms_epsilon".f32()
            ).takeUnless { it.headCount == null && it.layerNormRmsEpsilon == null },
            rope = GgufMetadata.RopeInfo(
                frequencyBase = "$arch.rope.freq_base".f32(),
                dimensionCount = "$arch.rope.dimension_count".u32()
            ).takeUnless { it.frequencyBase == null && it.dimensionCount == null },
            experts = GgufMetadata.ExpertsInfo(
                count = (m["$arch.expert_count"] as? MetadataValue.UInt32)?.value?.toInt(),
                usedCount = (m["$arch.expert_used_count"] as? MetadataValue.UInt32)?.value?.toInt()
            ).takeUnless { it.count == null }
        )
    }

    private fun <T> T?.takeUnless(check: (T) -> Boolean): T? = if (this != null && !check(this)) this else null
}
