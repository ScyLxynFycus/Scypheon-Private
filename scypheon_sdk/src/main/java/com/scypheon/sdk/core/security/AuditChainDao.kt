package com.scypheon.sdk.core.security

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AuditChainDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entry: AuditEntry)

    @Query("SELECT * FROM audit_log ORDER BY id DESC LIMIT 1")
    suspend fun getLastEntry(): AuditEntry?

    @Query("SELECT COUNT(*) FROM audit_log")
    suspend fun getCount(): Int

    @Query("SELECT * FROM audit_log ORDER BY id ASC")
    suspend fun getAllEntries(): List<AuditEntry>
}
