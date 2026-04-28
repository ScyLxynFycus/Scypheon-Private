package com.scypheon.app.ui.viewmodel

import com.scypheon.app.data.models.GraphEdge
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

    fun start(rawEdges: List<com.scypheon.app.ui.screens.GraphEdge>) {
        simJob?.cancel()
        
        val uniqueLabels = (rawEdges.map { it.subject } + rawEdges.map { it.obj }).distinct()
        val initialNodes = uniqueLabels.map { label ->
            GraphNode(
                id = label,
                label = label,
                posX = Random.nextFloat() * 1000f,
                posY = Random.nextFloat() * 1000f,
                colorArgb = if (label.lowercase().contains("health")) 0xFFFF5252.toInt() else 0xFF00E676.toInt(),
                radius = 35f,
                category = "General"
            )
        }
        
        _layout.value = GraphLayout(initialNodes, emptyList())
        isPaused = false
        
        simJob = scope.launch(Dispatchers.Default) {
            // Working buffers for background mutation
            val workingNodes = initialNodes.map { it.copy() }.toMutableList()
            
            while (isActive) {
                if (!isPaused) {
                    // 1. Compute physics in-place on working buffer
                    workingNodes.forEach { node ->
                        node.posX += (500f - node.posX) * 0.005f
                        node.posY += (500f - node.posY) * 0.005f
                    }
                    
                    // 2. Generate pre-computed edges
                    val nextEdges = rawEdges.map { edge ->
                        val s = workingNodes.find { it.id == edge.subject }!!
                        val o = workingNodes.find { it.id == edge.obj }!!
                        GraphEdge(s.posX, s.posY, o.posX, o.posY, Random.nextFloat(), edge.predicate)
                    }
                    
                    // 3. Emit immutable snapshot for safe UI consumption
                    // Reference change triggers StateFlow collectors
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
