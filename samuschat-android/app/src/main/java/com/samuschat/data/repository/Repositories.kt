package com.samuschat.data.repository

import com.google.gson.Gson
import com.samuschat.data.api.ApiService
import com.samuschat.data.model.*
import com.samuschat.util.TokenManager
import com.samuschat.util.Constants
import retrofit2.Response

class ApiFailure(val status: Int, message: String) : Exception(message)
fun <T> Response<ApiResponse<T>>.checked(): ApiResponse<T> {
    val envelope = body()
    if (isSuccessful && envelope?.success == true) return envelope
    val detail = runCatching { Gson().fromJson(errorBody()?.string(), ApiResponse::class.java) }.getOrNull()
    throw ApiFailure(code(), detail?.error ?: detail?.message ?: envelope?.error ?: "Falha na solicitação (${code()})")
}
fun <T> Response<ApiResponse<T>>.data(): T = checked().data ?: error("Resposta sem dados")
class AuthRepository(private val api: ApiService, private val tokens: TokenManager) {
    suspend fun login(email: String, password: String) {
        val token = api.login(LoginRequest(email.trim(), password)).data().token
        tokens.saveSession(token, api.profile("Bearer $token").data().email)
    }
    suspend fun register(username: String, email: String, password: String) {
        val token = api.register(RegisterRequest(username.trim(), email.trim(), password)).data().token
        tokens.saveSession(token, api.profile("Bearer $token").data().email)
    }
}
class ServerRepository(private val api: ApiService, private val tokens: TokenManager) {
    suspend fun list() = api.servers(tokens.authorization()).data()
    suspend fun create(name: String) = api.createServer(tokens.authorization(), ServerRequest(name.trim(), null)).data()
    suspend fun join(id: Long) = api.join(tokens.authorization(), id).checked()
}
class MessageRepository(private val api: ApiService, private val tokens: TokenManager) {
    suspend fun history(id: Long, page: Int) = api.messages(tokens.authorization(), id, page, Constants.PAGE_SIZE).data()
    suspend fun send(id: Long, content: String) = api.send(tokens.authorization(), id, MessageRequest(content)).data()
}
