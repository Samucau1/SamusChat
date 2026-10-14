package com.samuschat.ui.server

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.samuschat.data.model.Server
import com.samuschat.ui.components.*

@Composable fun ServerAppearanceDialog(server: Server, viewModel: ServerViewModel, busy: Boolean, error: String?, close: () -> Unit) {
    var color by rememberSaveable(server.accentColor) { mutableStateOf(server.accentColor ?: "#5865F2") }
    var slot by rememberSaveable { mutableStateOf("icon") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { viewModel.media(server.id, slot, it) } }
    AlertDialog(onDismissRequest = { if (!busy) close() }, title = { Text("Personalizar servidor") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            error?.let { ErrorNotice(it) }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            AnimationPreference { animated -> AppearanceImage(server.iconUrl, "Ícone do servidor", Modifier.size(64.dp), animated) }
            AppearanceImage(server.bannerUrl, "Capa do servidor", Modifier.fillMaxWidth().height(100.dp))
            Text("PNG/JPG até 4 MB. GIF até 2 MB e 60 quadros; ícone até 512 px, capa/fundo até 960×540. Fundos usam a versão estática para economizar recursos.")
            for ((key, label, url) in listOf(Triple("icon", "Ícone", server.iconUrl), Triple("banner", "Capa", server.bannerUrl), Triple("background", "Fundo", server.backgroundUrl))) {
                TextButton(onClick = { slot = key; picker.launch(arrayOf("image/png", "image/jpeg", "image/gif")) }, enabled = !busy) { Text("Escolher $label") }
                if (url != null) TextButton(onClick = { viewModel.media(server.id, key, null) }, enabled = !busy) { Text("Remover $label") }
            }
            OutlinedTextField(color, { color = it }, label = { Text("Cor (#RRGGBB)") }, singleLine = true, enabled = !busy)
            Button(onClick = { viewModel.saveAppearance(server.id, color) }, enabled = !busy && color.matches(Regex("#[0-9a-fA-F]{6}"))) { Text("Salvar cor") }
        } }, confirmButton = { TextButton(onClick = close, enabled = !busy) { Text("Fechar") } })
}
