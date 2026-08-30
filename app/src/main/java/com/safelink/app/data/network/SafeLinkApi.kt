package com.safelink.app.data.network

import com.safelink.app.data.model.SafeLinkDevice
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface SafeLinkApi {
    @GET("/device/info")
    suspend fun getDeviceInfo(): SafeLinkDevice

    @POST("/relay/{id}/on")
    suspend fun turnRelayOn(@Path("id") relayId: Int): SafeLinkDevice

    @POST("/relay/{id}/off")
    suspend fun turnRelayOff(@Path("id") relayId: Int): SafeLinkDevice
}
