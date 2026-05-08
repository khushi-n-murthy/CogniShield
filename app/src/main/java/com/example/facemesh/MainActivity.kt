package com.example.facemesh

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.Button
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.facemesh.gaze.GazeRepository
import com.example.facemesh.gaze.GazeService
import com.example.facemesh.ui.theme.FaceMeshTheme
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Permission granted, start background service
            GazeService.startService(this)
        } else {
            Toast.makeText(this, "Camera permission is required", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        requestCameraPermission()

        setContent {
            FaceMeshTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    GazeScreen()
                }
            }
        }
    }

    private fun requestCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                // Permission already granted, start background service
                GazeService.startService(this)
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Stop service when app is closed to prevent dangling camera processes
        GazeService.stopService(this)
    }
}

@Composable
fun GazeScreen() {
    val context = LocalContext.current

    // Safely observe the state flow exported by the singleton repository
    val gazeScore by GazeRepository.gazeScoreFlow.collectAsState()

    var showRecovery by remember { mutableStateOf(false) }
    var highStressCount by remember { mutableStateOf(0) }

    LaunchedEffect(gazeScore) {
        if (gazeScore >= 0.8f) {
            highStressCount++
            // More than 2 consecutive emissions (i.e. >= 3)
            if (highStressCount >= 3 && !showRecovery) {
                showRecovery = true
                highStressCount = 0 // Reset after triggering
            }
        } else {
            highStressCount = 0
        }
    }

    if (showRecovery) {
        com.example.facemesh.ui.RecoveryScreen(
            onDismiss = { showRecovery = false }
        )
    }
    
    // Compute simple UI status strings
    val status = if (gazeScore > 0.6f) "Focused" else "Distracted"
    val formattedScore = String.format(Locale.US, "%.2f", gazeScore)

    // Center everything as requested
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Gaze Score: $formattedScore",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )

            LinearProgressIndicator(
                progress = gazeScore,
                modifier = Modifier
                    .padding(top = 16.dp)
                    .fillMaxWidth(0.6f),
                color = if (gazeScore > 0.6f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
            
            Text(
                text = status,
                style = MaterialTheme.typography.titleLarge,
                color = if (gazeScore > 0.6f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 12.dp)
            )

            Button(
                onClick = { GazeService.startService(context) },
                modifier = Modifier.padding(top = 24.dp)
            ) {
                Text("Start Tracking")
            }

            Button(
                onClick = { GazeService.stopService(context) },
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text("Stop Tracking")
            }
        }
    }
}
