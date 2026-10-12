package com.samuschat.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import com.samuschat.BuildConfig
import com.samuschat.util.attachmentUrl

fun appearanceUrl(url: String?, animated: Boolean): String? = url?.let {
    attachmentUrl(BuildConfig.BASE_URL, if (!animated && it.endsWith(".gif")) it.dropLast(4) + ".png" else it)
}
fun appearanceColor(value: String?, fallback: Color): Color =
    if (value?.matches(Regex("#[0-9a-fA-F]{6}")) == true) Color(android.graphics.Color.parseColor(value)) else fallback

@Composable fun AppearanceImage(url: String?, description: String?, modifier: Modifier = Modifier, animated: Boolean = false) {
    val context = LocalContext.current
    appearanceUrl(url, animated)?.let {
        AsyncImage(model = coil.request.ImageRequest.Builder(context).data(it).crossfade(false).build(),
            contentDescription = description, modifier = modifier, contentScale = ContentScale.Crop)
    }
}

@Composable fun AnimationPreference(content: @Composable (Boolean) -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("appearance_settings", android.content.Context.MODE_PRIVATE) }
    var reduce by remember { mutableStateOf(prefs.getBoolean("reduce_motion", true)) }
    androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        androidx.compose.material3.Checkbox(checked = reduce, onCheckedChange = { reduce = it; prefs.edit().putBoolean("reduce_motion", it).apply() })
        androidx.compose.material3.Text("Economizar animações")
    }
    content(!reduce)
}
