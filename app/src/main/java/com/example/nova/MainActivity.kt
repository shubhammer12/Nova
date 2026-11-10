package com.example.nova

import android.app.NotificationManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.example.nova.helpers.BiometricHelper
import com.example.nova.receiver.NovaDeviceAdminReceiver
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nova.helpers.ActionExecutor
import com.example.nova.helpers.CommandProcessor
import com.example.nova.helpers.SpeechHelper
import com.example.nova.helpers.TTSHelper
import com.example.nova.helpers.CallAnnouncerManager
import com.example.nova.presentation.ui.BottomNavigationBar
import com.example.nova.presentation.ui.HistoryItem
import com.example.nova.presentation.ui.HistoryScreen
import com.example.nova.presentation.ui.NovaScreen
import com.example.nova.presentation.ui.SettingsScreen
import com.example.nova.presentation.ui.ProfileScreen
import org.json.JSONArray
import org.json.JSONObject
import com.example.nova.ui.theme.NovaTheme
import com.example.nova.utils.PermissionUtils
import kotlinx.coroutines.delay

enum class AssistantState {
    IDLE,
    LISTENING,
    PROCESSING,
    SPEAKING,
    ERROR
}

class MainActivity : ComponentActivity(), SpeechHelper.SpeechStateListener {

    private lateinit var ttsHelper: TTSHelper
    private lateinit var speechHelper: SpeechHelper
    private lateinit var actionExecutor: ActionExecutor
    private lateinit var commandProcessor: CommandProcessor

