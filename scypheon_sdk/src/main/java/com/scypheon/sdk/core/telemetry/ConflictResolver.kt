package com.scypheon.sdk.core.telemetry

/**
 * Enterprise Conflict Resolution Engine for Offline-First Data Sync.
 * Implements the Last-Write-Wins (LWW) strategy to ensure deterministic state resolution 
 * across partitioned nodes in disaster response scenarios.
 */
object ConflictResolver {
    
    /**
     * Resolves a conflict between a local and remote TelemetryEvent using the LWW strategy.
     * Preserves the event with the higher timestamp.
     */
    fun resolve(local: TelemetryEvent, remote: TelemetryEvent): TelemetryEvent {
        return if (remote.timestamp >= local.timestamp) {
            remote
        } else {
            local
        }
    }

    /**
     * Strategic Resolve Strategy for bulk operations.
     */
    enum class Strategy {
        LWW, // Last Write Wins
        MERGE // Deep Merge (not implemented for simple telemetry)
    }
}
