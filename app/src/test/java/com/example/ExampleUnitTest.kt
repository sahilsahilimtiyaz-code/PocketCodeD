package com.example

import com.example.engine.MicroGgufSynthesizer
import com.example.engine.PocketEngineBridge
import com.example.middleware.GbnfGrammarCompiler
import com.example.middleware.HarnessDietMiddleware
import com.example.model.ChatMessage
import com.example.model.EngineConfig
import com.example.model.GgmlQuantType
import com.example.model.ToolFunctionSpec
import com.example.model.ToolPropertySpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ExampleUnitTest {

    @Test
    fun `micro GGUF v3 synthesizer writes valid magic and parses via zero-copy mmap`() {
        val tempDir = Files.createTempDirectory("pocketcoded_gguf_test").toFile()
        try {
            val ggufFile = MicroGgufSynthesizer.ensureMicroGgufExists(tempDir)
            assertTrue(ggufFile.exists())
            assertTrue(ggufFile.length() >= 64 * 1024L)

            val parsed = MicroGgufSynthesizer.parseGgufViaZeroCopyMmap(ggufFile)
            assertNotNull(parsed)
            val (header, mappedBuf) = parsed!!
            assertTrue(header.magicValid)
            assertEquals(3, header.version)
            assertEquals("qwen2", header.architecture)
            assertTrue(mappedBuf.isDirect)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `Harness Diet middleware compresses bloated agent system prompts and applies 85 percent sliding window`() {
        val verboseSystemPrompt = buildString {
            appendLine("You are opencode, an interactive CLI tool.")
            appendLine("CWD: /data/data/com.termux/files/home/project")
            repeat(40) {
                appendLine("IMPORTANT: When making changes to files, first understand the file's code conventions in src/main/Engine.kt.")
            }
        }
        val hugeToolOutput = "File dump of app/src/main/cpp/PocketEngine.cpp:\n" + "int x = 1;\n".repeat(400)

        val messages = listOf(
            ChatMessage("system", verboseSystemPrompt),
            ChatMessage("user", "Read the engine file"),
            ChatMessage("tool", hugeToolOutput),
            ChatMessage("assistant", "Read complete."),
            ChatMessage("user", "Check memory mapping"),
            ChatMessage("assistant", "Checking mmap."),
            ChatMessage("user", "Now enable Flash Attention in app/src/main/cpp/PocketEngine.cpp")
        )

        val tools = listOf(
            ToolFunctionSpec(
                name = "edit_file",
                description = "Edit file",
                parametersSchemaJson = """{"type":"object","required":["file_path","patch"]}""",
                requiredFields = listOf("file_path", "patch"),
                properties = mapOf(
                    "file_path" to ToolPropertySpec("file_path", "string"),
                    "patch" to ToolPropertySpec("patch", "string"),
                    "mode" to ToolPropertySpec("mode", "string", enumValues = listOf("replace", "insert"))
                )
            )
        )

        val result = HarnessDietMiddleware.processRequest(
            messages = messages,
            tools = tools,
            contextLimitTokens = 600, // Force 85% sliding window trigger
            enableDietCompression = true,
            enableGbnf = true
        )

        assertTrue(result.systemPromptCompressed)
        assertTrue(result.tokensSaved > 200)
        assertTrue(result.preservedFilePaths.contains("app/src/main/cpp/PocketEngine.cpp"))
        assertNotNull(result.compiledGbnfGrammar)
        assertTrue(result.compiledGbnfGrammar!!.contains("root ::="))
        assertTrue(result.compiledGbnfGrammar!!.contains("edit_file"))
        assertTrue(result.compiledGbnfGrammar!!.contains("replace"))
    }

    @Test
    fun `PocketEngineBridge pins prefix tokens, drafts PLD n-gram tokens, and flushes non-pinned KV on OOM`() {
        val tempDir = Files.createTempDirectory("pocketcoded_engine_test").toFile()
        try {
            val ggufFile = File(tempDir, "test_model.gguf")
            MicroGgufSynthesizer.writeValidGgufV3Binary(
                targetFile = ggufFile,
                architecture = "qwen2",
                modelName = "Qwen2.5-Coder-1.5B-Instruct-Q4_K_M",
                contextLength = 4096,
                embeddingLength = 1536,
                blockCount = 28,
                kvHeadCount = 2,
                tensorBlocksKb = 128
            )

            val bridge = PocketEngineBridge()
            val cfg = EngineConfig(
                contextSize = 4096,
                typeK = GgmlQuantType.Q8_0,
                typeV = GgmlQuantType.Q4_0,
                enablePld = true
            )
            assertTrue(bridge.loadModel(ggufFile, cfg))

            // 1. Pin system prefix
            val (pinnedCount, firstHit) = bridge.evaluateAndPinSystemPrefix("System prompt with tools read_file edit_file")
            assertTrue(pinnedCount > 0)
            assertEquals(false, firstHit)

            // Second evaluation with identical prefix should be a KV prefix cache hit
            val (_, secondHit) = bridge.evaluateAndPinSystemPrefix("System prompt with tools read_file edit_file")
            assertEquals(true, secondHit)

            // 2. Test Prompt Lookup Decoding (PLD) n-gram matching
            val sampleCode = "val flashAttn = true\nval useMmap = true\nval flashAttn = true"
            val tokens = bridge.tokenizeCode(sampleCode)
            val drafted = bridge.lookupPldSpeculativeDraft(tokens, ngramLen = 3, maxDraft = 4)
            assertTrue(drafted.isNotEmpty())

            // 3. Test Emergency OOM Guard flushing non-pinned tokens while retaining pinned prefix
            bridge.emergencyFlushNonPinnedKvCache("TRIM_MEMORY_RUNNING_CRITICAL")
            assertEquals(pinnedCount, bridge.metrics.value.kvTokensUsed)

            // 4. Test Persistent Disk-Backed KV Session Snapshot save & zero-prefill restore
            val snapshotFile = File(tempDir, "kv_session.bin")
            val savedOk = bridge.saveKvSessionToDisk(snapshotFile)
            assertTrue(savedOk)
            val restoredTokens = bridge.restoreKvSessionFromDisk(snapshotFile)
            assertTrue(restoredTokens > 0)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `AST-aware semantic code skeletonizer preserves signatures and folds deep implementation bodies`() {
        val largeSourceDump = buildString {
            appendLine("package com.example.engine")
            appendLine("import java.nio.ByteBuffer")
            appendLine("class HeavyEngine {")
            appendLine("    fun computeMatrix(input: FloatArray): FloatArray {")
            repeat(30) { idx ->
                appendLine("        val step$idx = input.size * $idx")
            }
            appendLine("        return input")
            appendLine("    }")
            appendLine("}")
        }

        val (skeleton, wasSkeletonized) = HarnessDietMiddleware.skeletonizeReferenceCode(largeSourceDump)
        assertTrue(wasSkeletonized)
        assertTrue(skeleton.contains("class HeavyEngine"))
        assertTrue(skeleton.contains("fun computeMatrix"))
        assertTrue(skeleton.contains("AST Skeletonizer elided"))
        assertTrue(skeleton.length < largeSourceDump.length)
    }

    @Test
    fun `PocketEngineBridge generates L2-normalized 64d code embeddings and memoizes tokenized blocks`() {
        val bridge = PocketEngineBridge()
        val codeSnippet = "fun executeZeroCopyInference(promptTokens: IntArray): Flow<String>"
        val tokensFirst = bridge.tokenizeCode(codeSnippet)
        val tokensCached = bridge.tokenizeCode(codeSnippet)
        assertTrue(tokensFirst === tokensCached)

        val embedding = bridge.generateCodeEmbedding(codeSnippet, dimensions = 64)
        assertEquals(64, embedding.size)
        val normSq = embedding.sumOf { (it * it).toDouble() }
        assertTrue(normSq in 0.90..1.10)
    }

    @Test
    fun `PocketEngineBridge synthesizes multi-language code and chat responses offline`() {
        val bridge = PocketEngineBridge()

        val pythonChunks = StringBuilder()
        kotlinx.coroutines.runBlocking {
            bridge.generateChatStream(
                messages = listOf(ChatMessage("user", "Write a Python function for binary search with example tests")),
                tools = emptyList(),
                compiledGbnf = null
            ).collect { step ->
                pythonChunks.append(step.tokenChunk)
            }
        }
        val pythonOutput = pythonChunks.toString()
        assertTrue(pythonOutput.contains("def binary_search"))
        assertTrue(pythonOutput.contains("```python"))

        val htmlChunks = StringBuilder()
        kotlinx.coroutines.runBlocking {
            bridge.generateChatStream(
                messages = listOf(ChatMessage("user", "Create a responsive HTML and CSS landing page")),
                tools = emptyList(),
                compiledGbnf = null
            ).collect { step ->
                htmlChunks.append(step.tokenChunk)
            }
        }
        val htmlOutput = htmlChunks.toString()
        assertTrue(htmlOutput.contains("<!DOCTYPE html>"))
    }
}


