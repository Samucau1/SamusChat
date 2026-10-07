package com.samuschat.ui.call

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
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
        Dialog(onDismissRequest = {}, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false, usePlatformDefaultWidth = false)) {
            Surface(Modifier.fillMaxWidth().padding(16.dp), shape = MaterialTheme.shapes.large) {
                Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(call.peer(model.email), style = MaterialTheme.typography.titleLarge)
                    Text(when { state.connected -> "Em chamada"; call.state == "RINGING" -> if (call.callee == model.email) "Chamada recebida" else "Chamando…"; else -> "Conectando…" })
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    model.rtc?.let { engine ->
                        if (state.sharing) {
                            Text("Sua tela • prévia", style = MaterialTheme.typography.labelMedium)
                            ScreenShareVideo(engine.localVideo, engine.egl.eglBaseContext,
                                Modifier.fillMaxWidth().height(240.dp), local = true)
                        }
                        if (state.remoteSharing) video?.let { track ->
                            Text("Tela de ${call.peer(model.email)}", style = MaterialTheme.typography.labelMedium)
                            ScreenShareVideo(track, engine.egl.eglBaseContext,
                                Modifier.fillMaxWidth().height(240.dp))
                        }
                    }
                    if (CallPolicy.mayAccept(call, model.email)) {
                        Button(onClick = { microphone() }, enabled = !state.busy) { Text("Aceitar") }
                        OutlinedButton(onClick = { model.end(decline = true) }) { Text("Recusar") }
                    } else {
                        if (state.connected) {
                            Row {
                                TextButton(onClick = model::mute) { Text(if (state.muted) "Ativar microfone" else "Silenciar") }
                                TextButton(onClick = model::speaker) { Text(if (state.speaker) "Desligar viva-voz" else "Viva-voz") }
                            }
                            ScreenShareButton(model)
                            if (state.sharing && Build.VERSION.SDK_INT >= 29) {
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Switch(checked = state.deviceAudio, onCheckedChange = { model.deviceAudio() }, enabled = !state.busy)
                                    Text("Áudio do dispositivo", Modifier.padding(top = 12.dp))
                                }
                                Text("Alguns aplicativos bloqueiam a captura de áudio.", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Button(onClick = { model.end() }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Encerrar") }
                    }
                }
            }
        }
    }
}

private val ScreenCamera = ImageVector.Builder("ScreenCamera", 24.dp, 24.dp, 24f, 24f).apply {
    path(fill = SolidColor(Color.Black)) {
        moveTo(3f, 5f); lineTo(16f, 5f); lineTo(16f, 10f); lineTo(22f, 6f)
        lineTo(22f, 18f); lineTo(16f, 14f); lineTo(16f, 19f); lineTo(3f, 19f); close()
    }
}.build()

@Composable
fun ScreenShareButton(model: CallViewModel) {
    val state by model.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val projection = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) result.data?.let(model::share)
    }
    FilledTonalButton(onClick = {
        if (state.sharing) model.stopSharing()
        else projection.launch(context.getSystemService(MediaProjectionManager::class.java).createScreenCaptureIntent())
    }, enabled = !state.busy) {
        Icon(ScreenCamera, null); Spacer(Modifier.width(8.dp))
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
