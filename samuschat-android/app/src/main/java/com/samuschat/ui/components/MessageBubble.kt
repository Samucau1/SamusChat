package com.samuschat.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.samuschat.data.model.Message
import com.samuschat.util.Constants
import java.net.URI

@Composable
fun MessageBubble(message: Message, isOwnMessage: Boolean) {
    val uriHandler = LocalUriHandler.current
    Row(Modifier.fillMaxWidth().padding(6.dp), horizontalArrangement = if (isOwnMessage) Arrangement.End else Arrangement.Start) {
        Surface(color = if (isOwnMessage) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
            Column(Modifier.widthIn(max = 280.dp).padding(10.dp)) {
                Text(message.senderUsername, style = MaterialTheme.typography.labelMedium)
                message.attachmentUrl?.let { path ->
                    val url = runCatching { URI(Constants.BASE_URL).resolve(path).toString() }.getOrNull()
                    if (url != null && (url.startsWith("https://") || url.startsWith("http://"))) {
                        if (message.attachmentType == "IMAGE") AsyncImage(url, "Anexo", Modifier.sizeIn(maxWidth = 250.dp, maxHeight = 200.dp))
                        else TextButton(onClick = { runCatching { uriHandler.openUri(url) } }) { Text("Abrir anexo") }
                    }
                }
                message.content?.let { Text(it) }
                Text(message.createdAt.getOrNull(11)?.let { message.createdAt.drop(11).take(5) }.orEmpty(), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
