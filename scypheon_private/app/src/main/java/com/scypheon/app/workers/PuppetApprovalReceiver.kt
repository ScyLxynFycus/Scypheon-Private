package com.scypheon.app.workers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.scypheon.app.data.repository.ScypheonRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Enterprise HITL (Human-in-the-Loop) Receiver.
 * Uses EntryPointAccessors instead of @AndroidEntryPoint to bypass ASM bytecode injection bugs in Hilt/AGP.
 */
class PuppetApprovalReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface PuppetApprovalEntryPoint {
        fun repository(): ScypheonRepository
    }

    override fun onReceive(context: Context, intent: Intent) {
        // Safe manual injection bypassing the Hilt_ prefix bytecode generation
        val appContext = context.applicationContext
        val entryPoint = EntryPointAccessors.fromApplication(appContext, PuppetApprovalEntryPoint::class.java)
        val repository = entryPoint.repository()

        if (intent.action == "com.scypheon.app.ACTION_APPROVE_PUPPET") {
            val taskId = intent.getStringExtra("task_id")
            Timber.i("🛡 HITL Approval Received for Task: $taskId")

            // In a production system, this would trigger a state change in the DB 
            // allowing the suspended background task to resume or be marked as 'Verified'.
            CoroutineScope(Dispatchers.IO).launch {
                repository.saveSessionMessage(
                    "swarm_background_ops",
                    "[USER_APPROVED] The previously suspended high-risk operation has been authorized by the human operator.",
                    isUser = true
                )
                
                // Cancel the notification
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                notificationManager.cancel(1338)
            }
        }
    }
}
