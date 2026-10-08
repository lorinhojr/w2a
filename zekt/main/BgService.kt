package com.w2a.runtime

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import org.json.JSONObject

/**
 * Segundo plano (serviço "bg").
 *
 * - keepRunning: com o app minimizado o jogo continua rodando (o WebView não é
 *   pausado). Se o sistema congelar o app mesmo assim, o jogo recupera o tempo
 *   ao voltar (ver BackgroundRunner na ZEngine).
 * - audio: música tocando com "Tocar em segundo plano" → serviço em primeiro
 *   plano de mídia (notificação "Tocando…"), como apps de rádio/música. Só
 *   existe no app quando o projeto usa isso (recurso "bgaudio").
 */
class BgService(private val host: ZeHost) : ZeService {

    override val name = "bg"

    private var keepRunning = false
    private var audioPlaying = false

    /** O WebView deve continuar rodando com o app minimizado? */
    val keepWebAlive: Boolean get() = keepRunning || audioPlaying

    override fun handle(cmd: String, msg: JSONObject) {
        when (cmd) {
            "keepRunning" -> keepRunning = msg.optBoolean("on", false)
            "audio" -> {
                val playing = msg.optBoolean("playing", false)
                if (playing == audioPlaying) return
                audioPlaying = playing
                if (!BuildConfig.BG_AUDIO) return
                val ctx = host.activity
                if (playing) {
                    val title = msg.optString("title").take(80)
                    try {
                        val i = Intent(ctx, ZEPlaybackService::class.java).putExtra("title", title)
                        if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
                    } catch (e: Exception) {
                        host.emit(name, "error", JSONObject().put("error", errorText(e)))
                    }
                } else {
                    ctx.stopService(Intent(ctx, ZEPlaybackService::class.java))
                }
            }
        }
    }

    override fun onDestroy() {
        if (BuildConfig.BG_AUDIO && audioPlaying) host.activity.stopService(Intent(host.activity, ZEPlaybackService::class.java))
    }
}

/** Serviço em primeiro plano enquanto a música do jogo toca com o app minimizado. */
class ZEPlaybackService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val title = intent?.getStringExtra("title")?.takeIf { it.isNotBlank() } ?: applicationInfo.loadLabel(packageManager).toString()
        ServiceCompat.startForeground(this, NOTIF_ID, notification(this, title),
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0)
        return START_NOT_STICKY
    }

    // App removido da lista de recentes: a música para junto
    override fun onTaskRemoved(rootIntent: Intent?) {
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    companion object {
        private const val NOTIF_ID = 7201
        private const val CHANNEL = "ze_playback"

        private fun notification(ctx: Context, title: String): Notification {
            if (Build.VERSION.SDK_INT >= 26) {
                val nm = ctx.getSystemService(NotificationManager::class.java)
                if (nm != null && nm.getNotificationChannel(CHANNEL) == null) {
                    nm.createNotificationChannel(NotificationChannel(CHANNEL, "Reprodução", NotificationManager.IMPORTANCE_LOW))
                }
            }
            val open = Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val pi = PendingIntent.getActivity(ctx, 0, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            return NotificationCompat.Builder(ctx, CHANNEL)
                .setSmallIcon(ctx.applicationInfo.icon)
                .setContentTitle(title)
                .setContentText("Tocando")
                .setOngoing(true)
                .setSilent(true)
                .setContentIntent(pi)
                .build()
        }
    }
}
