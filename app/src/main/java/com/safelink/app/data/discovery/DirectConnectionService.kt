package com.safelink.app.data.discovery

import com.safelink.app.data.model.SafeLinkDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Direct IP discovery for when the phone is connected to the SafeLink AP hotspot.
 * In AP mode the ESP32 ALWAYS has IP 192.168.4.1, so we can skip BLE entirely.
 */
class DirectConnectionService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Tries a list of candidate IPs (AP default + common router ranges) and returns
     * the first SafeLinkDevice that responds, or null if none found.
     */
    suspend fun tryDirectConnect(): SafeLinkDevice? = withContext(Dispatchers.IO) {
        val candidateIps = listOf(
            "192.168.4.1",   // ESP32 AP mode default — always try this first
            "192.168.1.1",   // Common router IP if ESP32 in STA mode
            "192.168.0.1"
        )
        for (ip in candidateIps) {
            val device = fetchDevice(ip)
            if (device != null) return@withContext device
        }
        null
    }

    private fun fetchDevice(ip: String): SafeLinkDevice? {
        return try {
            val request = Request.Builder()
                .url("http://$ip:80/api/status")
                .get()
                .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null
            json.decodeFromString<SafeLinkDevice>(body)
                .copy(ip = ip)
        } catch (e: Exception) {
            null
        }
    }
}
