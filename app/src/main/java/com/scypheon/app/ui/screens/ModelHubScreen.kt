package com.scypheon.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.scypheon.app.ui.MainViewModel
import com.scypheon.sdk.core.provision.ModelHubSource
import com.scypheon.sdk.core.provision.ModelMetadata
import androidx.compose.ui.input.pointer.pointerInput

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelHubScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var tokenInput by remember { mutableStateOf(uiState.hfToken) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(Color.White)) {
                CenterAlignedTopAppBar(
                    title = { 
                        Text(
                            "Scypheon Model Hub", 
                            style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1F1F1F))
                        ) 
                    },
                    navigationIcon = {
                        IconButton(onClick = { 
                            scope.launch { 
                                delay(50)
                                onBack() 
                            }
                        }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color(0xFF5F6368))
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.White
                    )
                )
                
                // Status Bar (X models available)
                Surface(
                    color = Color(0xFFE8F0FE),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "${ModelHubSource.recommendedModels.size} models available",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        textAlign = TextAlign.Center,
                        style = TextStyle(fontSize = 13.sp, color = Color(0xFF1F1F1F), fontWeight = FontWeight.Medium)
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(Color(0xFFF8F9FA))
        ) {
            // Secure Vault Section for HF Token
            Card(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("Secure Vault: Hugging Face Token", fontWeight = FontWeight.Bold)
                    }
                    Text(
                        "Required for gated models (Gemma 4/3n). Your token is encrypted via AegisVault.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = tokenInput,
                        onValueChange = { tokenInput = it },
                        placeholder = { Text("hf_...") },
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { 
                                scope.launch { 
                                    delay(50)
                                    viewModel.saveHfToken(tokenInput) 
                                }
                            }) {
                                Icon(Icons.Default.Check, contentDescription = "Save Token")
                            }
                        },
                        singleLine = true
                    )
                }
            }

            // Hardware Acceleration Mode Section
            Card(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Memory, contentDescription = null, tint = Color(0xFF0A56D1), modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Hardware Acceleration", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        BackendChip("Auto", uiState.config.selectedBackendMode == 0) { 
                           scope.launch { delay(50); viewModel.setBackendMode(0) } 
                        }
                        BackendChip("GPU", uiState.config.selectedBackendMode == 2) { 
                           scope.launch { delay(50); viewModel.setBackendMode(2) } 
                        }
                        BackendChip("CPU", uiState.config.selectedBackendMode == 1) { 
                           scope.launch { delay(50); viewModel.setBackendMode(1) } 
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = when(uiState.config.selectedBackendMode) {
                            0 -> "Smart fallback: Try GPU first, use CPU if unstable."
                            1 -> "Force CPU: Energy efficient, avoids GPU overheating."
                            2 -> "Force GPU: Maximum performance using Vulkan."
                            else -> ""
                        },
                        fontSize = 11.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }

            Text(
                "Recommended models",
                modifier = Modifier.padding(start = 20.dp, top = 24.dp, bottom = 12.dp),
                style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1F1F1F))
            )

            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(
                    items = ModelHubSource.recommendedModels,
                    key = { it.id },
                    contentType = { "ModelCard" }
                ) { model ->
                    ModelCard(
                        model = model,
                        isDownloaded = viewModel.isModelDownloaded(model.fileName),
                        isDownloading = uiState.downloadingModelId == model.id,
                        scope = scope,
                        onDownload = { viewModel.downloadModel(model) },
                        onTry = { viewModel.tryModel(model) },
                        onDelete = { viewModel.deleteModel(model.fileName) }
                    )
                }
            }
        }
    }
}

