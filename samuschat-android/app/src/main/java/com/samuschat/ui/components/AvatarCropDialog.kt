package com.samuschat.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

@Composable
fun AvatarCropDialog(uri: Uri, close: () -> Unit, save: (ByteArray) -> Unit, original: () -> Unit) {
    val context = LocalContext.current
    var bitmap by remember(uri) { mutableStateOf<Bitmap?>(null) }
    var gif by remember(uri) { mutableStateOf(false) }
    var error by remember(uri) { mutableStateOf<String?>(null) }
    var zoom by remember(uri) { mutableFloatStateOf(1f) }
    var x by remember(uri) { mutableFloatStateOf(0.5f) }
    var y by remember(uri) { mutableFloatStateOf(0.5f) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(uri) {
        try {
            val loaded = withContext(Dispatchers.IO) {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytesLimited(4 * 1024 * 1024) } ?: error("Não foi possível abrir a imagem.")
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                require(bounds.outMimeType in setOf("image/png", "image/jpeg", "image/gif")) { "Use PNG, JPG ou GIF." }
                require(bounds.outWidth in 1..4096 && bounds.outHeight in 1..4096 && bounds.outWidth.toLong() * bounds.outHeight <= 16_000_000) { "Use imagem de até 4096 px e 16 megapixels." }
                val isGif = bytes.take(3).toByteArray().contentEquals("GIF".toByteArray())
                require(!isGif || bytes.size <= 2 * 1024 * 1024) { "GIF deve ter até 2 MB." }
                val decoded = if (Build.VERSION.SDK_INT >= 28) ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) { decoder, _, _ -> decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE }
                    else decodeOrientedBitmap(bytes)
                decoded to isGif
            }
            bitmap = loaded.first; gif = loaded.second
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (failure: Exception) { error = failure.message ?: "Não foi possível abrir a foto." }
    }
    Dialog(onDismissRequest = { if (!busy) close() }) {
        Surface(shape = MaterialTheme.shapes.large) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Enquadrar foto de perfil", style = MaterialTheme.typography.titleLarge)
                Text("Arraste a imagem e ajuste o zoom. O círculo mostra como sua foto vai aparecer.")
                bitmap?.let { image ->
                    Canvas(Modifier.fillMaxWidth().aspectRatio(1f).clip(CircleShape).background(Color.Black).pointerInput(image, zoom, busy) {
                        detectDragGestures { change, amount ->
                            if (!busy) {
                                change.consume()
                                val rect = avatarRect(image.width, image.height, zoom, x, y)
                                val scale = size.width / rect.side
                                x = (x - amount.x / maxOf(1f, (image.width - rect.side) * scale)).coerceIn(0f, 1f)
                                y = (y - amount.y / maxOf(1f, (image.height - rect.side) * scale)).coerceIn(0f, 1f)
                            }
                        }
                    }) {
                        val rect = avatarRect(image.width, image.height, zoom, x, y)
                        val scale = size.width / rect.side
                        drawContext.canvas.nativeCanvas.drawBitmap(image, null, RectF(-rect.left * scale, -rect.top * scale, (image.width - rect.left) * scale, (image.height - rect.top) * scale), android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
                    }
                    Text("Zoom"); Slider(zoom, { zoom = it }, valueRange = 1f..4f, enabled = !busy)
                    Text("Posição horizontal"); Slider(x, { x = it }, enabled = !busy)
                    Text("Posição vertical"); Slider(y, { y = it }, enabled = !busy)
                }
                if (gif) Text("O recorte salva uma foto estática. Para manter a animação, use o GIF original.")
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (bitmap == null && error == null) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                TextButton(onClick = { zoom = 1f; x = 0.5f; y = 0.5f }, enabled = !busy) { Text("Centralizar") }
                if (gif) TextButton(onClick = original, enabled = !busy) { Text("Usar GIF original") }
                Button(enabled = bitmap != null && !busy, onClick = {
                    val image = bitmap ?: return@Button; busy = true
                    scope.launch {
                        try {
                            val rect = avatarRect(image.width, image.height, zoom, x, y)
                            val bytes = withContext(Dispatchers.Default) {
                                val output = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
                                try {
                                    val scale = 512f / rect.side
                                    android.graphics.Canvas(output).drawBitmap(image, null, RectF(-rect.left * scale, -rect.top * scale, (image.width - rect.left) * scale, (image.height - rect.top) * scale), android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
                                    ByteArrayOutputStream().use { stream -> check(output.compress(Bitmap.CompressFormat.PNG, 100, stream)); stream.toByteArray() }
                                } finally { output.recycle() }
                            }
                            save(bytes)
                        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                        catch (failure: Exception) { error = failure.message ?: "Não foi possível preparar a foto." }
                        finally { busy = false }
                    }
                }) { Text("Salvar enquadramento") }
                TextButton(onClick = close, enabled = !busy) { Text("Cancelar recorte") }
            }
        }
    }
}

private fun java.io.InputStream.readBytesLimited(max: Int): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) { val count = read(buffer); if (count < 0) break; require(output.size() + count <= max) { "Imagem deve ter até 4 MB." }; output.write(buffer, 0, count) }
    return output.toByteArray()
}

private fun decodeOrientedBitmap(bytes: ByteArray): Bitmap {
    val image = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: kotlin.error("Imagem inválida.")
    val orientation = runCatching { android.media.ExifInterface(java.io.ByteArrayInputStream(bytes)).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1) }.getOrDefault(1)
    val matrix = android.graphics.Matrix().apply {
        when (orientation) {
            2 -> setScale(-1f, 1f)
            3 -> setRotate(180f)
            4 -> setScale(1f, -1f)
            5 -> { setRotate(90f); postScale(-1f, 1f) }
            6 -> setRotate(90f)
            7 -> { setRotate(270f); postScale(-1f, 1f) }
            8 -> setRotate(270f)
        }
    }
    if (matrix.isIdentity) return image
    return Bitmap.createBitmap(image, 0, 0, image.width, image.height, matrix, true).also { if (it !== image) image.recycle() }
}