    // UI state variables
    private val assistantState = mutableStateOf(AssistantState.IDLE)
    private val userSpokenText = mutableStateOf("")
    private val assistantResponseText = mutableStateOf("")
    private val statusMessage = mutableStateOf("Tap the microphone button to start")
    private val hasPermissionsState = mutableStateOf(false)
    private val currentRmsDb = mutableStateOf(0f)
    private val historyListState = mutableStateOf<List<HistoryItem>>(emptyList())

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        hasPermissionsState.value = allGranted
        if (allGranted) {
            Toast.makeText(this, "All permissions granted!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(
                this,
                "Permissions required for voice recognition, contacts, and calls.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request 120Hz High Refresh Rate on Supported Displays (OnePlus Nord CE 4, etc.)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val maxMode = window.context.display?.supportedModes?.maxByOrNull { it.refreshRate }
                if (maxMode != null) {
                    val lp = window.attributes
                    lp.preferredDisplayModeId = maxMode.modeId
                    window.attributes = lp
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                @Suppress("DEPRECATION")
                val lp = window.attributes
                lp.preferredRefreshRate = 120f
                window.attributes = lp
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to set 120Hz display refresh rate", e)
        }

        enableEdgeToEdge()

        actionExecutor = ActionExecutor(this)
        commandProcessor = CommandProcessor(actionExecutor)
        speechHelper = SpeechHelper(this, this)
        ttsHelper = TTSHelper(this)
        CallAnnouncerManager.initTts(this)

        hasPermissionsState.value = PermissionUtils.hasAllPermissions(this)
        historyListState.value = loadHistoryFromPrefs(this)

        // Clear any old/lingering foreground notifications immediately and disable background mode
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.cancelAll()
        } catch (_: Exception) {}

        getSharedPreferences("nova_prefs", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("background_voice_enabled", false)
            .apply()

        setContent {
            var activeNavScreen by remember { mutableStateOf(NovaScreen.ASSISTANT) }

            val prefs = remember { getSharedPreferences("nova_prefs", Context.MODE_PRIVATE) }
            val dpm = remember { getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager }
            val adminComp = remember { ComponentName(this@MainActivity, NovaDeviceAdminReceiver::class.java) }

            val initialProfileName = remember { prefs.getString("profile_custom_name", "Shubham") ?: "Shubham" }
            val initialHomeName = remember { prefs.getString("home_custom_name", "Shubham") ?: "Shubham" }
            val initialHomeEmoji = remember { prefs.getString("home_custom_emoji", "👋") ?: "👋" }

            var profileNameState by remember { mutableStateOf(initialProfileName) }
            var homeNameState by remember { mutableStateOf(initialHomeName) }
            var homeEmojiState by remember { mutableStateOf(initialHomeEmoji) }

            val initialProfileUri = remember { prefs.getString("profile_image_uri", null) }
            var profileImageUriState by remember { mutableStateOf(initialProfileUri) }
            var profileBitmapState by remember {
                mutableStateOf(initialProfileUri?.let { loadBitmapFromUri(this@MainActivity, it) })
            }

            val profileGalleryLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.GetContent()
            ) { uri: Uri? ->
                if (uri != null) {
                    try {
                        contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    } catch (e: Exception) {
                        // Ignore
                    }
                    val uriStr = uri.toString()
                    prefs.edit().putString("profile_image_uri", uriStr).apply()
                    profileImageUriState = uriStr
                    profileBitmapState = loadBitmapFromUri(this@MainActivity, uriStr)
                    Toast.makeText(this@MainActivity, "Profile photo updated!", Toast.LENGTH_SHORT).show()
                }
            }

            val initialCallAnnouncerEnabled = remember { prefs.getBoolean("call_announcer_enabled", true) }
            val initialCallAnnouncerLang = remember { prefs.getString("call_announcer_language", "gu") ?: "gu" }
            val initialCallAnnouncerRepeats = remember { prefs.getInt("call_announcer_repeats", 2) }

            var callAnnouncerEnabledState by remember { mutableStateOf(initialCallAnnouncerEnabled) }
            var callAnnouncerLangState by remember { mutableStateOf(initialCallAnnouncerLang) }
            var callAnnouncerRepeatsState by remember { mutableStateOf(initialCallAnnouncerRepeats) }

            val initialVoiceStyle = remember { prefs.getString("voice_style", "clear_female") ?: "clear_female" }
            val initialVoiceSpeed = remember { prefs.getFloat("voice_speed", 0.92f) }
            val initialVoicePitch = remember { prefs.getFloat("voice_pitch", 1.04f) }

            var voiceStyleState by remember { mutableStateOf(initialVoiceStyle) }
            var voiceSpeedState by remember { mutableStateOf(initialVoiceSpeed) }
            var voicePitchState by remember { mutableStateOf(initialVoicePitch) }

            val initialProtectionActive = remember {
                dpm.isAdminActive(adminComp) || prefs.getBoolean("uninstall_protection_enabled", false)
            }
            var uninstallProtectionState by remember { mutableStateOf(initialProtectionActive) }

            fun refreshAllSettingsStates() {
                val adminActive = dpm.isAdminActive(adminComp)
                val storedProtection = prefs.getBoolean("uninstall_protection_enabled", false)
                uninstallProtectionState = adminActive || storedProtection
                if (adminActive && !storedProtection) {
                    prefs.edit().putBoolean("uninstall_protection_enabled", true).apply()
                }
                callAnnouncerEnabledState = prefs.getBoolean("call_announcer_enabled", true)
                callAnnouncerLangState = prefs.getString("call_announcer_language", "gu") ?: "gu"
                callAnnouncerRepeatsState = prefs.getInt("call_announcer_repeats", 2)
                voiceStyleState = prefs.getString("voice_style", "clear_female") ?: "clear_female"
                voiceSpeedState = prefs.getFloat("voice_speed", 0.92f)
                voicePitchState = prefs.getFloat("voice_pitch", 1.04f)
            }

            DisposableEffect(this@MainActivity) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        refreshAllSettingsStates()
                    }
                }
                this@MainActivity.lifecycle.addObserver(observer)
                onDispose {
                    this@MainActivity.lifecycle.removeObserver(observer)
                }
            }

            LaunchedEffect(Unit) {
                refreshAllSettingsStates()
            }

            fun saveAssistantVoiceStyle(style: String) {
                prefs.edit().putString("voice_style", style).apply()
                voiceStyleState = style
                ttsHelper.applyVoiceSettings(voiceSpeedState, voicePitchState, style)
                Toast.makeText(this@MainActivity, "Voice tone updated!", Toast.LENGTH_SHORT).show()
            }

            fun saveAssistantVoiceSpeed(speed: Float) {
                prefs.edit().putFloat("voice_speed", speed).apply()
                voiceSpeedState = speed
                ttsHelper.applyVoiceSettings(speed, voicePitchState, voiceStyleState)
            }

            fun saveAssistantVoicePitch(pitch: Float) {
                prefs.edit().putFloat("voice_pitch", pitch).apply()
                voicePitchState = pitch
                ttsHelper.applyVoiceSettings(voiceSpeedState, pitch, voiceStyleState)
            }

            fun testAssistantVoice() {
                assistantState.value = AssistantState.SPEAKING
                statusMessage.value = "Nova speaking..."
                ttsHelper.speakTestVoice(
                    onDone = {
                        assistantState.value = AssistantState.IDLE
                        statusMessage.value = "Tap the microphone button to start"
                    }
                )
            }

            fun toggleCallAnnouncer(enabled: Boolean) {
                if (enabled) {
                    val missing = PermissionUtils.getMissingCallPermissions(this@MainActivity).toTypedArray()
                    if (missing.isNotEmpty()) {
                        permissionLauncher.launch(missing)
                    }
                }
                prefs.edit().putBoolean("call_announcer_enabled", enabled).apply()
                callAnnouncerEnabledState = enabled
            }

            fun saveCallAnnouncerLanguage(lang: String) {
                prefs.edit().putString("call_announcer_language", lang).apply()
                callAnnouncerLangState = lang
            }

            fun saveCallAnnouncerRepeats(repeats: Int) {
                prefs.edit().putInt("call_announcer_repeats", repeats).apply()
                callAnnouncerRepeatsState = repeats
            }

            fun saveProfileName(newName: String) {
                val clean = newName.trim().ifEmpty { "Shubham" }
                prefs.edit().putString("profile_custom_name", clean).apply()
                profileNameState = clean
                Toast.makeText(this@MainActivity, "Profile name updated to $clean", Toast.LENGTH_SHORT).show()
            }

            fun saveHomeName(newName: String) {
                val clean = newName.trim().ifEmpty { "Shubham" }
                prefs.edit().putString("home_custom_name", clean).apply()
                homeNameState = clean
                Toast.makeText(this@MainActivity, "Home name updated to $clean", Toast.LENGTH_SHORT).show()
            }

            fun saveHomeEmoji(newEmoji: String) {
                val clean = newEmoji.trim().ifEmpty { "👋" }
                prefs.edit().putString("home_custom_emoji", clean).apply()
                homeEmojiState = clean
                Toast.makeText(this@MainActivity, "Emoji updated to $clean", Toast.LENGTH_SHORT).show()
            }

            NovaTheme {
                Scaffold(
                    bottomBar = {
                        BottomNavigationBar(
                            currentScreen = activeNavScreen,
                            onScreenSelected = { activeNavScreen = it },
                            profileBitmap = profileBitmapState
                        )
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        when (activeNavScreen) {
                            NovaScreen.ASSISTANT -> {
                                VoiceAssistantScreen(
                                    assistantState = assistantState.value,
                                    userSpokenText = userSpokenText.value,
                                    assistantResponseText = assistantResponseText.value,
                                    statusMessage = statusMessage.value,
                                    hasPermissions = hasPermissionsState.value,
                                    rmsDb = currentRmsDb.value,
                                    userName = homeNameState,
                                    userEmoji = homeEmojiState,
                                    onUpdateName = { saveHomeName(it) },
                                    onUpdateEmoji = { saveHomeEmoji(it) },
                                    onRequestPermissions = { requestPermissions() },
                                    onMicButtonClick = { toggleListening() },
                                    onOpenProfile = {
                                        activeNavScreen = NovaScreen.PROFILE
                                    },
                                    onSuggestionClick = { command ->
                                        onSpeechResult(command)
                                    }
                                )
                            }
                            NovaScreen.HISTORY -> {
                                HistoryScreen(
                                    historyList = historyListState.value,
                                    onClearHistory = {
                                        historyListState.value = emptyList()
                                        saveHistoryToPrefs(this@MainActivity, emptyList())
                                        userSpokenText.value = ""
                                        assistantResponseText.value = ""
                                        Toast.makeText(this@MainActivity, "History cleared!", Toast.LENGTH_SHORT).show()
                                    },
                                    onSpeakMessage = { text ->
                                        speakResponse(text)
                                    }
                                )
                            }
                            NovaScreen.SETTINGS -> {
                                SettingsScreen(
                                    onClearHistory = {
                                        historyListState.value = emptyList()
                                        saveHistoryToPrefs(this@MainActivity, emptyList())
                                        userSpokenText.value = ""
                                        assistantResponseText.value = ""
                                        Toast.makeText(this@MainActivity, "Conversation memory cleared!", Toast.LENGTH_SHORT).show()
                                    },
                                    isUninstallProtectionEnabled = uninstallProtectionState,
                                    onToggleUninstallProtection = { enabled ->
                                        if (enabled) {
                                            prefs.edit().putBoolean("uninstall_protection_enabled", true).apply()
                                            uninstallProtectionState = true
                                            if (!dpm.isAdminActive(adminComp)) {
                                                try {
                                                    val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                                                        putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComp)
                                                        putExtra(
                                                            DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                                                            "Protect Nova against unauthorized uninstallation. Display fingerprint scan required to uninstall."
                                                        )
                                                    }
                                                    startActivity(intent)
                                                } catch (e: Exception) {
                                                    Toast.makeText(this@MainActivity, "Could not open Device Admin settings", Toast.LENGTH_SHORT).show()
                                                }
                                            } else {
                                                Toast.makeText(this@MainActivity, "Nova Uninstall Protection is Active!", Toast.LENGTH_SHORT).show()
                                            }
                                        } else {
                                            val bioHelper = BiometricHelper(this@MainActivity)
                                            bioHelper.authenticate(
                                                title = "Disable Uninstall Protection",
                                                subtitle = "Scan your display fingerprint to turn off uninstall protection",
                                                negativeButtonText = "Cancel",
                                                onSuccess = {
                                                    try {
                                                        dpm.removeActiveAdmin(adminComp)
                                                    } catch (_: Exception) {}
                                                    prefs.edit().putBoolean("uninstall_protection_enabled", false).apply()
                                                    uninstallProtectionState = false
                                                    Toast.makeText(this@MainActivity, "Uninstall protection disabled.", Toast.LENGTH_SHORT).show()
                                                },
                                                onError = { err ->
                                                    uninstallProtectionState = true
                                                    Toast.makeText(this@MainActivity, "Fingerprint verification required: $err", Toast.LENGTH_SHORT).show()
                                                }
                                            )
                                        }
                                    },
                                    isCallAnnouncerEnabled = callAnnouncerEnabledState,
                                    onToggleCallAnnouncer = { enabled ->
                                        toggleCallAnnouncer(enabled)
                                    },
                                    callAnnouncerLanguage = callAnnouncerLangState,
                                    onChangeCallAnnouncerLanguage = { lang ->
                                        saveCallAnnouncerLanguage(lang)
                                    },
                                    callAnnouncerRepeats = callAnnouncerRepeatsState,
                                    onChangeCallAnnouncerRepeats = { repeats ->
                                        saveCallAnnouncerRepeats(repeats)
                                    },
                                    onTestCallAnnouncement = {
                                        val sample = when (callAnnouncerLangState) {
                                            "gu" -> "રાહુલ પટેલ"
                                            "hi" -> "राहुल पटेल"
                                            else -> "Rahul Patel"
                                        }
                                        CallAnnouncerManager.testAnnouncement(
                                            this@MainActivity,
                                            sampleName = sample,
                                            langCode = callAnnouncerLangState
                                        )
                                    },
                                    assistantVoiceStyle = voiceStyleState,
                                    onChangeAssistantVoiceStyle = { style ->
                                        saveAssistantVoiceStyle(style)
                                    },
                                    assistantVoiceSpeed = voiceSpeedState,
                                    onChangeAssistantVoiceSpeed = { speed ->
                                        saveAssistantVoiceSpeed(speed)
                                    },
                                    assistantVoicePitch = voicePitchState,
                                    onChangeAssistantVoicePitch = { pitch ->
                                        saveAssistantVoicePitch(pitch)
                                    },
                                    onTestAssistantVoice = {
                                        testAssistantVoice()
                                    },
                                    onShareApp = {
                                        actionExecutor.shareNovaApk()
                                    }
                                )
                            }
                            NovaScreen.PROFILE -> {
                                ProfileScreen(
                                    userName = profileNameState,
                                    profileBitmap = profileBitmapState,
                                    onUpdateName = { newName ->
                                        saveProfileName(newName)
                                        saveHomeName(newName)
                                    },
                                    onChangePhoto = {
                                        profileGalleryLauncher.launch("image/*")
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun checkPermissions(): Boolean {
        val granted = PermissionUtils.hasAllPermissions(this)
        hasPermissionsState.value = granted
        return granted
    }

    private fun requestPermissions() {
        val missing = PermissionUtils.getMissingPermissions(this).toTypedArray()
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing)
        }
    }

    private fun performHapticClick() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? android.os.VibratorManager
                vm?.defaultVibrator?.vibrate(android.os.VibrationEffect.createPredefined(android.os.VibrationEffect.EFFECT_CLICK))
            } else {
                @Suppress("DEPRECATION")
                val v = getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v?.vibrate(android.os.VibrationEffect.createOneShot(35, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    v?.vibrate(35)
                }
            }
        } catch (_: Exception) {}
    }

    private fun performHapticSuccess() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? android.os.VibratorManager
                vm?.defaultVibrator?.vibrate(android.os.VibrationEffect.createPredefined(android.os.VibrationEffect.EFFECT_DOUBLE_CLICK))
            } else {
                @Suppress("DEPRECATION")
                val v = getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v?.vibrate(android.os.VibrationEffect.createWaveform(longArrayOf(0, 25, 40, 35), -1))
                } else {
                    @Suppress("DEPRECATION")
                    v?.vibrate(longArrayOf(0, 25, 40, 35), -1)
                }
            }
        } catch (_: Exception) {}
    }

    private fun toggleListening() {
        if (!checkPermissions()) {
            requestPermissions()
            return
        }

        performHapticClick()

        if (assistantState.value == AssistantState.LISTENING) {
            speechHelper.stopListening()
            assistantState.value = AssistantState.IDLE
            statusMessage.value = "Tap the microphone button to start"
        } else {
            ttsHelper.stop()
            userSpokenText.value = ""
            assistantResponseText.value = ""
            speechHelper.startListening()
        }
    }

    private fun speakResponse(text: String, onDone: () -> Unit = {}) {
        assistantState.value = AssistantState.SPEAKING
        statusMessage.value = "Assistant speaking..."
        ttsHelper.speak(
            text = text,
            onStart = {
                assistantState.value = AssistantState.SPEAKING
                statusMessage.value = "Assistant speaking..."
            },
            onDone = {
                assistantState.value = AssistantState.IDLE
                statusMessage.value = "Tap the microphone button to start"
                onDone()
            }
        )
    }

    // --- SpeechHelper.SpeechStateListener callbacks ---

    override fun onReadyForSpeech() {
        performHapticClick()
        assistantState.value = AssistantState.LISTENING
        statusMessage.value = "Listening... Speak now!"
    }

    override fun onBeginningOfSpeech() {
        statusMessage.value = "Hearing you speak..."
    }

    override fun onRmsChanged(rmsdB: Float) {
        currentRmsDb.value = rmsdB
    }

    override fun onSpeechResult(text: String) {
        performHapticSuccess()
        userSpokenText.value = text
        assistantState.value = AssistantState.PROCESSING
        statusMessage.value = "Processing command..."

        val response = commandProcessor.processCommand(text)
        assistantResponseText.value = response

        val newItem = HistoryItem(userText = text, novaResponse = response)
        val updatedList = listOf(newItem) + historyListState.value
        historyListState.value = updatedList
        saveHistoryToPrefs(this, updatedList)

        speakResponse(response)
    }

    override fun onError(errorMessage: String) {
        assistantState.value = AssistantState.ERROR
        statusMessage.value = errorMessage
        assistantResponseText.value = errorMessage
        speakResponse(errorMessage)
    }

    override fun onEndOfSpeech() {
        if (assistantState.value == AssistantState.LISTENING) {
            assistantState.value = AssistantState.PROCESSING
            statusMessage.value = "Processing command..."
        }
    }

    override fun onResume() {
        super.onResume()
        val hasPerms = PermissionUtils.hasAllPermissions(this)
        hasPermissionsState.value = hasPerms
    }

    override fun onDestroy() {
        super.onDestroy()
        speechHelper.destroy()
        ttsHelper.shutdown()
    }
}

