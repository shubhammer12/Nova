package com.example.nova.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class HistoryItem(
    val userText: String,
    val novaResponse: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Composable
fun HistoryScreen(
    historyList: List<HistoryItem>,
    onClearHistory: () -> Unit = {}
) {
    val darkBackground = Color(0xFF0A0C14)
    val cardBackground = Color(0xFF161A26)
    val accentCyan = Color(0xFF00E5FF)
    val accentPurple = Color(0xFF7C4DFF)
    val textLight = Color(0xFFE0E6ED)

    val timeFormat = SimpleDateFormat("hh:mm a, dd MMM", Locale.getDefault())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(darkBackground)
            .padding(20.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "History (${historyList.size})",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            if (historyList.isNotEmpty()) {
                Button(
                    onClick = onClearHistory,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x33FF5252)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Clear All", color = Color(0xFFFF5252), fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (historyList.isEmpty()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 100.dp)
            ) {
                Text(
                    text = "No conversation history yet.\nStart speaking with Nova to record searches!",
                    color = Color.Gray,
                    fontSize = 14.sp
                )
            }
        } else {
            // Display all searches in history list (newest first)
            historyList.forEachIndexed { index, item ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .border(1.dp, accentCyan.copy(alpha = 0.35f), RoundedCornerShape(18.dp)),
                    colors = CardDefaults.cardColors(containerColor = cardBackground),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "SEARCH #${historyList.size - index}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = accentCyan,
                                letterSpacing = 1.sp
                            )

                            Text(
                                text = timeFormat.format(Date(item.timestamp)),
                                fontSize = 10.sp,
                                color = Color.Gray
                            )
                        }

                        if (item.userText.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "YOU SAID:",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.LightGray
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = item.userText,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }

                        if (item.novaResponse.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "NOVA REPLIED:",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = accentPurple
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = item.novaResponse,
                                fontSize = 14.sp,
                                color = textLight
                            )
                        }
                    }
                }
            }
        }
    }
}
