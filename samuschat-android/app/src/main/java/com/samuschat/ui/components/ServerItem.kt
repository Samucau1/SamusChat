package com.samuschat.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.samuschat.data.model.Server

@Composable
fun ServerItem(server: Server, onChannelClick: (Long, String) -> Unit) {
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(server.name, style = MaterialTheme.typography.titleLarge)
            server.description?.let { Text(it) }
            Text("ID: ${server.id}")
            server.channels.filter { it.type == "TEXT" }.forEach { channel ->
                TextButton(onClick = { onChannelClick(channel.id, channel.name) }) { Text("# ${channel.name}") }
            }
        }
    }
}
