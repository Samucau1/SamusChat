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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.webrtc.SurfaceViewRenderer

@Composable
fun CallRoomScreen(model: CallViewModel, id: Long, name: String, onBack: () -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val videos by model.roomVideos.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) model.joinRoom(id, name) else model.permissionDenied()
    }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
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
                    Text(participant.username + when { participant.email == model.email -> " (você)"; link?.id in state.roomConnected -> " • conectado"; else -> " • conectando" })
                    val track = videos[link?.id]
                    val engine = model.roomRtc
                    if (link?.id in state.roomSharing && track != null && engine != null) {
                        Text("Tela de ${participant.username}", style = MaterialTheme.typography.labelMedium)
                        AndroidView(factory = { ctx -> SurfaceViewRenderer(ctx).apply {
                            init(engine.egl.eglBaseContext, null); setScalingType(org.webrtc.RendererCommon.ScalingType.SCALE_ASPECT_FIT); track.addSink(this)
                        } }, modifier = Modifier.fillMaxWidth().height(220.dp), onRelease = { track.removeSink(it); it.release() })
                    }
                }
            }
            Row {
                TextButton(onClick = model::mute) { Text(if (state.muted) "Ativar microfone" else "Silenciar") }
                TextButton(onClick = model::speaker) { Text(if (state.speaker) "Desligar viva-voz" else "Viva-voz") }
            }
            ScreenShareButton(model)
            OutlinedButton(onClick = { model.end() }) { Text("Sair da chamada") }
        }
    }
}
