package com.samuschat
import com.samuschat.data.model.DirectMessage
import com.samuschat.ui.home.DirectEventWindow
import org.junit.Assert.*
import org.junit.Test
class DirectInboxTest {
    private fun message(id: Long, sender: String="friend", recipient: String="me") = DirectMessage(id,"body",sender,sender,recipient,"2026-10-20T12:00:00")
    @Test fun excludesOwnUnrelatedAndRepeatedEvents() {
        val window=DirectEventWindow("me")
        assertTrue(window.accept(message(1)))
        assertFalse(window.accept(message(1)))
        assertFalse(window.accept(message(2,"me","friend")))
        assertFalse(window.accept(message(3,"friend","other")))
        assertFalse(window.accept(message(0)))
        assertTrue(window.accept(message(4,"other")))
    }
    @Test fun retainsOnlyBoundedRecentNotificationIds() {
        val window=DirectEventWindow("me",2)
        for(id in 1L..3L) assertTrue(window.accept(message(id)))
        assertFalse(window.accept(message(2)))
        assertFalse(window.accept(message(3)))
        assertTrue(window.accept(message(1)))
    }
}
