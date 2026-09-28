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
}
