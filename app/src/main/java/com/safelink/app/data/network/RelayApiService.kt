package com.safelink.app.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Sends HTTP commands to the SafeLink ESP32 device.
 * Uses OkHttp directly (no Retrofit) for lightweight, coroutine-based calls.
 */
class RelayApiService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .build()

    /**
     * Toggles (or sets) a relay on the ESP32.
     * @param ip      IP address of the ESP32 device
     * @param port    HTTP port (default 80)
     * @param name    Relay name (e.g. "Light", "Fan")
     * @param state   Optional desired state. null = toggle, true = ON, false = OFF
     * @return true if the HTTP call succeeded (2xx response), false otherwise
     */
    suspend fun toggleRelay(
        ip: String,
        port: Int = 80,
        name: String? = null,
        relayIndex: Int? = null,
        state: Boolean? = null
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val bodyBuilder = FormBody.Builder()
            if (name != null) bodyBuilder.add("relayName", name)
            if (relayIndex != null) bodyBuilder.add("relayIndex", relayIndex.toString())
            if (state != null) {
                bodyBuilder.add("state", if (state) "1" else "0")
            }

            val request = Request.Builder()
                .url("http://$ip:$port/api/relay/toggle")
                .post(bodyBuilder.build())
                .build()

            val response = client.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            // Network error (timeout, unreachable, etc.)
            false
        }
    }

    suspend fun setTimer(
        ip: String,
        port: Int = 80,
        relayIndex: Int,
        autoOnDelay: Long? = null,
        autoOffDelay: Long? = null
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val bodyBuilder = FormBody.Builder()
            bodyBuilder.add("relayIndex", relayIndex.toString())
            if (autoOnDelay != null) bodyBuilder.add("autoOnDelay", autoOnDelay.toString())
            if (autoOffDelay != null) bodyBuilder.add("autoOffDelay", autoOffDelay.toString())

            val request = Request.Builder()
                .url("http://$ip:$port/api/relay/timer")
                .post(bodyBuilder.build())
                .build()

            val response = client.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Fetches the current status of all relays on the device.
     * Returns the raw JSON string, or null on failure.
     */
    suspend fun fetchStatus(ip: String, port: Int = 80): String? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("http://$ip:$port/api/status")
                .get()
                .build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) response.body?.string() else null
        } catch (e: Exception) {
            null
        }
    }
}
