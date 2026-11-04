package com.shnyak.viewpie.data

import retrofit2.http.Body
import retrofit2.http.GET\nimport retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

interface ViewpieApi {
    @GET("api/status") suspend fun status(): StatusDto
    @GET("api/panels") suspend fun panels(): PanelsResponse
    @GET("api/panels/{id}") suspend fun panel(@Path("id") id: String): PanelDetailDto
    @GET("api/cameras") suspend fun cameras(): List<CameraDto>
    @GET("api/settings") suspend fun settings(): SettingsDto
    @PUT("api/settings") suspend fun updateSettings(@Body patch: Map<String, Any>)
    @POST("api/cameras/{name}/test") suspend fun testCamera(@Path("name") name: String): CameraTestDto\n    @POST("api/restart") suspend fun restart(@Header("Authorization") authorization: String)
    @POST("api/pause") suspend fun pause()
    @POST("api/resume") suspend fun resume()
    @POST("api/grid") suspend fun grid()
    @POST("api/reload") suspend fun reload()
    @POST("api/panels/{id}/activate") suspend fun activatePanel(@Path("id") id: String)
    @POST("api/fullscreen") suspend fun fullscreen(@Body body: Map<String, Any>)
}
