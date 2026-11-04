package com.example.nova.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

enum class NovaScreen {
    ASSISTANT,
    HISTORY,
    SETTINGS
}

@Composable
fun BottomNavigationBar(
    currentScreen: NovaScreen,
    onScreenSelected: (NovaScreen) -> Unit
) {
    val darkCard = Color(0xFF161A26)
    val accentCyan = Color(0xFF00E5FF)

    NavigationBar(
        containerColor = darkCard,
        contentColor = Color.White,
        modifier = Modifier.background(darkCard)
    ) {
        NavigationBarItem(
            selected = currentScreen == NovaScreen.ASSISTANT,
            onClick = { onScreenSelected(NovaScreen.ASSISTANT) },
            icon = { Icon(Icons.Default.Mic, contentDescription = "Assistant") },
            label = { Text("Assistant") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = accentCyan,
                selectedTextColor = accentCyan,
                unselectedIconColor = Color.Gray,
                unselectedTextColor = Color.Gray,
                indicatorColor = Color(0xFF232838)
            )
        )

        NavigationBarItem(
            selected = currentScreen == NovaScreen.HISTORY,
            onClick = { onScreenSelected(NovaScreen.HISTORY) },
            icon = { Icon(Icons.Default.History, contentDescription = "History") },
            label = { Text("History") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = accentCyan,
                selectedTextColor = accentCyan,
                unselectedIconColor = Color.Gray,
                unselectedTextColor = Color.Gray,
                indicatorColor = Color(0xFF232838)
            )
        )

        NavigationBarItem(
            selected = currentScreen == NovaScreen.SETTINGS,
            onClick = { onScreenSelected(NovaScreen.SETTINGS) },
            icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
            label = { Text("Settings") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = accentCyan,
                selectedTextColor = accentCyan,
                unselectedIconColor = Color.Gray,
                unselectedTextColor = Color.Gray,
                indicatorColor = Color(0xFF232838)
            )
        )
    }
}