@Composable
fun ModelCard(
    model: ModelMetadata,
    isDownloaded: Boolean,
    isDownloading: Boolean,
    scope: kotlinx.coroutines.CoroutineScope,
    onDownload: () -> Unit,
    onTry: () -> Unit,
    onDelete: () -> Unit
) {
    // Crucial: use rememberSaveable to keep state during recycling
    var isExpanded by androidx.compose.runtime.saveable.rememberSaveable { 
        mutableStateOf(model.id.contains("e4b")) 
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) { /* Stability Guard: Prevent ACTION_HOVER_EXIT crash during scroll */ },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    // Badge (Best overall)
                    if (model.id.contains("e4b")) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ScypheonIcon(Icons.Default.Star, contentDescription = null, size = 16.dp, tint = Color(0xFFF9AB00))
                            Spacer(Modifier.width(4.dp))
                            Text("Best overall", fontSize = 12.sp, color = Color(0xFF5F6368))
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                    
                    Text(
                        text = model.title,
                        style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1F1F1F))
                    )
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isDownloaded) {
                        IconButton(onClick = { 
                            scope.launch { delay(50); onDelete() } 
                        }) {
                            ScypheonIcon(Icons.Default.Delete, contentDescription = "Delete", size = 20.dp, tint = Color(0xFF5F6368))
                        }
                    }
                    IconButton(onClick = { 
                        scope.launch { delay(50); isExpanded = !isExpanded } 
                    }) {
                        ScypheonIcon(
                            if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = "Expand",
                            size = 20.dp,
                            tint = Color(0xFF5F6368)
                        )
                    }
                }
            }

            if (isExpanded) {
                Spacer(Modifier.height(8.dp))
                
                // Stats & License Link
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ScypheonIcon(
                            Icons.Default.HelpOutline, 
                            contentDescription = null, 
                            size = 14.dp, 
                            tint = if (isDownloaded) Color(0xFF0A56D1) else Color(0xFF5F6368)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "${(model.sizeBytes / 1.074e+9).format(1)} GB", 
                            fontSize = 14.sp, 
                            color = Color(0xFF1F1F1F),
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ScypheonIcon(Icons.Default.OpenInNew, contentDescription = null, size = 14.dp, tint = Color(0xFF5F6368))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "Learn more and see model license", 
                            fontSize = 14.sp, 
                            color = Color(0xFF0A56D1),
                            modifier = Modifier.padding(bottom = 1.dp)
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                Text(
                    text = model.description,
                    style = TextStyle(fontSize = 14.sp, color = Color(0xFF1F1F1F), lineHeight = 20.sp)
                )

                Spacer(Modifier.height(24.dp))

                Spacer(Modifier.height(24.dp))

                Box(modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    if (isDownloading) {
                        Column {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                                color = Color(0xFF0A56D1),
                                trackColor = Color(0xFFE8F0FE)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text("Downloading Model Data...", fontSize = 12.sp, color = Color(0xFF0A56D1))
                        }
                    } else {
                        Button(
                            onClick = { 
                                scope.launch { 
                                    delay(50)
                                    if (isDownloaded) onTry() else onDownload() 
                                }
                            },
                            modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                            shape = RoundedCornerShape(24.dp),
                            colors = if (isDownloaded) ButtonDefaults.buttonColors(containerColor = Color(0xFF0A56D1))
                                     else ButtonDefaults.buttonColors(containerColor = Color(0xFFF1F3F4), contentColor = Color(0xFF1F1F1F))
                        ) {
                            ScypheonIcon(
                                if (isDownloaded) Icons.Default.ArrowForward else Icons.Default.Download,
                                contentDescription = null,
                                size = 18.dp,
                                tint = if (isDownloaded) Color.White else Color(0xFF1F1F1F)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(if (isDownloaded) "Try it" else "Download", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                // Collapsed Preview
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ScypheonIcon(Icons.Default.HelpOutline, contentDescription = null, size = 14.dp, tint = Color(0xFF5F6368))
                        Spacer(Modifier.width(4.dp))
                        Text(text = "${(model.sizeBytes / 1.074e+9).format(1)} GB", fontSize = 14.sp, color = Color(0xFF5F6368))
                    }
                    
                    Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                        if (!isDownloaded && !isDownloading) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape),
                                color = Color(0xFFF1F3F4),
                                onClick = { 
                                    scope.launch { delay(50); onDownload() } 
                                }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    ScypheonIcon(Icons.Default.Download, contentDescription = "Download", size = 18.dp, tint = Color(0xFF5F6368))
                                }
                            }
                        } else if (isDownloading) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = Color(0xFF0A56D1))
                        }
                    }
                }
            }
        }
    }
}

// Helpers
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
