package com.samuschat.ui.chat

import com.google.gson.Gson
import com.samuschat.data.model.Message
import com.samuschat.util.Constants
import io.reactivex.disposables.CompositeDisposable
import ua.naiksoftware.stomp.Stomp
import ua.naiksoftware.stomp.dto.LifecycleEvent
import ua.naiksoftware.stomp.dto.StompHeader

class WebSocketManager {
    private val subscriptions = CompositeDisposable()
    private var client: ua.naiksoftware.stomp.StompClient? = null
    fun connect(token: String, channelId: Long, onMessage: (Message) -> Unit, onOpen: () -> Unit, onError: (String) -> Unit) {
        disconnect()
        val stomp = Stomp.over(Stomp.ConnectionProvider.OKHTTP, Constants.WS_URL)
        client = stomp
        subscriptions.add(stomp.lifecycle().subscribe({ event ->
            when (event.type) {
                LifecycleEvent.Type.OPENED -> onOpen()
                LifecycleEvent.Type.ERROR, LifecycleEvent.Type.CLOSED -> onError("Conexão em tempo real interrompida. Toque em Reconectar.")
                else -> Unit
            }
        }, { onError("Não foi possível conectar ao chat") }))
        subscriptions.add(stomp.topic(Constants.WS_SUBSCRIBE_PREFIX + channelId).subscribe({ frame ->
            try {
                val message = Gson().fromJson(frame.payload, Message::class.java)
                if (message.channelId == channelId && message.id > 0 && (message.type == null || message.type == "CHAT")) onMessage(message)
            } catch (_: Exception) { onError("Mensagem recebida em formato inválido") }
        }, { onError("Falha ao receber mensagens. Toque em Reconectar.") }))
        stomp.connect(listOf(StompHeader("Authorization", "Bearer $token")))
    }
    fun disconnect() { subscriptions.clear(); client?.disconnect(); client = null }
}