fun loadBitmapFromUri(context: Context, uriString: String): ImageBitmap? {
    return try {
        val uri = Uri.parse(uriString)
        val inputStream = context.contentResolver.openInputStream(uri)
        val bitmap = BitmapFactory.decodeStream(inputStream)
        inputStream?.close()
        bitmap?.asImageBitmap()
    } catch (e: Exception) {
        null
    }
}

fun saveHistoryToPrefs(context: Context, historyList: List<HistoryItem>) {
    val jsonArray = JSONArray()
    for (item in historyList) {
        val obj = JSONObject().apply {
            put("userText", item.userText)
            put("novaResponse", item.novaResponse)
            put("timestamp", item.timestamp)
        }
        jsonArray.put(obj)
    }
    val prefs = context.getSharedPreferences("nova_prefs", Context.MODE_PRIVATE)
    prefs.edit().putString("nova_history_list", jsonArray.toString()).apply()
}

fun loadHistoryFromPrefs(context: Context): List<HistoryItem> {
    val prefs = context.getSharedPreferences("nova_prefs", Context.MODE_PRIVATE)
    val jsonString = prefs.getString("nova_history_list", null) ?: return emptyList()
    val list = mutableListOf<HistoryItem>()
    try {
        val jsonArray = JSONArray(jsonString)
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            list.add(
                HistoryItem(
                    userText = obj.optString("userText", ""),
                    novaResponse = obj.optString("novaResponse", ""),
                    timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                )
            )
        }
    } catch (e: Exception) {
        Log.e("History", "Error loading history", e)
    }
    return list
}

