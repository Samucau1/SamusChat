package com.samuschat

import com.samuschat.data.api.ApiService
import com.samuschat.data.repository.data
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class FriendsApiTest {
    @Test fun searchEncodesNamesAndIdsAndReadsRelationship() = runTest {
        val server = MockWebServer()
        try {
            val api = Retrofit.Builder().baseUrl(server.url("/")).addConverterFactory(GsonConverterFactory.create()).build().create(ApiService::class.java)
            for (query in listOf("João & Ana", "usr_14_abcdefghijklmnopqrstuv")) {
                server.enqueue(MockResponse().setBody("""{"success":true,"data":[{"publicId":"usr_14_abcdefghijklmnopqrstuv","username":"João","relationship":"NONE","avatarUrl":"/uploads/appearance/a.png"}]}"""))
                val profile = api.searchFriends("Bearer test", query).data().single()
                assertEquals("NONE", profile.relationship)
                assertEquals("/uploads/appearance/a.png", profile.avatarUrl)
                val request = server.takeRequest()
                assertEquals("GET", request.method)
                assertEquals("/api/friends/search", request.requestUrl!!.encodedPath)
                assertEquals(query, request.requestUrl!!.queryParameter("q"))
                assertEquals("Bearer test", request.getHeader("Authorization"))
            }
        } finally { server.shutdown() }
    }

    @Test fun invitationsUseFriendEndpointsAndPersistServerStatuses() = runTest {
        val server = MockWebServer()
        try {
            val api = Retrofit.Builder().baseUrl(server.url("/")).addConverterFactory(GsonConverterFactory.create()).build().create(ApiService::class.java)
            val id = "usr_14_abcdefghijklmnopqrstuv"
            for ((action, status) in listOf("request" to "OUTGOING", "accept" to "FRIEND", "remove" to "NONE")) {
                server.enqueue(MockResponse().setBody("""{"success":true,"data":{"publicId":"$id","username":"Amigo","relationship":"$status"}}"""))
                val result = when (action) {
                    "request" -> api.requestFriend("Bearer test", id)
                    "accept" -> api.acceptFriend("Bearer test", id)
                    else -> api.removeFriend("Bearer test", id)
                }.data()
                assertEquals(status, result.relationship)
                val request = server.takeRequest()
                assertEquals(if (action == "remove") "DELETE" else "POST", request.method)
                assertEquals("/api/friends/$id" + if (action == "remove") "" else "/$action", request.path)
                assertEquals("Bearer test", request.getHeader("Authorization"))
            }
            server.enqueue(MockResponse().setBody("""{"success":true,"data":[{"publicId":"$id","username":"Amigo","relationship":"INCOMING"}]}"""))
            assertEquals("INCOMING", api.friends("Bearer test").data().single().relationship)
            assertEquals("/api/friends", server.takeRequest().path)
        } finally { server.shutdown() }
    }
}
