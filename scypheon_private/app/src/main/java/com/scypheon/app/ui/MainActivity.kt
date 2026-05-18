package com.scypheon.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import android.Manifest
import android.content.Intent
import android.provider.Settings
import android.net.Uri
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.scypheon.app.ui.screens.MainChatScreen
import com.scypheon.app.ui.screens.TelemetryDashboardScreen
import com.scypheon.app.ui.screens.GraphExplorerScreen
import com.scypheon.app.ui.screens.ModelHubScreen
import com.scypheon.app.ui.screens.LiveModeScreen
import com.scypheon.app.ui.MainViewModel
import com.scypheon.app.ui.viewmodel.GraphViewModel
import com.scypheon.app.data.models.SystemHealth
import com.scypheon.app.data.models.OomDiagnostic
import dagger.hilt.android.AndroidEntryPoint
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import timber.log.Timber
import androidx.compose.foundation.layout.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.scypheon.app.R

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private val graphViewModel: GraphViewModel by viewModels()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Handle permission results if needed
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition {
            !com.scypheon.app.startup.DatabaseReadySignal.isReady
        }
        super.onCreate(savedInstanceState)

        requestPermissions()

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val uiState by viewModel.uiState.collectAsState()

                    if (uiState.isNotificationSuppressed) {
                        BlockingNotificationDialog(onOpenSettings = {
                            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                            }
                            try {
                                startActivity(intent)
                            } catch (e: Exception) {
                                // Fallback for various OS versions
                                val fallbackIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", packageName, null)
                                }
                                startActivity(fallbackIntent)
                            }
                        })
                    } else {
                        // ─── First Launch Terms & Conditions ───
                        // [v1.5.3-SAR] CRITICAL FIX: Read SharedPreferences OFF the main thread.
                        // getSharedPreferences() triggers File.exists() which is a blocking disk IO
                        // call that was causing ~1883ms StrictMode DiskReadViolation, cascading into
                        // Activity destruction during model loading.
                        var termsChecked by remember { mutableStateOf(false) }
                        var showTerms by remember { mutableStateOf(false) }

                        LaunchedEffect(Unit) {
                            val accepted = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                getSharedPreferences("scypheon_private", MODE_PRIVATE)
                                    .getBoolean("terms_accepted", false)
                            }
                            showTerms = !accepted
                            termsChecked = true
                        }

                        if (termsChecked && showTerms) {
                            ScypheonTermsDialog(
                                onAccept = {
                                    kotlinx.coroutines.MainScope().launch(kotlinx.coroutines.Dispatchers.IO) {
                                        getSharedPreferences("scypheon_private", MODE_PRIVATE)
                                            .edit().putBoolean("terms_accepted", true).apply()
                                    }
                                    showTerms = false
                                },
                                onDecline = { finish() }
                            )
                        }

                        // Waking Neural Gateway on demand only - removed startup initializeEngines

                    if (uiState.isTelemetryDashboardVisible) {
                        TelemetryDashboardScreen(
                            logs = uiState.telemetryLogs,
                            onBack = { viewModel.hideTelemetryDashboard() }
                        )
                    } else if (uiState.isGraphExplorerVisible) {
                        GraphExplorerScreen(
                            viewModel = graphViewModel,
                            graphData = uiState.graphData.map { it.toRawEdge() },
                            onBack = { viewModel.hideGraphExplorer() }
                        )
                    } else if (uiState.isModelHubVisible) {
                        ModelHubScreen(
                            viewModel = viewModel,
                            onBack = { viewModel.hideModelHub() }
                        )
                    } else if (uiState.isLiveModeActive) {
                        // Full-screen immersive Live Mode
                        LiveModeScreen(
                            liveState = uiState.liveState,
                            audioLevel = uiState.liveAudioLevel,
                            transcript = uiState.liveTranscript,
                            onEndSession = { viewModel.toggleLiveMode() },
                            onOrbClick = { viewModel.onLiveOrbClick() }
                        )
                    } else {
                        MainChatScreen(
                            messages = uiState.messages,
                            isReady = uiState.isReady,
                            onSendMessage = { text, uri -> viewModel.sendMessage(text, uri) },
                            activeFeature = uiState.activeFeature,
                            onToggleFeature = { feature -> viewModel.toggleFeature(feature) },
                            onOpenTelemetry = { viewModel.showTelemetryDashboard() },
                            onOpenGraphExplorer = { viewModel.showGraphExplorer() },
                            sessionHistory = uiState.sessionHistory,
                            onNewSession = { viewModel.startNewSession() },
                            onLoadSession = { sessionId -> viewModel.loadSession(sessionId) },
                            onOpenModelHub = { viewModel.showModelHub() },
                            activeModelName = uiState.activeModelName,
                            activeEngineType = uiState.activeEngineType,
                            isLiveModeActive = uiState.isLiveModeActive,
                            onToggleLiveMode = { viewModel.toggleLiveMode() },
                            userName = uiState.userName,
                            onSaveUserName = { name -> viewModel.saveUserName(name) },
                            error = uiState.error,
                            localModels = uiState.config.localModels,
                            isLocalModelPickerVisible = uiState.config.isLocalModelPickerVisible,
                            onShowLocalModelPicker = { viewModel.showLocalModelPicker() },
                            onHideLocalModelPicker = { viewModel.hideLocalModelPicker() },
                            onSelectLocalModel = { file -> viewModel.hotswapLocalModel(file) },
                            systemHealth = uiState.systemHealth,
                            isDiagnosticVisible = uiState.isSystemHealthVisible,
                            onShowDiagnostics = { viewModel.showSystemHealth() },
                            onHideDiagnostics = { viewModel.hideSystemHealth() },
                            onDismissError = { viewModel.dismissError() },
                            systemWarning = uiState.systemWarning,
                            onDismissWarning = { viewModel.dismissSystemWarning() },
                            onConfirmWarning = { viewModel.confirmModelLoad() },
                            config = uiState.config,
                            isConfigVisible = uiState.isConfigVisible,
                            onUpdateConfig = { viewModel.updateConfig(it) },
                            onToggleConfig = { viewModel.toggleConfigDialog(it) },
                            onResetHardware = { viewModel.resetHardwareOverrides() },
                            engineState = uiState.engineState,
                            ragState = uiState.ragState,
                            diagnosticLogs = uiState.diagnosticLogs,
                            isSandboxAlive = uiState.isSandboxAlive,
                            isMemoryOptimized = uiState.isMemoryOptimized,
                            onDismissMemoryOptimization = { viewModel.dismissMemoryOptimization() },
                            memoryStabilityState = uiState.memoryStabilityState,
                            memoryWarningCooldown = uiState.memoryWarningCooldown,
                            onConfirmStabilityWarning = { viewModel.onConfirmStabilityWarning() },
                            isAiGenerating = uiState.isAiGenerating,
                            onStopGeneration = { viewModel.stopGeneration() },
                            onRetryMessage = { text, uri -> viewModel.retryMessage(text, uri) },
                            oomDiagnostic = uiState.oomDiagnostic,
                            onDismissOom = { viewModel.dismissOomDiagnostic() },
                            isMemoryInconsistent = uiState.isMemoryInconsistent,
                            onResetMemory = { viewModel.resetMemoryDatabase() },
                            onIgnoreInconsistency = { viewModel.ignoreMemoryInconsistency() }
                        )
                    }
                }
            }
        }
        }
    }

    override fun onStart() {
        super.onStart()
        
        // 🛡️ [V.I.I.P] Critical Service Promotion (SAR-1.0.4)
        // Triggered from Activity to comply with Android 12+ background start restrictions.
        lifecycleScope.launch {
            val success = viewModel.promoteToForeground()
            if (!success) {
                Timber.e(" [V.I.I.P] V.I.I.P Shield promotion failed. Engine running in DEGRADED mode.")
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.checkNotificationStatus()
    }

    private fun requestPermissions() {
        val permissionsToRequest = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.CAMERA)
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }
}

