package com.scypheon.sdk.core.humanitarian.mesh

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class MeshService : Service() {

    @Inject
    lateinit var bleMeshNetwork: BleMeshNetwork

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        bleMeshNetwork.startScanning()
        Timber.d("🚀 MeshService: Started and scanning")
    }

    override fun onDestroy() {
        bleMeshNetwork.stopScanning()
        Timber.d("🚀 MeshService: Destroyed and stopped scanning")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Mesh Networking",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Maintains offline mesh connectivity for emergency alerts"
        }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Scypheon Mesh Active")
            .setContentText("Listening for nearby emergency alerts...")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "mesh_service_channel"

        fun start(context: Context) {
            val intent = Intent(context, MeshService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, MeshService::class.java)
            context.stopService(intent)
        }
    }
}
