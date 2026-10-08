package com.w2a.runtime

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.KeyEvent
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONArray
import org.json.JSONObject
import java.lang.ref.WeakReference

/**
 * Roda o jogo HTML5 de assets/www em tela cheia.
 *
 * - Arquivos servidos em https://appassets.androidplatform.net/assets/www/
 *   (WebViewAssetLoader): fetch, Workers e WebAssembly sem liberar arquivos locais.
 * - Ponte com o jogo só para a origem do próprio app (window.ZEAndroid):
 *   mensagens JSON da ZEngine (ver ZeBridge.kt) + as antigas "exit" / "vibrate:N".
 * - Voltar: dispara "zeandroidback" na página; se o jogo cancelar, o app não fecha.
 */
class MainActivity : Activity(), ZeHost {

    private lateinit var rootView: FrameLayout
    private lateinit var webView: WebView
    override val activity: Activity get() = this
    override val root: FrameLayout get() = rootView
    override val web: WebView get() = webView

    private val main = Handler(Looper.getMainLooper())
    private val services = LinkedHashMap<String, ZeService>()
    private var reply: JavaScriptReplyProxy? = null
    /** Mensagens para o jogo antes de ele falar com o app (ex.: toque numa notificação) */
    private val outbox = ArrayList<String>()
    private var resumed = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        current = WeakReference(this)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        rootView = FrameLayout(this)
        rootView.setBackgroundColor(0xFF000000.toInt())
        webView = WebView(this)
        web.setBackgroundColor(0xFF000000.toInt())
        root.addView(web, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        setContentView(root)
        hideSystemBars()

        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = false
            allowContentAccess = false
            loadWithOverviewMode = true
            useWideViewPort = true
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            textZoom = 100
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }

        val loader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                loader.shouldInterceptRequest(request.url)

            // Links externos abrem no navegador; o jogo fica no app
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url
                if (url.host == APP_HOST) return false
                if (url.scheme == "http" || url.scheme == "https" || url.scheme == "mailto") openUrl(url)
                return true
            }
        }

        // Serviços que este app tem (dependem dos plugins usados no projeto)
        for (s in listOf<ZeService>(BgService(this)) + IapFactory.create(this) + AdsFactory.create(this) + FirebaseFactory.create(this)) services[s.name] = s

        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            WebViewCompat.addWebMessageListener(web, "ZEAndroid", setOf("https://$APP_HOST")) { _, message, _, isMainFrame, proxy ->
                if (isMainFrame) {
                    reply = proxy
                    flushOutbox()
                    onGameMessage(message.data ?: "")
                }
            }
        }

        if (savedInstanceState != null) web.restoreState(savedInstanceState)
        else web.loadUrl("https://$APP_HOST/assets/www/index.html")

        intent?.let { i -> services.values.forEach { it.onNewIntent(i) } }
    }

    // ── Ponte ────────────────────────────────────────────────────────────────

    private fun onGameMessage(raw: String) {
        if (raw.length > 1_000_000) return
        when {
            raw == "exit" -> { finishAndRemoveTask(); return }
            raw.startsWith("vibrate:") -> { vibrate(raw.substringAfter(':').toLongOrNull() ?: 50); return }
            !raw.startsWith("{") -> return
        }
        val msg = try { JSONObject(raw) } catch (_: Exception) { return }
        val service = msg.optString("ze")
        val cmd = msg.optString("cmd")
        if (service == "app") {
            when (cmd) {
                "hello" -> {
                    val f = JSONArray()
                    f.put("app")
                    services.keys.forEach { f.put(it) }
                    if (BuildConfig.BG_AUDIO) f.put("bgaudio")
                    emit("app", "hello", JSONObject().put("features", f))
                }
                "exit" -> finishAndRemoveTask()
                "vibrate" -> vibrate(msg.optLong("ms", 50))
                "openUrl" -> {
                    val u = Uri.parse(msg.optString("url"))
                    if (u.scheme == "https" || u.scheme == "http" || u.scheme == "mailto" || u.scheme == "market") openUrl(u)
                }
            }
            return
        }
        val s = services[service] ?: run {
            emit(service, "error", JSONObject().put("error", "Este app não tem o recurso \"$service\"."))
            return
        }
        try {
            s.handle(cmd, msg)
        } catch (e: Exception) {
            emit(service, "error", JSONObject().put("error", errorText(e)))
        }
    }

    override fun emit(service: String, ev: String, data: JSONObject) {
        val text = try { data.put("ze", service).put("ev", ev).toString() } catch (_: Exception) { return }
        if (Looper.myLooper() == Looper.getMainLooper()) post(text) else main.post { post(text) }
    }

    private fun post(text: String) {
        val r = reply
        if (r == null || isDestroyed) {
            if (outbox.size < 100) outbox.add(text)
            return
        }
        try { r.postMessage(text) } catch (_: Exception) { }
    }

    private fun flushOutbox() {
        if (outbox.isEmpty()) return
        val list = ArrayList(outbox)
        outbox.clear()
        list.forEach { post(it) }
    }

    override fun requestPermission(permission: String, requestCode: Int) {
        if (Build.VERSION.SDK_INT < 23 || checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) {
            services.values.any { it.onPermissionResult(requestCode, true) }
            return
        }
        requestPermissions(arrayOf(permission), requestCode)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val granted = grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }
        services.values.any { it.onPermissionResult(requestCode, granted) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        services.values.forEach { it.onNewIntent(intent) }
    }

    private fun openUrl(url: Uri) {
        try { startActivity(Intent(Intent.ACTION_VIEW, url)) } catch (_: ActivityNotFoundException) { }
    }

    @Suppress("DEPRECATION")
    private fun vibrate(msRaw: Long) {
        val ms = msRaw.coerceIn(1, 5000)
        val v: Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
            (getSystemService(VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            getSystemService(VIBRATOR_SERVICE) as? Vibrator
        }
        if (v == null || !v.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        else v.vibrate(ms)
    }

    private fun hideSystemBars() {
        val c = WindowInsetsControllerCompat(window, root)
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        c.hide(WindowInsetsCompat.Type.systemBars())
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    // Voltar: o jogo decide (evento cancelável); sem tratamento, fecha o app
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            web.evaluateJavascript(
                "(function(){var e=new CustomEvent('zeandroidback',{cancelable:true});return window.dispatchEvent(e);})()"
            ) { notCancelled -> if (notCancelled != "false") finish() }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onPause() {
        resumed = false
        services.values.forEach { it.onPause() }
        // Jogo/música em segundo plano: o WebView continua rodando
        if ((services["bg"] as? BgService)?.keepWebAlive != true) {
            web.onPause()
            web.pauseTimers()
        }
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        web.resumeTimers()
        web.onResume()
        hideSystemBars()
        services.values.forEach { it.onResume() }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }

    override fun onDestroy() {
        services.values.forEach { try { it.onDestroy() } catch (_: Exception) { } }
        services.clear()
        reply = null
        if (current?.get() === this) current = null
        web.destroy()
        super.onDestroy()
    }

    companion object {
        private const val APP_HOST = "appassets.androidplatform.net"

        /** Activity viva (para o serviço de push avisar o jogo) */
        @Volatile var current: WeakReference<MainActivity>? = null

        /** true se o jogo está na tela agora */
        fun isForeground(): Boolean = current?.get()?.resumed == true
    }
}