@Composable
fun EditNameDialog(
    currentName: String,
    onSaveName: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var textValue by remember { mutableStateOf(currentName) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(120)
        focusRequester.requestFocus()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF161A26),
        title = {
            Text(
                text = "Edit Your Name",
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontSize = 18.sp
            )
        },
        text = {
            Column {
                Text(
                    text = "Enter your preferred name below:",
                    color = Color(0xFF00E5FF),
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = textValue,
                    onValueChange = { textValue = it },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF00E5FF),
                        unfocusedBorderColor = Color.Gray,
                        focusedLabelColor = Color(0xFF00E5FF)
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val clean = textValue.trim()
                    if (clean.isNotEmpty()) {
                        onSaveName(clean)
                    }
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF))
            ) {
                Text("Save Name", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2B2E3C))
            ) {
                Text("Cancel", color = Color.White)
            }
        }
    )
}

@Composable
fun EditEmojiDialog(
    currentEmoji: String,
    onSaveEmoji: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var textValue by remember { mutableStateOf(currentEmoji) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(120)
        focusRequester.requestFocus()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF161A26),
        title = {
            Text(
                text = "Choose Your Emoji",
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontSize = 18.sp
            )
        },
        text = {
            Column {
                Text(
                    text = "Type or select any emoji from your keyboard:",
                    color = Color(0xFF00E5FF),
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = textValue,
                    onValueChange = { textValue = it },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF00E5FF),
                        unfocusedBorderColor = Color.Gray,
                        focusedLabelColor = Color(0xFF00E5FF)
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val clean = textValue.trim()
                    if (clean.isNotEmpty()) {
                        onSaveEmoji(clean)
                    }
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF))
            ) {
                Text("Save Emoji", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2B2E3C))
            ) {
                Text("Cancel", color = Color.White)
            }
        }
    )
}

