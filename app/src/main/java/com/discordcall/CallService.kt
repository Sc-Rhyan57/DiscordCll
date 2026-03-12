package com.discordcall

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat

class CallService : Service() {

    companion object {
        const val CHANNEL_ID     = "discord_call"
        const val NOTIF_ID       = 1
        const val ACTION_MUTE    = "com.discordcall.MUTE"
        const val ACTION_DEAFEN  = "com.discordcall.DEAFEN"
        const val ACTION_HANGUP  = "com.discordcall.HANGUP"

        fun start(context: Context, channelName: String) {
            val intent = Intent(context, CallService::class.java).apply {
                putExtra("channel_name", channelName)
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, CallService::class.java))
        }
    }

    inner class CallBinder : Binder() {
        fun getService() = this@CallService
    }

    private val binder = CallBinder()
    private var channelName = ""

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        channelName = intent?.getStringExtra("channel_name") ?: "Voice Channel"
        startForeground(NOTIF_ID, buildNotification())
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        super.onDestroy()
    }

    fun updateNotification(muted: Boolean, deafened: Boolean) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIF_ID, buildNotification(muted, deafened))
    }

    private fun buildNotification(muted: Boolean = false, deafened: Boolean = false): Notification {
        val mainIntent  = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val muteIntent  = PendingIntent.getService(this, 1, Intent(this, CallService::class.java).setAction(ACTION_MUTE),   PendingIntent.FLAG_IMMUTABLE)
        val hangupIntent= PendingIntent.getService(this, 2, Intent(this, CallService::class.java).setAction(ACTION_HANGUP), PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Discord Call")
            .setContentText("In voice channel: $channelName")
            .setContentIntent(mainIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(android.R.drawable.ic_lock_silent_mode, if (muted) "Unmute" else "Mute", muteIntent)
            .addAction(android.R.drawable.ic_delete, "Hang Up", hangupIntent)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Discord Call",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Active Discord voice call"
        }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
    }
}
