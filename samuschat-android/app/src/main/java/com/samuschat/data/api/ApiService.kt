package com.samuschat.data.api

import com.samuschat.data.model.*
import retrofit2.Response
import retrofit2.http.*

interface ApiService {
    @POST("api/auth/login") suspend fun login(@Body request: LoginRequest): Response<ApiResponse<TokenData>>
    @POST("api/auth/register") suspend fun register(@Body request: RegisterRequest): Response<ApiResponse<TokenData>>
    @GET("api/users/me") suspend fun profile(@Header("Authorization") token: String): Response<ApiResponse<User>>
    @GET("api/servers") suspend fun servers(@Header("Authorization") token: String): Response<ApiResponse<List<Server>>>
    @GET("api/servers/{id}") suspend fun server(@Header("Authorization") token: String, @Path("id") id: Long): Response<ApiResponse<Server>>
    @POST("api/servers") suspend fun createServer(@Header("Authorization") token: String, @Body request: ServerRequest): Response<ApiResponse<Server>>
    @POST("api/servers/{id}/join") suspend fun join(@Header("Authorization") token: String, @Path("id") id: Long): Response<ApiResponse<String>>
    @GET("api/servers/{id}/members") suspend fun members(@Header("Authorization") token: String, @Path("id") id: Long): Response<ApiResponse<List<MemberResponse>>>
    @GET("api/channels/{id}/messages") suspend fun messages(@Header("Authorization") token: String, @Path("id") id: Long, @Query("page") page: Int, @Query("size") size: Int): Response<ApiResponse<List<Message>>>
    @POST("api/channels/{id}/messages") suspend fun send(@Header("Authorization") token: String, @Path("id") id: Long, @Body request: MessageRequest): Response<ApiResponse<Message>>
    @DELETE("api/channels/{id}/messages/{messageId}") suspend fun delete(@Header("Authorization") token: String, @Path("id") id: Long, @Path("messageId") messageId: Long): Response<ApiResponse<Unit>>
    @POST("api/devices/register") suspend fun registerDevice(@Header("Authorization") token: String, @Body request: DeviceTokenRequest): Response<ApiResponse<Unit>>
    @DELETE("api/devices/unregister") suspend fun unregisterDevice(@Header("Authorization") token: String, @Query("fcmToken") fcmToken: String): Response<ApiResponse<Unit>>
}
