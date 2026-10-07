package com.samuschat.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.samuschat.data.model.Message
import com.samuschat.util.Constants
import com.samuschat.util.attachmentUrl

@Composable
fun MessageBubble(message: Message, isOwnMessage: Boolean) {
    val uriHandler = LocalUriHandler.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        InitialAvatar(message.senderUsername)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(message.senderUsername, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold, color = if (isOwnMessage) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
                Text(message.createdAt.drop(11).take(5), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            message.content?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
            message.attachmentUrl?.let { path ->
                val url = attachmentUrl(Constants.BASE_URL, path)
                if (url != null && (url.startsWith("https://") || url.startsWith("http://"))) {
                    if (message.attachmentType == "IMAGE") AsyncImage(url, "Imagem enviada por ${message.senderUsername}",
                        Modifier.padding(top = 8.dp).sizeIn(maxWidth = 280.dp, maxHeight = 220.dp))
                    else TextButton(onClick = { runCatching { uriHandler.openUri(url) } }) { Text("Abrir anexo") }
                }
            }
        }
    }
}
