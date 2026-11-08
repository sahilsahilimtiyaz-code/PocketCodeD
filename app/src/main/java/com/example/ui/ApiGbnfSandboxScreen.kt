package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.ArgonViolet
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.PhosphorEmerald
import com.example.ui.theme.TerminalBorder
import com.example.ui.theme.TerminalSlate
import com.example.ui.theme.TerminalTextSecondary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ApiGbnfSandboxScreen(
    sandboxState: SandboxUiState,
    onSelectMode: (SandboxMode) -> Unit,
    onUpdatePrompt: (String) -> Unit,
    onUpdateFimPrefix: (String) -> Unit,
    onUpdateFimSuffix: (String) -> Unit,
    onRunPipeline: () -> Unit,
    onStatusToast: (String) -> Unit = {}
) {
    val clipboard = LocalClipboardManager.current
    var showGrammarDetails by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("api_sandbox_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Easy AI Playground Card
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
                            imageVector = Icons.AutoMirrored.Filled.Rule,
                            contentDescription = "Test Your AI Model",
                            tint = PhosphorEmerald
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Test Your Loaded Coding AI",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Pick a test mode or tap an example below, then tap 'Run AI Test'",
                                style = MaterialTheme.typography.labelSmall,
                                color = TerminalTextSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SandboxMode.entries.forEach { mode ->
                            val friendlyLabel = when (mode) {
                                SandboxMode.AGENT_TOOL_CALL -> "1. Coding Agent / Tool"
                                SandboxMode.FIM_COMPLETION -> "2. Code Autocomplete"
                                SandboxMode.HIGH_THINKING -> "3. Deep Architecture"
                            }
                            FilterChip(
                                selected = sandboxState.mode == mode,
                                onClick = { onSelectMode(mode) },
                                label = { Text(friendlyLabel) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = when (mode) {
                                        SandboxMode.AGENT_TOOL_CALL -> PhosphorEmerald.copy(alpha = 0.2f)
                                        SandboxMode.FIM_COMPLETION -> CyberCyan.copy(alpha = 0.2f)
                                        SandboxMode.HIGH_THINKING -> ArgonViolet.copy(alpha = 0.25f)
                                    },
                                    selectedLabelColor = when (mode) {
                                        SandboxMode.AGENT_TOOL_CALL -> PhosphorEmerald
                                        SandboxMode.FIM_COMPLETION -> CyberCyan
                                        SandboxMode.HIGH_THINKING -> ArgonViolet
                                    }
                                ),
                                modifier = Modifier.testTag("sandbox_mode_${mode.name.lowercase()}")
                            )
                        }
                    }

                    // Show Firebase High-Thinking notice when HIGH_THINKING mode is active
                    if (sandboxState.mode == SandboxMode.HIGH_THINKING) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            color = ArgonViolet.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, ArgonViolet.copy(alpha = 0.6f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "High-Thinking Notice",
                                    tint = ArgonViolet,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(id = R.string.firebase_high_thinking_notice),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ArgonViolet
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (sandboxState.mode == SandboxMode.FIM_COMPLETION) {
                        OutlinedTextField(
                            value = sandboxState.fimPrefix,
                            onValueChange = onUpdateFimPrefix,
                            label = { Text("Code Before Cursor") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("sandbox_fim_prefix_input"),
                            minLines = 3
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = sandboxState.fimSuffix,
                            onValueChange = onUpdateFimSuffix,
                            label = { Text("Code After Cursor") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("sandbox_fim_suffix_input"),
                            minLines = 2
                        )
                    } else {
                        Text(
                            text = "Quick Example Prompts (Tap to Fill):",
                            style = MaterialTheme.typography.labelSmall,
                            color = TerminalTextSecondary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedButton(
                                onClick = { onUpdatePrompt("Write a fast binary search function in Kotlin with unit tests") },
                                border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.6f)),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text("Binary Search", style = MaterialTheme.typography.labelSmall, color = CyberCyan)
                            }
                            OutlinedButton(
                                onClick = { onUpdatePrompt("Refactor PocketEngine.cpp to enable flash_attn and quantized KV cache") },
                                border = BorderStroke(1.dp, PhosphorEmerald.copy(alpha = 0.6f)),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text("Optimize C++ Code", style = MaterialTheme.typography.labelSmall, color = PhosphorEmerald)
                            }
                            OutlinedButton(
                                onClick = { onUpdatePrompt("Fix null pointer exception and add error handling to HTTP client") },
                                border = BorderStroke(1.dp, ArgonViolet.copy(alpha = 0.6f)),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text("Fix Bug", style = MaterialTheme.typography.labelSmall, color = ArgonViolet)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = sandboxState.userPrompt,
                            onValueChange = onUpdatePrompt,
                            label = { Text("Ask your coding question or task") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("sandbox_prompt_input"),
                            minLines = 2
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = sandboxState.statusBadge,
                            style = MaterialTheme.typography.labelSmall,
                            color = CyberCyan,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = onRunPipeline,
                            enabled = !sandboxState.isStreaming,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PhosphorEmerald,
                                contentColor = ObsidianVoid
                            ),
                            modifier = Modifier.testTag("run_sandbox_pipeline_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Run AI Test",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (sandboxState.isStreaming) "Running..." else "Run AI Test",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // 2. Live AI Output Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = TerminalSlate),
                border = BorderStroke(1.dp, PhosphorEmerald.copy(alpha = 0.5f)),
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
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "AI Response",
                                tint = PhosphorEmerald
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "AI Response",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Prompt compressed: ${sandboxState.rawTokensBeforeDiet} -> ${sandboxState.compressedTokensAfterDiet} tokens",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AmberWarning
                                )
                            }
                        }

                        if (sandboxState.outputStreamText.isNotBlank()) {
                            OutlinedButton(
                                onClick = {
                                    clipboard.setText(AnnotatedString(sandboxState.outputStreamText))
                                    onStatusToast("Copied AI response to clipboard!")
                                },
                                border = BorderStroke(1.dp, CyberCyan),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy Output",
                                    tint = CyberCyan,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Copy", style = MaterialTheme.typography.labelSmall, color = CyberCyan)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(ObsidianVoid)
                            .border(1.dp, TerminalBorder, RoundedCornerShape(10.dp))
                            .padding(12.dp)
                            .testTag("sandbox_output_box")
                    ) {
                        Text(
                            text = sandboxState.outputStreamText.ifBlank {
                                "// Tap 'Run AI Test' above to see the model's live output here."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = JetBrainsMonoFamily,
                            color = PhosphorEmerald
                        )
                    }
                }
            }
        }

        // 3. Collapsible Technical JSON Grammar Inspector (Optional)
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
                                imageVector = Icons.Default.Compress,
                                contentDescription = "JSON Schema Rules",
                                tint = CyberCyan
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "JSON Tool Grammar (Advanced)",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Ensures 100% valid JSON output for OpenCode & DeepSeek tools",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TerminalTextSecondary
                                )
                            }
                        }
                        OutlinedButton(
                            onClick = { showGrammarDetails = !showGrammarDetails },
                            border = BorderStroke(1.dp, TerminalBorder),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = if (showGrammarDetails) "Hide" else "View Rules",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }

                    AnimatedVisibility(visible = showGrammarDetails) {
                        Column {
                            Spacer(modifier = Modifier.height(10.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(ObsidianVoid)
                                    .border(1.dp, TerminalBorder, RoundedCornerShape(10.dp))
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = sandboxState.compiledGbnfPreview,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontFamily = JetBrainsMonoFamily,
                                    color = CyberCyan
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
