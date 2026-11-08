package com.example.nova.presentation.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

/**
 * Animated Profile Screen featuring the original high-end Aurora flow,
 * floating oscillations, waterfall animations, glowing avatar, and name editor.
 */
@Composable
fun ProfileScreen(
    userName: String = "Shubham",
    profileBitmap: ImageBitmap? = null,
    onUpdateName: (String) -> Unit = {},
    onChangePhoto: () -> Unit = {},
) {
    val electricBlue = Color(0xFF00B0FF)
    val cyanGlow = Color(0xFF00E5FF)
    val turquoiseGlow = Color(0xFF00E676)
    val purpleGlow = Color(0xFF7C4DFF)
    val violetGlow = Color(0xFF9D4EDD)
    val magentaGlow = Color(0xFFFF007F)

    var showHint by remember { mutableStateOf(value = true) }
    var hintDissolvePhase by remember { mutableFloatStateOf(value = 0f) }
    var showEditDialog by remember { mutableStateOf(value = false) }

    // Waterfall Falling Drop Down Animation from top to center for "Hello Shubham"
    val waterfallY = remember { Animatable(initialValue = -250f) }
    val waterfallAlpha = remember { Animatable(initialValue = 0f) }

    // 1. Continuous Aurora Flow Animation
    val infiniteTransition = rememberInfiniteTransition(label = "profile_aurora")

    val auroraPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(4500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
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

    LaunchedEffect(Unit) {
        // Smooth waterfall drop down animation from top (-250dp -> 0dp)
        waterfallAlpha.animateTo(1f, tween(350, easing = FastOutSlowInEasing))
        waterfallY.animateTo(
            targetValue = 0f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )

        // 2.6s delay before 400ms dissolution animation
        delay(2600L)
        val startTime = System.currentTimeMillis()
        while ((System.currentTimeMillis() - startTime) < 400) {
            val progress = (System.currentTimeMillis() - startTime) / 400f
            hintDissolvePhase = progress.coerceIn(0f, 1f)
            delay(16L)
        }
        hintDissolvePhase = 1f
        showHint = false
    }

    if (showEditDialog) {
        ProfileEditNameDialog(
            currentName = userName,
            onSaveName = { newName ->
                onUpdateName(newName)
                showEditDialog = false
            },
            onDismiss = { showEditDialog = false }
        )
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF05070D)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            // --- SUBTLE PROFILE EDGE GLOW ---
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
                            .clickable { onChangePhoto() }
                    ) {
                        if (profileBitmap != null) {
                            Image(
                                bitmap = profileBitmap,
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
                            .clickable { onChangePhoto() }
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "Change Photo",
                            tint = Color.White,
                            modifier = Modifier.size(15.dp)
                        )
                    }
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
private fun ProfileEditNameDialog(
    currentName: String,
    onSaveName: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var textValue by remember { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF161A26),
        title = {
            Text(
                text = "Edit Profile Name",
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
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = textValue,
                    onValueChange = { textValue = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF00E5FF),
                        unfocusedBorderColor = Color.Gray
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val clean = textValue.trim().ifEmpty { currentName }
                    onSaveName(clean)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF))
            ) {
                Text("Save", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.Gray)
            }
        }
    )
}
