package com.example.facemesh.gaze

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class GazeService : LifecycleService() {

    companion object {
        private const val TAG = "GazeService"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "GazeServiceChannel"

        /**
         * Starts the GazeService as a Foreground Service.
         */
        fun startService(context: Context) {
            val intent = Intent(context, GazeService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /**
         * Stops the GazeService.
         */
        fun stopService(context: Context) {
            val intent = Intent(context, GazeService::class.java)
            context.stopService(intent)
        }
    }

    private lateinit var cameraExecutor: ExecutorService
    private lateinit var gazeAnalyzer: GazeAnalyzer

    override fun onCreate() {
        super.onCreate()
        // Initialize an executor for camera image analysis
        cameraExecutor = Executors.newSingleThreadExecutor()
        // Initialize a single instance of the analyzer
        gazeAnalyzer = GazeAnalyzer(lifecycleScope)
        
        // Expose it to the repository
        GazeRepository.analyzer = gazeAnalyzer
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        
        // Setup and start the foreground service notification
        startForegroundNotification()

        // Initialize and bind CameraX use cases
        startCamera()

        return START_STICKY
    }

    private fun startForegroundNotification() {
        createNotificationChannel()

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Face Tracking Active")
            .setContentText("Camera is running in the background for gaze analysis")
            .setSmallIcon(android.R.drawable.ic_menu_camera) // Using default system icon for now
            .setPriority(NotificationCompat.PRIORITY_LOW) // Low priority so it doesn't make a sound
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Gaze Tracking Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Used for running CameraX in the background for gaze tracking"
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            try {
                // Used to bind the lifecycle of cameras to the lifecycle owner
                val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

                // Select front camera as a default
                val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

                // Prepare ImageAnalysis use case
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                // Forward frames to the single instance of GazeAnalyzer
                imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                    gazeAnalyzer.analyze(imageProxy)
                }

                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to the service's lifecycle (this service extends LifecycleService)
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, imageAnalysis
                )

            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clear it from the repository
        GazeRepository.analyzer = null
        
        // Shutdown the analyzer's coroutines
        if (::gazeAnalyzer.isInitialized) {
            gazeAnalyzer.shutdown()
        }
        // Shutdown the executor when the service is destroyed
        cameraExecutor.shutdown()
    }
}
