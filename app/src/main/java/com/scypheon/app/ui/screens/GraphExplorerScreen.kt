package com.scypheon.app.ui.screens

import android.os.Build
import androidx.compose.animation.*
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.scypheon.app.data.models.GraphNode
import com.scypheon.app.ui.viewmodel.GraphViewModel
import com.scypheon.app.ui.views.NeuralGraphView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GraphExplorerScreen(
    viewModel: GraphViewModel,
    graphData: List<com.scypheon.app.data.models.RawGraphEdge>,
    onBack: () -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var selectedNode by remember { mutableStateOf<GraphNode?>(null) }
    
    // Viewport State
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(graphData) {
        viewModel.initGraph(graphData)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("NEURAL VAULT", style = MaterialTheme.typography.labelLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = Color.Black
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(0.2f, 5f)
                        offset += pan
                    }
                }
        ) {
            
            // [HARDENING] Production-Grade AndroidView Integration
            AndroidView(
                factory = { ctx ->
                    NeuralGraphView(ctx).apply {
                        setLifecycle(lifecycleOwner.lifecycle)
                        setOnNodeClickListener { node ->
                            selectedNode = node
                        }
                        // Bind physics ONCE in factory
                        bindPhysics(viewModel.physics.layout, scope)
                    }
                },
                update = { view ->
                    // Update matrix on every pan/zoom
                    view.updateTransform(scale, offset.x, offset.y)
                },
                onRelease = { view ->
                    // [CRITICAL] Explicit cleanup on View detach/recompose
                    view.unbindPhysics()
                },
                modifier = Modifier.fillMaxSize()
            )

            AnimatedVisibility(
                visible = selectedNode != null,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it },
                modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp)
            ) {
                selectedNode?.let { node ->
                    NeuralInfoCard(node = node)
                }
            }
        }
    }
}

@Composable
fun NeuralInfoCard(node: GraphNode) {
    val isBlurSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(130.dp),
        color = Color.Black.copy(alpha = 0.7f),
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 8.dp
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(node.label.uppercase(), fontWeight = FontWeight.Black, color = Color.White, fontSize = 18.sp)
            Text("Knowledge Entity | Trust: Verified", color = Color(0xFF00E676), fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            Text("Neural mapping synchronized with the Sentient Core.", color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp)
        }
    }
}

