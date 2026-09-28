package com.samuschat

import android.app.Application
import com.samuschat.data.api.RetrofitClient
import com.samuschat.data.repository.*
import com.samuschat.util.TokenManager

class SamusChatApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        getSystemService(android.app.NotificationManager::class.java).createNotificationChannel(
            android.app.NotificationChannel("samuschat_messages", "Mensagens", android.app.NotificationManager.IMPORTANCE_DEFAULT)
        )
    }
    val tokens by lazy { TokenManager(this) }
    val api by lazy { RetrofitClient.api }
    val auth by lazy { AuthRepository(api, tokens) }
    val servers by lazy { ServerRepository(api, tokens) }
    val messages by lazy { MessageRepository(api, tokens) }
}
