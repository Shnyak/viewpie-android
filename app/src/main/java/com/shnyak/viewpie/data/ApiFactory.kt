package com.shnyak.viewpie.data

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiFactory {
    fun create(host: String, port: Int): ViewpieApi {
        val cleanHost = host.trim().removePrefix("http://").removePrefix("https://").trimEnd('/')
        require(cleanHost.isNotBlank()) { "Укажите IP или имя хоста" }
        require(port in 1..65535) { "Некорректный порт" }
        val client = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
        return Retrofit.Builder()
            .baseUrl("http://$cleanHost:$port/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build().create(ViewpieApi::class.java)
    }
}
