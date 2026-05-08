package com.example.cognistate.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.*
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat

class GrayscaleOverlayService : Service() {

    private val TAG = "GrayscaleService"
    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: GrayscaleView

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Creating GrayscaleOverlayService")

        startForegroundServiceWithNotification()

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        overlayView = GrayscaleView(this)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.TOP

        try {
            windowManager.addView(overlayView, params)
            Log.i(TAG, "Grayscale view added")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add grayscale view", e)
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
            .setContentText("Dopamine reduction enabled")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()

        startForeground(3, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        windowManager.removeView(overlayView)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}

class GrayscaleView(context: android.content.Context) : View(context) {

    private val paint = Paint().apply {
        colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
    }

    override fun onDraw(canvas: Canvas) {
        // Draw a translucent layer with the grayscale filter
        // Note: Full hardware-accelerated grayscale of external apps usually requires 
        // AccessibilityService or a hardware color transform. This view applies to the overlay layer.
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }
}
