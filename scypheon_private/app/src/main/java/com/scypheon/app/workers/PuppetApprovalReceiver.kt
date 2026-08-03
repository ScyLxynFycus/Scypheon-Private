package com.scypheon.app.workers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.WorkManager
import com.scypheon.app.data.repository.ScypheonRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

/**
 * Enterprise HITL (Human-in-the-Loop) Receiver.
 * Listens for the "APPROVE" action from the VitreusFlowWorker notification.
 * Once approved, it clears the [AWAITING_APPROVAL] flag and allows the task to proceed (logically).
 */
@AndroidEntryPoint
class PuppetApprovalReceiver : BroadcastReceiver() {

    @Inject
    lateinit var repository: ScypheonRepository

    override fun onReceive(context: Context, intent: Intent) {
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
