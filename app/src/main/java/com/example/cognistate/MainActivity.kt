package com.example.cognistate

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.example.cognistate.cogni.engine.CogniEngine
import com.example.cognistate.cogni.model.CogniState
import com.example.cognistate.services.AuraBorderService
import com.example.cognistate.services.GrayscaleOverlayService
import com.example.cognistate.ui.screens.HistoryScreen
import com.example.cognistate.ui.screens.RecoveryScreen
import com.example.cognistate.ui.theme.CogniStateTheme

class MainActivity : ComponentActivity() {

    private var isOverlayPermissionGranted by mutableStateOf(false)
    private var isNotificationPermissionGranted by mutableStateOf(false)
    private var isCameraPermissionGranted by mutableStateOf(false)

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        isCameraPermissionGranted = granted
        if (isSetupComplete()) startEngine()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        updatePermissions()

        setContent {
            CogniStateTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (isSetupComplete()) {
                        MainNavigationContainer()
                    } else {
                        PermissionGateScreen(
                            overlayGranted = isOverlayPermissionGranted,
                            notifGranted = isNotificationPermissionGranted,
                            cameraGranted = isCameraPermissionGranted,
                            onGrantOverlay = { requestOverlay() },
                            onGrantNotif = { requestNotif() },
                            onGrantCamera = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) }
                        )
                    }
                }
            }
        }

        if (isSetupComplete()) {
            startEngine()
        }
    }

    override fun onResume() {
        super.onResume()
        updatePermissions()
    }

    private fun isSetupComplete() =
        isOverlayPermissionGranted && isNotificationPermissionGranted && isCameraPermissionGranted

    private fun updatePermissions() {
        isOverlayPermissionGranted = Settings.canDrawOverlays(this)
        val listeners = Settings.Secure.getString(
            contentResolver,
            "enabled_notification_listeners"
        )
        val componentName = "$packageName/$packageName.services.CogniNotificationListener"
        isNotificationPermissionGranted =
            listeners?.contains(componentName) == true || listeners?.contains(packageName) == true
        
        isCameraPermissionGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun startEngine() {
        try {
            CogniEngine.start(applicationContext)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Engine Failure", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestOverlay() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            "package:$packageName".toUri()
        )
        startActivityForResult(intent, 1001)
    }

    private fun requestNotif() {
        startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
    }

    @Deprecated("Deprecated in Android activity API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        updatePermissions()
        if (isSetupComplete()) {
            startEngine()
        }
    }

    @Composable
    fun MainNavigationContainer() {
        var selectedTab by remember { mutableIntStateOf(0) }
        val state by CogniEngine.CogniStateFlow.collectAsState()
        var showRecoveryDialog by remember { mutableStateOf(false) }

        // Trigger Shield Services (Aura/Grayscale) on Redlining
        LaunchedEffect(state.stateLabel) {
            val auraIntent = Intent(this@MainActivity, AuraBorderService::class.java)
            val grayIntent = Intent(this@MainActivity, GrayscaleOverlayService::class.java)

            if (state.stateLabel == CogniState.StateLabel.REDLINING) {
                startForegroundService(auraIntent)
                startForegroundService(grayIntent)
            } else {
                stopService(auraIntent)
                stopService(grayIntent)
            }

            // Launch Recovery Dialog on Transition to RECOVERY
            if (state.stateLabel == CogniState.StateLabel.RECOVERY) {
                showRecoveryDialog = true
            }
        }

        if (showRecoveryDialog) {
            RecoveryScreen(onDismiss = { showRecoveryDialog = false })
        }

        Scaffold(
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = { Icon(Icons.Default.Home, contentDescription = "Shield") },
                        label = { Text("Dashboard") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = { Icon(Icons.Default.List, contentDescription = "History") },
                        label = { Text("History") }
                    )
                }
            }
        ) { padding ->
            Box(modifier = Modifier.padding(padding)) {
                when (selectedTab) {
                    0 -> DashboardScreen(state)
                    1 -> HistoryScreen()
                }
            }
        }
    }
}

@Composable
fun PermissionGateScreen(
    overlayGranted: Boolean,
    notifGranted: Boolean,
    cameraGranted: Boolean,
    onGrantOverlay: () -> Unit,
    onGrantNotif: () -> Unit,
    onGrantCamera: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "CogniShield Setup",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(32.dp))
        PermissionCard("System Overlay", "Shows the Redline Aura", overlayGranted, onGrantOverlay)
        Spacer(modifier = Modifier.height(16.dp))
        PermissionCard("Notifications", "Enables Neural Voicemail", notifGranted, onGrantNotif)
        Spacer(modifier = Modifier.height(16.dp))
        PermissionCard("Camera", "Fuses Heart Rate & Gaze", cameraGranted, onGrantCamera)
    }
}

@Composable
fun PermissionCard(
    title: String,
    desc: String,
    granted: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(desc, style = MaterialTheme.typography.bodySmall)
            }
            Button(onClick = onClick, enabled = !granted) {
                Text(if (granted) "OK" else "Fix")
            }
        }
    }
}

@Composable
fun DashboardScreen(state: CogniState) {
    val score = state.stressScore
    val safeScore = score.coerceIn(0f, 1f)

    val stateColor by animateColorAsState(
        targetValue = when (state.stateLabel) {
            CogniState.StateLabel.FLOW -> Color(0xFF00FFFF)
            CogniState.StateLabel.REDLINING -> Color(0xFFFFBF00)
            CogniState.StateLabel.RECOVERY -> Color(0xFF00FF41)
        },
        label = "stateColor"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 64.dp, start = 24.dp, end = 24.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "COGNISHIELD",
            fontWeight = FontWeight.Black,
            letterSpacing = 4.sp
        )

        Spacer(modifier = Modifier.height(48.dp))

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(240.dp)
        ) {
            val transition = rememberInfiniteTransition(label = "pulse")
            val strokeWidth by transition.animateFloat(
                initialValue = 10f,
                targetValue = 16f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1000, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "strokeWidth"
            )

            Canvas(modifier = Modifier.fillMaxSize()) {
                drawArc(
                    color = stateColor.copy(alpha = 0.1f),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(width = 12.dp.toPx())
                )
                drawArc(
                    color = stateColor,
                    startAngle = -90f,
                    sweepAngle = 360f * safeScore,
                    useCenter = false,
                    style = Stroke(width = strokeWidth.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = state.stateLabel.name,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = stateColor
                )
                Text(text = "STATE", style = MaterialTheme.typography.labelSmall)
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        Text(
            text = "STRESS LOAD",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        LinearProgressIndicator(
            progress = { safeScore },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
            color = stateColor,
            trackColor = stateColor.copy(alpha = 0.1f),
            strokeCap = StrokeCap.Round
        )

        Spacer(modifier = Modifier.height(48.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = stateColor.copy(alpha = 0.05f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("AI Insights", fontWeight = FontWeight.Bold)
                Text(
                    text = when(state.stateLabel) {
                        CogniState.StateLabel.FLOW -> "Multimodal fusion indicates deep focus. Neural Voicemail inactive."
                        CogniState.StateLabel.REDLINING -> "High cognitive load detected. Shield active: Grayscale + Aura enabled."
                        CogniState.StateLabel.RECOVERY -> "Transition detected. NPU suggesting 4-7-8 breathing exercise."
                    },
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
