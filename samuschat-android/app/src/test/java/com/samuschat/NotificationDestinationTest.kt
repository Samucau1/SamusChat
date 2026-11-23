package com.samuschat

import com.samuschat.data.push.NotificationDestination
import org.junit.Assert.*
import org.junit.Test

class NotificationDestinationTest {
    private val recipient = "usr_1_abcdefghijklmnopqrstuv"
    private val contact = "usr_2_abcdefghijklmnopqrstuv"
    private val base = mapOf("type" to "NEW_MESSAGE", "recipientId" to recipient)

    @Test fun directMessageOpensThePublicContact() {
        val target = NotificationDestination.fromData(base + mapOf("channelId" to "direct", "contactId" to contact))
        assertEquals(NotificationDestination(recipient, contactId = contact), target)
        assertEquals("direct:$contact", target?.conversationKey)
    }

    @Test fun channelMessageOpensThePositiveChannel() {
        val target = NotificationDestination.fromData(base + ("channelId" to "42"))
        assertEquals(NotificationDestination(recipient, channelId = 42), target)
    }

    @Test fun unrelatedMalformedAndPrivateIdentifiersAreIgnored() {
        for (channel in listOf("0", "-1", "other", "999999999999999999999999999")) {
            assertNull(NotificationDestination.fromData(base + ("channelId" to channel)))
        }
        assertNull(NotificationDestination.fromData(base + mapOf("channelId" to "direct", "contactId" to "private@example.test")))
        assertNull(NotificationDestination.fromData(base + mapOf("channelId" to "42", "recipientId" to "private@example.test")))
        assertNull(NotificationDestination.fromData(base + mapOf("channelId" to "42", "type" to "OTHER")))
    }

    @Test fun missingRoutingDataCannotProduceANotificationDestination() {
        assertNull(NotificationDestination.fromData(emptyMap()))
        assertNull(NotificationDestination.fromData(mapOf("type" to "NEW_MESSAGE", "channelId" to "42")))
        assertNull(NotificationDestination.fromData(mapOf("recipientId" to recipient, "channelId" to "42")))
        assertNull(NotificationDestination.fromData(base))
        assertNull(NotificationDestination.fromData(base + ("channelId" to "direct")))
    }
}
