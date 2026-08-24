package com.lovestory.app.presentation.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.lovestory.app.R
import com.lovestory.app.presentation.notifications.ExactTimeNotifier
import com.lovestory.app.presentation.common.SecretMessages

// приёмник для сработавших будильников, показывает уведомления
class TimeAlarmReceiver : BroadcastReceiver() {

    private val TAG = "TIME_ALARM_RECEIVER"

    override fun onReceive(context: Context, intent: Intent) {
        val type = intent.getStringExtra("type")
        val randomIndex = intent.getIntExtra("random_index", -1)

        when (type) {
            "morning" -> {
                Log.d(TAG, "🌅 Сработал УТРЕННИЙ будильник в ${getCurrentTime()}")
                showMorningNotification(context)
                ExactTimeNotifier.rescheduleSingleAlarm(context, "morning")
            }
            "night" -> {
                Log.d(TAG, "🌙 Сработал ВЕЧЕРНИЙ будильник в ${getCurrentTime()}")
                showNightNotification(context)
                ExactTimeNotifier.rescheduleSingleAlarm(context, "night")
            }
            "random" -> {
                Log.d(TAG, "🎲 Сработал СЛУЧАЙНЫЙ будильник #${randomIndex + 1} в ${getCurrentTime()}")
                showRandomNotification(context)
            }
            null -> {
                Log.d(TAG, "📱 BOOT_COMPLETED — переустанавливаем все будильники")
                ExactTimeNotifier.rescheduleAlarms(context)
            }
        }
    }

    // показывает утреннее уведомление
    private fun showMorningNotification(context: Context) {
        val notification = NotificationCompat.Builder(context, "exact_time_channel")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(context.getString(R.string.notification_morning_title))
            .setContentText(context.getString(R.string.notification_morning_text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager ?: return
        manager.notify(1001, notification)
        Log.d(TAG, "✅ Утреннее уведомление показано")
    }

    // показывает вечернее уведомление
    private fun showNightNotification(context: Context) {
        val notification = NotificationCompat.Builder(context, "exact_time_channel")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(context.getString(R.string.notification_night_title))
            .setContentText(context.getString(R.string.notification_night_text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager ?: return
        manager.notify(1002, notification)
        Log.d(TAG, "✅ Вечернее уведомление показано")
    }

    // показывает случайное уведомление в течение дня
    private fun showRandomNotification(context: Context) {
        val randomMessage = SecretMessages.getRandomShortMessage(context)

        val notification = NotificationCompat.Builder(context, "exact_time_channel")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(context.getString(R.string.notification_random_title))
            .setContentText(randomMessage)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager ?: return
        manager.notify(System.currentTimeMillis().toInt(), notification)
        Log.d(TAG, "✅ Случайное уведомление показано: \"$randomMessage\"")
    }

    // возвращает текущее время для логов
    private fun getCurrentTime(): String {
        val calendar = java.util.Calendar.getInstance()
        return String.format(java.util.Locale.getDefault(),"%02d:%02d:%02d",
            calendar.get(java.util.Calendar.HOUR_OF_DAY),
            calendar.get(java.util.Calendar.MINUTE),
            calendar.get(java.util.Calendar.SECOND))
    }
}