package com.samuschat.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.samuschat.SamusChatApplication
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
import com.samuschat.ui.call.CallRoomScreen
import com.samuschat.data.repository.data

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    app: SamusChatApplication,
    email: String,
    servers: ServerViewModel,
    notificationTarget: com.samuschat.data.push.NotificationDestination? = null,
    onNotificationHandled: () -> Unit = {},
    onLogout: () -> Unit,
    channelContent: @Composable (Long, String, String, () -> Unit) -> Unit
) {
    var publicId by rememberSaveable(email) { mutableStateOf<String?>(null) }
    var identityError by remember { mutableStateOf(false) }
    var retry by remember { mutableStateOf(0) }
    LaunchedEffect(email, retry) {
        try {
            publicId = app.api.profile(app.tokens.authorization()).data().publicId
            publicId?.let { app.tokens.rememberIdentity(email, it) }
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
    val scope = rememberCoroutineScope()
    // Buttons switch directly; HorizontalPager still handles the swipe gesture.
    fun navigate(page: Int) { scope.launch { pager.scrollToPage(page) } }
    var pushNotice by remember { mutableStateOf<com.samuschat.data.push.NotificationDestination?>(null) }
    var notificationError by remember { mutableStateOf<String?>(null) }
    suspend fun openNotification(target: com.samuschat.data.push.NotificationDestination) {
        if (target.recipientId != identity) { notificationError = "Este aviso pertence a outra conta."; return }
        try {
            val contact = target.contactId?.let { id -> app.api.callContacts(app.tokens.authorization()).data().firstOrNull { it.email == id } }
            if (contact != null) {
                contactEmail = contact.email; contactName = contact.username
                pager.scrollToPage(1)
                return
            }
            val channel = app.servers.list().flatMap { it.channels }.firstOrNull { it.id == target.channelId && it.type == "TEXT" }
                ?: error("A conversa deste aviso não está disponível para esta conta.")
            channelId = channel.id; channelName = channel.name; channelType = channel.type
            pager.scrollToPage(0)
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (error: Exception) { notificationError = error.message ?: "Não foi possível abrir a conversa." }
    }
    LaunchedEffect(notificationTarget, identity) {
        notificationTarget?.let { target ->
            openNotification(target)
            onNotificationHandled()
        }
    }
    LaunchedEffect(identity) {
        app.notificationEvents.collect { target ->
            if (target.contactId == null || !inbox.live) pushNotice = target
        }
    }
    DisposableEffect(tab, contactEmail, channelId) {
        app.visibleConversation = when {
            tab == "friends" && contactEmail != null -> "direct:$contactEmail"
            tab == "servers" && channelId != null -> "channel:$channelId"
            else -> null
        }
        onDispose { app.visibleConversation = null }
    }
    BackHandler(pager.currentPage != 1 || contactEmail != null) {
        contactEmail = null
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
                    onClick = { navigate(1); contactEmail = null; calls.refreshContacts() },
                    icon = { Icon(Icons.Default.Face, null) }, label = { Text("Amigos") })
                NavigationBarItem(selected = tab == "profile",
                    onClick = { navigate(2) },
                    icon = { Icon(Icons.Default.Person, null) }, label = { Text("Perfil") })
            }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).background(ChatBackground)) {
            notificationError?.let { error ->
                TextButton(onClick = { notificationError = null }) { Text(error, color = MaterialTheme.colorScheme.error) }
            }
            pushNotice?.let { target ->
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Você tem uma nova mensagem.", Modifier.weight(1f))
                    TextButton(onClick = { pushNotice = null; scope.launch { openNotification(target) } }) { Text("Abrir") }
                    TextButton(onClick = { pushNotice = null }) { Text("Fechar") }
                }
            }
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
                contactEmail != null -> key(contactEmail) { DirectConversation(app, identity, CallContact(contactEmail!!, contactName), calls, inbox, pager.currentPage == 1) { contactEmail = null } }
                else -> FriendsScreen(app, pager.currentPage == 1, inbox.unread, { contactEmail = it.email; contactName = it.username }, { calls.refreshContacts() })
            }
            }
        }
    }
}