enum class EdgeLightingState {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING,
    STOPPED
}

@Composable
fun HomeNeonBorderCanvas(
    state: EdgeLightingState = EdgeLightingState.IDLE,
    rmsDb: Float = 0f,
    modifier: Modifier = Modifier
) {
    val electricCyan = Color(0xFF00E5FF)
    val deepBlue = Color(0xFF00B0FF)
    val violet = Color(0xFF9D4EDD)
    val purple = Color(0xFF7C4DFF)
    val magenta = Color(0xFFFF007F)
    val teal = Color(0xFF00BFA5)

    val density = LocalDensity.current

    val infiniteTransition = rememberInfiniteTransition(label = "home_neon_border")

    val rotationPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(5000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation_phase"
    )

    val breathingGlow by infiniteTransition.animateFloat(
        initialValue = 0.55f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathing_glow"
    )

    val currentAlpha = when (state) {
        EdgeLightingState.STOPPED -> 0f
        EdgeLightingState.IDLE -> 0f
        EdgeLightingState.SPEAKING -> (breathingGlow * (0.80f + (rmsDb / 35f).coerceIn(0f, 0.20f)))
        else -> 0.95f
    }

    Canvas(modifier = modifier) {
        if (currentAlpha <= 0.01f) return@Canvas

        val w = size.width
        val h = size.height
        val cornerRadiusPx = with(density) { 28.dp.toPx() }

        val borderWidth = when (state) {
            EdgeLightingState.SPEAKING -> with(density) { (3.5.dp + (rmsDb / 8f).coerceIn(0f, 3f).dp).toPx() }
            EdgeLightingState.THINKING -> with(density) { 4.5.dp.toPx() }
            EdgeLightingState.LISTENING -> with(density) { 4.0.dp.toPx() }
            else -> with(density) { 3.0.dp.toPx() }
        }

        val roundedPath = Path().apply {
            addRoundRect(
                RoundRect(
                    rect = Rect(offset = Offset.Zero, size = size),
                    cornerRadius = CornerRadius(cornerRadiusPx)
                )
            )
        }

        val rgbColors = listOf(
            electricCyan.copy(alpha = currentAlpha),
            deepBlue.copy(alpha = currentAlpha),
            violet.copy(alpha = currentAlpha * 0.9f),
            purple.copy(alpha = currentAlpha * 0.9f),
            magenta.copy(alpha = currentAlpha * 0.85f),
            teal.copy(alpha = currentAlpha),
            electricCyan.copy(alpha = currentAlpha)
        )

        val centerX = (w / 2f) + sin(rotationPhase) * (w * 0.05f)
        val centerY = (h / 2f) + cos(rotationPhase) * (h * 0.05f)

        val rgbSweepBrush = Brush.sweepGradient(
            colors = rgbColors,
            center = Offset(centerX, centerY)
        )

        drawPath(
            path = roundedPath,
            brush = rgbSweepBrush,
            style = Stroke(width = borderWidth * 2.8f, cap = StrokeCap.Round)
        )

        drawPath(
            path = roundedPath,
            brush = Brush.sweepGradient(
                colors = listOf(
                    Color.White.copy(alpha = currentAlpha * 0.95f),
                    electricCyan.copy(alpha = currentAlpha),
                    deepBlue.copy(alpha = currentAlpha),
                    violet.copy(alpha = currentAlpha),
                    magenta.copy(alpha = currentAlpha),
                    teal.copy(alpha = currentAlpha),
                    Color.White.copy(alpha = currentAlpha * 0.95f)
                ),
                center = Offset(centerX, centerY)
            ),
            style = Stroke(width = borderWidth, cap = StrokeCap.Round)
        )

        val corners = listOf(
            Offset(cornerRadiusPx, cornerRadiusPx),
            Offset(w - cornerRadiusPx, cornerRadiusPx),
            Offset(cornerRadiusPx, h - cornerRadiusPx),
            Offset(w - cornerRadiusPx, h - cornerRadiusPx)
        )

        for ((idx, corner) in corners.withIndex()) {
            val cornerColor = if (idx % 2 == 0) electricCyan else magenta
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        cornerColor.copy(alpha = currentAlpha * 0.6f),
                        Color.Transparent
                    ),
                    center = corner,
                    radius = with(density) { 35.dp.toPx() }
                ),
                radius = with(density) { 35.dp.toPx() },
                center = corner
            )
        }

        val particleDistanceX = w * 0.44f
        val particleDistanceY = h * 0.45f
        val numParticles = 6
        for (i in 0 until numParticles) {
            val particleAngle = rotationPhase + (i * (2 * Math.PI / numParticles)).toFloat()
            val px = (w / 2f) + cos(particleAngle.toDouble()).toFloat() * particleDistanceX
            val py = (h / 2f) + sin(particleAngle.toDouble()).toFloat() * particleDistanceY

            val particleColor = if (i % 2 == 0) electricCyan else teal

            drawCircle(
                color = particleColor.copy(alpha = currentAlpha * 0.9f),
                radius = with(density) { 4.dp.toPx() },
                center = Offset(px, py)
            )
            drawCircle(
                color = Color.White.copy(alpha = currentAlpha),
                radius = with(density) { 2.dp.toPx() },
                center = Offset(px, py)
            )
        }
    }
}

