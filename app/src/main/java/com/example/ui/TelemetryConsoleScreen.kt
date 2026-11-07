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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.EngineRuntimeMetrics
import com.example.model.ExecutionRoute
import com.example.model.TelemetryLogEntry
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.ArgonViolet
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.GlassHighlight
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.PhosphorEmerald
import com.example.ui.theme.TerminalBorder
import com.example.ui.theme.TerminalSlate
import com.example.ui.theme.TerminalTextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TelemetryConsoleScreen(
    logs: List<TelemetryLogEntry>,
    engineMetrics: EngineRuntimeMetrics,
    totalDietTokensSaved: Long,
    onClearLogs: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("telemetry_console_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
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
                                imageVector = Icons.Default.Terminal,
                                contentDescription = "Live Telemetry Console",
                                tint = PhosphorEmerald
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Live Agent Telemetry Console",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Harness Diet savings: $totalDietTokensSaved tok • KV Hit Ratio: ${engineMetrics.kvCacheHitRatioPercent}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CyberCyan
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = onClearLogs,
                            border = BorderStroke(1.dp, TerminalBorder),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("clear_telemetry_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "Clear Logs",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Clear", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TelemetryBadge(
                            text = "Events: ${logs.size}",
                            color = PhosphorEmerald
                        )
                        TelemetryBadge(
                            text = "PLD Accepted: ${engineMetrics.pldAcceptedTokens}/${engineMetrics.pldDraftedTokens}",
                            color = ArgonViolet
                        )
                        TelemetryBadge(
                            text = "OOM Guards: ${engineMetrics.oomTrimEventsCount}",
                            color = AmberWarning
                        )
                    }
                }
            }
        }

        if (logs.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = TerminalSlate),
                    border = BorderStroke(1.dp, TerminalBorder),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = "No telemetry events",
                            tint = TerminalTextSecondary,
                            modifier = Modifier.size(38.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Awaiting incoming opencode / DeepSeek / Aider / Sandbox requests...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TerminalTextSecondary
                        )
                    }
                }
            }
        } else {
            items(logs, key = { it.id }) { entry ->
                TelemetryLogCard(entry = entry)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TelemetryLogCard(entry: TelemetryLogEntry) {
    val formattedTime = remember(entry.timestampMs) {
        SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(entry.timestampMs))
    }
    val routeColor = when (entry.route) {
        ExecutionRoute.NATIVE_GGUF_MMAP -> PhosphorEmerald
        ExecutionRoute.HYBRID_GEMINI_HIGH_THINKING -> ArgonViolet
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = TerminalSlate),
        border = BorderStroke(1.dp, routeColor.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${entry.method} ${entry.endpoint}",
                    style = MaterialTheme.typography.labelLarge,
                    color = routeColor,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "$formattedTime • ${entry.latencyMs}ms",
                    style = MaterialTheme.typography.labelSmall,
                    color = TerminalTextSecondary
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TelemetryBadge(text = entry.route.badge, color = routeColor)
                if (entry.tokensSavedByDiet > 0) {
                    TelemetryBadge(
                        text = "Diet: ${entry.rawPromptTokens}->${entry.compressedPromptTokens} (-${entry.tokensSavedByDiet} tok)",
                        color = AmberWarning
                    )
                }
                if (entry.gbnfGrammarApplied) {
                    TelemetryBadge(
                        text = entry.gbnfRuleSummary ?: "GBNF Tool Grammar",
                        color = CyberCyan
                    )
                }
                if (entry.kvPrefixHitTokens > 0) {
                    TelemetryBadge(
                        text = "KV Prefix Hit: ${entry.kvPrefixHitTokens} tok",
                        color = PhosphorEmerald
                    )
                }
                if (entry.pldAcceptedCount > 0) {
                    TelemetryBadge(
                        text = "PLD: ${entry.pldAcceptedCount}/${entry.pldDraftedCount} drafted",
                        color = ArgonViolet
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Speed: Prefill ${String.format("%.1f", entry.prefillTokPerSec)} tok/s | Decode ${String.format("%.1f", entry.decodeTokPerSec)} tok/s | Client: ${entry.clientAddress}",
                style = MaterialTheme.typography.labelSmall,
                color = TerminalTextSecondary
            )

            Spacer(modifier = Modifier.height(6.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(ObsidianVoid)
                    .border(1.dp, TerminalBorder, RoundedCornerShape(10.dp))
                    .padding(10.dp)
            ) {
                Text(
                    text = entry.previewSnippet,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = JetBrainsMonoFamily,
                    color = PhosphorEmerald
                )
            }
        }
    }
}

@Composable
private fun TelemetryBadge(text: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.14f),
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.5f))
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}
