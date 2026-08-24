package com.lovestory.app.presentation.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.lovestory.app.R
import com.lovestory.app.domain.repository.AppPrefs
import com.lovestory.app.presentation.main.MainActivity
import com.lovestory.app.di.appContainer

// обработка push-уведомлений от Firebase Cloud Messaging
// принимает сообщения от партнёра и отображает уведомление
class LovestoryMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "FCM token обновлён: $token")
        saveFcmToken(token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "Получено push-уведомление: ${remoteMessage.data}")

        // не показываем уведомление если приложение на переднем плане
        if (isAppInForeground()) return

        val title = remoteMessage.notification?.title
            ?: remoteMessage.data["title"]
            ?: getString(R.string.app_name)
        val body = remoteMessage.notification?.body
            ?: remoteMessage.data["body"]
            ?: ""

        if (body.isNotEmpty()) {
            showNotification(title, body)
        }
    }

    private fun isAppInForeground(): Boolean {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager ?: return false
        @Suppress("DEPRECATION")
        val procInfo = am.runningAppProcesses ?: return false
        for (info in procInfo) {
            if (info.processName == packageName) {
                return info.importance == android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
            }
        }
        return false
    }

    private fun showNotification(title: String, body: String) {
        val channelId = CHANNEL_ID_MESSAGES

        // гарантируем существование канала
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                getString(R.string.notification_channel_messages_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.notification_channel_messages_desc)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300, 200, 300)
                enableLights(true)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            nm.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_calendar", true)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVibrate(longArrayOf(0, 300, 200, 300))
            .setAutoCancel(true)
            .setFullScreenIntent(pendingIntent, true)
            .setContentIntent(pendingIntent)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun saveFcmToken(token: String) {
        val prefs = getSharedPreferences(AppPrefs.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_FCM_TOKEN, token).apply()

        appContainer.coupleRepository.refreshFcmToken()
    }

    companion object {
        private const val TAG = "LovestoryMessaging"
        private const val CHANNEL_ID_MESSAGES = "couple_msgs_headsup"
        private const val NOTIFICATION_ID = 2001
        private const val KEY_FCM_TOKEN = AppPrefs.KEY_FCM_TOKEN

        // создать канал уведомлений для сообщений пары
        // вызывать при старте приложения
        fun createNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID_MESSAGES,
                    context.getString(R.string.notification_channel_messages_name),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = context.getString(R.string.notification_channel_messages_desc)
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 300, 200, 300)
                }
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
                manager.createNotificationChannel(channel)
            }
        }
    }
}
