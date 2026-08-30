package com.safelink.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class SettingsState(
    val notificationsEnabled: Boolean = true,
    val darkModeEnabled: Boolean = false,
    val hapticFeedbackEnabled: Boolean = true,
    val pairingKey: String = "123456"
)

class SettingsRepository(private val dataStore: DataStore<Preferences>) {

    private object PreferencesKeys {
        val NOTIFICATIONS = booleanPreferencesKey("notifications_enabled")
        val DARK_MODE = booleanPreferencesKey("dark_mode_enabled")
        val HAPTIC_FEEDBACK = booleanPreferencesKey("haptic_feedback_enabled")
        val PAIRING_KEY = stringPreferencesKey("pairing_key")
    }

    val settingsFlow: Flow<SettingsState> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val notifications = preferences[PreferencesKeys.NOTIFICATIONS] ?: true
            val darkMode = preferences[PreferencesKeys.DARK_MODE] ?: false
            val hapticFeedback = preferences[PreferencesKeys.HAPTIC_FEEDBACK] ?: true
            val pairingKey = preferences[PreferencesKeys.PAIRING_KEY] ?: "123456"
            SettingsState(notifications, darkMode, hapticFeedback, pairingKey)
        }

    suspend fun updateNotifications(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.NOTIFICATIONS] = enabled
        }
    }

    suspend fun updateDarkMode(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.DARK_MODE] = enabled
        }
    }

    suspend fun updateHapticFeedback(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.HAPTIC_FEEDBACK] = enabled
        }
    }

    suspend fun updatePairingKey(key: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.PAIRING_KEY] = key
        }
    }
}
