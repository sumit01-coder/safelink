package com.safelink.app.data.discovery

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import com.safelink.app.data.model.SafeLinkDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

/**
 * Direct IP discovery for when the phone is connected to the SafeLink AP hotspot.
 * In AP mode the ESP32 ALWAYS has IP 192.168.4.1.
 *
 * KEY FIX: Android 10+ routes all traffic through cellular when a Wi-Fi network
 * has no internet ("Connected without internet"). We must explicitly bind the
 * HTTP request to the Wi-Fi network to reach 192.168.4.1.
 */
class DirectConnectionService(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Tries candidate IPs over the Wi-Fi network (bypassing Android's
     * automatic cellular routing for "no internet" Wi-Fi networks).
     */
    suspend fun tryDirectConnect(): SafeLinkDevice? = withContext(Dispatchers.IO) {
        val candidateIps = listOf(
            "192.168.4.1",   // ESP32 AP mode — always this
            "192.168.4.2",
            "192.168.1.1",
            "192.168.0.1"
        )
        val wifiNetwork = getWifiNetwork()
        for (ip in candidateIps) {
            val device = fetchDevice(ip, wifiNetwork)
            if (device != null) return@withContext device
        }
        null
    }

    /** Public: fetch device status from a known IP via the Wi-Fi network. */
    suspend fun fetchStatus(ip: String): SafeLinkDevice? = withContext(Dispatchers.IO) {
        fetchDevice(ip, getWifiNetwork())
    }

    private fun getWifiNetwork(): Network? {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return connectivityManager.allNetworks.firstOrNull { network ->
            val caps = connectivityManager.getNetworkCapabilities(network) ?: return@firstOrNull false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        }
    }

    private fun fetchDevice(ip: String, wifiNetwork: Network?): SafeLinkDevice? {
        return try {
            val url = URL("http://$ip:80/api/status")
            val connection = if (wifiNetwork != null) {
                // Canonical Android way to force a connection over a specific Network
                wifiNetwork.openConnection(url) as HttpURLConnection
            } else {
                url.openConnection() as HttpURLConnection
            }
            
            connection.connectTimeout = 3000
            connection.readTimeout = 5000
            connection.requestMethod = "GET"
            
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                json.decodeFromString<SafeLinkDevice>(body).copy(ip = ip)
            } else {
                Log.e("DirectConnect", "Failed to fetch from $ip: HTTP $responseCode")
                null
            }
        } catch (e: Exception) {
            Log.e("DirectConnect", "Exception fetching from $ip: ${e.message}")
            null
        }
    }
}


