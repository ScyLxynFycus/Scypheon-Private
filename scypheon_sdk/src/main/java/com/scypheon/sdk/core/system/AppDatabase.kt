package com.scypheon.sdk.core.system

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.scypheon.sdk.core.agent.AgentCheckpoint
import com.scypheon.sdk.core.agent.AgentCheckpointDao
import com.scypheon.sdk.core.humanitarian.maps.MapTile
import com.scypheon.sdk.core.humanitarian.maps.MapTileDao
import com.scypheon.sdk.core.humanitarian.medical.*
import com.scypheon.sdk.core.telemetry.TelemetryDao
import com.scypheon.sdk.core.telemetry.TelemetryEvent
import com.scypheon.sdk.core.security.AuditEntry
import com.scypheon.sdk.core.security.AuditChainDao
import com.scypheon.sdk.core.humanitarian.mesh.MeshMessageEntity
import com.scypheon.sdk.core.humanitarian.mesh.MeshDao
import com.scypheon.sdk.core.grounding.*

import com.scypheon.sdk.core.intelligence.graph.GraphNode
import com.scypheon.sdk.core.intelligence.graph.GraphEdge
import com.scypheon.sdk.core.intelligence.graph.GraphDao

@Database(
    entities = [
        AgentCheckpoint::class, 
        MapTile::class,
        TelemetryEvent::class,
        FirstAidEntity::class,
        InteractionEntity::class,
        PharmacopeiaMetadata::class,
        MedicalVectorEntity::class,
        AuditEntry::class,
        MeshMessageEntity::class,
        PharmacopeiaEntry::class,
        PharmacopeiaFts::class,
        KnowledgeEntry::class,
        KnowledgeFts::class,
        GraphNode::class,
        GraphEdge::class
    ],
    version = 8,
    exportSchema = false
)
@TypeConverters(MedicalTypeConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun agentCheckpointDao(): AgentCheckpointDao
    abstract fun mapTileDao(): MapTileDao
    abstract fun telemetryDao(): TelemetryDao
    abstract fun pharmacopeiaDao(): PharmacopeiaDao
    abstract fun knowledgeDao(): KnowledgeDao
    abstract fun auditChainDao(): AuditChainDao
    abstract fun meshDao(): MeshDao
    abstract fun graphDao(): GraphDao
}
