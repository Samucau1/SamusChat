package com.samuschat

import android.app.Application
import com.samuschat.data.api.RetrofitClient
import com.samuschat.data.repository.*
import com.samuschat.util.TokenManager

class SamusChatApplication : Application(), coil.ImageLoaderFactory {
    override fun newImageLoader(): coil.ImageLoader = coil.ImageLoader.Builder(this).components {
        if (android.os.Build.VERSION.SDK_INT >= 28) add(coil.decode.ImageDecoderDecoder.Factory())
        else add(coil.decode.GifDecoder.Factory())
    }.build()
    override fun onCreate() {
        super.onCreate()
        getSystemService(android.app.NotificationManager::class.java).createNotificationChannel(
            android.app.NotificationChannel("samuschat_messages", "Mensagens", android.app.NotificationManager.IMPORTANCE_DEFAULT)
        )
    }
    val tokens by lazy { TokenManager(this) }
    val api by lazy { RetrofitClient.create(tokens) }
    val auth by lazy { AuthRepository(api, tokens) }
    val servers by lazy { ServerRepository(api, tokens) }
    val messages by lazy { MessageRepository(api, tokens) }
}
