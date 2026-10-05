package com.samuschat.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.samuschat.SamusChatApplication
import com.samuschat.data.model.*
import com.samuschat.data.repository.data
import com.samuschat.ui.call.*
import kotlinx.coroutines.*

@Composable
fun DirectConversation(app: SamusChatApplication, email: String, contact: CallContact, calls: CallViewModel, onBack: () -> Unit) {
    var messages by remember(contact.email) { mutableStateOf(emptyList<DirectMessage>()) }
    var draft by rememberSaveable(contact.email) { mutableStateOf("") }
    var error by remember(contact.email) { mutableStateOf<String?>(null) }
    var sending by remember { mutableStateOf(false) }
    var loadingOlder by remember { mutableStateOf(false) }
    var oldestPage by remember { mutableStateOf(0) }
    var hasOlder by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    fun merge(incoming: List<DirectMessage>) {
        messages = (messages + incoming).associateBy { it.id }.values.sortedByDescending { it.id }
    }
    LaunchedEffect(contact.email, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                try {
                    val latest = app.api.directMessages(app.tokens.authorization(), contact.email, 0).data()
                    merge(latest)
                    if (oldestPage == 0) hasOlder = latest.size == 50
                    error = null
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { error = failure.message ?: "Não foi possível atualizar a conversa" }
                delay(3000)
            }
        }
    }
    Column(Modifier.fillMaxSize().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(8.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar aos amigos") }
            Text(contact.username, Modifier.weight(1f).padding(12.dp), style = MaterialTheme.typography.titleLarge)
            FriendCallButton(calls, contact)
        }
        error?.let { Text(it, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.error) }
        LazyColumn(Modifier.weight(1f), reverseLayout = true, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(messages, key = { it.id }) { message ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.senderEmail == email) Arrangement.End else Arrangement.Start) {
                    Surface(color = if (message.senderEmail == email) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
                        Column(Modifier.widthIn(max = 280.dp).padding(12.dp)) {
                            Text(message.content)
                            Text(message.createdAt.drop(11).take(5), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            if (messages.isEmpty()) item { Text("Comece a conversa com ${contact.username}.") }
            if (hasOlder) item {
                TextButton(enabled = !loadingOlder, onClick = {
                    loadingOlder = true
                    scope.launch {
                        try {
                            val older = app.api.directMessages(app.tokens.authorization(), contact.email, oldestPage + 1).data()
                            merge(older); oldestPage++; hasOlder = older.size == 50
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (failure: Exception) { error = failure.message }
                        finally { loadingOlder = false }
                    }
                }) { Text("Carregar mensagens anteriores") }
            }
        }
        Row(Modifier.padding(12.dp)) {
            OutlinedTextField(draft, { draft = it }, Modifier.weight(1f), maxLines = 4, enabled = !sending,
                placeholder = { Text("Conversar com ${contact.username}") }, isError = draft.length > 2000)
            IconButton(enabled = !sending && draft.isNotBlank() && draft.length <= 2000, onClick = {
                sending = true
                scope.launch {
                    try {
                        merge(listOf(app.api.sendDirectMessage(app.tokens.authorization(), contact.email, MessageRequest(draft.trim())).data()))
                        draft = ""; error = null
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (failure: Exception) { error = failure.message ?: "Não foi possível enviar a mensagem" }
                    finally { sending = false }
                }
            }) { Icon(Icons.AutoMirrored.Filled.Send, "Enviar mensagem") }
        }
    }
}
