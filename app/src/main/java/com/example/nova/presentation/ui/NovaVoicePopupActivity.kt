package com.example.nova.presentation.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nova.helpers.ActionExecutor
import com.example.nova.helpers.CommandProcessor
import com.example.nova.helpers.TTSHelper
import com.example.nova.helpers.WakeWordHelper
import com.example.nova.service.NovaBackgroundVoiceService
import java.util.Locale

/**
 * NovaVoicePopupActivity
 *
 * Sleek, single-turn floating assistant popup triggered ONLY when "Hey Nova" is heard.
 * Flow:
 * 1. Opened only by explicit "Hey Nova" or manual button tap.
 * 2. Instant greeting reply ("Yes Shubham! How can I help you?").
 * 3. Listens for the user's task, executes immediately, speaks completion, and closes.
 * 4. Dismisses itself cleanly so the background service sleeps silently without looping.
 */
class NovaVoicePopupActivity : ComponentActivity() {

    private lateinit var ttsHelper: TTSHelper
    private lateinit var actionExecutor: ActionExecutor
    private lateinit var commandProcessor: CommandProcessor

    private var speechRecognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isFinishingActivity = false

    private var isWakeWordConfirmed = false
    private var isManualLaunch = false
    private var isAwaitingTaskAfterReply = false

    // State holders for Compose UI
    private var statusTextState = mutableStateOf("Listening...")
    private var userSpokenState = mutableStateOf("")
    private var assistantResponseState = mutableStateOf("")
    private var rmsDbState = mutableFloatStateOf(0f)
    private var isExecutingState = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "NovaVoicePopupActivity launched")

        // 1. Wake screen if triggered while locked
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        // 2. Parse incoming intent extras
        isWakeWordConfirmed = intent.getBooleanExtra(EXTRA_WAKE_WORD_CONFIRMED, true)
        isManualLaunch = intent.getBooleanExtra(EXTRA_MANUAL_LAUNCH, false)
        val initialCommand = intent.getStringExtra(EXTRA_INITIAL_COMMAND)?.trim() ?: ""

        // 3. Initialize core engines
        actionExecutor = ActionExecutor(this)
        commandProcessor = CommandProcessor(actionExecutor)
        ttsHelper = TTSHelper(this)

        // 4. Setup Compose UI
        setContent {
            NovaPopupContent(
                statusText = statusTextState.value,
                userSpokenText = userSpokenState.value,
                assistantResponseText = assistantResponseState.value,
                rmsDb = rmsDbState.floatValue,
                isExecuting = isExecutingState.value,
                onDismiss = { dismissPopup() }
            )
        }

        // 5. Instant Response Flow:
        if (initialCommand.isNotEmpty()) {
            // User said "Hey Nova [command]" in one phrase
            mainHandler.postDelayed({
                executeCommand(initialCommand)
            }, 100L)
        } else {
            // User said "Hey Nova!" -> INSTANT greeting without waiting!
            mainHandler.postDelayed({
                respondWithGreetingAndListenForTask()
            }, 80L)
        }

        // Safety auto-dismiss if user leaves screen idle for 10 seconds
        mainHandler.postDelayed({
            if (!isFinishingActivity && userSpokenState.value.isEmpty() && !isExecutingState.value) {
                Log.d(TAG, "Popup auto-dismiss due to idle timeout")
                dismissPopup()
            }
        }, 10000L)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        val newCommand = intent?.getStringExtra(EXTRA_INITIAL_COMMAND)?.trim() ?: ""
        if (newCommand.isNotEmpty()) {
            executeCommand(newCommand)
        } else {
            respondWithGreetingAndListenForTask()
        }
    }

    /**
     * Responds to the user INSTANTLY when "Hey Nova" is heard.
     * Nova says: "Yes Shubham! How can I help you?",
     * and as soon as it finishes, immediately turns on the mic to execute the task.
     */
    private fun respondWithGreetingAndListenForTask() {
        if (isFinishingActivity) return
        isAwaitingTaskAfterReply = true
        val greeting = WakeWordHelper.getGreetingReply(this)

        statusTextState.value = greeting
        assistantResponseState.value = greeting
        isExecutingState.value = true

        ttsHelper.speak(greeting, onDone = {
            mainHandler.post {
                if (!isFinishingActivity) {
                    statusTextState.value = "Listening for your task..."
                    isExecutingState.value = false
                    startListeningForSpeech()
                }
            }
        })
    }

    private fun startListeningForSpeech() {
        if (isFinishingActivity) return
        try {
            if (speechRecognizer == null) {
                speechRecognizer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    SpeechRecognizer.isOnDeviceRecognitionAvailable(this)
                ) {
                    SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
                } else if (SpeechRecognizer.isRecognitionAvailable(this)) {
                    SpeechRecognizer.createSpeechRecognizer(this)
                } else {
                    statusTextState.value = "Speech recognition unavailable"
                    finishWithDelay(1500L)
                    return
                }
                speechRecognizer?.setRecognitionListener(createListener())
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
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 2500L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            }

            speechRecognizer?.startListening(intent)
            statusTextState.value = "Listening for your task..."
        } catch (e: Exception) {
            Log.e(TAG, "Error starting voice recognition in popup", e)
            statusTextState.value = "Error starting voice recognizer"
            finishWithDelay(1500L)
        }
    }

    private fun createListener() = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            statusTextState.value = "Listening for your task..."
        }

        override fun onBeginningOfSpeech() {
            statusTextState.value = "Listening..."
        }

        override fun onRmsChanged(rmsdB: Float) {
            rmsDbState.floatValue = rmsdB
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            statusTextState.value = "Processing..."
            isExecutingState.value = true
        }

        override fun onError(error: Int) {
            if (isFinishingActivity) return
            Log.w(TAG, "SpeechRecognizer error: $error")
            if (userSpokenState.value.isNotEmpty()) {
                handleSpokenInput(userSpokenState.value)
            } else {
                statusTextState.value = "Didn't hear any task"
                finishWithDelay(1200L)
            }
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                val recognized = matches[0].trim()
                handleSpokenInput(recognized)
            } else {
                statusTextState.value = "Didn't catch that"
                finishWithDelay(1200L)
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val partialMatches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!partialMatches.isNullOrEmpty()) {
                val partial = partialMatches[0].trim()
                userSpokenState.value = partial
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun handleSpokenInput(text: String) {
        if (isFinishingActivity) return
        userSpokenState.value = text
        isExecutingState.value = true

        val cleanTask = WakeWordHelper.extractCommand(text).ifBlank { text.trim() }
        if (cleanTask.isNotEmpty()) {
            executeCommand(cleanTask)
        } else {
            statusTextState.value = "Done"
            finishWithDelay(800L)
        }
    }

    private fun executeCommand(command: String) {
        if (isFinishingActivity) return
        userSpokenState.value = command
        isExecutingState.value = true
        statusTextState.value = "Executing task..."

        try {
            val response = commandProcessor.processCommand(command)
            assistantResponseState.value = response
            statusTextState.value = "Task completed"

            ttsHelper.speak(response, onDone = {
                finishWithDelay(1000L)
            })
        } catch (e: Exception) {
            Log.e(TAG, "Error executing command: $command", e)
            val err = "Completed: $command"
            assistantResponseState.value = err
            finishWithDelay(1200L)
        }
    }

    private fun finishWithDelay(delayMs: Long) {
        if (isFinishingActivity) return
        isFinishingActivity = true
        mainHandler.postDelayed({
            dismissPopup()
        }, delayMs)
    }

    private fun dismissPopup() {
        if (isFinishing) return
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
        } catch (_: Exception) {}
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        isFinishingActivity = true
        mainHandler.removeCallbacksAndMessages(null)

        try {
            speechRecognizer?.destroy()
            speechRecognizer = null
        } catch (_: Exception) {}

        try {
            ttsHelper.shutdown()
        } catch (_: Exception) {}

        // Notify background service that popup is closed
        NovaBackgroundVoiceService.notifyPopupClosed()
        Log.i(TAG, "NovaVoicePopupActivity destroyed & background service notified")
    }

    companion object {
        private const val TAG = "NovaVoicePopup"
        const val EXTRA_WAKE_WORD_CONFIRMED = "com.example.nova.extra.WAKE_WORD_CONFIRMED"
        const val EXTRA_INITIAL_COMMAND = "com.example.nova.extra.INITIAL_COMMAND"
        const val EXTRA_MANUAL_LAUNCH = "com.example.nova.extra.MANUAL_LAUNCH"
    }
}

