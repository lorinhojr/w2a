package com.w2a.runtime

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import org.json.JSONObject

object FirebaseFactory {
    fun create(host: ZeHost): List<ZeService> = listOf(FcmService(host), AnalyticsService(host))
}

private const val CHANNEL_ID = "ze_default"
private const val PERMISSION_CODE = 7101

/** Canal padrão das notificações (Android 8+). */
private fun ensureChannel(ctx: Context) {
    if (Build.VERSION.SDK_INT < 26) return
    val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
    if (nm.getNotificationChannel(CHANNEL_ID) == null) {
        nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Notificações", NotificationManager.IMPORTANCE_DEFAULT))
    }
}

/** Dados de uma notificação tocada (extras do Intent) → JSON. */
private fun extrasJson(b: Bundle): JSONObject {
    val o = JSONObject()
    for (k in b.keySet()) {
        if (k.startsWith("google.") || k.startsWith("gcm.") || k == "from" || k == "collapse_key") continue
        @Suppress("DEPRECATION")
        val v = b.get(k)
        if (v is String || v is Number || v is Boolean) o.put(k, v)
    }
    return o
}

/**
 * Push (Firebase Cloud Messaging) no formato do plugin "Cloud Messaging".
 * Eventos: token{token,refresh}, permission{granted}, message{title,body,imageUrl,data,background}, badge{value}, error.
 */
class FcmService(private val host: ZeHost) : ZeService {

    override val name = "fcm"
    private val main = Handler(Looper.getMainLooper())
    private val ctx: Context = host.activity.applicationContext
    private val prefs = ctx.getSharedPreferences("ze_fcm", Context.MODE_PRIVATE)

    init { ensureChannel(ctx) }

    private fun ui(block: () -> Unit) { main.post(block) }
    private fun fail(e: Exception?) = host.emit(name, "error", JSONObject().put("error", errorText(e)))

    override fun handle(cmd: String, msg: JSONObject) {
        val fm = FirebaseMessaging.getInstance()
        when (cmd) {
            "requestPermission" -> {
                if (Build.VERSION.SDK_INT >= 33) host.requestPermission(Manifest.permission.POST_NOTIFICATIONS, PERMISSION_CODE)
                else onPermissionResult(PERMISSION_CODE, NotificationManagerCompat.from(ctx).areNotificationsEnabled())
            }
            "getToken" -> fm.token.addOnCompleteListener { t ->
                ui {
                    if (t.isSuccessful && t.result != null) host.emit(name, "token", JSONObject().put("token", t.result))
                    else fail(t.exception)
                }
            }
            "deleteToken" -> fm.deleteToken().addOnCompleteListener { t -> ui { if (!t.isSuccessful) fail(t.exception) } }
            "subscribe" -> topic(msg)?.let { tp ->
                fm.subscribeToTopic(tp).addOnCompleteListener { t -> ui { if (!t.isSuccessful) fail(t.exception) } }
            }
            "unsubscribe" -> topic(msg)?.let { tp ->
                fm.unsubscribeFromTopic(tp).addOnCompleteListener { t -> ui { if (!t.isSuccessful) fail(t.exception) } }
            }
            "clearNotifications" -> NotificationManagerCompat.from(ctx).cancelAll()
            "getBadge" -> host.emit(name, "badge", JSONObject().put("value", prefs.getInt("badge", 0)))
            "setBadge" -> prefs.edit().putInt("badge", msg.optInt("value", 0).coerceAtLeast(0)).apply()
        }
    }

    private fun topic(msg: JSONObject): String? {
        val t = msg.optString("topic").trim()
        return if (Regex("^[A-Za-z0-9\\-_.~%]{1,900}$").matches(t)) t else null
    }

    override fun onPermissionResult(requestCode: Int, granted: Boolean): Boolean {
        if (requestCode != PERMISSION_CODE) return false
        host.emit(name, "permission", JSONObject().put("granted", granted))
        return true
    }

