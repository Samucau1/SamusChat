package com.samuschat.ui.home
import com.samuschat.data.model.DirectMessage

/** Bounded notification deduplication; message history remains authoritative. */
class DirectEventWindow(private val identity: String, private val capacity: Int = 2048) {
    private val seen = LinkedHashSet<Long>()
    init { require(capacity > 0) }
    fun accept(message: DirectMessage): Boolean {
        if (message.id <= 0 || message.recipientEmail != identity || message.senderEmail == identity || !seen.add(message.id)) return false
        if (seen.size > capacity) seen.remove(seen.first())
        return true
    }
}
