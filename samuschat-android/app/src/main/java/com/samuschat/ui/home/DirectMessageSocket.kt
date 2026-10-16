package com.samuschat.ui.home

import com.google.gson.Gson
import com.samuschat.data.model.DirectMessage
import com.samuschat.util.Constants
import io.reactivex.disposables.CompositeDisposable
import ua.naiksoftware.stomp.Stomp
import ua.naiksoftware.stomp.dto.LifecycleEvent
import ua.naiksoftware.stomp.dto.StompHeader

class DirectMessageSocket {
    private val subscriptions = CompositeDisposable()
    private var client: ua.naiksoftware.stomp.StompClient? = null

    fun connect(token: String, onMessage: (DirectMessage) -> Unit, onOpen: () -> Unit, onClose: () -> Unit) {
        disconnect()
        val stomp = Stomp.over(Stomp.ConnectionProvider.OKHTTP, Constants.WS_URL)
        client = stomp
        subscriptions.add(stomp.lifecycle().subscribe({ event ->
            when (event.type) {
                LifecycleEvent.Type.OPENED -> onOpen()
                LifecycleEvent.Type.ERROR, LifecycleEvent.Type.CLOSED -> onClose()
                else -> Unit
            }
        }, { onClose() }))
        subscriptions.add(stomp.topic("/user/queue/direct-messages").subscribe({ frame ->
            try {
                val message = Gson().fromJson(frame.payload, DirectMessage::class.java)
                if (message.id > 0) onMessage(message)
            } catch (_: Exception) { onClose() }
        }, { onClose() }))
        stomp.connect(listOf(StompHeader("Authorization", "Bearer $token")))
    }

    fun disconnect() { subscriptions.clear(); client?.disconnect(); client = null }
}
