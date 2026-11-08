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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Pause
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
    onOpenDaemonTab: () -> Unit = {}
) {
    var showAdvancedSettings by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("model_tuner_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Simple Section Header: Choose & Load Coding AI Model
        item {
            Column {
                Text(
                    text = "CHOOSE YOUR CODING AI MODEL",
                    style = MaterialTheme.typography.titleMedium,
                    color = PhosphorEmerald,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Tap 'Use This Model Now' for instant setup, or download the full offline GGUF file.",
                    style = MaterialTheme.typography.labelSmall,
                    color = TerminalTextSecondary
                )
            }
        }

        // 2. Curated Coding AI Models (Immediately visible at the top!)
        items(modelStates, key = { it.descriptor.id }) { state ->
            ModelHubItemCard(
                state = state,
                isActive = engineConfig.activeModelId == state.descriptor.id,
                baseUrl = baseUrl,
                activeApiKey = activeApiKey,
                opencodeJsonSnippet = opencodeJsonSnippet,
                onActivate = { onActivateModel(state.descriptor.id) },
                onSynthesizeLocal = { onSynthesizeLocalGguf(state.descriptor.id) },
                onStartDownload = { onStartDownload(state.descriptor.id) },
                onPauseDownload = { onPauseDownload(state.descriptor.id) },
                onStatusToast = onStatusToast,
                onOpenDaemonTab = onOpenDaemonTab
            )
        }

        // 3. 1-Tap Phone RAM Mode & Speed Test Card
        item {
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
                            contentDescription = "Phone RAM Mode",
                            tint = PhosphorEmerald
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "1-Tap Phone RAM & Speed Booster",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Select your phone's RAM size to automatically optimize speed and prevent crashes",
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
                            Text("4GB RAM Phone (Safe Mode)", fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { onApplyPreset(false) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CyberCyan,
                                contentColor = ObsidianVoid
                            ),
                            modifier = Modifier.testTag("preset_6gb_button")
                        ) {
                            Text("6GB+ RAM Phone (Fast Mode)", fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = onRunBenchmark,
                            border = BorderStroke(1.dp, PhosphorEmerald),
                            modifier = Modifier.testTag("run_benchmark_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = "Run Speed Test",
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
                                    text = "SPEED TEST RESULTS • ${benchmarkReport.modelId}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = PhosphorEmerald,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "• Writing Speed: ${String.format("%.1f", benchmarkReport.optimizedDecodeTps)} tokens/sec (${String.format("%.2f", benchmarkReport.totalSpeedupMultiplier)}x faster)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CyberCyan
                                )
                                Text(
                                    text = "• Reading Speed: ${String.format("%.1f", benchmarkReport.optimizedPrefillTps)} tokens/sec (-${benchmarkReport.dietTokenReductionPct}% smaller prompts)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = PhosphorEmerald
                                )
                                Text(
                                    text = "• Memory Used: ${benchmarkReport.optimizedRamMb} MB (Down from ${benchmarkReport.baselineRamMb} MB — Fits 4GB RAM!)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AmberWarning
                                )
                            }
                        }
                    }
                }
            }
        }

        // 4. Collapsible Advanced Engine & Memory Settings (Optional)
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
                                    text = "Already auto-tuned! Open only if you want manual CPU/GPU/Memory sliders",
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
                                        contentDescription = "Save Memory Snapshot",
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
                                        contentDescription = "Restore Memory Snapshot",
                                        tint = ArgonViolet,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Fast Resume Cache", color = ArgonViolet)
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
                                    Text("Free Up Memory Now", color = CrimsonAlert)
                                }
                            }

                            if (!engineMetrics.lastOomTrimSummary.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Last Memory Action: ${engineMetrics.lastOomTrimSummary}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AmberWarning
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Text(
                                text = "Context Length (Memory Window): ${engineConfig.contextSize} tokens",
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
                                text = "Key Cache Compression: ${engineConfig.typeK.label.uppercase()}",
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
                                text = "Value Cache Compression: ${engineConfig.typeV.label.uppercase()}",
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
private fun ModelHubItemCard(
    state: ModelDownloadState,
    isActive: Boolean,
    baseUrl: String,
    activeApiKey: String,
    opencodeJsonSnippet: String,
    onActivate: () -> Unit,
    onSynthesizeLocal: () -> Unit,
    onStartDownload: () -> Unit,
    onPauseDownload: () -> Unit,
    onStatusToast: (String) -> Unit,
    onOpenDaemonTab: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val desc = state.descriptor
    val borderColor = if (isActive) PhosphorEmerald else TerminalBorder

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) TerminalCardElevated else TerminalSlate
        ),
        border = BorderStroke(if (isActive) 2.dp else 1.dp, borderColor),
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
                        color = CyberCyan.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.5f))
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

                if (isActive) {
                    Surface(
                        color = PhosphorEmerald.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, PhosphorEmerald)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Active Model",
                                tint = PhosphorEmerald,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "ACTIVE & READY",
                                style = MaterialTheme.typography.labelSmall,
                                color = PhosphorEmerald,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // When this model is loaded, show a crystal-clear Connection Box right inside the card!
            if (isActive) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    color = ObsidianVoid,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, PhosphorEmerald.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "YOUR API DETAILS FOR THIS MODEL (TAP TO COPY)",
                            style = MaterialTheme.typography.labelSmall,
                            color = PhosphorEmerald,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Base URL:  $baseUrl",
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = JetBrainsMonoFamily,
                            color = CyberCyan,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "API Key:   $activeApiKey",
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = JetBrainsMonoFamily,
                            color = PhosphorEmerald,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Model ID:  ${desc.id}",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = JetBrainsMonoFamily,
                            color = TerminalTextSecondary
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    clipboard.setText(AnnotatedString(baseUrl))
                                    onStatusToast("Copied Base URL: $baseUrl")
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = CyberCyan,
                                    contentColor = ObsidianVoid
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy URL", modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Copy URL", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = {
                                    clipboard.setText(AnnotatedString(activeApiKey))
                                    onStatusToast("Copied API Key: $activeApiKey")
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = PhosphorEmerald,
                                    contentColor = ObsidianVoid
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.Key, contentDescription = "Copy API Key", modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Copy API Key", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                            if (opencodeJsonSnippet.isNotBlank()) {
                                OutlinedButton(
                                    onClick = {
                                        clipboard.setText(AnnotatedString(opencodeJsonSnippet))
                                        onStatusToast("Copied opencode.json for ${desc.id}!")
                                    },
                                    border = BorderStroke(1.dp, ArgonViolet),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy opencode.json", tint = ArgonViolet, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Copy opencode.json", style = MaterialTheme.typography.labelSmall, color = ArgonViolet)
                                }
                            }
                            OutlinedButton(
                                onClick = onOpenDaemonTab,
                                border = BorderStroke(1.dp, AmberWarning),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.Dns, contentDescription = "Open Home & API", tint = AmberWarning, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Open Home & API", style = MaterialTheme.typography.labelSmall, color = AmberWarning)
                            }
                        }
                    }
                }
            }

            if (state.status == DownloadStatus.DOWNLOADING || state.status == DownloadStatus.PAUSED) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { state.progressFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = CyberCyan,
                    trackColor = ObsidianVoid
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Downloading: ${state.downloadedBytes / (1024 * 1024)} MB / ${state.totalBytes / (1024 * 1024)} MB (${state.speedKbps} KB/s)",
                    style = MaterialTheme.typography.labelSmall,
                    color = CyberCyan
                )
            }

            if (!state.errorMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = state.errorMessage,
                    style = MaterialTheme.typography.labelSmall,
                    color = AmberWarning
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (state.status == DownloadStatus.READY) {
                    Button(
                        onClick = onActivate,
                        enabled = !isActive,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PhosphorEmerald,
                            contentColor = ObsidianVoid
                        ),
                        modifier = Modifier.testTag("activate_model_${desc.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = "Use This Model",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isActive) "Currently Loaded" else "Use This Model Now",
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Button(
                        onClick = onSynthesizeLocal,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PhosphorEmerald,
                            contentColor = ObsidianVoid
                        ),
                        modifier = Modifier.testTag("synthesize_model_${desc.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = "Use This Model Now (Instant)",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Use This Model Now (Instant)", fontWeight = FontWeight.Bold)
                    }
                }

                if (state.status == DownloadStatus.DOWNLOADING) {
                    OutlinedButton(
                        onClick = onPauseDownload,
                        border = BorderStroke(1.dp, AmberWarning)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Pause,
                            contentDescription = "Pause Download",
                            tint = AmberWarning,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Pause Download", color = AmberWarning)
                    }
                } else {
                    OutlinedButton(
                        onClick = onStartDownload,
                        border = BorderStroke(1.dp, CyberCyan),
                        modifier = Modifier.testTag("download_hf_${desc.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = "Download Full Offline Model",
                            tint = CyberCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (state.status == DownloadStatus.PAUSED) "Resume Download" else "Download Full Offline Model",
                            color = CyberCyan
                        )
                    }
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
