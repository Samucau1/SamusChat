package com.samuschat.data.api

import com.samuschat.BuildConfig
import com.samuschat.util.Constants
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

object RetrofitClient {
    fun create(tokens: com.samuschat.util.TokenManager): ApiService {
        val logging = HttpLoggingInterceptor().apply {
            redactHeader("Authorization")
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
        }
        return Retrofit.Builder().baseUrl(Constants.BASE_URL)
            .client(OkHttpClient.Builder().authenticator(SessionAuthenticator(Constants.BASE_URL,
                { kotlinx.coroutines.runBlocking { tokens.session.first() } },
                { previous, token -> kotlinx.coroutines.runBlocking { tokens.renewSession(previous, token) } }
            )).addInterceptor(logging).connectTimeout(30, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build())
            .addConverterFactory(GsonConverterFactory.create()).build().create(ApiService::class.java)
    }
}
