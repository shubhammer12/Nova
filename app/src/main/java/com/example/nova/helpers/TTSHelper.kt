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
 * Configures ultra high-quality, crystal-clear natural speech synthesis for Shubham.
 * Delivers articulate, deliberate pacing (0.92x speed, 1.04x pitch, WaveNet/Neural voices)
 * so every single word is cleanly audible and easily understood.
 */
class TTSHelper(
    private val context: Context,
    private val onInitResult: (isSuccess: Boolean) -> Unit = {}
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isInitialized = false

    private var pendingText: String? = null
    private var pendingOnStart: (() -> Unit)? = null
    private var pendingOnDone: (() -> Unit)? = null

    // Audio clarity settings:
    // Rate: 0.92f ensures deliberate articulation without slurring or rushing syllables
    // Pitch: 1.04f provides clear, bright, friendly assistant vocal presence
    private var speechRate = 0.92f
    private var speechPitch = 1.04f
    private var voiceStyle = "clear_female"

    init {
        loadSettingsFromPrefs()
        initEngine()
    }

    private fun loadSettingsFromPrefs() {
        try {
            val prefs = context.getSharedPreferences("nova_prefs", Context.MODE_PRIVATE)
            speechRate = prefs.getFloat("voice_speed", 0.92f)
            speechPitch = prefs.getFloat("voice_pitch", 1.04f)
            voiceStyle = prefs.getString("voice_style", "clear_female") ?: "clear_female"
        } catch (e: Exception) {
            Log.w(TAG, "Could not load voice settings from prefs", e)
        }
    }

    private fun initEngine() {
        try {
            // First attempt with Google TTS for state-of-the-art WaveNet / Neural clarity
            tts = TextToSpeech(context.applicationContext, this, "com.google.android.tts")
        } catch (e: Exception) {
            Log.w(TAG, "Google TTS engine unavailable, falling back to system default", e)
            tts = TextToSpeech(context.applicationContext, this)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            setupAudioEngine()
            isInitialized = true
            Log.d(TAG, "TTS Engine successfully initialized with crystal-clear settings")
            onInitResult(true)

            // Flush pending speech if queued before initialization finished
            val pText = pendingText
            if (pText != null) {
                val pStart = pendingOnStart ?: {}
                val pDone = pendingOnDone ?: {}
                pendingText = null
                pendingOnStart = null
                pendingOnDone = null
                speakInternal(pText, pStart, pDone)
            }
        } else {
            Log.w(TAG, "Google TTS init failed (status: $status), attempting fallback to default TTS...")
            try {
                tts = TextToSpeech(context.applicationContext) { fallbackStatus ->
                    if (fallbackStatus == TextToSpeech.SUCCESS) {
                        setupAudioEngine()
                        isInitialized = true
                        onInitResult(true)
                    } else {
                        isInitialized = false
                        onInitResult(false)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Fallback TTS initialization error", e)
                isInitialized = false
                onInitResult(false)
            }
        }
    }

    private fun setupAudioEngine() {
        tts?.let { engine ->
            engine.setSpeechRate(speechRate)
            engine.setPitch(speechPitch)

            val usage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                AudioAttributes.USAGE_ASSISTANT
            } else {
                AudioAttributes.USAGE_MEDIA
            }
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(usage)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            engine.setAudioAttributes(audioAttributes)

            // Default initial locale: en-IN (or system locale if matching)
            val defaultLocale = Locale.forLanguageTag("en-IN")
            val result = engine.setLanguage(defaultLocale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                engine.setLanguage(Locale.US)
            }
            selectBestVoice(engine.voice?.locale ?: defaultLocale, voiceStyle)
        }
    }

    /**
     * Updates voice settings dynamically and immediately applies to engine.
     */
    fun applyVoiceSettings(rate: Float, pitch: Float, style: String) {
        this.speechRate = rate
        this.speechPitch = pitch
        this.voiceStyle = style

        tts?.let { engine ->
            engine.setSpeechRate(rate)
            engine.setPitch(pitch)
            val currentLoc = engine.voice?.locale ?: Locale.forLanguageTag("en-IN")
            selectBestVoice(currentLoc, style)
        }
    }

    /**
     * Dynamically adjust speech rate.
     */
    fun setSpeechRate(rate: Float) {
        this.speechRate = rate
        tts?.setSpeechRate(rate)
    }

    /**
     * Sanitizes raw text: strips markdown, emojis, awkward punctuation,
     * and expands symbols to ensure smooth, natural, crystal-clear articulation.
     */
    fun sanitizeForSpeech(rawText: String): String {
        var text = rawText
        // Strip markdown bold / italic / code
        text = text.replace(Regex("\\*\\*(.*?)\\*\\*"), "$1")
        text = text.replace(Regex("\\*(.*?)\\*"), "$1")
        text = text.replace(Regex("__(.*?)__"), "$1")
        text = text.replace(Regex("_(.*?)_"), "$1")
        text = text.replace(Regex("`{1,3}(.*?)`{1,3}"), "$1")
        text = text.replace(Regex("#+\\s*"), "")

        // Strip URLs
        text = text.replace(Regex("https?://\\S+"), "link")

        // Strip emojis to avoid weird pauses or verbal descriptions
        text = text.replace(Regex("[\\p{So}\\p{Sk}\\p{Cs}\\p{Cn}]"), " ")

        // Expand common symbols for natural flow
        text = text.replace("&", " and ")
        text = text.replace("%", " percent ")
        text = text.replace("@", " at ")
        text = text.replace("+", " plus ")
        text = text.replace("=", " equals ")

        // Strip bullet points and list dashes
        text = text.replace(Regex("^[\\s*•·▪\\-]+", RegexOption.MULTILINE), "")
        text = text.replace(Regex("\\n+"), ". ")
        text = text.replace(Regex("\\s+"), " ")

        return text.trim()
    }

    /**
     * Detects language of text (Gujarati, Hindi, or English) to route to appropriate voice.
     */
    private fun detectLanguage(text: String): Locale {
        val hasGujarati = text.any { it in '\u0A80'..'\u0AFF' }
        if (hasGujarati) return Locale.forLanguageTag("gu-IN")

        val hasHindi = text.any { it in '\u0900'..'\u097F' }
        if (hasHindi) return Locale.forLanguageTag("hi-IN")

        return Locale.forLanguageTag("en-IN")
    }

    /**
     * Intelligently selects the highest-quality, most articulate voice for the target locale.
     */
    fun selectBestVoice(targetLocale: Locale, style: String = voiceStyle) {
        try {
            val engine = tts ?: return
            val voices = engine.voices
            if (voices.isNullOrEmpty()) {
                engine.setLanguage(targetLocale)
                return
            }

            // Available voices matching target language
            var matching = voices.filter { v ->
                !v.features.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) &&
                        v.locale.language.equals(targetLocale.language, ignoreCase = true)
            }

            // Fallback to English if target language not found
            if (matching.isEmpty()) {
                matching = voices.filter { v ->
                    !v.features.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) &&
                            v.locale.language.equals("en", ignoreCase = true)
                }
            }

            if (matching.isEmpty()) {
                engine.setLanguage(targetLocale)
                return
            }

            // Score voices for maximum clarity, naturalness, and user preference
            val bestVoice = matching.maxByOrNull { v ->
                var score = 0

                // Prioritize high-quality voices
                if (v.quality == Voice.QUALITY_VERY_HIGH) score += 60
                if (v.quality == Voice.QUALITY_HIGH) score += 40
                if (v.quality == Voice.QUALITY_NORMAL) score += 15

                // Prioritize country match (e.g. IN for gu-IN, hi-IN, en-IN)
                if (!targetLocale.country.isNullOrEmpty() &&
                    v.locale.country.equals(targetLocale.country, ignoreCase = true)
                ) {
                    score += 25
                }

                val name = v.name.lowercase(Locale.ROOT)
                // Neural / Natural / WaveNet indicators in Google TTS
                if (name.contains("natural") || name.contains("neural") || name.contains("wavenet")) score += 45
                if (name.contains("network")) score += 25
                if (name.contains("local")) score += 15

                // Detect voice gender / tone from name tokens
                val isFemale = name.contains("female") || name.contains("-fem") || name.contains("-f-") ||
                        name.contains("sfg") || name.contains("enc") || name.contains("end") ||
                        name.contains("iob") || name.contains("cxx")
                val isMale = name.contains("male") || name.contains("-m-") || name.contains("tpd") ||
                        name.contains("iom")

                when (style) {
                    "clear_female" -> if (isFemale) score += 30
                    "clear_male" -> if (isMale) score += 30
                    "clear_natural" -> score += 15
                }

                // Penalize legacy robotic/compact voices
                if (name.contains("compact") || name.contains("legacy")) score -= 30

                score
            }

            if (bestVoice != null) {
                engine.voice = bestVoice
                Log.d(TAG, "Selected pristine voice: ${bestVoice.name} for ${bestVoice.locale}")
            } else {
                engine.setLanguage(targetLocale)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Voice selection exception: ${e.message}")
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
        val cleanedText = sanitizeForSpeech(text)
        if (cleanedText.isBlank()) {
            onDone()
            return
        }

        val targetLocale = detectLanguage(cleanedText)
        val engine = tts ?: return

        // Set language and select appropriate voice
        try {
            val langResult = engine.setLanguage(targetLocale)
            if (langResult != TextToSpeech.LANG_MISSING_DATA && langResult != TextToSpeech.LANG_NOT_SUPPORTED) {
                selectBestVoice(targetLocale, voiceStyle)
            } else {
                // Fallback to English
                val fallbackLocale = Locale.forLanguageTag("en-IN")
                engine.setLanguage(fallbackLocale)
                selectBestVoice(fallbackLocale, voiceStyle)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Locale setup error: ${e.message}")
        }

        // Re-apply deliberate clarity rate and pitch
        engine.setSpeechRate(speechRate)
        engine.setPitch(speechPitch)

        val utteranceId = "NOVA_SPEECH_${System.currentTimeMillis()}"

        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
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
        engine.speak(cleanedText, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    /**
     * Speaks a sample test phrase demonstrating the new clear voice.
     */
    fun speakTestVoice(customSample: String? = null, onDone: () -> Unit = {}) {
        val sample = customSample ?: "Hello Shubham! My voice is now crystal clear, natural, and pleasant. Every single word is articulate and easy to understand."
        speak(sample, onDone = onDone)
    }

    fun stop() {
        try {
            tts?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping TTS", e)
        }
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            Log.e(TAG, "Error shutting down TTS", e)
        }
        tts = null
        isInitialized = false
    }

    companion object {
        private const val TAG = "TTSHelper"
    }
}