    /** App aberto (ou trazido de volta) pelo toque numa notificação */
    override fun onNewIntent(intent: Intent) {
        val b = intent.extras ?: return
        if (!b.containsKey("google.message_id") && !b.containsKey("ze_push")) return
        val data = extrasJson(b)
        data.remove("ze_push")
        host.emit(name, "message", JSONObject()
            .put("title", b.getString("ze_title") ?: "")
            .put("body", b.getString("ze_body") ?: "")
            .put("data", data)
            .put("background", true))
        intent.replaceExtras(Bundle())
    }
}

/** Serviço do Firebase: token novo e mensagens recebidas. */
class ZEMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        val a = MainActivity.current?.get() ?: return
        a.emit("fcm", "token", JSONObject().put("token", token).put("refresh", true))
    }

    override fun onMessageReceived(m: RemoteMessage) {
        val n = m.notification
        val title = n?.title ?: m.data["title"] ?: ""
        val body = n?.body ?: m.data["body"] ?: ""
        val image = n?.imageUrl?.toString() ?: m.data["image"] ?: ""
        val data = JSONObject()
        for ((k, v) in m.data) data.put(k, v)
        val a = MainActivity.current?.get()
        if (a != null && MainActivity.isForeground()) {
            a.emit("fcm", "message", JSONObject().put("title", title).put("body", body).put("imageUrl", image).put("data", data))
            return
        }
        // App em segundo plano com mensagem só de dados: mostra a notificação
        if (title.isEmpty() && body.isEmpty()) return
        ensureChannel(this)
        val open = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("ze_push", "1")
            putExtra("ze_title", title)
            putExtra("ze_body", body)
            for ((k, v) in m.data) if (!k.startsWith("ze_")) putExtra(k, v)
        }
        val pi = PendingIntent.getActivity(this, (System.currentTimeMillis() and 0xFFFFFFF).toInt(), open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(applicationInfo.icon)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        val nm = NotificationManagerCompat.from(this)
        if (Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            try { nm.notify((System.currentTimeMillis() and 0xFFFFFFF).toInt(), notif) } catch (_: SecurityException) { }
        }
    }
}

/** Firebase Analytics no formato do plugin "Firebase Analytics". */
class AnalyticsService(private val host: ZeHost) : ZeService {

    override val name = "analytics"
    private val fa = FirebaseAnalytics.getInstance(host.activity.applicationContext)

    private fun bundle(o: JSONObject?): Bundle {
        val b = Bundle()
        if (o == null) return b
        val keys = o.keys()
        var n = 0
        while (keys.hasNext() && n < 25) {
            val k = keys.next()
            if (!Regex("^[A-Za-z][A-Za-z0-9_]{0,39}$").matches(k)) continue
            when (val v = o.opt(k)) {
                is Int -> b.putLong(k, v.toLong())
                is Long -> b.putLong(k, v)
                is Number -> b.putDouble(k, v.toDouble())
                is Boolean -> b.putLong(k, if (v) 1 else 0)
                is String -> b.putString(k, v.take(100))
                else -> continue
            }
            n++
        }
        return b
    }

    override fun handle(cmd: String, msg: JSONObject) {
        when (cmd) {
            "logEvent" -> {
                val ev = msg.optString("name")
                if (Regex("^[A-Za-z][A-Za-z0-9_]{0,39}$").matches(ev)) fa.logEvent(ev, bundle(msg.optJSONObject("params")))
            }
            "setUserProperty" -> {
                val k = msg.optString("name")
                if (Regex("^[A-Za-z][A-Za-z0-9_]{0,23}$").matches(k)) fa.setUserProperty(k, msg.optString("value").take(36).ifEmpty { null })
            }
            "setUserId" -> fa.setUserId(msg.optString("id").take(256).ifEmpty { null })
            "reset" -> fa.resetAnalyticsData()
            "setEnabled" -> fa.setAnalyticsCollectionEnabled(msg.optBoolean("enabled", true))
            "setDefaults" -> fa.setDefaultEventParameters(bundle(msg.optJSONObject("params")))
        }
    }
}
