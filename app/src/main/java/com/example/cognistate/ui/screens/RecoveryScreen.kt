package com.example.cognistate.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay

enum class BreathPhase(val instruction: String, val scale: Float, val durationMs: Int) {
    INHALE("Inhale...", 2.5f, 4000),
    HOLD("Hold...", 2.5f, 7000),
    EXHALE("Exhale...", 1.0f, 8000)
}

@Composable
fun RecoveryScreen(onDismiss: () -> Unit) {
    var currentPhase by remember { mutableStateOf(BreathPhase.INHALE) }

    // Animate the scale based on the current phase
    val animatedScale by animateFloatAsState(
        targetValue = currentPhase.scale,
        animationSpec = tween(
            durationMillis = currentPhase.durationMs,
            easing = LinearEasing
        ),
        label = "BreathScale"
    )

    // Run the 3 cycles of 4-7-8 breathing
    LaunchedEffect(Unit) {
        repeat(3) {
            currentPhase = BreathPhase.INHALE
            delay(BreathPhase.INHALE.durationMs.toLong())
            
            currentPhase = BreathPhase.HOLD
            delay(BreathPhase.HOLD.durationMs.toLong())
            
            currentPhase = BreathPhase.EXHALE
            delay(BreathPhase.EXHALE.durationMs.toLong())
        }
        // Auto-close after exactly 3 full breath cycles
        onDismiss()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false, // Allow full screen overlay
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        // High-end soft blur/translucent background overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xCC000000)), // 80% opaque black for a sleek dark overlay
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Recovery Mode",
                    style = MaterialTheme.typography.headlineLarge,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 80.dp)
                )

                // Breathing Circle
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .scale(animatedScale)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(70.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                    )
                }

                Spacer(modifier = Modifier.height(140.dp))

                Text(
                    text = currentPhase.instruction,
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(48.dp))

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                ) {
                    Text("Dismiss", color = Color.White)
                }
            }
        }
    }
}
