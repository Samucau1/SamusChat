package com.samuschat

import com.samuschat.data.model.Server
import com.samuschat.ui.server.findServers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerSearchTest {
    private val servers = listOf(
        Server(7, "Programação", null, "demo@example.test", emptyList(), ""),
        Server(70, "Games", null, "demo@example.test", emptyList(), "")
    )

    @Test fun blankQueryKeepsAllServersInOrder() {
        assertEquals(servers, findServers(servers, "  "))
    }

    @Test fun findsNamesIgnoringCaseAccentsAndOuterSpaces() {
        assertEquals(listOf(servers[0]), findServers(servers, "  PROGRAMACAO "))
        assertEquals(listOf(servers[0]), findServers(servers, "grama"))
    }

    @Test fun numericQueryMatchesExactId() {
        assertEquals(listOf(servers[0]), findServers(servers, "7"))
    }

    @Test fun unknownQueryAndEmptyMembershipsReturnNoResults() {
        assertTrue(findServers(servers, "inexistente").isEmpty())
        assertTrue(findServers(emptyList(), "Games").isEmpty())
    }
}
