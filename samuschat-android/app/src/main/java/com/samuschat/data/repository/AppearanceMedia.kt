package com.samuschat.data.repository

import android.content.Context
import android.net.Uri
import okhttp3.MultipartBody

object AppearancePolicy {
    const val MAX_BYTES = 4L * 1024 * 1024
    const val MAX_GIF_BYTES = 2L * 1024 * 1024
    fun validate(type: String, size: Long?) {
        require(type in listOf("image/png", "image/jpeg", "image/gif")) { "Escolha PNG, JPG ou GIF." }
        require(size == null || size in 1..if (type == "image/gif") MAX_GIF_BYTES else MAX_BYTES) { "PNG/JPG até 4 MB; GIF até 2 MB." }
    }
}
suspend fun <T> withAppearancePart(context: Context, uri: Uri, upload: suspend (MultipartBody.Part) -> T): T {
    val selected = inspectAttachment(context, uri)
    AppearancePolicy.validate(selected.type, selected.size)
    return withAttachmentPart(context, selected, if (selected.type == "image/gif") AppearancePolicy.MAX_GIF_BYTES else AppearancePolicy.MAX_BYTES, upload)
}
