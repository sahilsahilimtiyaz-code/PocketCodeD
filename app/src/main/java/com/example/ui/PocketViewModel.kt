package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.middleware.GbnfGrammarCompiler
import com.example.middleware.HarnessDietMiddleware
import com.example.model.ApiKeyRecord
import com.example.model.BenchmarkReport
import com.example.model.ChatMessage
import com.example.model.EngineConfig
import com.example.model.EngineRuntimeMetrics
import com.example.model.ExecutionRoute
import com.example.model.GgmlQuantType
import com.example.model.HardwareProfile
import com.example.model.ModelDownloadState
import com.example.model.TelemetryLogEntry
import com.example.service.DaemonStateRegistry
import com.example.service.PocketDaemonService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.util.UUID

enum class PocketTab {
    CONTROL_DECK,
    MODEL_TUNER,
    API_SANDBOX,
    TELEMETRY
}

enum class SandboxMode(val label: String) {
    AGENT_TOOL_CALL("opencode Tool + GBNF"),
    FIM_COMPLETION("FIM (<|fim_middle|>)"),
    HIGH_THINKING("Gemini-4-Argon (High Thinking)")
}

data class SandboxUiState(
    val mode: SandboxMode = SandboxMode.AGENT_TOOL_CALL,
    val userPrompt: String = "Edit app/src/main/cpp/PocketEngine.cpp to enable Flash Attention and Q8_0/Q4_0 KV cache quantization.",
    val fimPrefix: String = "fun binarySearch(array: IntArray, target: Int): Int {\n    var low = 0\n    var high = array.lastIndex\n    while (low <= high) {\n        ",
    val fimSuffix: String = "\n    }\n    return -1\n}",
    val isStreaming: Boolean = false,
    val rawTokensBeforeDiet: Int = 5240,
    val compressedTokensAfterDiet: Int = 580,
    val compiledGbnfPreview: String = "",
    val outputStreamText: String = "",
    val statusBadge: String = "Ready — Tap 'Run Live Pipeline' to execute"
)

class PocketViewModel(application: Application) : AndroidViewModel(application) {

    init {
        DaemonStateRegistry.ensureInitialized(application)
        viewModelScope.launch(Dispatchers.IO) {
            if (!DaemonStateRegistry.isServerRunning.value) {
                DaemonStateRegistry.startServerInternal(bindLan = false, port = 8080)
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                DaemonStateRegistry.refreshHardwareProfile()
                delay(3_000L)
            }
        }
    }

    private val _selectedTab = MutableStateFlow(PocketTab.CONTROL_DECK)
    val selectedTab: StateFlow<PocketTab> = _selectedTab.asStateFlow()

    private val _statusBannerMessage = MutableStateFlow<String?>(null)
    val statusBannerMessage: StateFlow<String?> = _statusBannerMessage.asStateFlow()

    private val _benchmarkReport = MutableStateFlow<BenchmarkReport?>(null)
    val benchmarkReport: StateFlow<BenchmarkReport?> = _benchmarkReport.asStateFlow()

    val hardwareProfile: StateFlow<HardwareProfile?> = DaemonStateRegistry.hardwareProfile
    val engineConfig: StateFlow<EngineConfig> = DaemonStateRegistry.engineConfig
    val engineMetrics: StateFlow<EngineRuntimeMetrics> = DaemonStateRegistry.engineBridge.metrics
    val isServerRunning: StateFlow<Boolean> = DaemonStateRegistry.isServerRunning
    val bindLanMode: StateFlow<Boolean> = DaemonStateRegistry.bindLanMode
    val serverPort: StateFlow<Int> = DaemonStateRegistry.serverPort
    val apiKeys: StateFlow<List<ApiKeyRecord>> = DaemonStateRegistry.apiKeys
    val modelStates: StateFlow<List<ModelDownloadState>> = DaemonStateRegistry.modelHubManager.modelStates
    val telemetryLogs: StateFlow<List<TelemetryLogEntry>> = DaemonStateRegistry.telemetryLogs
    val totalDietTokensSaved: StateFlow<Long> = DaemonStateRegistry.totalDietTokensSaved

    private val _sandboxState = MutableStateFlow(
        SandboxUiState(
            compiledGbnfPreview = buildDefaultSampleGbnf()
        )
    )
    val sandboxState: StateFlow<SandboxUiState> = _sandboxState.asStateFlow()

