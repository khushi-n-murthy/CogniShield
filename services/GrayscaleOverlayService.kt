package com.example.cognishield.services

import android.animation.ValueAnimator
import android.app.Service
import android.content.Intent
import android.graphics.*
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.WindowManager

class GrayscaleOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: GrayscaleView

    override fun onCreate() {
        super.onCreate()

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        overlayView = GrayscaleView(this)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.TOP

        windowManager.addView(overlayView, params)

        // REDLINING
        overlayView.setGrayscale()
        overlayView.animateRecovery()
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

    private val paint = Paint()
    private var saturation = 0f

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val matrix = ColorMatrix()

        matrix.setSaturation(saturation)

        paint.colorFilter = ColorMatrixColorFilter(matrix)

        canvas.drawPaint(paint)
    }

    fun setGrayscale() {
        saturation = 0f
        invalidate()
    }

    fun animateRecovery() {
        val animator = ValueAnimator.ofFloat(0f, 1f)

        animator.duration = 800

        animator.addUpdateListener {

            saturation = it.animatedValue as Float

            invalidate()
        }

        animator.start()
    }
}