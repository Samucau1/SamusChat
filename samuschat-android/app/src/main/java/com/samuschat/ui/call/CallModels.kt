package com.samuschat.ui.call

data class CallContact(val email: String, val username: String)
data class IceServerConfig(val urls: List<String>, val username: String, val credential: String)
data class CallInvite(val callee: String, val requestId: String)
data class CallAction(val action: String, val sdp: String? = null)
data class CallRoom(val channelId: Long, val participants: List<CallContact>, val sessions: List<CallSession>, val capacity: Int)
data class CallSession(val id: String, val caller: String, val callee: String, val state: String, val offer: String?, val answer: String?, val channelId: Long? = null) {
    val ended get() = state in setOf("ENDED", "DECLINED", "EXPIRED")
    fun peer(email: String) = if (caller == email) callee else caller
}

object CallPolicy {
    fun mayAccept(call: CallSession?, email: String) = call?.state == "RINGING" && call.callee == email
    fun mayShare(connected: Boolean, sharing: Boolean) = connected && !sharing
    fun validAudioPacket(bytes: Int) = bytes == 640 // 20 ms, 16 kHz, mono PCM16
    fun mayQueueAudio(buffered: Long) = buffered < 640 * 5 // at most 100 ms queued
}
