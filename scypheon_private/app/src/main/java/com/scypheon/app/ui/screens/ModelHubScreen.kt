package com.scypheon.app.ui.screens

import com.scypheon.sdk.core.model.ScypheonConfig

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalContext
import com.scypheon.app.ui.MainViewModel
import com.scypheon.app.ui.NO_MODEL_SELECTED
import com.scypheon.app.provision.HuggingFaceClient
import com.scypheon.sdk.core.provision.ModelHubSource
import com.scypheon.sdk.core.provision.ModelMetadata
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

// ═══════════════════════════════════════════════════════════════════
// [v1.5.2-SAR] SCYPHEON MODEL HUB — Premium Model Management
// Apple App Store-inspired cards with real download/swap/delete logic
// ═══════════════════════════════════════════════════════════════════

// Design Tokens
private val HubBg = Color(0xFFF2F2F7)
private val HubCardBg = Color.White
private val HubAccent = Color(0xFF007AFF)
private val HubGreen = Color(0xFF34C759)
private val HubRed = Color(0xFFFF3B30)
private val HubOrange = Color(0xFFFF9500)
private val HubTextPrimary = Color(0xFF1C1C1E)
private val HubTextSecondary = Color(0xFF8E8E93)
private val HubDivider = Color(0xFFE5E5EA)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelHubScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()

    // Scan local models on enter
    LaunchedEffect(Unit) {
        viewModel.showLocalModelPicker()
        delay(100)
        viewModel.hideLocalModelPicker()
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "Model Hub",
                        style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = HubTextPrimary)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { scope.launch { delay(50); onBack() } }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = HubAccent)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = HubBg
                )
            )
        },
        containerColor = HubBg
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ─── Active Model Card ───
            item {
                ActiveModelCard(
                    modelName = uiState.activeModelName,
                    engineType = uiState.activeEngineType,
                    isReady = uiState.isReady
                )
            }

            // ─── Local Models Section ───
            item {
                SectionHeader(
                    title = "On Device",
                    subtitle = "${uiState.config.localModels.size} models found"
                )
            }

            if (uiState.config.localModels.isEmpty()) {
                item {
                    EmptyLocalModelsCard()
                }
            } else {
                items(uiState.config.localModels, key = { it.absolutePath }) { file ->
                    LocalModelCard(
                        file = file,
                        isActive = uiState.activeModelName.contains(file.nameWithoutExtension, ignoreCase = true),
                        onLoad = {
                            scope.launch {
                                delay(50)
                                viewModel.hotswapLocalModel(file)
                            }
                        }
                    )
                }
            }

            // ─── Recommended Downloads Section ───
            item {
                Spacer(Modifier.height(8.dp))
                SectionHeader(
                    title = "Recommended",
                    subtitle = "Curated for mobile"
                )
            }

            items(
                items = ModelHubSource.recommendedModels,
                key = { it.id }
            ) { model ->
                val isDownloaded = viewModel.isModelDownloaded(model.fileName)
                val isDownloadingActive = uiState.downloadingModelId == model.id
                val isPaused = (uiState.isDownloadPaused && isDownloadingActive) ||
                               (!isDownloaded && !isDownloadingActive && viewModel.isModelDownloadingOrPaused(model.fileName))
                
                val customProgress = viewModel.getCustomDownloadProgress(model.fileName)
                val progressPercent = if (isDownloadingActive) uiState.downloadProgress else (customProgress?.percentage ?: 0f)

                RecommendedModelCard(
                    model = model,
                    isDownloaded = isDownloaded,
                    isDownloading = isDownloadingActive,
                    isPaused = isPaused,
                    downloadProgress = progressPercent,
                    scope = scope,
                    onDownload = { viewModel.downloadModel(model) },
                    onPause = { viewModel.pauseModelDownload(model) },
                    onTry = { viewModel.tryModel(model) },
                    onDelete = { viewModel.deleteModel(model.fileName) }
                )
            }

            // ─── HuggingFace Live Search Section ───
            item {
                Spacer(Modifier.height(8.dp))
                SectionHeader(
                    title = "Browse HuggingFace",
                    subtitle = "Search public models"
                )
            }

            item {
                HfSearchBar(
                    query = uiState.hfSearchQuery,
                    isLoading = uiState.hfSearchLoading,
                    onSearch = { viewModel.searchHuggingFace(it) },
                    onClear = { viewModel.clearHfSearch() }
                )
            }

            // Search results or file browser
            if (uiState.hfSelectedRepo != null) {
                // ─── File Browser for selected repo ───
                item {
                    HfRepoBrowser(
                        repoId = uiState.hfSelectedRepo!!,
                        files = uiState.hfRepoFiles,
                        detail = uiState.hfRepoDetail,
                        isLoading = uiState.hfFilesLoading,
                        isDownloading = uiState.downloadingModelId != null,
                        isPaused = uiState.isDownloadPaused,
                        downloadProgress = uiState.downloadProgress,
                        onBack = { viewModel.clearHfSelection() },
                        onDownloadFile = { file ->
                            viewModel.requestDownloadHfFile(file, uiState.hfSelectedRepo!!)
                        },
                        onPauseDownload = {
                            // Find the current downloading model metadata to pause it
                            val downloadingId = uiState.downloadingModelId
                            if (downloadingId != null) {
                                val model = ModelHubSource.recommendedModels.find { it.id == downloadingId }
                                if (model != null) {
                                    viewModel.pauseModelDownload(model)
                                } else {
                                    // Handle HF models (simplified: use currentDownloadId if available)
                                    // Actually, we can just call a generic pause in ViewModel
                                    viewModel.pauseCurrentDownload()
                                }
                            }
                        },
                        onCancelDownload = {
                            viewModel.cancelCurrentDownload()
                        }
                    )
                }
            } else if (uiState.hfSearchResults.isNotEmpty()) {
                items(uiState.hfSearchResults, key = { it.repoId }) { model ->
                    HfSearchResultCard(
                        model = model,
                        onClick = { viewModel.selectHfRepo(model.repoId) }
                    )
                }
            }

            // ─── Footer ───
            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Models are stored locally on your device.\nNo data is sent to external servers during inference.",
                    style = TextStyle(
                        fontSize = 12.sp,
                        color = HubTextSecondary,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp, vertical = 16.dp)
                )
            }
        }

        // ─── License Confirmation Dialog ───
        if (uiState.pendingDownloadFile != null) {
            LicenseConfirmationDialog(
                fileName = uiState.pendingDownloadFile!!.fileName,
                fileSize = uiState.pendingDownloadFile!!.displaySize,
                repoId = uiState.pendingDownloadRepoId ?: "",
                license = uiState.pendingDownloadLicense ?: "See model card",
                licenseUrl = uiState.pendingDownloadLicenseUrl ?: "",
                onConfirm = { viewModel.confirmHfDownload() },
                onDismiss = { viewModel.cancelHfDownload() }
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// Active Model Card — Shows what's currently loaded
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun ActiveModelCard(modelName: String, engineType: String?, isReady: Boolean) {
    val context = LocalContext.current
    var totalGb by remember { mutableStateOf(0.0) }
    var freeGb by remember { mutableStateOf(0.0) }
    var usagePercent by remember { mutableStateOf(0f) }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val stat = android.os.StatFs(android.os.Environment.getDataDirectory().path)
                val blockSize = stat.blockSizeLong
                val totalBytes = stat.blockCountLong * blockSize
                val availableBytes = stat.availableBlocksLong * blockSize
                val usedBytes = totalBytes - availableBytes
                totalGb = totalBytes / 1_000_000_000.0
                freeGb = availableBytes / 1_000_000_000.0
                usagePercent = if (totalBytes > 0) (usedBytes.toFloat() / totalBytes) else 0f
            } catch (e: Exception) {
                // Fallback
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0xFFE5E5EA)),
        colors = CardDefaults.cardColors(containerColor = HubCardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: Active Model Info
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Status dot indicator
                val dotColor = when {
                    modelName == NO_MODEL_SELECTED || modelName.isBlank() -> Color(0xFF8E8E93)
                    isReady -> HubGreen
                    else -> HubOrange
                }
                
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(dotColor, CircleShape)
                )
                
                Spacer(Modifier.width(8.dp))
                
                Text(
                    text = when {
                        modelName == NO_MODEL_SELECTED || modelName.isBlank() -> "NO ACTIVE MODEL"
                        isReady -> "SYSTEM ACTIVE"
                        else -> "LOADING NEURAL LINK..."
                    },
                    style = TextStyle(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = dotColor,
                        letterSpacing = 0.5.sp
                    )
                )
            }
            
            Spacer(Modifier.height(6.dp))
            
            Text(
                text = if (modelName == NO_MODEL_SELECTED || modelName.isBlank()) "Model Missing" else modelName,
                style = TextStyle(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = HubTextPrimary
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            if (engineType != null && modelName != NO_MODEL_SELECTED && modelName.isNotBlank()) {
                Text(
                    text = "Engine: $engineType Backend",
                    style = TextStyle(fontSize = 12.sp, color = HubTextSecondary),
                    modifier = Modifier.padding(top = 1.dp)
                )
            }
            
            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = HubDivider, thickness = 1.dp)
            Spacer(Modifier.height(14.dp))
            
            // Storage Section
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Storage,
                        contentDescription = null,
                        tint = HubAccent,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Storage Status",
                        style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = HubTextPrimary)
                    )
                }
                Text(
                    "%.1f GB Free of %.0f GB".format(freeGb, totalGb),
                    style = TextStyle(fontSize = 12.sp, color = HubTextSecondary)
                )
            }
            
            Spacer(Modifier.height(8.dp))
            
            LinearProgressIndicator(
                progress = { usagePercent },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = HubAccent,
                trackColor = HubBg
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// Section Header
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            title,
            style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, color = HubTextPrimary)
        )
        Text(
            subtitle,
            style = TextStyle(fontSize = 13.sp, color = HubTextSecondary)
        )
    }
}

