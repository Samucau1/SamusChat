package com.samuschat.ui.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.samuschat.ui.components.MessageBubble

@Composable
fun ChatScreen(viewModel: ChatViewModel, channelName: String, email: String, onBack: () -> Unit) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val connectionError by viewModel.connectionError.collectAsStateWithLifecycle()
    val hasMore by viewModel.hasMore.collectAsStateWithLifecycle()
    var draft by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) viewModel.connect()
            if (event == Lifecycle.Event.ON_STOP) viewModel.disconnect()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); viewModel.disconnect() }
    }
    LaunchedEffect(messages.lastOrNull()?.id) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex + 1)
    }
    Column(Modifier.fillMaxSize().imePadding().padding(12.dp)) {
        Row { TextButton(onClick = onBack) { Text("Voltar") }; Text("# $channelName") }
        connectionError?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = { viewModel.connect() }) { Text("Reconectar") }
        }
        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = { viewModel.refresh() }, enabled = !busy) { Text("Atualizar histórico") }
        }
        LazyColumn(Modifier.weight(1f), state = listState) {
            item { if (hasMore) TextButton(onClick = { viewModel.loadMore() }, enabled = !busy) { Text("Mensagens anteriores") } }
            items(messages, key = { it.id }) { MessageBubble(it, it.senderEmail == email) }
        }
        Row {
            OutlinedTextField(draft, { draft = it }, modifier = Modifier.weight(1f), placeholder = { Text("Mensagem") }, maxLines = 4, enabled = !busy)
            TextButton(onClick = { viewModel.send(draft) { draft = "" } }, enabled = !busy && draft.isNotBlank()) { Text("Enviar") }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    }
}
