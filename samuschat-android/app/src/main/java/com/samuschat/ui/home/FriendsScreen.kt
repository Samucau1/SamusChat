package com.samuschat.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.samuschat.SamusChatApplication
import com.samuschat.data.model.FriendProfile
import com.samuschat.data.repository.data
import com.samuschat.ui.call.CallContact
import com.samuschat.ui.components.InitialAvatar
import com.samuschat.ui.theme.PanelBackground
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Composable
fun FriendsScreen(app: SamusChatApplication, visible: Boolean, unread: Map<String, Long>, onContact: (CallContact) -> Unit, onChanged: () -> Unit) {
    var tab by rememberSaveable { mutableStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var people by remember { mutableStateOf(emptyList<FriendProfile>()) }
    var results by remember { mutableStateOf(emptyList<FriendProfile>()) }
    var loading by remember { mutableStateOf(true) }
    var searching by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var revision by remember { mutableStateOf(0) }
    val mutex = remember { Mutex() }
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(visible, revision, lifecycle) {
        if (!visible) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            do {
                try {
                    mutex.withLock { people = app.api.friends(app.tokens.authorization()).data() }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { error = failure.message ?: "Não foi possível carregar seus amigos." }
                finally { loading = false }
                delay(15000)
            } while (isActive)
        }
    }
    LaunchedEffect(query, tab, revision, visible, lifecycle) {
        results = emptyList()
        searching = false
        val term = query.trim()
        if (!visible || tab != 2 || term.length < 2) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            searching = true
            try {
                delay(300)
                results = mutex.withLock { app.api.searchFriends(app.tokens.authorization(), term).data() }
                error = null
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { error = failure.message ?: "Não foi possível buscar pessoas." }
            finally { searching = false }
            awaitCancellation()
        }
    }
    fun act(person: FriendProfile, action: String) {
        if (busy) return
        busy = true
        error = null
        notice = null
        scope.launch {
            try {
                mutex.withLock {
                    val token = app.tokens.authorization()
                    val updated = when (action) {
                        "request" -> app.api.requestFriend(token, person.publicId).data()
                        "accept" -> app.api.acceptFriend(token, person.publicId).data()
                        else -> app.api.removeFriend(token, person.publicId).data()
                    }
                    results = results.map { if (it.publicId == updated.publicId) updated else it }
                    people = people.filter { it.publicId != updated.publicId } + listOf(updated).filter { it.relationship in listOf("FRIEND", "INCOMING", "OUTGOING") }
                }
                notice = when (action) {
                    "request" -> "Solicitação enviada. Aguarde a pessoa aceitar."
                    "accept" -> "Amizade aceita. Vocês já podem conversar."
                    else -> "Conexão removida. O histórico da conversa foi preservado."
                }
                revision++
                onChanged()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { error = failure.message ?: "Não foi possível atualizar a amizade." }
            finally { busy = false }
        }
    }
    val friends = people.filter { it.relationship == "FRIEND" }
    val pending = people.filter { it.relationship in listOf("INCOMING", "OUTGOING") }
    val shown = when (tab) { 2 -> results; 1 -> pending; else -> friends }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Amigos", Modifier.weight(1f), style = MaterialTheme.typography.headlineLarge)
                IconButton(onClick = { tab = 2 }) { Icon(Icons.Default.Search, "Encontrar amigos") }
                IconButton(onClick = { error = null; revision++ }) { Icon(Icons.Default.Refresh, "Atualizar amigos") }
            }
            Text("Encontre pessoas e envie um convite para começar uma amizade.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            ScrollableTabRow(selectedTabIndex = tab, edgePadding = 0.dp, containerColor = PanelBackground) {
                listOf("Amigos (${friends.size})", "Solicitações (${pending.size})", "Buscar").forEachIndexed { index, label ->
                    Tab(selected = tab == index, onClick = { tab = index; error = null; notice = null }, text = { Text(label) })
                }
            }
        }
        if (tab == 2) item {
            OutlinedTextField(value = query, onValueChange = { query = it.take(100) }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("Nome de usuário ou ID completo") }, leadingIcon = { Icon(Icons.Default.Search, null) },
                supportingText = { Text("Digite pelo menos 2 caracteres ou cole o ID completo.") })
        }
        error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error); TextButton(onClick = { error = null; revision++ }) { Text("Tentar novamente") } } }
        notice?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.primary) } }
        if ((tab == 2 && searching) || (tab != 2 && loading)) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        else if (shown.isEmpty() && error == null) item {
            Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Search, null, Modifier.size(36.dp))
                Text(when (tab) { 2 -> if (query.trim().length < 2) "Quem você quer encontrar?" else "Nenhuma pessoa encontrada"; 1 -> "Nenhuma solicitação pendente"; else -> "Sua lista de amigos começa aqui" }, Modifier.padding(top = 12.dp))
                if (tab == 0) TextButton(onClick = { tab = 2 }) { Text("Encontrar amigos") }
            }
        }
        items(shown, key = { it.publicId }) { person ->
            Surface(color = PanelBackground, shape = MaterialTheme.shapes.large) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        InitialAvatar(person.username, url = person.avatarUrl, size = 48.dp)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(person.username, style = MaterialTheme.typography.titleMedium)
                            Text(when (person.relationship) { "FRIEND" -> "Seu amigo"; "INCOMING" -> "Convite recebido"; "OUTGOING" -> "Convite enviado"; else -> "Encontrado na busca" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            unread[person.publicId]?.takeIf { it > 0 }?.let { Text("$it mensagens não lidas", color = MaterialTheme.colorScheme.primary) }
                        }
                    }
                    Text(person.publicId, style = MaterialTheme.typography.labelSmall)
                    when (person.relationship) {
                        "FRIEND" -> Button(onClick = { onContact(CallContact(person.publicId, person.username, person.avatarUrl)) }, modifier = Modifier.fillMaxWidth()) { Text("Conversar") }
                        "INCOMING" -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { act(person, "accept") }, enabled = !busy) { Text("Aceitar") }
                            TextButton(onClick = { act(person, "remove") }, enabled = !busy) { Text("Recusar") }
                        }
                        "OUTGOING" -> OutlinedButton(onClick = { act(person, "remove") }, enabled = !busy) { Text("Cancelar solicitação") }
                        "NONE" -> Button(onClick = { act(person, "request") }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Adicionar amigo") }
                    }
                }
            }
        }
    }
}
