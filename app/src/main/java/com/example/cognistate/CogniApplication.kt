package com.cognishield

import android.app.Application
import android.util.Log
import com.cognishield.engine.CogniEngine

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
