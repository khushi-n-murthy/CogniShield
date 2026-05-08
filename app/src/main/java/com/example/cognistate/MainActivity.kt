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
import androidx.compose.ui.unit.sp
import com.example.cognistate.haptic.HapticController
import com.example.cognistate.ui.theme.CogniStateTheme

class MainActivity : ComponentActivity() {

    private val imuAnalyzer by lazy {
        (application as CogniApplication).imuAnalyzer
    }

    private lateinit var hapticController: HapticController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        hapticController = HapticController(this)

        enableEdgeToEdge()

        setContent {

            CogniStateTheme {

                val imuScore by
                imuAnalyzer.imuScoreFlow.collectAsState()

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
                }
            }
        }
    }
}