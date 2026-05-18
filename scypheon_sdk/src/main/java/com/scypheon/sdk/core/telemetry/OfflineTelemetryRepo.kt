package com.scypheon.sdk.core.telemetry

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import java.util.UUID

@Singleton
class OfflineTelemetryRepo @Inject constructor(
    private val db: TelemetryDao
) {

    suspend fun enqueue(type: String, payload: String) = withContext(Dispatchers.IO) {
        val event = TelemetryEvent(
            eventId = UUID.randomUUID().toString(),
            type = type,
            payload = payload,
            timestamp = System.currentTimeMillis(),
            synced = false
        )
        db.insert(event)
    }

    suspend fun flushPending() = withContext(Dispatchers.IO) {
        val pending = db.getUnsynced(limit = 50)
        if (pending.isNotEmpty()) {
            db.markSynced(pending.map { it.id })
        }
    }

    suspend fun getUnsyncedCount(): Int = withContext(Dispatchers.IO) {
        db.getUnsyncedCount()
    }
}