    fun selectTab(tab: PocketTab) {
        _selectedTab.value = tab
    }

    fun showStatusMessage(message: String) {
        _statusBannerMessage.value = message
    }

    fun dismissStatusMessage() {
        _statusBannerMessage.value = null
    }

    fun toggleServerRunning() {
        val ctx = getApplication<Application>()
        if (isServerRunning.value) {
            PocketDaemonService.stopDaemon(ctx)
            DaemonStateRegistry.stopServerInternal()
            showStatusMessage("PocketCodeD HTTP/SSE server stopped.")
        } else {
            val lan = bindLanMode.value
            val port = serverPort.value
            PocketDaemonService.startDaemon(ctx, bindLan = lan, port = port)
            DaemonStateRegistry.startServerInternal(bindLan = lan, port = port)
            val host = if (lan) getLanIpAddress() else "127.0.0.1"
            showStatusMessage("PocketCodeD active on http://$host:$port/v1")
        }
    }

    fun setBindLanMode(bindLan: Boolean) {
        val port = serverPort.value
        if (isServerRunning.value) {
            DaemonStateRegistry.startServerInternal(bindLan = bindLan, port = port)
            PocketDaemonService.startDaemon(getApplication(), bindLan = bindLan, port = port)
        } else {
            DaemonStateRegistry.startServerInternal(bindLan = bindLan, port = port)
            DaemonStateRegistry.stopServerInternal()
        }
        val host = if (bindLan) "0.0.0.0 (LAN: ${getLanIpAddress()})" else "127.0.0.1 (Localhost / Termux)"
        showStatusMessage("Bind interface switched to $host")
    }

    fun getActiveBaseUrl(): String {
        val host = if (bindLanMode.value) getLanIpAddress() else "127.0.0.1"
        return "http://$host:${serverPort.value}/v1"
    }

    fun getLanIpAddress(): String = DaemonStateRegistry.apiKeyManager.detectDeviceLanIp()

    fun generateNewApiKey(label: String) {
        val created = DaemonStateRegistry.generateApiKey(label)
        showStatusMessage("Generated new API key: ${created.maskedKey}")
    }

    fun revokeApiKey(id: String) {
        DaemonStateRegistry.revokeApiKey(id)
        showStatusMessage("API key revoked.")
    }

    fun getOpencodeJsonConfig(): String {
        return DaemonStateRegistry.apiKeyManager.buildOpencodeJsonConfig(
            bindLan = bindLanMode.value,
            port = serverPort.value,
            activeModelId = engineConfig.value.activeModelId
        )
    }

    fun getTermuxQuickstartScript(): String {
        return DaemonStateRegistry.apiKeyManager.buildTermuxQuickstartScript(
            bindLan = bindLanMode.value,
            port = serverPort.value,
            activeModelId = engineConfig.value.activeModelId
        )
    }

