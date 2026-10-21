package com.samuschat.ui.call

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Phone
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.samuschat.ui.theme.ControlBackground

@Composable
fun CallPanel(model: CallViewModel) {
    val state by model.state.collectAsStateWithLifecycle()
    val video by model.remoteVideo.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) model.accept() else model.permissionDenied()
    }
    fun microphone() {
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            model.accept()
        } else permission.launch(Manifest.permission.RECORD_AUDIO)
    }
    DisposableEffect(owner, model) {
        model.foreground(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) model.foreground(true)
            if (event == Lifecycle.Event.ON_STOP) model.foreground(false)
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    state.call?.let { call ->
        val peer = state.contacts.firstOrNull { it.email == call.peer(model.email) }
        val peerName = peer?.username ?: "Participante"
        Dialog(onDismissRequest = {}, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false, usePlatformDefaultWidth = false)) {
            Surface(Modifier.fillMaxSize(), color = CallBackground, contentColor = Color.White) {
                BoxWithConstraints(Modifier.fillMaxSize().systemBarsPadding()) {
                    Column(Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp + maxHeight * 0.15f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(peerName, style = MaterialTheme.typography.titleLarge)
                            Text(when { state.connected -> "Em chamada"; call.state == "RINGING" -> if (call.callee == model.email) "Chamada recebida" else "Chamando…"; else -> "Conectando…" })
                            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                            if (!state.remoteSharing) CallParticipant(peerName, if (state.connected) "Em chamada" else "Aguardando conexão", avatarUrl = peer?.avatarUrl, speaking = "remote" in state.speaking)
                            CallParticipant("Você", if (state.muted) "Microfone silenciado" else "Microfone ativo", avatarUrl = state.ownAvatarUrl, speaking = model.email in state.speaking && !state.muted)
                            model.rtc?.let { engine ->
                                if (state.sharing) {
                                    CallParticipant("Sua tela", "Prévia da transmissão", sharing = true, avatarUrl = state.ownAvatarUrl, speaking = model.email in state.speaking && !state.muted) {
                                        Text("Sua tela • prévia", style = MaterialTheme.typography.labelMedium)
                                        ScreenShareVideo(engine.localVideo, engine.egl.eglBaseContext,
                                            Modifier.fillMaxWidth().height(240.dp), local = true, name = "Voc?", avatarUrl = state.ownAvatarUrl, speaking = model.email in state.speaking && !state.muted)
                                    }
                                }
                                if (state.remoteSharing) video?.let { track ->
                                    CallParticipant(peerName, "Transmitindo tela", sharing = true, avatarUrl = peer?.avatarUrl, speaking = "remote" in state.speaking) {
                                        Text("Tela de ${peerName}", style = MaterialTheme.typography.labelMedium)
                                        ScreenShareVideo(track, engine.egl.eglBaseContext,
                                            Modifier.fillMaxWidth().height(240.dp), name = peerName, avatarUrl = peer?.avatarUrl, speaking = "remote" in state.speaking)
                                    }
                                }
                            }
                        }
                        if (CallPolicy.mayAccept(call, model.email)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedButton(onClick = { model.end(decline = true) }, modifier = Modifier.weight(1f)) { Text("Recusar", color = CallRed) }
                                Button(onClick = { microphone() }, enabled = !state.busy, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = CallGreen, contentColor = Color.Black)) { Text("Aceitar") }
                            }
                        } else {
                            CallControls(model, connected = state.connected)
                        }
                    }
                }
            }
        }
    }
}

private val ScreenPhone = ImageVector.Builder("ScreenPhone", 24.dp, 24.dp, 24f, 24f).apply {
    path(fill = SolidColor(Color.Black), pathFillType = androidx.compose.ui.graphics.PathFillType.EvenOdd) {
        moveTo(6f, 1f); lineTo(18f, 1f); lineTo(18f, 23f); lineTo(6f, 23f); close()
        moveTo(8f, 4f); lineTo(8f, 19f); lineTo(16f, 19f); lineTo(16f, 4f); close()
        moveTo(11f, 20f); lineTo(13f, 20f); lineTo(13f, 22f); lineTo(11f, 22f); close()
    }
}.build()

@Composable
fun ScreenShareButton(model: CallViewModel, compact: Boolean = false, connected: Boolean = true) {
    val state by model.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val projection = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) result.data?.let(model::share)
    }
    val shareAction: () -> Unit = {
        if (state.sharing) model.stopSharing()
        else projection.launch(context.getSystemService(MediaProjectionManager::class.java).createScreenCaptureIntent())
    }
    if (compact) {
        CallControl(if (state.sharing) "Parar tela" else "Transmitir",
        if (state.sharing) "Parar transmissão" else "Compartilhar tela do celular", ScreenPhone,
        color = if (state.sharing) CallGreen else ControlBackground,
        enabled = !state.busy && (connected || state.sharing), onClick = shareAction)
    } else FilledTonalButton(onClick = shareAction, enabled = !state.busy && (connected || state.sharing)) {
        Icon(ScreenPhone, null); Spacer(Modifier.width(8.dp))
        Text(if (state.sharing) "Parar compartilhamento" else "Compartilhar tela")
    }
}

@Composable
fun FriendCallButton(model: CallViewModel, contact: CallContact) {
    val state by model.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) model.invite(contact) else model.permissionDenied()
    }
    IconButton(onClick = {
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) model.invite(contact)
        else permission.launch(Manifest.permission.RECORD_AUDIO)
    }, enabled = !state.busy && state.call == null && state.room == null) { Icon(Icons.Default.Phone, "Ligar") }
}
