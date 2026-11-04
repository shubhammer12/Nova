package com.example.nova

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.example.nova.presentation.ui.BottomNavigationBar
import com.example.nova.presentation.ui.HistoryItem
import com.example.nova.presentation.ui.HistoryScreen
import com.example.nova.presentation.ui.NovaScreen
import com.example.nova.presentation.ui.SettingsScreen
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
    private val currentScreenState = mutableStateOf("splash") // "splash" -> "home"
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

        hasPermissionsState.value = PermissionUtils.hasAllPermissions(this)
        historyListState.value = loadHistoryFromPrefs(this)

        setContent {
            var activeNavScreen by remember { mutableStateOf(NovaScreen.ASSISTANT) }
            var isManualProfileOpen by remember { mutableStateOf(false) }

            val prefs = remember { getSharedPreferences("nova_prefs", Context.MODE_PRIVATE) }

            val initialProfileName = remember { prefs.getString("profile_custom_name", "Shubham") ?: "Shubham" }
            val initialHomeName = remember { prefs.getString("home_custom_name", "Shubham") ?: "Shubham" }
            val initialHomeEmoji = remember { prefs.getString("home_custom_emoji", "👋") ?: "👋" }

            var profileNameState by remember { mutableStateOf(initialProfileName) }
            var homeNameState by remember { mutableStateOf(initialHomeName) }
            var homeEmojiState by remember { mutableStateOf(initialHomeEmoji) }

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
                if (currentScreenState.value == "splash") {
                    SplashScreen(
                        userName = profileNameState,
                        onUpdateName = { saveProfileName(it) },
                        isManualOpen = isManualProfileOpen,
                        onSplashFinished = {
                            isManualProfileOpen = false
                            currentScreenState.value = "home"
                        }
                    )
                } else {
                    Scaffold(
                        bottomBar = {
                            BottomNavigationBar(
                                currentScreen = activeNavScreen,
                                onScreenSelected = { activeNavScreen = it }
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
                                            isManualProfileOpen = true
                                            currentScreenState.value = "splash"
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
                                        }
                                    )
                                }
                                NovaScreen.SETTINGS -> {
                                    val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                                    val adminComp = ComponentName(this@MainActivity, NovaDeviceAdminReceiver::class.java)
                                    val isProtectionActive = dpm.isAdminActive(adminComp)

                                    SettingsScreen(
                                        onClearHistory = {
                                            historyListState.value = emptyList()
                                            saveHistoryToPrefs(this@MainActivity, emptyList())
                                            userSpokenText.value = ""
                                            assistantResponseText.value = ""
                                            Toast.makeText(this@MainActivity, "Conversation memory cleared!", Toast.LENGTH_SHORT).show()
                                        },
                                        isUninstallProtectionEnabled = isProtectionActive,
                                        onToggleUninstallProtection = { enabled ->
                                            val bioHelper = BiometricHelper(this@MainActivity)
                                            bioHelper.authenticateForUninstall(
                                                onSuccess = {
                                                    if (enabled) {
                                                        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                                                            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComp)
                                                            putExtra(
                                                                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                                                                "Protect Nova against unauthorized uninstallation. Display fingerprint scan required to uninstall."
                                                            )
                                                        }
                                                        startActivity(intent)
                                                    } else {
                                                        dpm.removeActiveAdmin(adminComp)
                                                        Toast.makeText(this@MainActivity, "Uninstall protection disabled.", Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                onError = { err ->
                                                    Toast.makeText(this@MainActivity, "Fingerprint verification required: $err", Toast.LENGTH_SHORT).show()
                                                }
                                            )
                                        }
                                    )
                                }
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

    private fun toggleListening() {
        if (!checkPermissions()) {
            requestPermissions()
            return
        }

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
fun SplashScreen(
    userName: String = "Shubham",
    onUpdateName: (String) -> Unit = {},
    isManualOpen: Boolean = false,
    onSplashFinished: () -> Unit
) {
    val electricBlue = Color(0xFF00B0FF)
    val cyanGlow = Color(0xFF00E5FF)
    val turquoiseGlow = Color(0xFF00E676)
    val purpleGlow = Color(0xFF7C4DFF)
    val violetGlow = Color(0xFF9D4EDD)
    val magentaGlow = Color(0xFFFF007F)

    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("nova_prefs", Context.MODE_PRIVATE) }
    var profileBitmap by remember {
        val savedUri = prefs.getString("profile_image_uri", null)
        mutableStateOf(savedUri?.let { loadBitmapFromUri(context, it) })
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                // Ignore
            }
            prefs.edit().putString("profile_image_uri", uri.toString()).apply()
            profileBitmap = loadBitmapFromUri(context, uri.toString())
        }
    }

    val isGlowActive = remember { mutableStateOf(true) }
    var showHint by remember { mutableStateOf(true) }
    var hintDissolvePhase by remember { mutableFloatStateOf(0f) }

    // Waterfall Falling Drop Down Animation from top to center for "Hello Shubham"
    val waterfallY = remember { Animatable(-250f) }
    val waterfallAlpha = remember { Animatable(0f) }

    // 1. Continuous Aurora Flow Animation
    val infiniteTransition = rememberInfiniteTransition(label = "splash_aurora")

    val auroraPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(4500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "aurora_phase"
    )

    // Soft Edge Border Glow Pulse
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    // Continuous Up & Down Floating Oscillation for "Hello [Name]" Text
    val floatingY by infiniteTransition.animateFloat(
        initialValue = -7f,
        targetValue = 7f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "floating_y"
    )

    LaunchedEffect(isManualOpen) {
        // Smooth waterfall drop down animation from top (-250dp -> 0dp)
        waterfallAlpha.animateTo(1f, tween(350, easing = FastOutSlowInEasing))
        waterfallY.animateTo(
            targetValue = 0f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )

        // 2.6s delay before 400ms katka katka dissolution animation at T=3.0s
        delay(2600)
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < 400) {
            val progress = (System.currentTimeMillis() - startTime) / 400f
            hintDissolvePhase = progress.coerceIn(0f, 1f)
            delay(16)
        }
        hintDissolvePhase = 1f
        showHint = false

        if (!isManualOpen) {
            // Initial app launch -> auto transition to home after speech/animation
            delay(500)
            isGlowActive.value = false
            onSplashFinished()
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectHorizontalDragGestures { _, dragAmount ->
                    // User slides/swipes to go back to Home page
                    if (dragAmount > 20f || dragAmount < -20f) {
                        isGlowActive.value = false
                        onSplashFinished()
                    }
                }
            },
        color = Color(0xFF05070D)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            // --- SUBTLE PROFILE EDGE GLOW ---
            if (isGlowActive.value) {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(
                            width = 3.dp,
                            brush = Brush.sweepGradient(
                                listOf(
                                    cyanGlow.copy(alpha = glowAlpha),
                                    electricBlue.copy(alpha = glowAlpha),
                                    violetGlow.copy(alpha = glowAlpha * 0.7f),
                                    cyanGlow.copy(alpha = glowAlpha)
                                )
                            ),
                            shape = RoundedCornerShape(28.dp)
                        )
                        .padding(2.dp),
                    color = Color.Transparent
                ) {}
            }
            // --- CONTINUOUS MOVING AURORA AMBIENT LIGHTS BACKGROUND ---
            Canvas(modifier = Modifier.fillMaxSize()) {
                val centerPoint = center
                val w = size.width
                val h = size.height

                // Dark Navy Base Gradient
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF05070D),
                            Color(0xFF080B18),
                            Color(0xFF0C1024),
                            Color(0xFF05070D)
                        )
                    )
                )

                // Aurora Light Blob 1 (Electric Blue / Cyan Moving Center)
                val c1X = centerPoint.x + sin(auroraPhase) * (w * 0.28f)
                val c1Y = centerPoint.y - cos(auroraPhase) * (h * 0.22f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            cyanGlow.copy(alpha = 0.35f),
                            electricBlue.copy(alpha = 0.20f),
                            Color.Transparent
                        ),
                        center = Offset(c1X, c1Y),
                        radius = w * 0.65f
                    ),
                    center = Offset(c1X, c1Y),
                    radius = w * 0.65f
                )

                // Aurora Light Blob 2 (Purple / Violet / Magenta Moving Center)
                val c2X = centerPoint.x - cos(auroraPhase * 0.8f) * (w * 0.30f)
                val c2Y = centerPoint.y + sin(auroraPhase * 0.8f) * (h * 0.25f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            violetGlow.copy(alpha = 0.32f),
                            magentaGlow.copy(alpha = 0.18f),
                            Color.Transparent
                        ),
                        center = Offset(c2X, c2Y),
                        radius = w * 0.70f
                    ),
                    center = Offset(c2X, c2Y),
                    radius = w * 0.70f
                )

                // Aurora Light Blob 3 (Turquoise / Cyan Center)
                val c3X = centerPoint.x + sin(auroraPhase * 1.3f) * (w * 0.22f)
                val c3Y = centerPoint.y + cos(auroraPhase * 1.3f) * (h * 0.20f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            turquoiseGlow.copy(alpha = 0.22f),
                            Color.Transparent
                        ),
                        center = Offset(c3X, c3Y),
                        radius = w * 0.50f
                    ),
                    center = Offset(c3X, c3Y),
                    radius = w * 0.50f
                )

                // Floating Energy Light Particles
                val numParticles = 12
                for (p in 0 until numParticles) {
                    val pRad = Math.toRadians((p * 360.0 / numParticles) + Math.toDegrees(auroraPhase.toDouble()))
                    val px = (centerPoint.x + cos(pRad) * (w * 0.38f)).toFloat()
                    val py = (centerPoint.y + sin(pRad) * (h * 0.18f)).toFloat()
                    val particleColor = if (p % 2 == 0) cyanGlow else violetGlow

                    drawCircle(
                        color = particleColor.copy(alpha = 0.65f),
                        radius = 4.5f,
                        center = Offset(px, py)
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(24.dp)
            ) {
                // Profile Section with Animated Gradient Border
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(bottom = 22.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(92.dp)
                            .clip(CircleShape)
                            .border(
                                width = 2.5.dp,
                                brush = Brush.sweepGradient(
                                    colors = listOf(
                                        cyanGlow,
                                        electricBlue,
                                        purpleGlow,
                                        violetGlow,
                                        cyanGlow
                                    )
                                ),
                                shape = CircleShape
                            )
                            .clickable { galleryLauncher.launch("image/*") }
                    ) {
                        val currentBitmap = profileBitmap
                        if (currentBitmap != null) {
                            Image(
                                bitmap = currentBitmap,
                                contentDescription = "Profile Photo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "Profile Photo",
                                tint = Color.White,
                                modifier = Modifier.size(50.dp)
                            )
                        }
                    }

                    // Small Camera Badge Icon at Bottom-Right
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(28.dp)
                            .align(Alignment.BottomEnd)
                            .clip(CircleShape)
                            .background(purpleGlow)
                            .border(1.5.dp, Color.White, CircleShape)
                            .clickable { galleryLauncher.launch("image/*") }
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "Change Photo",
                            tint = Color.White,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }

    var showEditDialog by remember { mutableStateOf(false) }

    if (showEditDialog) {
        EditNameDialog(
            currentName = userName,
            onSaveName = { newName ->
                onUpdateName(newName)
            },
            onDismiss = { showEditDialog = false }
        )
    }

                // Center Text: "Hello [Name]" with Waterfall Drop Down Animation & Continuous Up-Down Floating
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .offset(y = (waterfallY.value + floatingY).dp)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onDoubleTap = {
                                    showEditDialog = true
                                }
                            )
                        }
                ) {
                    // Soft Ambient Glow Aura behind Text
                    Canvas(modifier = Modifier.size(260.dp, 80.dp)) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    cyanGlow.copy(alpha = 0.35f * waterfallAlpha.value),
                                    Color.Transparent
                                )
                            ),
                            radius = 100.dp.toPx()
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Hello $userName",
                            fontSize = 38.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            letterSpacing = 1.2.sp,
                            modifier = Modifier.alpha(waterfallAlpha.value)
                        )
                        if (showHint) {
                            Spacer(modifier = Modifier.height(2.dp))
                            val dissolveAlpha = ((1f - hintDissolvePhase) * waterfallAlpha.value * 0.7f).coerceIn(0f, 1f)
                            val dissolveOffset = (sin(hintDissolvePhase * 25f) * 5f * hintDissolvePhase).dp
                            Text(
                                text = "✏️ (Double-tap to edit name)",
                                fontSize = 11.sp,
                                color = Color.Gray,
                                modifier = Modifier
                                    .offset(x = dissolveOffset)
                                    .alpha(dissolveAlpha)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Subtitle: "Personal Voice Assistant"
                Text(
                    text = "Personal Voice Assistant",
                    fontSize = 14.sp,
                    color = cyanGlow,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.0.sp,
                    modifier = Modifier.alpha(waterfallAlpha.value)
                )
            }
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
    onOpenProfile: () -> Unit
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
                                Text(
                                    text = assistantResponseText,
                                    fontSize = 16.sp,
                                    color = textLight,
                                    fontWeight = FontWeight.Normal
                                )
                            }
                        }
                    }
                }

                // 5. 3D Holographic AI Microphone Core & Audio Visualizer
                Holographic3DMicrophoneCore(
                    assistantState = assistantState,
                    rmsDb = rmsDb,
                    onMicButtonClick = onMicButtonClick,
                    modifier = Modifier.padding(vertical = 16.dp)
                )

                // Quick Helper Hints
                Text(
                    text = "Say \"Hey Nova\" or tap mic to speak",
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


