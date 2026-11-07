package com.example.ai

import com.example.model.ChatMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * High-Thinking Cloud Router Interface (`gemini-4-argon` with `ThinkingLevel.HIGH`).
 *
 * Per Firebase AI Logic integration rules: because Firebase setup was declined during provisioning,
 * all cloud Gemini High-Thinking requests are routed through this [AiRepository] interface backed
 * by [PlaceholderHighThinkingAiRepository], which returns clearly labeled sample high-thinking
 * reasoning responses and informs the user that live Gemini cloud calls require enabling Firebase AI Logic.
 */
interface AiRepository {
    val isFirebaseConnected: Boolean
    val statusNotice: String

    fun streamHighThinkingResponse(
        messages: List<ChatMessage>,
        requestedModel: String = "gemini-4-argon"
    ): Flow<String>
}

class PlaceholderHighThinkingAiRepository : AiRepository {

    override val isFirebaseConnected: Boolean = false

    override val statusNotice: String =
        "Cloud Gemini 4 Argon (ThinkingLevel.HIGH) requires Firebase AI Logic. Returning labeled sample High-Thinking trace while local C++ GGUF inference runs natively on-device."

    override fun streamHighThinkingResponse(
        messages: List<ChatMessage>,
        requestedModel: String
    ): Flow<String> = flow {
        val lastUserPrompt = messages.lastOrNull { it.role == "user" }?.content.orEmpty()
        val sampleChunks = listOf(
            "[SAMPLE HIGH-THINKING RESPONSE — Enable Firebase AI Logic for live gemini-4-argon ThinkingLevel.HIGH]\n",
            "<thinking>\n",
            "1. Analyzing architectural query: \"${lastUserPrompt.take(72)}\"\n",
            "2. Evaluating memory constraints (4GB/6GB ARM64 target, Q8_0/Q4_0 KV cache quantization, Flash Attention).\n",
            "3. Verifying GBNF tool call schema determinism and Prefix KV slot pinning (n_keep).\n",
            "</thinking>\n\n",
            "### High-Thinking Architectural Recommendation (Sample)\n",
            "- **Zero-Copy Weights**: Keep `use_mmap = true` and `use_mlock = false` so the Linux kernel page cache evicts cold pages cleanly.\n",
            "- **Hybrid Escalation**: Enable Firebase AI Logic in AI Studio to connect this `gemini-4-argon` (`ThinkingLevel.HIGH`) route to live cloud inference.\n"
        )
        for (chunk in sampleChunks) {
            delay(20L)
            emit(chunk)
        }
    }

    /**
     * Heuristic used by the Hybrid Router when `enableHybridHighThinking` is active:
     * Escalates requests that explicitly ask for `gemini-4-argon` / `deepseek-reasoner`
     * or contain deep multi-system architectural reasoning markers.
     */
    fun shouldEscalateToHighThinking(requestedModel: String, messages: List<ChatMessage>): Boolean {
        val modelLower = requestedModel.lowercase()
        if (modelLower.contains("gemini-4-argon") || modelLower.contains("deepseek-reasoner")) {
            return true
        }
        val latest = messages.lastOrNull { it.role == "user" }?.content?.lowercase().orEmpty()
        return latest.contains("[high-thinking]") ||
            latest.contains("architectural tradeoff") ||
            latest.contains("prove formal correctness")
    }
}
