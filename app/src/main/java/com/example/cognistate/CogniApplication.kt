package com.example.cognistate

import android.app.Application
import android.util.Log
import com.example.cognistate.cogni.engine.CogniEngine
import com.example.cognistate.cogni.sensor.ImuCadenceAnalyser

/**
 * CogniApplication — Application subclass that boots CogniEngine.
 *
 * This is the single startup point for the entire AI pipeline.
 * Palak and Sinchana do NOT need to call CogniEngine.start() anywhere else —
 * it is already running by the time any Activity or Service starts.
 *
 * Registered in AndroidManifest.xml via android:name=".CogniApplication"
 */
class CogniApplication : Application() {

    private val TAG = "CogniApp"

    val imuAnalyzer: ImuCadenceAnalyser
        get() = CogniEngine.imuAnalyzer!!

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "CogniShield application starting — booting CogniEngine")
        CogniEngine.start(applicationContext)
    }

    override fun onTerminate() {
        super.onTerminate()
        CogniEngine.stop()
        Log.i(TAG, "CogniEngine stopped")
    }
}
