package com.samuschat

import com.samuschat.data.api.SessionAuthenticator
import com.samuschat.util.Session
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Test
import org.junit.Assert.*
import java.io.IOException

class SessionAuthenticatorTest {
    private fun exercise(refreshCode: Int, refreshBody: String, acceptSave: Boolean = true): Pair<Int, Session> {
        val server = MockWebServer()
        server.start()
        var saved = Session("expired", "mobile@example.test", "refresh-secret")
        val authenticator = SessionAuthenticator(server.url("/").toString(), { saved }, { old, token ->
            if (acceptSave && saved == old) { saved = old.copy(token = token); true } else false
        })
        val client = OkHttpClient.Builder().authenticator(authenticator).build()
        try {
            server.enqueue(MockResponse().setResponseCode(401))
            server.enqueue(MockResponse().setResponseCode(refreshCode).setBody(refreshBody))
            server.enqueue(MockResponse().setResponseCode(200).setBody("profile"))
            val response = client.newCall(Request.Builder().url(server.url("/api/users/me")).header("Authorization", "Bearer expired").build()).execute()
            val status = response.use { it.code }
            val original = server.takeRequest()
            val refresh = server.takeRequest()
            assertEquals("/api/users/me", original.path)
            assertEquals("/api/auth/refresh", refresh.path)
            assertNull(refresh.getHeader("Authorization"))
            if (status == 200) assertEquals("Bearer renewed", server.takeRequest().getHeader("Authorization"))
            return status to saved
        } finally { server.shutdown(); client.connectionPool.evictAll(); client.dispatcher.executorService.shutdown() }
    }
    @Test fun expiredAccessRenewsAndRetries() {
        val (code, session) = exercise(200, "{\"success\":true,\"data\":{\"token\":\"renewed\"}}")
        assertEquals(200, code)
        assertEquals("renewed", session.token)
        assertEquals("refresh-secret", session.refreshToken)
    }
    @Test fun revokedSessionDoesNotRetry() { assertEquals(401, exercise(401, "{}").first) }
    @Test fun logoutDuringRenewalDoesNotRestoreSession() {
        assertEquals(401, exercise(200, "{\"success\":true,\"data\":{\"token\":\"renewed\"}}", false).first)
    }
    @Test fun serverUnavailablePreservesLogin() {
        try { exercise(503, "{}"); fail("Expected recoverable network error") } catch (expected: IOException) { }
    }
}
