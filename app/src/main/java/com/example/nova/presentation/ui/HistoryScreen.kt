package com.example.nova.presentation.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class HistoryItem(
    val userText: String,
    val novaResponse: String,
    val timestamp: Long = System.currentTimeMillis()
)

object ChatDateUtils {
    fun isSameDay(ts1: Long, ts2: Long): Boolean {
        val cal1 = Calendar.getInstance().apply { timeInMillis = ts1 }
        val cal2 = Calendar.getInstance().apply { timeInMillis = ts2 }
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    fun formatTime(timestamp: Long): String {
        val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    fun formatCenterBadge(timestamp: Long): String {
        val now = Calendar.getInstance()
        val msgCal = Calendar.getInstance().apply { timeInMillis = timestamp }

        val isSameYear = now.get(Calendar.YEAR) == msgCal.get(Calendar.YEAR)
        val isToday = isSameYear && now.get(Calendar.DAY_OF_YEAR) == msgCal.get(Calendar.DAY_OF_YEAR)

        val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        val isYesterday = yesterday.get(Calendar.YEAR) == msgCal.get(Calendar.YEAR) &&
                yesterday.get(Calendar.DAY_OF_YEAR) == msgCal.get(Calendar.DAY_OF_YEAR)

        val timeStr = formatTime(timestamp)

        return when {
            isToday -> "Today, ${SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(timestamp))} • $timeStr"
            isYesterday -> "Yesterday, ${SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(timestamp))} • $timeStr"
            else -> "${SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault()).format(Date(timestamp))} • $timeStr"
        }
    }
}

@Composable
fun HistoryScreen(
    historyList: List<HistoryItem>,
    onClearHistory: () -> Unit = {},
    onSpeakMessage: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    // WhatsApp Dark Mode Colors
    val chatBackground = Color(0xFF0B141B)
    val topBarBackground = Color(0xFF161F26)
    val userBubbleGreen = Color(0xFF005C4B) // WhatsApp dark mode sent bubble
    val novaBubbleDark = Color(0xFF202C33)  // WhatsApp dark mode received bubble
    val centerPillBackground = Color(0xFF182229)
    val centerPillBorder = Color(0xFF2A3942)
    val centerPillText = Color(0xFF8696A0)
    val doubleCheckBlue = Color(0xFF53BDEB) // WhatsApp blue double tick
    val accentCyan = Color(0xFF00E5FF)
    val textLight = Color(0xFFE9EDEF)

    // Sort order: default is chronological (oldest at top, newest at bottom like WhatsApp)
    var sortDescending by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }

    val sortedList = remember(historyList, sortDescending) {
        if (sortDescending) {
            historyList.sortedByDescending { it.timestamp }
        } else {
            historyList.sortedBy { it.timestamp }
        }
    }

    val listState = rememberLazyListState()

    // Auto-scroll to bottom on opening if in chronological order
    LaunchedEffect(sortedList.size, sortDescending) {
        if (sortedList.isNotEmpty() && !sortDescending) {
            listState.animateScrollToItem(sortedList.size - 1)
        }
    }

    val showScrollToBottom by remember {
        derivedStateOf {
            if (sortedList.isEmpty() || sortDescending) false
            else {
                val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                lastVisibleIndex < sortedList.size - 2
            }
        }
    }

    // Confirmation dialog before clearing history
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = {
                Text(
                    text = "Clear Chat History?",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete all messages? This will remove all your conversation history.",
                    color = Color(0xFFCBD5E1),
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearDialog = false
                        onClearHistory()
                    }
                ) {
                    Text("Clear All", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel", color = Color.Gray)
                }
            },
            containerColor = Color(0xFF1E293B),
            shape = RoundedCornerShape(16.dp)
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(chatBackground)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Bar
            Surface(
                color = topBarBackground,
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0x2200E5FF),
                            border = BorderStroke(1.dp, accentCyan.copy(alpha = 0.5f)),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Forum,
                                    contentDescription = "Chat",
                                    tint = accentCyan,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = "Conversation History",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = if (historyList.isEmpty()) "No messages" else "${historyList.size} conversations",
                                fontSize = 11.sp,
                                color = Color(0xFF8696A0)
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Toggle sorting order
                        if (historyList.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    sortDescending = !sortDescending
                                    Toast.makeText(
                                        context,
                                        if (sortDescending) "Showing newest first" else "Showing WhatsApp chat order",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SwapVert,
                                    contentDescription = "Sort",
                                    tint = if (sortDescending) accentCyan else Color(0xFF8696A0)
                                )
                            }

                            // Clear history button
                            IconButton(onClick = { showClearDialog = true }) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "Clear History",
                                    tint = Color(0xFFFF5252)
                                )
                            }
                        }
                    }
                }
            }

            // Chat Content
            if (sortedList.isEmpty()) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF182229),
                            border = BorderStroke(1.dp, Color(0xFF2A3942)),
                            modifier = Modifier.size(80.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Forum,
                                    contentDescription = null,
                                    tint = accentCyan,
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Text(
                            text = "No conversation history yet",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Start speaking with Nova!\nYour questions will appear on the right (in green)\nand Nova's replies on the left with automatic dates and times.",
                            color = Color(0xFF8696A0),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(sortedList) { index, item ->
                        val isFirstOfDay = if (index == 0) {
                            true
                        } else {
                            val previousItem = sortedList[index - 1]
                            !ChatDateUtils.isSameDay(item.timestamp, previousItem.timestamp)
                        }

                        val timeFormatted = ChatDateUtils.formatTime(item.timestamp)

                        Column(modifier = Modifier.fillMaxWidth()) {
                            // CENTERED DATE & TIME BADGE - ONLY WHEN DAY CHANGES
                            if (isFirstOfDay) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = centerPillBackground,
                                        border = BorderStroke(0.6.dp, centerPillBorder),
                                        shadowElevation = 2.dp
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CalendarToday,
                                                contentDescription = null,
                                                tint = accentCyan,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = ChatDateUtils.formatCenterBadge(item.timestamp),
                                                color = centerPillText,
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                            }

                            // 1. USER MESSAGE (RIGHT SIDE)
                            if (item.userText.isNotBlank()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 6.dp),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(
                                            topStart = 16.dp,
                                            topEnd = 4.dp,
                                            bottomStart = 16.dp,
                                            bottomEnd = 16.dp
                                        ),
                                        color = userBubbleGreen,
                                        border = BorderStroke(0.5.dp, Color(0x3300E5FF)),
                                        shadowElevation = 1.5.dp,
                                        modifier = Modifier
                                            .widthIn(max = 300.dp)
                                            .clickable {
                                                clipboardManager.setText(AnnotatedString(item.userText))
                                                Toast.makeText(context, "Copied text", Toast.LENGTH_SHORT).show()
                                            }
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(
                                                start = 12.dp,
                                                end = 12.dp,
                                                top = 8.dp,
                                                bottom = 6.dp
                                            )
                                        ) {
                                            Text(
                                                text = item.userText,
                                                color = Color.White,
                                                fontSize = 14.5.sp,
                                                lineHeight = 20.sp
                                            )

                                            Spacer(modifier = Modifier.height(4.dp))

                                            Row(
                                                modifier = Modifier.align(Alignment.End),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = timeFormatted,
                                                    color = Color(0xCCFFFFFF),
                                                    fontSize = 10.sp
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "✓✓",
                                                    color = doubleCheckBlue,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // 2. NOVA RESPONSE (LEFT SIDE)
                            if (item.novaResponse.isNotBlank()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 6.dp),
                                    horizontalArrangement = Arrangement.Start
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(
                                            topStart = 4.dp,
                                            topEnd = 16.dp,
                                            bottomStart = 16.dp,
                                            bottomEnd = 16.dp
                                        ),
                                        color = novaBubbleDark,
                                        border = BorderStroke(0.5.dp, Color(0x337C4DFF)),
                                        shadowElevation = 1.5.dp,
                                        modifier = Modifier
                                            .widthIn(max = 315.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(
                                                start = 12.dp,
                                                end = 12.dp,
                                                top = 8.dp,
                                                bottom = 6.dp
                                            )
                                        ) {
                                            // Nova sender header
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "✦ Nova AI",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = accentCyan
                                                )

                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    // Copy button
                                                    Icon(
                                                        imageVector = Icons.Default.ContentCopy,
                                                        contentDescription = "Copy",
                                                        tint = Color(0xFF8696A0),
                                                        modifier = Modifier
                                                            .size(14.dp)
                                                            .clickable {
                                                                clipboardManager.setText(AnnotatedString(item.novaResponse))
                                                                Toast.makeText(context, "Copied reply", Toast.LENGTH_SHORT).show()
                                                            }
                                                    )

                                                    if (onSpeakMessage != null) {
                                                        Spacer(modifier = Modifier.width(10.dp))
                                                        Icon(
                                                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                                            contentDescription = "Speak",
                                                            tint = accentCyan,
                                                            modifier = Modifier
                                                                .size(15.dp)
                                                                .clickable {
                                                                    onSpeakMessage(item.novaResponse)
                                                                }
                                                        )
                                                    }
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(4.dp))

                                            Text(
                                                text = item.novaResponse,
                                                color = textLight,
                                                fontSize = 14.sp,
                                                lineHeight = 19.sp
                                            )

                                            Spacer(modifier = Modifier.height(4.dp))

                                            Row(
                                                modifier = Modifier.align(Alignment.End),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = timeFormatted,
                                                    color = Color(0xFF8696A0),
                                                    fontSize = 10.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Floating Scroll to Bottom Button
        AnimatedVisibility(
            visible = showScrollToBottom,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 20.dp, end = 20.dp)
        ) {
            FloatingActionButton(
                onClick = {
                    scope.launch {
                        if (sortedList.isNotEmpty()) {
                            listState.animateScrollToItem(sortedList.size - 1)
                        }
                    }
                },
                containerColor = Color(0xFF1E293B),
                contentColor = accentCyan,
                shape = CircleShape,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Scroll to bottom",
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