@Composable
fun FuturisticMicrophoneVisualizer(
    assistantState: AssistantState,
    rmsDb: Float,
    rotation1: Float,
    rotation2: Float,
    modifier: Modifier = Modifier
) {
    val electricBlue = Color(0xFF00B0FF)
    val cyanColor = Color(0xFF00E5FF)
    val violetColor = Color(0xFF9D4EDD)
    val purpleColor = Color(0xFF7C4DFF)
    val magentaColor = Color(0xFFFF007F)

    val infiniteTransition = rememberInfiniteTransition(label = "futuristic_mic_anim")

    // Expanding Neon Rings
    val ringPhase1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring_phase_1"
    )

    val ringPhase2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            initialStartOffset = StartOffset(600),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring_phase_2"
    )

    val ringPhase3 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            initialStartOffset = StartOffset(1200),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring_phase_3"
    )

    val isListening = assistantState == AssistantState.LISTENING
    val isSpeaking = assistantState == AssistantState.SPEAKING
    val isProcessing = assistantState == AssistantState.PROCESSING
    val isActive = isListening || isSpeaking || isProcessing

    val normalizedRms = if (isListening) {
        (rmsDb.coerceIn(-2f, 10f) + 2f) / 12f
    } else if (isSpeaking) {
        0.55f + 0.35f * sin(Math.toRadians(rotation1.toDouble())).toFloat()
    } else if (isProcessing) {
        0.4f
    } else {
        0f
    }

    Canvas(modifier = modifier) {
        val centerPoint = center
        val baseRadius = 43.dp.toPx()

        // 1. Holographic Ambient Energy Aura
        val auraRadius = baseRadius * (if (isActive) 1.85f + normalizedRms * 0.55f else 1.45f)
        val auraAlpha = if (isActive) 0.45f + normalizedRms * 0.30f else 0.22f

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    cyanColor.copy(alpha = auraAlpha),
                    magentaColor.copy(alpha = auraAlpha * 0.5f),
                    violetColor.copy(alpha = auraAlpha * 0.3f),
                    Color.Transparent
                ),
                center = centerPoint,
                radius = auraRadius
            ),
            radius = auraRadius,
            center = centerPoint
        )

        // 2. Dual Concentric Holographic Rotating Rings
        val ring1Radius = baseRadius + 16.dp.toPx()
        drawCircle(
            brush = Brush.sweepGradient(
                colors = listOf(
                    cyanColor.copy(alpha = 0.7f),
                    electricBlue.copy(alpha = 0.2f),
                    magentaColor.copy(alpha = 0.7f),
                    purpleColor.copy(alpha = 0.2f),
                    cyanColor.copy(alpha = 0.7f)
                ),
                center = centerPoint
            ),
            radius = ring1Radius + (if (isActive) normalizedRms * 12.dp.toPx() else 0f),
            center = centerPoint,
            style = Stroke(width = 2.5.dp.toPx())
        )

        val ring2Radius = baseRadius + 28.dp.toPx()
        drawCircle(
            brush = Brush.sweepGradient(
                colors = listOf(
                    purpleColor.copy(alpha = 0.3f),
                    magentaColor.copy(alpha = 0.8f),
                    cyanColor.copy(alpha = 0.3f),
                    electricBlue.copy(alpha = 0.8f),
                    purpleColor.copy(alpha = 0.3f)
                ),
                center = centerPoint
            ),
            radius = ring2Radius + (if (isActive) normalizedRms * 18.dp.toPx() else 0f),
            center = centerPoint,
            style = Stroke(width = 2.dp.toPx())
        )

        // 3. Audio-Reactive Expanding Wave Energy Rings
        val ringPhases = listOf(ringPhase1, ringPhase2, ringPhase3)
        for (p in ringPhases) {
            val maxExpansion = baseRadius + (if (isActive) 60.dp.toPx() + normalizedRms * 48.dp.toPx() else 32.dp.toPx())
            val currentRingRadius = baseRadius + p * (maxExpansion - baseRadius)
            val ringAlpha = (1f - p) * (if (isActive) 0.85f else 0.30f)
            val strokeW = (3.dp.toPx() * (1f - p * 0.5f))

            if (currentRingRadius > baseRadius) {
                drawCircle(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            electricBlue.copy(alpha = ringAlpha),
                            cyanColor.copy(alpha = ringAlpha),
                            magentaColor.copy(alpha = ringAlpha * 0.8f),
                            purpleColor.copy(alpha = ringAlpha),
                            electricBlue.copy(alpha = ringAlpha)
                        ),
                        center = centerPoint
                    ),
                    radius = currentRingRadius,
                    center = centerPoint,
                    style = Stroke(width = strokeW)
                )
            }
        }

        // 4. Orbiting Floating Energy Particles
        val numParticles = 10
        for (i in 0 until numParticles) {
            val particleAngleDeg = (i * 360f / numParticles) + rotation1
            val particleRad = Math.toRadians(particleAngleDeg.toDouble())

            val orbitDist = baseRadius + 24.dp.toPx() + (sin(rotation2 * 0.05f + i) * 8.dp.toPx())
            val px = (centerPoint.x + cos(particleRad) * orbitDist).toFloat()
            val py = (centerPoint.y + sin(particleRad) * orbitDist).toFloat()

            val particleColor = if (i % 2 == 0) cyanColor else magentaColor
            val particleRadius = if (isActive) (4.0.dp.toPx() + normalizedRms * 2.5f.dp.toPx()) else 2.8.dp.toPx()

            drawCircle(
                color = particleColor.copy(alpha = if (isActive) 0.9f else 0.45f),
                radius = particleRadius,
                center = Offset(px, py)
            )
            drawCircle(
                color = Color.White.copy(alpha = if (isActive) 0.95f else 0.6f),
                radius = particleRadius * 0.5f,
                center = Offset(px, py)
            )
        }
    }
}

