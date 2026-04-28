package com.scypheon.app.data.models

/**
 * High-Performance Graph Models.
 * Uses raw primitives (Float, Int ARGB) to prevent boxing and allocation churn
 * during high-frequency drawing and physics calculations.
 */
data class GraphNode(
    val id: String,
    val label: String,
    var posX: Float,
    var posY: Float,
    val colorArgb: Int,
    val radius: Float,
    val category: String,
    var isSelected: Boolean = false
)

data class GraphEdge(
    val fromX: Float,
    val fromY: Float,
    val toX: Float,
    val toY: Float,
    val particleOffset: Float, // Precomputed [0..1]
    val predicate: String
)

data class RawGraphEdge(
    val subject: String,
    val predicate: String,
    val obj: String
)

data class GraphLayout(
    val nodes: List<GraphNode> = emptyList(),
    val edges: List<GraphEdge> = emptyList()
)
