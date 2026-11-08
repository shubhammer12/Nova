package com.example.nova.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsScreen(
    onClearHistory: () -> Unit = {},
    isUninstallProtectionEnabled: Boolean = false,
    onToggleUninstallProtection: (Boolean) -> Unit = {},
    isCallAnnouncerEnabled: Boolean = true,
    onToggleCallAnnouncer: (Boolean) -> Unit = {},
    callAnnouncerLanguage: String = "gu",
    onChangeCallAnnouncerLanguage: (String) -> Unit = {},
    callAnnouncerRepeats: Int = 2,
    onChangeCallAnnouncerRepeats: (Int) -> Unit = {},
    onTestCallAnnouncement: () -> Unit = {},
    assistantVoiceStyle: String = "clear_female",
    onChangeAssistantVoiceStyle: (String) -> Unit = {},
    assistantVoiceSpeed: Float = 0.92f,
    onChangeAssistantVoiceSpeed: (Float) -> Unit = {},
    assistantVoicePitch: Float = 1.04f,
    onChangeAssistantVoicePitch: (Float) -> Unit = {},
    onTestAssistantVoice: () -> Unit = {},
    onShareApp: () -> Unit = {}
) {
    val darkBackground = Color(0xFF0A0C14)
    val cardBackground = Color(0xFF161A26)
    val accentCyan = Color(0xFF00E5FF)
    val textLight = Color(0xFFE0E6ED)
    val primaryGreen = Color(0xFF00E676)
    val vibrantPurple = Color(0xFFBB86FC)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(darkBackground)
            .padding(20.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Nova Settings",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(16.dp))

        // 1. Nova Voice Assistant Audio & Clarity Card
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = cardBackground),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Nova Assistant Voice",
                            fontWeight = FontWeight.Bold,
                            color = accentCyan,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            "Crystal Clear, Natural & Articulate Speech",
                            color = textLight.copy(alpha = 0.85f),
                            fontSize = 12.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Customize voice tone, deliberate cadence, and clear pronunciation.",
                    color = textLight.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    "Voice Tone / Style:",
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SelectionChip(
                        label = "🌸 Melodic",
                        isSelected = assistantVoiceStyle == "clear_female",
                        modifier = Modifier.weight(1f),
                        selectedColor = accentCyan,
                        onClick = { onChangeAssistantVoiceStyle("clear_female") }
                    )
                    SelectionChip(
                        label = "🎙️ Studio",
                        isSelected = assistantVoiceStyle == "clear_natural",
                        modifier = Modifier.weight(1f),
                        selectedColor = accentCyan,
                        onClick = { onChangeAssistantVoiceStyle("clear_natural") }
                    )
                    SelectionChip(
                        label = "🎩 Deep",
                        isSelected = assistantVoiceStyle == "clear_male",
                        modifier = Modifier.weight(1f),
                        selectedColor = accentCyan,
                        onClick = { onChangeAssistantVoiceStyle("clear_male") }
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    "Speech Speed (Word Clarity):",
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SelectionChip(
                        label = "Slow 0.85x",
                        isSelected = assistantVoiceSpeed == 0.85f,
                        modifier = Modifier.weight(1f),
                        selectedColor = vibrantPurple,
                        onClick = { onChangeAssistantVoiceSpeed(0.85f) }
                    )
                    SelectionChip(
                        label = "Clear 0.92x",
                        isSelected = assistantVoiceSpeed in 0.90f..0.94f,
                        modifier = Modifier.weight(1f),
                        selectedColor = vibrantPurple,
                        onClick = { onChangeAssistantVoiceSpeed(0.92f) }
                    )
                    SelectionChip(
                        label = "Normal 1.0x",
                        isSelected = assistantVoiceSpeed in 0.98f..1.02f,
                        modifier = Modifier.weight(1f),
                        selectedColor = vibrantPurple,
                        onClick = { onChangeAssistantVoiceSpeed(1.0f) }
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    "Voice Pitch (Tone):",
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SelectionChip(
                        label = "Melodic (1.05x)",
                        isSelected = assistantVoicePitch in 1.03f..1.06f,
                        modifier = Modifier.weight(1f),
                        selectedColor = primaryGreen,
                        onClick = { onChangeAssistantVoicePitch(1.04f) }
                    )
                    SelectionChip(
                        label = "Natural (1.0x)",
                        isSelected = assistantVoicePitch in 0.98f..1.02f,
                        modifier = Modifier.weight(1f),
                        selectedColor = primaryGreen,
                        onClick = { onChangeAssistantVoicePitch(1.0f) }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onTestAssistantVoice,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("🔊 Test Assistant Voice", color = accentCyan, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // 2. Incoming Call Announcer Card
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = cardBackground),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Incoming Call Announcer",
                            fontWeight = FontWeight.Bold,
                            color = primaryGreen,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            "Speaks caller identity aloud on incoming calls",
                            color = textLight.copy(alpha = 0.8f),
                            fontSize = 12.sp
                        )
                    }
                    Switch(
                        checked = isCallAnnouncerEnabled,
                        onCheckedChange = { enabled ->
                            onToggleCallAnnouncer(enabled)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = primaryGreen
                        )
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (isCallAnnouncerEnabled)
                        "Nova will announce caller name or number when your phone rings."
                    else
                        "Call announcer is currently disabled.",
                    color = if (isCallAnnouncerEnabled) textLight else Color.Gray,
                    fontSize = 12.sp
                )

                if (isCallAnnouncerEnabled) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        "Announcement Language:",
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LanguageChip(
                            label = "Gujarati",
                            isSelected = callAnnouncerLanguage == "gu",
                            modifier = Modifier.weight(1f),
                            onClick = { onChangeCallAnnouncerLanguage("gu") }
                        )
                        LanguageChip(
                            label = "English",
                            isSelected = callAnnouncerLanguage == "en",
                            modifier = Modifier.weight(1f),
                            onClick = { onChangeCallAnnouncerLanguage("en") }
                        )
                        LanguageChip(
                            label = "Hindi",
                            isSelected = callAnnouncerLanguage == "hi",
                            modifier = Modifier.weight(1f),
                            onClick = { onChangeCallAnnouncerLanguage("hi") }
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        "Announcement Repeats:",
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        RepeatChip(
                            label = "1 Time",
                            isSelected = callAnnouncerRepeats == 1,
                            modifier = Modifier.weight(1f),
                            onClick = { onChangeCallAnnouncerRepeats(1) }
                        )
                        RepeatChip(
                            label = "2 Times",
                            isSelected = callAnnouncerRepeats == 2,
                            modifier = Modifier.weight(1f),
                            onClick = { onChangeCallAnnouncerRepeats(2) }
                        )
                        RepeatChip(
                            label = "3 Times",
                            isSelected = callAnnouncerRepeats == 3,
                            modifier = Modifier.weight(1f),
                            onClick = { onChangeCallAnnouncerRepeats(3) }
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = onTestCallAnnouncement,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("🔊 Test Call Announcement", color = primaryGreen, fontSize = 13.sp)
                    }
                }
            }
        }

        // 3. Uninstall Fingerprint Security Protection Card
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = cardBackground),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Uninstall Fingerprint Protection", fontWeight = FontWeight.Bold, color = accentCyan, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (isUninstallProtectionEnabled) "Nova Protected • Display fingerprint required to uninstall" else "Requires display fingerprint scan before Nova can be uninstalled",
                    color = if (isUninstallProtectionEnabled) accentCyan else Color.Gray,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Enable Fingerprint Lock", color = textLight, fontSize = 14.sp)
                    Switch(
                        checked = isUninstallProtectionEnabled,
                        onCheckedChange = { enabled ->
                            onToggleUninstallProtection(enabled)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = accentCyan
                        )
                    )
                }
            }
        }

        // 4. Share Nova App Card
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = cardBackground),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "📤 Share Nova App",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFFD600),
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "Share Nova APK with friends via Bluetooth, WhatsApp, or any app. They can install it directly — no internet or Play Store needed.",
                    color = textLight.copy(alpha = 0.8f),
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = onShareApp,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD600)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("📤 Share Nova with Friends", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }

        // 5. Privacy & Memory Card
        var showClearConfirmDialog by remember { mutableStateOf(false) }

        if (showClearConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showClearConfirmDialog = false },
                title = {
                    Text(
                        text = "Clear Conversation Memory?",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = "Are you sure you want to delete all conversation history and AI memory? This action cannot be undone.",
                        color = Color(0xFFCBD5E1),
                        fontSize = 14.sp
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showClearConfirmDialog = false
                            onClearHistory()
                        }
                    ) {
                        Text(
                            text = "Yes, Clear All",
                            color = Color(0xFFFF5252),
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showClearConfirmDialog = false }
                    ) {
                        Text(
                            text = "Cancel",
                            color = Color.LightGray
                        )
                    }
                },
                containerColor = Color(0xFF1E293B),
                shape = RoundedCornerShape(16.dp)
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = cardBackground),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Privacy & Memory", fontWeight = FontWeight.Bold, color = Color(0xFFFF5252), fontSize = 16.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Manage stored conversation history and AI memory.", color = textLight, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { showClearConfirmDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252))
                ) {
                    Text("Clear Conversation Memory", color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun SelectionChip(
    label: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    selectedColor: Color = Color(0xFF00E5FF),
    onClick: () -> Unit
) {
    val unselectedBg = Color(0xFF1E2433)
    val textCol = if (isSelected) Color.Black else Color(0xFFE0E6ED)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) selectedColor else unselectedBg)
            .clickable { onClick() }
            .padding(horizontal = 4.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = textCol,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            fontSize = 11.5.sp,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

@Composable
private fun LanguageChip(
    label: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val selectedBg = Color(0xFF00E676)
    val unselectedBg = Color(0xFF1E2433)
    val textCol = if (isSelected) Color.Black else Color(0xFFE0E6ED)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) selectedBg else unselectedBg)
            .clickable { onClick() }
            .padding(horizontal = 4.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = textCol,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            fontSize = 11.5.sp,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

@Composable
private fun RepeatChip(
    label: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val selectedBg = Color(0xFF00E5FF)
    val unselectedBg = Color(0xFF1E2433)
    val textCol = if (isSelected) Color.Black else Color(0xFFE0E6ED)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) selectedBg else unselectedBg)
            .clickable { onClick() }
            .padding(horizontal = 4.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = textCol,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            fontSize = 11.5.sp,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}
