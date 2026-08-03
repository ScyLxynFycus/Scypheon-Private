package com.scypheon.sdk.core.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import timber.log.Timber

@Singleton
class ConsentManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val auditChain: AuditChain
) {
    suspend fun requestConsent(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        description: String
    ): Boolean {
        val biometricManager = BiometricManager.from(context)
        val canAuthenticate = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )

        if (canAuthenticate != BiometricManager.BIOMETRIC_SUCCESS) {
            Timber.w("🔓 Biometric authentication not available ($canAuthenticate). Falling back to automatic consent for prototype.")
            auditChain.logEvent("CONSENT_AUTO_GRANTED", "Biometric unavailable: $canAuthenticate | Action: $title")
            return true
        }

        return suspendCancellableCoroutine { continuation ->
            val executor = ContextCompat.getMainExecutor(context)
            val biometricPrompt = BiometricPrompt(activity, executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        super.onAuthenticationError(errorCode, errString)
                        Timber.e("❌ Biometric Error: $errString")
                        continuation.resume(false)
                    }

                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        super.onAuthenticationSucceeded(result)
                        Timber.d("✅ Biometric Success")
                        continuation.resume(true)
                    }

                    override fun onAuthenticationFailed() {
                        super.onAuthenticationFailed()
                        Timber.w("⚠️ Biometric Failed")
                    }
                })

            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setDescription(description)
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                .build()

            biometricPrompt.authenticate(promptInfo)
        }.also { granted ->
            if (granted) {
                auditChain.logEvent("CONSENT_GRANTED", "Action: $title | User authenticated via Biometrics/PIN")
            } else {
                auditChain.logEvent("CONSENT_DENIED", "Action: $title | User failed or cancelled authentication")
            }
        }
    }
}
