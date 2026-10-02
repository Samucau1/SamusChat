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
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.webrtc.SurfaceViewRenderer

@Composable
fun CallPanel(model: CallViewModel, showContacts: Boolean = true) {
    val state by model.state.collectAsStateWithLifecycle()
    val video by model.remoteVideo.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var pending by remember { mutableStateOf<CallContact?>(null) }
    var accepting by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) { if (accepting) model.accept() else pending?.let(model::invite) } else model.permissionDenied()
        pending = null; accepting = false
    }
    val projection = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) result.data?.let(model::share)
    }
    fun microphone(contact: CallContact? = null) {
        pending = contact; accepting = contact == null
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            if (contact == null) model.accept() else model.invite(contact)
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
    if (showContacts) Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        state.notice?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Chamadas individuais", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { model.refreshContacts() }, enabled = !state.busy) { Text("Atualizar") }
        }
        Text("Contatos dos seus servidores. Mantenha o app aberto para receber chamadas.", style = MaterialTheme.typography.bodySmall)
        if (state.contacts.isEmpty()) Text("Entre em um servidor com outra pessoa para ligar.", style = MaterialTheme.typography.bodySmall)
        Column(Modifier.heightIn(max = 150.dp).verticalScroll(rememberScrollState())) {
            state.contacts.forEach { contact ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(contact.username, Modifier.weight(1f).padding(top = 12.dp))
                    TextButton(onClick = { microphone(contact) }, enabled = state.call == null && !state.busy) { Text("Ligar") }
                }
            }
        }
    }
    state.call?.let { call ->
        Dialog(onDismissRequest = {}, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false, usePlatformDefaultWidth = false)) {
            Surface(Modifier.fillMaxWidth().padding(16.dp), shape = MaterialTheme.shapes.large) {
                Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(call.peer(model.email), style = MaterialTheme.typography.titleLarge)
                    Text(when { state.connected -> "Em chamada"; call.state == "RINGING" -> if (call.callee == model.email) "Chamada recebida" else "Chamando…"; else -> "Conectando…" })
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (state.remoteSharing && video != null && model.rtc != null) {
                        val track = video!!; val egl = model.rtc!!.egl.eglBaseContext
                        AndroidView(factory = { ctx -> SurfaceViewRenderer(ctx).apply { init(egl, null); setEnableHardwareScaler(true); track.addSink(this) } },
                            modifier = Modifier.fillMaxWidth().height(240.dp), onRelease = { track.removeSink(it); it.release() })
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
                            if (state.sharing) OutlinedButton(onClick = model::stopSharing) { Text("Parar compartilhamento") }
                            else Button(onClick = { projection.launch(context.getSystemService(MediaProjectionManager::class.java).createScreenCaptureIntent()) }, enabled = !state.busy) { Text("Compartilhar tela") }
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
