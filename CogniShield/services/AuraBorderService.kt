package com.example.cognishield.services

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import com.example.cognishield.views.AuraBorderView

class AuraBorderService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var auraView: AuraBorderView

    override fun onCreate() {
        super.onCreate()

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        auraView = AuraBorderView(this)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.TOP

        windowManager.addView(auraView, params)

        auraView.fadeIn()
    }

    override fun onDestroy() {
        super.onDestroy()
        windowManager.removeView(auraView)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}