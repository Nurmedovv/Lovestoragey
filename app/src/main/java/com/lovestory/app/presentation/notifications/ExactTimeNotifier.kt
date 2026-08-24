package com.lovestory.app.presentation.notifications

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import java.util.Calendar
import java.util.Random
import com.lovestory.app.R
import com.lovestory.app.presentation.notifications.TimeAlarmReceiver

// управляет точными уведомлениями через AlarmManager
object ExactTimeNotifier {

    private const val TAG = "EXACT_TIME_NOTIFIER"
    private const val PREFS_NAME = "notification_prefs"
    private const val KEY_RANDOM_PREFIX = "random_alarm_"

    // настраивает все уведомления при запуске приложения
    fun setup(context: Context) {
        Log.d(TAG, "Настраиваем точные уведомления")
        createNotificationChannel(context)
        scheduleMorningAlarm(context)
        scheduleNightAlarm(context)
        scheduleRandomDailyAlarms(context)
        Log.d(TAG, "Будильники установлены на 7:00, 23:00 и случайные времена в течение дня")
    }

    // создаёт канал для уведомлений (Android 8+)
    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "exact_time_channel",
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notification_channel_desc)
                enableVibration(true)
            }

            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
            Log.d(TAG, "Канал уведомлений создан")
        }
    }

    // устанавливает утренний будильник на 7:00
    private fun scheduleMorningAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 7)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (getTimeInMillis() <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        val intent = Intent(context, TimeAlarmReceiver::class.java).apply {
            putExtra("type", "morning")
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context, 1001, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
        }
        Log.d(TAG, "🌅 Утренний будильник установлен на: ${formatTime(calendar)}")
    }

    // устанавливает вечерний будильник на 23:00
    private fun scheduleNightAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (getTimeInMillis() <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        val intent = Intent(context, TimeAlarmReceiver::class.java).apply {
            putExtra("type", "night")
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context, 1002, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
        }
        Log.d(TAG, "🌙 Вечерний будильник установлен на: ${formatTime(calendar)}")
    }

    // генерирует и устанавливает 1-3 случайных будильника в день
    private fun scheduleRandomDailyAlarms(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        clearOldRandomAlarms(context, prefs)

        val random = Random()
        val notificationsCount = random.nextInt(3) + 1
        Log.d(TAG, "🎲 Сегодня будет $notificationsCount случайных уведомлений")

        val randomTimes = generateRandomTimes(notificationsCount)

        for (i in 0 until notificationsCount) {
            val time = randomTimes[i]
            scheduleSingleRandomAlarm(context, i, time.first, time.second, prefs)
        }
    }

    // генерирует уникальные случайные времена без повторений
    private fun generateRandomTimes(count: Int): List<Pair<Int, Int>> {
        val random = Random()
        val times = mutableSetOf<Pair<Int, Int>>()

        while (times.size < count) {
            val hour = random.nextInt(16) + 8
            val minute = random.nextInt(60)
            times.add(Pair(hour, minute))
        }

        return times.toList()
    }

    // устанавливает один случайный будильник
    private fun scheduleSingleRandomAlarm(context: Context, index: Int, hour: Int, minute: Int, prefs: SharedPreferences) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)

            if (getTimeInMillis() <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        val requestCode = 2000 + index
        val intent = Intent(context, TimeAlarmReceiver::class.java).apply {
            putExtra("type", "random")
            putExtra("random_index", index)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
        }

        prefs.edit().putLong("${KEY_RANDOM_PREFIX}${index}_time", calendar.timeInMillis).apply()

        Log.d(TAG, "🎲 Случайный будильник #${index + 1} установлен на: ${formatTime(calendar)} (${formatTimeHM(hour, minute)})")
    }

    // отменяет все ранее установленные случайные будильники
    private fun clearOldRandomAlarms(context: Context, prefs: SharedPreferences) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        for (i in 0..10) {
            val intent = Intent(context, TimeAlarmReceiver::class.java).apply {
                putExtra("type", "random")
                putExtra("random_index", i)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, 2000 + i, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
        }
        Log.d(TAG, "Очищены старые случайные будильники")
    }

    // форматирует время для логов
    private fun formatTime(calendar: Calendar): String {
        return String.format(java.util.Locale.getDefault(),"%02d:%02d:%02d",
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            calendar.get(Calendar.SECOND))
    }

    // форматирует часы и минуты для логов
    private fun formatTimeHM(hour: Int, minute: Int): String {
        return String.format(java.util.Locale.getDefault(),"%02d:%02d", hour, minute)
    }

    // переустанавливает все будильники
    fun rescheduleAlarms(context: Context) {
        Log.d(TAG, "Переустанавливаем все будильники")
        scheduleMorningAlarm(context)
        scheduleNightAlarm(context)
        scheduleRandomDailyAlarms(context)
    }

    // переустанавливает конкретный будильник (утро/вечер) на следующий день
    fun rescheduleSingleAlarm(context: Context, type: String) {
        Log.d(TAG, "Переустанавливаем будильник: $type")
        when (type) {
            "morning" -> scheduleMorningAlarm(context)
            "night" -> scheduleNightAlarm(context)
        }
    }
}