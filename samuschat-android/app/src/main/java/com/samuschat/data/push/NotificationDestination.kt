package com.samuschat.data.push

data class NotificationDestination(val recipientId: String, val contactId: String? = null, val channelId: Long? = null) {
    val conversationKey get() = contactId?.let { "direct:$it" } ?: "channel:$channelId"
    companion object {
        private val identity = Regex("usr_[1-9][0-9]*_[A-Za-z0-9_-]{22}")

        fun fromData(data: Map<String, String>): NotificationDestination? {
            if (data["type"] != "NEW_MESSAGE") return null
            val recipient = data["recipientId"]?.takeIf(identity::matches) ?: return null
            if (data["channelId"] == "direct") {
                val contact = data["contactId"]?.takeIf(identity::matches) ?: return null
                return NotificationDestination(recipient, contactId = contact)
            }
            val channel = data["channelId"]?.takeIf { it.matches(Regex("[1-9][0-9]*")) }?.toLongOrNull() ?: return null
            return NotificationDestination(recipient, channelId = channel)
        }
    }
}
