package com.samuschat.ui.server

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.samuschat.ui.components.ServerItem

@Composable
fun ServerListScreen(viewModel: ServerViewModel, onChannelClick: (Long, String) -> Unit, onLogout: () -> Unit) {
    val servers by viewModel.servers.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    var name by rememberSaveable { mutableStateOf("") }
    var id by rememberSaveable { mutableStateOf("") }
    LazyColumn(Modifier.fillMaxSize().imePadding().padding(16.dp)) {
        item {
            Text("Seus servidores", style = MaterialTheme.typography.headlineMedium)
            Row {
                TextButton(onClick = { viewModel.refresh() }, enabled = !busy) { Text("Atualizar") }
                TextButton(onClick = onLogout) { Text("Sair") }
            }
            OutlinedTextField(name, { name = it }, label = { Text("Novo servidor") })
            Button(onClick = { viewModel.create(name) }, enabled = !busy && name.isNotBlank()) { Text("Criar") }
            OutlinedTextField(id, { id = it }, label = { Text("ID para entrar") })
            Button(onClick = { viewModel.join(id) }, enabled = !busy) { Text("Entrar no servidor") }
            if (busy) CircularProgressIndicator()
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (!busy && servers.isEmpty()) Text("Crie ou entre em um servidor para conversar.")
        }
        items(servers, key = { it.id }) { ServerItem(it, onChannelClick) }
    }
}
