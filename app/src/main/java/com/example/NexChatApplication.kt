package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions

class NexChatApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        initFirebase()
        createNotificationChannels()
    }

    private fun initFirebase() {
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                val options = FirebaseOptions.Builder()
                    .setProjectId("nexchat-pro-8e480")
                    .setApplicationId("1:418090114272:android:5fa7216cbe67cb724c94bf")
                    .setApiKey("AIzaSyAb6XQuA5QqqoglYSvtQ-cjpOuuV3s_vwo")
                    .setDatabaseUrl("https://nexchat-pro-8e480-default-rtdb.firebaseio.com")
                    .setStorageBucket("nexchat-pro-8e480.firebasestorage.app")
                    .setGcmSenderId("418090114272")
                    .build()
                FirebaseApp.initializeApp(this, options)
                Log.d("NexChatApp", "Firebase initialized successfully with options")
            } else {
                Log.d("NexChatApp", "Firebase already initialized")
            }
        } catch (e: Exception) {
            Log.e("NexChatApp", "Firebase init error: ${e.message}", e)
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Messages channel
            val messageChannel = NotificationChannel(
                CHANNEL_MESSAGES,
                "NexChat Messages",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Incoming chat and group message alerts"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 250, 250)
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_INSTANT)
                        .build()
                )
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }

            // Calls channel
            val callChannel = NotificationChannel(
                CHANNEL_CALLS,
                "NexChat Incoming Calls",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Incoming voice and video call notifications"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 1000, 1000, 1000, 1000)
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .build()
                )
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }

            notificationManager.createNotificationChannel(messageChannel)
            notificationManager.createNotificationChannel(callChannel)
        }
    }

    companion object {
        const val CHANNEL_MESSAGES = "nexchat_messages_high"
        const val CHANNEL_CALLS = "nexchat_calls_high"
    }
}
