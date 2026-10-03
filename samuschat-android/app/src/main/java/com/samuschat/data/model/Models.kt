package com.samuschat.data.model

data class ApiResponse<T>(val success: Boolean, val message: String?, val data: T?, val error: String?)
data class LoginRequest(val email: String, val password: String)
data class RegisterRequest(val username: String, val email: String, val password: String)
data class TokenData(val token: String)
data class User(val id: Long, val username: String, val email: String)
data class Server(val id: Long, val name: String, val description: String?, val ownerEmail: String, val channels: List<Channel>, val createdAt: String)
data class Channel(val id: Long, val name: String, val type: String, val createdAt: String)
data class ServerRequest(val name: String, val description: String?)
data class ChannelRequest(val name: String, val type: String)
data class Message(val id: Long, val content: String?, val senderEmail: String, val senderUsername: String, val channelId: Long, val attachmentUrl: String?, val attachmentType: String?, val createdAt: String, val type: String? = null)
data class MessageRequest(val content: String)
data class MemberResponse(val id: Long, val userEmail: String, val username: String, val role: String)
data class DeviceTokenRequest(val fcmToken: String, val deviceModel: String?)

fun mergeMessages(current: List<Message>, incoming: List<Message>): List<Message> =
    (current + incoming).associateBy { it.id }.values.sortedWith(compareBy<Message> { it.createdAt }.thenBy { it.id })
