package com.samuschat.ui.chat

import androidx.lifecycle.viewModelScope
import com.samuschat.SamusChatApplication
import com.samuschat.data.model.*
import com.samuschat.ui.AppViewModel
import com.samuschat.util.Constants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ChatViewModel(app: SamusChatApplication, private val channelId: Long) : AppViewModel(app) {
    val messages = MutableStateFlow<List<Message>>(emptyList())
    val hasMore = MutableStateFlow(true)
    val connectionError = MutableStateFlow<String?>(null)
    private val socket = WebSocketManager()
    private var page = 0
    fun connect() {
        viewModelScope.launch {
            val token = app.tokens.getToken() ?: return@launch
            socket.connect(token, channelId,
                onMessage = { message -> messages.update { mergeMessages(it, listOf(message)) } },
                onOpen = { connectionError.value = null; refresh() },
                onError = { connectionError.value = it })
        }
        refresh()
    }
    fun refresh() = runTask {
        val history = app.messages.history(channelId, 0)
        messages.update { mergeMessages(it, history) }
        page = 1; hasMore.value = history.size == Constants.PAGE_SIZE
    }
    fun loadMore() = runTask {
        val history = app.messages.history(channelId, page)
        messages.update { mergeMessages(it, history) }
        page++; hasMore.value = history.size == Constants.PAGE_SIZE
    }
    fun send(content: String, onSuccess: () -> Unit) = runTask {
        require(content.isNotBlank() && content.length <= 2000) { "Use entre 1 e 2000 caracteres" }
        val sent = app.messages.send(channelId, content)
        messages.update { mergeMessages(it, listOf(sent)) }
        onSuccess()
    }
    fun disconnect() = socket.disconnect()
    override fun onCleared() { disconnect() }
}
