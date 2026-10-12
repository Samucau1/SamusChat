package com.samuschat

import com.samuschat.data.api.ApiService
import com.samuschat.data.model.*
import com.samuschat.data.repository.*
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MultipartBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class AppearanceContractTest {
    @Test fun mediaAndAppearanceUseSharedEndpointsAndDecodeNewProfileFields() = runTest {
        val server = MockWebServer()
        try {
            val api = Retrofit.Builder().baseUrl(server.url("/")).addConverterFactory(GsonConverterFactory.create()).build().create(ApiService::class.java)
            val profile = """{"success":true,"data":{"id":1,"username":"Ana","email":"ana@example.com","avatarUrl":"/uploads/appearance/a.gif","bannerUrl":"/uploads/appearance/b.png","accentColor":"#123456","bio":"Olá"}}"""
            server.enqueue(MockResponse().setBody(profile))
            val user = api.updateAppearance("Bearer jwt", ProfileAppearance("Olá", "#123456")).data()
            assertEquals("Olá", user.bio); assertEquals("/uploads/appearance/a.gif", user.avatarUrl)
            val request = server.takeRequest()
            assertEquals("PUT", request.method); assertEquals("/api/users/me/appearance", request.path)
            assertEquals("Bearer jwt", request.getHeader("Authorization")); assertTrue(request.body.readUtf8().contains("#123456"))
            val file = MultipartBody.Part.createFormData("file", "a.gif", "GIF89a".toRequestBody("image/gif".toMediaType()))
            server.enqueue(MockResponse().setBody(profile))
            api.uploadProfileMedia("Bearer jwt", "avatar", file).data()
            val upload = server.takeRequest()
            assertEquals("POST", upload.method); assertEquals("/api/users/me/media/avatar", upload.path)
            assertTrue(upload.body.readUtf8().contains("name=\"file\"; filename=\"a.gif\""))
            server.enqueue(MockResponse().setBody(profile))
            api.deleteProfileMedia("Bearer jwt", "banner").data()
            assertEquals("DELETE", server.takeRequest().method)
            server.enqueue(MockResponse().setBody("""{"success":true,"data":{"id":2,"name":"Servidor","ownerEmail":"ana@example.com","channels":[],"createdAt":"2026-10-12","iconUrl":"/uploads/appearance/icon.png","backgroundUrl":"/uploads/appearance/bg.png"}}"""))
            assertEquals("/uploads/appearance/bg.png", api.uploadServerMedia("Bearer jwt", 2, "background", file).data().backgroundUrl)
            val uploadServer = server.takeRequest()
            assertEquals("/api/servers/2/media/background", uploadServer.path); assertEquals("Bearer jwt", uploadServer.getHeader("Authorization"))
        } finally { server.shutdown() }
    }
    @Test fun appearancePolicyRejectsUnsupportedAndOversizedMedia() {
        for ((type, size) in listOf("image/png" to 1L, "image/jpeg" to AppearancePolicy.MAX_BYTES, "image/gif" to AppearancePolicy.MAX_GIF_BYTES)) AppearancePolicy.validate(type, size)
        for ((type, size) in listOf("image/gif" to AppearancePolicy.MAX_GIF_BYTES + 1, "image/png" to AppearancePolicy.MAX_BYTES + 1, "image/svg+xml" to 100L, "image/png" to 0L)) {
            try { AppearancePolicy.validate(type, size); fail("Expected rejection") } catch (_: IllegalArgumentException) {}
        }
        val source = java.io.ByteArrayInputStream(ByteArray(5))
        try { AttachmentPolicy.copy(source, java.io.ByteArrayOutputStream(), 4); fail("Expected bounded copy rejection") } catch (_: IllegalArgumentException) {}
    }
}
