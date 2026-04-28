package com.scypheon.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import dagger.hilt.android.AndroidEntryPoint
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import timber.log.Timber

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Handle permission results if needed
    }

    override fun onCreate(savedInstanceState: Bundle?) {
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
                        LaunchedEffect(Unit) {
                        viewModel.initializeEngines()
                    }

                    if (uiState.isTelemetryDashboardVisible) {
                        TelemetryDashboardScreen(
                            logs = uiState.telemetryLogs,
                            onBack = { viewModel.hideTelemetryDashboard() }
                        )
                    } else if (uiState.isGraphExplorerVisible) {
                        GraphExplorerScreen(
                            graphData = uiState.graphData,
                            onBack = { viewModel.hideGraphExplorer() }
                        )
                    } else if (uiState.isModelHubVisible) {
                        ModelHubScreen(
                            viewModel = viewModel,
                            onBack = { viewModel.hideModelHub() }
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
                            onDismissOom = { viewModel.dismissOomDiagnostic() }
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
        title = { androidx.compose.material3.Text("🏛️ Solaris Shield Inactive", color = androidx.compose.material3.MaterialTheme.colorScheme.error) },
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
