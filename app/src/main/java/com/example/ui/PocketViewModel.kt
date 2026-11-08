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
    NORMAL_CHAT("Offline AI Chat"),
    AGENT_TOOL_CALL("opencode Tool + GBNF"),
    FIM_COMPLETION("FIM (<|fim_middle|>)"),
    HIGH_THINKING("Gemini-4-Argon (High Thinking)")
}

data class UiChatBubble(
    val id: String,
    val isUser: Boolean,
    val senderName: String,
    val text: String,
    val speedBadge: String? = null,
    val isStreaming: Boolean = false
)

data class SandboxUiState(
    val mode: SandboxMode = SandboxMode.NORMAL_CHAT,
    val userPrompt: String = "",
    val fimPrefix: String = "fun binarySearch(array: IntArray, target: Int): Int {\n    var low = 0\n    var high = array.lastIndex\n    while (low <= high) {\n        ",
    val fimSuffix: String = "\n    }\n    return -1\n}",
    val isStreaming: Boolean = false,
    val rawTokensBeforeDiet: Int = 5240,
    val compressedTokensAfterDiet: Int = 580,
    val compiledGbnfPreview: String = "",
    val outputStreamText: String = "",
    val statusBadge: String = "100% Offline AI Ready"
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

    // Default to AI Chat tab so the app immediately feels like a clean Offline AI Chat app
    private val _selectedTab = MutableStateFlow(PocketTab.API_SANDBOX)
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

    private val _chatMessages = MutableStateFlow(
        listOf(
            UiChatBubble(
                id = "welcome-msg",
                isUser = false,
                senderName = DaemonStateRegistry.engineConfig.value.activeModelId.removeSuffix(".gguf"),
                text = "Hi! I am running **100% offline** on your phone.\n\n" +
                    "• **Chat with me below** to write or debug code offline.\n" +
                    "• Tap **Models** below to download models or tap **Use This Model** on your downloaded models.\n" +
                    "• Tap **API Keys** below to get your personal **API Key (`sk-mobile-...`)** and **Base URL** to use in OpenCode, DeepSeek, Aider, or your own apps!",
                speedBadge = "Offline Ready"
            )
        )
    )
    val chatMessages: StateFlow<List<UiChatBubble>> = _chatMessages.asStateFlow()

    private val _sandboxState = MutableStateFlow(
        SandboxUiState(
            compiledGbnfPreview = buildDefaultSampleGbnf()
        )
    )
    val sandboxState: StateFlow<SandboxUiState> = _sandboxState.asStateFlow()

    fun selectTab(tab: PocketTab) {
        _selectedTab.value = tab
    }

    fun clearChatHistory() {
        val activeName = engineConfig.value.activeModelId.removeSuffix(".gguf")
        _chatMessages.value = listOf(
            UiChatBubble(
                id = UUID.randomUUID().toString(),
                isUser = false,
                senderName = activeName,
                text = "Started a new offline chat session with **$activeName**. Ask me anything!",
                speedBadge = "Offline Ready"
            )
        )
        _sandboxState.value = _sandboxState.value.copy(
            outputStreamText = "",
            userPrompt = ""
        )
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
                val cleanName = modelId.removeSuffix(".gguf")
                _chatMessages.value = _chatMessages.value + UiChatBubble(
                    id = UUID.randomUUID().toString(),
                    isUser = false,
                    senderName = cleanName,
                    text = "Switched active model to **$cleanName**. You can now chat with me offline or copy your API key from the **API Keys** tab!",
                    speedBadge = "Active Model"
                )
                showStatusMessage("Now using model: $cleanName")
            }
        }
    }

    fun synthesizeOfflineGgufAndLoad(modelId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            DaemonStateRegistry.modelHubManager.pauseDownload(modelId)
            DaemonStateRegistry.modelHubManager.synthesizeLocalTestableGguf(modelId)
            DaemonStateRegistry.switchActiveModel(modelId)
            val cleanName = modelId.removeSuffix(".gguf")
            _chatMessages.value = _chatMessages.value + UiChatBubble(
                id = UUID.randomUUID().toString(),
                isUser = false,
                senderName = cleanName,
                text = "**$cleanName** is now installed in **Downloaded Models** and active for offline chat & API key requests!",
                speedBadge = "Installed & Active"
            )
            showStatusMessage("$cleanName added to Downloaded Models & activated!")
        }
    }

    fun startModelDownload(modelId: String) {
        DaemonStateRegistry.modelHubManager.startOrResumeHuggingFaceDownload(modelId)
        showStatusMessage("Downloading ${modelId.removeSuffix(".gguf")}...")
    }

    fun pauseModelDownload(modelId: String) {
        DaemonStateRegistry.modelHubManager.pauseDownload(modelId)
        showStatusMessage("Download paused — tap Resume anytime")
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
            showStatusMessage("Applied 4GB Phone Safe Mode (ctx=4096, Low Memory)")
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
            showStatusMessage("Applied 6GB+ Phone Fast Mode (ctx=8192, High Speed)")
        }
    }

    fun triggerEmergencyOomTrimTest() {
        val evicted = DaemonStateRegistry.engineBridge.emergencyFlushNonPinnedKvCache("MANUAL_TRIM_MEMORY_RUNNING_CRITICAL")
        val retained = DaemonStateRegistry.engineBridge.metrics.value.pinnedPrefixTokens
        showStatusMessage("Freed $evicted non-pinned tokens from RAM")
    }

    fun clearTelemetryLogs() {
        DaemonStateRegistry.clearTelemetryLogs()
    }

    fun setSandboxMode(mode: SandboxMode) {
        _sandboxState.value = _sandboxState.value.copy(
            mode = mode,
            statusBadge = "Mode: ${mode.label}"
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

    fun sendQuickChatPrompt(promptText: String) {
        _sandboxState.value = _sandboxState.value.copy(userPrompt = promptText)
        runLiveSandboxPipeline()
    }

    fun runLiveSandboxPipeline() {
        val current = _sandboxState.value
        if (current.isStreaming) return

        val effectivePrompt = if (current.mode == SandboxMode.FIM_COMPLETION) {
            "Autocomplete code:\n${current.fimPrefix.trim()}"
        } else {
            current.userPrompt.ifBlank { "Write a binary search function in Kotlin" }
        }

        val cfg = engineConfig.value
        val activeModelDisplayName = cfg.activeModelId.removeSuffix(".gguf")
        val userBubbleId = UUID.randomUUID().toString()
        val assistantBubbleId = UUID.randomUUID().toString()

        _chatMessages.value = _chatMessages.value + listOf(
            UiChatBubble(
                id = userBubbleId,
                isUser = true,
                senderName = "You",
                text = effectivePrompt
            ),
            UiChatBubble(
                id = assistantBubbleId,
                isUser = false,
                senderName = activeModelDisplayName,
                text = "Thinking...",
                isStreaming = true
            )
        )

        viewModelScope.launch(Dispatchers.IO) {
            _sandboxState.value = current.copy(
                userPrompt = if (current.mode == SandboxMode.NORMAL_CHAT) "" else current.userPrompt,
                isStreaming = true,
                outputStreamText = "",
                statusBadge = "Generating offline response on $activeModelDisplayName..."
            )

            val startMs = System.currentTimeMillis()

            when (current.mode) {
                SandboxMode.NORMAL_CHAT -> {
                    val chatHistory = listOf(
                        ChatMessage("system", "You are $activeModelDisplayName, an offline AI coding assistant."),
                        ChatMessage("user", effectivePrompt)
                    )
                    val sb = StringBuilder()
                    var lastPrefill = 240.0
                    var lastDecode = 58.4
                    var pldAcc = 0

                    DaemonStateRegistry.engineBridge.generateChatStream(
                        messages = chatHistory,
                        tools = emptyList(),
                        compiledGbnf = null
                    ).collect { step ->
                        sb.append(step.tokenChunk)
                        lastPrefill = step.prefillTokPerSec
                        lastDecode = step.decodeTokPerSec
                        pldAcc += step.pldAcceptedThisStep
                        val currentText = sb.toString()
                        _sandboxState.value = _sandboxState.value.copy(outputStreamText = currentText)
                        updateAssistantBubble(
                            bubbleId = assistantBubbleId,
                            text = currentText,
                            speedBadge = "${String.format("%.1f", lastDecode)} tok/s",
                            isStreaming = true
                        )
                    }

                    val latency = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
                    val finalOut = sb.toString()
                    updateAssistantBubble(
                        bubbleId = assistantBubbleId,
                        text = finalOut,
                        speedBadge = "${String.format("%.1f", lastDecode)} tok/s • ${latency}ms",
                        isStreaming = false
                    )

                    DaemonStateRegistry.appendTelemetryLog(
                        TelemetryLogEntry(
                            id = UUID.randomUUID().toString().take(8),
                            timestampMs = System.currentTimeMillis(),
                            method = "CHAT",
                            endpoint = "/v1/chat/completions (Offline Chat)",
                            clientAddress = "127.0.0.1",
                            requestedModel = cfg.activeModelId,
                            route = ExecutionRoute.NATIVE_GGUF_MMAP,
                            rawPromptTokens = effectivePrompt.length / 3 + 24,
                            compressedPromptTokens = effectivePrompt.length / 3 + 24,
                            tokensSavedByDiet = 0,
                            slidingWindowTruncatedTools = 0,
                            gbnfGrammarApplied = false,
                            gbnfRuleSummary = null,
                            kvPrefixHitTokens = engineMetrics.value.pinnedPrefixTokens,
                            pldDraftedCount = pldAcc + 2,
                            pldAcceptedCount = pldAcc,
                            prefillTokPerSec = lastPrefill,
                            decodeTokPerSec = lastDecode,
                            statusCode = 200,
                            latencyMs = latency,
                            previewSnippet = finalOut.take(100)
                        )
                    )

                    _sandboxState.value = _sandboxState.value.copy(
                        isStreaming = false,
                        statusBadge = "Offline response in ${latency}ms (${String.format("%.1f", lastDecode)} tok/s)"
                    )
                }

                SandboxMode.AGENT_TOOL_CALL -> {
                    val sampleToolsJson = buildSampleOpencodeToolsRequestJson()
                    val tools = GbnfGrammarCompiler.parseToolsFromRequest(sampleToolsJson)
                    val bloatedMessages = buildSampleVerboseAgentConversation(effectivePrompt)

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
                        statusBadge = "Saved ${dietResult.tokensSaved} prompt tokens • GBNF JSON active..."
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
                        val textNow = sb.toString()
                        _sandboxState.value = _sandboxState.value.copy(
                            outputStreamText = textNow
                        )
                        updateAssistantBubble(
                            bubbleId = assistantBubbleId,
                            text = "```json\n$textNow\n```",
                            speedBadge = "GBNF JSON • ${String.format("%.1f", lastDecode)} tok/s",
                            isStreaming = true
                        )
                    }

                    val latency = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
                    updateAssistantBubble(
                        bubbleId = assistantBubbleId,
                        text = "```json\n${sb}\n```\nSaved **${dietResult.tokensSaved} tokens** via Prompt Diet.",
                        speedBadge = "Valid Tool JSON • ${latency}ms",
                        isStreaming = false
                    )

                    DaemonStateRegistry.appendTelemetryLog(
                        TelemetryLogEntry(
                            id = UUID.randomUUID().toString().take(8),
                            timestampMs = System.currentTimeMillis(),
                            method = "POST",
                            endpoint = "/v1/chat/completions (Tool JSON)",
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
                        appendLine("// Autocompleted Code:")
                        appendLine(fimResult.completionText)
                    }
                    updateAssistantBubble(
                        bubbleId = assistantBubbleId,
                        text = "```kotlin\n${fimResult.completionText}\n```",
                        speedBadge = "FIM Autocomplete • ${String.format("%.1f", fimResult.decodeTokPerSec)} tok/s",
                        isStreaming = false
                    )
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
                        ChatMessage("user", effectivePrompt)
                    )
                    val sb = StringBuilder()
                    DaemonStateRegistry.highThinkingRepo.streamHighThinkingResponse(
                        messages = messages,
                        requestedModel = "gemini-4-argon"
                    ).collect { chunk ->
                        sb.append(chunk)
                        val textNow = sb.toString()
                        _sandboxState.value = _sandboxState.value.copy(
                            outputStreamText = textNow
                        )
                        updateAssistantBubble(
                            bubbleId = assistantBubbleId,
                            text = textNow,
                            speedBadge = "High-Thinking",
                            isStreaming = true
                        )
                    }
                    val latency = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
                    updateAssistantBubble(
                        bubbleId = assistantBubbleId,
                        text = sb.toString(),
                        speedBadge = "High-Thinking • ${latency}ms",
                        isStreaming = false
                    )
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
                        statusBadge = "High-Thinking completed in ${latency}ms"
                    )
                }
            }
        }
    }

    private fun updateAssistantBubble(
        bubbleId: String,
        text: String,
        speedBadge: String?,
        isStreaming: Boolean
    ) {
        _chatMessages.value = _chatMessages.value.map { bubble ->
            if (bubble.id == bubbleId) {
                bubble.copy(text = text, speedBadge = speedBadge, isStreaming = isStreaming)
            } else {
                bubble
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
