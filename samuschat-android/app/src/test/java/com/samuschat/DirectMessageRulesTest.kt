package com.samuschat

import com.samuschat.data.model.DirectMessage
import com.samuschat.ui.home.belongsToDirectConversation
import com.samuschat.ui.home.mergeDirectMessages
import org.junit.Assert.*
import org.junit.Test

class DirectMessageRulesTest {
    private fun message(id: Long, sender: String = "me", recipient: String = "friend") =
        DirectMessage(id, "message $id", sender, sender, recipient, "2026-10-16T15:00:00")

    @Test fun onlyBothDirectionsOfSelectedConversationAreAccepted() {
        assertTrue(belongsToDirectConversation(message(1), "me", "friend"))
        assertTrue(belongsToDirectConversation(message(2, "friend", "me"), "me", "friend"))
        for (item in listOf(message(3, "outsider", "me"), message(4, "me", "outsider"), message(5, "friend", "outsider"), message(0))) {
            assertFalse(belongsToDirectConversation(item, "me", "friend"))
        }
    }

    @Test fun restAndSocketDuplicatesAreMergedWithoutLosingOlderPages() {
        val history = (1L..50L).map { message(it) }
        val result = mergeDirectMessages(history, listOf(message(50), message(51), message(49)))
        assertEquals((51L downTo 1L).toList(), result.map { it.id })
        assertEquals(result, mergeDirectMessages(result, listOf(message(51))))
    }
}
