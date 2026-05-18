package com.scypheon.app.ui.screens

data class GraphEdge(
    val subject: String,
    val predicate: String,
    val obj: String
) {
    fun toRawEdge() = com.scypheon.app.data.models.RawGraphEdge(subject, predicate, obj)
}
