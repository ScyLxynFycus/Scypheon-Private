package com.scypheon.sdk.core.humanitarian

import android.content.Context
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.scypheon.sdk.R
import java.util.Locale

/**
 * DangerAlertActivity: Full-Screen RED Emergency Warning.
 *
 * Used for critical allergy/drug interaction warnings.
 * Cannot be dismissed easily - Requires Explicit acknowledgement.
 */
class DangerAlertActivity : AppCompatActivity() {

    private var tts: TextToSpeech? = null
    private var vibrator: Vibrator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Make it full screen and wake up device
        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )

        // Get message from intent
        val message = intent.getStringExtra("message") ?: getString(R.string.default_health_warning)

        // Build simple layout - just red screen with message
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFFCC0000.toInt()) // Deep red
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
        }

        // Warning icon
        val icon = TextView(this).apply {
            text = getString(R.string.warning_icon)
            textSize = 80f
            gravity = Gravity.CENTER
        }

        // Title
        val title = TextView(this).apply {
            text = getString(R.string.danger_title)
            textSize = 48f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }

        // Message
        val messageView = TextView(this).apply {
            text = message
            textSize = 24f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
            setPadding(32, 32, 32, 32)
        }

        // Acknowledge button
        val button = Button(this).apply {
            text = getString(R.string.i_understand)
            textSize = 20f
            setOnClickListener {
                finish()
            }
        }

        layout.addView(icon)
        layout.addView(title)
        layout.addView(messageView)
        layout.addView(button)

        setContentView(layout)

        // Speak the warning
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.ENGLISH
                tts?.speak("${getString(R.string.danger_title)} $message", TextToSpeech.QUEUE_FLUSH, null, "danger")
            }
        }

        // Vibrate continuously
        @Suppress("DEPRECATION")
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        // ENTERPRISE: minSdk 28 guarantees O, so always use VibrationEffect
        vibrator?.vibrate(VibrationEffect.createWaveform(
            longArrayOf(0, 500, 200, 500, 200, 500),
            0 // Repeat from index 0
        ))
    }

    override fun onDestroy() {
        super.onDestroy()
        tts?.shutdown()
        vibrator?.cancel()
    }

    @Deprecated("Deprecated in Java", ReplaceWith("Unit"))
    override fun onBackPressed() {
        // ENTERPRISE: Intentionally block back press during emergency alert
        // User must explicitly tap the button to dismiss - this is a safety feature
        // super.onBackPressed() is NOT called intentionally to prevent dismissal
    }
}
