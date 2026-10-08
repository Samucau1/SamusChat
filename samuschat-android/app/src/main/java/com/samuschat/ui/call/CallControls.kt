package com.samuschat.ui.call

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

internal val CallBackground = Color(0xFF1E1F22)
internal val CallCard = Color(0xFF2B2D31)
internal val CallGreen = Color(0xFF23A55A)
internal val CallRed = Color(0xFFDA373C)

private val Microphone = ImageVector.Builder("Microphone", 24.dp, 24.dp, 24f, 24f).apply {
    path(fill = SolidColor(Color.Black)) {
        moveTo(9f, 3f); lineTo(15f, 3f); lineTo(15f, 13f); lineTo(9f, 13f); close()
        moveTo(5f, 10f); lineTo(7f, 10f); lineTo(7f, 14f); lineTo(10f, 17f)
        lineTo(14f, 17f); lineTo(17f, 14f); lineTo(17f, 10f); lineTo(19f, 10f)
        lineTo(19f, 15f); lineTo(15f, 19f); lineTo(13f, 19f); lineTo(13f, 21f)
        lineTo(11f, 21f); lineTo(11f, 19f); lineTo(9f, 19f); lineTo(5f, 15f); close()
    }
}.build()
private val Speaker = ImageVector.Builder("Speaker", 24.dp, 24.dp, 24f, 24f).apply {
    path(fill = SolidColor(Color.Black)) {
        moveTo(3f, 9f); lineTo(7f, 9f); lineTo(12f, 4f); lineTo(12f, 20f)
        lineTo(7f, 15f); lineTo(3f, 15f); close()
        moveTo(15f, 7f); lineTo(17f, 7f); lineTo(20f, 10f); lineTo(20f, 14f)
        lineTo(17f, 17f); lineTo(15f, 17f); lineTo(18f, 13f); lineTo(18f, 11f); close()
    }
}.build()
private val HangUp = ImageVector.Builder("HangUp", 24.dp, 24.dp, 24f, 24f).apply {
    path(fill = SolidColor(Color.Black)) {
        moveTo(2f, 11f); lineTo(7f, 7f); lineTo(17f, 7f); lineTo(22f, 11f)
        lineTo(22f, 16f); lineTo(17f, 16f); lineTo(17f, 12f); lineTo(7f, 12f)
        lineTo(7f, 16f); lineTo(2f, 16f); close()
    }
}.build()

@Composable
internal fun CallControl(label: String, description: String, icon: ImageVector, color: Color = Color(0xFF383A40), enabled: Boolean = true, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledIconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(48.dp), shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = color, contentColor = Color.White)) {
            Icon(icon, description)
        }
        Text(label, color = Color(0xFFDBDEE1), style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
internal fun CallControls(model: CallViewModel, connected: Boolean) {
    val state by model.state.collectAsStateWithLifecycle()
    Surface(color = CallCard, shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.sharing) Text("● Transmissão ao vivo • sua tela está sendo compartilhada", color = CallGreen,
                style = MaterialTheme.typography.labelMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Top) {
                CallControl(if (state.muted) "Ativar mic" else "Microfone", if (state.muted) "Ativar microfone" else "Silenciar microfone",
                    Microphone, color = if (state.muted) CallRed else Color(0xFF383A40), enabled = state.call != null || state.room != null, onClick = model::mute)
                CallControl(if (state.speaker) "Viva-voz" else "Celular", if (state.speaker) "Desligar viva-voz" else "Ativar viva-voz", Speaker,
                    color = if (state.speaker) Color(0xFF5865F2) else Color(0xFF383A40), enabled = state.call != null || state.room != null, onClick = model::speaker)
                ScreenShareButton(model, compact = true, connected = connected)
                CallControl("Encerrar", "Encerrar chamada", HangUp, color = CallRed, onClick = { model.end() })
            }
            if (state.sharing && Build.VERSION.SDK_INT >= 29) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Áudio do dispositivo", color = Color.White, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    Switch(checked = state.deviceAudio, onCheckedChange = { model.deviceAudio() }, enabled = !state.busy)
                }
                Text("Alguns aplicativos bloqueiam a captura de áudio.", color = Color(0xFFB5BAC1), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
internal fun CallParticipant(name: String, status: String, sharing: Boolean = false, content: @Composable ColumnScope.() -> Unit = {}) {
    Surface(color = CallCard, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(color = Color(0xFF5865F2), shape = CircleShape, modifier = Modifier.size(48.dp)) {
                    Box(contentAlignment = Alignment.Center) { Text(name.take(1).uppercase(), color = Color.White, style = MaterialTheme.typography.titleLarge) }
                }
                Column(Modifier.weight(1f)) {
                    Text(name, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                    Text(status, color = Color(0xFFB5BAC1), style = MaterialTheme.typography.bodySmall)
                }
                if (sharing) Text("AO VIVO", color = CallGreen, style = MaterialTheme.typography.labelSmall)
            }
            content()
        }
    }
}
