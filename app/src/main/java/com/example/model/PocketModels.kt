package com.example.model

enum class GgmlQuantType(
    val nativeCode: Int,
    val label: String,
    val bytesPerElement: Double,
    val description: String
) {
    F16(1, "f16", 2.0, "16-bit FP (0% KV savings, max precision)"),
    Q8_0(8, "q8_0", 1.0625, "8-bit Quant (~47% KV RAM reduction)"),
    Q4_0(2, "q4_0", 0.5625, "4-bit Quant (~72% KV RAM reduction)"),
    IQ2_XXS(16, "iq2_xxs", 0.2578, "2.06-bit Importance Matrix (~87% KV RAM reduction)");

    companion object {
        fun fromLabel(label: String): GgmlQuantType =
            entries.firstOrNull { it.label.equals(label, ignoreCase = true) } ?: Q8_0
    }
}

enum class RamTier(val title: String, val badgeColorHex: Long) {
    TIER_4GB_CONSTRAINED("4GB Constrained Profile", 0xFFF59E0B),
    TIER_6GB_STANDARD("6GB Balanced Profile", 0xFF06B6D4),
    TIER_8GB_PLUS("8GB+ High-Capacity Profile", 0xFF10B981)
}

data class HardwareProfile(
    val totalRamMb: Long,
    val availableRamMb: Long,
    val lowMemoryWarning: Boolean,
    val thresholdMb: Long,
    val totalCpuCores: Int,
    val bigCoresCount: Int,
    val socSummary: String,
    val tier: RamTier,
    val recommendedModelId: String,
    val recommendedContextSize: Int,
    val recommendedTypeK: GgmlQuantType,
    val recommendedTypeV: GgmlQuantType,
    val recommendedUbatch: Int
)

data class EngineConfig(
    val activeModelId: String = "Qwen2.5-Coder-1.5B-Instruct-Q4_K_M.gguf",
    val activeModelPath: String = "",
    val useMmap: Boolean = true,
    val useMlock: Boolean = false,
    val flashAttn: Boolean = true,
    val contextSize: Int = 4096,
    val ubatchSize: Int = 256,
    val threads: Int = 4,
    val gpuLayers: Int = 0,
    val typeK: GgmlQuantType = GgmlQuantType.Q8_0,
    val typeV: GgmlQuantType = GgmlQuantType.Q4_0,
    val enablePld: Boolean = true,
    val pldNgramLen: Int = 3,
    val pldMaxDraftTokens: Int = 6,
    val pinSystemPrefix: Boolean = true,
    val enableHarnessDiet: Boolean = true,
    val enableGbnfToolGrammar: Boolean = true,
    val enableHybridHighThinking: Boolean = true,
    // Next-Gen Edge AI Optimizations for 4GB/6GB RAM Smartphones:
    val enableLayerStreaming: Boolean = true,
    val activeLayerWindow: Int = 6,
    val enableSelfSpeculativeLayerSkip: Boolean = true,
    val enableAstCodeSkeletonizer: Boolean = true,
    val enableDiskKvSessionCache: Boolean = true,
    val pinBigCpuCores: Boolean = true
)

data class GgufModelDescriptor(
    val id: String,
    val displayName: String,
    val parameterCount: String,
    val quantization: String,
    val fileName: String,
    val huggingFaceUrl: String,
    val expectedSizeMb: Int,
    val minRamGb: Int,
    val architecture: String,
    val supportsFim: Boolean = true,
    val supportsTools: Boolean = true,
    val isMicroSynthesized: Boolean = false,
    val highlightTag: String = "EDGE CODER"
)

enum class DownloadStatus {
    NOT_DOWNLOADED,
    DOWNLOADING,
    PAUSED,
    READY,
    ERROR
}

