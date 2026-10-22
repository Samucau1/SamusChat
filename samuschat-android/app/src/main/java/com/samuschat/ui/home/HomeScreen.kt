package com.samuschat.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.samuschat.SamusChatApplication
import com.samuschat.ui.components.InitialAvatar
import com.samuschat.ui.components.dismissKeyboardOnOutsideTap
import com.samuschat.ui.server.ServerListScreen
import com.samuschat.ui.server.ServerViewModel
import com.samuschat.ui.theme.*
import kotlinx.coroutines.launch
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.samuschat.ui.call.CallPanel
import com.samuschat.ui.call.CallViewModel
import com.samuschat.ui.call.CallContact
import com.samuschat.ui.call.FriendCallButton
import com.samuschat.ui.call.CallRoomScreen
import com.samuschat.data.repository.data

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    app: SamusChatApplication,
    email: String,
    servers: ServerViewModel,
    onLogout: () -> Unit,
    channelContent: @Composable (Long, String, String, () -> Unit) -> Unit
) {
    var publicId by rememberSaveable(email) { mutableStateOf<String?>(null) }
    var identityError by remember { mutableStateOf(false) }
    var retry by remember { mutableStateOf(0) }
    LaunchedEffect(email, retry) {
        try {
            publicId = app.api.profile(app.tokens.authorization()).data().publicId
            identityError = publicId == null
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { identityError = true }
    }
    val identity = publicId
    if (identity == null) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            if (identityError) {
                Text("Não foi possível carregar sua conta. Confira a conexão com a API.")
                TextButton(onClick = { identityError = false; retry++ }) { Text("Tentar novamente") }
                TextButton(onClick = onLogout) { Text("Sair da conta") }
            } else CircularProgressIndicator()
        }
        return
    }
    val calls: CallViewModel = viewModel(key = "calls-$identity", factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = CallViewModel(app, identity) as T
    })
    val pager = rememberPagerState(initialPage = 1, pageCount = { 3 })
    val tab = listOf("servers", "friends", "profile")[pager.currentPage]
    var channelId by rememberSaveable { mutableStateOf<Long?>(null) }
    var channelName by rememberSaveable { mutableStateOf("") }
    var channelType by rememberSaveable { mutableStateOf("TEXT") }
    var contactEmail by rememberSaveable { mutableStateOf<String?>(null) }
    var contactName by rememberSaveable { mutableStateOf("") }
    val inbox = rememberDirectInbox(app, identity, if (pager.currentPage == 1) contactEmail else null)
    val callState by calls.state.collectAsStateWithLifecycle()
    var demoChat by rememberSaveable { mutableStateOf(false) }
    var demoMessages by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    val scope = rememberCoroutineScope()
    // Buttons switch directly; HorizontalPager still handles the swipe gesture.
    fun navigate(page: Int) { scope.launch { pager.scrollToPage(page) } }
    BackHandler(pager.currentPage != 1 || demoChat || contactEmail != null) {
        demoChat = false; contactEmail = null
        navigate(1)
    }
    Scaffold(
        modifier = Modifier.imePadding().dismissKeyboardOnOutsideTap(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (!WindowInsets.isImeVisible) {
            NavigationBar(containerColor = RailBackground, windowInsets = WindowInsets(0, 0, 0, 0)) {
                NavigationBarItem(selected = tab == "servers",
                    onClick = { channelId = null; navigate(0) },
                    icon = { Icon(Icons.Default.Menu, null) }, label = { Text("Servidores") })
                NavigationBarItem(selected = tab == "friends",
                    onClick = { navigate(1); demoChat = false; contactEmail = null; calls.refreshContacts() },
                    icon = { Icon(Icons.Default.Face, null) }, label = { Text("Amigos") })
                NavigationBarItem(selected = tab == "profile",
                    onClick = { navigate(2) },
                    icon = { Icon(Icons.Default.Person, null) }, label = { Text("Perfil") })
            }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).background(ChatBackground)) {
            inbox.notice?.let { message ->
                Surface(color = MaterialTheme.colorScheme.primaryContainer) {
                    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Nova mensagem de " + message.senderUsername, Modifier.weight(1f))
                        TextButton(onClick = { contactEmail = message.senderEmail; contactName = message.senderUsername; inbox.notice = null; navigate(1) }) { Text("Abrir") }
                        TextButton(onClick = { inbox.notice = null }) { Text("Fechar") }
                    }
                }
            }
            CallPanel(calls)
            callState.error?.let { Text(it, Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.error) }
            callState.room?.let { room ->
                if (tab != "servers" || channelId != room.channelId) TextButton(onClick = { channelId = room.channelId; channelName = callState.roomName; channelType = "VOICE"; navigate(0) }) { Text("Voltar à chamada: ${callState.roomName}") }
            }
            HorizontalPager(state = pager, modifier = Modifier.weight(1f).fillMaxWidth(), key = { it }) { page ->
            when {
                page == 2 -> ProfileScreen(app, email) { calls.end(); onLogout() }
                page == 0 && channelId != null && channelType == "VOICE" -> CallRoomScreen(calls, channelId!!, channelName) { channelId = null; navigate(0) }
                page == 0 && channelId != null -> channelContent(channelId!!, channelName, identity) {
                    channelId = null; navigate(0)
                }
                page == 0 -> ServerListScreen(servers, identity) { id, name, type ->
                    channelId = id; channelName = name; channelType = type
                }
                demoChat -> DemoConversation(demoMessages, { demoMessages = ArrayList(demoMessages + it) }) { demoChat = false }
                contactEmail != null -> key(contactEmail) { DirectConversation(app, identity, CallContact(contactEmail!!, contactName), calls, inbox, pager.currentPage == 1) { contactEmail = null } }
                else -> FriendsScreen(email, callState.contacts, inbox.unread, { contactEmail = it.email; contactName = it.username }, { calls.refreshContacts() }) { demoChat = true }
            }
            }
        }
    }
}

