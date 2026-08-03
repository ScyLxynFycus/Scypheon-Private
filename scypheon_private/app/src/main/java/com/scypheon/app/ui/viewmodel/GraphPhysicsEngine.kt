package com.scypheon.app.ui.viewmodel

import com.scypheon.app.data.models.GraphEdge
import com.scypheon.app.data.models.RawGraphEdge
import com.scypheon.app.data.models.GraphLayout
import com.scypheon.app.data.models.GraphNode
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.random.Random

class GraphPhysicsEngine(
    private val scope: CoroutineScope
) {
    private val _layout = MutableStateFlow(GraphLayout())
    val layout: StateFlow<GraphLayout> = _layout
    
    private var simJob: Job? = null
    private var isPaused = true

    fun start(rawEdges: List<com.scypheon.app.data.models.RawGraphEdge>) {
        simJob?.cancel()
        
        val uniqueLabels = (rawEdges.map { it.subject } + rawEdges.map { it.obj }).distinct()
        val initialNodes = uniqueLabels.map { label ->
            GraphNode(
                id = label,
                label = label,
                posX = Random.nextFloat() * 1000f,
                posY = Random.nextFloat() * 1000f,
                colorArgb = if (label.length % 2 == 0) 0xFF007AFF.toInt() else 0xFF8E8E93.toInt(),
                radius = 35f,
                category = "General"
            )
        }
        
        _layout.value = GraphLayout(initialNodes, emptyList())
        isPaused = false
        
        simJob = scope.launch(Dispatchers.Default) {
            // Working buffers for background mutation
            val workingNodes = initialNodes.map { it.copy() }.toMutableList()
            var time = 0f
            
            while (isActive) {
                if (!isPaused) {
                    time += 0.02f
                    // 1. Position nodes in a gorgeous Fermat's Spiral constellation with breathing micro-animations
                    workingNodes.forEachIndexed { i, node ->
                        val c = 160f // Perfect spacing constant to prevent overlaps
                        val theta = i * 137.5f * (Math.PI.toFloat() / 180f)
                        val r = c * kotlin.math.sqrt(i.toFloat() + 1f)
                        
                        // Gentle premium breathing and orbital swirl animations
                        val breathingScale = 1f + 0.04f * kotlin.math.sin(time + i * 0.5f)
                        val orbitAngle = theta + 0.015f * kotlin.math.cos(time * 0.3f + i * 0.2f)
                        
                        node.posX = 500f + r * breathingScale * kotlin.math.cos(orbitAngle)
                        node.posY = 500f + r * breathingScale * kotlin.math.sin(orbitAngle)
                    }
                    
                    // 2. Generate pre-computed edges with null-safety
                    val nextEdges = rawEdges.mapNotNull { edge ->
                        val s = workingNodes.find { it.id == edge.subject }
                        val o = workingNodes.find { it.id == edge.obj }
                        if (s != null && o != null) {
                            GraphEdge(s.posX, s.posY, o.posX, o.posY, Random.nextFloat(), edge.predicate)
                        } else null
                    }
                    
                    // 3. Emit immutable snapshot for safe UI consumption
                    _layout.emit(GraphLayout(
                        nodes = workingNodes.map { it.copy() }, 
                        edges = nextEdges
                    ))
                }
                delay(33) // Fixed 30Hz Timestep
            }
        }
    }

    fun pause() { isPaused = true }
    fun resume() { isPaused = false }
    fun stop() { simJob?.cancel(); simJob = null }
}
