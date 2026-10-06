package com.w2a.runtime

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.KeyEvent
import android.view.WindowManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

/**
 * Roda o jogo HTML5 de assets/www em tela cheia.
 *
 * - Os arquivos são servidos em https://appassets.androidplatform.net/assets/www/
 *   (WebViewAssetLoader): fetch, Web Workers e WebAssembly funcionam sem
 *   liberar acesso a arquivos locais.
 * - Ponte com o jogo (só para a origem do próprio app): window.ZEAndroid.postMessage(
 *   "vibrate:200" | "exit" ). O botão Voltar dispara o evento "zeandroidback"
 *   na página; se o jogo chamar preventDefault(), o app não fecha.
 */
class MainActivity : Activity() {

    private lateinit var web: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        web = WebView(this)
        web.setBackgroundColor(0xFF000000.toInt())
        setContentView(web)
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
                if (url.scheme == "http" || url.scheme == "https" || url.scheme == "mailto") {
                    try { startActivity(Intent(Intent.ACTION_VIEW, url)) } catch (_: ActivityNotFoundException) { }
                }
                return true
            }
        }

        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            WebViewCompat.addWebMessageListener(web, "ZEAndroid", setOf("https://$APP_HOST")) { _, message, _, _, _ ->
                onGameMessage(message.data ?: "")
            }
        }

        if (savedInstanceState != null) web.restoreState(savedInstanceState)
        else web.loadUrl("https://$APP_HOST/assets/www/index.html")
    }

    private fun onGameMessage(msg: String) {
        when {
            msg == "exit" -> runOnUiThread { finishAndRemoveTask() }
            msg.startsWith("vibrate:") -> vibrate(msg.substringAfter(':').toLongOrNull()?.coerceIn(1, 5000) ?: 50)
        }
    }

    @Suppress("DEPRECATION")
    private fun vibrate(ms: Long) {
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
        val c = WindowInsetsControllerCompat(window, web)
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
        web.onPause()
        web.pauseTimers()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        web.resumeTimers()
        web.onResume()
        hideSystemBars()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }

    override fun onDestroy() {
        web.destroy()
        super.onDestroy()
    }

    companion object {
        private const val APP_HOST = "appassets.androidplatform.net"
    }
}
