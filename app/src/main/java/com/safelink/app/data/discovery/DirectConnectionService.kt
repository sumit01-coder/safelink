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
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.SocketTimeoutException
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
    var lastError: String? = null
        private set

    /**
     * Tries candidate IPs over the Wi-Fi network (bypassing Android's
     * automatic cellular routing for "no internet" Wi-Fi networks).
     */
    private var boundNetwork: Network? = null
    
    init {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val request = android.net.NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
            
        connectivityManager.requestNetwork(request, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                boundNetwork = network
            }
            override fun onLost(network: Network) {
                if (boundNetwork == network) boundNetwork = null
            }
        })
    }

    suspend fun tryDirectConnect(pairingKey: String): SafeLinkDevice? = withContext(Dispatchers.IO) {
        lastError = null
        val wifiNetwork = boundNetwork ?: getWifiNetwork()

        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket()
            // FIX: bind only this socket to Wi-Fi — avoids the process-wide race condition
            // that bindProcessToNetwork() caused when multiple coroutines ran concurrently.
            if (wifiNetwork != null) {
                wifiNetwork.bindSocket(socket)
            }
            socket.soTimeout = 3000 // 3 seconds wait for response

            val message = "DISCOVER_SAFELINK:$pairingKey".toByteArray()
            // Unicast directly to the ESP32 to bypass broadcast ENETUNREACH routing errors
            val targetAddress = InetAddress.getByName("192.168.4.1")
            val packet = DatagramPacket(message, message.size, targetAddress, 8888)

            socket.send(packet)

            val receiveBuffer = ByteArray(1024)
            val receivePacket = DatagramPacket(receiveBuffer, receiveBuffer.size)
            socket.receive(receivePacket)

            val responseText = String(receivePacket.data, 0, receivePacket.length)
            return@withContext json.decodeFromString<SafeLinkDevice>(responseText).copy(ip = "192.168.4.1")

        } catch (e: SocketTimeoutException) {
            lastError = "UDP Timeout"
            Log.e("DirectConnect", "UDP Timeout waiting for ESP32")
        } catch (e: Exception) {
            lastError = e.javaClass.simpleName + ": " + e.message
            Log.e("DirectConnect", "UDP Error: ${e.message}")
        } finally {
            socket?.close()
        }

        // Fallback: HTTP GET /api/status if UDP timed out
        return@withContext fetchDevice("192.168.4.1", wifiNetwork)
    }

    /** Public: fetch device status from a known IP via the Wi-Fi network. */
    suspend fun fetchStatus(ip: String): SafeLinkDevice? = withContext(Dispatchers.IO) {
        fetchDevice(ip, getWifiNetwork())
    }

    fun hasWifiNetwork(): Boolean {
        return getWifiNetwork() != null
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
            // Open HTTP connection and optionally bind its underlying socket to the Wi-Fi network.
            // We use openConnection on the network's socket factory directly to avoid the
            // process-wide bindProcessToNetwork() race condition.
            val url = URL("http://$ip:80/api/status")
            val connection = if (wifiNetwork != null) {
                wifiNetwork.openConnection(url) as HttpURLConnection
            } else {
                url.openConnection() as HttpURLConnection
            }

            connection.connectTimeout = 3000
            connection.readTimeout    = 5000
            connection.requestMethod  = "GET"

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                json.decodeFromString<SafeLinkDevice>(body).copy(ip = ip)
            } else {
                lastError = "HTTP $responseCode"
                Log.e("DirectConnect", "Failed to fetch from $ip: HTTP $responseCode")
                null
            }
        } catch (e: Exception) {
            lastError = e.javaClass.simpleName + ": " + e.message
            Log.e("DirectConnect", "Exception fetching from $ip: ${e.message}")
            null
        }
    }
}