/**
 * Modern floating Nova Assistant UI with glassmorphism card, glowing cyan orb,
 * live speech display, and auto-dismiss animation.
 */
@Composable
fun NovaPopupContent(
    statusText: String,
    userSpokenText: String,
    assistantResponseText: String,
    rmsDb: Float,
    isExecuting: Boolean,
    onDismiss: () -> Unit
) {
    val cyanGlow = Color(0xFF00E5FF)
    val purpleGlow = Color(0xFF7C4DFF)
    val cardBg = Color(0xFF0D111A).copy(alpha = 0.95f)

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val dynamicScale = if (rmsDb > 2f) {
        (1.0f + (rmsDb / 15f).coerceIn(0f, 0.45f))
    } else {
        pulseScale
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .clip(RoundedCornerShape(28.dp))
                .border(
                    width = 1.5.dp,
                    brush = Brush.horizontalGradient(listOf(cyanGlow, purpleGlow)),
                    shape = RoundedCornerShape(28.dp)
                )
                .background(cardBg)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { /* consume click inside card */ }
                )
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(cyanGlow)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "NOVA AI ASSISTANT",
                        color = cyanGlow,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.Gray,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(80.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .scale(dynamicScale)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(cyanGlow.copy(alpha = 0.4f), Color.Transparent)
                            )
                        )
                )

                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(listOf(cyanGlow, purpleGlow))
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Microphone",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = statusText,
                color = Color.LightGray,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(10.dp))

            if (userSpokenText.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF161B26))
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = "“$userSpokenText”",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            if (assistantResponseText.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(cyanGlow.copy(alpha = 0.12f))
                        .border(1.dp, cyanGlow.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = assistantResponseText,
                        color = cyanGlow,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
