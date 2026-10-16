package com.samuschat.ui.home

import com.samuschat.data.model.DirectMessage

fun belongsToDirectConversation(message: DirectMessage, me: String, contact: String): Boolean =
    message.id > 0 && ((message.senderEmail == me && message.recipientEmail == contact) ||
        (message.senderEmail == contact && message.recipientEmail == me))

fun mergeDirectMessages(current: List<DirectMessage>, incoming: List<DirectMessage>): List<DirectMessage> =
    (current + incoming).associateBy { it.id }.values.sortedByDescending { it.id }
