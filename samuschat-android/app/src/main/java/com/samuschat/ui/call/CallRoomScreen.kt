package com.samuschat.ui.call

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun CallRoomScreen(model: CallViewModel, id: Long, name: String, onBack: () -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val videos by model.roomVideos.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) model.joinRoom(id, name) else model.permissionDenied()
    }
    Surface(color = CallBackground, contentColor = androidx.compose.ui.graphics.Color.White, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar aos servidores") }
                Text(name, Modifier.weight(1f).padding(12.dp), style = MaterialTheme.typography.titleLarge)
            }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (state.room?.channelId != id) {
                Text("Canal de chamada", style = MaterialTheme.typography.headlineMedium)
                Text("Entre para conversar e compartilhar sua tela com os membros deste servidor.")
                Button(onClick = {
                    if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) model.joinRoom(id, name)
                    else permission.launch(Manifest.permission.RECORD_AUDIO)
                }, enabled = !state.busy && state.call == null && state.room == null) { Text("Entrar na chamada") }
                if (state.room != null || state.call != null) Text("Encerre a chamada atual antes de entrar neste canal.")
            } else {
                val room = state.room!!
                Text("${room.participants.size}/${room.capacity} participantes", style = MaterialTheme.typography.titleMedium)
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(room.participants, key = { it.email }) { participant ->
                        val link = room.sessions.firstOrNull { it.peer(model.email) == participant.email }
                        val engine = model.roomRtc
                        val local = participant.email == model.email
                        val sharing = if (local) state.sharing else link?.id in state.roomSharing
                        val track = if (local) engine?.localVideo else videos[link?.id]
                        CallParticipant(participant.username, when { local -> "Você • conectado"; link?.id in state.roomConnected -> "Conectado"; else -> "Conectando…" }, sharing) {
                            if (sharing && track != null && engine != null) {
                                Text(if (local) "Sua tela • prévia" else "Tela de ${participant.username}", style = MaterialTheme.typography.labelMedium)
                                ScreenShareVideo(track, engine.egl.eglBaseContext,
                                    Modifier.fillMaxWidth().height(220.dp), local = local)
                            }
                        }
                    }
                }
                CallControls(model, connected = true)
            }
        }
    }
}