data class ModelDownloadState(
    val descriptor: GgufModelDescriptor,
    val status: DownloadStatus = DownloadStatus.NOT_DOWNLOADED,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = descriptor.expectedSizeMb * 1024L * 1024L,
    val localFilePath: String? = null,
    val speedKbps: Long = 0L,
    val errorMessage: String? = null
) {
    val progressFraction: Float
        get() = if (totalBytes > 0L) (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
}

data class GgufParsedHeader(
    val magicValid: Boolean,
    val version: Int,
    val tensorCount: Long,
    val metadataKvCount: Long,
    val architecture: String,
    val modelName: String,
    val contextLength: Int,
    val embeddingLength: Int,
    val blockCount: Int,
    val kvHeadCount: Int,
    val fileSizeBytes: Long,
    val isZeroCopyMmap: Boolean
)

data class EngineRuntimeMetrics(
    val isModelLoaded: Boolean = false,
    val nativeJniLinked: Boolean = false,
    val ggufHeader: GgufParsedHeader? = null,
    val mmapBytes: Long = 0L,
    val residentRssCapBytes: Long = 0L,
    val kvCacheBytes: Long = 0L,
    val kvCacheSavedBytes: Long = 0L,
    val kvCacheReductionPercent: Int = 60,
    val kvTokensUsed: Int = 0,
    val kvTokensCapacity: Int = 4096,
    val pinnedPrefixTokens: Int = 0,
    val kvCacheHitRatioPercent: Int = 0,
    val diskKvSnapshotSaved: Boolean = false,
    val lastPrefillTokPerSec: Double = 0.0,
    val lastDecodeTokPerSec: Double = 0.0,
    val pldDraftedTokens: Int = 0,
    val pldAcceptedTokens: Int = 0,
    val pldSpeedupMultiplier: Double = 1.0,
    val oomTrimEventsCount: Int = 0,
    val lastOomTrimSummary: String? = null
)

data class BenchmarkReport(
    val modelId: String,
    val baselinePrefillTps: Double,
    val optimizedPrefillTps: Double,
    val baselineDecodeTps: Double,
    val optimizedDecodeTps: Double,
    val baselineRamMb: Int,
    val optimizedRamMb: Int,
    val dietTokenReductionPct: Int,
    val totalSpeedupMultiplier: Double,
    val activeOptimizationsSummary: String
)

data class ApiKeyRecord(
    val id: String,
    val key: String,
    val label: String,
    val createdAtEpochMs: Long,
    val requestCount: Long = 0L
) {
    val maskedKey: String
        get() = if (key.length > 16) {
            "${key.take(13)}••••••••${key.takeLast(4)}"
        } else key
}

enum class ExecutionRoute(val badge: String) {
    NATIVE_GGUF_MMAP("LOCAL GGUF (mmap + PLD + LayerSkip)"),
    HYBRID_GEMINI_HIGH_THINKING("HYBRID GEMINI-4-ARGON (HIGH THINKING)")
}

data class TelemetryLogEntry(
    val id: String,
    val timestampMs: Long,
    val method: String,
    val endpoint: String,
    val clientAddress: String,
    val requestedModel: String,
    val route: ExecutionRoute,
    val rawPromptTokens: Int,
    val compressedPromptTokens: Int,
    val tokensSavedByDiet: Int,
    val slidingWindowTruncatedTools: Int,
    val gbnfGrammarApplied: Boolean,
    val gbnfRuleSummary: String?,
    val kvPrefixHitTokens: Int,
    val pldDraftedCount: Int,
    val pldAcceptedCount: Int,
    val prefillTokPerSec: Double,
    val decodeTokPerSec: Double,
    val statusCode: Int,
    val latencyMs: Long,
    val previewSnippet: String
)

data class ChatMessage(
    val role: String,
    val content: String,
    val name: String? = null,
    val toolCallId: String? = null,
    val toolCallsJson: String? = null
)

data class ToolFunctionSpec(
    val name: String,
    val description: String,
    val parametersSchemaJson: String,
    val requiredFields: List<String> = emptyList(),
    val properties: Map<String, ToolPropertySpec> = emptyMap()
)

data class ToolPropertySpec(
    val name: String,
    val type: String,
    val description: String = "",
    val enumValues: List<String> = emptyList()
)

data class HarnessDietResult(
    val originalMessages: List<ChatMessage>,
    val optimizedMessages: List<ChatMessage>,
    val originalTokenEstimate: Int,
    val optimizedTokenEstimate: Int,
    val tokensSaved: Int,
    val systemPromptCompressed: Boolean,
    val astSkeletonizedBlocksCount: Int = 0,
    val truncatedToolMessagesCount: Int,
    val preservedFilePaths: List<String>,
    val compiledGbnfGrammar: String?,
    val gbnfRootSummary: String?
)