@androidx.compose.runtime.Composable
fun BlockingNotificationDialog(onOpenSettings: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = { /* Blocking */ },
        title = { 
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Icon(
                    painter = painterResource(id = R.drawable.ic_solaris_shield),
                    contentDescription = null,
                    tint = androidx.compose.material3.MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                androidx.compose.material3.Text(
                    "Solaris Shield Inactive", 
                    color = androidx.compose.material3.MaterialTheme.colorScheme.error
                )
            }
        },
        text = {
            androidx.compose.material3.Text(
                "Foreground protection is compromised. Scypheon requires active " +
                "notification visibility to prevent the OS from terminating the AI engine " +
                "process (LMK Protection).\n\nPlease re-enable notifications for this app."
            )
        },
        confirmButton = {
            androidx.compose.material3.Button(onClick = onOpenSettings) {
                androidx.compose.material3.Text("Open Settings")
            }
        }
    )
}

// ═══════════════════════════════════════════════════════════════════
// Terms & Conditions / Medical Disclaimer Dialog
// ═══════════════════════════════════════════════════════════════════

@androidx.compose.runtime.Composable
fun ScypheonTermsDialog(
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = { /* Cannot dismiss — must accept or decline */ },
        icon = {
            androidx.compose.material3.Text(
                "🛡️",
                style = androidx.compose.ui.text.TextStyle(
                    fontSize = androidx.compose.ui.unit.TextUnit(32f, androidx.compose.ui.unit.TextUnitType.Sp)
                )
            )
        },
        title = {
            androidx.compose.material3.Text(
                "Scypheon Private",
                style = androidx.compose.ui.text.TextStyle(
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    fontSize = androidx.compose.ui.unit.TextUnit(20f, androidx.compose.ui.unit.TextUnitType.Sp)
                )
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Medical Disclaimer
                androidx.compose.material3.Surface(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                    color = androidx.compose.ui.graphics.Color(0xFFFFF3CD)
                ) {
                    Row(modifier = Modifier.padding(12.dp)) {
                        androidx.compose.material3.Text("⚕️", modifier = Modifier.padding(end = 8.dp))
                        Column {
                            androidx.compose.material3.Text(
                                "Medical Disclaimer",
                                style = androidx.compose.ui.text.TextStyle(
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                    color = androidx.compose.ui.graphics.Color(0xFF664D03)
                                )
                            )
                            androidx.compose.material3.Text(
                                "This app provides AI-assisted information ONLY. It is NOT a replacement for professional medical advice, diagnosis, or treatment. In emergencies, contact local emergency services.",
                                style = androidx.compose.ui.text.TextStyle(
                                    fontSize = androidx.compose.ui.unit.TextUnit(12f, androidx.compose.ui.unit.TextUnitType.Sp),
                                    color = androidx.compose.ui.graphics.Color(0xFF664D03)
                                )
                            )
                        }
                    }
                }

                // Privacy
                androidx.compose.material3.Surface(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                    color = androidx.compose.ui.graphics.Color(0xFFE8F5E9)
                ) {
                    Row(modifier = Modifier.padding(12.dp)) {
                        androidx.compose.material3.Text("🔒", modifier = Modifier.padding(end = 8.dp))
                        Column {
                            androidx.compose.material3.Text(
                                "Privacy First",
                                style = androidx.compose.ui.text.TextStyle(
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                    color = androidx.compose.ui.graphics.Color(0xFF1B5E20)
                                )
                            )
                            androidx.compose.material3.Text(
                                "All AI inference runs 100% on your device. No data is sent to external servers. Your conversations, medical queries, and personal data never leave your phone.",
                                style = androidx.compose.ui.text.TextStyle(
                                    fontSize = androidx.compose.ui.unit.TextUnit(12f, androidx.compose.ui.unit.TextUnitType.Sp),
                                    color = androidx.compose.ui.graphics.Color(0xFF1B5E20)
                                )
                            )
                        }
                    }
                }

                // AI Limitations
                androidx.compose.material3.Surface(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                    color = androidx.compose.ui.graphics.Color(0xFFE3F2FD)
                ) {
                    Row(modifier = Modifier.padding(12.dp)) {
                        androidx.compose.material3.Text("🤖", modifier = Modifier.padding(end = 8.dp))
                        Column {
                            androidx.compose.material3.Text(
                                "AI Limitations",
                                style = androidx.compose.ui.text.TextStyle(
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                    color = androidx.compose.ui.graphics.Color(0xFF0D47A1)
                                )
                            )
                            androidx.compose.material3.Text(
                                "Scypheon uses Gemma 4 AI models which may produce inaccurate or hallucinated content. Always verify critical information independently.",
                                style = androidx.compose.ui.text.TextStyle(
                                    fontSize = androidx.compose.ui.unit.TextUnit(12f, androidx.compose.ui.unit.TextUnitType.Sp),
                                    color = androidx.compose.ui.graphics.Color(0xFF0D47A1)
                                )
                            )
                        }
                    }
                }

                // Attribution
                androidx.compose.material3.Text(
                    "Powered by Google Gemma 4 · Built for Gemma 4 Good Hackathon",
                    style = androidx.compose.ui.text.TextStyle(
                        fontSize = androidx.compose.ui.unit.TextUnit(11f, androidx.compose.ui.unit.TextUnitType.Sp),
                        color = androidx.compose.ui.graphics.Color.Gray,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.Button(
                onClick = onAccept,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = androidx.compose.ui.graphics.Color(0xFF4CAF50)
                ),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)
            ) {
                androidx.compose.material3.Text("I Understand & Accept", fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDecline) {
                androidx.compose.material3.Text("Decline", color = androidx.compose.ui.graphics.Color.Gray)
            }
        },
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)
    )
}
