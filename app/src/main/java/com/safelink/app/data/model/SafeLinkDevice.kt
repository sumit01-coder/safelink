package com.safelink.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class SafeLinkDevice(
    val type: String = "SAFELINK_DEVICE",
    val deviceId: String,
    val deviceName: String,
    val ip: String,
    val port: Int = 80,
    val relayCount: Int,
    val firmware: String = "1.0.0",

    // UI specific, not necessarily in the JSON response
    val isOnline: Boolean = true,
    val wifiSignal: Int = -50,
    val relays: List<Relay> = emptyList()
)
