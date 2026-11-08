package com.example.nova.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.nova.MainActivity
import com.example.nova.R
import com.example.nova.helpers.WakeWordHelper
import com.example.nova.presentation.ui.NovaVoicePopupActivity
import java.util.Locale

/**
 * Nova AI Background Voice Service
 *
 * Runs completely silently in the background with zero popups and zero dialogs.
 * STRICT RULE:
 * - NO popup is EVER opened unless the user explicitly and clearly says "Hey Nova"!
 * - Ambient noise, room talk, background sounds, or other people speaking will NEVER trigger a popup.
 * - When "Hey Nova" is heard:
 *   1. Vibrates immediately to acknowledge.
 *   2. Opens the popup EXACTLY ONCE with wakeWordConfirmed = true.
 *   3. Nova greets the user immediately ("Yes Shubham! How can I help you?").
 *   4. Listens for the user's task and executes it.
 *   5. When done or dismissed, the popup closes completely and Nova returns to silent sleep.
 *   6. Nova will NEVER open another popup until the user says "Hey Nova" again.
 */
class NovaBackgroundVoiceService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private var wakeWordRecognizer: SpeechRecognizer? = null
    private var isListeningWakeWord = false

    override fun onCreate() {
        super.onCreate()
        instance = this
        isRunning = true
        Log.i(TAG, "Nova Background Voice Service Created")

        // 1. Partial WakeLock to keep CPU alive in background
        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Nova::BackgroundVoiceWakeLock")?.apply {
            setReferenceCounted(false)
            acquire(12 * 60 * 60 * 1000L)
        }

        // 2. Foreground service notification
        createNotificationChannel()
        startForegroundServiceWithNotification("Listening for \"Hey Nova\"...")

        // 3. Start strict wake word recognition
        startWakeWordMonitoring()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_SERVICE) {
            Log.i(TAG, "Stop command received via notification")
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_TRIGGER_POPUP) {
            // User manually tapped "Open Nova" in notification
            launchAssistantPopup(wakeWordConfirmed = true, initialCommand = "", isManual = true)
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        isPopupActive = false
        instance = null
        mainHandler.removeCallbacksAndMessages(null)

        stopWakeWordMonitoring()

        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {}

        Log.i(TAG, "Nova Background Voice Service Destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // --- Strict Wake Word Monitoring ---

    private fun startWakeWordMonitoring() {
        if (!isRunning || isPopupActive) return

        mainHandler.post {
            try {
                if (!SpeechRecognizer.isRecognitionAvailable(this)) {
                    Log.w(TAG, "SpeechRecognizer is not available on this device!")
                    return@post
                }
                initWakeWordRecognizer()
                startListeningForWakeWord()
            } catch (e: Exception) {
                Log.e(TAG, "Error starting wake word monitor", e)
                scheduleRestartWakeWord(2000L)
            }
        }
    }

    private fun initWakeWordRecognizer() {
        destroyWakeWordRecognizer()

        try {
            wakeWordRecognizer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                SpeechRecognizer.isOnDeviceRecognitionAvailable(this)
            ) {
                Log.i(TAG, "Using OnDeviceSpeechRecognizer for silent background wake word")
                SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
            } else {
                SpeechRecognizer.createSpeechRecognizer(this)
            }
            wakeWordRecognizer?.setRecognitionListener(createWakeWordListener())
        } catch (e: Exception) {
            Log.e(TAG, "Error creating SpeechRecognizer", e)
            try {
                wakeWordRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
                wakeWordRecognizer?.setRecognitionListener(createWakeWordListener())
            } catch (ex: Exception) {
                Log.e(TAG, "Fallback createSpeechRecognizer failed", ex)
            }
        }
    }

    private fun startListeningForWakeWord() {
        if (!isRunning || isPopupActive) return

        mainHandler.post {
            try {
                if (wakeWordRecognizer == null) {
                    initWakeWordRecognizer()
                }

                val currentLocale = Locale.getDefault()
                val languageTag = currentLocale.toLanguageTag()

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageTag)
                    putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
                    putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("en-IN", "hi-IN", "gu-IN", "en-US"))
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 3000L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
                }

                wakeWordRecognizer?.startListening(intent)
                isListeningWakeWord = true
            } catch (e: Exception) {
                Log.e(TAG, "Error starting speech recognizer", e)
                scheduleRestartWakeWord(1500L, reinit = true)
            }
        }
    }

    private fun createWakeWordListener() = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}

        override fun onError(error: Int) {
            isListeningWakeWord = false
            if (!isRunning || isPopupActive) return

            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                    // Normal room silence — restart silently
                    scheduleRestartWakeWord(300L)
                }
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
                SpeechRecognizer.ERROR_CLIENT -> {
                    scheduleRestartWakeWord(800L, reinit = true)
                }
                SpeechRecognizer.ERROR_AUDIO -> {
                    scheduleRestartWakeWord(1500L, reinit = true)
                }
                else -> {
                    scheduleRestartWakeWord(800L)
                }
            }
        }

        override fun onResults(results: Bundle?) {
            isListeningWakeWord = false
            if (!isRunning || isPopupActive) return

            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (matches.isNullOrEmpty()) {
                scheduleRestartWakeWord(300L)
                return
            }

            // STRICT CHECK: Does any recognized text contain "Hey Nova"?
            val matched = matches.firstOrNull { WakeWordHelper.containsWakeWord(it) }
            if (matched != null) {
                Log.i(TAG, ">>> 'Hey Nova' WAKE WORD CONFIRMED: '$matched' <<<")
                onWakeWordDetected(matched)
            } else {
                // OTHER VOICE OR NOISE HEARD, BUT NO "HEY NOVA" -> COMPLETELY IGNORE!
                // NO POPUP, NO VIBRATION, NO SOUND!
                Log.d(TAG, "Ignored background noise/voice (No 'Hey Nova'): $matches")
                scheduleRestartWakeWord(300L)
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            if (!isRunning || isPopupActive) return

            val partialMatches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!partialMatches.isNullOrEmpty()) {
                val matched = partialMatches.firstOrNull { WakeWordHelper.containsWakeWord(it) }
                if (matched != null) {
                    Log.i(TAG, ">>> 'Hey Nova' INSTANT PARTIAL MATCH: '$matched' <<<")
                    try {
                        wakeWordRecognizer?.stopListening()
                    } catch (_: Exception) {}
                    isListeningWakeWord = false
                    onWakeWordDetected(matched)
                }
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun onWakeWordDetected(fullSpokenText: String) {
        mainHandler.post {
            val now = System.currentTimeMillis()
            // Ignore if popup is already open or closed less than 3 seconds ago
            if (isPopupActive || (now - lastPopupCloseTime < 3000L)) {
                return@post
            }

            Log.i(TAG, "Triggering Nova popup for wake word: '$fullSpokenText'")
            triggerHapticFeedback()

            val trailingCommand = WakeWordHelper.extractCommand(fullSpokenText)
            launchAssistantPopup(
                wakeWordConfirmed = true,
                initialCommand = trailingCommand,
                isManual = false
            )
        }
    }

    private fun scheduleRestartWakeWord(delayMs: Long, reinit: Boolean = false) {
        if (!isRunning || isPopupActive) return

        mainHandler.postDelayed({
            if (isRunning && !isPopupActive) {
                if (reinit) {
                    initWakeWordRecognizer()
                }
                startListeningForWakeWord()
            }
        }, delayMs)
    }

    private fun stopWakeWordMonitoring() {
        destroyWakeWordRecognizer()
    }

    private fun destroyWakeWordRecognizer() {
        try {
            wakeWordRecognizer?.stopListening()
            wakeWordRecognizer?.cancel()
            wakeWordRecognizer?.destroy()
        } catch (_: Exception) {}
        wakeWordRecognizer = null
        isListeningWakeWord = false
    }

    // --- Assistant Popup Launch ---

    private fun launchAssistantPopup(
        wakeWordConfirmed: Boolean = true,
        initialCommand: String = "",
        isManual: Boolean = false
    ) {
        if (isPopupActive) return
        isPopupActive = true

        // Free the microphone immediately for the popup
        destroyWakeWordRecognizer()

        try {
            val popupIntent = Intent(this, NovaVoicePopupActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(NovaVoicePopupActivity.EXTRA_WAKE_WORD_CONFIRMED, wakeWordConfirmed)
                putExtra(NovaVoicePopupActivity.EXTRA_INITIAL_COMMAND, initialCommand)
                putExtra(NovaVoicePopupActivity.EXTRA_MANUAL_LAUNCH, isManual)
            }
            startActivity(popupIntent)
            updateNotification("Active • Nova Assistant Running")
        } catch (e: Exception) {
            Log.e(TAG, "Error launching NovaVoicePopupActivity", e)
            isPopupActive = false
            scheduleRestartWakeWord(2000L)
        }
    }

    fun onPopupDismissed() {
        mainHandler.post {
            updateNotification("Listening for \"Hey Nova\"...")
            // Wait 1.5 seconds for all audio to fully settle before resuming silent wake word listener
            mainHandler.postDelayed({
                if (isRunning && !isPopupActive) {
                    startWakeWordMonitoring()
                    Log.i(TAG, "Silent wake word monitor resumed — waiting for next 'Hey Nova'")
                }
            }, 1500L)
        }
    }

    private fun triggerHapticFeedback() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(70, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(70)
                }
            }
        } catch (_: Exception) {}
    }

    // --- Notification & Foreground Service Setup ---

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Nova Voice Assistant Background Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps Nova voice recognition active in background"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun startForegroundServiceWithNotification(statusText: String) {
        val notification = buildNotification(statusText)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(statusText: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(statusText))
    }

    private fun buildNotification(statusText: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerIntent = Intent(this, NovaBackgroundVoiceService::class.java).apply {
            action = ACTION_TRIGGER_POPUP
        }
        val triggerPendingIntent = PendingIntent.getService(
            this,
            1,
            triggerIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, NovaBackgroundVoiceService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            2,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Nova AI Voice Assistant")
            .setContentText(statusText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(openPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(android.R.drawable.ic_btn_speak_now, "Open Nova", triggerPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
            .build()
    }

    companion object {
        private const val TAG = "NovaVoiceService"
        private const val CHANNEL_ID = "nova_background_voice_channel"
        private const val NOTIFICATION_ID = 2026
        const val ACTION_STOP_SERVICE = "com.example.nova.action.STOP_VOICE_SERVICE"
        const val ACTION_TRIGGER_POPUP = "com.example.nova.action.TRIGGER_POPUP"

        var isRunning: Boolean = false
            private set

        var isPopupActive: Boolean = false
            private set

        private var lastPopupCloseTime: Long = 0L

        private var instance: NovaBackgroundVoiceService? = null

        fun notifyPopupClosed() {
            isPopupActive = false
            lastPopupCloseTime = System.currentTimeMillis()
            instance?.onPopupDismissed()
        }

        fun start(context: Context) {
            val intent = Intent(context, NovaBackgroundVoiceService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            context.getSharedPreferences("nova_prefs", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("background_voice_enabled", true)
                .apply()
        }

        fun stop(context: Context) {
            val intent = Intent(context, NovaBackgroundVoiceService::class.java)
            context.stopService(intent)
            context.getSharedPreferences("nova_prefs", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("background_voice_enabled", false)
                .apply()
        }
    }
}
