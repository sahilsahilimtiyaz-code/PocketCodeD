package com.example.server

import android.util.Log
import com.example.ai.PlaceholderHighThinkingAiRepository
import com.example.engine.PocketEngineBridge
import com.example.middleware.GbnfGrammarCompiler
import com.example.middleware.HarnessDietMiddleware
import com.example.model.ChatMessage
import com.example.model.EngineConfig
import com.example.model.ExecutionRoute
import com.example.model.HardwareProfile
import com.example.model.TelemetryLogEntry
import com.example.security.ApiKeyManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Pillar 3: Embedded OpenAI & DeepSeek Compatible HTTP + SSE API Gateway
 *
 * Implements both `/v1/...` (OpenAI / opencode standard) and root `/chat/completions` & `/models`
 * (DeepSeek harness standard):
 * - `GET /v1/models` & `GET /models`
 * - `POST /v1/chat/completions` & `POST /chat/completions` (Supports `stream: true` via SSE
 *   `text/event-stream` and `stream: false`, executes the "Harness Diet" Middleware + AST Skeletonizer +
 *   GBNF Tool Schema Compiler before tokenization, and supports OpenAI `tools` / `tool_calls` + DeepSeek `reasoning_content`)
 * - `POST /v1/completions` & `POST /completions` (FIM — Fill-In-The-Middle code completion)
 * - `GET /health` & `GET /metrics`
 * - `Authorization: Bearer sk-mobile-...` authentication middleware on all inference routes.
 */
