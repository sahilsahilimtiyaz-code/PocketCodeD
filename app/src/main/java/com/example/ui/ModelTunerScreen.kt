package com.example.ui

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
import androidx.compose.material.icons.filled.DeveloperBoard
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
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
    onTriggerOomTrimTest: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("model_tuner_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Hyper-Edge Benchmark & Persistent Disk KV Snapshot Deck
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
                            imageVector = Icons.Default.Speed,
                            contentDescription = "Hyper-Edge Speed & Memory Optimizer",
                            tint = PhosphorEmerald
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Hyper-Edge Speed & 4GB/6GB RAM Optimizer",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "6-Layer Rolling mmap + PLD + LayerSkip + AST Skeletonizer + Flash KV Snapshot",
                                style = MaterialTheme.typography.labelSmall,
                                color = CyberCyan
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
                            onClick = onRunBenchmark,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PhosphorEmerald,
                                contentColor = ObsidianVoid
                            ),
                            modifier = Modifier.testTag("run_benchmark_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = "Run Benchmark",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Run Hyper-Edge Benchmark", fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = onSaveKvSnapshot,
                            border = BorderStroke(1.dp, CyberCyan),
                            modifier = Modifier.testTag("save_kv_snapshot_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Save,
                                contentDescription = "Save KV Snapshot",
                                tint = CyberCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Save Flash KV", color = CyberCyan)
                        }

                        OutlinedButton(
                            onClick = onRestoreKvSnapshot,
                            border = BorderStroke(1.dp, ArgonViolet),
                            modifier = Modifier.testTag("restore_kv_snapshot_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Restore,
                                contentDescription = "Instant Restore KV",
                                tint = ArgonViolet,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("0-Prefill KV Resume (<25ms)", color = ArgonViolet)
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
                                    text = "BENCHMARK REPORT • ${benchmarkReport.modelId}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = PhosphorEmerald,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "• Decode Speed: ${String.format("%.1f", benchmarkReport.baselineDecodeTps)} tok/s -> ${String.format("%.1f", benchmarkReport.optimizedDecodeTps)} tok/s (${String.format("%.2f", benchmarkReport.totalSpeedupMultiplier)}x speedup)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CyberCyan
                                )
                                Text(
                                    text = "• Prefill Speed: ${String.format("%.1f", benchmarkReport.baselinePrefillTps)} tok/s -> ${String.format("%.1f", benchmarkReport.optimizedPrefillTps)} tok/s (-${benchmarkReport.dietTokenReductionPct}% prompt tokens)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = PhosphorEmerald
                                )
                                Text(
                                    text = "• Resident RAM (RSS): ${benchmarkReport.baselineRamMb} MB -> ${benchmarkReport.optimizedRamMb} MB (Fits 4GB RAM!)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AmberWarning
                                )
                                Text(
                                    text = "• Active Stack: ${benchmarkReport.activeOptimizationsSummary}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TerminalTextSecondary
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. Hardware Profile Quick Presets & Emergency OOM Watchdog Guard
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
                            imageVector = Icons.Default.Memory,
                            contentDescription = "RAM Watchdog",
                            tint = AmberWarning
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "RAM Watchdog & Hardware Profiles",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "One-tap 4GB/6GB presets & TRIM_MEMORY_RUNNING_CRITICAL guard",
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
                            Text("4GB RAM Profile (ctx=4096, K=q8_0, V=q4_0)", fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { onApplyPreset(false) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CyberCyan,
                                contentColor = ObsidianVoid
                            ),
                            modifier = Modifier.testTag("preset_6gb_button")
                        ) {
                            Text("6GB+ RAM Profile (ctx=8192, K=q8_0, V=q8_0)", fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = onTriggerOomTrimTest,
                            border = BorderStroke(1.dp, CrimsonAlert),
                            modifier = Modifier.testTag("trigger_oom_trim_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.CleaningServices,
                                contentDescription = "OOM Flush",
                                tint = CrimsonAlert,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Test Emergency OOM KV Flush", color = CrimsonAlert)
                        }
                    }

                    if (!engineMetrics.lastOomTrimSummary.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Last Watchdog / KV Action: ${engineMetrics.lastOomTrimSummary}",
                            style = MaterialTheme.typography.labelSmall,
                            color = AmberWarning
                        )
                    }
                }
            }
        }

        // 3. Curated HuggingFace GGUF Model Hub
        item {
            Text(
                text = "CURATED HUGGINGFACE GGUF CODING MODELS (1.3B TO 7B LAYER-STREAM)",
                style = MaterialTheme.typography.labelLarge,
                color = PhosphorEmerald
            )
        }

        items(modelStates, key = { it.descriptor.id }) { state ->
            ModelHubItemCard(
                state = state,
                isActive = engineConfig.activeModelId == state.descriptor.id,
                onActivate = { onActivateModel(state.descriptor.id) },
                onSynthesizeLocal = { onSynthesizeLocalGguf(state.descriptor.id) },
                onStartDownload = { onStartDownload(state.descriptor.id) },
                onPauseDownload = { onPauseDownload(state.descriptor.id) }
            )
        }

        // 4. Native C++ llama.cpp & KV Cache Hardware Tuner
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
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Hardware Tuner",
                            tint = CyberCyan
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "llama.cpp NDK & KV Cache Tuner",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Flags: -DGGML_USE_KLEIDIAI=ON • dotprod • i8mm • fp16 • Vulkan",
                                style = MaterialTheme.typography.labelSmall,
                                color = CyberCyan
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Context Window (n_ctx): ${engineConfig.contextSize} tokens",
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
                        text = "CPU Big-Core Threads (n_threads): ${engineConfig.threads}",
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
                        text = "Adreno/Mali Vulkan GPU Layers (n_gpu_layers): ${engineConfig.gpuLayers}",
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
                        text = "KV Cache Key Quantization (type_k): ${engineConfig.typeK.label.uppercase()}",
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
                        text = "KV Cache Value Quantization (type_v): ${engineConfig.typeV.label.uppercase()}",
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
                        title = "Rolling 6-Layer mmap Streaming (MADV_WILLNEED / DONTNEED)",
                        subtitle = "Streams 3B–7B models through a 6-layer physical RAM window (<1.1 GB RSS on 4GB phones)",
                        checked = engineConfig.enableLayerStreaming,
                        onCheckedChange = onToggleLayerStreaming,
                        tag = "switch_layer_streaming"
                    )
                    TunerSwitchRow(
                        title = "Self-Speculative Early-Exit LayerSkip (8-Layer Draft)",
                        subtitle = "Drafts tokens using early transformer layers and batch-verifies on full depth",
                        checked = engineConfig.enableSelfSpeculativeLayerSkip,
                        onCheckedChange = onToggleLayerSkip,
                        tag = "switch_layer_skip"
                    )
                    TunerSwitchRow(
                        title = "AST-Aware Semantic Code Skeletonizer",
                        subtitle = "Elides untouched function bodies in reference files while keeping all signatures & types",
                        checked = engineConfig.enableAstCodeSkeletonizer,
                        onCheckedChange = onToggleAstSkeletonizer,
                        tag = "switch_ast_skeletonizer"
                    )
                    TunerSwitchRow(
                        title = "ARM64 Big-Core Affinity Pinning (sched_setaffinity)",
                        subtitle = "Locks inference compute threads to high-frequency Prime/Big Cortex cores",
                        checked = engineConfig.pinBigCpuCores,
                        onCheckedChange = onToggleBigCorePinning,
                        tag = "switch_big_core_pinning"
                    )
                    TunerSwitchRow(
                        title = "Flash Attention (-fa on)",
                        subtitle = "Fused QKV attention kernel with zero temporary attention matrix allocation",
                        checked = engineConfig.flashAttn,
                        onCheckedChange = onToggleFlashAttn,
                        tag = "switch_flash_attn"
                    )
                    TunerSwitchRow(
                        title = "Prompt Lookup Decoding (PLD Speculative N-Gram)",
                        subtitle = "Drafts 4–8 tokens from input code files for 2x–3x tok/s with 0 MB extra RAM",
                        checked = engineConfig.enablePld,
                        onCheckedChange = onTogglePld,
                        tag = "switch_pld"
                    )
                    TunerSwitchRow(
                        title = "Prefix KV Cache Pinning (n_keep)",
                        subtitle = "Pins system prompt & tool schemas in KV slot 0 across multi-turn agent loops",
                        checked = engineConfig.pinSystemPrefix,
                        onCheckedChange = onTogglePrefixPinning,
                        tag = "switch_prefix_pin"
                    )
                    TunerSwitchRow(
                        title = "\"Harness Diet\" Agent Middleware",
                        subtitle = "Compresses 5,000+ token agent prompts & enforces 85% sliding window",
                        checked = engineConfig.enableHarnessDiet,
                        onCheckedChange = onToggleHarnessDiet,
                        tag = "switch_harness_diet"
                    )
                    TunerSwitchRow(
                        title = "Hybrid Gemini-4-Argon High-Thinking Escalation",
                        subtitle = "Routes complex architectural queries & gemini-4-argon alias to High-Thinking",
                        checked = engineConfig.enableHybridHighThinking,
                        onCheckedChange = onToggleHybridThinking,
                        tag = "switch_hybrid_thinking"
                    )
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
    onActivate: () -> Unit,
    onSynthesizeLocal: () -> Unit,
    onStartDownload: () -> Unit,
    onPauseDownload: () -> Unit
) {
    val desc = state.descriptor
    val borderColor = if (isActive) PhosphorEmerald else TerminalBorder

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) TerminalCardElevated else TerminalSlate
        ),
        border = BorderStroke(if (isActive) 1.5.dp else 1.dp, borderColor),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
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
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = desc.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${desc.parameterCount} params • ${desc.quantization} • Min ${desc.minRamGb}GB RAM • ~${desc.expectedSizeMb}MB",
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
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Active mmap model",
                                tint = PhosphorEmerald,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "MMAP ACTIVE",
                                style = MaterialTheme.typography.labelSmall,
                                color = PhosphorEmerald,
                                fontWeight = FontWeight.Bold
                            )
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
                    text = "${state.downloadedBytes / (1024 * 1024)}MB / ${state.totalBytes / (1024 * 1024)}MB (${state.speedKbps} KB/s)",
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
                            contentDescription = "Load via mmap",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isActive) "Loaded in mmap" else "Load via mmap", fontWeight = FontWeight.Bold)
                    }
                } else {
                    OutlinedButton(
                        onClick = onSynthesizeLocal,
                        border = BorderStroke(1.dp, PhosphorEmerald),
                        modifier = Modifier.testTag("synthesize_model_${desc.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeveloperBoard,
                            contentDescription = "Synthesize Local GGUF v3",
                            tint = PhosphorEmerald,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Synthesize Local GGUF v3", color = PhosphorEmerald)
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
                        Text("Pause HF Download", color = AmberWarning)
                    }
                } else {
                    OutlinedButton(
                        onClick = onStartDownload,
                        border = BorderStroke(1.dp, CyberCyan),
                        modifier = Modifier.testTag("download_hf_${desc.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = "Download from HuggingFace",
                            tint = CyberCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (state.status == DownloadStatus.PAUSED) "Resume HF Download" else "Pull Full HF GGUF",
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
