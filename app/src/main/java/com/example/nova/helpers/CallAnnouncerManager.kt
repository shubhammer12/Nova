package com.example.nova.helpers

import android.content.Context
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.CallLog
import android.provider.ContactsContract
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import java.util.Locale

/**
 * Singleton manager to announce incoming callers dynamically with high-quality TTS.
 * Detects phone numbers, matches device contacts dynamically, and speaks the caller's name
 * during ringing, stopping immediately when answered or hung up.
 */
object CallAnnouncerManager : TextToSpeech.OnInitListener {

    private const val TAG = "CallAnnouncerManager"
    private const val WAKE_LOCK_TAG = "Nova:CallAnnouncerWakeLock"

    private var tts: TextToSpeech? = null
    private var isTtsInitialized = false
    private var announceJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    @Volatile
    private var isRinging = false

    private var appContextRef: WeakReference<Context>? = null
    private var originalRingtoneVolume: Int = -1
    private var isDucked: Boolean = false
    private var audioManagerRef: WeakReference<android.media.AudioManager>? = null

    /**
     * Initializes TTS if not already done, prioritizing Google TTS for crystal clear voice.
     */
    fun initTts(context: Context) {
        appContextRef = WeakReference(context.applicationContext)
        if (tts == null) {
            try {
                tts = TextToSpeech(context.applicationContext, this, "com.google.android.tts")
            } catch (_: Exception) {
                tts = TextToSpeech(context.applicationContext, this)
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isTtsInitialized = true
            configureTtsAudio()
            Log.d(TAG, "CallAnnouncer TTS successfully initialized")
        } else {
            // Attempt fallback to default TTS if Google TTS engine failed
            val ctx = appContextRef?.get() ?: return
            try {
                tts = TextToSpeech(ctx) { fallbackStatus ->
                    if (fallbackStatus == TextToSpeech.SUCCESS) {
                        isTtsInitialized = true
                        configureTtsAudio()
                    } else {
                        isTtsInitialized = false
                    }
                }
            } catch (_: Exception) {
                isTtsInitialized = false
                Log.e(TAG, "CallAnnouncer TTS initialization failed with status: $status")
            }
        }
    }

    private fun configureTtsAudio() {
        tts?.let { engine ->
            // 0.92f ensures deliberate pronunciation so names and words are clearly understood
            engine.setSpeechRate(0.92f)
            // 1.04f provides clear, bright, friendly assistant vocal presence
            engine.setPitch(1.04f)

            val usage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                AudioAttributes.USAGE_ASSISTANT
            } else {
                AudioAttributes.USAGE_NOTIFICATION_RINGTONE
            }

            val audioAttributes = AudioAttributes.Builder()
                .setUsage(usage)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            engine.setAudioAttributes(audioAttributes)
        }
    }

    /**
     * Softly lowers ringtone volume during announcement so Nova's voice speaks through crystal clear.
     */
    private fun duckRingtone(context: Context) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager ?: return
            audioManagerRef = WeakReference(audioManager)
            if (!isDucked) {
                originalRingtoneVolume = audioManager.getStreamVolume(android.media.AudioManager.STREAM_RING)
                if (originalRingtoneVolume > 1) {
                    val duckedVolume = (originalRingtoneVolume * 0.35f).toInt().coerceAtLeast(1)
                    audioManager.setStreamVolume(android.media.AudioManager.STREAM_RING, duckedVolume, 0)
                    isDucked = true
                    Log.d(TAG, "Ringtone ducked from $originalRingtoneVolume to $duckedVolume for clear announcement")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not duck ringtone: ${e.message}")
        }
    }

