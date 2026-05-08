package com.example.cognistate.haptic

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

class HapticController(context: Context) {

    private val vibrator: Vibrator =

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {

            val manager =
                context.getSystemService(
                    Context.VIBRATOR_MANAGER_SERVICE
                ) as VibratorManager

            manager.defaultVibrator

        } else {

            @Suppress("DEPRECATION")

            context.getSystemService(
                Context.VIBRATOR_SERVICE
            ) as Vibrator
        }

    fun vibrateAlert() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            vibrator.vibrate(
                VibrationEffect.createOneShot(
                    300,
                    180
                )
            )

        } else {

            @Suppress("DEPRECATION")

            vibrator.vibrate(300)
        }
    }
}