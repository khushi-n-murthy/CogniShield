package com.example.cognistate.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.example.cognistate.views.AuraBorderView

class AuraBorderService : Service() {

    private val TAG = "AuraService"
    private lateinit var windowManager: WindowManager
    private lateinit var auraView: AuraBorderView

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Creating AuraBorderService")
        
        startForegroundServiceWithNotification()

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        auraView = AuraBorderView(this)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, // Better for overlaying system UI
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.TOP

        try {
            windowManager.addView(auraView, params)
            auraView.fadeIn()
            Log.i(TAG, "Aura view added to WindowManager")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add aura view", e)
        }
    }

    private fun startForegroundServiceWithNotification() {
        val channelId = "shield_service"
        val channel = NotificationChannel(
            channelId,
            "CogniShield Protections",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Shield Active")
            .setContentText("Aura protection enabled")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(2, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::windowManager.isInitialized && ::auraView.isInitialized) {
            try {
                windowManager.removeView(auraView)
            } catch (e: Exception) {
                Log.e(TAG, "Error removing aura view", e)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