// ═══════════════════════════════════════════════════════════════════
// Local Model Card — Models already on device
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun LocalModelCard(file: java.io.File, isActive: Boolean, onLoad: () -> Unit) {
    val sizeMb = file.length() / (1024.0 * 1024.0)
    val sizeText = if (sizeMb > 1024) "%.1f GB".format(sizeMb / 1024.0) else "%.0f MB".format(sizeMb)
    val engineType = when {
        file.name.endsWith(".task") || file.name.endsWith(".litertlm") -> "LiteRT"
        file.name.endsWith(".gguf") -> "Llama"
        else -> "Unknown"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) HubAccent.copy(alpha = 0.04f) else HubCardBg
        ),
        border = BorderStroke(1.dp, if (isActive) HubAccent.copy(alpha = 0.4f) else Color(0xFFE5E5EA)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        if (isActive) HubAccent.copy(alpha = 0.12f) else HubBg,
                        RoundedCornerShape(10.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (engineType == "LiteRT") Icons.Default.Bolt else Icons.Default.Memory,
                    contentDescription = null,
                    tint = if (isActive) HubAccent else HubTextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    file.nameWithoutExtension,
                    style = TextStyle(fontSize = 15.sp, color = HubTextPrimary, fontWeight = FontWeight.Medium),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(sizeText, style = TextStyle(fontSize = 12.sp, color = HubTextSecondary))
                    Text("·", style = TextStyle(fontSize = 12.sp, color = HubTextSecondary))
                    Text(engineType, style = TextStyle(fontSize = 12.sp, color = HubTextSecondary))
                }
            }

            if (isActive) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = HubAccent.copy(alpha = 0.12f)
                ) {
                    Text(
                        "Active",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = HubAccent)
                    )
                }
            } else {
                FilledTonalButton(
                    onClick = onLoad,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = HubAccent.copy(alpha = 0.1f),
                        contentColor = HubAccent
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text("Load", style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold))
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// Recommended Model Card — Downloadable from cloud
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun RecommendedModelCard(
    model: ModelMetadata,
    isDownloaded: Boolean,
    isDownloading: Boolean,
    isPaused: Boolean,
    downloadProgress: Float,
    scope: kotlinx.coroutines.CoroutineScope,
    onDownload: () -> Unit,
    onPause: () -> Unit,
    onTry: () -> Unit,
    onDelete: () -> Unit
) {
    val sizeGb = model.sizeBytes / 1_000_000_000.0

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0xFFE5E5EA)),
        colors = CardDefaults.cardColors(containerColor = HubCardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Quantized Box
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF007AFF).copy(alpha = 0.08f), Color(0xFF5856D6).copy(alpha = 0.08f))
                            ),
                            RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        model.quantization.uppercase(),
                        style = TextStyle(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF007AFF)
                        )
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        model.title,
                        style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = HubTextPrimary),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "%.1f GB".format(sizeGb),
                            style = TextStyle(fontSize = 12.sp, color = HubTextSecondary)
                        )
                        Text("·", style = TextStyle(fontSize = 12.sp, color = HubTextSecondary))
                        Text(
                            if (model.engineType == com.scypheon.sdk.core.provision.EngineType.LITE_RT) "LiteRT" else "Llama",
                            style = TextStyle(fontSize = 12.sp, color = HubTextSecondary)
                        )
                    }
                }

                // Best Badge
                if (model.id.contains("e4b") && model.quantization == "int8") {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFFFFF3CD),
                        border = BorderStroke(0.5.dp, Color(0xFFFFC107).copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(Icons.Default.Star, null, tint = Color(0xFFFFB300), modifier = Modifier.size(10.dp))
                            Text("Best Choice", style = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF856404)))
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Description
            Text(
                model.description,
                style = TextStyle(fontSize = 13.sp, color = HubTextSecondary, lineHeight = 18.sp),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(12.dp))

            // Provider & RAM Requirements
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Provider tag
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFFF2F2F7)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.Person, null, tint = HubTextSecondary, modifier = Modifier.size(12.dp))
                        Text(model.provider, style = TextStyle(fontSize = 11.sp, color = HubTextSecondary, fontWeight = FontWeight.Medium))
                    }
                }

                // RAM tag
                if (model.ramRequired.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFFF2F2F7)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.Memory, null, tint = HubTextSecondary, modifier = Modifier.size(12.dp))
                            Text(model.ramRequired, style = TextStyle(fontSize = 11.sp, color = HubTextSecondary, fontWeight = FontWeight.Medium))
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Action area
            if (isDownloading) {
                // Downloading progress bar & pause/cancel actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        LinearProgressIndicator(
                            progress = { downloadProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = HubAccent,
                            trackColor = HubBg
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Downloading... ${(downloadProgress * 100).toInt()}%",
                            style = TextStyle(fontSize = 11.sp, color = HubAccent, fontWeight = FontWeight.Bold)
                        )
                    }

                    // Pause Button
                    IconButton(
                        onClick = onPause,
                        modifier = Modifier
                            .size(32.dp)
                            .background(Color(0xFFF2F2F7), CircleShape)
                    ) {
                        Icon(Icons.Default.Pause, contentDescription = "Pause", tint = HubAccent, modifier = Modifier.size(16.dp))
                    }

                    // Cancel Button
                    IconButton(
                        onClick = { scope.launch { delay(50); onDelete() } },
                        modifier = Modifier
                            .size(32.dp)
                            .background(HubRed.copy(alpha = 0.08f), CircleShape)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel", tint = HubRed, modifier = Modifier.size(16.dp))
                    }
                }
            } else if (isPaused) {
                // Paused / Resumable state
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        LinearProgressIndicator(
                            progress = { downloadProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = HubOrange,
                            trackColor = HubBg
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Paused at ${(downloadProgress * 100).toInt()}%",
                            style = TextStyle(fontSize = 11.sp, color = HubOrange, fontWeight = FontWeight.Bold)
                        )
                    }

                    // Resume Button
                    IconButton(
                        onClick = onDownload,
                        modifier = Modifier
                            .size(32.dp)
                            .background(HubOrange.copy(alpha = 0.08f), CircleShape)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Resume", tint = HubOrange, modifier = Modifier.size(16.dp))
                    }

                    // Clear / Delete Button
                    IconButton(
                        onClick = { scope.launch { delay(50); onDelete() } },
                        modifier = Modifier
                            .size(32.dp)
                            .background(HubRed.copy(alpha = 0.08f), CircleShape)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Delete", tint = HubRed, modifier = Modifier.size(16.dp))
                    }
                }
            } else if (isDownloaded) {
                // Downloaded state - show Load Model & Delete
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { scope.launch { delay(50); onTry() } },
                        modifier = Modifier.weight(1f).height(42.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = HubAccent)
                    ) {
                        Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Load Model", style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White))
                    }

                    OutlinedButton(
                        onClick = { scope.launch { delay(50); onDelete() } },
                        modifier = Modifier.height(42.dp),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, HubRed.copy(alpha = 0.3f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = HubRed)
                    ) {
                        Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp))
                    }
                }
            } else {
                // Not downloaded state - show Download outline button
                OutlinedButton(
                    onClick = { scope.launch { delay(50); onDownload() } },
                    modifier = Modifier.fillMaxWidth().height(42.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, HubAccent.copy(alpha = 0.4f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = HubAccent)
                ) {
                    Icon(Icons.Default.Download, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Download (%.1f GB)".format(sizeGb), style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold))
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// Empty State
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun EmptyLocalModelsCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = HubCardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.FolderOpen,
                contentDescription = null,
                tint = HubTextSecondary,
                modifier = Modifier.size(32.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "No local models found",
                style = TextStyle(fontSize = 14.sp, color = HubTextSecondary, fontWeight = FontWeight.Medium)
            )
            Text(
                "Place .gguf, .task, or .litertlm files in the app's Download folder",
                style = TextStyle(fontSize = 12.sp, color = HubTextSecondary, textAlign = TextAlign.Center),
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// HuggingFace Search Bar
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun HfSearchBar(
    query: String,
    isLoading: Boolean,
    onSearch: (String) -> Unit,
    onClear: () -> Unit
) {
    var text by remember { mutableStateOf(query) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = HubCardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("Search models (e.g. gemma gguf)", fontSize = 14.sp) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = TextStyle(fontSize = 14.sp),
                leadingIcon = {
                    Icon(Icons.Default.Search, null, tint = HubTextSecondary, modifier = Modifier.size(20.dp))
                },
                trailingIcon = {
                    if (text.isNotBlank()) {
                        IconButton(onClick = { text = ""; onClear() }) {
                            Icon(Icons.Default.Close, null, tint = HubTextSecondary, modifier = Modifier.size(18.dp))
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = HubAccent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent
                ),
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(
                onClick = { if (text.isNotBlank()) onSearch(text) },
                enabled = text.isNotBlank() && !isLoading,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = HubAccent,
                    contentColor = Color.White
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                } else {
                    Text("Search", style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold))
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// HuggingFace Search Result Card
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun HfSearchResultCard(
    model: HuggingFaceClient.HfModelInfo,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = HubCardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Repo name
            Text(
                model.displayName,
                style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = HubTextPrimary),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                model.author,
                style = TextStyle(fontSize = 12.sp, color = HubAccent)
            )

            Spacer(Modifier.height(8.dp))

            // Stats row
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Downloads
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Icon(Icons.Default.Download, null, tint = HubTextSecondary, modifier = Modifier.size(14.dp))
                    Text(
                        formatCount(model.downloads),
                        style = TextStyle(fontSize = 12.sp, color = HubTextSecondary)
                    )
                }
                // Likes
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Icon(Icons.Default.Favorite, null, tint = HubTextSecondary, modifier = Modifier.size(14.dp))
                    Text(
                        formatCount(model.likes),
                        style = TextStyle(fontSize = 12.sp, color = HubTextSecondary)
                    )
                }
                // License
                if (model.license.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = HubBg
                    ) {
                        Text(
                            model.license,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = TextStyle(fontSize = 10.sp, color = HubTextSecondary, fontWeight = FontWeight.Medium)
                        )
                    }
                }

                Spacer(Modifier.weight(1f))

                Icon(Icons.Default.ChevronRight, null, tint = HubTextSecondary, modifier = Modifier.size(20.dp))
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// HuggingFace Repo File Browser
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun HfRepoBrowser(
    repoId: String,
    files: List<HuggingFaceClient.HfModelFile>,
    detail: HuggingFaceClient.HfModelDetail?,
    isLoading: Boolean,
    isDownloading: Boolean,
    isPaused: Boolean,
    downloadProgress: Float,
    onBack: () -> Unit,
    onDownloadFile: (HuggingFaceClient.HfModelFile) -> Unit,
    onPauseDownload: () -> Unit,
    onCancelDownload: () -> Unit
) {
    val uriHandler = LocalUriHandler.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = HubCardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.ArrowBack, null, tint = HubAccent, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(4.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        repoId.substringAfter("/"),
                        style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = HubTextPrimary),
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        repoId.substringBefore("/"),
                        style = TextStyle(fontSize = 12.sp, color = HubAccent)
                    )
                }
            }

            // License & info
            if (detail != null) {
                Spacer(Modifier.height(10.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(shape = RoundedCornerShape(6.dp), color = HubBg) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.Policy, null, tint = HubTextSecondary, modifier = Modifier.size(12.dp))
                            Text(detail.licenseName, style = TextStyle(fontSize = 11.sp, color = HubTextSecondary, fontWeight = FontWeight.Medium))
                        }
                    }
                    Surface(shape = RoundedCornerShape(6.dp), color = HubBg) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.Download, null, tint = HubTextSecondary, modifier = Modifier.size(12.dp))
                            Text(formatCount(detail.downloads), style = TextStyle(fontSize = 11.sp, color = HubTextSecondary))
                        }
                    }
                    // View on HF link
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = HubAccent.copy(alpha = 0.08f),
                        modifier = Modifier.clickable { uriHandler.openUri(detail.modelCardUrl) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.OpenInNew, null, tint = HubAccent, modifier = Modifier.size(12.dp))
                            Text("Model Card", style = TextStyle(fontSize = 11.sp, color = HubAccent, fontWeight = FontWeight.Medium))
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = HubDivider)
            Spacer(Modifier.height(10.dp))

            // Files header
            Text(
                "Available Files (${files.size})",
                style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = HubTextPrimary)
            )
            Spacer(Modifier.height(8.dp))

            if (isLoading) {
                Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = HubAccent)
                }
            } else if (files.isEmpty()) {
                Text(
                    "No mobile-compatible files found in this repo.",
                    style = TextStyle(fontSize = 13.sp, color = HubTextSecondary),
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                files.forEach { file ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Quant badge
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF667EEA).copy(alpha = 0.1f)
                        ) {
                            Text(
                                file.quantization,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF667EEA))
                            )
                        }

                        Spacer(Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                file.fileName,
                                style = TextStyle(fontSize = 13.sp, color = HubTextPrimary),
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                file.displaySize,
                                style = TextStyle(fontSize = 11.sp, color = HubTextSecondary)
                            )
                        }

                        // Download button
                        FilledTonalButton(
                            onClick = { onDownloadFile(file) },
                            enabled = !isDownloading,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = HubAccent.copy(alpha = 0.1f),
                                contentColor = HubAccent
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(Icons.Default.Download, null, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }

            // Download progress
            if (isDownloading) {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        LinearProgressIndicator(
                            progress = { downloadProgress },
                            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                            color = if (isPaused) HubOrange else HubAccent,
                            trackColor = (if (isPaused) HubOrange else HubAccent).copy(alpha = 0.12f)
                        )
                        Text(
                            if (isPaused) "Paused at ${(downloadProgress * 100).toInt()}%" else "Downloading... ${(downloadProgress * 100).toInt()}%",
                            style = TextStyle(fontSize = 11.sp, color = if (isPaused) HubOrange else HubAccent, fontWeight = FontWeight.Medium),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    // Pause/Resume Button
                    IconButton(
                        onClick = { if (isPaused) {
                            // Resume logic (simplified) - will call confirmHfDownload which triggers downloadModel again
                            // For HF, we need the metadata which we might not have here.
                            // But downloadModel(model) handles resumption if file exists.
                            onPauseDownload() // This will actually act as toggle
                        } else onPauseDownload() },
                        modifier = Modifier
                            .size(32.dp)
                            .background(HubBg, CircleShape)
                    ) {
                        Icon(
                            if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = if (isPaused) "Resume" else "Pause",
                            tint = if (isPaused) HubOrange else HubAccent,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Cancel Button
                    IconButton(
                        onClick = onCancelDownload,
                        modifier = Modifier
                            .size(32.dp)
                            .background(HubRed.copy(alpha = 0.1f), CircleShape)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Cancel Download",
                            tint = HubRed,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// License Confirmation Dialog
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun LicenseConfirmationDialog(
    fileName: String,
    fileSize: String,
    repoId: String,
    license: String,
    licenseUrl: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val uriHandler = LocalUriHandler.current

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Policy, null, tint = HubAccent) },
        title = {
            Text(
                "License Agreement",
                style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 18.sp)
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "You are about to download:",
                    style = TextStyle(fontSize = 14.sp, color = HubTextSecondary)
                )

                // File info
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = HubBg,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(fileName, style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = HubTextPrimary))
                        Text("Source: $repoId", style = TextStyle(fontSize = 12.sp, color = HubTextSecondary))
                        Text("Size: $fileSize", style = TextStyle(fontSize = 12.sp, color = HubTextSecondary))
                    }
                }

                // License
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFFFF3CD),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, null, tint = Color(0xFFB8860B), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                "License: $license",
                                style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF664D03))
                            )
                            Text(
                                "By downloading, you agree to this model's license terms.",
                                style = TextStyle(fontSize = 11.sp, color = Color(0xFF664D03))
                            )
                        }
                    }
                }

                // View license link
                if (licenseUrl.isNotBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { uriHandler.openUri(licenseUrl) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.OpenInNew, null, tint = HubAccent, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Read full license terms",
                            style = TextStyle(fontSize = 13.sp, color = HubAccent, fontWeight = FontWeight.Medium)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = HubAccent),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Accept & Download", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = HubTextSecondary)
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}

// ═══════════════════════════════════════════════════════════════════
// Helpers
// ═══════════════════════════════════════════════════════════════════

private fun formatCount(count: Int): String {
    return when {
        count >= 1_000_000 -> "%.1fM".format(count / 1_000_000.0)
        count >= 1_000 -> "%.1fK".format(count / 1_000.0)
        else -> count.toString()
    }
}

// Legacy helpers kept for compat
fun Double.format(digits: Int) = "%.${digits}f".format(this)

@Composable
fun BackendChip(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = if (isSelected) Color(0xFFE8F0FE) else Color(0xFFF1F3F4),
        border = if (isSelected) BorderStroke(1.dp, Color(0xFF0A56D1)) else null
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = TextStyle(
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) Color(0xFF0A56D1) else Color(0xFF5F6368)
            )
        )
    }
}

@Composable
fun ScypheonIcon(imageVector: androidx.compose.ui.graphics.vector.ImageVector, contentDescription: String?, size: androidx.compose.ui.unit.Dp, tint: Color) {
    androidx.compose.material3.Icon(imageVector, contentDescription, modifier = Modifier.size(size), tint = tint)
}
