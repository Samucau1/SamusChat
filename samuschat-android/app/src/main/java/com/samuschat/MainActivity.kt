package com.samuschat

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.*
import com.samuschat.data.push.ChatMessagingService
import com.samuschat.data.push.NotificationDestination
import com.samuschat.ui.auth.*
import com.samuschat.ui.server.*
import com.samuschat.ui.chat.*
import com.samuschat.util.Session
import com.samuschat.ui.theme.SamusChatTheme
import com.samuschat.ui.theme.RailBackground
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val notificationTarget = kotlinx.coroutines.flow.MutableStateFlow<NotificationDestination?>(null)
    private fun readNotification(intent: Intent?) {
        val data = listOf("type", "recipientId", "contactId", "channelId").mapNotNull { key ->
            intent?.getStringExtra(key)?.let { key to it }
        }.toMap()
        notificationTarget.value = NotificationDestination.fromData(data)
    }
    private fun notificationHandled() {
        notificationTarget.value = null
        // A later Activity recreation must not reopen an already handled notification.
        for (key in listOf("type", "recipientId", "contactId", "channelId")) intent?.removeExtra(key)
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readNotification(intent)
    }
    override fun onStart() { super.onStart(); (application as SamusChatApplication).inForeground = true }
    override fun onStop() { (application as SamusChatApplication).inForeground = false; super.onStop() }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        readNotification(intent)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.rgb(30, 31, 34)),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.rgb(30, 31, 34))
        )
        val app = application as SamusChatApplication
        setContent {
            SamusChatTheme {
                Surface(Modifier.fillMaxSize().background(RailBackground).systemBarsPadding()) {
                    var loaded by remember { mutableStateOf(false) }
                    var session by remember { mutableStateOf<Session?>(null) }
                    val target by notificationTarget.collectAsState()
                    LaunchedEffect(Unit) { app.tokens.session.collect { session = it; loaded = true } }
                    if (!loaded) CircularProgressIndicator()
                    else key(session?.email) {
                        val nav = rememberNavController()
                        val scope = rememberCoroutineScope()
                        if (session != null) {
                            LaunchedEffect(session?.token, session?.refreshToken) {
                                try { app.auth.rememberSession() }
                                catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                                catch (_: Exception) { /* Keep the saved session available for the next connection. */ }
                            }
                            LaunchedEffect(Unit) {
                                ChatMessagingService.syncToken(app)
                            }
                            NavHost(nav, startDestination = "home") {
                                composable("home") {
                                    val servers: ServerViewModel = viewModel(factory = factory { ServerViewModel(app) })
                                    com.samuschat.ui.home.HomeScreen(app, session!!.email, servers,
                                        notificationTarget = target, onNotificationHandled = { notificationHandled() },
                                        onLogout = { scope.launch { if (!ChatMessagingService.logout(app)) android.widget.Toast.makeText(this@MainActivity, "Não foi possível encerrar as sessões. Confira sua conexão.", android.widget.Toast.LENGTH_LONG).show() } }) { id, name, identity, back ->
                                        key(id) {
                                            val chat: ChatViewModel = viewModel(key = "chat-$id", factory = factory { ChatViewModel(app, id) })
                                            ChatScreen(chat, name, identity, back)
                                        }
                                    }
                                }
                            }
                        } else {
                            NavHost(nav, startDestination = "login") {
                                composable("login") {
                                    val vm: AuthViewModel = viewModel(factory = factory { AuthViewModel(app) })
                                    LoginScreen(vm) { nav.navigate("register") }
                                }
                                composable("register") {
                                    val vm: AuthViewModel = viewModel(factory = factory { AuthViewModel(app) })
                                    RegisterScreen(vm) { nav.popBackStack() }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun factory(create: () -> ViewModel) = object : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = create() as T
}
