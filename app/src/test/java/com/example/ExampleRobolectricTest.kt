package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.service.DaemonStateRegistry
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `verify app name and DaemonStateRegistry initialization`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("PocketCodeD", appName)

        DaemonStateRegistry.ensureInitialized(context)
        val profile = DaemonStateRegistry.hardwareProfile.value
        assertTrue(profile != null && profile.totalRamMb > 0)

        val primaryKey = DaemonStateRegistry.apiKeyManager.getPrimaryKey()
        assertTrue(primaryKey.key.startsWith("sk-mobile-"))
        assertTrue(DaemonStateRegistry.apiKeyManager.validateBearerHeader("Bearer ${primaryKey.key}"))
        assertFalse(DaemonStateRegistry.apiKeyManager.validateBearerHeader("Bearer sk-mobile-invalid"))

        val opencodeConfig = DaemonStateRegistry.apiKeyManager.buildOpencodeJsonConfig(
            bindLan = false,
            port = 8080,
            activeModelId = "Qwen2.5-Coder-1.5B-Instruct-Q4_K_M.gguf"
        )
        assertTrue(opencodeConfig.contains("@opencode/ai/providers/openai-compatible"))
        assertTrue(opencodeConfig.contains("http://127.0.0.1:8080/v1"))
    }

    @Test
    fun `verify embedded OpenAI and DeepSeek HTTP server endpoints and Bearer auth`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        DaemonStateRegistry.ensureInitialized(context)

        val started = DaemonStateRegistry.startServerInternal(bindLan = false, port = 0)
        assertTrue(started)
        val port = DaemonStateRegistry.serverPort.value
        val validKey = DaemonStateRegistry.apiKeyManager.getPrimaryKey().key

        try {
            // 1. GET /health (unauthenticated health probe)
            val healthConn = (URL("http://127.0.0.1:$port/health").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
            }
            assertEquals(200, healthConn.responseCode)
            val healthJson = JSONObject(healthConn.inputStream.bufferedReader().readText())
            assertEquals("ok", healthJson.getString("status"))

            // 2. GET /v1/models without Authorization header -> 401 Unauthorized
            val unauthConn = (URL("http://127.0.0.1:$port/v1/models").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
            }
            assertEquals(401, unauthConn.responseCode)

            // 3. GET /v1/models with valid Bearer sk-mobile-... key -> 200 OK
            val modelsConn = (URL("http://127.0.0.1:$port/v1/models").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer $validKey")
            }
            assertEquals(200, modelsConn.responseCode)
            val modelsBody = modelsConn.inputStream.bufferedReader().readText()
            assertTrue(modelsBody.contains("qwen2.5-coder"))
            assertTrue(modelsBody.contains("deepseek-coder"))

            // 4. POST /v1/chat/completions with tool schema -> GBNF valid tool_calls response
            val chatConn = (URL("http://127.0.0.1:$port/v1/chat/completions").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Authorization", "Bearer $validKey")
                setRequestProperty("Content-Type", "application/json")
            }
            val payload = """
                {
                  "model": "qwen2.5-coder",
                  "stream": false,
                  "messages": [
                    {"role": "system", "content": "You are opencode."},
                    {"role": "user", "content": "Edit app/src/main/cpp/PocketEngine.cpp to enable flash_attn"}
                  ],
                  "tools": [
                    {
                      "type": "function",
                      "function": {
                        "name": "edit_file",
                        "parameters": {
                          "type": "object",
                          "properties": {
                            "file_path": {"type": "string"},
                            "patch": {"type": "string"}
                          }
                        }
                      }
                    }
                  ]
                }
            """.trimIndent()
            OutputStreamWriter(chatConn.outputStream).use { it.write(payload) }
            assertEquals(200, chatConn.responseCode)
            val chatResp = BufferedReader(InputStreamReader(chatConn.inputStream)).readText()
            assertTrue(chatResp.contains("tool_calls"))
            assertTrue(chatResp.contains("edit_file"))
        } finally {
            DaemonStateRegistry.stopServerInternal()
        }
    }
}
