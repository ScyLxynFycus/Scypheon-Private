package com.scypheon.sdk.core.humanitarian.mesh

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(
    tableName = "mesh_messages",
    indices = [Index(value = ["packetId"], unique = true)]
)
data class MeshMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packetId: String,
    val senderDeviceId: String,
    val payload: String,
    val timestamp: Long,
    val relayCount: Int,
    val signature: String
)