    /**
     * Restores ringtone volume back to its original state.
     */
    private fun restoreRingtone() {
        try {
            if (isDucked && originalRingtoneVolume >= 0) {
                val audioManager = audioManagerRef?.get()
                audioManager?.setStreamVolume(android.media.AudioManager.STREAM_RING, originalRingtoneVolume, 0)
                Log.d(TAG, "Ringtone restored to original volume: $originalRingtoneVolume")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not restore ringtone: ${e.message}")
        } finally {
            isDucked = false
            originalRingtoneVolume = -1
        }
    }

    /**
     * Called when an incoming call starts ringing.
     */
    fun onCallRinging(context: Context, rawNumber: String?) {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences("nova_prefs", Context.MODE_PRIVATE)
        val isEnabled = prefs.getBoolean("call_announcer_enabled", true)
        if (!isEnabled) {
            Log.d(TAG, "Call Announcer is disabled in settings")
            return
        }

        isRinging = true
        acquireWakeLock(appContext)
        initTts(appContext)
        duckRingtone(appContext)

        announceJob?.cancel()
        announceJob = scope.launch {
            // Find caller identity dynamically
            val callerIdentity = resolveCallerIdentity(appContext, rawNumber)
            val langCode = prefs.getString("call_announcer_language", "gu") ?: "gu"
            val repeatCount = prefs.getInt("call_announcer_repeats", 2).coerceIn(1, 4)

            val announcement = buildAnnouncementText(callerIdentity, langCode)
            Log.d(TAG, "Announcing incoming call: '$announcement' (repeats: $repeatCount, lang: $langCode)")

            // Wait a moment for ringtone and TTS engine ready
            var waitCount = 0
            while ((!isTtsInitialized) && waitCount < 15 && isRinging) {
                delay(200L)
                waitCount++
            }

            if (!isRinging) {
                restoreRingtone()
                return@launch
            }

            applyLanguage(langCode)

            repeat(repeatCount) {
                if (!isRinging) return@repeat

                speakText(announcement)

                // Wait while speaking + pause before next repeat
                delay(4000L)
            }

            // After speaking all repetitions, restore normal ringtone volume
            restoreRingtone()
        }
    }

    /**
     * Called when the call is answered (OFFHOOK) or rejected/ended (IDLE).
     */
    fun onCallEndedOrAnswered() {
        isRinging = false
        announceJob?.cancel()
        announceJob = null
        stopSpeaking()
        restoreRingtone()
        releaseWakeLock()
        Log.d(TAG, "Call ringing stopped. Audio halted and ringtone restored.")
    }

    /**
     * Immediately stop any in-progress speech.
     */
    fun stopSpeaking() {
        try {
            tts?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping TTS", e)
        }
    }

    /**
     * Previews/tests the announcement from Settings.
     */
    fun testAnnouncement(context: Context, sampleName: String, langCode: String) {
        val appContext = context.applicationContext
        initTts(appContext)

        scope.launch {
            var waitCount = 0
            while ((!isTtsInitialized) && waitCount < 15) {
                delay(200L)
                waitCount++
            }
            applyLanguage(langCode)
            val text = buildAnnouncementText(CallerIdentity(name = sampleName, isKnownContact = true), langCode)
            speakText(text)
        }
    }

    private fun applyLanguage(langCode: String) {
        tts?.let { engine ->
            val targetLocale = when (langCode) {
                "gu" -> Locale.forLanguageTag("gu-IN")
                "hi" -> Locale.forLanguageTag("hi-IN")
                else -> Locale.forLanguageTag("en-IN")
            }

            val result = engine.setLanguage(targetLocale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w(TAG, "Locale $targetLocale not fully supported, falling back to English/Hindi")
                if (langCode == "gu") {
                    val fallbackHi = engine.setLanguage(Locale.forLanguageTag("hi-IN"))
                    if (fallbackHi == TextToSpeech.LANG_MISSING_DATA || fallbackHi == TextToSpeech.LANG_NOT_SUPPORTED) {
                        engine.setLanguage(Locale.forLanguageTag("en-IN"))
                    }
                } else {
                    engine.setLanguage(Locale.forLanguageTag("en-IN"))
                }
            }

            selectBestVoice(targetLocale)
        }
    }

    private fun selectBestVoice(targetLocale: Locale) {
        try {
            val engine = tts ?: return
            val voices = engine.voices ?: return
            val bestVoice = voices.filter { v ->
                !v.features.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) &&
                        v.locale.language.equals(targetLocale.language, ignoreCase = true)
            }.maxByOrNull { v ->
                var score = 0
                if (v.quality == Voice.QUALITY_VERY_HIGH) score += 50
                if (v.quality == Voice.QUALITY_HIGH) score += 30
                val name = v.name.lowercase(Locale.ROOT)
                if (name.contains("natural") || name.contains("neural") || name.contains("wavenet")) score += 40
                if (name.contains("network")) score += 20
                if (name.contains("local")) score += 10
                if (name.contains("female") || name.contains("sfg") || name.contains("enc") || name.contains("end")) score += 20
                score
            }
            if (bestVoice != null) {
                engine.voice = bestVoice
            }
        } catch (e: Exception) {
            Log.w(TAG, "CallAnnouncer voice selection exception: ${e.message}")
        }
    }

