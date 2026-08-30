package com.safelink.app

import android.app.Application
import com.safelink.app.data.repository.SettingsRepository
import com.safelink.app.data.repository.dataStore

class SafeLinkApplication : Application() {
    lateinit var settingsRepository: SettingsRepository

    override fun onCreate() {
        super.onCreate()
        settingsRepository = SettingsRepository(dataStore)
    }
}
