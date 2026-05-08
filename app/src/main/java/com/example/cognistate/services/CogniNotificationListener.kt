package com.example.cognistate.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import com.example.cognistate.R
import com.example.cognistate.data.database.CogniDatabase
import com.example.cognistate.data.entities.SuppressedNotif
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CogniNotificationListener : NotificationListenerService() {
    private var heldCount = 0

    override fun onNotificationPosted(sbn: StatusBarNotification?) {

        val packageName = sbn?.packageName ?: return

        val redlining = true

        if (
            redlining &&
            (
                packageName == "com.slack" ||
                packageName == "com.microsoft.teams"
            )
        ) {

            heldCount++

            val extras = sbn.notification.extras
            val title = extras.getString("android.title") ?: "No Title"

            val text = extras.getString("android.text") ?: "No Text"
            val db = CogniDatabase.getDatabase(applicationContext)

            CoroutineScope(Dispatchers.IO).launch {

                db.suppressedNotifDao().insert(
                    SuppressedNotif(
                        packageName = packageName,
                        title = title,
                        text = text,
                        timestamp = System.currentTimeMillis()
                    )
                )
            }

            cancelNotification(sbn.key)
            showHeldNotification()
        }
    }

    private fun showHeldNotification() {

        val channelId = "cogni_hold_channel"

        val manager =
            getSystemService(Context.NOTIFICATION_SERVICE)
                    as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "CogniShield",
                NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(
            this,
            channelId
        )
            .setContentTitle("CogniShield")
            .setContentText("$heldCount messages held by CogniShield")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()
        manager.notify(1, notification)
    }
}