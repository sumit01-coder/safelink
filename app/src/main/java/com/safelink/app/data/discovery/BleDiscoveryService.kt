package com.safelink.app.data.discovery

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.os.ParcelUuid
import android.util.Log
import com.safelink.app.data.model.SafeLinkDevice
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.UUID

@SuppressLint("MissingPermission")
class BleDiscoveryService {
    private val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
    private val scanner = bluetoothAdapter?.bluetoothLeScanner
    private val safeLinkServiceUuid = ParcelUuid(UUID.fromString("a07498ca-1088-4361-9c3a-23d9a101fcc4"))

    fun discoverDevices(timeoutMs: Long = 10000, pairingKey: String = "123456"): Flow<SafeLinkDevice> = callbackFlow {
        if (scanner == null) {
            close()
            return@callbackFlow
        }

        val scanFilter = ScanFilter.Builder()
            .setServiceUuid(safeLinkServiceUuid)
            .build()

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        val scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                result?.scanRecord?.let { record ->
                    // Manufacturer ID is 0xFFFF (65535)
                    val mData = record.getManufacturerSpecificData(0xFFFF)
                    if (mData != null && mData.size >= 4) {
                        // Decode IP (little endian)
                        val ip1 = mData[0].toUByte().toInt()
                        val ip2 = mData[1].toUByte().toInt()
                        val ip3 = mData[2].toUByte().toInt()
                        val ip4 = mData[3].toUByte().toInt()
                        val ipAddress = "$ip1.$ip2.$ip3.$ip4"

                        // Decode pairing key
                        var receivedKey = ""
                        if (mData.size > 4) {
                            receivedKey = String(mData.sliceArray(4 until mData.size))
                        }

                        if (receivedKey == pairingKey) {
                            // Valid device found!
                            val deviceName = record.deviceName ?: "SafeLink Hub"
                            trySend(
                                SafeLinkDevice(
                                    deviceId = result.device.address,
                                    deviceName = deviceName,
                                    ip = ipAddress,
                                    port = 80,
                                    relayCount = 8, // Need HTTP status to get actual count later
                                    firmware = "BLE",
                                    wifiSignal = result.rssi,
                                    relays = emptyList() // Will be fetched via HTTP
                                )
                            )
                        }
                    }
                }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e("BleDiscovery", "Scan failed with error: \$errorCode")
                close(Exception("BLE Scan Failed: \$errorCode"))
            }
        }

        try {
            scanner.startScan(listOf(scanFilter), scanSettings, scanCallback)
        } catch (e: Exception) {
            close(e)
        }

        // Close when the flow collector stops collecting (or on timeout from caller)
        awaitClose {
            try {
                scanner.stopScan(scanCallback)
            } catch (e: Exception) {
                // Ignore if bluetooth turned off
            }
        }
    }
}
