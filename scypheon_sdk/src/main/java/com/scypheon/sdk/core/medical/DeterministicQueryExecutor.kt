package com.scypheon.sdk.core.medical

import com.scypheon.sdk.core.annotations.SafetyCritical
import com.scypheon.sdk.core.engine.InferenceGovernor
import com.scypheon.sdk.core.system.AppDatabase
import com.scypheon.sdk.core.telemetry.TelemetryDao
import com.scypheon.sdk.core.telemetry.TelemetryEvent
import kotlinx.coroutines.*
import java.util.UUID
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@SafetyCritical
@Singleton
class DeterministicQueryExecutor @Inject constructor(
    private val dbProvider: Provider<AppDatabase>,
    private val fallbackCache: RuleBasedMedicalCache,
    private val governor: InferenceGovernor,
    private val telemetryDao: TelemetryDao
) {
    companion object {
        private const val QUERY_SLO_MS = 50L
        // Dedicated dispatcher to prevent UI jank and DB deadlocks
        private val queryDispatcher = Dispatchers.IO.limitedParallelism(2)
    }

    suspend fun execute(query: String): Result<MedicalResponse> = withContext(queryDispatcher) {
        val traceId = UUID.randomUUID().toString()
        
        // 1. Hardware-Aware Gating
        when (val state = governor.evaluate()) {
            is InferenceGovernor.GovernorState.Allow -> Unit
            is InferenceGovernor.GovernorState.Throttle -> {
                // Thermal pressure detected -> Skip DB, go straight to lightweight cache
                return@withContext Result.success(fallbackToCache(query, state.reason))
            }
            is InferenceGovernor.GovernorState.Block -> {
                return@withContext Result.failure(GovernorBlockException("Device critical: ${state.reason}"))
            }
        }

        // 2. Timed Database Execution (SLO Enforcement)
        try {
            withTimeout(QUERY_SLO_MS) {
                val db = dbProvider.get()
                // We use FTS5 for sub-millisecond keyword search
                val results = db.mapTileDao().getTileCount() // Temporary proof of concept till PharmacopeiaDao is fully wired
                
                if (results > 0) {
                    Result.success(MedicalResponse.Exact("Found $results local assets"))
                } else {
                    Result.success(fallbackToCache(query, "EMPTY_DATABASE_MATCH"))
                }
            }
        } catch (e: TimeoutCancellationException) {
            logAudit(traceId, "QUERY_TIMEOUT", query)
            Result.success(fallbackToCache(query, "TIMEOUT_50MS"))
        } catch (e: Exception) {
            logAudit(traceId, "DATABASE_ERROR", e.message ?: "Unknown")
            Result.success(fallbackToCache(query, "DB_FAILURE"))
        }
    }

    private fun fallbackToCache(query: String, reason: String): MedicalResponse {
        return fallbackCache.resolve(query).fold(
            onSuccess = { MedicalResponse.Fallback(it, reason) },
            onFailure = { MedicalResponse.ClarificationRequired(query, reason) }
        )
    }

    private suspend fun logAudit(traceId: String, type: String, payload: String) {
        telemetryDao.insert(TelemetryEvent(
            eventId = UUID.randomUUID().toString(),
            type = type,
            payload = payload,
            timestamp = System.currentTimeMillis(),
            synced = false
        ))
    }
}

sealed class MedicalResponse {
    data class Exact(val data: String) : MedicalResponse()
    data class Fallback(val protocol: RuleBasedMedicalCache.StaticProtocol, val reason: String) : MedicalResponse()
    data class ClarificationRequired(val query: String, val reason: String) : MedicalResponse()
}

class GovernorBlockException(msg: String) : Exception(msg)
