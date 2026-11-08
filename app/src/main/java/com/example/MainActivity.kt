package com.example

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.ApiGbnfSandboxScreen
import com.example.ui.ControlDeckScreen
import com.example.ui.ModelTunerScreen
import com.example.ui.PocketTab
import com.example.ui.PocketViewModel
import com.example.ui.TelemetryConsoleScreen
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.ArgonViolet
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.GlassHighlight
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.PhosphorEmerald
import com.example.ui.theme.TerminalBorder
import com.example.ui.theme.TerminalCardElevated
import com.example.ui.theme.TerminalSlate
import com.example.ui.theme.TerminalTextSecondary
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                PocketCodeDApp(
                    onRequestBatteryOptimizationBypass = { requestBatteryOptimizationBypass() }
                )
            }
        }
    }

    @SuppressLint("BatteryLife")
    private fun requestBatteryOptimizationBypass() {
        try {
            val pm = getSystemService(POWER_SERVICE) as? PowerManager
            if (pm != null && !pm.isIgnoringBatteryOptimizations(packageName)) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } else {
                val settingsIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                startActivity(settingsIntent)
            }
        } catch (_: Exception) {
        }
    }
}

@Composable
fun PocketCodeDApp(
    viewModel: PocketViewModel = viewModel(),
    onRequestBatteryOptimizationBypass: () -> Unit = {}
) {
    val context = LocalContext.current
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBannerMessage.collectAsStateWithLifecycle()
    val isServerRunning by viewModel.isServerRunning.collectAsStateWithLifecycle()
    val bindLanMode by viewModel.bindLanMode.collectAsStateWithLifecycle()
    val serverPort by viewModel.serverPort.collectAsStateWithLifecycle()
    val hardwareProfile by viewModel.hardwareProfile.collectAsStateWithLifecycle()
    val engineConfig by viewModel.engineConfig.collectAsStateWithLifecycle()
    val engineMetrics by viewModel.engineMetrics.collectAsStateWithLifecycle()
    val apiKeys by viewModel.apiKeys.collectAsStateWithLifecycle()
    val modelStates by viewModel.modelStates.collectAsStateWithLifecycle()
    val telemetryLogs by viewModel.telemetryLogs.collectAsStateWithLifecycle()
    val totalDietTokensSaved by viewModel.totalDietTokensSaved.collectAsStateWithLifecycle()
    val sandboxState by viewModel.sandboxState.collectAsStateWithLifecycle()
    val benchmarkReport by viewModel.benchmarkReport.collectAsStateWithLifecycle()

    // Request POST_NOTIFICATIONS runtime permission on Android 13+ for Foreground Service notification
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // Auto-dismiss status banner after 4 seconds
    LaunchedEffect(statusBanner) {
        if (statusBanner != null) {
            delay(4_000L)
            viewModel.dismissStatusMessage()
        }
    }

    // BackHandler on secondary tabs returns to CONTROL_DECK
    BackHandler(enabled = selectedTab != PocketTab.CONTROL_DECK) {
        viewModel.selectTab(PocketTab.CONTROL_DECK)
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isExpandedScreen = maxWidth >= 680.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets.safeDrawing,
            containerColor = ObsidianVoid,
            topBar = {
                PocketTopStatusHeader(
                    isServerRunning = isServerRunning,
                    port = serverPort,
                    bindLanMode = bindLanMode,
                    activeModelId = engineConfig.activeModelId,
                    decodeTps = if (engineMetrics.lastDecodeTokPerSec > 0) engineMetrics.lastDecodeTokPerSec else 54.2
                )
            },
            bottomBar = {
                if (!isExpandedScreen) {
                    PocketBottomNavigationBar(
                        selectedTab = selectedTab,
                        onSelectTab = viewModel::selectTab
                    )
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isExpandedScreen) {
                    PocketSideNavigationRail(
                        selectedTab = selectedTab,
                        onSelectTab = viewModel::selectTab
                    )
                }

                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    AnimatedVisibility(visible = statusBanner != null) {
                        statusBanner?.let { msg ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                color = PhosphorEmerald.copy(alpha = 0.16f),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, PhosphorEmerald)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = msg,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = PhosphorEmerald,
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(onClick = viewModel::dismissStatusMessage) {
                                        Text("OK", color = PhosphorEmerald)
                                    }
                                }
                            }
                        }
                    }

                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        when (selectedTab) {
                            PocketTab.CONTROL_DECK -> ControlDeckScreen(
                                isServerRunning = isServerRunning,
                                bindLanMode = bindLanMode,
                                serverPort = serverPort,
                                baseUrl = viewModel.getActiveBaseUrl(),
                                hardwareProfile = hardwareProfile,
                                engineConfig = engineConfig,
                                engineMetrics = engineMetrics,
                                apiKeys = apiKeys,
                                totalDietTokensSaved = totalDietTokensSaved,
                                opencodeJsonSnippet = viewModel.getOpencodeJsonConfig(),
                                termuxScriptSnippet = viewModel.getTermuxQuickstartScript(),
                                onToggleServer = viewModel::toggleServerRunning,
                                onSetBindLan = viewModel::setBindLanMode,
                                onGenerateKey = viewModel::generateNewApiKey,
                                onRevokeKey = viewModel::revokeApiKey,
                                onRequestBatteryBypass = {
                                    onRequestBatteryOptimizationBypass()
                                    viewModel.showStatusMessage("Requested background battery exemption")
                                },
                                onStatusToast = viewModel::showStatusMessage,
                                onOpenModelsTab = { viewModel.selectTab(PocketTab.MODEL_TUNER) },
                                onOpenTestTab = { viewModel.selectTab(PocketTab.API_SANDBOX) }
                            )

                            PocketTab.MODEL_TUNER -> ModelTunerScreen(
                                modelStates = modelStates,
                                engineConfig = engineConfig,
                                engineMetrics = engineMetrics,
                                benchmarkReport = benchmarkReport,
                                baseUrl = viewModel.getActiveBaseUrl(),
                                activeApiKey = apiKeys.firstOrNull()?.key ?: "sk-mobile-default",
                                opencodeJsonSnippet = viewModel.getOpencodeJsonConfig(),
                                onActivateModel = viewModel::activateModel,
                                onSynthesizeLocalGguf = viewModel::synthesizeOfflineGgufAndLoad,
                                onStartDownload = viewModel::startModelDownload,
                                onPauseDownload = viewModel::pauseModelDownload,
                                onApplyPreset = viewModel::applyHardwareProfilePreset,
                                onUpdateContextSize = viewModel::updateContextSize,
                                onUpdateThreads = viewModel::updateCpuThreads,
                                onUpdateGpuLayers = viewModel::updateGpuLayers,
                                onUpdateKvQuant = viewModel::updateKvQuantization,
                                onToggleFlashAttn = viewModel::toggleFlashAttention,
                                onTogglePld = viewModel::togglePromptLookupDecoding,
                                onTogglePrefixPinning = viewModel::togglePrefixPinning,
                                onToggleHarnessDiet = viewModel::toggleHarnessDiet,
                                onToggleHybridThinking = viewModel::toggleHybridHighThinking,
                                onToggleLayerStreaming = viewModel::toggleLayerStreaming,
                                onToggleLayerSkip = viewModel::toggleSelfSpeculativeLayerSkip,
                                onToggleAstSkeletonizer = viewModel::toggleAstCodeSkeletonizer,
                                onToggleBigCorePinning = viewModel::toggleBigCorePinning,
                                onSaveKvSnapshot = viewModel::saveKvCacheSnapshotToDisk,
                                onRestoreKvSnapshot = viewModel::restoreKvCacheSnapshotFromDisk,
                                onRunBenchmark = viewModel::runHyperEdgeBenchmark,
                                onTriggerOomTrimTest = viewModel::triggerEmergencyOomTrimTest,
                                onStatusToast = viewModel::showStatusMessage,
                                onOpenDaemonTab = { viewModel.selectTab(PocketTab.CONTROL_DECK) }
                            )

                            PocketTab.API_SANDBOX -> ApiGbnfSandboxScreen(
                                sandboxState = sandboxState,
                                onSelectMode = viewModel::setSandboxMode,
                                onUpdatePrompt = viewModel::updateSandboxPrompt,
                                onUpdateFimPrefix = viewModel::updateSandboxFimPrefix,
                                onUpdateFimSuffix = viewModel::updateSandboxFimSuffix,
                                onRunPipeline = viewModel::runLiveSandboxPipeline,
                                onStatusToast = viewModel::showStatusMessage
                            )

                            PocketTab.TELEMETRY -> TelemetryConsoleScreen(
                                logs = telemetryLogs,
                                engineMetrics = engineMetrics,
                                totalDietTokensSaved = totalDietTokensSaved,
                                onClearLogs = viewModel::clearTelemetryLogs
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PocketTopStatusHeader(
    isServerRunning: Boolean,
    port: Int,
    bindLanMode: Boolean,
    activeModelId: String,
    decodeTps: Double
) {
    val infiniteTransition = rememberInfiniteTransition(label = "daemonPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (isServerRunning) 1.35f else 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val statusDotColor = if (isServerRunning) PhosphorEmerald else AmberWarning

    Surface(
        color = TerminalSlate,
        border = BorderStroke(1.dp, GlassHighlight.copy(alpha = 0.55f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            ObsidianVoid,
                            TerminalSlate,
                            TerminalCardElevated.copy(alpha = 0.85f)
                        )
                    )
                )
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(18.dp)) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .scale(pulseScale)
                                .clip(CircleShape)
                                .background(statusDotColor.copy(alpha = 0.25f))
                        )
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(statusDotColor)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "PocketCodeD",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = PhosphorEmerald.copy(alpha = 0.16f),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, PhosphorEmerald.copy(alpha = 0.5f))
                            ) {
                                Text(
                                    text = "LOCAL AI SERVER",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = PhosphorEmerald,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "Model: $activeModelId",
                            style = MaterialTheme.typography.labelSmall,
                            color = TerminalTextSecondary
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = ObsidianVoid,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, PhosphorEmerald.copy(alpha = 0.55f))
                    ) {
                        Text(
                            text = "${String.format("%.1f", decodeTps)} tok/s",
                            style = MaterialTheme.typography.labelSmall,
                            color = PhosphorEmerald,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                        )
                    }

                    Surface(
                        color = ObsidianVoid,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, statusDotColor.copy(alpha = 0.65f))
                    ) {
                        Text(
                            text = if (isServerRunning) {
                                "${if (bindLanMode) "Wi-Fi" else "Local"}:$port"
                            } else {
                                "STOPPED"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = statusDotColor,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PocketBottomNavigationBar(
    selectedTab: PocketTab,
    onSelectTab: (PocketTab) -> Unit
) {
    Surface(
        color = TerminalSlate,
        border = BorderStroke(1.dp, TerminalBorder),
        tonalElevation = 6.dp
    ) {
        NavigationBar(
            containerColor = TerminalSlate,
            tonalElevation = 0.dp
        ) {
            NavigationBarItem(
                selected = selectedTab == PocketTab.CONTROL_DECK,
                onClick = { onSelectTab(PocketTab.CONTROL_DECK) },
                icon = { Icon(Icons.Default.Dns, contentDescription = "Control Deck") },
                label = { Text(stringResource(R.string.tab_control_deck)) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = PhosphorEmerald,
                    selectedTextColor = PhosphorEmerald,
                    indicatorColor = PhosphorEmerald.copy(alpha = 0.18f)
                ),
                modifier = Modifier.testTag("nav_tab_control_deck")
            )
            NavigationBarItem(
                selected = selectedTab == PocketTab.MODEL_TUNER,
                onClick = { onSelectTab(PocketTab.MODEL_TUNER) },
                icon = { Icon(Icons.Default.DeveloperBoard, contentDescription = "Model & Tuner") },
                label = { Text(stringResource(R.string.tab_model_tuner)) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = CyberCyan,
                    selectedTextColor = CyberCyan,
                    indicatorColor = CyberCyan.copy(alpha = 0.18f)
                ),
                modifier = Modifier.testTag("nav_tab_model_tuner")
            )
            NavigationBarItem(
                selected = selectedTab == PocketTab.API_SANDBOX,
                onClick = { onSelectTab(PocketTab.API_SANDBOX) },
                icon = { Icon(Icons.Default.Code, contentDescription = "API & GBNF") },
                label = { Text(stringResource(R.string.tab_sandbox)) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = PhosphorEmerald,
                    selectedTextColor = PhosphorEmerald,
                    indicatorColor = PhosphorEmerald.copy(alpha = 0.18f)
                ),
                modifier = Modifier.testTag("nav_tab_sandbox")
            )
            NavigationBarItem(
                selected = selectedTab == PocketTab.TELEMETRY,
                onClick = { onSelectTab(PocketTab.TELEMETRY) },
                icon = { Icon(Icons.Default.Terminal, contentDescription = "Telemetry") },
                label = { Text(stringResource(R.string.tab_telemetry)) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = CyberCyan,
                    selectedTextColor = CyberCyan,
                    indicatorColor = CyberCyan.copy(alpha = 0.18f)
                ),
                modifier = Modifier.testTag("nav_tab_telemetry")
            )
        }
    }
}

@Composable
private fun PocketSideNavigationRail(
    selectedTab: PocketTab,
    onSelectTab: (PocketTab) -> Unit
) {
    NavigationRail(
        containerColor = TerminalSlate
    ) {
        NavigationRailItem(
            selected = selectedTab == PocketTab.CONTROL_DECK,
            onClick = { onSelectTab(PocketTab.CONTROL_DECK) },
            icon = { Icon(Icons.Default.Dns, contentDescription = "Control Deck") },
            label = { Text(stringResource(R.string.tab_control_deck)) },
            colors = NavigationRailItemDefaults.colors(
                selectedIconColor = PhosphorEmerald,
                selectedTextColor = PhosphorEmerald,
                indicatorColor = PhosphorEmerald.copy(alpha = 0.18f)
            ),
            modifier = Modifier.testTag("rail_tab_control_deck")
        )
        NavigationRailItem(
            selected = selectedTab == PocketTab.MODEL_TUNER,
            onClick = { onSelectTab(PocketTab.MODEL_TUNER) },
            icon = { Icon(Icons.Default.DeveloperBoard, contentDescription = "Model & Tuner") },
            label = { Text(stringResource(R.string.tab_model_tuner)) },
            colors = NavigationRailItemDefaults.colors(
                selectedIconColor = CyberCyan,
                selectedTextColor = CyberCyan,
                indicatorColor = CyberCyan.copy(alpha = 0.18f)
            ),
            modifier = Modifier.testTag("rail_tab_model_tuner")
        )
        NavigationRailItem(
            selected = selectedTab == PocketTab.API_SANDBOX,
            onClick = { onSelectTab(PocketTab.API_SANDBOX) },
            icon = { Icon(Icons.Default.Code, contentDescription = "API & GBNF") },
            label = { Text(stringResource(R.string.tab_sandbox)) },
            colors = NavigationRailItemDefaults.colors(
                selectedIconColor = PhosphorEmerald,
                selectedTextColor = PhosphorEmerald,
                indicatorColor = PhosphorEmerald.copy(alpha = 0.18f)
            ),
            modifier = Modifier.testTag("rail_tab_sandbox")
        )
        NavigationRailItem(
            selected = selectedTab == PocketTab.TELEMETRY,
            onClick = { onSelectTab(PocketTab.TELEMETRY) },
            icon = { Icon(Icons.Default.Terminal, contentDescription = "Telemetry") },
            label = { Text(stringResource(R.string.tab_telemetry)) },
            colors = NavigationRailItemDefaults.colors(
                selectedIconColor = CyberCyan,
                selectedTextColor = CyberCyan,
                indicatorColor = CyberCyan.copy(alpha = 0.18f)
            ),
            modifier = Modifier.testTag("rail_tab_telemetry")
        )
    }
}
