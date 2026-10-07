package com.samuschat.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.samuschat.data.repository.AttachmentPolicy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.samuschat.ui.components.ErrorNotice
import com.samuschat.ui.components.MessageBubble
import com.samuschat.ui.theme.*

@Composable
fun ChatScreen(viewModel: ChatViewModel, channelName: String, email: String, onBack: () -> Unit) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val taskBusy by viewModel.busy.collectAsStateWithLifecycle()
    val selectingAttachment by viewModel.selectingAttachment.collectAsStateWithLifecycle()
    val busy = taskBusy || selectingAttachment
    val error by viewModel.error.collectAsStateWithLifecycle()
    val connectionError by viewModel.connectionError.collectAsStateWithLifecycle()
    val hasMore by viewModel.hasMore.collectAsStateWithLifecycle()
    val attachment by viewModel.attachment.collectAsStateWithLifecycle()
    val uploading by viewModel.uploading.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.selectAttachment(it) }
    }
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
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex + 2)
    }
    Column(Modifier.fillMaxSize().background(ChatBackground).imePadding()) {
        Row(Modifier.fillMaxWidth().background(PanelBackground).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Voltar aos servidores") }
            Text("#", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.padding(start = 12.dp)) {
                Text(channelName, style = MaterialTheme.typography.titleLarge)
                Text("Canal de texto", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        connectionError?.let {
            ErrorNotice(it, Modifier.padding(12.dp))
            TextButton(onClick = { viewModel.connect() }) { Text("Reconectar") }
        }
        error?.let {
            ErrorNotice(it, Modifier.padding(12.dp))
            TextButton(onClick = { viewModel.refresh() }, enabled = !busy) { Text("Atualizar histórico") }
        }
        LazyColumn(Modifier.weight(1f), state = listState, contentPadding = PaddingValues(vertical = 16.dp)) {
            item(key = "welcome") {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 20.dp)) {
                    Text("#", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Bem-vindo a #$channelName", style = MaterialTheme.typography.headlineMedium)
                    Text("Este é o espaço da sua conversa.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item(key = "history") {
                if (hasMore) TextButton(onClick = { viewModel.loadMore() }, enabled = !busy) { Text("Carregar mensagens anteriores") }
            }
            items(messages, key = { it.id }) { MessageBubble(it, it.senderEmail == email) }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        attachment?.let { selected ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(selected.name, maxLines = 2, style = MaterialTheme.typography.bodyMedium)
                    Text(if (uploading) "Enviando anexo…" else "Anexo selecionado • até 10 MB", style = MaterialTheme.typography.labelSmall)
                }
                TextButton(onClick = viewModel::removeAttachment, enabled = !busy) { Text("Remover") }
            }
        }
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Bottom) {
            IconButton(onClick = { picker.launch(AttachmentPolicy.types) }, enabled = !busy) {
                Icon(Icons.Default.Add, "Adicionar anexo")
            }
            OutlinedTextField(draft, { draft = it }, modifier = Modifier.weight(1f),
                placeholder = { Text("Conversar em #$channelName") }, maxLines = 4, enabled = !busy,
                shape = MaterialTheme.shapes.large, isError = draft.length > 2000)
            Spacer(Modifier.width(8.dp))
            FilledIconButton(onClick = { viewModel.send(draft) { draft = "" } },
                modifier = Modifier.padding(bottom = 4.dp), enabled = !busy && (draft.isNotBlank() || attachment != null) && draft.length <= 2000) {
                Icon(Icons.Default.Send, "Enviar mensagem")
            }
        }
        if (draft.length > 2000) Text("Limite de 2000 caracteres", Modifier.padding(start = 16.dp, bottom = 8.dp), color = MaterialTheme.colorScheme.error)
    }
}
