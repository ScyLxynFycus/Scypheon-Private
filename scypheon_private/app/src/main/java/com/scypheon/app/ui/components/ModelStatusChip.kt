package com.scypheon.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scypheon.sdk.core.engine.DetectedModel
import com.scypheon.sdk.core.engine.EngineType
import com.scypheon.app.ui.viewmodel.ModelSelectionViewModel
import com.scypheon.app.ui.viewmodel.ModelSelectionUiState

// ─── Design tokens ────────────────────────────────────────────────────────────
private val PitchBlack  = Color(0xFF0A0A0B)
private val SurfaceDark = Color(0xFF161618)
private val PremiumBlue = Color(0xFF4285F4)
private val AccentGreen = Color(0xFF34A853)  // LiteRT — GPU/NPU (fast, green = go)
private val AccentAmber = Color(0xFFFBBC04)  // LLaMA.cpp — CPU (universal, amber)
private val Divider     = Color.White.copy(alpha = 0.06f)

// Per spec: LiteRT = FlashOn icon, LLaMA.cpp = Memory icon
private val EngineType.icon: ImageVector
    get() = when (this) {
        EngineType.LITE_RT   -> Icons.Default.FlashOn
        EngineType.LLAMA_CPP -> Icons.Default.Memory
    }

private val EngineType.color: Color
    get() = when (this) {
        EngineType.LITE_RT   -> AccentGreen
        EngineType.LLAMA_CPP -> AccentAmber
    }

private val EngineType.label: String
    get() = when (this) {
        EngineType.LITE_RT   -> "LiteRT"
        // Shows the triage priority to set user expectations.
        // After selection, the chip updates to reflect the actual winning backend
        // via sandboxEngine.hardwareStatus (read in ModelRow subtitle).
        EngineType.LLAMA_CPP -> "LLaMA.cpp"
    }

/**
 * Drop-in replacement for the static ModelPill in the TopAppBar.
 *
 * Shows the currently selected model and engine. Tapping opens a bottom sheet
 * with all detected models. Selection triggers a real engine swap via
 * [ModelSelectionViewModel.selectModel].
 *
 * Spec requirements met:
 *  ✅ "No model selected" when nothing chosen
 *  ✅ "Name – Engine" when a model is chosen
 *  ✅ Dropdown icon on the right
 *  ✅ LiteRT = green + FlashOn, LLaMA.cpp = amber + Memory
 *  ✅ Loading spinner during engine swap
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelStatusChip(
    viewModel: ModelSelectionViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // ── Chip ────────────────────────────────────────────────────────────────
    Surface(
        color = Color.White.copy(alpha = 0.05f),
        shape = CircleShape,
        modifier = Modifier
            .height(26.dp)
            .clickable(enabled = !state.isLoading) {
                viewModel.showPicker()
            },
        border = androidx.compose.foundation.BorderStroke(
            1.dp, Color.White.copy(alpha = 0.1f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (state.isLoading) {
                // Spinner during engine swap
                CircularProgressIndicator(
                    modifier = Modifier.size(10.dp),
                    strokeWidth = 1.5.dp,
                    color = PremiumBlue
                )
            } else {
                // Status dot
                val dotColor = state.selectedModel?.engine?.color ?: PremiumBlue
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
            }

            Text(
                text = state.chipLabel(),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = if (state.isLoading) 0.5f else 0.9f),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "Select model",
                modifier = Modifier.size(14.dp),
                tint = Color.White.copy(alpha = 0.4f)
            )
        }
    }

    // ── Picker Sheet ─────────────────────────────────────────────────────────
    if (state.showPicker) {
        ModelPickerSheet(
            state    = state,
            onDismiss = { viewModel.dismissPicker() },
            onSelect  = { viewModel.selectModel(it) },
            onClear   = { viewModel.clearSelection() }
        )
    }
}

private fun ModelSelectionUiState.chipLabel(): String = when {
    isLoading        -> "Loading…"
    selectedModel != null -> "${selectedModel.displayName} – ${selectedModel.engine.label}"
    else             -> "No model selected"
}

// ─────────────────────────────────────────────────────────────────────────────
// Bottom Sheet
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelPickerSheet(
    state: ModelSelectionUiState,
    onDismiss: () -> Unit,
    onSelect: (DetectedModel) -> Unit,
    onClear: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.2f)) },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(shape = CircleShape, color = PremiumBlue.copy(alpha = 0.15f), modifier = Modifier.size(40.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Memory, null, tint = PremiumBlue, modifier = Modifier.size(20.dp))
                    }
                }
                Column {
                    Text(
                        "Select AI Model",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                    val ggufCount  = state.availableModels.count { it.engine == EngineType.LLAMA_CPP }
                    val litertCount = state.availableModels.count { it.engine == EngineType.LITE_RT }
                    Text(
                        "$ggufCount GGUF · $litertCount LiteRT detected",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.4f)
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            HorizontalDivider(color = Divider)
            Spacer(Modifier.height(16.dp))

            if (state.availableModels.isEmpty()) {
                EmptyModelMessage()
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.heightIn(max = 400.dp)
                ) {
                    // Group by engine type
                    val ggufModels   = state.availableModels.filter { it.engine == EngineType.LLAMA_CPP }
                    val litertModels = state.availableModels.filter { it.engine == EngineType.LITE_RT }

                    if (litertModels.isNotEmpty()) {
                        item { EngineHeader("LiteRT (GPU/NPU)", AccentGreen, Icons.Default.FlashOn) }
                        items(litertModels, key = { it.id }) { model ->
                            ModelRow(model, isSelected = model.id == state.selectedModel?.id, onClick = { onSelect(model) })
                        }
                        if (ggufModels.isNotEmpty()) {
                            item { Spacer(Modifier.height(8.dp)) }
                        }
                    }
                    if (ggufModels.isNotEmpty()) {
                        item { EngineHeader("LLaMA.cpp (CPU/Vulkan)", AccentAmber, Icons.Default.Memory) }
                        items(ggufModels, key = { it.id }) { model ->
                            ModelRow(model, isSelected = model.id == state.selectedModel?.id, onClick = { onSelect(model) })
                        }
                    }
                }
            }

            // Clear option
            if (state.selectedModel != null) {
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = Divider)
                Spacer(Modifier.height(12.dp))
                TextButton(
                    onClick = onClear,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Clear selection",
                        color = Color.White.copy(alpha = 0.4f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun EngineHeader(label: String, color: Color, icon: ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(14.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
private fun ModelRow(
    model: DetectedModel,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bgColor     = if (isSelected) model.engine.color.copy(alpha = 0.12f) else Color.Transparent
    val borderColor = if (isSelected) model.engine.color.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.07f)

    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        color = bgColor,
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Engine icon badge
            Surface(
                shape = CircleShape,
                color = model.engine.color.copy(alpha = 0.15f),
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = model.engine.icon,
                        contentDescription = model.engine.label,
                        tint = model.engine.color,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = model.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = model.engine.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = model.engine.color,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "·",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.3f)
                    )
                    Text(
                        text = "${model.sizeMb} MB",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.4f)
                    )
                }
            }

            Icon(
                imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (isSelected) model.engine.color else Color.White.copy(alpha = 0.2f),
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun EmptyModelMessage() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.Memory,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.2f),
            modifier = Modifier.size(48.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "No compatible model files found.",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Add .gguf (LLaMA.cpp) or .tflite (LiteRT)\nfiles to the app's files/models directory.",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.35f),
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )
    }
}
