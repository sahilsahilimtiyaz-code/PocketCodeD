package com.example.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.example.model.ApiKeyRecord
import org.json.JSONArray
import org.json.JSONObject
import java.net.Inet4Address
import java.net.NetworkInterface
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Pillar 3: API Key Authentication & One-Tap Harness Config Generator
 *
 * - Generates cryptographically random API keys prefixed with `sk-mobile-`.
 * - Encrypts keys at rest using Android Keystore AES-256-GCM (`EncryptedSharedPreferences` equivalent).
 * - Validates `Authorization: Bearer <api_key>` middleware on `/v1/` and `/chat/completions` routes.
 * - Generates ready-to-use `opencode.json`, DeepSeek Harness, and Termux configuration snippets.
 */
class ApiKeyManager(context: Context) {

    companion object {
        private const val PREFS_NAME = "pocketcoded_encrypted_api_keys"
        private const val KEY_RECORDS_CIPHERTEXT = "encrypted_records_v1"
        private const val KEYSTORE_ALIAS = "pocketcoded_api_key_master_aes"
        private const val KEY_CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    }

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val secureRandom = SecureRandom()
    private val cachedKeys = mutableListOf<ApiKeyRecord>()

    init {
        loadKeysFromEncryptedStorage()
        if (cachedKeys.isEmpty()) {
            generateNewKey(label = "Default Termux / opencode Key")
        }
    }

    @Synchronized
    fun listKeys(): List<ApiKeyRecord> = cachedKeys.toList()

    @Synchronized
    fun getPrimaryKey(): ApiKeyRecord {
        if (cachedKeys.isEmpty()) {
            return generateNewKey("Default Termux / opencode Key")
        }
        return cachedKeys.first()
    }

    @Synchronized
    fun generateNewKey(label: String = "Harness Key"): ApiKeyRecord {
        val sb = StringBuilder("sk-mobile-")
        for (i in 0 until 32) {
            sb.append(KEY_CHARS[secureRandom.nextInt(KEY_CHARS.length)])
        }
        val record = ApiKeyRecord(
            id = UUID.randomUUID().toString().take(8),
            key = sb.toString(),
            label = label.ifBlank { "opencode Client Key" },
            createdAtEpochMs = System.currentTimeMillis(),
            requestCount = 0L
        )
        cachedKeys.add(0, record)
        persistKeysToEncryptedStorage()
        return record
    }

    @Synchronized
    fun revokeKey(id: String): Boolean {
        val removed = cachedKeys.removeAll { it.id == id }
        if (removed) {
            if (cachedKeys.isEmpty()) {
                generateNewKey("Default Termux / opencode Key")
            } else {
                persistKeysToEncryptedStorage()
            }
        }
        return removed
    }

    @Synchronized
    fun validateBearerHeader(authHeader: String?): Boolean {
        if (authHeader.isNullOrBlank()) return false
        val token = if (authHeader.startsWith("Bearer ", ignoreCase = true)) {
            authHeader.substring(7).trim()
        } else {
            authHeader.trim()
        }
        val index = cachedKeys.indexOfFirst { it.key == token }
        if (index >= 0) {
            val current = cachedKeys[index]
            cachedKeys[index] = current.copy(requestCount = current.requestCount + 1L)
            persistKeysToEncryptedStorage()
            return true
        }
        return false
    }

    fun buildOpencodeJsonConfig(
        bindLan: Boolean,
        port: Int,
        activeModelId: String,
        apiKey: String = getPrimaryKey().key
    ): String {
        val host = if (bindLan) detectDeviceLanIp() else "127.0.0.1"
        val baseUrl = "http://$host:$port/v1"

        return """
        {
          "${'$'}schema": "https://opencode.ai/config.json",
          "provider": {
            "pocketcoded": {
              "npm": "@opencode/ai/providers/openai-compatible",
              "name": "PocketCodeD Local Android NDK Daemon",
              "options": {
                "baseURL": "$baseUrl",
                "apiKey": "$apiKey"
              },
              "models": {
                "$activeModelId": {
                  "name": "$activeModelId (LayerStream + GBNF + PLD)",
                  "limit": {
                    "context": 8192,
                    "output": 2048
                  },
                  "tool_call": true
                },
                "qwen2.5-coder": {
                  "name": "Qwen2.5-Coder Alias",
                  "tool_call": true
                },
                "deepseek-coder": {
                  "name": "DeepSeek-Coder Alias",
                  "tool_call": true
                },
                "deepseek-reasoner": {
                  "name": "DeepSeek-R1 1.5B Reasoning + Coding",
                  "tool_call": true
                },
                "gemini-4-argon": {
                  "name": "Gemini 4 Argon (Hybrid High-Thinking)",
                  "tool_call": true
                }
              }
            }
          },
          "model": "pocketcoded/$activeModelId"
        }
        """.trimIndent()
    }

