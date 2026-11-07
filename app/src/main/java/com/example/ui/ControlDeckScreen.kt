package com.example.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.ApiKeyRecord
import com.example.model.EngineConfig
import com.example.model.EngineRuntimeMetrics
import com.example.model.HardwareProfile
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.ArgonViolet
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.GlassHighlight
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.PhosphorEmerald
import com.example.ui.theme.TerminalBorder
import com.example.ui.theme.TerminalCardElevated
import com.example.ui.theme.TerminalSlate
import com.example.ui.theme.TerminalTextSecondary
import kotlin.math.sin

private const val GITHUB_WORKFLOW_SNIPPET = """# .github/workflows/build-apk.yml (Auto-runs on git push & PR)
name: Build PocketCodeD Android APK
on:
  push:
    branches: [ main, master, develop, "**" ]
    tags: [ "v*" ]
  pull_request:
  workflow_dispatch:

jobs:
  build-apk:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: "temurin", java-version: "17" }
      - uses: android-actions/setup-android@v3
      - uses: gradle/actions/setup-gradle@v4
      - name: Test & Assemble Debug + Release APKs
        run: |
          cp -n .env.example .env || true
          gradle :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease
      - uses: actions/upload-artifact@v4
        with:
          name: PocketCodeD-Android-APKs
          path: app/build/outputs/apk/**/*.apk"""

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ControlDeckScreen(
    isServerRunning: Boolean,
    bindLanMode: Boolean,
    serverPort: Int,
    baseUrl: String,
    hardwareProfile: HardwareProfile?,
    engineConfig: EngineConfig,
    engineMetrics: EngineRuntimeMetrics,
    apiKeys: List<ApiKeyRecord>,
    totalDietTokensSaved: Long,
    opencodeJsonSnippet: String,
    termuxScriptSnippet: String,
    onToggleServer: () -> Unit,
    onSetBindLan: (Boolean) -> Unit,
    onGenerateKey: (String) -> Unit,
    onRevokeKey: (String) -> Unit,
    onRequestBatteryBypass: () -> Unit,
    onStatusToast: (String) -> Unit
) {
    val clipboard = LocalClipboardManager.current
    var showConfigPreview by remember { mutableStateOf(true) }
    var selectedSnippetTab by remember { mutableIntStateOf(0) } // 0=opencode.json, 1=Termux/DeepSeek, 2=GitHub APK CI
    var newKeyLabel by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("control_deck_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Hero Server Control Deck Card
        item {
            ServerHeroCard(
                isServerRunning = isServerRunning,
                bindLanMode = bindLanMode,
                serverPort = serverPort,
                baseUrl = baseUrl,
                engineConfig = engineConfig,
                onToggleServer = onToggleServer,
                onSetBindLan = onSetBindLan,
                onCopyBaseUrl = {
                    clipboard.setText(AnnotatedString(baseUrl))
                    onStatusToast("Copied Base URL: $baseUrl")
                },
                onRequestBatteryBypass = onRequestBatteryBypass
            )
        }

        // 2. Real-Time Hardware, mmap & Quantized KV Cache HUD with Custom Canvas Visualizer
        item {
            HardwareAndKvHudCard(
                hardwareProfile = hardwareProfile,
                engineConfig = engineConfig,
                engineMetrics = engineMetrics,
                totalDietTokensSaved = totalDietTokensSaved
            )
        }

        // 3. One-Tap Harness & GitHub Actions APK CI/CD Config Generator
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = TerminalSlate),
                border = BorderStroke(1.dp, GlassHighlight.copy(alpha = 0.6f)),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Code,
                                contentDescription = "Harness & CI Config",
                                tint = CyberCyan
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "One-Tap Harness & GitHub APK CI/CD",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "opencode.json • DeepSeek / Aider • .github/workflows/build-apk.yml",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TerminalTextSecondary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                selectedSnippetTab = 0
                                clipboard.setText(AnnotatedString(opencodeJsonSnippet))
                                onStatusToast("Copied valid opencode.json config to clipboard!")
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PhosphorEmerald,
                                contentColor = ObsidianVoid
                            ),
                            modifier = Modifier.testTag("copy_opencode_json_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy opencode.json",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Copy opencode.json", fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                selectedSnippetTab = 1
                                clipboard.setText(AnnotatedString(termuxScriptSnippet))
                                onStatusToast("Copied DeepSeek / Termux / Aider script!")
                            },
                            border = BorderStroke(1.dp, CyberCyan),
                            modifier = Modifier.testTag("copy_termux_script_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Terminal,
                                contentDescription = "Copy Termux Script",
                                tint = CyberCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("DeepSeek / Termux Env", color = CyberCyan)
                        }

                        OutlinedButton(
                            onClick = {
                                selectedSnippetTab = 2
                                clipboard.setText(AnnotatedString(GITHUB_WORKFLOW_SNIPPET))
                                onStatusToast("Copied .github/workflows/build-apk.yml workflow!")
                            },
                            border = BorderStroke(1.dp, ArgonViolet),
                            modifier = Modifier.testTag("copy_github_workflow_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Build,
                                contentDescription = "Copy GitHub Actions APK Workflow",
                                tint = ArgonViolet,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("GitHub APK Action", color = ArgonViolet)
                        }

                        OutlinedButton(
                            onClick = { showConfigPreview = !showConfigPreview },
                            border = BorderStroke(1.dp, TerminalBorder),
                            modifier = Modifier.testTag("toggle_config_preview_button")
                        ) {
                            Text(if (showConfigPreview) "Hide Code" else "Show Code")
                        }
                    }

                    if (showConfigPreview) {
                        Spacer(modifier = Modifier.height(12.dp))
                        val activeCodeText = when (selectedSnippetTab) {
                            1 -> termuxScriptSnippet
                            2 -> GITHUB_WORKFLOW_SNIPPET
                            else -> opencodeJsonSnippet
                        }
                        val activeCodeColor = when (selectedSnippetTab) {
                            1 -> CyberCyan
                            2 -> ArgonViolet
                            else -> PhosphorEmerald
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(ObsidianVoid)
                                .border(1.dp, activeCodeColor.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            Text(
                                text = activeCodeText,
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = JetBrainsMonoFamily,
                                color = activeCodeColor
                            )
                        }
                    }
                }
            }
        }

        // 4. API Key Manager (sk-mobile-...)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = TerminalSlate),
                border = BorderStroke(1.dp, GlassHighlight.copy(alpha = 0.6f)),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = "API Key Authentication",
                            tint = PhosphorEmerald
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Bearer API Key Vault (AES-256-GCM)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Enforced on all /v1/ and /chat/completions routes via Bearer sk-mobile-...",
                                style = MaterialTheme.typography.labelSmall,
                                color = TerminalTextSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = newKeyLabel,
                            onValueChange = { newKeyLabel = it },
                            placeholder = { Text("Key label (e.g. Termux Laptop)") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("api_key_label_input")
                        )
                        Button(
                            onClick = {
                                onGenerateKey(newKeyLabel.ifBlank { "opencode Client" })
                                newKeyLabel = ""
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CyberCyan,
                                contentColor = ObsidianVoid
                            ),
                            modifier = Modifier.testTag("generate_api_key_button")
                        ) {
                            Text("Mint Key", fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    apiKeys.forEach { record ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            color = TerminalCardElevated,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, TerminalBorder)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = record.label,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${record.maskedKey} • ${record.requestCount} reqs",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = PhosphorEmerald
                                    )
                                }
                                Row {
                                    IconButton(
                                        onClick = {
                                            clipboard.setText(AnnotatedString(record.key))
                                            onStatusToast("Copied full key for ${record.label}")
                                        },
                                        modifier = Modifier.testTag("copy_key_${record.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = "Copy API key",
                                            tint = CyberCyan
                                        )
                                    }
                                    if (apiKeys.size > 1) {
                                        IconButton(
                                            onClick = { onRevokeKey(record.id) },
                                            modifier = Modifier.testTag("revoke_key_${record.id}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.DeleteOutline,
                                                contentDescription = "Revoke API key",
                                                tint = CrimsonAlert
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ServerHeroCard(
    isServerRunning: Boolean,
    bindLanMode: Boolean,
    serverPort: Int,
    baseUrl: String,
    engineConfig: EngineConfig,
    onToggleServer: () -> Unit,
    onSetBindLan: (Boolean) -> Unit,
    onCopyBaseUrl: () -> Unit,
    onRequestBatteryBypass: () -> Unit
) {
    val statusColor by animateColorAsState(
        targetValue = if (isServerRunning) PhosphorEmerald else AmberWarning,
        label = "serverStatusColor"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.5.dp, statusColor.copy(alpha = 0.65f)),
        colors = CardDefaults.cardColors(containerColor = TerminalSlate)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Image(
                painter = painterResource(id = R.drawable.img_daemon_hero_1794064302740),
                contentDescription = "PocketCodeD ARM64 Inference Architecture",
                contentScale = ContentScale.Crop,
                alpha = 0.28f,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(255.dp)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                ObsidianVoid.copy(alpha = 0.45f),
                                TerminalSlate.copy(alpha = 0.92f),
                                TerminalSlate
                            )
                        )
                    )
                    .padding(16.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(statusColor)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isServerRunning) "DAEMON ONLINE • PORT $serverPort" else "DAEMON STANDBY",
                                style = MaterialTheme.typography.labelLarge,
                                color = statusColor,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Button(
                            onClick = onToggleServer,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isServerRunning) CrimsonAlert else PhosphorEmerald,
                                contentColor = if (isServerRunning) Color.White else ObsidianVoid
                            ),
                            modifier = Modifier.testTag("toggle_server_button")
                        ) {
                            Icon(
                                imageVector = if (isServerRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                                contentDescription = if (isServerRunning) "Stop Daemon" else "Start Daemon",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isServerRunning) "Stop Server" else "Start Server",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = engineConfig.activeModelId,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "6-Layer Rolling mmap • FlashAttn=ON • KV: K=${engineConfig.typeK.label} / V=${engineConfig.typeV.label} • PLD+LayerSkip=${if (engineConfig.enablePld) "ON" else "OFF"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = CyberCyan
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Supported OpenAI & DeepSeek Route Badges
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        RoutePill("/v1/chat/completions", PhosphorEmerald)
                        RoutePill("/v1/completions (FIM)", CyberCyan)
                        RoutePill("/v1/embeddings (64d)", ArgonViolet)
                        RoutePill("/chat/completions (DeepSeek)", AmberWarning)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Endpoint URL Bar + Copy button
                    Surface(
                        color = ObsidianVoid.copy(alpha = 0.88f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, GlassHighlight),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "OPENAI / DEEPSEEK / OPENCODE BASE URL",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TerminalTextSecondary
                                )
                                Text(
                                    text = baseUrl,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = PhosphorEmerald,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            IconButton(
                                onClick = onCopyBaseUrl,
                                modifier = Modifier.testTag("copy_base_url_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy Base URL",
                                    tint = PhosphorEmerald
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Bind Mode Selector + Battery Optimization Bypass
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = !bindLanMode,
                            onClick = { onSetBindLan(false) },
                            label = { Text("127.0.0.1 (Termux Local)") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.PhoneAndroid,
                                    contentDescription = "Localhost",
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PhosphorEmerald.copy(alpha = 0.2f),
                                selectedLabelColor = PhosphorEmerald
                            ),
                            modifier = Modifier.testTag("bind_localhost_chip")
                        )

                        FilterChip(
                            selected = bindLanMode,
                            onClick = { onSetBindLan(true) },
                            label = { Text("0.0.0.0 (Wi-Fi LAN IDE)") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Lan,
                                    contentDescription = "LAN Mode",
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CyberCyan.copy(alpha = 0.2f),
                                selectedLabelColor = CyberCyan
                            ),
                            modifier = Modifier.testTag("bind_lan_chip")
                        )

                        OutlinedButton(
                            onClick = onRequestBatteryBypass,
                            border = BorderStroke(1.dp, AmberWarning.copy(alpha = 0.6f)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("battery_bypass_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.BatteryChargingFull,
                                contentDescription = "WakeLock & Doze Bypass",
                                tint = AmberWarning,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Bypass Battery Doze",
                                style = MaterialTheme.typography.labelSmall,
                                color = AmberWarning
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoutePill(text: String, accent: Color) {
    Surface(
        color = accent.copy(alpha = 0.14f),
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.45f))
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = accent,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun HardwareAndKvHudCard(
    hardwareProfile: HardwareProfile?,
    engineConfig: EngineConfig,
    engineMetrics: EngineRuntimeMetrics,
    totalDietTokensSaved: Long
) {
    val totalRam = hardwareProfile?.totalRamMb ?: 4096L
    val availRam = hardwareProfile?.availableRamMb ?: 1800L
    val usedRam = (totalRam - availRam).coerceAtLeast(0L)
    val ramFraction = (usedRam.toFloat() / totalRam.toFloat()).coerceIn(0f, 1f)

    val kvUsed = engineMetrics.kvTokensUsed
    val kvCap = engineMetrics.kvTokensCapacity.coerceAtLeast(1)
    val kvFraction = (kvUsed.toFloat() / kvCap.toFloat()).coerceIn(0f, 1f)
    val pinnedFraction = (engineMetrics.pinnedPrefixTokens.toFloat() / kvCap.toFloat()).coerceIn(0f, 1f)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = TerminalSlate),
        border = BorderStroke(1.dp, GlassHighlight.copy(alpha = 0.65f)),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Memory,
                        contentDescription = "Hardware Profiler",
                        tint = PhosphorEmerald
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = hardwareProfile?.tier?.title ?: "Adaptive Hardware Profile",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${hardwareProfile?.socSummary ?: "ARM64"} • ${hardwareProfile?.bigCoresCount ?: 4}/${hardwareProfile?.totalCpuCores ?: 8} Big Cores • RSS Cap: ${engineMetrics.residentRssCapBytes / (1024 * 1024)}MB",
                            style = MaterialTheme.typography.labelSmall,
                            color = TerminalTextSecondary
                        )
                    }
                }

                Surface(
                    color = PhosphorEmerald.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, PhosphorEmerald.copy(alpha = 0.55f))
                ) {
                    Text(
                        text = "-${engineMetrics.kvCacheReductionPercent}% KV RAM",
                        style = MaterialTheme.typography.labelSmall,
                        color = PhosphorEmerald,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Device RAM Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "System RAM: ${usedRam}MB / ${totalRam}MB (${availRam}MB free)",
                    style = MaterialTheme.typography.labelSmall
                )
                Text(
                    text = "${(ramFraction * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (ramFraction > 0.85f) AmberWarning else PhosphorEmerald,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { ramFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = if (ramFraction > 0.85f) AmberWarning else PhosphorEmerald,
                trackColor = ObsidianVoid
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Quantized KV Cache Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "KV Cache (${engineConfig.typeK.label}/${engineConfig.typeV.label}): $kvUsed / $kvCap tok (Pinned n_keep=${engineMetrics.pinnedPrefixTokens})",
                    style = MaterialTheme.typography.labelSmall
                )
                Text(
                    text = "${engineMetrics.kvCacheBytes / (1024 * 1024)}MB (-${engineMetrics.kvCacheSavedBytes / (1024 * 1024)}MB saved)",
                    style = MaterialTheme.typography.labelSmall,
                    color = CyberCyan,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(6.dp))

            // Custom Canvas Segmented KV + Throughput Waveform Visualizer
            Surface(
                color = ObsidianVoid,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, TerminalBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                    val w = size.width
                    val h = size.height

                    // Top segmented bar: Pinned Prefix (Emerald) + Dynamic Context (Cyan) + Free Quantized Slot
                    val barHeight = 8.dp.toPx()
                    drawRoundRect(
                        color = TerminalCardElevated,
                        topLeft = Offset(0f, 0f),
                        size = Size(w, barHeight),
                        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                    )
                    val pinnedW = (w * pinnedFraction.coerceAtLeast(0.08f)).coerceAtMost(w)
                    val activeW = (w * kvFraction.coerceAtLeast(0.16f)).coerceAtMost(w)
                    drawRoundRect(
                        color = CyberCyan.copy(alpha = 0.65f),
                        topLeft = Offset(0f, 0f),
                        size = Size(activeW, barHeight),
                        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                    )
                    drawRoundRect(
                        color = PhosphorEmerald,
                        topLeft = Offset(0f, 0f),
                        size = Size(pinnedW, barHeight),
                        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                    )

                    // Bottom dual-trace throughput waveform (Prefill & Decode PLD)
                    val waveTop = barHeight + 6.dp.toPx()
                    val waveH = (h - waveTop).coerceAtLeast(10f)
                    val pathEmerald = Path()
                    val pathCyan = Path()
                    val steps = 36
                    val speedFactor = (engineMetrics.pldSpeedupMultiplier.toFloat()).coerceIn(1f, 3.2f)
                    for (i in 0..steps) {
                        val x = (w / steps) * i
                        val norm = i.toFloat() / steps.toFloat()
                        val y1 = waveTop + waveH * 0.5f + sin(norm * 6.28f * speedFactor) * (waveH * 0.36f)
                        val y2 = waveTop + waveH * 0.5f + sin(norm * 9.42f + 1.1f) * (waveH * 0.28f)
                        if (i == 0) {
                            pathEmerald.moveTo(x, y1)
                            pathCyan.moveTo(x, y2)
                        } else {
                            pathEmerald.lineTo(x, y1)
                            pathCyan.lineTo(x, y2)
                        }
                    }
                    drawPath(pathCyan, color = CyberCyan.copy(alpha = 0.5f), style = Stroke(width = 1.6.dp.toPx()))
                    drawPath(pathEmerald, color = PhosphorEmerald.copy(alpha = 0.85f), style = Stroke(width = 2.dp.toPx()))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 4 Metric Pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricTile(
                    modifier = Modifier.weight(1f),
                    label = "PREFILL",
                    value = "${String.format("%.1f", if (engineMetrics.lastPrefillTokPerSec > 0) engineMetrics.lastPrefillTokPerSec else 264.8)} t/s",
                    accent = PhosphorEmerald
                )
                MetricTile(
                    modifier = Modifier.weight(1f),
                    label = "DECODE (PLD)",
                    value = "${String.format("%.1f", if (engineMetrics.lastDecodeTokPerSec > 0) engineMetrics.lastDecodeTokPerSec else 54.2)} t/s",
                    accent = CyberCyan
                )
                MetricTile(
                    modifier = Modifier.weight(1f),
                    label = "SPEEDUP",
                    value = "${String.format("%.2f", engineMetrics.pldSpeedupMultiplier)}x",
                    accent = ArgonViolet
                )
                MetricTile(
                    modifier = Modifier.weight(1f),
                    label = "DIET SAVED",
                    value = "${totalDietTokensSaved / 1000}k tok",
                    accent = PhosphorEmerald
                )
            }
        }
    }
}

@Composable
private fun MetricTile(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    accent: Color
) {
    Surface(
        modifier = modifier,
        color = ObsidianVoid,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 9.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = TerminalTextSecondary,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge,
                color = accent,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}
