package com.lovestory.app

import android.app.Application
import com.lovestory.app.di.AppContainer

// точка входа приложения: владеет контейнером зависимостей
class LoveStoryApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
