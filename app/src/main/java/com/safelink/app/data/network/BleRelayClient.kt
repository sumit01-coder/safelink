package com.safelink.app.data.network

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.util.UUID
import kotlin.coroutines.resume

@SuppressLint("MissingPermission")
class BleRelayClient(private val context: Context) {
    private val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
    
    // The UUIDs we defined in the ESP32 firmware
    private val SERVICE_UUID = UUID.fromString("a07498ca-1088-4361-9c3a-23d9a101fcc4")
    private val COMMAND_CHAR_UUID = UUID.fromString("d486d365-27a1-4ee6-85dc-b1187799d123")

    /**
     * Connects to the ESP32 via BLE and writes a toggle command.
     * @param macAddress The MAC address of the ESP32 (device.deviceId)
     * @param relayIndex The index of the relay to toggle
     * @param state The desired state (true=ON, false=OFF). If null, the ESP32 will toggle it.
     * @return true if successful, false otherwise
     */
    suspend fun toggleRelay(macAddress: String, relayIndex: Int, state: Boolean?): Boolean = withContext(Dispatchers.IO) {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) return@withContext false
        
        var gatt: BluetoothGatt? = null
        
        try {
            val device = bluetoothAdapter.getRemoteDevice(macAddress)
            
            // Connect and perform operations with a 10 second timeout
            val success = withTimeoutOrNull(10000L) {
                suspendCancellableCoroutine<Boolean> { continuation ->
                    var isResumed = false
                    
                    val gattCallback = object : BluetoothGattCallback() {
                        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                            if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                                Log.d("BleRelayClient", "Connected to GATT server. Discovering services...")
                                g.discoverServices()
                            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                                Log.d("BleRelayClient", "Disconnected from GATT server.")
                                if (!isResumed) {
                                    isResumed = true
                                    continuation.resume(false)
                                }
                            }
                        }

                        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                            if (status == BluetoothGatt.GATT_SUCCESS) {
                                val service = g.getService(SERVICE_UUID)
                                val characteristic = service?.getCharacteristic(COMMAND_CHAR_UUID)
                                
                                if (characteristic != null) {
                                    // Build the JSON command
                                    val json = JSONObject()
                                    json.put("relayIndex", relayIndex)
                                    if (state != null) {
                                        json.put("state", state)
                                    }
                                    
                                    characteristic.value = json.toString().toByteArray(Charsets.UTF_8)
                                    characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                                    
                                    val writeSuccess = g.writeCharacteristic(characteristic)
                                    Log.d("BleRelayClient", "Write characteristic initiated: $writeSuccess")
                                    
                                    if (!isResumed) {
                                        isResumed = true
                                        // We consider it successful if the write was successfully initiated
                                        continuation.resume(writeSuccess)
                                    }
                                } else {
                                    Log.e("BleRelayClient", "Command characteristic not found!")
                                    if (!isResumed) {
                                        isResumed = true
                                        continuation.resume(false)
                                    }
                                }
                            } else {
                                if (!isResumed) {
                                    isResumed = true
                                    continuation.resume(false)
                                }
                            }
                        }
                    }
                    
                    // Connect to device
                    gatt = device.connectGatt(context, false, gattCallback)
                    
                    // Cleanup on cancellation
                    continuation.invokeOnCancellation {
                        try {
                            gatt?.disconnect()
                            gatt?.close()
                        } catch (e: Exception) {
                            // Ignore
                        }
                    }
                }
            } ?: false
            
            // Give the BLE stack a moment to physically transmit the packet before closing
            if (success) {
                delay(200)
            }
            
            return@withContext success
        } catch (e: Exception) {
            Log.e("BleRelayClient", "Exception during BLE toggle", e)
            return@withContext false
        } finally {
            try {
                gatt?.disconnect()
                gatt?.close()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
}
