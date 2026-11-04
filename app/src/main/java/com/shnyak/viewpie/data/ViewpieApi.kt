package com.shnyak.viewpie.data

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface ViewpieApi {
    @GET("api/status") suspend fun status(): StatusDto
    @GET("api/panels") suspend fun panels(): PanelsResponse
    @GET("api/panels/{id}") suspend fun panel(@Path("id") id: String): Map<String, Any?>
    @GET("api/cameras") suspend fun cameras(): List<CameraDto>
    @POST("api/pause") suspend fun pause()
    @POST("api/resume") suspend fun resume()
    @POST("api/grid") suspend fun grid()
    @POST("api/reload") suspend fun reload()
    @POST("api/panels/{id}/activate") suspend fun activatePanel(@Path("id") id: String)
    @POST("api/fullscreen") suspend fun fullscreen(@Body body: Map<String, Any>)
}
