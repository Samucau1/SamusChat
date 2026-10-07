package com.samuschat.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.InputStream
import java.io.OutputStream

object AttachmentPolicy {
    const val MAX_BYTES = 10L * 1024 * 1024
    val images = arrayOf("image/jpeg", "image/png", "image/gif", "image/webp")
    val documents = arrayOf("application/pdf", "text/plain", "application/msword",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
    val videos = arrayOf("video/mp4", "video/webm", "video/3gpp")
    val types = images + documents + videos
    fun validate(type: String?, size: Long?) {
        require(type in types) { "Escolha uma imagem, PDF, TXT, DOC, DOCX ou vídeo MP4, WebM ou 3GP." }
        require(size == null || size <= MAX_BYTES) { "O arquivo deve ter no máximo 10 MB." }
        require(size == null || size != 0L) { "O arquivo está vazio." }
    }
    /** Check actual bytes even when a document provider omits or understates its size. */
    fun copy(input: InputStream, output: OutputStream): Long {
        val buffer = ByteArray(8192)
        var total = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            require(total <= MAX_BYTES) { "O arquivo deve ter no máximo 10 MB." }
            output.write(buffer, 0, count)
        }
        require(total > 0) { "O arquivo está vazio." }
        return total
    }
}

data class SelectedAttachment(val uri: Uri, val name: String, val type: String, val size: Long?)

suspend fun inspectAttachment(context: Context, uri: Uri): SelectedAttachment = withContext(Dispatchers.IO) {
    val resolver = context.contentResolver
    val type = resolver.getType(uri)
    var name = "anexo"
    var size: Long? = null
    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && !cursor.isNull(nameIndex)) name = cursor.getString(nameIndex)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex).takeIf { it >= 0 }
        }
    }
    AttachmentPolicy.validate(type, size)
    val safeName = name.substringAfterLast('/').substringAfterLast('\\').replace(Regex("[\\r\\n\"]"), "_").take(180).ifBlank { "anexo" }
    SelectedAttachment(uri, safeName, checkNotNull(type), size)
}

/** Cache a bounded document snapshot for the duration of one upload, then always remove it. */
suspend fun <T> withAttachmentPart(context: Context, selected: SelectedAttachment,
    upload: suspend (MultipartBody.Part) -> T): T {
    var snapshot: File? = null
    try {
        val ready = withContext(Dispatchers.IO) {
            val file = File.createTempFile("attachment-", ".tmp", context.cacheDir)
            snapshot = file
            val input = context.contentResolver.openInputStream(selected.uri) ?: error("Não foi possível abrir o arquivo. Selecione-o novamente.")
            input.use { source -> file.outputStream().use { destination -> AttachmentPolicy.copy(source, destination) } }
            file
        }
        return upload(MultipartBody.Part.createFormData("file", selected.name, ready.asRequestBody(selected.type.toMediaType())))
    } finally { snapshot?.delete() }
}