@Composable
fun Holographic3DMicrophoneCore(
    assistantState: AssistantState,
    rmsDb: Float,
    onMicButtonClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val electricBlue = Color(0xFF00B0FF)
    val cyanColor = Color(0xFF00E5FF)
    val purpleColor = Color(0xFF7C4DFF)
    val magentaColor = Color(0xFFFF007F)

    val infiniteTransition = rememberInfiniteTransition(label = "holographic_mic_core")

    // 1. Continuous Rotation for Holographic Rings
    val ringRotation1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring_rotation_1"
    )

    val ringRotation2 by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(4500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring_rotation_2"
    )

    // 2. 3D Floating/Bobbing Motion Effect
    val floatOffset by infiniteTransition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "float_offset"
    )

    // 3. Subtle Scale & Breathing Pulse
    val breathingScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathing_scale"
    )

    val isListening = assistantState == AssistantState.LISTENING
    val isSpeaking = assistantState == AssistantState.SPEAKING
    val isProcessing = assistantState == AssistantState.PROCESSING
    val isActive = isListening || isSpeaking || isProcessing

    // Dynamic Voice Amplitude Normalization (0.0 to 1.0)
    val voiceAmp = if (isListening) {
        (rmsDb.coerceIn(-2f, 10f) + 2f) / 12f
    } else if (isSpeaking) {
        0.55f + 0.35f * sin(ringRotation1 * 0.05f)
    } else if (isProcessing) {
        0.45f
    } else {
        0f
    }

    val dynamicScale = if (isActive) (1.05f + voiceAmp * 0.18f) else breathingScale

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .offset(y = floatOffset.dp)
            .scale(dynamicScale)
    ) {
        // --- 1. Background Visualizer Canvas (Aura, Expanding Waves, Orbiting Particles) ---
        FuturisticMicrophoneVisualizer(
            assistantState = assistantState,
            rmsDb = rmsDb,
            rotation1 = ringRotation1,
            rotation2 = ringRotation2,
            modifier = Modifier.size(240.dp)
        )

        // --- 2. 3D Holographic Spherical Mic Button Core ---
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(86.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.95f),
                            cyanColor,
                            electricBlue,
                            purpleColor,
                            Color(0xFF0F121F)
                        ),
                        center = Offset(28f, 28f),
                        radius = 120f
                    )
                )
                .border(
                    width = 2.5.dp,
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            Color.White,
                            cyanColor,
                            magentaColor,
                            purpleColor,
                            Color.White
                        )
                    ),
                    shape = CircleShape
                )
                .clickable { onMicButtonClick() }
        ) {
            // Inner Metallic Glass Reflection Layer
            Canvas(modifier = Modifier.fillMaxSize()) {
                val centerPoint = center
                val r = size.width / 2f

                // Glass Top Highlight
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.65f),
                            Color.Transparent
                        ),
                        center = Offset(r * 0.7f, r * 0.5f),
                        radius = r * 0.75f
                    ),
                    radius = r,
                    center = centerPoint
                )

                // Rhythmic Voice Reactive Ring
                if (isActive) {
                    drawCircle(
                        color = magentaColor.copy(alpha = 0.4f + voiceAmp * 0.5f),
                        radius = r * (0.82f + voiceAmp * 0.15f),
                        style = Stroke(width = 3.dp.toPx())
                    )
                }
            }

            // Metallic 3D White Icon
            Icon(
                imageVector = if (isListening) Icons.Default.Stop else Icons.Default.Mic,
                contentDescription = "Microphone",
                tint = Color.White,
                modifier = Modifier
                    .size(38.dp)
                    .scale(if (isListening) 1.0f + voiceAmp * 0.15f else 1.0f)
            )
        }
    }
}

