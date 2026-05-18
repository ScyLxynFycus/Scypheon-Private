package com.scypheon.app.startup

import kotlinx.coroutines.CompletableDeferred

/**
 * Process-scoped database readiness signal.
 *
 * Architecture:
 *   ScypheonApplication  ──[complete()]──► DatabaseReadySignal ──[await()]──► MainActivity
 *
 * This eliminates the race condition where MainViewModel's init block launches
 * Room queries on Dispatchers.IO while the SQLCipher key derivation is still
 * holding the JNI monitor on DefaultDispatcher-worker-1, causing:
 *   - JNI critical lock held for ~370ms
 *   - Long monitor contention for ~518ms
 *   - Choreographer: Skipped 39 frames! / Davey! 823ms
 *
 * By holding the splash screen until this signal fires, we ensure the database
 * is fully opened and WAL mode is active before any ViewModel query races it.
 */
object DatabaseReadySignal {
    private val _deferred = CompletableDeferred<Unit>()

    /** Called by ScypheonApplication after the pre-warming SELECT 1 completes. */
    fun markReady() {
        _deferred.complete(Unit)
    }

    /** Suspend until markReady() is called. Safe to call from multiple coroutines. */
    suspend fun awaitReady() = _deferred.await()

    /** Non-suspending poll — true once markReady() has been called. */
    val isReady: Boolean get() = _deferred.isCompleted
}
