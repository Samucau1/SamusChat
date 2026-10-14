package com.samuschat.data.api

import com.google.gson.Gson
import com.samuschat.util.Session
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Renews access without saving the password or recursively authenticating refresh calls. */
class SessionAuthenticator(
    baseUrl: String,
    private val session: () -> Session?,
    private val save: (Session, String) -> Boolean
) : Authenticator {
    private val refreshUrl = baseUrl.toHttpUrl().resolve("api/auth/refresh")!!
    private val client = OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build()
    private val gson = Gson()

    @Synchronized
    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.priorResponse != null) return null
        val authorization = response.request.header("Authorization") ?: return null
        val current = session() ?: return null
        if (response.request.url.scheme != refreshUrl.scheme || response.request.url.host != refreshUrl.host || response.request.url.port != refreshUrl.port) return null
        if (authorization != "Bearer ${current.token}") {
            return response.request.newBuilder().header("Authorization", "Bearer ${current.token}").build()
        }
        val refresh = current.refreshToken ?: return null
        val request = Request.Builder().url(refreshUrl)
            .post(gson.toJson(mapOf("refreshToken" to refresh)).toRequestBody("application/json".toMediaType())).build()
        val token = client.newCall(request).execute().use {
            if (it.code == 401) return null
            // A temporary server/network failure must not erase the saved login.
            if (!it.isSuccessful) throw IOException("Nao foi possivel renovar a sessao. Tente novamente.")
            val json = gson.fromJson(it.body?.string(), com.google.gson.JsonObject::class.java)
            if (json?.get("success")?.asBoolean != true) throw IOException("Resposta de renovacao invalida")
            json.getAsJsonObject("data")?.get("token")?.asString?.takeIf { value -> value.isNotBlank() }
                ?: throw IOException("Resposta de renovacao sem token")
        }
        if (!save(current, token)) return null
        return response.request.newBuilder().header("Authorization", "Bearer $token").build()
    }
}
