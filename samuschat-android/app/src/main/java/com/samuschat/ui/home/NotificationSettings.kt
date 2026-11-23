package com.samuschat.ui.home

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.firebase.FirebaseApp
import com.samuschat.SamusChatApplication
import com.samuschat.data.push.ChatMessagingService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun NotificationSettings(app: SamusChatApplication) {
    val context = LocalContext.current
    val manager = context.getSystemService(NotificationManager::class.java)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    var enabled by remember { mutableStateOf(ChatMessagingService.enabled(app)) }
    var permitted by remember { mutableStateOf(manager.areNotificationsEnabled()) }
    var busy by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    val configured = FirebaseApp.getApps(app).isNotEmpty()

    fun activate() {
        scope.launch {
            busy = true; notice = null
            try {
                ChatMessagingService.setEnabled(app, true); enabled = true
                val registered = ChatMessagingService.syncToken(app)
                notice = if (registered) "Notificações ativadas neste celular."
                    else "Não foi possível registrar os avisos. Confira a conexão e tente novamente."
            } catch (cancelled: CancellationException) { throw cancelled }
            finally { busy = false }
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permitted = granted
        if (granted) activate() else notice = "Permita as notificações nas configurações do Android."
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) permitted = manager.areNotificationsEnabled()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    HorizontalDivider()
    Text("Notificações", style = MaterialTheme.typography.titleMedium)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Avisos de novas mensagens", Modifier.weight(1f))
        Switch(checked = enabled && permitted && configured, enabled = !busy && configured, onCheckedChange = { value ->
            if (!value) {
                scope.launch {
                    busy = true
                    try { ChatMessagingService.disable(app); enabled = false; notice = "Avisos desativados neste celular." }
                    finally { busy = false }
                }
            } else if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else if (!permitted) {
                context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
            } else activate()
        })
    }
    if (!configured) Text("A ativação dos avisos em segundo plano está pendente neste aplicativo.", style = MaterialTheme.typography.bodySmall)
    if (configured && enabled && permitted) TextButton(onClick = { activate() }, enabled = !busy) { Text("Verificar recebimento de avisos") }
    TextButton(onClick = {
        context.startActivity(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName).putExtra(Settings.EXTRA_CHANNEL_ID, "samuschat_messages"))
    }) { Text("Som e notificações no Android") }
    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    notice?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    Spacer(Modifier.height(4.dp))
}
