package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.remote.FirebaseManager
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions

/**
 * High-priority Foreground Call Service for NexChat Pro.
 * Ensures incoming calls ring and wake the screen even when the app is minimized or screen is off,
 * and synchronizes Decline/Accept/Remote-Cancel states with Firestore.
 */
class NexChatCallService : Service() {

    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var activeCallId: String = ""
    private var callStatusListener: ListenerRegistration? = null

    companion object {
        const val CHANNEL_ID = "nexchat_pro_calls_channel"
        const val NOTIFICATION_ID = 9001

        const val ACTION_INCOMING_CALL = "com.example.ACTION_INCOMING_CALL"
        const val ACTION_END_CALL = "com.example.ACTION_END_CALL"
        const val ACTION_ACCEPT_CALL = "com.example.ACTION_ACCEPT_CALL"

        const val EXTRA_CALL_ID = "callId"
        const val EXTRA_CALLER_NAME = "callerName"
        const val EXTRA_IS_VIDEO = "isVideo"
    }

    override fun onCreate() {
        super.onCreate()
        createCallNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_INCOMING_CALL -> {
                val callId = intent.getStringExtra(EXTRA_CALL_ID) ?: ""
                val callerName = intent.getStringExtra(EXTRA_CALLER_NAME) ?: "NexChat User"
                val isVideo = intent.getBooleanExtra(EXTRA_IS_VIDEO, false)
                activeCallId = callId

                acquireWakeLock()
                val notification = buildIncomingCallNotification(callId, callerName, isVideo)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }

                startRingingAndVibrating()
                observeCallCancellation(callId)
            }

            ACTION_END_CALL -> {
                val callId = intent.getStringExtra(EXTRA_CALL_ID)?.ifBlank { activeCallId } ?: activeCallId
                if (callId.isNotBlank()) {
                    try {
                        FirebaseManager.firestore.collection("calls").document(callId).set(
                            mapOf(
                                "status" to "rejected",
                                "endedAt" to System.currentTimeMillis()
                            ),
                            SetOptions.merge()
                        )
                    } catch (_: Exception) {}
                }
                cleanupAndStop()
            }

            ACTION_ACCEPT_CALL -> {
                val callId = intent.getStringExtra(EXTRA_CALL_ID)?.ifBlank { activeCallId } ?: activeCallId
                if (callId.isNotBlank()) {
                    try {
                        FirebaseManager.firestore.collection("calls").document(callId).set(
                            mapOf(
                                "status" to "accepted",
                                "acceptedAt" to System.currentTimeMillis()
                            ),
                            SetOptions.merge()
                        )
                    } catch (_: Exception) {}
                }
                stopRingingAndVibrating()
                cleanupAndStop()
            }
        }
        return START_NOT_STICKY
    }

    private fun observeCallCancellation(callId: String) {
        callStatusListener?.remove()
        if (callId.isBlank()) return
        callStatusListener = FirebaseManager.firestore.collection("calls")
            .document(callId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                if (snapshot == null || !snapshot.exists()) {
                    cleanupAndStop()
                    return@addSnapshotListener
                }
                val status = snapshot.getString("status") ?: "ringing"
                if (status in listOf("cancelled", "ended", "rejected", "missed", "timeout", "failed", "accepted", "connected")) {
                    if (status != "ringing") {
                        cleanupAndStop()
                    }
                }
            }
    }

    private fun cleanupAndStop() {
        callStatusListener?.remove()
        callStatusListener = null
        stopRingingAndVibrating()
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "NexChatPro::IncomingCallWakeLock"
            ).apply {
                acquire(45_000L) // Hold up to 45 seconds for ringing
            }
        } catch (_: Exception) {}
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {}
    }

    private fun buildIncomingCallNotification(callId: String, callerName: String, isVideo: Boolean): Notification {
        val fullScreenIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_CALL_ID, callId)
            putExtra(EXTRA_CALLER_NAME, callerName)
            putExtra(EXTRA_IS_VIDEO, isVideo)
            putExtra("acceptFromNotification", true)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            callId.hashCode(),
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val declineIntent = Intent(this, NexChatCallService::class.java).apply {
            action = ACTION_END_CALL
            putExtra(EXTRA_CALL_ID, callId)
        }
        val declinePendingIntent = PendingIntent.getService(
            this,
            callId.hashCode() + 1,
            declineIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val callTypeLabel = if (isVideo) "Incoming Video Call" else "Incoming Voice Call"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(callerName)
            .setContentText(callTypeLabel)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Decline", declinePendingIntent)
            .addAction(android.R.drawable.ic_menu_call, "Answer", fullScreenPendingIntent)
            .build()
    }

    private fun startRingingAndVibrating() {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ringtone = RingtoneManager.getRingtone(applicationContext, uri)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ringtone?.isLooping = true
            }
            ringtone?.play()

            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            val pattern = longArrayOf(0, 1000, 800, 1000)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        } catch (_: Exception) {}
    }

    private fun stopRingingAndVibrating() {
        try {
            ringtone?.stop()
            vibrator?.cancel()
        } catch (_: Exception) {}
    }

    private fun createCallNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "NexChat Pro Incoming Calls",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "High priority alerts for incoming voice and video calls"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                enableVibration(true)
                val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                setSound(
                    ringtoneUri,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        callStatusListener?.remove()
        stopRingingAndVibrating()
        releaseWakeLock()
        super.onDestroy()
    }
}
