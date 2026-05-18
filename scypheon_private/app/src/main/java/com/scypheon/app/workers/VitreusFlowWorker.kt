package com.scypheon.app.workers

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.scypheon.app.data.repository.ScypheonRepository
import com.scypheon.sdk.core.memory.DualMemoryManager
import com.scypheon.sdk.core.swarm.AgentOrchestrator
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

/**
 * Enterprise Worker: Vitreus Flow
 * Background orchestrator for multi-agent execution using Llama.cpp.
 * Uses a Mutex to prevent Kernel Panics from simultaneous LLM instantiations.
 * Promotes itself to a Foreground Service to evade OS timeouts during heavy inference.
 */
@HiltWorker
class VitreusFlowWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: ScypheonRepository,
    private val dualMemoryManager: DualMemoryManager,
    private val agentOrchestrator: AgentOrchestrator
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private val aiExecutionMutex = Mutex()
    }

    override suspend fun doWork(): Result {
        Timber.i("⚙︁EVitreusFlowWorker: Background AI task started")

        //  PROTOCOL: Database TTL Sweep (AGENTS.md Section 3)
        // Purge historical logs > 30 days and expire zombie AWAITING_APPROVAL tasks > 15 mins
        performDatabaseCleanup()

        // Promote to Foreground Service to prevent Android from killing us after 10 mins
        setForeground(createForegroundInfo())

        return try {
            // Strictly route all AI executions through a global Kotlin Mutex
            aiExecutionMutex.withLock {
                Timber.d("⚙︁EVitreusFlowWorker: Acquired Mutex lock for LLM inference")

                // TRUE SWARM EXECUTION: Analyze recent user history concurrently using multiple specialized agents

                // 1. Fetch recent global context
                val recentSessions = dualMemoryManager.getAllSessions().take(3)
                val conversationContext = StringBuilder()
                recentSessions.forEach { session ->
                    val msgs = dualMemoryManager.getMessagesForSession(session.id)
                        .filter { it.isContextEligible && it.status == com.scypheon.sdk.core.memory.ScypheonDbHelper.STATUS_SUCCESS }
                        .takeLast(5)
                    msgs.forEach { msg ->
                        val role = if (msg.isUser) "User" else "AI"
                        conversationContext.append("$role: ${msg.text}\n")
                    }
                }

                if (conversationContext.isBlank()) {
                     conversationContext.append("No recent history available.")
                }

                // 2. Delegate the context to the concurrent AgentOrchestrator swarm
                val swarmTask = "Analyze the following recent user interactions. Is there any immediate medical concern or security risk (e.g. scams/phishing)? \n\nInteractions:\n$conversationContext"
                Timber.i(" VitreusFlowWorker: Triggering Multi-Agent Swarm execution...")

                val swarmReport = agentOrchestrator.swarmExecute(swarmTask)

                Timber.i(" VitreusFlowWorker: Swarm Report Generated: \n$swarmReport")

                // 🛡 SECURITY: High-Risk Operation Detection (Human-in-the-Loop)
                // As per AGENTS.md, we must intercept destructive or sensitive actions.
                val isHighRisk = detectHighRiskTask(swarmReport)
                val finalReport = if (isHighRisk) {
                    Timber.w("🚨 SECURITY ALERT: High-risk operation detected in Swarm Report. Suspending for HITL approval.")
                    "[AWAITING_APPROVAL] ⚠️ POTENTIAL SENSITIVE ACTION DETECTED:\n$swarmReport"
                } else {
                    "🐝 Multi-Agent Swarm Analysis:\n$swarmReport"
                }

                // 3. Save the background agent's thought into memory
                repository.saveSessionMessage(
                    "swarm_background_ops", 
                    finalReport, 
                    isUser = false, 
                    status = if (isHighRisk) com.scypheon.sdk.core.memory.ScypheonDbHelper.STATUS_SYSTEM else com.scypheon.sdk.core.memory.ScypheonDbHelper.STATUS_SUCCESS,
                    isContextEligible = false // Internal swarm reports shouldn't pollute user context
                )

                if (isHighRisk) {
                    showApprovalNotification()
                }

                Timber.d("⚙︁EVitreusFlowWorker: LLM inference completed")
            }
            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "⚙︁EVitreusFlowWorker: Failed during background inference")
            Result.retry() // Thermal Throttling Backoff handles this natively via WorkManager policies
        }
    }

    /**
     * Scans the agent output for high-risk keywords defined in AGENTS.md
     */
    private fun detectHighRiskTask(report: String): Boolean {
        val lowReport = report.lowercase()
        val riskKeywords = listOf("send", "transfer", "gmail", "delete", "remove", "format", "pay")
        return riskKeywords.any { lowReport.contains(it) }
    }

    private fun showApprovalNotification() {
        val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        
        // 🛡 SECURITY AUDIT: Check for POST_NOTIFICATIONS permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = android.Manifest.permission.POST_NOTIFICATIONS
            if (appContext.checkSelfPermission(permission) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Timber.e(" HITL FAILURE: Missing POST_NOTIFICATIONS permission. User will not see approval request!")
                return
            }
        }

        // 🛡 PROTOCOL: PuppetApprovalReceiver Intent (AGENTS.md Section 1)
        // Note: You must define PuppetApprovalReceiver in AndroidManifest.xml
        val approveIntent = android.content.Intent("com.scypheon.app.ACTION_APPROVE_PUPPET").apply {
            putExtra("task_id", id.toString())
            `package` = appContext.packageName
        }
        val pendingApprove = android.app.PendingIntent.getBroadcast(
            appContext, 0, approveIntent, 
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(appContext, "scypheon_worker_channel")
            .setContentTitle("🛡 Action Required: Vitreon Agent")
            .setContentText("A high-risk background task requires your manual approval.")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .addAction(android.R.drawable.ic_media_play, "APPROVE", pendingApprove)
            .setAutoCancel(true)
            .build()
            
        notificationManager.notify(1338, notification)
    }

    private suspend fun performDatabaseCleanup() {
        try {
            val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
            val fifteenMinsAgo = System.currentTimeMillis() - (15L * 60 * 1000)
            
            Timber.d(" Database Sweep: Cleaning up records older than 30 days...")
            repository.performTtlSweep(thirtyDaysAgo)
            repository.expireZombieTasks(fifteenMinsAgo)
            
            Timber.i(" Database Cleanup: Completed successfully.")
        } catch (e: Exception) {
            Timber.e(e, " Database Sweep Failed")
        }
    }

    private fun createForegroundInfo(): ForegroundInfo {
        // Create the NotificationChannel
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                "scypheon_worker_channel",
                "Scypheon AI Agents",
                android.app.NotificationManager.IMPORTANCE_LOW
            )
            val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        // Create a simple notification for the foreground service
        val notification = NotificationCompat.Builder(appContext, "scypheon_worker_channel")
            .setContentTitle("Scypheon Agent")
            .setContentText("Running background AI inference...")
            .setSmallIcon(android.R.drawable.ic_dialog_info) // Using a default icon for simplicity
            .setOngoing(true)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                1337,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(1337, notification)
        }
    }
}
