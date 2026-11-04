package com.example.nova.helpers

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import java.util.Locale

/**
 * Helper class to manage Text-To-Speech (TTS) engine.
 * Configures high-quality, crystal-clear natural speech synthesis for Shubham.
 */
class TTSHelper(
    context: Context,
    private val onInitResult: (isSuccess: Boolean) -> Unit = {}
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = TextToSpeech(context, this)
    private var isInitialized = false

    private var pendingText: String? = null
    private var pendingOnStart: (() -> Unit)? = null
    private var pendingOnDone: (() -> Unit)? = null

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.US)
            isInitialized = (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED)

            if (isInitialized) {
                // Set speech rate to 1.0f for crystal clear, natural, full-name articulation
                tts?.setSpeechRate(1.0f)
                tts?.setPitch(1.0f)

                // Configure dedicated speech audio attributes
                val usage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    AudioAttributes.USAGE_ASSISTANT
                } else {
                    AudioAttributes.USAGE_MEDIA
                }
                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(usage)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                tts?.setAudioAttributes(audioAttributes)

                // Select Highest Quality Voice available on device
                selectHighQualityVoice()

                // Execute pending speech if queued before initialization finished
                val pText = pendingText
                if (pText != null) {
                    val pStart = pendingOnStart ?: {}
                    val pDone = pendingOnDone ?: {}
                    pendingText = null
                    pendingOnStart = null
                    pendingOnDone = null
                    speakInternal(pText, pStart, pDone)
                }
            }

            Log.d(TAG, "TTS Initialized with high quality settings: $isInitialized")
            onInitResult(isInitialized)
        } else {
            Log.e(TAG, "TTS Initialization failed with status: $status")
            isInitialized = false
            onInitResult(false)
        }
    }

    /**
     * Dynamically adjust speech rate (e.g., 1.0f for normal clear speed).
     */
    fun setSpeechRate(rate: Float) {
        tts?.setSpeechRate(rate)
    }

    /**
     * Finds and selects the highest quality natural voice on the device.
     */
    private fun selectHighQualityVoice() {
        try {
            val voices = tts?.voices
            if (!voices.isNullOrEmpty()) {
                val bestVoice = voices.firstOrNull { voice ->
                    val name = voice.name.lowercase(Locale.US)
                    voice.locale.language == Locale.US.language &&
                            (voice.quality == Voice.QUALITY_VERY_HIGH || voice.quality == Voice.QUALITY_HIGH) &&
                            (name.contains("local") || name.contains("network") || name.contains("female") || name.contains("en-us"))
                } ?: voices.firstOrNull { voice ->
                    voice.locale.language == Locale.US.language &&
                            (voice.quality == Voice.QUALITY_VERY_HIGH || voice.quality == Voice.QUALITY_HIGH)
                } ?: voices.firstOrNull { voice ->
                    voice.locale.language == Locale.ENGLISH.language
                }

                if (bestVoice != null) {
                    tts?.voice = bestVoice
                    Log.d(TAG, "Selected High Quality Voice: ${bestVoice.name}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not set custom voice profile: ${e.localizedMessage}")
        }
    }

    /**
     * Speaks the specified text string clearly with optional listeners.
     * Auto-queues speech if TTS is still initializing.
     */
    fun speak(
        text: String,
        onStart: () -> Unit = {},
        onDone: () -> Unit = {}
    ) {
        if (!isInitialized || tts == null) {
            Log.d(TAG, "TTS not initialized yet. Queueing speech: $text")
            pendingText = text
            pendingOnStart = onStart
            pendingOnDone = onDone
            return
        }

        speakInternal(text, onStart, onDone)
    }

    private fun speakInternal(
        text: String,
        onStart: () -> Unit,
        onDone: () -> Unit
    ) {
        val utteranceId = "NOVA_SPEECH_${System.currentTimeMillis()}"

        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {
                if (id == utteranceId) {
                    onStart()
                }
            }

            override fun onDone(id: String?) {
                if (id == utteranceId) {
                    onDone()
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) {
                if (id == utteranceId) {
                    onDone()
                }
            }
        })

        onStart()
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }

    companion object {
        private const val TAG = "TTSHelper"
    }
}
