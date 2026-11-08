package com.example.ui

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DeveloperBoard
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
import androidx.compose.material3.HorizontalDivider
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
        with: { distribution: "temurin", java-version: "21" }
      - name: Accept Android SDK Licenses Non-Interactively
        run: |
          mkdir -p "${'$'}ANDROID_HOME/licenses"
          printf "\n24333f8a63b6825ea9c5514f83c2829b004d1fee\n" > "${'$'}ANDROID_HOME/licenses/android-sdk-license"
      - uses: gradle/actions/setup-gradle@v4
        with: { gradle-version: "9.3.1" }
      - name: Test & Assemble Debug + Release APKs
        run: |
          cp -n .env.example .env || true
          gradle :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease --no-daemon
      - uses: actions/upload-artifact@v4
        with:
          name: PocketCodeD-Android-APKs
          path: dist-apks/*.apk"""

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
    onStatusToast: (String) -> Unit,
    onOpenModelsTab: () -> Unit = {},
    onOpenTestTab: () -> Unit = {}
) {
    val clipboard = LocalClipboardManager.current
    var showConfigPreview by remember { mutableStateOf(false) }
    var selectedSnippetTab by remember { mutableIntStateOf(0) } // 0=opencode.json, 1=Termux/DeepSeek, 2=GitHub APK CI
    var newKeyLabel by remember { mutableStateOf("") }
    var showKeyVaultDetails by remember { mutableStateOf(false) }

    val primaryApiKey = apiKeys.firstOrNull()?.key ?: "sk-mobile-default"

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("control_deck_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Easy 3-Step Quick Start & Live Connection Hub
        item {
            QuickSetupHeroCard(
                isServerRunning = isServerRunning,
                bindLanMode = bindLanMode,
                serverPort = serverPort,
                baseUrl = baseUrl,
                primaryApiKey = primaryApiKey,
                activeModelId = engineConfig.activeModelId,
                onToggleServer = onToggleServer,
                onSetBindLan = onSetBindLan,
                onCopyBaseUrl = {
                    clipboard.setText(AnnotatedString(baseUrl))
                    onStatusToast("Copied Base URL: $baseUrl")
                },
                onCopyApiKey = {
                    clipboard.setText(AnnotatedString(primaryApiKey))
                    onStatusToast("Copied API Key to clipboard!")
                },
                onCopyModelId = {
                    clipboard.setText(AnnotatedString(engineConfig.activeModelId))
                    onStatusToast("Copied Model ID: ${engineConfig.activeModelId}")
                },
                onOpenModelsTab = onOpenModelsTab,
                onOpenTestTab = onOpenTestTab
            )
        }

        // 2. One-Tap Setup for OpenCode, DeepSeek, Aider & Termux
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
                            imageVector = Icons.Default.Code,
                            contentDescription = "Quick Tool Setup",
                            tint = CyberCyan
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "One-Tap Coding Tool Setup",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Tap a button below to copy ready-to-use config with your URL, Key & Model pre-filled",
                                style = MaterialTheme.typography.labelSmall,
                                color = TerminalTextSecondary
                            )
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
                                onStatusToast("Copied opencode.json config to clipboard!")
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
                            Text("1. Copy opencode.json", fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                selectedSnippetTab = 1
                                clipboard.setText(AnnotatedString(termuxScriptSnippet))
                                onStatusToast("Copied DeepSeek / Termux / Aider commands!")
                            },
                            border = BorderStroke(1.dp, CyberCyan),
                            modifier = Modifier.testTag("copy_termux_script_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Terminal,
                                contentDescription = "Copy Terminal Commands",
                                tint = CyberCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("2. Copy Terminal Commands", color = CyberCyan)
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
                            Text(if (showConfigPreview) "Hide Config File" else "Preview Config File")
                        }
                    }

                    AnimatedVisibility(visible = showConfigPreview) {
                        Column {
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
        }

        // 3. Phone RAM, Speed & Background Protection Card
        item {
            HardwareAndKvHudCard(
                hardwareProfile = hardwareProfile,
                engineConfig = engineConfig,
                engineMetrics = engineMetrics,
                totalDietTokensSaved = totalDietTokensSaved,
                onRequestBatteryBypass = onRequestBatteryBypass
            )
        }

        // 4. Manage Multiple API Keys (Clean & Collapsible)
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
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = "API Key Manager",
                                tint = PhosphorEmerald
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "API Key Manager (${apiKeys.size} Active)",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Create extra keys for different devices or revoke old keys",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TerminalTextSecondary
                                )
                            }
                        }
                        OutlinedButton(
                            onClick = { showKeyVaultDetails = !showKeyVaultDetails },
                            border = BorderStroke(1.dp, TerminalBorder),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = if (showKeyVaultDetails) "Hide" else "Manage Keys",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = newKeyLabel,
                            onValueChange = { newKeyLabel = it },
                            placeholder = { Text("New key name (e.g. My Laptop)") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("api_key_label_input")
                        )
                        Button(
                            onClick = {
                                onGenerateKey(newKeyLabel.ifBlank { "opencode Client" })
                                newKeyLabel = ""
                                showKeyVaultDetails = true
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CyberCyan,
                                contentColor = ObsidianVoid
                            ),
                            modifier = Modifier.testTag("generate_api_key_button")
                        ) {
                            Text("Create Key", fontWeight = FontWeight.Bold)
                        }
                    }

                    if (showKeyVaultDetails || apiKeys.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        val visibleKeys = if (showKeyVaultDetails) apiKeys else apiKeys.take(1)
                        visibleKeys.forEach { record ->
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
                                            text = "${record.key} • ${record.requestCount} requests",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontFamily = JetBrainsMonoFamily,
                                            color = PhosphorEmerald,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Row {
                                        IconButton(
                                            onClick = {
                                                clipboard.setText(AnnotatedString(record.key))
                                                onStatusToast("Copied API Key for ${record.label}")
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
                                                    contentDescription = "Delete API key",
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
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickSetupHeroCard(
    isServerRunning: Boolean,
    bindLanMode: Boolean,
    serverPort: Int,
    baseUrl: String,
    primaryApiKey: String,
    activeModelId: String,
    onToggleServer: () -> Unit,
    onSetBindLan: (Boolean) -> Unit,
    onCopyBaseUrl: () -> Unit,
    onCopyApiKey: () -> Unit,
    onCopyModelId: () -> Unit,
    onOpenModelsTab: () -> Unit,
    onOpenTestTab: () -> Unit
) {
    val statusColor by animateColorAsState(
        targetValue = if (isServerRunning) PhosphorEmerald else AmberWarning,
        label = "serverStatusColor"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.5.dp, statusColor.copy(alpha = 0.7f)),
        colors = CardDefaults.cardColors(containerColor = TerminalSlate)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Image(
                painter = painterResource(id = R.drawable.img_daemon_hero_1794064302740),
                contentDescription = "PocketCodeD Local AI Server",
                contentScale = ContentScale.Crop,
                alpha = 0.20f,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                ObsidianVoid.copy(alpha = 0.55f),
                                TerminalSlate.copy(alpha = 0.95f),
                                TerminalSlate
                            )
                        )
                    )
                    .padding(16.dp)
            ) {
                Column {
                    // Header: Personal AI Cloud Provider Portal
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(statusColor)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = if (isServerRunning) "PERSONAL AI CLOUD • ONLINE (PORT $serverPort)" else "PERSONAL AI CLOUD • OFFLINE",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = statusColor,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Works just like OpenAI, DeepSeek, or OpenRouter API — 100% free & offline",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TerminalTextSecondary
                                )
                            }
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
                                contentDescription = if (isServerRunning) "Stop Server" else "Start Server",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isServerRunning) "Stop API" else "Start API",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Credential 1: Secret API Key (First & Most Prominent!)
                    CredentialCopyRow(
                        label = "1. YOUR SECRET API KEY (USE IN ANY APP / TOOL)",
                        value = primaryApiKey,
                        valueColor = PhosphorEmerald,
                        buttonText = "Copy API Key",
                        buttonTag = "copy_primary_api_key_button",
                        onCopy = onCopyApiKey
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Credential 2: API Base URL
                    CredentialCopyRow(
                        label = "2. API BASE URL (OPENAI / DEEPSEEK COMPATIBLE)",
                        value = baseUrl,
                        valueColor = CyberCyan,
                        buttonText = "Copy URL",
                        buttonTag = "copy_base_url_button",
                        onCopy = onCopyBaseUrl
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Credential 3: Active Model ID
                    CredentialCopyRow(
                        label = "3. ACTIVE MODEL NAME (OR USE: qwen2.5-coder)",
                        value = activeModelId,
                        valueColor = ArgonViolet,
                        buttonText = "Copy Model",
                        buttonTag = "copy_active_model_id_button",
                        onCopy = onCopyModelId
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = TerminalBorder)
                    Spacer(modifier = Modifier.height(10.dp))

                    // Connection Mode & Quick Actions
                    Text(
                        text = "Where are you using this API key?",
                        style = MaterialTheme.typography.labelSmall,
                        color = TerminalTextSecondary
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = !bindLanMode,
                            onClick = { onSetBindLan(false) },
                            label = { Text("On This Phone (127.0.0.1)") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.PhoneAndroid,
                                    contentDescription = "Same Phone",
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
                            label = { Text("PC / Other Devices (Wi-Fi LAN)") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Lan,
                                    contentDescription = "Wi-Fi LAN",
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
                            onClick = onOpenModelsTab,
                            border = BorderStroke(1.dp, CyberCyan),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeveloperBoard,
                                contentDescription = "Switch Model",
                                tint = CyberCyan,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Switch Model", style = MaterialTheme.typography.labelSmall, color = CyberCyan)
                        }

                        OutlinedButton(
                            onClick = onOpenTestTab,
                            border = BorderStroke(1.dp, PhosphorEmerald),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Open AI Chat",
                                tint = PhosphorEmerald,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Open AI Chat", style = MaterialTheme.typography.labelSmall, color = PhosphorEmerald)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StepBadge(number: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.2f),
        shape = CircleShape,
        border = BorderStroke(1.5.dp, color),
        modifier = Modifier.size(26.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = number,
                style = MaterialTheme.typography.labelLarge,
                color = color,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun CredentialCopyRow(
    label: String,
    value: String,
    valueColor: Color,
    buttonText: String,
    buttonTag: String,
    onCopy: () -> Unit
) {
    Surface(
        color = ObsidianVoid.copy(alpha = 0.92f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, valueColor.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = TerminalTextSecondary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = JetBrainsMonoFamily,
                    color = valueColor,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Button(
                onClick = onCopy,
                colors = ButtonDefaults.buttonColors(
                    containerColor = valueColor.copy(alpha = 0.2f),
                    contentColor = valueColor
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.testTag(buttonTag)
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = buttonText,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = buttonText,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun HardwareAndKvHudCard(
    hardwareProfile: HardwareProfile?,
    engineConfig: EngineConfig,
    engineMetrics: EngineRuntimeMetrics,
    totalDietTokensSaved: Long,
    onRequestBatteryBypass: () -> Unit
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
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = Icons.Default.Memory,
                        contentDescription = "Phone Memory & Speed",
                        tint = PhosphorEmerald
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Phone RAM & AI Speed Monitor",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${hardwareProfile?.tier?.title ?: "4GB/6GB Optimized"} • Max AI RAM Cap: ${engineMetrics.residentRssCapBytes / (1024 * 1024)} MB",
                            style = MaterialTheme.typography.labelSmall,
                            color = TerminalTextSecondary
                        )
                    }
                }

                OutlinedButton(
                    onClick = onRequestBatteryBypass,
                    border = BorderStroke(1.dp, AmberWarning.copy(alpha = 0.6f)),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("battery_bypass_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.BatteryChargingFull,
                        contentDescription = "Keep Alive in Background",
                        tint = AmberWarning,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Keep Alive",
                        style = MaterialTheme.typography.labelSmall,
                        color = AmberWarning
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
                    text = "Phone RAM: ${usedRam} MB used / ${totalRam} MB total (${availRam} MB free)",
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
                    text = "AI Context Memory: $kvUsed / $kvCap tokens",
                    style = MaterialTheme.typography.labelSmall
                )
                Text(
                    text = "${engineMetrics.kvCacheBytes / (1024 * 1024)} MB (-${engineMetrics.kvCacheReductionPercent}% RAM saved)",
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
                    .height(48.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                    val w = size.width
                    val h = size.height

                    val barHeight = 7.dp.toPx()
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

                    val waveTop = barHeight + 5.dp.toPx()
                    val waveH = (h - waveTop).coerceAtLeast(8f)
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

            Spacer(modifier = Modifier.height(10.dp))

            // 4 Metric Pills with Simple Labels
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricTile(
                    modifier = Modifier.weight(1f),
                    label = "READ SPEED",
                    value = "${String.format("%.0f", if (engineMetrics.lastPrefillTokPerSec > 0) engineMetrics.lastPrefillTokPerSec else 265.0)} t/s",
                    accent = PhosphorEmerald
                )
                MetricTile(
                    modifier = Modifier.weight(1f),
                    label = "WRITE SPEED",
                    value = "${String.format("%.1f", if (engineMetrics.lastDecodeTokPerSec > 0) engineMetrics.lastDecodeTokPerSec else 54.2)} t/s",
                    accent = CyberCyan
                )
                MetricTile(
                    modifier = Modifier.weight(1f),
                    label = "TURBO BOOST",
                    value = "${String.format("%.1f", engineMetrics.pldSpeedupMultiplier)}x",
                    accent = ArgonViolet
                )
                MetricTile(
                    modifier = Modifier.weight(1f),
                    label = "TOKENS SAVED",
                    value = "${totalDietTokensSaved / 1000}k",
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
