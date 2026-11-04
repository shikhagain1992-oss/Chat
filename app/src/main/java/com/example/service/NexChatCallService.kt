package com.example.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.NexChatApplication
import com.example.R

class NexChatCallService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val callId = intent?.getStringExtra(EXTRA_CALL_ID) ?: ""
        val callerName = intent?.getStringExtra(EXTRA_CALLER_NAME) ?: "NexChat Contact"
        val isVideo = intent?.getBooleanExtra(EXTRA_IS_VIDEO, false) ?: false

        when (action) {
            ACTION_INCOMING_CALL -> {
                startRinging(callId, callerName, isVideo)
            }
            ACTION_ACCEPT_CALL, ACTION_END_CALL -> {
                stopRingingAndRelease()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startRinging(callId: String, callerName: String, isVideo: Boolean) {
        acquireWakeLock()
        playSystemRingtone()
        startVibrating()

        val fullScreenIntent = Intent(this, MainActivity::class.java).apply {
            this.action = "OPEN_CALL"
            putExtra("callId", callId)
            putExtra("callerName", callerName)
            putExtra("isVideo", isVideo)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            1001,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val declineIntent = Intent(this, NexChatCallService::class.java).apply {
            action = ACTION_END_CALL
        }
        val declinePendingIntent = PendingIntent.getService(
            this,
            1002,
            declineIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, NexChatApplication.CHANNEL_CALLS)
            .setContentTitle("Incoming ${if (isVideo) "Video" else "Voice"} Call")
            .setContentText("$callerName is calling you on NexChat Pro")
            .setSmallIcon(android.R.drawable.stat_sys_phone_call)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setContentIntent(fullScreenPendingIntent)
            .setAutoCancel(true)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Decline", declinePendingIntent)
            .addAction(android.R.drawable.stat_sys_phone_call, "Answer", fullScreenPendingIntent)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
                "NexChat:IncomingCallWakeLock"
            ).apply {
                acquire(60 * 1000L) // 1 minute timeout
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire wake lock: ${e.message}")
        }
    }

    private fun playSystemRingtone() {
        try {
            val alert = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            mediaPlayer = MediaPlayer().apply {
                setDataSource(applicationContext, alert)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error playing ringtone: ${e.message}")
        }
    }

    private fun startVibrating() {
        try {
            vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            val pattern = longArrayOf(0, 1000, 1000, 1000)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Vibration error: ${e.message}")
        }
    }

    private fun stopRingingAndRelease() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null

            vibrator?.cancel()
            vibrator = null

            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
            wakeLock = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping ringtone: ${e.message}")
        }
    }

    override fun onDestroy() {
        stopRingingAndRelease()
        super.onDestroy()
    }

    companion object {
        const val TAG = "NexChatCallService"
        const val NOTIFICATION_ID = 2001
        const val ACTION_INCOMING_CALL = "com.example.ACTION_INCOMING_CALL"
        const val ACTION_ACCEPT_CALL = "com.example.ACTION_ACCEPT_CALL"
        const val ACTION_END_CALL = "com.example.ACTION_END_CALL"
        const val EXTRA_CALL_ID = "extra_call_id"
        const val EXTRA_CALLER_NAME = "extra_caller_name"
        const val EXTRA_IS_VIDEO = "extra_is_video"
    }
}