@Composable
private fun FriendsScreen(email: String, contacts: List<CallContact>, unread: Map<String, Long>, onContact: (CallContact) -> Unit, onRefresh: () -> Unit, onChat: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item {
            Text("Amigos", style = MaterialTheme.typography.headlineLarge)
            Text("Seu próximo papo começa aqui.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onRefresh) { Text("Atualizar contatos") }
        }
        item {
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true,
                placeholder = { Text("Buscar amigos") }, leadingIcon = { Icon(Icons.Default.Search, null) })
        }
        item {
            Text("PRINCIPAIS CONVERSAS", style = MaterialTheme.typography.labelMedium)
            Text("Um amigo de teste para conhecer a nova tela.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (matchesFriend("Alex", query)) item {
            Surface(onClick = onChat, color = PanelBackground, shape = MaterialTheme.shapes.large) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    InitialAvatar("Alex", size = 52.dp)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text("Alex", style = MaterialTheme.typography.titleMedium)
                        Text("Disponível • amigo de teste", color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodySmall)
                        Text("Toque para experimentar a conversa", style = MaterialTheme.typography.bodySmall)
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Abrir conversa com Alex")
                }
            }
        }
        item {
            Text("CONTATOS", style = MaterialTheme.typography.labelMedium)
            Text("Toque em um amigo para abrir a conversa. Para receber uma ligação, mantenha o app aberto.", style = MaterialTheme.typography.bodySmall)
        }
        items(contacts.filter { matchesFriend(it.username, query) || matchesFriend(it.email, query) }, key = { it.email }) { contact ->
            Surface(onClick = { onContact(contact) }, color = PanelBackground, shape = MaterialTheme.shapes.large) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    InitialAvatar(contact.username, url = contact.avatarUrl)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(contact.username)
                        unread[contact.email]?.takeIf { it > 0 }?.let { Text("$it mensagens nao lidas", color = MaterialTheme.colorScheme.primary) }
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Abrir conversa com ${contact.username}")
                }
            }
        }
    }
}

@Composable
private fun DemoConversation(messages: List<String>, onSend: (String) -> Unit, onBack: () -> Unit) {
    var draft by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize().imePadding()) {
        Row(Modifier.fillMaxWidth().background(PanelBackground).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar aos amigos") }
            InitialAvatar("Alex")
            Column(Modifier.padding(start = 12.dp)) {
                Text("Alex", style = MaterialTheme.typography.titleLarge)
                Text("Conversa de demonstração", style = MaterialTheme.typography.labelSmall)
            }
        }
        LazyColumn(Modifier.weight(1f), reverseLayout = true, contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(messages.asReversed()) { message ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Surface(color = Blurple, shape = MaterialTheme.shapes.medium) {
                        Text(message, Modifier.widthIn(max = 280.dp).padding(12.dp))
                    }
                }
            }
            item {
                Surface(color = PanelBackground, shape = MaterialTheme.shapes.medium) {
                    Text("Oi! Sou o Alex, seu amigo de teste. Experimente escrever uma mensagem!", Modifier.padding(12.dp))
                }
            }
            item { Text("As mensagens desta demonstração não são enviadas a uma pessoa real.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Row(Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(draft, { draft = it }, Modifier.weight(1f), maxLines = 4,
                placeholder = { Text("Conversar com Alex") }, isError = draft.length > 2000)
            IconButton(onClick = { if (canSendDemoMessage(draft)) { onSend(draft.trim()); draft = "" } }, enabled = canSendDemoMessage(draft)) {
                Icon(Icons.AutoMirrored.Filled.Send, "Enviar mensagem de teste")
            }
        }
    }
}
