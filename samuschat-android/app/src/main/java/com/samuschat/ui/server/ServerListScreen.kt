package com.samuschat.ui.server

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.samuschat.ui.components.*
import com.samuschat.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerListScreen(viewModel: ServerViewModel, onChannelClick: (Long, String) -> Unit) {
    val servers by viewModel.servers.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var sheet by rememberSaveable { mutableStateOf<String?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    var inviteId by rememberSaveable { mutableStateOf("") }
    val current = servers.firstOrNull { it.id == selectedId } ?: servers.firstOrNull()
    val results = remember(servers, query) { findServers(servers, query) }
    val openSheet: (String) -> Unit = { viewModel.error.value = null; sheet = it }

    Column(Modifier.fillMaxSize()) {
        // These primary actions remain fixed at the requested top corners.
        Row(Modifier.fillMaxWidth().background(RailBackground).padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            FilledIconButton(onClick = { openSheet("create") },
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Blurple)) {
                Icon(Icons.Default.Add, contentDescription = "Criar servidor")
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text("SamusChat", style = MaterialTheme.typography.titleLarge)
                Text("SEUS SERVIDORES", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { openSheet("search") }) {
                Icon(Icons.Default.Search, contentDescription = "Procurar servidor")
            }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (sheet == null) error?.let { ErrorNotice(it, Modifier.padding(12.dp)) }
        Row(Modifier.weight(1f)) {
            LazyColumn(Modifier.width(76.dp).fillMaxHeight().background(RailBackground),
                horizontalAlignment = Alignment.CenterHorizontally,
                contentPadding = PaddingValues(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(servers, key = { it.id }) { server ->
                    val active = current?.id == server.id
                    Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(4.dp).height(if (active) 32.dp else 8.dp)
                            .background(if (active) Color.White else Color.Transparent, RoundedCornerShape(4.dp)))
                        Spacer(Modifier.width(8.dp))
                        Surface(onClick = { selectedId = server.id }, modifier = Modifier.size(52.dp)
                            .semantics { contentDescription = "Servidor ${server.name}"; selected = active },
                            shape = RoundedCornerShape(if (active) 16.dp else 26.dp),
                            color = if (active) Blurple else ChatBackground) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(server.name.take(2).uppercase(), style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight()) {
                if (current != null) {
                    Box(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF414780), PanelBackground)))
                        .padding(horizontal = 20.dp, vertical = 24.dp)) {
                        Column {
                            Text(current.name, style = MaterialTheme.typography.headlineMedium)
                            Spacer(Modifier.height(6.dp))
                            Text(current.description?.takeIf { it.isNotBlank() } ?: "Seu espaço para conversar e compartilhar.",
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(12.dp))
                            Text("ID do servidor: ${current.id}", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("CANAIS DE TEXTO", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        IconButton(onClick = { viewModel.refresh() }, enabled = !busy) {
                            Icon(Icons.Default.Refresh, "Atualizar servidores", Modifier.size(20.dp))
                        }
                    }
                    LazyColumn(contentPadding = PaddingValues(horizontal = 10.dp)) {
                        items(current.channels.filter { it.type == "TEXT" }, key = { it.id }) { channel ->
                            Surface(onClick = { onChannelClick(channel.id, channel.name) },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                color = ChatBackground, shape = RoundedCornerShape(8.dp)) {
                                Row(Modifier.padding(horizontal = 12.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("#", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(channel.name, Modifier.weight(1f).padding(start = 12.dp), style = MaterialTheme.typography.titleMedium)
                                    Icon(Icons.Default.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        if (current.channels.none { it.type == "TEXT" }) item {
                            Text("Este servidor ainda não tem canais de texto.", Modifier.padding(12.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
                        Text("Seu próximo papo\ncomeça aqui.", style = MaterialTheme.typography.headlineMedium)
                        Spacer(Modifier.height(12.dp))
                        Text("Crie um servidor no + ou use a lupa para entrar com um ID.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = { viewModel.refresh() }, enabled = !busy) { Text("Atualizar") }
                    }
                }
            }
        }
    }

    if (sheet != null) {
        ModalBottomSheet(onDismissRequest = { if (!busy) sheet = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = PanelBackground) {
            LazyColumn(Modifier.fillMaxWidth().imePadding(), contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (sheet == "create") "Crie seu servidor" else "Procurar servidor",
                            Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium)
                        IconButton(onClick = { sheet = null }, enabled = !busy) { Icon(Icons.Default.Close, "Fechar") }
                    }
                    Text(if (sheet == "create") "Um lugar só seu para reunir a galera." else "Encontre seus servidores ou entre usando um ID.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                error?.let { item { ErrorNotice(it) } }
                if (sheet == "create") {
                    item {
                        OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Nome do servidor") },
                            placeholder = { Text("Ex.: Cantinho dos games") }, singleLine = true, enabled = !busy)
                    }
                    item {
                        Button(onClick = { viewModel.create(name) { selectedId = it; name = ""; sheet = null } },
                            modifier = Modifier.fillMaxWidth(), enabled = !busy && name.isNotBlank() && name.trim().length <= 255) {
                            Text(if (busy) "Criando…" else "Criar servidor", Modifier.padding(6.dp))
                        }
                    }
                } else {
                    item {
                        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true,
                            leadingIcon = { Icon(Icons.Default.Search, null) }, label = { Text("Buscar por nome ou ID") })
                    }
                    item { Text("SEUS SERVIDORES", style = MaterialTheme.typography.labelSmall) }
                    items(results, key = { "result-${it.id}" }) { server ->
                        ServerItem(server) { selectedId = server.id; sheet = null }
                    }
                    if (results.isEmpty()) item {
                        Text("Nenhum servidor encontrado na sua lista.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
                    item {
                        Text("Recebeu um ID?", style = MaterialTheme.typography.titleMedium)
                        Text("Peça o ID a alguém do servidor para entrar.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    item {
                        OutlinedTextField(inviteId, { inviteId = it }, Modifier.fillMaxWidth(), singleLine = true,
                            label = { Text("ID do servidor") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), enabled = !busy)
                    }
                    item {
                        Button(onClick = { viewModel.join(inviteId) { selectedId = it; inviteId = ""; sheet = null } },
                            modifier = Modifier.fillMaxWidth(), enabled = !busy && (inviteId.trim().toLongOrNull() ?: 0) > 0) {
                            Text(if (busy) "Entrando…" else "Entrar no servidor", Modifier.padding(6.dp))
                        }
                    }
                }
            }
        }
    }
}
