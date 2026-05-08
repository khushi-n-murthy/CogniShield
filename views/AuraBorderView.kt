package com.example.cognishield.views

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.view.View

class AuraBorderView(context: Context) : View(context) {
    private val paint = Paint()
    private var alphaValue = 0f
    init {

        paint.style = Paint.Style.STROKE

        paint.strokeWidth = 12f

        paint.color = Color.parseColor("#00D4FF")

        paint.setShadowLayer(
            35f,
            0f,
            0f,
            Color.CYAN
        )

        setLayerType(LAYER_TYPE_SOFTWARE, paint)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        paint.alpha = (255 * alphaValue).toInt()

        canvas.drawRect(
            20f,
            20f,
            width - 20f,
            height - 20f,
            paint
        )
    }

    fun fadeIn() {
        val animator = ValueAnimator.ofFloat(0f, 1f)
        animator.duration = 600
        animator.addUpdateListener {
            alphaValue = it.animatedValue as Float
            invalidate()
        }
        animator.start()
    }
    fun fadeOut() {

        val animator = ValueAnimator.ofFloat(1f, 0f)

        animator.duration = 600
        animator.addUpdateListener {
            alphaValue = it.animatedValue as Float
            invalidate()
        }
        animator.start()
    }
}