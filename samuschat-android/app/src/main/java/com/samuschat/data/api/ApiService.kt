package com.samuschat.data.api

import com.samuschat.data.model.*
import retrofit2.Response
import retrofit2.http.*

interface ApiService {
    @Multipart
    @POST("api/channels/{id}/upload")
    suspend fun uploadAttachment(@Header("Authorization") token: String, @Path("id") id: Long,
        @Part file: okhttp3.MultipartBody.Part, @Part("content") content: okhttp3.RequestBody?): Response<ApiResponse<Message>>
    @GET("api/direct-messages") suspend fun directMessages(@Header("Authorization") token: String, @Query("contact") contact: String, @Query("page") page: Int): Response<ApiResponse<List<DirectMessage>>>
    @POST("api/direct-messages") suspend fun sendDirectMessage(@Header("Authorization") token: String, @Query("contact") contact: String, @Body request: MessageRequest): Response<ApiResponse<DirectMessage>>
    @POST("api/auth/password/request") suspend fun requestRecovery(@Body request: RecoveryEmail): Response<ApiResponse<String>>
    @POST("api/auth/password/verify") suspend fun verifyRecovery(@Body request: RecoveryCode): Response<ApiResponse<ResetAuthorization>>
    @POST("api/auth/password/reset") suspend fun resetPassword(@Body request: RecoveryReset): Response<ApiResponse<String>>
    @POST("api/auth/google") suspend fun googleLogin(@Body request: GoogleLogin): Response<ApiResponse<TokenData>>
    @POST("api/channels/{id}/call/join") suspend fun joinCallRoom(@Header("Authorization") token: String, @Path("id") id: Long): Response<ApiResponse<com.samuschat.ui.call.CallRoom>>
    @GET("api/channels/{id}/call") suspend fun callRoom(@Header("Authorization") token: String, @Path("id") id: Long): Response<ApiResponse<com.samuschat.ui.call.CallRoom>>
    @POST("api/channels/{id}/call/leave") suspend fun leaveCallRoom(@Header("Authorization") token: String, @Path("id") id: Long): Response<ApiResponse<String>>
    @POST("api/servers/{id}/channels") suspend fun createChannel(@Header("Authorization") token: String, @Path("id") id: Long, @Body request: ChannelRequest): Response<ApiResponse<Channel>>
    @GET("api/calls/contacts") suspend fun callContacts(@Header("Authorization") token: String): Response<ApiResponse<List<com.samuschat.ui.call.CallContact>>>
    @GET("api/calls/ice") suspend fun callIce(@Header("Authorization") token: String): Response<ApiResponse<List<com.samuschat.ui.call.IceServerConfig>>>
    @GET("api/calls") suspend fun currentCalls(@Header("Authorization") token: String): Response<ApiResponse<List<com.samuschat.ui.call.CallSession>>>
    @GET("api/calls/{id}") suspend fun call(@Header("Authorization") token: String, @Path("id") id: String): Response<ApiResponse<com.samuschat.ui.call.CallSession>>
    @POST("api/calls") suspend fun inviteCall(@Header("Authorization") token: String, @Body request: com.samuschat.ui.call.CallInvite): Response<ApiResponse<com.samuschat.ui.call.CallSession>>
    @POST("api/calls/{id}") suspend fun callAction(@Header("Authorization") token: String, @Path("id") id: String, @Body request: com.samuschat.ui.call.CallAction): Response<ApiResponse<com.samuschat.ui.call.CallSession>>
    @POST("api/auth/login") suspend fun login(@Body request: LoginRequest): Response<ApiResponse<TokenData>>
    @POST("api/auth/register") suspend fun register(@Body request: RegisterRequest): Response<ApiResponse<TokenData>>
    @GET("api/users/me") suspend fun profile(@Header("Authorization") token: String): Response<ApiResponse<User>>
    @PUT("api/users/me/username") suspend fun updateUsername(@Header("Authorization") token: String, @Query("newUsername") username: String): Response<ApiResponse<User>>
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
