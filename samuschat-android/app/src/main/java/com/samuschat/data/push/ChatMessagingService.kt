package com.samuschat.data.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.samuschat.MainActivity
import com.samuschat.SamusChatApplication
import com.samuschat.data.model.DeviceTokenRequest
import com.samuschat.data.repository.checked
import kotlinx.coroutines.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class ChatMessagingService : FirebaseMessagingService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    override fun onNewToken(token: String) {
        scope.launch { register(application as SamusChatApplication, token) }
    }
    override fun onMessageReceived(message: RemoteMessage) {
        val manager = getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled()) return
        manager.createNotificationChannel(NotificationChannel("samuschat_messages", "Mensagens", NotificationManager.IMPORTANCE_DEFAULT))
        val pending = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(this, "samuschat_messages")
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(message.notification?.title ?: message.data["title"] ?: "SamusChat")
            .setContentText(message.notification?.body ?: message.data["body"] ?: "Nova mensagem")
            .setContentIntent(pending).setAutoCancel(true).build()
        manager.notify(message.messageId?.hashCode() ?: 1, notification)
    }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
    companion object {
        private suspend fun token(): String = suspendCancellableCoroutine { continuation ->
            FirebaseMessaging.getInstance().token.addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
                .addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
        }
        private suspend fun register(app: SamusChatApplication, token: String) {
            val jwt = app.tokens.getToken() ?: return
            try { app.api.registerDevice("Bearer $jwt", DeviceTokenRequest(token, Build.MODEL)).checked() }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { Log.w("SamusChat", "Registro de notificações indisponível; nova tentativa no próximo login") }
        }
        suspend fun syncToken(app: SamusChatApplication) {
            if (FirebaseApp.getApps(app).isEmpty()) return
            try { register(app, token()) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { Log.w("SamusChat", "Token de notificações indisponível") }
        }
        suspend fun logout(app: SamusChatApplication) {
            try {
                if (FirebaseApp.getApps(app).isNotEmpty()) {
                    withTimeout(5000) { app.api.unregisterDevice(app.tokens.authorization(), token()).checked() }
                }
            } catch (_: Exception) { Log.w("SamusChat", "Não foi possível remover o dispositivo remoto") }
            finally { app.tokens.clearSession() }
        }
    }
}