@Composable
fun VoiceAssistantScreen(
    assistantState: AssistantState,
    userSpokenText: String,
    assistantResponseText: String,
    statusMessage: String,
    hasPermissions: Boolean,
    rmsDb: Float,
    userName: String = "Shubham",
    userEmoji: String = "👋",
    onUpdateName: (String) -> Unit = {},
    onUpdateEmoji: (String) -> Unit = {},
    onRequestPermissions: () -> Unit,
    onMicButtonClick: () -> Unit,
    onOpenProfile: () -> Unit,
    onSuggestionClick: (String) -> Unit = {}
) {
    val darkBackground = Color(0xFF10121A)
    val cardBackground = Color(0xFF1B1E2B)
    val accentCyan = Color(0xFF00E5FF)
    val accentPurple = Color(0xFF7C4DFF)
    val textLight = Color(0xFFE0E6ED)

    val pulseAnim = remember { Animatable(1f) }

    val edgeState = when (assistantState) {
        AssistantState.IDLE -> EdgeLightingState.IDLE
        AssistantState.LISTENING -> EdgeLightingState.LISTENING
        AssistantState.PROCESSING -> EdgeLightingState.THINKING
        AssistantState.SPEAKING -> EdgeLightingState.SPEAKING
        AssistantState.ERROR -> EdgeLightingState.STOPPED
    }

    LaunchedEffect(assistantState) {
        if (assistantState == AssistantState.LISTENING) {
            pulseAnim.animateTo(
                targetValue = 1.25f,
                animationSpec = infiniteRepeatable(
                    animation = tween(800, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                )
            )
        } else {
            pulseAnim.snapTo(1f)
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectHorizontalDragGestures { _, dragAmount ->
                    if (dragAmount > 35f) {
                        onOpenProfile()
                    }
                }
            },
        color = darkBackground
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // --- HOME PAGE ONLY: ULTRA-PREMIUM FUTURISTIC ANIMATED NEON BORDER ---
            HomeNeonBorderCanvas(
                state = edgeState,
                rmsDb = rmsDb,
                modifier = Modifier.fillMaxSize()
            )

            Scaffold(
                containerColor = Color.Transparent
            ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                var showEditNameDialog by remember { mutableStateOf(false) }
                var showEditEmojiDialog by remember { mutableStateOf(false) }

                if (showEditNameDialog) {
                    EditNameDialog(
                        currentName = userName,
                        onSaveName = { newName ->
                            onUpdateName(newName)
                        },
                        onDismiss = { showEditNameDialog = false }
                    )
                }

                if (showEditEmojiDialog) {
                    EditEmojiDialog(
                        currentEmoji = userEmoji,
                        onSaveEmoji = { newEmoji ->
                            onUpdateEmoji(newEmoji)
                        },
                        onDismiss = { showEditEmojiDialog = false }
                    )
                }

                var showHint by remember { mutableStateOf(true) }
                var hintDissolvePhase by remember { mutableFloatStateOf(0f) }
                var showSubtitle by remember { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    // Display hint for 2.6s
                    delay(2600)
                    // 400ms katka katka segment dissolution animation (completes at T=3.0s)
                    val startTime = System.currentTimeMillis()
                    while (System.currentTimeMillis() - startTime < 400) {
                        val progress = (System.currentTimeMillis() - startTime) / 400f
                        hintDissolvePhase = progress.coerceIn(0f, 1f)
                        delay(16)
                    }
                    hintDissolvePhase = 1f
                    showHint = false
                    showSubtitle = true
                }

                // 1. Header with Personalized Name ("Hi [Name] [Emoji]") & Double-Tap Gestures for Name and Emoji
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Hi $userName",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            letterSpacing = 1.sp,
                            modifier = Modifier.pointerInput(Unit) {
                                detectTapGestures(
                                    onDoubleTap = {
                                        showEditNameDialog = true
                                    }
                                )
                            }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = userEmoji,
                            fontSize = 26.sp,
                            modifier = Modifier.pointerInput(Unit) {
                                detectTapGestures(
                                    onDoubleTap = {
                                        showEditEmojiDialog = true
                                    }
                                )
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    if (showHint) {
                        val dissolveAlpha = (1f - hintDissolvePhase).coerceIn(0f, 1f)
                        val dissolveOffset = (sin(hintDissolvePhase * 25f) * 5f * hintDissolvePhase).dp
                        Text(
                            text = "(Double-tap name or emoji to edit)",
                            fontSize = 12.sp,
                            color = accentCyan,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .offset(x = dissolveOffset)
                                .alpha(dissolveAlpha)
                        )
                    } else if (showSubtitle) {
                        Text(
                            text = "I am your personal assistant",
                            fontSize = 13.sp,
                            color = accentCyan,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // 2. Permission Banner if missing
                if (!hasPermissions) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF2D1820)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Permissions Required",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFF5252),
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Microphone, Phone Call, and Contacts permissions are required.",
                                color = textLight,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = onRequestPermissions,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252))
                            ) {
                                Text("Grant Permissions", color = Color.White)
                            }
                        }
                    }
                }

                // 2. Conversation Display Cards
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                ) {
                    // Status Badge
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFF252A3A))
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = when (assistantState) {
                                    AssistantState.LISTENING -> Icons.Default.Mic
                                    AssistantState.SPEAKING -> Icons.AutoMirrored.Filled.VolumeUp
                                    AssistantState.PROCESSING -> Icons.Default.RecordVoiceOver
                                    else -> Icons.Default.SmartToy
                                },
                                contentDescription = null,
                                tint = if (assistantState == AssistantState.LISTENING) accentCyan else accentPurple,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = statusMessage,
                                color = textLight,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // User Spoken Text Box
                    if (userSpokenText.isNotEmpty()) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .border(1.dp, accentCyan.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
                            colors = CardDefaults.cardColors(containerColor = cardBackground),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "YOU SAID:",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = accentCyan,
                                    letterSpacing = 1.sp
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = userSpokenText,
                                    fontSize = 16.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Normal
                                )
                            }
                        }
                    }

                    // Assistant Reply Box
                    if (assistantResponseText.isNotEmpty()) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .border(1.dp, accentPurple.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
                            colors = CardDefaults.cardColors(containerColor = cardBackground),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "NOVA REPLIED:",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = accentPurple,
                                    letterSpacing = 1.sp
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                TypewriterText(
                                    text = assistantResponseText,
                                    fontSize = 16.sp,
                                    color = textLight,
                                    fontWeight = FontWeight.Normal
                                )
                            }
                        }
                    }
                }

                // 3. Quick Action Suggestion Chips
                val suggestions = listOf(
                    "💬 કેમ છો Nova?",
                    "😂 એક જોક સંભળાવો",
                    "🔦 Torch ચાલુ કરો",
                    "☀️ આજનું હવામાન",
                    "⏰ 5 મિનિટ નું ટાઈમર",
                    "🎵 YouTube પર ગીતો વગાડો"
                )
                androidx.compose.foundation.lazy.LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp)
                ) {
                    items(suggestions.size) { index ->
                        val item = suggestions[index]
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color(0xFF1E2433))
                                .border(1.dp, Color(0xFF334155), RoundedCornerShape(20.dp))
                                .clickable { onSuggestionClick(item) }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = item,
                                color = Color(0xFFE0E6ED),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // 4. 3D Holographic AI Microphone Core & Audio Visualizer
                Holographic3DMicrophoneCore(
                    assistantState = assistantState,
                    rmsDb = rmsDb,
                    onMicButtonClick = onMicButtonClick,
                    modifier = Modifier.padding(vertical = 12.dp)
                )

                // Quick Helper Hints
                Text(
                    text = "માઇક દબાવો અથવા ઉપરના પ્રશ્ન પર ક્લિક કરો",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
        }
    }
}
}

@Composable
fun TypewriterText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    fontSize: androidx.compose.ui.unit.TextUnit = 16.sp,
    fontWeight: FontWeight = FontWeight.Normal,
    charDelayMs: Long = 16L
) {
    var displayedLength by remember(text) { mutableStateOf(0) }

    LaunchedEffect(text) {
        displayedLength = 0
        if (text.isNotEmpty()) {
            for (i in 1..text.length) {
                displayedLength = i
                delay(charDelayMs)
            }
        }
    }

    Text(
        text = text.take(displayedLength),
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        fontWeight = fontWeight
    )
}

@Preview(showBackground = true)
@Composable
fun VoiceAssistantPreview() {
    NovaTheme {
        VoiceAssistantScreen(
            assistantState = AssistantState.IDLE,
            userSpokenText = "Hey Nova, what's the weather today?",
            assistantResponseText = "It is sunny and 24°C in your area.",
            statusMessage = "Tap the microphone button to start",
            hasPermissions = true,
            rmsDb = 0f,
            userName = "Shubham",
            userEmoji = "👋",
            onRequestPermissions = {},
            onMicButtonClick = {},
            onOpenProfile = {}
        )
    }
}


