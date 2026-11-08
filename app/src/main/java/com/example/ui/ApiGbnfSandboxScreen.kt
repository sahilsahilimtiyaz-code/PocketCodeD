package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.DownloadStatus
import com.example.model.ModelDownloadState
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.ArgonViolet
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
fun ApiGbnfSandboxScreen(
    sandboxState: SandboxUiState,
    chatMessages: List<UiChatBubble> = emptyList(),
    activeModelId: String = "Qwen2.5-Coder-1.5B-Instruct-Q4_K_M.gguf",
    modelStates: List<ModelDownloadState> = emptyList(),
    onSelectMode: (SandboxMode) -> Unit,
    onUpdatePrompt: (String) -> Unit,
    onUpdateFimPrefix: (String) -> Unit,
    onUpdateFimSuffix: (String) -> Unit,
    onRunPipeline: () -> Unit,
    onSendQuickPrompt: (String) -> Unit = {},
    onActivateModel: (String) -> Unit = {},
    onClearChat: () -> Unit = {},
    onOpenModelsTab: () -> Unit = {},
    onOpenApiKeysTab: () -> Unit = {},
    onStatusToast: (String) -> Unit = {}
) {
    val clipboard = LocalClipboardManager.current
    var showGrammarInspector by remember { mutableStateOf(false) }
    var showQuickModelSwitcher by remember { mutableStateOf(false) }
    val cleanModelName = activeModelId.removeSuffix(".gguf")

    val downloadedModels = modelStates.filter {
        it.status == DownloadStatus.READY || it.descriptor.id == activeModelId
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianVoid)
    ) {
        // 1. Sleek Chat Action Bar (Model Selector Pill + Get API Key + Clear Chat)
        Surface(
            color = TerminalSlate,
            border = BorderStroke(0.5.dp, TerminalBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Tappable Model Selector Pill (Just like ChatGPT / Ollama!)
                    Surface(
                        color = ObsidianVoid,
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, PhosphorEmerald.copy(alpha = 0.6f)),
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { showQuickModelSwitcher = !showQuickModelSwitcher }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(PhosphorEmerald)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = cleanModelName,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 180.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.ExpandMore,
                                contentDescription = "Switch Downloaded Model",
                                tint = PhosphorEmerald,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = onOpenApiKeysTab,
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PhosphorEmerald,
                                contentColor = ObsidianVoid
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = "Get API Key",
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "API Key",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        IconButton(
                            onClick = onClearChat,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "Clear Chat",
                                tint = TerminalTextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Expandable Quick Switcher for Downloaded Models right inside Chat!
                AnimatedVisibility(visible = showQuickModelSwitcher) {
                    Surface(
                        color = ObsidianVoid,
                        border = BorderStroke(1.dp, TerminalBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "MY DOWNLOADED MODELS (TAP TO USE)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = PhosphorEmerald,
                                    fontWeight = FontWeight.Bold
                                )
                                OutlinedButton(
                                    onClick = {
                                        showQuickModelSwitcher = false
                                        onOpenModelsTab()
                                    },
                                    border = BorderStroke(1.dp, CyberCyan),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "Download More Models",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = CyberCyan
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            downloadedModels.forEach { mState ->
                                val isCurrent = mState.descriptor.id == activeModelId
                                Surface(
                                    color = if (isCurrent) PhosphorEmerald.copy(alpha = 0.16f) else TerminalSlate,
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, if (isCurrent) PhosphorEmerald else TerminalBorder),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp)
                                        .clickable {
                                            onActivateModel(mState.descriptor.id)
                                            showQuickModelSwitcher = false
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 9.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = mState.descriptor.displayName,
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "${mState.descriptor.parameterCount} • Min ${mState.descriptor.minRamGb}GB RAM",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = TerminalTextSecondary
                                            )
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (isCurrent) {
                                                Icon(
                                                    imageVector = Icons.Default.CheckCircle,
                                                    contentDescription = "In Use",
                                                    tint = PhosphorEmerald,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "In Use",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = PhosphorEmerald,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            } else {
                                                Text(
                                                    text = "Use This Model",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = CyberCyan,
                                                    fontWeight = FontWeight.Bold
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

        // 2. Scrollable Chat Conversation Area
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag("api_sandbox_list"),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(chatMessages, key = { _, it -> it.id }) { index, bubble ->
                val isLastAssistant = !bubble.isUser && index == chatMessages.lastIndex
                ChatBubbleItem(
                    bubble = bubble,
                    attachOutputTestTag = isLastAssistant,
                    onCopy = {
                        clipboard.setText(AnnotatedString(bubble.text))
                        onStatusToast("Copied response to clipboard!")
                    }
                )
            }

            // Show quick horizontal suggestion chips when at the start of a chat
            if (chatMessages.size <= 2) {
                item {
                    Column {
                        Text(
                            text = "Try asking:",
                            style = MaterialTheme.typography.labelSmall,
                            color = TerminalTextSecondary,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            QuickPromptChip("Write binary search in Kotlin") {
                                onSendQuickPrompt("Write binary search in Kotlin")
                            }
                            QuickPromptChip("Build a Python calculator") {
                                onSendQuickPrompt("Write a calculator in Python")
                            }
                            QuickPromptChip("HTML/CSS login page") {
                                onSendQuickPrompt("Create a modern HTML and CSS login page")
                            }
                            QuickPromptChip("How do I get my API key?") {
                                onSendQuickPrompt("How do I get my API key and Base URL?")
                            }
                        }
                    }
                }
            }

            // Optional GBNF Grammar Inspector
            item {
                AnimatedVisibility(visible = showGrammarInspector) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = TerminalSlate),
                        border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Compress,
                                    contentDescription = "GBNF Grammar",
                                    tint = CyberCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Compiled llama.cpp GBNF Tool Grammar",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(ObsidianVoid)
                                    .border(1.dp, TerminalBorder, RoundedCornerShape(10.dp))
                                    .padding(10.dp)
                            ) {
                                Text(
                                    text = sandboxState.compiledGbnfPreview,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = JetBrainsMonoFamily,
                                    color = CyberCyan
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. Clean Bottom Chat Composer & Mode Strip
        Surface(
            color = TerminalSlate,
            border = BorderStroke(0.5.dp, TerminalBorder),
            tonalElevation = 8.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                // Single-row horizontally scrollable Mode Strip so it never wraps or crowds the screen
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SandboxMode.entries.forEach { mode ->
                        val chipLabel = when (mode) {
                            SandboxMode.NORMAL_CHAT -> "Chat"
                            SandboxMode.AGENT_TOOL_CALL -> "Agent Tool JSON"
                            SandboxMode.FIM_COMPLETION -> "Code Autocomplete"
                            SandboxMode.HIGH_THINKING -> "High Thinking"
                        }
                        FilterChip(
                            selected = sandboxState.mode == mode,
                            onClick = { onSelectMode(mode) },
                            label = { Text(chipLabel, style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = when (mode) {
                                    SandboxMode.NORMAL_CHAT -> PhosphorEmerald.copy(alpha = 0.22f)
                                    SandboxMode.AGENT_TOOL_CALL -> CyberCyan.copy(alpha = 0.22f)
                                    SandboxMode.FIM_COMPLETION -> AmberWarning.copy(alpha = 0.22f)
                                    SandboxMode.HIGH_THINKING -> ArgonViolet.copy(alpha = 0.25f)
                                },
                                selectedLabelColor = when (mode) {
                                    SandboxMode.NORMAL_CHAT -> PhosphorEmerald
                                    SandboxMode.AGENT_TOOL_CALL -> CyberCyan
                                    SandboxMode.FIM_COMPLETION -> AmberWarning
                                    SandboxMode.HIGH_THINKING -> ArgonViolet
                                }
                            ),
                            modifier = Modifier.testTag("sandbox_mode_${mode.name.lowercase()}")
                        )
                    }

                    FilterChip(
                        selected = showGrammarInspector,
                        onClick = { showGrammarInspector = !showGrammarInspector },
                        label = { Text("GBNF Rules", style = MaterialTheme.typography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CyberCyan.copy(alpha = 0.2f),
                            selectedLabelColor = CyberCyan
                        )
                    )
                }

                if (sandboxState.mode == SandboxMode.HIGH_THINKING) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        color = ArgonViolet.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, ArgonViolet.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "High-Thinking Notice",
                                tint = ArgonViolet,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(id = R.string.firebase_high_thinking_notice),
                                style = MaterialTheme.typography.labelSmall,
                                color = ArgonViolet
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                if (sandboxState.mode == SandboxMode.FIM_COMPLETION) {
                    OutlinedTextField(
                        value = sandboxState.fimPrefix,
                        onValueChange = onUpdateFimPrefix,
                        label = { Text("Code Before Cursor (<|fim_prefix|>)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("sandbox_fim_prefix_input"),
                        minLines = 2,
                        maxLines = 3
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = sandboxState.fimSuffix,
                            onValueChange = onUpdateFimSuffix,
                            label = { Text("Code After Cursor (<|fim_suffix|>)") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("sandbox_fim_suffix_input"),
                            singleLine = true
                        )
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
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Autocomplete",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Complete", fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = sandboxState.userPrompt,
                            onValueChange = onUpdatePrompt,
                            placeholder = {
                                Text("Ask $cleanModelName anything...")
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("sandbox_prompt_input"),
                            shape = RoundedCornerShape(22.dp),
                            maxLines = 3
                        )
                        Button(
                            onClick = onRunPipeline,
                            enabled = !sandboxState.isStreaming,
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PhosphorEmerald,
                                contentColor = ObsidianVoid
                            ),
                            contentPadding = PaddingValues(14.dp),
                            modifier = Modifier
                                .size(50.dp)
                                .testTag("run_sandbox_pipeline_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send Message",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickPromptChip(
    label: String,
    onClick: () -> Unit
) {
    Surface(
        color = TerminalSlate,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.5f)),
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = CyberCyan,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun ChatBubbleItem(
    bubble: UiChatBubble,
    attachOutputTestTag: Boolean = false,
    onCopy: () -> Unit
) {
    val alignment = if (bubble.isUser) Alignment.End else Alignment.Start
    val bubbleBg = if (bubble.isUser) {
        PhosphorEmerald.copy(alpha = 0.18f)
    } else {
        TerminalCardElevated
    }
    val borderColor = if (bubble.isUser) {
        PhosphorEmerald.copy(alpha = 0.65f)
    } else {
        TerminalBorder
    }

    val bubbleModifier = if (attachOutputTestTag) {
        Modifier
            .widthIn(max = 560.dp)
            .testTag("sandbox_output_box")
    } else {
        Modifier.widthIn(max = 560.dp)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Surface(
            color = bubbleBg,
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (bubble.isUser) 18.dp else 4.dp,
                bottomEnd = if (bubble.isUser) 4.dp else 18.dp
            ),
            border = BorderStroke(1.dp, borderColor),
            modifier = bubbleModifier
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (bubble.isUser) Icons.Default.Person else Icons.Default.AutoAwesome,
                            contentDescription = bubble.senderName,
                            tint = if (bubble.isUser) PhosphorEmerald else CyberCyan,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = bubble.senderName,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (bubble.isUser) PhosphorEmerald else CyberCyan,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!bubble.speedBadge.isNullOrBlank()) {
                            Surface(
                                color = ObsidianVoid,
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(0.5.dp, PhosphorEmerald.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = bubble.speedBadge,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = PhosphorEmerald,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                        }

                        if (!bubble.isUser) {
                            IconButton(
                                onClick = onCopy,
                                modifier = Modifier.size(26.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy response",
                                    tint = CyberCyan,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = bubble.text,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = if (bubble.text.contains("```")) JetBrainsMonoFamily else MaterialTheme.typography.bodyMedium.fontFamily,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
