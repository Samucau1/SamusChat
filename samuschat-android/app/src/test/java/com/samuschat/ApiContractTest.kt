package com.samuschat

import com.samuschat.data.api.ApiService
import com.samuschat.data.model.*
import com.samuschat.data.repository.*
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class ApiContractTest {
    @Test fun uploadsMultipartWithOptionalCaptionAndAuthorization() = runTest {
        val server = MockWebServer()
        try {
            val api = Retrofit.Builder().baseUrl(server.url("/"))
                .addConverterFactory(GsonConverterFactory.create()).build().create(ApiService::class.java)
            for (caption in listOf("Legenda do arquivo", null)) {
                server.enqueue(MockResponse().setBody("""{"success":true,"data":{"id":71,"content":null,"senderEmail":"a@test","senderUsername":"A","channelId":9,"attachmentUrl":"/uploads/test.txt","attachmentType":"FILE","createdAt":"2026-10-06T10:00:00"}}"""))
                val file = okhttp3.MultipartBody.Part.createFormData("file", "test.txt",
                    "Arquivo de teste".toRequestBody("text/plain".toMediaType()))
                val text = caption?.toRequestBody("text/plain".toMediaType())
                assertEquals("FILE", api.uploadAttachment("Bearer jwt", 9, file, text).data().attachmentType)
                val request = server.takeRequest()
                assertEquals("POST", request.method)
                assertEquals("/api/channels/9/upload", request.path)
                assertEquals("Bearer jwt", request.getHeader("Authorization"))
                assertTrue(request.getHeader("Content-Type")!!.startsWith("multipart/form-data; boundary="))
                val body = request.body.readUtf8()
                assertTrue(body.contains("name=\"file\"; filename=\"test.txt\""))
                assertTrue(body.contains("Arquivo de teste"))
                assertEquals(caption != null, body.contains("name=\"content\""))
                if (caption != null) assertTrue(body.contains(caption))
            }
        } finally { server.shutdown() }
    }
    @Test fun recoveryPreservesLeadingZerosAndUsesAuthorizationOnlyForReset() = runTest {
        val server = MockWebServer()
        try {
            val api = Retrofit.Builder().baseUrl(server.url("/")).addConverterFactory(GsonConverterFactory.create()).build().create(ApiService::class.java)
            server.enqueue(MockResponse().setBody("""{"success":true,"data":"Solicitado"}"""))
            assertEquals("Solicitado", api.requestRecovery(RecoveryEmail("test@example.com")).data())
            assertEquals("/api/auth/password/request", server.takeRequest().path)
            server.enqueue(MockResponse().setBody("""{"success":true,"data":{"resetToken":"authorization"}}"""))
            val token = api.verifyRecovery(RecoveryCode("test@example.com", "0012")).data().resetToken
            val verifyRequest = server.takeRequest()
            assertEquals("/api/auth/password/verify", verifyRequest.path)
            assertEquals("0012", com.google.gson.JsonParser().parse(verifyRequest.body.readUtf8()).asJsonObject["code"].asString)
            server.enqueue(MockResponse().setBody("""{"success":true,"data":"Atualizada"}"""))
            assertEquals("Atualizada", api.resetPassword(RecoveryReset("test@example.com", token, "new-password")).data())
            val resetRequest = server.takeRequest()
            assertEquals("/api/auth/password/reset", resetRequest.path)
            assertEquals("authorization", com.google.gson.JsonParser().parse(resetRequest.body.readUtf8()).asJsonObject["resetToken"].asString)
            assertNull(resetRequest.getHeader("Authorization"))
        } finally { server.shutdown() }
    }
    @Test fun googleLoginUsesVerifiedIdTokenContract() = runTest {
        val server = MockWebServer()
        try {
            val api = Retrofit.Builder().baseUrl(server.url("/")).addConverterFactory(GsonConverterFactory.create()).build().create(ApiService::class.java)
            server.enqueue(MockResponse().setBody("""{"success":true,"data":{"token":"session"}}"""))
            assertEquals("session", api.googleLogin(GoogleLogin("google-id-token", "current-password")).data().token)
            val request = server.takeRequest()
            assertEquals("/api/auth/google", request.path)
            val body = com.google.gson.JsonParser().parse(request.body.readUtf8()).asJsonObject
            assertEquals("google-id-token", body["idToken"].asString)
            assertEquals("current-password", body["password"].asString)
        } finally { server.shutdown() }
    }
    @Test fun callRoomIncludesParticipantsAndPrivatePeerSessions() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setBody("""{"success":true,"data":{"channelId":9,"capacity":6,"participants":[{"email":"a@test","username":"A"},{"email":"b@test","username":"B"}],"sessions":[{"id":"pair","caller":"a@test","callee":"b@test","state":"CONNECTING","channelId":9}]}}"""))
            val api = Retrofit.Builder().baseUrl(server.url("/")).addConverterFactory(GsonConverterFactory.create()).build().create(ApiService::class.java)
            val room = api.joinCallRoom("Bearer jwt", 9).data()
            assertEquals(2, room.participants.size)
            assertEquals(9L, room.sessions.single().channelId)
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/api/channels/9/call/join", request.path)
            assertEquals("Bearer jwt", request.getHeader("Authorization"))
        } finally { server.shutdown() }
    }
    @Test fun createsVoiceChannelsThroughAuthenticatedServerEndpoint() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setBody("""{"success":true,"data":{"id":9,"name":"Sala","type":"VOICE","createdAt":"2026-10-02"}}"""))
            val api = Retrofit.Builder().baseUrl(server.url("/")).addConverterFactory(GsonConverterFactory.create()).build().create(ApiService::class.java)
            assertEquals("VOICE", api.createChannel("Bearer jwt", 3, ChannelRequest("Sala", "VOICE")).data().type)
            val request = server.takeRequest()
            assertEquals("/api/servers/3/channels", request.path)
            assertEquals("Bearer jwt", request.getHeader("Authorization"))
            assertTrue(request.body.readUtf8().contains("\"type\":\"VOICE\""))
        } finally { server.shutdown() }
    }
    @Test fun profileNameUpdateUsesAuthenticatedBackendContract() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setBody("""{"success":true,"data":{"id":1,"username":"Ana Silva","email":"ana@example.com"}}"""))
            val api = Retrofit.Builder().baseUrl(server.url("/"))
                .addConverterFactory(GsonConverterFactory.create()).build().create(ApiService::class.java)
            assertEquals("Ana Silva", api.updateUsername("Bearer jwt", "Ana Silva").data().username)
            val request = server.takeRequest()
            assertEquals("PUT", request.method)
            assertEquals("/api/users/me/username", request.requestUrl!!.encodedPath)
            assertEquals("Ana Silva", request.requestUrl!!.queryParameter("newUsername"))
            assertEquals("Bearer jwt", request.getHeader("Authorization"))
        } finally { server.shutdown() }
    }

    @Test fun loginAndHistoryUseBackendEnvelopeAndPagination() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setBody("""{"success":true,"data":{"token":"jwt"}}"""))
            server.enqueue(MockResponse().setBody("""{"success":true,"data":[{"id":7,"content":"oi","senderEmail":"a@b.com","senderUsername":"a","channelId":2,"createdAt":"2026-09-28T10:00:00"}]}"""))
            val api = Retrofit.Builder().baseUrl(server.url("/")).addConverterFactory(GsonConverterFactory.create()).build().create(ApiService::class.java)
            assertEquals("jwt", api.login(LoginRequest("a@b.com", "password")).data().token)
            assertEquals("/api/auth/login", server.takeRequest().path)
            assertEquals(7L, api.messages("Bearer jwt", 2, 1, 50).data().single().id)
            val request = server.takeRequest()
            assertEquals("/api/channels/2/messages?page=1&size=50", request.path)
            assertEquals("Bearer jwt", request.getHeader("Authorization"))
        } finally { server.shutdown() }
    }
    @Test fun errorBodyIsShownInsteadOfTreatingFailureAsSuccess() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(401).setBody("""{"success":false,"error":"Credenciais invalidas"}"""))
            val api = Retrofit.Builder().baseUrl(server.url("/")).addConverterFactory(GsonConverterFactory.create()).build().create(ApiService::class.java)
            try { api.login(LoginRequest("x", "x")).data(); fail("Expected ApiFailure") }
            catch (e: ApiFailure) { assertEquals(401, e.status); assertEquals("Credenciais invalidas", e.message) }
        } finally { server.shutdown() }
    }
    @Test fun historyAndRealtimeAreMergedWithoutDuplicatesOrLostAttachments() {
        val first = Message(1, "oi", "a", "A", 2, null, null, "2026-09-28T10:00:00")
        val attachment = first.copy(id = 2, attachmentUrl = "/uploads/a.png", attachmentType = "IMAGE", createdAt = "2026-09-28T10:01:00")
        val merged = mergeMessages(listOf(attachment), listOf(first, attachment))
        assertEquals(listOf(1L, 2L), merged.map { it.id })
        assertEquals("/uploads/a.png", merged.last().attachmentUrl)
    }

    @Test fun standardizedSecurityErrorsRemainCompatibleWithAndroid() = runTest {
        val server = MockWebServer()
        try {
            val api = Retrofit.Builder().baseUrl(server.url("/"))
                .addConverterFactory(GsonConverterFactory.create()).build().create(ApiService::class.java)
            for ((status, message) in listOf(401 to "Nao autorizado", 429 to "Muitas requisicoes. Tente novamente em 30 segundos.")) {
                server.enqueue(MockResponse().setResponseCode(status).setBody(
                    """{"success":false,"error":"$message","timestamp":"2026-09-28T10:00:00"}"""
                ))
                try {
                    api.messages("Bearer expired", 1, 0, 50).data()
                    fail("Expected ApiFailure for $status")
                } catch (e: ApiFailure) {
                    assertEquals(status, e.status)
                    assertEquals(message, e.message)
                }
            }
        } finally { server.shutdown() }
    }
}
