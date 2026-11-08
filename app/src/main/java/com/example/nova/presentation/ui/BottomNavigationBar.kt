package com.example.nova.presentation.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp

enum class NovaScreen {
    ASSISTANT,
    HISTORY,
    SETTINGS,
    PROFILE
}

@Composable
fun BottomNavigationBar(
    currentScreen: NovaScreen,
    onScreenSelected: (NovaScreen) -> Unit,
    profileBitmap: ImageBitmap? = null,
) {
    val darkCard = Color(0xFF161A26)
    val accentCyan = Color(0xFF00E5FF)

    NavigationBar(
        containerColor = darkCard,
        contentColor = Color.White,
        modifier = Modifier.background(darkCard),
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

        NavigationBarItem(
            selected = currentScreen == NovaScreen.PROFILE,
            onClick = { onScreenSelected(NovaScreen.PROFILE) },
            icon = {
                if (profileBitmap != null) {
                    Image(
                        bitmap = profileBitmap,
                        contentDescription = "Profile",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .border(
                                width = if (currentScreen == NovaScreen.PROFILE) 2.dp else 1.dp,
                                color = if (currentScreen == NovaScreen.PROFILE) accentCyan else Color.Gray.copy(alpha = 0.6f),
                                shape = CircleShape
                            )
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Profile",
                        modifier = Modifier.size(24.dp)
                    )
                }
            },
            label = { Text("Profile") },
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
