package com.samuschat.ui.call

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.samuschat.MainActivity

/** Owns the required foreground notification; media is owned by the account-scoped call model. */
class CallMediaService : Service() {
    companion object {
        var onStopRequested: (() -> Unit)? = null
        var onReady: (() -> Unit)? = null
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "END") { onStopRequested?.invoke(); stopSelf(); return START_NOT_STICKY }
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel("calls", "Chamadas", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val end = PendingIntent.getService(this, 1, Intent(this, CallMediaService::class.java).setAction("END"), PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(this, "calls").setSmallIcon(android.R.drawable.sym_call_incoming)
            .setContentTitle("Chamada SamusChat em andamento").setContentText("Toque para voltar à chamada")
            .setContentIntent(open).setOngoing(true).addAction(Notification.Action.Builder(null, "Encerrar", end).build()).build()
        if (Build.VERSION.SDK_INT >= 29) {
            var type = if (Build.VERSION.SDK_INT >= 30) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0
            if (intent?.getBooleanExtra("screen", false) == true) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            startForeground(42, notification, type)
        } else startForeground(42, notification)
        onReady?.also { onReady = null; it() }
        return START_NOT_STICKY
    }
    override fun onTaskRemoved(rootIntent: Intent?) { onStopRequested?.invoke(); stopSelf() }
    override fun onBind(intent: Intent?): IBinder? = null
}
