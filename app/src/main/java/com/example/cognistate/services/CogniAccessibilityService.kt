package com.example.cognistate.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.Parcelable
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import kotlinx.parcelize.Parcelize

@Parcelize
data class CogniState(
    val stateLabel: String,
    val confidence: Float = 0.95f
) : Parcelable

class CogniAccessibilityService : AccessibilityService() {

    companion object {
        const val ACTION_SHIELD_ON = "ACTION_SHIELD_ON"
    }

    // Simulated state
    private var currentState = CogniState("REDLINING")

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {

        if (currentState.stateLabel == "REDLINING") {

     
            // LOCK SCREEN ROTATION
            Settings.System.putInt(
                contentResolver,
                Settings.System.ACCELEROMETER_ROTATION,
                0
            )

            
            // BROADCAST SHIELD MODE
            val intent = Intent(ACTION_SHIELD_ON)
            intent.putExtra("cogni_state", currentState)

            sendBroadcast(intent)

            // SCROLL DAMPENING

            if (event?.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
                applyScrollDampening()
            }
        }
    }

    private fun applyScrollDampening() {

        val path = Path()
        path.moveTo(500f, 1500f)
        path.lineTo(500f, 1200f)

        val gesture = GestureDescription.Builder()
            .addStroke(
                GestureDescription.StrokeDescription(
                    path,
                    0,
                    350
                )
            )
            .build()

        dispatchGesture(gesture, null, null)
    }

    override fun onInterrupt() {
    }
}