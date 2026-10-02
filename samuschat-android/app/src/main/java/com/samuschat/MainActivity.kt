package com.samuschat

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import com.samuschat.ui.auth.*
import com.samuschat.ui.server.*
import com.samuschat.ui.chat.*
import com.samuschat.util.Session
import com.samuschat.ui.theme.SamusChatTheme
import com.samuschat.ui.theme.RailBackground
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val notifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                    LaunchedEffect(Unit) { app.tokens.session.collect { session = it; loaded = true } }
                    if (!loaded) CircularProgressIndicator()
                    else key(session?.token) {
                        val nav = rememberNavController()
                        val scope = rememberCoroutineScope()
                        if (session != null) {
                            LaunchedEffect(Unit) {
                                ChatMessagingService.syncToken(app)
                                if (Build.VERSION.SDK_INT >= 33) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            NavHost(nav, startDestination = "home") {
                                composable("home") {
                                    val servers: ServerViewModel = viewModel(factory = factory { ServerViewModel(app) })
                                    com.samuschat.ui.home.HomeScreen(app, session!!.email, servers,
                                        onLogout = { scope.launch { ChatMessagingService.logout(app) } }) { id, name, back ->
                                        key(id) {
                                            val chat: ChatViewModel = viewModel(key = "chat-$id", factory = factory { ChatViewModel(app, id) })
                                            ChatScreen(chat, name, session!!.email, back)
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
