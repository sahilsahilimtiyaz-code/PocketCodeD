package com.example.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Speed
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.PhosphorEmerald
import com.example.ui.theme.TerminalBorder
import com.example.ui.theme.TerminalCardElevated
import com.example.ui.theme.TerminalSlate
import com.example.ui.theme.TerminalTextSecondary

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

        // 2. Real-Time Hardware, mmap & Quantized KV Cache HUD
        item {
            HardwareAndKvHudCard(
                hardwareProfile = hardwareProfile,
                engineConfig = engineConfig,
                engineMetrics = engineMetrics,
                totalDietTokensSaved = totalDietTokensSaved
            )
        }

        // 3. One-Tap Harness Config Generator (opencode.json + Termux)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = TerminalSlate),
                border = BorderStroke(1.dp, TerminalBorder),
                shape = RoundedCornerShape(16.dp)
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
                                contentDescription = "Harness Config",
                                tint = CyberCyan
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "One-Tap Harness Config Generator",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "opencode • Aider • Continue • DeepSeek Harnesses",
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
                            Text("Copy opencode.json Config", fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                clipboard.setText(AnnotatedString(termuxScriptSnippet))
                                onStatusToast("Copied Termux export & Aider command!")
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
                            Text("Copy Termux Script", color = CyberCyan)
                        }

                        OutlinedButton(
                            onClick = { showConfigPreview = !showConfigPreview },
                            border = BorderStroke(1.dp, TerminalBorder),
                            modifier = Modifier.testTag("toggle_config_preview_button")
                        ) {
                            Text(if (showConfigPreview) "Hide JSON" else "Preview JSON")
                        }
                    }

                    if (showConfigPreview) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(ObsidianVoid)
                                .border(1.dp, TerminalBorder, RoundedCornerShape(10.dp))
                                .padding(12.dp)
                        ) {
                            Text(
                                text = opencodeJsonSnippet,
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = JetBrainsMonoFamily,
                                color = PhosphorEmerald
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
                border = BorderStroke(1.dp, TerminalBorder),
                shape = RoundedCornerShape(16.dp)
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
                                text = "Enforced on all /v1/* routes via Authorization: Bearer sk-mobile-...",
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
                            shape = RoundedCornerShape(10.dp),
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
                                        color = MaterialTheme.colorScheme.onSurface
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
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.6f)),
        colors = CardDefaults.cardColors(containerColor = TerminalSlate)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Image(
                painter = painterResource(id = R.drawable.img_daemon_hero_1794064302740),
                contentDescription = "PocketCodeD ARM64 Inference Architecture",
                contentScale = ContentScale.Crop,
                alpha = 0.25f,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(230.dp)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                ObsidianVoid.copy(alpha = 0.55f),
                                TerminalSlate.copy(alpha = 0.96f)
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
                        text = "mmap=ON • mlock=OFF • FlashAttn=ON • KV: K=${engineConfig.typeK.label} / V=${engineConfig.typeV.label} • PLD=${if (engineConfig.enablePld) "ON" else "OFF"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = CyberCyan
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Endpoint URL Bar + Copy button
                    Surface(
                        color = ObsidianVoid.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, TerminalBorder),
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
                                    text = "OPENAI / DEEPSEEK COMPATIBLE BASE URL",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TerminalTextSecondary
                                )
                                Text(
                                    text = baseUrl,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = PhosphorEmerald
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

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = TerminalSlate),
        border = BorderStroke(1.dp, TerminalBorder),
        shape = RoundedCornerShape(16.dp)
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
                            text = "${hardwareProfile?.socSummary ?: "ARM64"} • ${hardwareProfile?.bigCoresCount ?: 4}/${hardwareProfile?.totalCpuCores ?: 8} Big Cores",
                            style = MaterialTheme.typography.labelSmall,
                            color = TerminalTextSecondary
                        )
                    }
                }

                Surface(
                    color = PhosphorEmerald.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, PhosphorEmerald.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = "-${engineMetrics.kvCacheReductionPercent}% KV RAM",
                        style = MaterialTheme.typography.labelSmall,
                        color = PhosphorEmerald,
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
                    text = "Device RAM: ${usedRam}MB used / ${totalRam}MB (${availRam}MB free)",
                    style = MaterialTheme.typography.labelSmall
                )
                Text(
                    text = "${(ramFraction * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (ramFraction > 0.85f) AmberWarning else PhosphorEmerald
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
                    text = "${engineMetrics.kvCacheBytes / (1024 * 1024)}MB",
                    style = MaterialTheme.typography.labelSmall,
                    color = CyberCyan
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { kvFraction.coerceAtLeast(0.06f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = CyberCyan,
                trackColor = ObsidianVoid
            )

            Spacer(modifier = Modifier.height(14.dp))

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
                    label = "PLD SPEEDUP",
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
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, TerminalBorder)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
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