class PocketHttpServer(
    private val engineBridge: PocketEngineBridge,
    private val apiKeyManager: ApiKeyManager,
    private val highThinkingRepo: PlaceholderHighThinkingAiRepository,
    private val configProvider: () -> EngineConfig,
    private val hardwareProvider: () -> HardwareProfile,
    private val onTelemetryEvent: (TelemetryLogEntry) -> Unit
) {
    companion object {
        private const val TAG = "PocketHttpServer"
        val MODEL_ALIASES = listOf(
            "qwen2.5-coder",
            "qwen2.5-coder-7b-iq2",
            "deepseek-coder",
            "deepseek-chat",
            "deepseek-reasoner",
            "gpt-4o",
            "gemini-4-argon"
        )
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null
    private val running = AtomicBoolean(false)
    private val totalTokensSavedByDiet = AtomicLong(0L)
    private val totalRequestsServed = AtomicLong(0L)

    var boundHost: String = "127.0.0.1"
        private set
    var boundPort: Int = 8080
        private set

    fun isRunning(): Boolean = running.get()

    fun getTotalTokensSaved(): Long = totalTokensSavedByDiet.get()

    @Synchronized
    fun startServer(bindLan: Boolean, port: Int = 8080): Boolean {
        if (running.get()) {
            stopServer()
        }
        return try {
            val host = if (bindLan) "0.0.0.0" else "127.0.0.1"
            val socket = ServerSocket()
            socket.reuseAddress = true
            socket.bind(InetSocketAddress(InetAddress.getByName(host), port), 64)
            serverSocket = socket
            boundHost = host
            boundPort = socket.localPort
            running.set(true)

            acceptJob = scope.launch {
                while (running.get()) {
                    try {
                        val client = socket.accept()
                        launch { handleClientSocket(client) }
                    } catch (e: Exception) {
                        if (running.get()) {
                            Log.w(TAG, "Socket accept error: ${e.message}")
                        }
                    }
                }
            }
            Log.i(TAG, "PocketCodeD HTTP/SSE Server listening on http://$boundHost:$boundPort")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind port $port", e)
            running.set(false)
            false
        }
    }

    @Synchronized
    fun stopServer() {
        running.set(false)
        acceptJob?.cancel()
        acceptJob = null
        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }
        serverSocket = null
    }

    private fun handleClientSocket(socket: Socket) {
        socket.use { client ->
            client.soTimeout = 60_000
            val clientIp = client.inetAddress?.hostAddress ?: "127.0.0.1"
            val reader = BufferedReader(InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8))
            val rawOut = BufferedOutputStream(client.getOutputStream())
            val writer = PrintWriter(OutputStreamWriter(rawOut, StandardCharsets.UTF_8), false)

            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return
            val method = parts[0].uppercase()
            val fullPath = parts[1]
            val path = fullPath.substringBefore("?")

            val headers = mutableMapOf<String, String>()
            var line: String?
            while (reader.readLine().also { line = it } != null && line!!.isNotEmpty()) {
                val idx = line!!.indexOf(':')
                if (idx > 0) {
                    val key = line!!.substring(0, idx).trim().lowercase()
                    val value = line!!.substring(idx + 1).trim()
                    headers[key] = value
                }
            }

            if (method == "OPTIONS") {
                writeJsonResponse(writer, 200, JSONObject().put("ok", true))
                return
            }

            val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
            val bodyString = if (contentLength > 0) {
                val chars = CharArray(contentLength)
                var totalRead = 0
                while (totalRead < contentLength) {
                    val r = reader.read(chars, totalRead, contentLength - totalRead)
                    if (r == -1) break
                    totalRead += r
                }
                String(chars, 0, totalRead)
            } else ""

            val isProtectedRoute = path.startsWith("/v1") ||
                path == "/models" ||
                path == "/chat/completions" ||
                path == "/completions"

            if (isProtectedRoute) {
                val authHeader = headers["authorization"]
                if (!apiKeyManager.validateBearerHeader(authHeader)) {
                    val err = JSONObject().apply {
                        put("error", JSONObject().apply {
                            put("message", "Invalid or missing PocketCodeD API key. Expected 'Authorization: Bearer sk-mobile-...'")
                            put("type", "authentication_error")
                            put("code", "invalid_api_key")
                        })
                    }
                    writeJsonResponse(writer, 401, err)
                    return
                }
            }

            when {
                method == "GET" && path == "/health" -> {
                    writeJsonResponse(writer, 200, buildHealthJson())
                }
                method == "GET" && path == "/metrics" -> {
                    writeJsonResponse(writer, 200, buildMetricsJson())
                }
                method == "GET" && (path == "/v1/models" || path == "/models") -> {
                    writeJsonResponse(writer, 200, buildModelsJson())
                }
                method == "POST" && (path == "/v1/completions" || path == "/completions") -> {
                    handleFimCompletionsHttp(writer, bodyString, clientIp)
                }
                method == "POST" && (path == "/v1/chat/completions" || path == "/chat/completions") -> {
                    handleChatCompletionsHttp(writer, bodyString, clientIp)
                }
                else -> {
                    val err = JSONObject().apply {
                        put("error", JSONObject().apply {
                            put("message", "Endpoint not found: $method $path")
                            put("type", "invalid_request_error")
                        })
                    }
                    writeJsonResponse(writer, 404, err)
                }
            }
        }
    }

    fun buildHealthJson(): JSONObject {
        val hw = hardwareProvider()
        val m = engineBridge.metrics.value
        val cfg = configProvider()
        return JSONObject().apply {
            put("status", "ok")
            put("daemon", "PocketCodeD")
            put("active_model", cfg.activeModelId)
            put("mmap_zero_copy", cfg.useMmap)
            put("layer_streaming_enabled", cfg.enableLayerStreaming)
            put("self_speculative_layer_skip", cfg.enableSelfSpeculativeLayerSkip)
            put("ast_skeletonizer_enabled", cfg.enableAstCodeSkeletonizer)
            put("flash_attn", cfg.flashAttn)
            put("kv_quantization", JSONObject().apply {
                put("type_k", cfg.typeK.label)
                put("type_v", cfg.typeV.label)
                put("ram_reduction_pct", m.kvCacheReductionPercent)
            })
            put("ram_mb", JSONObject().apply {
                put("total_mb", hw.totalRamMb)
                put("free_mb", hw.availableRamMb)
                put("used_mb", (hw.totalRamMb - hw.availableRamMb).coerceAtLeast(0L))
                put("low_memory_flag", hw.lowMemoryWarning)
            })
            put("kv_cache", JSONObject().apply {
                put("tokens_used", m.kvTokensUsed)
                put("tokens_capacity", m.kvTokensCapacity)
                put("fill_pct", if (m.kvTokensCapacity > 0) (m.kvTokensUsed * 100) / m.kvTokensCapacity else 0)
                put("pinned_prefix_tokens", m.pinnedPrefixTokens)
                put("disk_snapshot_saved", m.diskKvSnapshotSaved)
            })
            put("speed", JSONObject().apply {
                put("prefill_tok_per_sec", m.lastPrefillTokPerSec)
                put("decode_tok_per_sec", m.lastDecodeTokPerSec)
                put("pld_speedup_x", m.pldSpeedupMultiplier)
            })
        }
    }

    fun buildMetricsJson(): JSONObject {
        val hw = hardwareProvider()
        val m = engineBridge.metrics.value
        val cfg = configProvider()
        return JSONObject().apply {
            put("requests_served_total", totalRequestsServed.get())
            put("harness_diet_tokens_saved_total", totalTokensSavedByDiet.get())
            put("ram_total_mb", hw.totalRamMb)
            put("ram_available_mb", hw.availableRamMb)
            put("ram_used_mb", (hw.totalRamMb - hw.availableRamMb).coerceAtLeast(0L))
            put("resident_rss_cap_bytes", m.residentRssCapBytes)
            put("kv_cache_bytes", m.kvCacheBytes)
            put("kv_cache_saved_bytes", m.kvCacheSavedBytes)
            put("kv_cache_fill_percent", if (m.kvTokensCapacity > 0) (m.kvTokensUsed * 100.0) / m.kvTokensCapacity else 0.0)
            put("kv_cache_hit_ratio_percent", m.kvCacheHitRatioPercent)
            put("pinned_prefix_tokens", m.pinnedPrefixTokens)
            put("prefill_tok_per_sec", m.lastPrefillTokPerSec)
            put("decode_tok_per_sec", m.lastDecodeTokPerSec)
            put("pld_drafted_tokens_total", m.pldDraftedTokens)
            put("pld_accepted_tokens_total", m.pldAcceptedTokens)
            put("oom_trim_events_total", m.oomTrimEventsCount)
            put("active_config", JSONObject().apply {
                put("model", cfg.activeModelId)
                put("context_size", cfg.contextSize)
                put("threads", cfg.threads)
                put("ubatch", cfg.ubatchSize)
                put("type_k", cfg.typeK.label)
                put("type_v", cfg.typeV.label)
                put("pld_enabled", cfg.enablePld)
                put("layer_streaming", cfg.enableLayerStreaming)
                put("layer_skip", cfg.enableSelfSpeculativeLayerSkip)
            })
        }
    }

    fun buildModelsJson(): JSONObject {
        val cfg = configProvider()
        val dataArr = JSONArray()
        val nowSec = System.currentTimeMillis() / 1000L

        val allIds = (listOf(cfg.activeModelId) + MODEL_ALIASES).distinct()
        for (id in allIds) {
            dataArr.put(
                JSONObject().apply {
                    put("id", id)
                    put("object", "model")
                    put("created", nowSec)
                    put("owned_by", if (id == "gemini-4-argon") "google-hybrid-router" else "pocketcoded-local-ndk")
                    put("permission", JSONArray())
                }
            )
        }
        return JSONObject().apply {
            put("object", "list")
            put("data", dataArr)
        }
    }

    private fun handleFimCompletionsHttp(
        writer: PrintWriter,
        bodyString: String,
        clientIp: String
    ) = runBlocking {
        val startMs = System.currentTimeMillis()
        val reqJson = try {
            JSONObject(bodyString.ifBlank { "{}" })
        } catch (_: Exception) {
            JSONObject()
        }
        val cfg = configProvider()
        val model = reqJson.optString("model", cfg.activeModelId)
        val prompt = reqJson.optString("prompt", "")
        val suffix = reqJson.optString("suffix", "").takeIf { it.isNotEmpty() }

        val fimResult = engineBridge.generateFimCompletion(prompt, suffix)
        totalRequestsServed.incrementAndGet()
        val latencyMs = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)

        val responseJson = JSONObject().apply {
            put("id", "cmpl-${UUID.randomUUID().toString().take(12)}")
            put("object", "text_completion")
            put("created", System.currentTimeMillis() / 1000L)
            put("model", model)
            put("choices", JSONArray().put(
                JSONObject().apply {
                    put("text", fimResult.completionText)
                    put("index", 0)
                    put("finish_reason", "stop")
                }
            ))
            put("usage", JSONObject().apply {
                put("prompt_tokens", fimResult.promptTokens)
                put("completion_tokens", fimResult.completionTokens)
                put("total_tokens", fimResult.promptTokens + fimResult.completionTokens)
            })
        }

        writeJsonResponse(writer, 200, responseJson)

        onTelemetryEvent(
            TelemetryLogEntry(
                id = UUID.randomUUID().toString().take(8),
                timestampMs = System.currentTimeMillis(),
                method = "POST",
                endpoint = "/v1/completions (FIM)",
                clientAddress = clientIp,
                requestedModel = model,
                route = ExecutionRoute.NATIVE_GGUF_MMAP,
                rawPromptTokens = fimResult.promptTokens,
                compressedPromptTokens = fimResult.promptTokens,
                tokensSavedByDiet = 0,
                slidingWindowTruncatedTools = 0,
                gbnfGrammarApplied = false,
                gbnfRuleSummary = null,
                kvPrefixHitTokens = engineBridge.metrics.value.pinnedPrefixTokens,
                pldDraftedCount = fimResult.pldAcceptedTokens + 1,
                pldAcceptedCount = fimResult.pldAcceptedTokens,
                prefillTokPerSec = fimResult.prefillTokPerSec,
                decodeTokPerSec = fimResult.decodeTokPerSec,
                statusCode = 200,
                latencyMs = latencyMs,
                previewSnippet = fimResult.completionText.take(90)
            )
        )
    }

    private fun handleChatCompletionsHttp(
        writer: PrintWriter,
        bodyString: String,
        clientIp: String
    ) = runBlocking {
        val startMs = System.currentTimeMillis()
        val reqJson = try {
            JSONObject(bodyString.ifBlank { "{}" })
        } catch (_: Exception) {
            JSONObject()
        }

        val cfg = configProvider()
        val requestedModel = reqJson.optString("model", cfg.activeModelId)
        val stream = reqJson.optBoolean("stream", false)
        val temperature = reqJson.optDouble("temperature", 0.2)

        val messages = parseChatMessages(reqJson.optJSONArray("messages"))
        val tools = GbnfGrammarCompiler.parseToolsFromRequest(reqJson)

        val dietResult = HarnessDietMiddleware.processRequest(
            messages = messages,
            tools = tools,
            contextLimitTokens = cfg.contextSize,
            enableDietCompression = cfg.enableHarnessDiet,
            enableAstSkeletonizer = cfg.enableAstCodeSkeletonizer,
            enableGbnf = cfg.enableGbnfToolGrammar
        )
        totalTokensSavedByDiet.addAndGet(dietResult.tokensSaved.toLong())
        totalRequestsServed.incrementAndGet()

        val useHighThinkingCloudRoute = cfg.enableHybridHighThinking &&
            requestedModel.contains("gemini-4-argon", ignoreCase = true)

        val completionId = "chatcmpl-${UUID.randomUUID().toString().take(12)}"
        val createdSec = System.currentTimeMillis() / 1000L

        if (useHighThinkingCloudRoute) {
            handleHighThinkingRoute(
                writer = writer,
                stream = stream,
                completionId = completionId,
                createdSec = createdSec,
                requestedModel = requestedModel,
                dietResult = dietResult,
                clientIp = clientIp,
                startMs = startMs
            )
            return@runBlocking
        }

        val fullTextBuilder = StringBuilder()
        var toolCallName: String? = null
        var toolCallArgsJson: String? = null
        var lastPrefillTps = 220.0
        var lastDecodeTps = 58.0
        var pldDraftedSum = 0
        var pldAcceptedSum = 0
        var kvHitTokens = 0

        if (stream) {
            writeSseHeaders(writer)
        }

        engineBridge.generateChatStream(
            messages = dietResult.optimizedMessages,
            tools = tools,
            compiledGbnf = dietResult.compiledGbnfGrammar,
            temperature = temperature
        ).collect { step ->
            lastPrefillTps = step.prefillTokPerSec
            lastDecodeTps = step.decodeTokPerSec
            pldDraftedSum += step.pldDraftedThisStep
            pldAcceptedSum += step.pldAcceptedThisStep
            kvHitTokens = step.kvPrefixHitTokens

            if (step.isToolCall) {
                toolCallName = step.toolName
                toolCallArgsJson = step.fullToolCallArgumentsJson
                if (stream) {
                    val deltaChunk = buildToolCallStreamChunk(
                        id = completionId,
                        created = createdSec,
                        model = requestedModel,
                        toolName = step.toolName ?: "tool",
                        argumentsFragment = step.tokenChunk
                    )
                    writer.print("data: $deltaChunk\r\n\r\n")
                    writer.flush()
                }
            } else {
                fullTextBuilder.append(step.tokenChunk)
                if (stream) {
                    val deltaChunk = buildTextStreamChunk(
                        id = completionId,
                        created = createdSec,
                        model = requestedModel,
                        contentDelta = step.tokenChunk
                    )
                    writer.print("data: $deltaChunk\r\n\r\n")
                    writer.flush()
                }
            }
        }

        val latencyMs = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)

        if (stream) {
            val finishReason = if (toolCallName != null) "tool_calls" else "stop"
            val finalChunk = JSONObject().apply {
                put("id", completionId)
                put("object", "chat.completion.chunk")
                put("created", createdSec)
                put("model", requestedModel)
                put("choices", JSONArray().put(
                    JSONObject().apply {
                        put("index", 0)
                        put("delta", JSONObject())
                        put("finish_reason", finishReason)
                    }
                ))
            }
            writer.print("data: $finalChunk\r\n\r\n")
            writer.print("data: [DONE]\r\n\r\n")
            writer.flush()
        } else {
            val isDeepSeekReasoner = requestedModel.contains("reasoner", true) ||
                requestedModel.contains("R1", true)
            val responseObj = buildNonStreamChatCompletion(
                id = completionId,
                created = createdSec,
                model = requestedModel,
                content = fullTextBuilder.toString(),
                reasoningContent = if (isDeepSeekReasoner) {
                    "Evaluated via Local DeepSeek-R1 1.5B (LayerStream + PLD + GBNF)."
                } else null,
                toolName = toolCallName,
                toolArgsEnvelopeJson = toolCallArgsJson,
                promptTokens = dietResult.optimizedTokenEstimate,
                tokensSaved = dietResult.tokensSaved
            )
            writeJsonResponse(writer, 200, responseObj)
        }

        onTelemetryEvent(
            TelemetryLogEntry(
                id = UUID.randomUUID().toString().take(8),
                timestampMs = System.currentTimeMillis(),
                method = "POST",
                endpoint = if (stream) "/v1/chat/completions (SSE)" else "/v1/chat/completions",
                clientAddress = clientIp,
                requestedModel = requestedModel,
                route = ExecutionRoute.NATIVE_GGUF_MMAP,
                rawPromptTokens = dietResult.originalTokenEstimate,
                compressedPromptTokens = dietResult.optimizedTokenEstimate,
                tokensSavedByDiet = dietResult.tokensSaved,
                slidingWindowTruncatedTools = dietResult.truncatedToolMessagesCount,
                gbnfGrammarApplied = dietResult.compiledGbnfGrammar != null,
                gbnfRuleSummary = dietResult.gbnfRootSummary,
                kvPrefixHitTokens = kvHitTokens,
                pldDraftedCount = pldDraftedSum,
                pldAcceptedCount = pldAcceptedSum,
                prefillTokPerSec = lastPrefillTps,
                decodeTokPerSec = lastDecodeTps,
                statusCode = 200,
                latencyMs = latencyMs,
                previewSnippet = (toolCallArgsJson ?: fullTextBuilder.toString()).take(100)
            )
        )
    }

    private suspend fun handleHighThinkingRoute(
        writer: PrintWriter,
        stream: Boolean,
        completionId: String,
        createdSec: Long,
        requestedModel: String,
        dietResult: com.example.model.HarnessDietResult,
        clientIp: String,
        startMs: Long
    ) {
        val sb = StringBuilder()
        if (stream) {
            writeSseHeaders(writer)
        }
        highThinkingRepo.streamHighThinkingResponse(
            messages = dietResult.optimizedMessages,
            requestedModel = requestedModel
        ).collect { chunk ->
            sb.append(chunk)
            if (stream) {
                val delta = buildTextStreamChunk(completionId, createdSec, "gemini-4-argon", chunk)
                writer.print("data: $delta\r\n\r\n")
                writer.flush()
            }
        }
        val latencyMs = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
        if (stream) {
            writer.print("data: [DONE]\r\n\r\n")
            writer.flush()
        } else {
            val resp = buildNonStreamChatCompletion(
                id = completionId,
                created = createdSec,
                model = "gemini-4-argon",
                content = sb.toString(),
                reasoningContent = null,
                toolName = null,
                toolArgsEnvelopeJson = null,
                promptTokens = dietResult.optimizedTokenEstimate,
                tokensSaved = dietResult.tokensSaved
            )
            writeJsonResponse(writer, 200, resp)
        }

        onTelemetryEvent(
            TelemetryLogEntry(
                id = UUID.randomUUID().toString().take(8),
                timestampMs = System.currentTimeMillis(),
                method = "POST",
                endpoint = "/v1/chat/completions (High-Thinking)",
                clientAddress = clientIp,
                requestedModel = "gemini-4-argon",
                route = ExecutionRoute.HYBRID_GEMINI_HIGH_THINKING,
                rawPromptTokens = dietResult.originalTokenEstimate,
                compressedPromptTokens = dietResult.optimizedTokenEstimate,
                tokensSavedByDiet = dietResult.tokensSaved,
                slidingWindowTruncatedTools = dietResult.truncatedToolMessagesCount,
                gbnfGrammarApplied = false,
                gbnfRuleSummary = null,
                kvPrefixHitTokens = 0,
                pldDraftedCount = 0,
                pldAcceptedCount = 0,
                prefillTokPerSec = 240.0,
                decodeTokPerSec = 62.0,
                statusCode = 200,
                latencyMs = latencyMs,
                previewSnippet = sb.toString().take(100)
            )
        )
    }

    private fun parseChatMessages(arr: JSONArray?): List<ChatMessage> {
        if (arr == null) return emptyList()
        val list = ArrayList<ChatMessage>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val role = obj.optString("role", "user")
            val contentAny = obj.opt("content")
            val contentStr = when (contentAny) {
                is String -> contentAny
                is JSONArray -> {
                    val sb = StringBuilder()
                    for (j in 0 until contentAny.length()) {
                        val part = contentAny.optJSONObject(j)
                        if (part != null && part.optString("type") == "text") {
                            sb.append(part.optString("text"))
                        }
                    }
                    sb.toString()
                }
                else -> ""
            }
            list.add(
                ChatMessage(
                    role = role,
                    content = contentStr,
                    name = obj.optString("name", "").takeIf { it.isNotEmpty() },
                    toolCallId = obj.optString("tool_call_id", "").takeIf { it.isNotEmpty() }
                )
            )
        }
        return list
    }

    private fun buildTextStreamChunk(
        id: String,
        created: Long,
        model: String,
        contentDelta: String
    ): JSONObject = JSONObject().apply {
        put("id", id)
        put("object", "chat.completion.chunk")
        put("created", created)
        put("model", model)
        put("choices", JSONArray().put(
            JSONObject().apply {
                put("index", 0)
                put("delta", JSONObject().apply {
                    put("content", contentDelta)
                })
                put("finish_reason", JSONObject.NULL)
            }
        ))
    }

    private fun buildToolCallStreamChunk(
        id: String,
        created: Long,
        model: String,
        toolName: String,
        argumentsFragment: String
    ): JSONObject = JSONObject().apply {
        put("id", id)
        put("object", "chat.completion.chunk")
        put("created", created)
        put("model", model)
        put("choices", JSONArray().put(
            JSONObject().apply {
                put("index", 0)
                put("delta", JSONObject().apply {
                    put("tool_calls", JSONArray().put(
                        JSONObject().apply {
                            put("index", 0)
                            put("id", "call_${id.takeLast(8)}")
                            put("type", "function")
                            put("function", JSONObject().apply {
                                put("name", toolName)
                                put("arguments", argumentsFragment)
                            })
                        }
                    ))
                })
                put("finish_reason", JSONObject.NULL)
            }
        ))
    }

    private fun buildNonStreamChatCompletion(
        id: String,
        created: Long,
        model: String,
        content: String,
        reasoningContent: String?,
        toolName: String?,
        toolArgsEnvelopeJson: String?,
        promptTokens: Int,
        tokensSaved: Int
    ): JSONObject {
        val messageObj = JSONObject().apply {
            put("role", "assistant")
            if (reasoningContent != null) {
                put("reasoning_content", reasoningContent)
            }
            if (toolName != null && toolArgsEnvelopeJson != null) {
                val extractedArgs = try {
                    JSONObject(toolArgsEnvelopeJson).optJSONObject("arguments")?.toString()
                        ?: toolArgsEnvelopeJson
                } catch (_: Exception) {
                    toolArgsEnvelopeJson
                }
                put("content", JSONObject.NULL)
                put("tool_calls", JSONArray().put(
                    JSONObject().apply {
                        put("id", "call_${id.takeLast(8)}")
                        put("type", "function")
                        put("function", JSONObject().apply {
                            put("name", toolName)
                            put("arguments", extractedArgs)
                        })
                    }
                ))
            } else {
                put("content", content)
            }
        }

        val completionTokens = (content.length / 4).coerceAtLeast(8)
        return JSONObject().apply {
            put("id", id)
            put("object", "chat.completion")
            put("created", created)
            put("model", model)
            put("choices", JSONArray().put(
                JSONObject().apply {
                    put("index", 0)
                    put("message", messageObj)
                    put("finish_reason", if (toolName != null) "tool_calls" else "stop")
                }
            ))
            put("usage", JSONObject().apply {
                put("prompt_tokens", promptTokens)
                put("completion_tokens", completionTokens)
                put("total_tokens", promptTokens + completionTokens)
                put("harness_diet_tokens_saved", tokensSaved)
            })
        }
    }

    private fun writeSseHeaders(writer: PrintWriter) {
        writer.print("HTTP/1.1 200 OK\r\n")
        writer.print("Content-Type: text/event-stream; charset=utf-8\r\n")
        writer.print("Cache-Control: no-cache\r\n")
        writer.print("Connection: keep-alive\r\n")
        writer.print("Access-Control-Allow-Origin: *\r\n")
        writer.print("Access-Control-Allow-Headers: Authorization, Content-Type\r\n")
        writer.print("\r\n")
        writer.flush()
    }

    private fun writeJsonResponse(writer: PrintWriter, statusCode: Int, json: JSONObject) {
        val statusText = when (statusCode) {
            200 -> "OK"
            401 -> "Unauthorized"
            404 -> "Not Found"
            else -> "Error"
        }
        val bodyBytes = json.toString().toByteArray(StandardCharsets.UTF_8)
        writer.print("HTTP/1.1 $statusCode $statusText\r\n")
        writer.print("Content-Type: application/json; charset=utf-8\r\n")
        writer.print("Content-Length: ${bodyBytes.size}\r\n")
        writer.print("Access-Control-Allow-Origin: *\r\n")
        writer.print("Access-Control-Allow-Headers: Authorization, Content-Type\r\n")
        writer.print("Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n")
        writer.print("Connection: close\r\n")
        writer.print("\r\n")
        writer.print(String(bodyBytes, StandardCharsets.UTF_8))
        writer.flush()
    }
}
