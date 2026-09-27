package com.safelink.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import com.safelink.app.data.model.SafeLinkDevice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class SettingsState(
    val notificationsEnabled: Boolean = true,
    val darkModeEnabled: Boolean = false,
    val hapticFeedbackEnabled: Boolean = true,
    val pairingKey: String = "123456",
    val customRelayNames: Map<String, String> = emptyMap(),
    // Last-known device — shown instantly on reopen before scan completes
    val lastKnownDevice: SafeLinkDevice? = null
)

class SettingsRepository(private val dataStore: DataStore<Preferences>) {

    private val json = Json { ignoreUnknownKeys = true }

    private object PreferencesKeys {
        val NOTIFICATIONS       = booleanPreferencesKey("notifications_enabled")
        val DARK_MODE           = booleanPreferencesKey("dark_mode_enabled")
        val HAPTIC_FEEDBACK     = booleanPreferencesKey("haptic_feedback_enabled")
        val PAIRING_KEY         = stringPreferencesKey("pairing_key")
        val CUSTOM_RELAY_NAMES  = stringPreferencesKey("custom_relay_names")
        // Persisted device JSON — restores the device card instantly on cold relaunch
        val LAST_KNOWN_DEVICE   = stringPreferencesKey("last_known_device")
    }

    val settingsFlow: Flow<SettingsState> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences())
            else throw exception
        }
        .map { preferences ->
            val notifications  = preferences[PreferencesKeys.NOTIFICATIONS]    ?: true
            val darkMode       = preferences[PreferencesKeys.DARK_MODE]        ?: false
            val hapticFeedback = preferences[PreferencesKeys.HAPTIC_FEEDBACK]  ?: true
            val pairingKey     = preferences[PreferencesKeys.PAIRING_KEY]      ?: "123456"
            val customNamesJson= preferences[PreferencesKeys.CUSTOM_RELAY_NAMES] ?: "{}"
            val customNamesMap = parseJsonToMap(customNamesJson)
            val lastDeviceJson = preferences[PreferencesKeys.LAST_KNOWN_DEVICE]
            val lastDevice     = lastDeviceJson?.let {
                runCatching { json.decodeFromString<SafeLinkDevice>(it) }.getOrNull()
            }
            SettingsState(notifications, darkMode, hapticFeedback, pairingKey, customNamesMap, lastDevice)
        }

    private fun parseJsonToMap(jsonString: String): Map<String, String> {
        return try {
            val jsonObject = org.json.JSONObject(jsonString)
            val map = mutableMapOf<String, String>()
            val keys = jsonObject.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                map[key] = jsonObject.getString(key)
            }
            map
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun mapToJson(map: Map<String, String>): String {
        val jsonObject = org.json.JSONObject()
        for ((key, value) in map) {
            jsonObject.put(key, value)
        }
        return jsonObject.toString()
    }

    suspend fun updateNotifications(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.NOTIFICATIONS] = enabled }
    }

    suspend fun updateDarkMode(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.DARK_MODE] = enabled }
    }

    suspend fun updateHapticFeedback(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.HAPTIC_FEEDBACK] = enabled }
    }

    suspend fun updatePairingKey(key: String) {
        dataStore.edit { it[PreferencesKeys.PAIRING_KEY] = key }
    }

    /**
     * Persists the last successfully connected device so it can be shown
     * immediately on next app launch before the real scan completes.
     */
    suspend fun saveLastKnownDevice(device: SafeLinkDevice) {
        // Mark the cached copy as offline so the UI shows "Reconnecting..." on reopen
        val offlineCopy = device.copy(isOnline = false)
        dataStore.edit { it[PreferencesKeys.LAST_KNOWN_DEVICE] = json.encodeToString(offlineCopy) }
    }

    /** Called once a live scan confirms the device is reachable again. */
    suspend fun clearLastKnownDevice() {
        dataStore.edit { it.remove(PreferencesKeys.LAST_KNOWN_DEVICE) }
    }

    /**
     * Stores a relay's custom display name.
     * Key format: "<deviceId>_<relayId>" where relayId is the 1-based r.id
     * (matching how applyCustomNames looks it up in the ViewModel).
     */
    suspend fun updateCustomRelayName(deviceId: String, relayId: Int, newName: String) {
        dataStore.edit { preferences ->
            val jsonString = preferences[PreferencesKeys.CUSTOM_RELAY_NAMES] ?: "{}"
            val map = parseJsonToMap(jsonString).toMutableMap()
            // Use 1-based relayId to match the applyCustomNames lookup key
            val key = "${deviceId}_${relayId}"
            map[key] = newName
            preferences[PreferencesKeys.CUSTOM_RELAY_NAMES] = mapToJson(map)
        }
    }
}