    private fun speakText(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "CALL_ANNOUNCE_${System.currentTimeMillis()}")
    }

    /**
     * Dynamically searches the phone's contacts database for the incoming phone number.
     */
    private fun resolveCallerIdentity(context: Context, rawNumber: String?): CallerIdentity {
        var number = rawNumber?.trim()

        // Fallback: If intent didn't carry number, query recent CallLog
        if (number.isNullOrEmpty()) {
            number = getRecentCallLogNumber(context)
        }

        if (number.isNullOrEmpty()) {
            return CallerIdentity(name = null, isKnownContact = false, formattedNumber = null)
        }

        // 1. Primary Lookup: ContactsContract.PhoneLookup
        try {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
            val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        val contactName = cursor.getString(nameIndex)?.trim()
                        if (!contactName.isNullOrEmpty()) {
                            return CallerIdentity(name = contactName, isKnownContact = true, formattedNumber = number)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in PhoneLookup", e)
        }

        // 2. Fallback: Search CommonDataKinds.Phone with normalized digit matching
        val digitsOnly = number.filter { it.isDigit() }
        val last10Digits = if (digitsOnly.length >= 10) digitsOnly.takeLast(10) else digitsOnly

        if (last10Digits.isNotEmpty()) {
            try {
                val cursor = context.contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                        ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ),
                    null,
                    null,
                    null
                )
                cursor?.use {
                    val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    val numIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    while (it.moveToNext()) {
                        val cName = it.getString(nameIdx)?.trim()
                        val cNum = it.getString(numIdx)?.trim()
                        if (!cName.isNullOrEmpty() && !cNum.isNullOrEmpty()) {
                            val cDigits = cNum.filter { ch -> ch.isDigit() }
                            if (cDigits.endsWith(last10Digits)) {
                                return CallerIdentity(name = cName, isKnownContact = true, formattedNumber = number)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in CommonDataKinds fallback", e)
            }
        }

        return CallerIdentity(name = null, isKnownContact = false, formattedNumber = number)
    }

    private fun getRecentCallLogNumber(context: Context): String? {
        return try {
            val cursor = context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME, CallLog.Calls.DATE),
                null,
                null,
                "${CallLog.Calls.DATE} DESC"
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    val numIndex = it.getColumnIndex(CallLog.Calls.NUMBER)
                    val dateIndex = it.getColumnIndex(CallLog.Calls.DATE)
                    if (numIndex != -1 && dateIndex != -1) {
                        val callDate = it.getLong(dateIndex)
                        // If call was registered in the last 15 seconds
                        if (System.currentTimeMillis() - callDate < 15000) {
                            return it.getString(numIndex)
                        }
                    }
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error reading CallLog fallback", e)
            null
        }
    }

    /**
     * Builds natural, crystal-clear announcement phrase based on selected language.
     */
    private fun buildAnnouncementText(identity: CallerIdentity, langCode: String): String {
        val name = identity.name
        val number = identity.formattedNumber

        return if (name != null) {
            when (langCode) {
                "gu" -> "$name નો ફોન આવી રહ્યો છે"
                "hi" -> "$name का फोन आ रहा है"
                else -> "Incoming call from $name"
            }
        } else if (!number.isNullOrEmpty()) {
            val spacedDigits = number.filter { it.isDigit() }.chunked(1).joinToString(" ")
            when (langCode) {
                "gu" -> "અજાણ્યા નંબર પરથી કોલ આવી રહ્યો છે. $spacedDigits"
                "hi" -> "अज्ञात नंबर से कॉल आ रहा है. $spacedDigits"
                else -> "Incoming call from unknown number $spacedDigits"
            }
        } else {
            when (langCode) {
                "gu" -> "અજાણ્યા કોલર તરફથી ઇનકમિંગ કોલ આવી રહ્યો છે"
                "hi" -> "अज्ञात कॉलर से इनकमिंग कॉल आ रहा है"
                else -> "Incoming call from unknown caller"
            }
        }
    }

    private fun acquireWakeLock(context: Context) {
        try {
            if (wakeLock == null) {
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                wakeLock = powerManager?.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    WAKE_LOCK_TAG
                )
            }
            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire(35000L) // 35 seconds max
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error acquiring WakeLock", e)
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing WakeLock", e)
        }
    }

    data class CallerIdentity(
        val name: String?,
        val isKnownContact: Boolean,
        val formattedNumber: String? = null
    )
}
