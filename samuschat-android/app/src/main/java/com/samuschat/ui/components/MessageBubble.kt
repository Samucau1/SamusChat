package com.samuschat.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.samuschat.data.model.Message
import com.samuschat.util.Constants
import com.samuschat.util.attachmentUrl
import com.samuschat.util.isProtectedAttachment
import com.samuschat.util.openProtectedAttachment
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import coil.request.ImageRequest
import kotlinx.coroutines.launch

@Composable
fun MessageBubble(message: Message, isOwnMessage: Boolean) {
    val context = LocalContext.current
    val app = context.applicationContext as com.samuschat.SamusChatApplication
    val session by app.tokens.session.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    var downloading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        InitialAvatar(message.senderUsername, url = message.senderAvatarUrl)
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
                    if (message.attachmentType == "IMAGE" && isProtectedAttachment(Constants.BASE_URL, url) && session != null) AsyncImage(ImageRequest.Builder(context).data(url).addHeader("Authorization", "Bearer ${session!!.token}").build(), "Imagem enviada por ${message.senderUsername}",
                        Modifier.padding(top = 8.dp).sizeIn(maxWidth = 280.dp, maxHeight = 220.dp))
                    TextButton(enabled = !downloading, onClick = { scope.launch {
                        downloading = true; error = null
                        try { openProtectedAttachment(context, url) }
                        catch (e: kotlinx.coroutines.CancellationException) { throw e }
                        catch (_: Exception) { error = "Não foi possível abrir este anexo." }
                        finally { downloading = false }
                    } }) { Text(if (downloading) "Baixando…" else "Abrir anexo") }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
}
