package com.samuschat.ui.chat

import androidx.lifecycle.viewModelScope
import com.samuschat.SamusChatApplication
import com.samuschat.data.model.*
import com.samuschat.ui.AppViewModel
import com.samuschat.util.Constants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import android.net.Uri
import com.samuschat.data.repository.SelectedAttachment
import com.samuschat.data.repository.inspectAttachment
import com.samuschat.data.repository.withAttachmentPart
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

class ChatViewModel(app: SamusChatApplication, private val channelId: Long) : AppViewModel(app) {
    val messages = MutableStateFlow<List<Message>>(emptyList())
    val hasMore = MutableStateFlow(true)
    val connectionError = MutableStateFlow<String?>(null)
    val attachment = MutableStateFlow<SelectedAttachment?>(null)
    val uploading = MutableStateFlow(false)
    val selectingAttachment = MutableStateFlow(false)
    fun selectAttachment(uri: Uri) = viewModelScope.launch {
        if (uploading.value || selectingAttachment.value) return@launch
        selectingAttachment.value = true
        error.value = null
        try { attachment.value = inspectAttachment(app, uri) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { error.value = e.message ?: "Não foi possível selecionar o anexo." }
        finally { selectingAttachment.value = false }
    }
    fun removeAttachment() { if (!busy.value) attachment.value = null }
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
        check(!selectingAttachment.value) { "Aguarde a seleção do anexo." }
        val selected = attachment.value
        require(content.length <= 2000 && (content.isNotBlank() || selected != null)) { "Escreva uma mensagem ou escolha um anexo; limite de 2000 caracteres." }
        uploading.value = selected != null
        try {
            val sent = if (selected == null) app.messages.send(channelId, content)
            else withAttachmentPart(app, selected) { file ->
                app.messages.upload(channelId, file, content.trim().takeIf { it.isNotEmpty() }?.toRequestBody("text/plain".toMediaType()))
            }
            messages.update { mergeMessages(it, listOf(sent)) }
            attachment.value = null
            onSuccess()
        } finally { uploading.value = false }
    }
    fun disconnect() = socket.disconnect()
    override fun onCleared() { disconnect() }
}
