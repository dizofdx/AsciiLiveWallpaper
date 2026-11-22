package com.asciilive.wallpaper

import android.app.Application
import com.asciilive.wallpaper.data.SettingsRepository

class AsciiWallpaperApp : Application() {
    lateinit var settingsRepository: SettingsRepository
        private set

    override fun onCreate() {
        super.onCreate()
        settingsRepository = SettingsRepository(applicationContext)
    }
}
