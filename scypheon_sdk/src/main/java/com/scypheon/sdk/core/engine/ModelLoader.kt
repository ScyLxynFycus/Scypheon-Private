package com.scypheon.sdk.core.engine

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.ParcelFileDescriptor
import com.scypheon.sdk.core.sandbox.IModelLoader
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Proxy for the :loader process.
 * Manages the connection to ModelLoaderService and provides model FDs.
 */
@Singleton
class ModelLoader @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val loaderRef = AtomicReference<IModelLoader?>(null)
    private val isBound = AtomicReference(false)

    private val serviceIntent = Intent().setComponent(
        ComponentName(context.packageName, "com.scypheon.app.services.ModelLoaderService")
    )

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Timber.i("🛰️ [SAR] ModelLoader connected")
            loaderRef.set(IModelLoader.Stub.asInterface(service))
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            loaderRef.set(null)
            isBound.set(false)
        }
    }

    private suspend fun ensureServiceBound(): Boolean {
        if (loaderRef.get() != null) return true
        
        return suspendCancellableCoroutine { continuation ->
            try {
                // We use BIND_AUTO_CREATE and also START_STICKY in the service
                val bindSuccess = context.bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE)
                if (!bindSuccess) {
                    continuation.resume(false)
                } else {
                    isBound.set(true)
                    // Wait a bit for connection
                    continuation.resume(true)
                }
            } catch (e: Exception) {
                continuation.resume(false)
            }
        }
    }

    var isEnabled: Boolean = true
        set(value) {
            field = value
            if (!value) {
                Timber.i("🛰️ [SAR] Zero-Latency disabled by user. Purging resident models.")
                purge()
            }
        }

    suspend fun getModelFd(modelPath: String): ParcelFileDescriptor? {
        if (!isEnabled) return null 
        if (!ensureServiceBound()) return null
        
        // Wait for binder
        var wait = 0
        while (loaderRef.get() == null && wait < 20) {
            kotlinx.coroutines.delay(100)
            wait++
        }
        
        return try {
            loaderRef.get()?.loadModel(modelPath)
        } catch (e: Exception) {
            Timber.e(e, "Failed to get FD from loader")
            null
        }
    }

    fun isModelLoaded(): Boolean {
        return try {
            loaderRef.get()?.isModelLoaded ?: false
        } catch (e: Exception) {
            false
        }
    }

    fun purge() {
        try {
            loaderRef.get()?.purge()
        } catch (e: Exception) { /* Ignore */ }
    }
}
