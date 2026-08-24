package com.lovestory.app.presentation.common

import android.content.Context
import com.lovestory.app.R

// коллекция романтических фраз для кнопки и случайных уведомлений
object SecretMessages {

    // основные романтические фразы для кнопки "Секретное сообщение"
    fun getMessages(context: Context): List<String> {
        return context.resources.getStringArray(R.array.secret_messages).toList()
    }

    // короткие фразы для случайных уведомлений в течение дня
    fun getRandomNotificationMessages(context: Context): List<String> {
        return context.resources.getStringArray(R.array.random_notification_messages).toList()
    }

    // возвращает случайную романтическую фразу
    fun getRandomMessage(context: Context): String {
        val messages = getMessages(context)
        return messages.randomOrNull() ?: ""
    }

    // возвращает случайную короткую фразу для уведомлений
    fun getRandomShortMessage(context: Context): String {
        val messages = getRandomNotificationMessages(context)
        return messages.randomOrNull() ?: ""
    }
}