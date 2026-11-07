package com.example.ui

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
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Compress
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
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
    onRunPipeline: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("api_sandbox_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Mode Selector & Run Pipeline Deck
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
                            imageVector = Icons.AutoMirrored.Filled.Rule,
                            contentDescription = "API & GBNF Sandbox",
                            tint = PhosphorEmerald
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Interactive On-Device API & GBNF Console",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Test Harness Diet compression, GBNF grammar sampling, FIM & High-Thinking",
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
                            FilterChip(
                                selected = sandboxState.mode == mode,
                                onClick = { onSelectMode(mode) },
                                label = { Text(mode.label) },
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
                                    contentDescription = "Firebase AI Logic Notice",
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

                    Spacer(modifier = Modifier.height(12.dp))

                    if (sandboxState.mode == SandboxMode.FIM_COMPLETION) {
                        OutlinedTextField(
                            value = sandboxState.fimPrefix,
                            onValueChange = onUpdateFimPrefix,
                            label = { Text("<|fim_prefix|> Code Before Cursor") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("sandbox_fim_prefix_input"),
                            minLines = 3
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = sandboxState.fimSuffix,
                            onValueChange = onUpdateFimSuffix,
                            label = { Text("<|fim_suffix|> Code After Cursor") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("sandbox_fim_suffix_input"),
                            minLines = 2
                        )
                    } else {
                        OutlinedTextField(
                            value = sandboxState.userPrompt,
                            onValueChange = onUpdatePrompt,
                            label = { Text("Agent / Developer Task Prompt") },
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
                                contentDescription = "Execute Pipeline",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (sandboxState.isStreaming) "Streaming..." else "Run Live Pipeline",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // 2. Live Output Stream Terminal
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "Model Output",
                                tint = PhosphorEmerald
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Streaming Output / Tool Call Envelope",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "${sandboxState.rawTokensBeforeDiet} -> ${sandboxState.compressedTokensAfterDiet} tok",
                            style = MaterialTheme.typography.labelSmall,
                            color = AmberWarning
                        )
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
                                "// Tap 'Run Live Pipeline' above to stream GBNF-constrained tool calls, FIM completions, or High-Thinking output."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = JetBrainsMonoFamily,
                            color = PhosphorEmerald
                        )
                    }
                }
            }
        }

        // 3. Compiled llama.cpp GBNF Grammar Inspector
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
                            imageVector = Icons.Default.Compress,
                            contentDescription = "Compiled GBNF Grammar",
                            tint = CyberCyan
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Compiled llama.cpp GBNF Tool Grammar",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Dynamically synthesized from OpenAI tools[] JSON schema to guarantee 100% valid JSON",
                                style = MaterialTheme.typography.labelSmall,
                                color = TerminalTextSecondary
                            )
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
