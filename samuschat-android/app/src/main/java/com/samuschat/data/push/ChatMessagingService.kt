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
import com.samuschat.data.repository.data
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class ChatMessagingService : FirebaseMessagingService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    override fun onNewToken(token: String) {
        scope.launch { register(application as SamusChatApplication, token) }
    }
    override fun onMessageReceived(message: RemoteMessage) {
        val app = application as SamusChatApplication
        if (!enabled(app)) return
        val session = runBlocking { app.tokens.session.first() } ?: return
        val destination = NotificationDestination.fromData(message.data) ?: return
        if (destination.recipientId != session.publicId) return
        if (app.inForeground) {
            if (app.visibleConversation != destination.conversationKey) app.notificationEvents.tryEmit(destination)
            return
        }
        val manager = getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled()) return
        manager.createNotificationChannel(NotificationChannel("samuschat_messages", "Mensagens", NotificationManager.IMPORTANCE_DEFAULT))
        val notificationId = destination.conversationKey.hashCode()
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        for (key in listOf("type", "recipientId", "contactId", "channelId")) message.data[key]?.let { intent.putExtra(key, it) }
        val pending = PendingIntent.getActivity(this, notificationId, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(this, "samuschat_messages")
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle("SamusChat")
            .setContentText("Você tem uma nova mensagem.")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Você tem uma nova mensagem."))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(pending).setAutoCancel(true).build()
        manager.notify(notificationId, notification)
    }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
    companion object {
        fun enabled(app: SamusChatApplication) = app.getSharedPreferences("notifications", android.content.Context.MODE_PRIVATE).getBoolean("enabled", true)
        fun setEnabled(app: SamusChatApplication, value: Boolean) {
            app.getSharedPreferences("notifications", android.content.Context.MODE_PRIVATE).edit().putBoolean("enabled", value).apply()
            if (!value) clearNotifications(app)
        }
        private fun clearNotifications(app: SamusChatApplication) {
            val manager = app.getSystemService(NotificationManager::class.java)
            manager.activeNotifications.filter { it.notification.channelId == "samuschat_messages" }
                .forEach { manager.cancel(it.tag, it.id) }
        }
        private suspend fun token(): String = suspendCancellableCoroutine { continuation ->
            FirebaseMessaging.getInstance().token.addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
                .addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
        }
        private suspend fun register(app: SamusChatApplication, token: String): Boolean {
            if (!enabled(app)) return false
            val jwt = app.tokens.getToken() ?: return false
            return try { app.api.registerDevice("Bearer $jwt", DeviceTokenRequest(token, Build.MODEL)).checked(); true }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { Log.w("SamusChat", "Registro de notificações indisponível; tente novamente nas configurações"); false }
        }
        suspend fun syncToken(app: SamusChatApplication): Boolean {
            if (!enabled(app) || FirebaseApp.getApps(app).isEmpty()) return false
            return try {
                if (app.api.notificationStatus(app.tokens.authorization()).data()["enabled"] != true) return false
                register(app, token())
            }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { Log.w("SamusChat", "Token de notificações indisponível"); false }
        }
        suspend fun disable(app: SamusChatApplication) {
            setEnabled(app, false)
            if (FirebaseApp.getApps(app).isEmpty()) return
            try { withTimeout(5000) { app.api.unregisterDevice(app.tokens.authorization(), token()).checked() } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { Log.w("SamusChat", "Dispositivo silenciado localmente; registro remoto indisponível") }
        }
        suspend fun logout(app: SamusChatApplication): Boolean {
            try {
                if (FirebaseApp.getApps(app).isNotEmpty()) {
                    withTimeout(5000) { app.api.unregisterDevice(app.tokens.authorization(), token()).checked() }
                }
            } catch (_: Exception) { Log.w("SamusChat", "Não foi possível remover o dispositivo remoto") }
            try { app.api.logout(app.tokens.authorization()).checked() }
            catch (e: CancellationException) { throw e }
            catch (e: com.samuschat.data.repository.ApiFailure) { if (e.status != 401) return false }
            catch (_: Exception) { return false }
            app.tokens.clearSession()
            clearNotifications(app)
            java.io.File(app.cacheDir, "attachments").listFiles()?.filter { it.isFile }?.forEach { it.delete() }
            return true
        }
    }
}
