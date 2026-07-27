package com.scypheon.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.scypheon.app.data.models.RawGraphEdge
import com.scypheon.app.data.models.GraphNode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import androidx.lifecycle.viewModelScope

@HiltViewModel
class GraphViewModel @Inject constructor(
    application: Application
) : AndroidViewModel(application) {

    val engine = BarnesHutEngine()
    
enum class VaultViewMode { GRAPH, KNOWLEDGE }

    // We keep a list of nodes to display text labels on top in Compose
    private val _nodesState = MutableStateFlow<List<GraphNode>>(emptyList())
    val nodesState: StateFlow<List<GraphNode>> = _nodesState

    private val _currentView = MutableStateFlow(VaultViewMode.GRAPH)
    val currentView: StateFlow<VaultViewMode> = _currentView

    fun setViewMode(mode: VaultViewMode) {
        _currentView.value = mode
    }

    fun initGraph(data: List<RawGraphEdge>) {
        viewModelScope.launch {
            val uniqueLabels = (data.map { it.subject } + data.map { it.obj }).distinct()
            
            // Calculate degree centrality for each node
            val degreeMap = mutableMapOf<String, Int>()
            data.forEach { edge ->
                degreeMap[edge.subject] = (degreeMap[edge.subject] ?: 0) + 1
                degreeMap[edge.obj] = (degreeMap[edge.obj] ?: 0) + 1
            }

            // Implement Label Propagation Algorithm (LPA) to identify communities (clusters)
            val n = uniqueLabels.size
            val labelToId = uniqueLabels.mapIndexed { idx, label -> label to idx }.toMap()
            val clusters = (0 until n).toMutableList()

            // Build adjacency list for clustering
            val adj = Array(n) { mutableListOf<Int>() }
            data.forEach { edge ->
                val u = labelToId[edge.subject]
                val v = labelToId[edge.obj]
                if (u != null && v != null) {
                    adj[u].add(v)
                    adj[v].add(u)
                }
            }

            // Run label propagation for 5 iterations to group connected nodes
            repeat(5) {
                val order = (0 until n).shuffled()
                for (u in order) {
                    if (adj[u].isNotEmpty()) {
                        val neighborClusters = adj[u].map { clusters[it] }
                        val mostCommon = neighborClusters.groupBy { it }
                            .maxByOrNull { it.value.size }?.key
                        if (mostCommon != null) {
                            clusters[u] = mostCommon
                        }
                    }
                }
            }

            // Map clusters to palette indices [0..7]
            val uniqueClusters = clusters.distinct()
            val clusterMapping = uniqueClusters.mapIndexed { index, oldCluster -> oldCluster to (index % 8) }.toMap()
            val finalClusters = clusters.map { clusterMapping[it] ?: 0 }

            val nodes = uniqueLabels.mapIndexed { idx, label ->
                val degree = degreeMap[label] ?: 0
                // Node size in Compose can map to its degree (making hub nodes much larger)
                val radius = 8f + degree * 2f
                GraphNode(
                    id = label,
                    label = label,
                    posX = 0f,
                    posY = 0f,
                    colorArgb = 0, // Unused since color is handled inside GLSurfaceView shaders
                    radius = radius,
                    category = "General"
                )
            }
            
            engine.nativeInit(nodes.size, data.size)
            
            // Send nodes to C++ with computed mass (proportional to degree) and community cluster
            nodes.forEachIndexed { idx, node ->
                val degree = degreeMap[node.label] ?: 0
                val mass = 1.0f + degree * 1.5f
                engine.nativeSetNode(idx, mass, finalClusters[idx])
            }
            
            // Send edges to C++
            data.forEachIndexed { idx, edge ->
                val fromIdx = labelToId[edge.subject] ?: 0
                val toIdx = labelToId[edge.obj] ?: 0
                engine.nativeSetEdge(idx, fromIdx, toIdx, 1.0f)
            }
            
            _nodesState.value = nodes
        }
    }

    override fun onCleared() {
        super.onCleared()
        engine.nativeDestroy()
    }
}
