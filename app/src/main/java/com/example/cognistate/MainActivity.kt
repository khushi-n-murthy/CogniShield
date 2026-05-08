package com.example.cognistate

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import android.content.Intent


import com.example.cognistate.cogni.engine.CogniEngine
import com.example.cognistate.cogni.model.CogniState
import com.example.cognistate.haptic.HapticController
import com.example.cognistate.imu.IMUAnalyzer
import com.example.cognistate.ui.theme.CogniStateTheme

class MainActivity : ComponentActivity() {

    private val imuAnalyzer by lazy {
        IMUAnalyzer(applicationContext)
    }

    private lateinit var hapticController: HapticController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        imuAnalyzer.start()
        // Start the rPPG sensor service
        val bioServiceIntent = Intent(this, com.example.cognistate.cogni.sensor.PhoneBioService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(bioServiceIntent)
        } else {
            startService(bioServiceIntent)
        }

        hapticController = HapticController(this)

        enableEdgeToEdge()

        setContent {

            CogniStateTheme {

                val imuScore by
                imuAnalyzer.imuScoreFlow.collectAsState()

                // NEW
                val cogniState by
                CogniEngine.CogniStateFlow.collectAsState()

                LaunchedEffect(imuScore) {

                    if (imuScore > 0.7f) {
                        hapticController.vibrateAlert()
                    }
                }

                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {

                    Text(
                        text = "IMU Score",
                        style = MaterialTheme.typography.headlineMedium
                    )

                    Text(
                        text = String.format("%.2f", imuScore),
                        fontSize = 40.sp
                    )

                    // NEW
                    Text(
                        text = cogniState.stateLabel.name,
                        fontSize = 32.sp,
                        color = when (cogniState.stateLabel) {

                            CogniState.StateLabel.FLOW ->
                                Color.Green

                            CogniState.StateLabel.RECOVERY ->
                                Color.Yellow

                            CogniState.StateLabel.REDLINING ->
                                Color.Red
                        }
                    )

                    // NEW
                    Text(
                        text = "Stress: ${
                            "%.2f".format(cogniState.stressScore)
                        }",
                        fontSize = 20.sp
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        imuAnalyzer.stop()
        stopService(Intent(this, com.example.cognistate.cogni.sensor.PhoneBioService::class.java))
    }
}