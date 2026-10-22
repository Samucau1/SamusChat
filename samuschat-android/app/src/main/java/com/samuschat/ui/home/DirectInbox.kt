package com.samuschat.ui.home

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.samuschat.SamusChatApplication
import com.samuschat.data.model.DirectMessage
import com.samuschat.data.repository.data
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow

class DirectInbox {
    var unread by mutableStateOf<Map<String, Long>>(emptyMap())
    var notice by mutableStateOf<DirectMessage?>(null)
    var live by mutableStateOf(false)
    val events = MutableSharedFlow<DirectMessage>(extraBufferCapacity = 64)
    suspend fun refresh(app: SamusChatApplication) {
        try { unread = app.api.directUnread(app.tokens.authorization()).data().associate { it.senderEmail to it.count } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { /* retry at reconnect/focus */ }
    }
}

@Composable
fun rememberDirectInbox(app: SamusChatApplication, identity: String, activeContact: String?): DirectInbox {
    val inbox = remember(identity) { DirectInbox() }
    val currentContact by rememberUpdatedState(activeContact)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val session by app.tokens.session.collectAsStateWithLifecycle(initialValue = null)
    LaunchedEffect(identity, lifecycle, session?.token) {
        val token = session?.token ?: return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            val socket = DirectMessageSocket()
            val seen = DirectEventWindow(identity)
            var refresh: Job? = null
            try {
                inbox.refresh(app)
                while (isActive) {
                    if (!inbox.live) socket.connect(token,
                        onMessage = { message -> launch {
                            inbox.events.emit(message)
                            if (seen.accept(message)) {
                                if (currentContact != message.senderEmail) inbox.notice = message
                            }
                            refresh?.cancel()
                            refresh = launch { delay(150); inbox.refresh(app) }
                        } },
                        onOpen = { launch { inbox.live = true; inbox.refresh(app) } },
                        onClose = { launch { inbox.live = false } })
                    delay(3000)
                }
            } finally { inbox.live = false; refresh?.cancel(); socket.disconnect() }
        }
    }
    return inbox
}