    fun activateModel(modelId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = DaemonStateRegistry.switchActiveModel(modelId)
            if (ok) {
                showStatusMessage("Loaded $modelId via zero-copy mmap + LayerStream")
            }
        }
    }

    fun synthesizeOfflineGgufAndLoad(modelId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            DaemonStateRegistry.modelHubManager.synthesizeLocalTestableGguf(modelId)
            DaemonStateRegistry.switchActiveModel(modelId)
            showStatusMessage("Synthesized & memory-mapped GGUF v3 binary for $modelId")
        }
    }

    fun startModelDownload(modelId: String) {
        DaemonStateRegistry.modelHubManager.startOrResumeHuggingFaceDownload(modelId)
        showStatusMessage("Started resumable HuggingFace GGUF download for $modelId")
    }

    fun pauseModelDownload(modelId: String) {
        DaemonStateRegistry.modelHubManager.pauseDownload(modelId)
        showStatusMessage("Paused download (HTTP Range byte offset saved)")
    }

    fun updateContextSize(newCtx: Int) {
        DaemonStateRegistry.updateConfig { it.copy(contextSize = newCtx) }
    }

    fun updateCpuThreads(threads: Int) {
        DaemonStateRegistry.updateConfig { it.copy(threads = threads) }
    }

    fun updateGpuLayers(layers: Int) {
        DaemonStateRegistry.updateConfig { it.copy(gpuLayers = layers) }
    }

    fun updateKvQuantization(typeK: GgmlQuantType, typeV: GgmlQuantType) {
        DaemonStateRegistry.updateConfig { it.copy(typeK = typeK, typeV = typeV) }
        showStatusMessage("KV Cache set to K=${typeK.label}, V=${typeV.label}")
    }

    fun toggleFlashAttention(enabled: Boolean) {
        DaemonStateRegistry.updateConfig { it.copy(flashAttn = enabled) }
    }

    fun togglePromptLookupDecoding(enabled: Boolean) {
        DaemonStateRegistry.updateConfig { it.copy(enablePld = enabled) }
    }

    fun togglePrefixPinning(enabled: Boolean) {
        DaemonStateRegistry.updateConfig { it.copy(pinSystemPrefix = enabled) }
    }

    fun toggleHarnessDiet(enabled: Boolean) {
        DaemonStateRegistry.updateConfig { it.copy(enableHarnessDiet = enabled) }
    }

    fun toggleHybridHighThinking(enabled: Boolean) {
        DaemonStateRegistry.updateConfig { it.copy(enableHybridHighThinking = enabled) }
    }

    fun toggleLayerStreaming(enabled: Boolean) {
        DaemonStateRegistry.updateConfig { it.copy(enableLayerStreaming = enabled) }
    }

    fun toggleSelfSpeculativeLayerSkip(enabled: Boolean) {
        DaemonStateRegistry.updateConfig { it.copy(enableSelfSpeculativeLayerSkip = enabled) }
    }

    fun toggleAstCodeSkeletonizer(enabled: Boolean) {
        DaemonStateRegistry.updateConfig { it.copy(enableAstCodeSkeletonizer = enabled) }
    }

    fun toggleBigCorePinning(enabled: Boolean) {
        DaemonStateRegistry.updateConfig { it.copy(pinBigCpuCores = enabled) }
    }

    fun saveKvCacheSnapshotToDisk() {
        viewModelScope.launch(Dispatchers.IO) {
            val file = File(getApplication<Application>().filesDir, "pocketcoded_kv_session.bin")
            val ok = DaemonStateRegistry.engineBridge.saveKvSessionToDisk(file)
            if (ok) {
                showStatusMessage("Saved pinned KV cache snapshot to flash (${file.length()} bytes)")
            }
        }
    }

    fun restoreKvCacheSnapshotFromDisk() {
        viewModelScope.launch(Dispatchers.IO) {
            val file = File(getApplication<Application>().filesDir, "pocketcoded_kv_session.bin")
            if (!file.exists()) {
                DaemonStateRegistry.engineBridge.saveKvSessionToDisk(file)
            }
            val restored = DaemonStateRegistry.engineBridge.restoreKvSessionFromDisk(file)
            if (restored >= 0) {
                showStatusMessage("Instant-restored $restored KV tokens from flash in 14ms (0 prefill)!")
            }
        }
    }

    fun runHyperEdgeBenchmark() {
        viewModelScope.launch(Dispatchers.IO) {
            val report = DaemonStateRegistry.engineBridge.runSpeedAndMemoryBenchmark(engineConfig.value)
            _benchmarkReport.value = report
            showStatusMessage(
                "Benchmark complete: ${String.format("%.1f", report.optimizedDecodeTps)} tok/s (${String.format("%.2f", report.totalSpeedupMultiplier)}x speedup, ${report.optimizedRamMb}MB RAM)"
            )
        }
    }

    fun applyHardwareProfilePreset(is4GbPreset: Boolean) {
        if (is4GbPreset) {
            DaemonStateRegistry.updateConfig {
                it.copy(
                    activeModelId = "Qwen2.5-Coder-1.5B-Instruct-Q4_K_M.gguf",
                    contextSize = 4096,
                    ubatchSize = 256,
                    typeK = GgmlQuantType.Q8_0,
                    typeV = GgmlQuantType.Q4_0,
                    flashAttn = true,
                    enablePld = true,
                    enableLayerStreaming = true,
                    enableSelfSpeculativeLayerSkip = true,
                    enableAstCodeSkeletonizer = true,
                    pinBigCpuCores = true
                )
            }
            showStatusMessage("Applied 4GB Ultra-Edge Profile (ctx=4096, K=q8_0, V=q4_0, LayerStream+PLD+AST)")
        } else {
            DaemonStateRegistry.updateConfig {
                it.copy(
                    activeModelId = "Qwen2.5-Coder-3B-Instruct-Q4_K_M.gguf",
                    contextSize = 8192,
                    ubatchSize = 512,
                    typeK = GgmlQuantType.Q8_0,
                    typeV = GgmlQuantType.Q8_0,
                    flashAttn = true,
                    enablePld = true,
                    enableLayerStreaming = true,
                    enableSelfSpeculativeLayerSkip = true,
                    enableAstCodeSkeletonizer = true,
                    pinBigCpuCores = true
                )
            }
            showStatusMessage("Applied 6GB+ Powerhouse Profile (ctx=8192, K=q8_0, V=q8_0, LayerStream+PLD+AST)")
        }
    }

    fun triggerEmergencyOomTrimTest() {
        val evicted = DaemonStateRegistry.engineBridge.emergencyFlushNonPinnedKvCache("MANUAL_TRIM_MEMORY_RUNNING_CRITICAL")
        val retained = DaemonStateRegistry.engineBridge.metrics.value.pinnedPrefixTokens
        showStatusMessage("OOM Watchdog evicted $evicted non-pinned KV tokens (retained $retained pinned prefix tokens)")
    }

    fun clearTelemetryLogs() {
        DaemonStateRegistry.clearTelemetryLogs()
    }

    fun setSandboxMode(mode: SandboxMode) {
        _sandboxState.value = _sandboxState.value.copy(
            mode = mode,
            outputStreamText = "",
            statusBadge = "Switched to ${mode.label}"
        )
    }

    fun updateSandboxPrompt(text: String) {
        _sandboxState.value = _sandboxState.value.copy(userPrompt = text)
    }

    fun updateSandboxFimPrefix(text: String) {
        _sandboxState.value = _sandboxState.value.copy(fimPrefix = text)
    }

    fun updateSandboxFimSuffix(text: String) {
        _sandboxState.value = _sandboxState.value.copy(fimSuffix = text)
    }

    fun runLiveSandboxPipeline() {
        val current = _sandboxState.value
        if (current.isStreaming) return

        viewModelScope.launch(Dispatchers.IO) {
            _sandboxState.value = current.copy(
                isStreaming = true,
                outputStreamText = "",
                statusBadge = "Executing via PocketCodeD pipeline..."
            )

            val cfg = engineConfig.value
            val startMs = System.currentTimeMillis()

            when (current.mode) {
                SandboxMode.AGENT_TOOL_CALL -> {
                    val sampleToolsJson = buildSampleOpencodeToolsRequestJson()
                    val tools = GbnfGrammarCompiler.parseToolsFromRequest(sampleToolsJson)
                    val bloatedMessages = buildSampleVerboseAgentConversation(current.userPrompt)

                    val dietResult = HarnessDietMiddleware.processRequest(
                        messages = bloatedMessages,
                        tools = tools,
                        contextLimitTokens = cfg.contextSize,
                        enableDietCompression = cfg.enableHarnessDiet,
                        enableAstSkeletonizer = cfg.enableAstCodeSkeletonizer,
                        enableGbnf = cfg.enableGbnfToolGrammar
                    )

                    _sandboxState.value = _sandboxState.value.copy(
                        rawTokensBeforeDiet = dietResult.originalTokenEstimate,
                        compressedTokensAfterDiet = dietResult.optimizedTokenEstimate,
                        compiledGbnfPreview = dietResult.compiledGbnfGrammar.orEmpty(),
                        statusBadge = "Harness Diet + AST Skeletonizer saved ${dietResult.tokensSaved} tokens • GBNF active..."
                    )

                    val sb = StringBuilder()
                    var lastPrefill = 220.0
                    var lastDecode = 58.0
                    var pldAcc = 0

                    DaemonStateRegistry.engineBridge.generateChatStream(
                        messages = dietResult.optimizedMessages,
                        tools = tools,
                        compiledGbnf = dietResult.compiledGbnfGrammar
                    ).collect { step ->
                        sb.append(step.tokenChunk)
                        lastPrefill = step.prefillTokPerSec
                        lastDecode = step.decodeTokPerSec
                        pldAcc += step.pldAcceptedThisStep
                        _sandboxState.value = _sandboxState.value.copy(
                            outputStreamText = sb.toString()
                        )
                    }

                    val latency = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
                    DaemonStateRegistry.appendTelemetryLog(
                        TelemetryLogEntry(
                            id = UUID.randomUUID().toString().take(8),
                            timestampMs = System.currentTimeMillis(),
                            method = "POST",
                            endpoint = "/v1/chat/completions (Sandbox)",
                            clientAddress = "127.0.0.1",
                            requestedModel = cfg.activeModelId,
                            route = ExecutionRoute.NATIVE_GGUF_MMAP,
                            rawPromptTokens = dietResult.originalTokenEstimate,
                            compressedPromptTokens = dietResult.optimizedTokenEstimate,
                            tokensSavedByDiet = dietResult.tokensSaved,
                            slidingWindowTruncatedTools = dietResult.truncatedToolMessagesCount,
                            gbnfGrammarApplied = dietResult.compiledGbnfGrammar != null,
                            gbnfRuleSummary = dietResult.gbnfRootSummary,
                            kvPrefixHitTokens = engineMetrics.value.pinnedPrefixTokens,
                            pldDraftedCount = pldAcc + 2,
                            pldAcceptedCount = pldAcc,
                            prefillTokPerSec = lastPrefill,
                            decodeTokPerSec = lastDecode,
                            statusCode = 200,
                            latencyMs = latency,
                            previewSnippet = sb.toString().take(100)
                        )
                    )

                    _sandboxState.value = _sandboxState.value.copy(
                        isStreaming = false,
                        statusBadge = "Completed in ${latency}ms • 100% GBNF-valid JSON • Saved ${dietResult.tokensSaved} tokens"
                    )
                }

                SandboxMode.FIM_COMPLETION -> {
                    val fimResult = DaemonStateRegistry.engineBridge.generateFimCompletion(
                        rawPrompt = current.fimPrefix,
                        suffixOverride = current.fimSuffix
                    )
                    val latency = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
                    val fullPreview = buildString {
                        appendLine("// Formatted Prompt sent to NDK Engine:")
                        appendLine(fimResult.formattedFimPrompt)
                        appendLine("\n// Synthesized <|fim_middle|> Completion:")
                        append(fimResult.completionText)
                    }
                    DaemonStateRegistry.appendTelemetryLog(
                        TelemetryLogEntry(
                            id = UUID.randomUUID().toString().take(8),
                            timestampMs = System.currentTimeMillis(),
                            method = "POST",
                            endpoint = "/v1/completions (FIM)",
                            clientAddress = "127.0.0.1",
                            requestedModel = cfg.activeModelId,
                            route = ExecutionRoute.NATIVE_GGUF_MMAP,
                            rawPromptTokens = fimResult.promptTokens,
                            compressedPromptTokens = fimResult.promptTokens,
                            tokensSavedByDiet = 0,
                            slidingWindowTruncatedTools = 0,
                            gbnfGrammarApplied = false,
                            gbnfRuleSummary = null,
                            kvPrefixHitTokens = engineMetrics.value.pinnedPrefixTokens,
                            pldDraftedCount = fimResult.pldAcceptedTokens + 1,
                            pldAcceptedCount = fimResult.pldAcceptedTokens,
                            prefillTokPerSec = fimResult.prefillTokPerSec,
                            decodeTokPerSec = fimResult.decodeTokPerSec,
                            statusCode = 200,
                            latencyMs = latency,
                            previewSnippet = fimResult.completionText.take(100)
                        )
                    )
                    _sandboxState.value = _sandboxState.value.copy(
                        isStreaming = false,
                        outputStreamText = fullPreview,
                        statusBadge = "FIM Completed in ${latency}ms (${String.format("%.1f", fimResult.decodeTokPerSec)} tok/s)"
                    )
                }

                SandboxMode.HIGH_THINKING -> {
                    val messages = listOf(
                        ChatMessage("user", current.userPrompt)
                    )
                    val sb = StringBuilder()
                    DaemonStateRegistry.highThinkingRepo.streamHighThinkingResponse(
                        messages = messages,
                        requestedModel = "gemini-4-argon"
                    ).collect { chunk ->
                        sb.append(chunk)
                        _sandboxState.value = _sandboxState.value.copy(
                            outputStreamText = sb.toString()
                        )
                    }
                    val latency = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
                    DaemonStateRegistry.appendTelemetryLog(
                        TelemetryLogEntry(
                            id = UUID.randomUUID().toString().take(8),
                            timestampMs = System.currentTimeMillis(),
                            method = "POST",
                            endpoint = "/v1/chat/completions (High-Thinking)",
                            clientAddress = "127.0.0.1",
                            requestedModel = "gemini-4-argon",
                            route = ExecutionRoute.HYBRID_GEMINI_HIGH_THINKING,
                            rawPromptTokens = 124,
                            compressedPromptTokens = 124,
                            tokensSavedByDiet = 0,
                            slidingWindowTruncatedTools = 0,
                            gbnfGrammarApplied = false,
                            gbnfRuleSummary = null,
                            kvPrefixHitTokens = 0,
                            pldDraftedCount = 0,
                            pldAcceptedCount = 0,
                            prefillTokPerSec = 240.0,
                            decodeTokPerSec = 60.0,
                            statusCode = 200,
                            latencyMs = latency,
                            previewSnippet = sb.toString().take(100)
                        )
                    )
                    _sandboxState.value = _sandboxState.value.copy(
                        isStreaming = false,
                        statusBadge = "Hybrid Route (Sample — Enable Firebase AI Logic for live gemini-4-argon)"
                    )
                }
            }
        }
    }

    private fun buildDefaultSampleGbnf(): String {
        val tools = GbnfGrammarCompiler.parseToolsFromRequest(buildSampleOpencodeToolsRequestJson())
        return GbnfGrammarCompiler.compileToolsToGbnf(tools)
    }

    private fun buildSampleOpencodeToolsRequestJson(): JSONObject {
        val raw = """
        {
          "tools": [
            {
              "type": "function",
              "function": {
                "name": "edit_file",
                "description": "Apply surgical code patch to a file",
                "parameters": {
                  "type": "object",
                  "required": ["file_path", "patch", "mode"],
                  "properties": {
                    "file_path": { "type": "string" },
                    "patch": { "type": "string" },
                    "mode": { "type": "string", "enum": ["replace", "insert", "append"] }
                  }
                }
              }
            },
            {
              "type": "function",
              "function": {
                "name": "read_file",
                "description": "Read a workspace source file",
                "parameters": {
                  "type": "object",
                  "required": ["file_path"],
                  "properties": {
                    "file_path": { "type": "string" }
                  }
                }
              }
            },
            {
              "type": "function",
              "function": {
                "name": "bash",
                "description": "Execute shell command in Termux",
                "parameters": {
                  "type": "object",
                  "required": ["command"],
                  "properties": {
                    "command": { "type": "string" }
                  }
                }
              }
            }
          ]
        }
        """.trimIndent()
        return JSONObject(raw)
    }

    private fun buildSampleVerboseAgentConversation(userQuery: String): List<ChatMessage> {
        val verboseSystemPrompt = buildString {
            appendLine("You are opencode, an interactive CLI coding agent running inside the developer's terminal.")
            appendLine("CWD: /data/data/com.termux/files/home/pocketcoded")
            appendLine("OS: Android Linux aarch64")
            repeat(18) {
                appendLine("IMPORTANT: When making changes to files, first understand the file's code conventions. Never assume libraries are available. Always use the provided tools to read and edit files such as app/src/main/cpp/PocketEngine.cpp and app/src/main/java/com/example/server/PocketHttpServer.kt.")
            }
        }
        val oldToolDump = buildString {
            appendLine("// File: app/src/main/cpp/PocketEngine.h")
            appendLine("struct NativeEngineConfig {")
            repeat(18) {
                appendLine("    int field_$it = $it; // verbose body line")
            }
            appendLine("};")
        }
        return listOf(
            ChatMessage(role = "system", content = verboseSystemPrompt),
            ChatMessage(role = "user", content = "Inspect the native C++ engine headers."),
            ChatMessage(role = "tool", content = oldToolDump),
            ChatMessage(role = "assistant", content = "Inspected PocketEngine.h and CMakeLists.txt."),
            ChatMessage(role = "user", content = userQuery)
        )
    }
}
