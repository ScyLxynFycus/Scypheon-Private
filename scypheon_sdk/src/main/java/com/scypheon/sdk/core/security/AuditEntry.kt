package com.scypheon.sdk.core.security

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(
    tableName = "audit_log",
    indices = [Index(value = ["hash"], unique = true)]
)
data class AuditEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val actionType: String,
    val payload: String,
    val hash: String,
    val previousHash: String
)
