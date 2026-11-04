package com.example.nova.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsScreen(
    onClearHistory: () -> Unit = {},
    isUninstallProtectionEnabled: Boolean = false,
    onToggleUninstallProtection: (Boolean) -> Unit = {}
) {
    val darkBackground = Color(0xFF0A0C14)
    val cardBackground = Color(0xFF161A26)
    val accentCyan = Color(0xFF00E5FF)
    val textLight = Color(0xFFE0E6ED)

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

        // 1. Uninstall Fingerprint Security Protection Card
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

        // 2. Privacy & Memory Card
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
                    onClick = onClearHistory,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252))
                ) {
                    Text("Clear Conversation Memory", color = Color.White)
                }
            }
        }
    }
}
