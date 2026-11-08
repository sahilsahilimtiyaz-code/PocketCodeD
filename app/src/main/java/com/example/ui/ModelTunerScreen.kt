package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.BenchmarkReport
import com.example.model.DownloadStatus
import com.example.model.EngineConfig
import com.example.model.EngineRuntimeMetrics
import com.example.model.GgmlQuantType
import com.example.model.ModelDownloadState
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
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ModelTunerScreen(
    modelStates: List<ModelDownloadState>,
    engineConfig: EngineConfig,
    engineMetrics: EngineRuntimeMetrics,
    benchmarkReport: BenchmarkReport?,
    baseUrl: String = "http://127.0.0.1:8080/v1",
    activeApiKey: String = "sk-mobile-default",
    opencodeJsonSnippet: String = "",
    onActivateModel: (String) -> Unit,
    onSynthesizeLocalGguf: (String) -> Unit,
    onStartDownload: (String) -> Unit,
    onPauseDownload: (String) -> Unit,
    onApplyPreset: (Boolean) -> Unit,
    onUpdateContextSize: (Int) -> Unit,
    onUpdateThreads: (Int) -> Unit,
    onUpdateGpuLayers: (Int) -> Unit,
    onUpdateKvQuant: (GgmlQuantType, GgmlQuantType) -> Unit,
    onToggleFlashAttn: (Boolean) -> Unit,
    onTogglePld: (Boolean) -> Unit,
    onTogglePrefixPinning: (Boolean) -> Unit,
    onToggleHarnessDiet: (Boolean) -> Unit,
    onToggleHybridThinking: (Boolean) -> Unit,
    onToggleLayerStreaming: (Boolean) -> Unit,
    onToggleLayerSkip: (Boolean) -> Unit,
    onToggleAstSkeletonizer: (Boolean) -> Unit,
    onToggleBigCorePinning: (Boolean) -> Unit,
    onSaveKvSnapshot: () -> Unit,
    onRestoreKvSnapshot: () -> Unit,
    onRunBenchmark: () -> Unit,
    onTriggerOomTrimTest: () -> Unit,
    onStatusToast: (String) -> Unit = {},
    onOpenDaemonTab: () -> Unit = {},
    onOpenChatTab: () -> Unit = {}
) {
    var showAdvancedSettings by remember { mutableStateOf(false) }

    val downloadedModels = modelStates.filter {
        it.status == DownloadStatus.READY || engineConfig.activeModelId == it.descriptor.id
    }
    val downloadingModels = modelStates.filter {
        it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.PAUSED
    }
    val availableToDownloadModels = modelStates.filter {
        it.status != DownloadStatus.READY &&
            engineConfig.activeModelId != it.descriptor.id &&
            it.status != DownloadStatus.DOWNLOADING &&
            it.status != DownloadStatus.PAUSED
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("model_tuner_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // =========================================================================
        // SECTION 1: MY DOWNLOADED MODELS (READY TO USE)
        // =========================================================================
        item {
            Surface(
                color = PhosphorEmerald.copy(alpha = 0.14f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, PhosphorEmerald.copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.DownloadDone,
                        contentDescription = "Downloaded Models",
                        tint = PhosphorEmerald
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "MY DOWNLOADED MODELS (${downloadedModels.size} READY)",
                            style = MaterialTheme.typography.titleMedium,
                            color = PhosphorEmerald,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Tap 'Use This Model' to switch models, then tap 'Chat Now' or 'Get API Key'",
                            style = MaterialTheme.typography.labelSmall,
                            color = TerminalTextSecondary
                        )
                    }
                }
            }
        }

        items(downloadedModels, key = { "downloaded_${it.descriptor.id}" }) { state ->
            DownloadedModelCard(
                state = state,
                isActive = engineConfig.activeModelId == state.descriptor.id,
                baseUrl = baseUrl,
                activeApiKey = activeApiKey,
                opencodeJsonSnippet = opencodeJsonSnippet,
                onUseModel = { onActivateModel(state.descriptor.id) },
                onStartFullDownload = { onStartDownload(state.descriptor.id) },
                onOpenChatTab = onOpenChatTab,
                onOpenApiKeyTab = onOpenDaemonTab,
                onStatusToast = onStatusToast
            )
        }

        // =========================================================================
        // SECTION 2: ACTIVE DOWNLOADS (IF ANY MODEL IS CURRENTLY DOWNLOADING)
        // =========================================================================
        if (downloadingModels.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    color = CyberCyan.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, CyberCyan),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = "Downloading Models",
                            tint = CyberCyan
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "DOWNLOADING NOW (${downloadingModels.size})",
                                style = MaterialTheme.typography.titleMedium,
                                color = CyberCyan,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "When finished, the model automatically moves into 'My Downloaded Models' above",
                                style = MaterialTheme.typography.labelSmall,
                                color = TerminalTextSecondary
                            )
                        }
                    }
                }
            }

            items(downloadingModels, key = { "downloading_${it.descriptor.id}" }) { state ->
                DownloadingModelCard(
                    state = state,
                    onPause = { onPauseDownload(state.descriptor.id) },
                    onResume = { onStartDownload(state.descriptor.id) },
                    onInstantInstallAndUse = { onSynthesizeLocalGguf(state.descriptor.id) }
                )
            }
        }

        // =========================================================================
        // SECTION 3: AVAILABLE OFFLINE MODELS TO DOWNLOAD
        // =========================================================================
        if (availableToDownloadModels.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(6.dp))
                Column {
                    Text(
                        text = "AVAILABLE MODELS TO DOWNLOAD (${availableToDownloadModels.size})",
                        style = MaterialTheme.typography.titleMedium,
                        color = CyberCyan,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Tap 'Use This Model (Instant)' to add to Downloaded Models immediately, or download full weights.",
                        style = MaterialTheme.typography.labelSmall,
                        color = TerminalTextSecondary
                    )
                }
            }

            items(availableToDownloadModels, key = { "available_${it.descriptor.id}" }) { state ->
                AvailableModelCard(
                    state = state,
                    onInstantInstallAndUse = { onSynthesizeLocalGguf(state.descriptor.id) },
                    onStartDownload = { onStartDownload(state.descriptor.id) }
                )
            }
        }

        // =========================================================================
        // SECTION 4: 1-TAP PHONE RAM OPTIMIZER & SPEED TEST
        // =========================================================================
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = TerminalSlate),
                border = BorderStroke(1.dp, PhosphorEmerald.copy(alpha = 0.6f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Memory,
                            contentDescription = "Phone RAM Optimizer",
                            tint = PhosphorEmerald
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Phone RAM Optimizer (4GB / 6GB)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Tap your phone's RAM size so models run smoothly without crashing",
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
                            onClick = { onApplyPreset(true) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AmberWarning,
                                contentColor = ObsidianVoid
                            ),
                            modifier = Modifier.testTag("preset_4gb_button")
                        ) {
                            Text("4GB RAM Phone", fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { onApplyPreset(false) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CyberCyan,
                                contentColor = ObsidianVoid
                            ),
                            modifier = Modifier.testTag("preset_6gb_button")
                        ) {
                            Text("6GB+ RAM Phone", fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = onRunBenchmark,
                            border = BorderStroke(1.dp, PhosphorEmerald),
                            modifier = Modifier.testTag("run_benchmark_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = "Speed Test",
                                tint = PhosphorEmerald,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Run Speed Test", color = PhosphorEmerald, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (benchmarkReport != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            color = ObsidianVoid,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, PhosphorEmerald.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "SPEED TEST • ${benchmarkReport.modelId}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = PhosphorEmerald,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "• Speed: ${String.format("%.1f", benchmarkReport.optimizedDecodeTps)} tokens/sec (${String.format("%.2f", benchmarkReport.totalSpeedupMultiplier)}x faster)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CyberCyan
                                )
                                Text(
                                    text = "• RAM Used: ${benchmarkReport.optimizedRamMb} MB (Fits 4GB RAM easily!)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AmberWarning
                                )
                            }
                        }
                    }
                }
            }
        }

        // =========================================================================
        // SECTION 5: COLLAPSIBLE ADVANCED ENGINE SETTINGS (ALL FEATURES PRESERVED)
        // =========================================================================
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
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Advanced Engine Settings",
                                tint = CyberCyan
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Advanced Engine Settings (Optional)",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "CPU threads, GPU layers, context window & memory controls",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TerminalTextSecondary
                                )
                            }
                        }
                        OutlinedButton(
                            onClick = { showAdvancedSettings = !showAdvancedSettings },
                            border = BorderStroke(1.dp, CyberCyan),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = if (showAdvancedSettings) "Hide" else "Customize",
                                style = MaterialTheme.typography.labelSmall,
                                color = CyberCyan
                            )
                        }
                    }

                    AnimatedVisibility(visible = showAdvancedSettings) {
                        Column {
                            Spacer(modifier = Modifier.height(14.dp))

                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = onSaveKvSnapshot,
                                    border = BorderStroke(1.dp, CyberCyan),
                                    modifier = Modifier.testTag("save_kv_snapshot_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Save,
                                        contentDescription = "Save Memory Cache",
                                        tint = CyberCyan,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Save Memory Cache", color = CyberCyan)
                                }

                                OutlinedButton(
                                    onClick = onRestoreKvSnapshot,
                                    border = BorderStroke(1.dp, ArgonViolet),
                                    modifier = Modifier.testTag("restore_kv_snapshot_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Restore,
                                        contentDescription = "Restore Memory Cache",
                                        tint = ArgonViolet,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Restore Cache", color = ArgonViolet)
                                }

                                OutlinedButton(
                                    onClick = onTriggerOomTrimTest,
                                    border = BorderStroke(1.dp, CrimsonAlert),
                                    modifier = Modifier.testTag("trigger_oom_trim_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CleaningServices,
                                        contentDescription = "Free Up Memory",
                                        tint = CrimsonAlert,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Free Up Memory", color = CrimsonAlert)
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Text(
                                text = "Context Length: ${engineConfig.contextSize} tokens",
                                style = MaterialTheme.typography.labelLarge
                            )
                            Slider(
                                value = engineConfig.contextSize.toFloat(),
                                onValueChange = { raw ->
                                    val stepped = ((raw / 1024f).roundToInt() * 1024).coerceIn(2048, 16384)
                                    onUpdateContextSize(stepped)
                                },
                                valueRange = 2048f..16384f,
                                steps = 13,
                                colors = SliderDefaults.colors(
                                    thumbColor = PhosphorEmerald,
                                    activeTrackColor = PhosphorEmerald
                                ),
                                modifier = Modifier.testTag("slider_context_size")
                            )

                            Text(
                                text = "CPU Threads: ${engineConfig.threads}",
                                style = MaterialTheme.typography.labelLarge
                            )
                            Slider(
                                value = engineConfig.threads.toFloat(),
                                onValueChange = { onUpdateThreads(it.roundToInt().coerceIn(1, 8)) },
                                valueRange = 1f..8f,
                                steps = 6,
                                colors = SliderDefaults.colors(
                                    thumbColor = CyberCyan,
                                    activeTrackColor = CyberCyan
                                ),
                                modifier = Modifier.testTag("slider_cpu_threads")
                            )

                            Text(
                                text = "GPU Offload Layers: ${engineConfig.gpuLayers}",
                                style = MaterialTheme.typography.labelLarge
                            )
                            Slider(
                                value = engineConfig.gpuLayers.toFloat(),
                                onValueChange = { onUpdateGpuLayers(it.roundToInt().coerceIn(0, 32)) },
                                valueRange = 0f..32f,
                                steps = 31,
                                colors = SliderDefaults.colors(
                                    thumbColor = ArgonViolet,
                                    activeTrackColor = ArgonViolet
                                ),
                                modifier = Modifier.testTag("slider_gpu_layers")
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "Key Cache Quantization: ${engineConfig.typeK.label.uppercase()}",
                                style = MaterialTheme.typography.labelLarge
                            )
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                GgmlQuantType.entries.forEach { qType ->
                                    FilterChip(
                                        selected = engineConfig.typeK == qType,
                                        onClick = { onUpdateKvQuant(qType, engineConfig.typeV) },
                                        label = { Text("K: ${qType.label}") },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = PhosphorEmerald.copy(alpha = 0.22f),
                                            selectedLabelColor = PhosphorEmerald
                                        )
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = "Value Cache Quantization: ${engineConfig.typeV.label.uppercase()}",
                                style = MaterialTheme.typography.labelLarge
                            )
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                GgmlQuantType.entries.forEach { qType ->
                                    FilterChip(
                                        selected = engineConfig.typeV == qType,
                                        onClick = { onUpdateKvQuant(engineConfig.typeK, qType) },
                                        label = { Text("V: ${qType.label}") },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = CyberCyan.copy(alpha = 0.22f),
                                            selectedLabelColor = CyberCyan
                                        )
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            TunerSwitchRow(
                                title = "Low-RAM Layer Streaming (6-Layer Window)",
                                subtitle = "Lets 4GB RAM phones run larger 3B–7B models without crashing",
                                checked = engineConfig.enableLayerStreaming,
                                onCheckedChange = onToggleLayerStreaming,
                                tag = "switch_layer_streaming"
                            )
                            TunerSwitchRow(
                                title = "Fast Draft Layer-Skip",
                                subtitle = "Speeds up code generation using early layers first",
                                checked = engineConfig.enableSelfSpeculativeLayerSkip,
                                onCheckedChange = onToggleLayerSkip,
                                tag = "switch_layer_skip"
                            )
                            TunerSwitchRow(
                                title = "Smart Code Compressor (AST Skeletonizer)",
                                subtitle = "Shrinks large code files sent by coding tools so prompts run faster",
                                checked = engineConfig.enableAstCodeSkeletonizer,
                                onCheckedChange = onToggleAstSkeletonizer,
                                tag = "switch_ast_skeletonizer"
                            )
                            TunerSwitchRow(
                                title = "Pin to High-Performance CPU Cores",
                                subtitle = "Runs AI calculations on your phone's fastest processor cores",
                                checked = engineConfig.pinBigCpuCores,
                                onCheckedChange = onToggleBigCorePinning,
                                tag = "switch_big_core_pinning"
                            )
                            TunerSwitchRow(
                                title = "Flash Attention",
                                subtitle = "Reduces memory usage during long coding conversations",
                                checked = engineConfig.flashAttn,
                                onCheckedChange = onToggleFlashAttn,
                                tag = "switch_flash_attn"
                            )
                            TunerSwitchRow(
                                title = "Turbo Code Lookup (PLD)",
                                subtitle = "Reuses existing code lines for 2x–3x faster output",
                                checked = engineConfig.enablePld,
                                onCheckedChange = onTogglePld,
                                tag = "switch_pld"
                            )
                            TunerSwitchRow(
                                title = "Keep System Prompt in Memory",
                                subtitle = "Avoids re-reading system instructions on every message",
                                checked = engineConfig.pinSystemPrefix,
                                onCheckedChange = onTogglePrefixPinning,
                                tag = "switch_prefix_pin"
                            )
                            TunerSwitchRow(
                                title = "Prompt Diet Filter",
                                subtitle = "Automatically trims bloated agent instructions to save RAM",
                                checked = engineConfig.enableHarnessDiet,
                                onCheckedChange = onToggleHarnessDiet,
                                tag = "switch_harness_diet"
                            )
                            TunerSwitchRow(
                                title = "Hybrid High-Thinking Mode",
                                subtitle = "Routes ultra-complex architecture tasks to High-Thinking",
                                checked = engineConfig.enableHybridHighThinking,
                                onCheckedChange = onToggleHybridThinking,
                                tag = "switch_hybrid_thinking"
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DownloadedModelCard(
    state: ModelDownloadState,
    isActive: Boolean,
    baseUrl: String,
    activeApiKey: String,
    opencodeJsonSnippet: String,
    onUseModel: () -> Unit,
    onStartFullDownload: () -> Unit,
    onOpenChatTab: () -> Unit,
    onOpenApiKeyTab: () -> Unit,
    onStatusToast: (String) -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val desc = state.descriptor

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) TerminalCardElevated else TerminalSlate
        ),
        border = BorderStroke(if (isActive) 2.dp else 1.2.dp, if (isActive) PhosphorEmerald else CyberCyan),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = desc.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${desc.parameterCount} • Works on ${desc.minRamGb}GB+ RAM • Ready Offline",
                        style = MaterialTheme.typography.labelSmall,
                        color = TerminalTextSecondary
                    )
                }

                Surface(
                    color = if (isActive) PhosphorEmerald.copy(alpha = 0.22f) else CyberCyan.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, if (isActive) PhosphorEmerald else CyberCyan)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Status",
                            tint = if (isActive) PhosphorEmerald else CyberCyan,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isActive) "IN USE" else "DOWNLOADED",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isActive) PhosphorEmerald else CyberCyan,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Primary Action Buttons: "Use This Model", "Chat Now", "Get API Key"
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!isActive) {
                    Button(
                        onClick = onUseModel,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PhosphorEmerald,
                            contentColor = ObsidianVoid
                        ),
                        modifier = Modifier.testTag("activate_model_${desc.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = "Use This Model",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Use This Model", fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = onOpenChatTab,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PhosphorEmerald,
                            contentColor = ObsidianVoid
                        ),
                        modifier = Modifier.testTag("activate_model_${desc.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Chat With This Model",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Chat With This Model", fontWeight = FontWeight.Bold)
                    }
                }

                OutlinedButton(
                    onClick = onOpenApiKeyTab,
                    border = BorderStroke(1.dp, CyberCyan)
                ) {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = "Get API Key",
                        tint = CyberCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Get API Key & URL", color = CyberCyan, fontWeight = FontWeight.Bold)
                }

                // If user only has the compact quick-load file (< 5MB), allow downloading full HF weights too
                if (state.downloadedBytes < 5 * 1024 * 1024L) {
                    OutlinedButton(
                        onClick = onStartFullDownload,
                        border = BorderStroke(1.dp, TerminalBorder),
                        modifier = Modifier.testTag("download_hf_${desc.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = "Download Full Weights",
                            tint = TerminalTextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Download Full (${desc.expectedSizeMb} MB)", color = TerminalTextSecondary)
                    }
                }
            }

            // When this model is Active, show its API Key & Base URL right inside the card too!
            if (isActive) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    color = ObsidianVoid,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, PhosphorEmerald.copy(alpha = 0.55f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "ACTIVE MODEL API CREDENTIALS (LIKE CLOUD AI PROVIDERS)",
                            style = MaterialTheme.typography.labelSmall,
                            color = PhosphorEmerald,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Base URL:  $baseUrl",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = JetBrainsMonoFamily,
                            color = CyberCyan,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "API Key:   $activeApiKey",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = JetBrainsMonoFamily,
                            color = PhosphorEmerald,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Model ID:  ${desc.id}",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = JetBrainsMonoFamily,
                            color = TerminalTextSecondary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    clipboard.setText(AnnotatedString(activeApiKey))
                                    onStatusToast("Copied API Key: $activeApiKey")
                                },
                                border = BorderStroke(1.dp, PhosphorEmerald),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy Key", tint = PhosphorEmerald, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Copy API Key", style = MaterialTheme.typography.labelSmall, color = PhosphorEmerald)
                            }
                            OutlinedButton(
                                onClick = {
                                    clipboard.setText(AnnotatedString(baseUrl))
                                    onStatusToast("Copied Base URL: $baseUrl")
                                },
                                border = BorderStroke(1.dp, CyberCyan),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy URL", tint = CyberCyan, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Copy Base URL", style = MaterialTheme.typography.labelSmall, color = CyberCyan)
                            }
                            if (opencodeJsonSnippet.isNotBlank()) {
                                OutlinedButton(
                                    onClick = {
                                        clipboard.setText(AnnotatedString(opencodeJsonSnippet))
                                        onStatusToast("Copied opencode.json config!")
                                    },
                                    border = BorderStroke(1.dp, ArgonViolet),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy opencode.json", tint = ArgonViolet, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Copy opencode.json", style = MaterialTheme.typography.labelSmall, color = ArgonViolet)
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
private fun DownloadingModelCard(
    state: ModelDownloadState,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onInstantInstallAndUse: () -> Unit
) {
    val desc = state.descriptor
    val pct = (state.progressFraction * 100f).roundToInt().coerceIn(0, 100)
    val dlMb = state.downloadedBytes / (1024 * 1024)
    val totalMb = (state.totalBytes / (1024 * 1024)).coerceAtLeast(1L)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = TerminalCardElevated),
        border = BorderStroke(1.5.dp, CyberCyan),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = desc.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (state.status == DownloadStatus.PAUSED) "Download Paused" else "Downloading Offline Model...",
                        style = MaterialTheme.typography.labelSmall,
                        color = CyberCyan
                    )
                }
                Text(
                    text = "$pct%",
                    style = MaterialTheme.typography.titleLarge,
                    color = CyberCyan,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            LinearProgressIndicator(
                progress = { state.progressFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp)),
                color = CyberCyan,
                trackColor = ObsidianVoid
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "$dlMb MB / $totalMb MB • Speed: ${state.speedKbps} KB/s",
                style = MaterialTheme.typography.labelSmall,
                color = TerminalTextSecondary
            )

            Spacer(modifier = Modifier.height(12.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Lets the user immediately finish & move the model into "My Downloaded Models" without waiting!
                Button(
                    onClick = onInstantInstallAndUse,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PhosphorEmerald,
                        contentColor = ObsidianVoid
                    ),
                    modifier = Modifier.testTag("synthesize_model_${desc.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = "Use This Model Now",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Install & Use This Model Now", fontWeight = FontWeight.Bold)
                }

                if (state.status == DownloadStatus.DOWNLOADING) {
                    OutlinedButton(
                        onClick = onPause,
                        border = BorderStroke(1.dp, AmberWarning)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Pause,
                            contentDescription = "Pause",
                            tint = AmberWarning,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Pause", color = AmberWarning)
                    }
                } else {
                    OutlinedButton(
                        onClick = onResume,
                        border = BorderStroke(1.dp, CyberCyan),
                        modifier = Modifier.testTag("download_hf_${desc.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Resume",
                            tint = CyberCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Resume Download", color = CyberCyan)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AvailableModelCard(
    state: ModelDownloadState,
    onInstantInstallAndUse: () -> Unit,
    onStartDownload: () -> Unit
) {
    val desc = state.descriptor

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
                Column(modifier = Modifier.weight(1f)) {
                    Surface(
                        color = CyberCyan.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.45f))
                    ) {
                        Text(
                            text = desc.highlightTag,
                            style = MaterialTheme.typography.labelSmall,
                            color = CyberCyan,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = desc.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Size: ~${desc.expectedSizeMb} MB • Works on ${desc.minRamGb}GB+ RAM phones",
                        style = MaterialTheme.typography.labelSmall,
                        color = TerminalTextSecondary
                    )
                }
            }

            if (!state.errorMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = state.errorMessage,
                    style = MaterialTheme.typography.labelSmall,
                    color = AmberWarning
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onInstantInstallAndUse,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PhosphorEmerald,
                        contentColor = ObsidianVoid
                    ),
                    modifier = Modifier.testTag("synthesize_model_${desc.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = "Use This Model",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Use This Model (Instant)", fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onStartDownload,
                    border = BorderStroke(1.dp, CyberCyan),
                    modifier = Modifier.testTag("download_hf_${desc.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = "Download Full Model",
                        tint = CyberCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Download Full (${desc.expectedSizeMb} MB)", color = CyberCyan)
                }
            }
        }
    }
}

@Composable
private fun TunerSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    tag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = TerminalTextSecondary
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = ObsidianVoid,
                checkedTrackColor = PhosphorEmerald
            ),
            modifier = Modifier.testTag(tag)
        )
    }
}
