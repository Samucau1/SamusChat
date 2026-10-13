package com.samuschat.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.samuschat.BuildConfig
import com.samuschat.SamusChatApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.URI
import java.util.UUID

fun isProtectedAttachment(base: String, value: String): Boolean = runCatching {
    val origin = URI(base); val url = URI(value)
    origin.scheme == url.scheme && origin.host == url.host && origin.port == url.port && url.userInfo == null &&
        url.path.matches(Regex("/uploads/[0-9a-f-]{36}\\.[a-z0-9]+")) && url.query == null
}.getOrDefault(false)

private val attachmentClient = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).build()
suspend fun openProtectedAttachment(context: Context, value: String) {
    require(isProtectedAttachment(Constants.BASE_URL, value)) { "Anexo inválido" }
    val app = context.applicationContext as SamusChatApplication
    val authorization = app.tokens.authorization()
    val result = withContext(Dispatchers.IO) {
        attachmentClient.newCall(Request.Builder().url(value).header("Authorization", authorization).build()).execute().use { response ->
            require(response.isSuccessful) { "Não foi possível acessar o anexo" }
            val body = requireNotNull(response.body)
            require(body.contentLength() <= 10 * 1024 * 1024) { "Anexo muito grande" }
            val directory = File(app.cacheDir, "attachments").apply { mkdirs() }
            val extension = URI(value).path.substringAfterLast('.')
            val file = File(directory, "${UUID.randomUUID()}.$extension")
            try {
                body.byteStream().use { input -> file.outputStream().use { output ->
                    val buffer = ByteArray(8192); var total = 0L
                    while (true) { val count = input.read(buffer); if (count < 0) break; total += count
                        require(total <= 10 * 1024 * 1024) { "Anexo muito grande" }; output.write(buffer, 0, count) }
                } }
            } catch (problem: Exception) { file.delete(); throw problem }
            file to response.header("Content-Type", "application/octet-stream")!!.substringBefore(';')
        }
    }
    val uri = FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.attachments", result.first)
    context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, result.second).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
}
