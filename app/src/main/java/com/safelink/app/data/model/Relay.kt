package com.safelink.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Relay(
    val id: Int = 0,        // Optional — not present in ESP32 JSON; assigned by index locally
    val name: String,
    val state: Boolean
)
