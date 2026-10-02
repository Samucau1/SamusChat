package com.samuschat

import com.samuschat.data.api.ApiService
import com.samuschat.data.model.*
import com.samuschat.data.repository.*
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class ApiContractTest {
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
