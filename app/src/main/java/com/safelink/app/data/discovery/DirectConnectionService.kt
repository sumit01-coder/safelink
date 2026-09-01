package com.safelink.app.data.discovery

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.safelink.app.data.model.SafeLinkDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Direct IP discovery for when the phone is connected to the SafeLink AP hotspot.
 * In AP mode the ESP32 ALWAYS has IP 192.168.4.1.
 *
 * KEY FIX: Android 10+ routes all traffic through cellular when a Wi-Fi network
 * has no internet ("Connected without internet"). We must explicitly bind the
 * HTTP client to the Wi-Fi network socket to reach 192.168.4.1.
 */
class DirectConnectionService(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Tries candidate IPs over the Wi-Fi network socket (bypassing Android's
     * automatic cellular routing for "no internet" Wi-Fi networks).
     */
    suspend fun tryDirectConnect(): SafeLinkDevice? = withContext(Dispatchers.IO) {
        val candidateIps = listOf(
            "192.168.4.1",   // ESP32 AP mode — always this
            "192.168.4.2",
            "192.168.1.1",
            "192.168.0.1"
        )
        // Build a client that is FORCED to use the Wi-Fi interface
        val wifiClient = buildWifiClient()
        for (ip in candidateIps) {
            val device = fetchDevice(ip, wifiClient)
            if (device != null) return@withContext device
        }
        null
    }

    /**
     * Creates an OkHttpClient whose socket is bound to the active Wi-Fi network.
     * This forces traffic through Wi-Fi even when Android would normally prefer cellular.
     */
    private fun buildWifiClient(): OkHttpClient {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val wifiNetwork: Network? = connectivityManager.allNetworks.firstOrNull { network ->
            val caps = connectivityManager.getNetworkCapabilities(network) ?: return@firstOrNull false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        }

        return OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .apply {
                if (wifiNetwork != null) {
                    // Bind socket to the Wi-Fi interface regardless of internet availability
                    socketFactory(wifiNetwork.socketFactory)
                }
            }
            .build()
    }

    /** Public: fetch device status from a known IP via the Wi-Fi-bound socket. */
    suspend fun fetchStatus(ip: String): SafeLinkDevice? = withContext(Dispatchers.IO) {
        fetchDevice(ip, buildWifiClient())
    }

    private fun fetchDevice(ip: String, client: OkHttpClient): SafeLinkDevice? {
        return try {
            val request = Request.Builder()
                .url("http://$ip:80/api/status")
                .get()
                .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null
            json.decodeFromString<SafeLinkDevice>(body).copy(ip = ip)
        } catch (e: Exception) {
            null
        }
    }
}