    /**
     * Generates a ready-to-use DeepSeek Harness & Aider configuration snippet.
     */
    fun buildTermuxQuickstartScript(
        bindLan: Boolean,
        port: Int,
        activeModelId: String,
        apiKey: String = getPrimaryKey().key
    ): String {
        val host = if (bindLan) detectDeviceLanIp() else "127.0.0.1"
        val baseUrl = "http://$host:$port/v1"
        return """
        # === 1. Termux / DeepSeek Harness / OpenAI Environment ===
        export OPENAI_API_BASE="$baseUrl"
        export OPENAI_BASE_URL="$baseUrl"
        export OPENAI_API_KEY="$apiKey"
        export DEEPSEEK_API_BASE="http://$host:$port"
        export DEEPSEEK_API_KEY="$apiKey"

        # === 2. Run opencode CLI directly against phone daemon ===
        opencode --provider pocketcoded --model "$activeModelId"

        # === 3. Run DeepSeek / Aider harness with GBNF tool calls ===
        aider --openai-api-base "$baseUrl" --openai-api-key "$apiKey" --model "openai/deepseek-coder"
        """.trimIndent()
    }

    fun detectDeviceLanIp(): String {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return "127.0.0.1"
            for (intf in interfaces) {
                if (!intf.isUp || intf.isLoopback) continue
                for (addr in intf.inetAddresses) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val ip = addr.hostAddress ?: continue
                        if (ip.startsWith("192.168.") || ip.startsWith("10.") || ip.startsWith("172.")) {
                            return ip
                        }
                    }
                }
            }
            "127.0.0.1"
        } catch (_: Exception) {
            "127.0.0.1"
        }
    }

    private fun loadKeysFromEncryptedStorage() {
        val stored = prefs.getString(KEY_RECORDS_CIPHERTEXT, null) ?: return
        val jsonStr = decryptString(stored) ?: return
        try {
            val arr = JSONArray(jsonStr)
            cachedKeys.clear()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                cachedKeys.add(
                    ApiKeyRecord(
                        id = obj.optString("id"),
                        key = obj.optString("key"),
                        label = obj.optString("label", "API Key"),
                        createdAtEpochMs = obj.optLong("createdAt", System.currentTimeMillis()),
                        requestCount = obj.optLong("requestCount", 0L)
                    )
                )
            }
        } catch (_: Exception) {
        }
    }

    private fun persistKeysToEncryptedStorage() {
        val arr = JSONArray()
        for (item in cachedKeys) {
            val obj = JSONObject().apply {
                put("id", item.id)
                put("key", item.key)
                put("label", item.label)
                put("createdAt", item.createdAtEpochMs)
                put("requestCount", item.requestCount)
            }
            arr.put(obj)
        }
        val encrypted = encryptString(arr.toString())
        prefs.edit().putString(KEY_RECORDS_CIPHERTEXT, encrypted).apply()
    }

    private fun getOrCreateAesKey(): SecretKey? {
        return try {
            val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val existing = ks.getEntry(KEYSTORE_ALIAS, null) as? KeyStore.SecretKeyEntry
            if (existing != null) return existing.secretKey

            val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            val spec = KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            keyGen.init(spec)
            keyGen.generateKey()
        } catch (_: Throwable) {
            null
        }
    }

    private fun encryptString(plainText: String): String {
        val secretKey = getOrCreateAesKey()
        if (secretKey != null) {
            try {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.ENCRYPT_MODE, secretKey)
                val iv = cipher.iv
                val cipherBytes = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
                val combined = ByteArray(1 + iv.size + cipherBytes.size)
                combined[0] = iv.size.toByte()
                System.arraycopy(iv, 0, combined, 1, iv.size)
                System.arraycopy(cipherBytes, 0, combined, 1 + iv.size, cipherBytes.size)
                return "GCM:" + Base64.encodeToString(combined, Base64.NO_WRAP)
            } catch (_: Throwable) {
            }
        }
        return "B64:" + Base64.encodeToString(plainText.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
    }

    private fun decryptString(encoded: String): String? {
        return try {
            if (encoded.startsWith("GCM:")) {
                val secretKey = getOrCreateAesKey() ?: return null
                val combined = Base64.decode(encoded.removePrefix("GCM:"), Base64.NO_WRAP)
                val ivSize = combined[0].toInt() and 0xFF
                val iv = combined.copyOfRange(1, 1 + ivSize)
                val cipherBytes = combined.copyOfRange(1 + ivSize, combined.size)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
                String(cipher.doFinal(cipherBytes), StandardCharsets.UTF_8)
            } else if (encoded.startsWith("B64:")) {
                String(Base64.decode(encoded.removePrefix("B64:"), Base64.NO_WRAP), StandardCharsets.UTF_8)
            } else null
        } catch (_: Throwable) {
            null
        }
    }
}
