package com.samuschat.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.samuschat.R
import com.samuschat.data.repository.AttachmentPolicy
import com.samuschat.ui.theme.PanelBackground

@Composable
fun AttachmentMenu(enabled: Boolean, onSelect: (Array<String>) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        FilledTonalIconButton(onClick = { expanded = !expanded }, enabled = enabled,
            modifier = Modifier.padding(bottom = 4.dp)) {
            Icon(Icons.Default.Add, "Adicionar anexo")
        }
        DropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false },
            modifier = Modifier.width(264.dp).background(PanelBackground)) {
            Text("Adicionar à conversa", Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.titleSmall)
            AttachmentOption("Imagem", "JPG, PNG, GIF e WebP", R.drawable.ic_attachment_photo) {
                expanded = false; onSelect(AttachmentPolicy.images)
            }
            AttachmentOption("Documento", "PDF, TXT, DOC e DOCX", R.drawable.ic_attachment_document) {
                expanded = false; onSelect(AttachmentPolicy.documents)
            }
            AttachmentOption("Vídeo", "MP4, WebM e 3GP", R.drawable.ic_attachment_video) {
                expanded = false; onSelect(AttachmentPolicy.videos)
            }
            Text("Até 10 MB por arquivo", Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AttachmentOption(label: String, formats: String, icon: Int, onClick: () -> Unit) {
    DropdownMenuItem(onClick = onClick, leadingIcon = {
        Icon(painterResource(icon), null, tint = MaterialTheme.colorScheme.primary)
    }, text = {
        Column {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(formats, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    })
}
