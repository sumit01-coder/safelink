package com.safelink.app.data.discovery

import com.safelink.app.data.model.SafeLinkDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException

class UdpDiscoveryService {
    private val json = Json { ignoreUnknownKeys = true }

    fun discoverDevices(timeoutMs: Int = 3000, pairingKey: String = "123456"): Flow<SafeLinkDevice> = flow {
        val socket = DatagramSocket()
        socket.broadcast = true
        socket.soTimeout = timeoutMs

        try {
            val sendData = "DISCOVER_SAFELINK:$pairingKey".toByteArray()
            // Android requires specific broadcast IP usually, using 255.255.255.255 as global broadcast
            val broadcastAddress = InetAddress.getByName("255.255.255.255")
            val sendPacket = DatagramPacket(sendData, sendData.size, broadcastAddress, 8888)
            socket.send(sendPacket)

            val receiveData = ByteArray(1024)
            val receivePacket = DatagramPacket(receiveData, receiveData.size)

            // Listen for responses until timeout
            while (true) {
                socket.receive(receivePacket)
                val response = String(receivePacket.data, 0, receivePacket.length)
                
                try {
                    val device = json.decodeFromString<SafeLinkDevice>(response)
                    val deviceWithIp = device.copy(ip = receivePacket.address.hostAddress ?: device.ip)
                    emit(deviceWithIp)
                } catch (e: Exception) {
                    // Ignore parsing errors from unexpected UDP packets
                }
            }
        } catch (e: SocketTimeoutException) {
            // Expected timeout
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            socket.close()
        }
    }.flowOn(Dispatchers.IO)
}
