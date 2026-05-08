package com.example.cognistate

import android.app.Application
import com.example.cognistate.imu.IMUAnalyzer

class CogniApplication : Application() {

    lateinit var imuAnalyzer: IMUAnalyzer

    override fun onCreate() {
        super.onCreate()

        imuAnalyzer = IMUAnalyzer(this)

        imuAnalyzer.start()
    }
}