package com.scypheon.sdk.core.intelligence.graph

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Knowledge Graph Schema for GraphRAG Oracle.
 * Optimized for local SQLite traversal with importance-based filtering.
 */

@Entity(tableName = "graph_nodes")
data class GraphNode(
    @PrimaryKey val id: String,
    val label: String,
    val type: String, // e.g., "DRUG", "LOCATION", "SYMPTOM", "PERSON"
    val metadata: String, // JSON blob for extended properties
    val importance: Float = 0.5f // 0.0 to 1.0
)

@Entity(
    tableName = "graph_edges",
    foreignKeys = [
        ForeignKey(entity = GraphNode::class, parentColumns = ["id"], childColumns = ["sourceId"]),
        ForeignKey(entity = GraphNode::class, parentColumns = ["id"], childColumns = ["targetId"])
    ],
    indices = [Index("sourceId"), Index("targetId")]
)
data class GraphEdge(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceId: String,
    val targetId: String,
    val relation: String, // e.g., "CAUSES", "TREATS", "LOCATED_IN", "ALLERGIC_TO"
    val impactScore: Float = 0.5f, // Criticality of this relationship
    val source: String? = null // Citation/Source of information
)

@Dao
interface GraphDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNode(node: GraphNode)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEdge(edge: GraphEdge)

    @Query("SELECT * FROM graph_nodes WHERE label LIKE :query OR id = :query LIMIT 10")
    suspend fun findNodesByLabel(query: String): List<GraphNode>

    /**
     * Weighted Traversal: Finds related nodes within 1-hop that meet the impact threshold.
     * This implements the "Patroli Terarah" optimization.
     */
    @Query("""
        SELECT n.* FROM graph_nodes n
        INNER JOIN graph_edges e ON n.id = e.targetId
        WHERE e.sourceId = :nodeId AND e.impactScore >= :minImpact
        UNION
        SELECT n.* FROM graph_nodes n
        INNER JOIN graph_edges e ON n.id = e.sourceId
        WHERE e.targetId = :nodeId AND e.impactScore >= :minImpact
    """)
    suspend fun getRelatedNodes(nodeId: String, minImpact: Float): List<GraphNode>

    @Query("SELECT * FROM graph_edges WHERE sourceId = :nodeId OR targetId = :nodeId")
    suspend fun getEdgesForNode(nodeId: String): List<GraphEdge>
}
