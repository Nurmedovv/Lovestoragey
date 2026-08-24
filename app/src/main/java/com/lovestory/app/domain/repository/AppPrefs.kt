package com.lovestory.app.domain.repository

// единый контракт SharedPreferences: имя файла и ключи, общие для нескольких файлов.
// Строковые значения — сохранённые контракты данных, менять их нельзя
object AppPrefs {
    /** имя основного prefs-файла приложения */
    const val PREFS_NAME = "AppSettings"

    // диапазон дат отношений (календарь, настройки, экспорт/импорт)
    const val KEY_START_YEAR = "start_year"
    const val KEY_START_MONTH = "start_month"
    const val KEY_START_DAY = "start_day"
    const val KEY_END_YEAR = "end_year"
    const val KEY_END_MONTH = "end_month"
    const val KEY_END_DAY = "end_day"

    // пользовательский фон
    const val KEY_CUSTOM_BACKGROUND_URI = "custom_background_uri"

    // язык интерфейса и вибрация
    const val KEY_APP_LANGUAGE = "app_language"
    const val KEY_VIBRATION_ENABLED = "vibration_enabled"

    // FCM-токен текущего устройства
    const val KEY_FCM_TOKEN = "fcm_token"
}
