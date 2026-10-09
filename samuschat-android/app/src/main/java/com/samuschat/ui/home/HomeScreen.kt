package com.samuschat.ui.home

import androidx.activity.compose.BackHandler
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    app: SamusChatApplication,
    email: String,
    servers: ServerViewModel,
    onLogout: () -> Unit,
    channelContent: @Composable (Long, String, () -> Unit) -> Unit
) {
    val calls: CallViewModel = viewModel(key = "calls-$email", factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = CallViewModel(app, email) as T
    })
    var tab by rememberSaveable { mutableStateOf("friends") }
    var channelId by rememberSaveable { mutableStateOf<Long?>(null) }
    var channelName by rememberSaveable { mutableStateOf("") }
    var channelType by rememberSaveable { mutableStateOf("TEXT") }
    var contactEmail by rememberSaveable { mutableStateOf<String?>(null) }
    var contactName by rememberSaveable { mutableStateOf("") }
    val callState by calls.state.collectAsStateWithLifecycle()
    var demoChat by rememberSaveable { mutableStateOf(false) }
    var demoMessages by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    BackHandler(drawer.isOpen || tab != "friends" || demoChat || contactEmail != null) {
        if (drawer.isOpen) scope.launch { drawer.close() }
        else { tab = "friends"; demoChat = false; contactEmail = null }
    }
    ModalNavigationDrawer(
        drawerState = drawer,
        gesturesEnabled = drawer.isOpen,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = PanelBackground) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    IconButton(onClick = { scope.launch { drawer.close() } }) {
                        Icon(Icons.Default.Close, "Fechar servidores")
                    }
                }
                ServerListScreen(servers, email) { id, name, type ->
                    channelId = id; channelName = name; channelType = type; tab = "servers"
                    scope.launch { drawer.close() }
                }
            }
        }
    ) {
        Scaffold(
            modifier = Modifier.imePadding().dismissKeyboardOnOutsideTap(),
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                if (!WindowInsets.isImeVisible) {
                NavigationBar(containerColor = RailBackground, windowInsets = WindowInsets(0, 0, 0, 0)) {
                    NavigationBarItem(selected = drawer.isOpen || tab == "servers",
                        onClick = { scope.launch { drawer.open() } },
                        icon = { Icon(Icons.Default.Menu, null) }, label = { Text("Servidores") })
                    NavigationBarItem(selected = tab == "friends" && !drawer.isOpen,
                        onClick = { tab = "friends"; demoChat = false; contactEmail = null; calls.refreshContacts() },
                        icon = { Icon(Icons.Default.Face, null) }, label = { Text("Amigos") })
                    NavigationBarItem(selected = tab == "profile" && !drawer.isOpen,
                        onClick = { tab = "profile" },
                        icon = { Icon(Icons.Default.Person, null) }, label = { Text("Perfil") })
                }
                }
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).background(ChatBackground)) {
                CallPanel(calls)
                callState.error?.let { Text(it, Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.error) }
                callState.room?.let { room ->
                    if (tab != "servers" || channelId != room.channelId) TextButton(onClick = { channelId = room.channelId; channelName = callState.roomName; channelType = "VOICE"; tab = "servers" }) { Text("Voltar à chamada: ${callState.roomName}") }
                }
                Box(Modifier.weight(1f)) {
                when {
                    tab == "profile" -> ProfileScreen(app, email) { calls.end(); onLogout() }
                    tab == "servers" && channelId != null && channelType == "VOICE" -> CallRoomScreen(calls, channelId!!, channelName) { scope.launch { drawer.open() } }
                    tab == "servers" && channelId != null -> channelContent(channelId!!, channelName) {
                        scope.launch { drawer.open() }
                    }
                    demoChat -> DemoConversation(demoMessages, { demoMessages = ArrayList(demoMessages + it) }) { demoChat = false }
                    contactEmail != null -> key(contactEmail) { DirectConversation(app, email, CallContact(contactEmail!!, contactName), calls) { contactEmail = null } }
                    else -> FriendsScreen(callState.contacts, { contactEmail = it.email; contactName = it.username }, { calls.refreshContacts() }) { demoChat = true }
                }
                }
            }
        }
    }
}

@Composable
private fun FriendsScreen(contacts: List<CallContact>, onContact: (CallContact) -> Unit, onRefresh: () -> Unit, onChat: () -> Unit) {
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
                    InitialAvatar(contact.username)
                    Text(contact.username, Modifier.weight(1f).padding(horizontal = 12.dp))
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
