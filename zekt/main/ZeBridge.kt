package com.w2a.runtime

import android.app.Activity
import android.content.Intent
import android.webkit.WebView
import android.widget.FrameLayout
import org.json.JSONObject

// ============================================================================
// Ponte jogo ↔ app (protocolo JSON da ZEngine)
//   jogo → app: {"ze":"<serviço>","cmd":"...", ...}
//   app  → jogo: {"ze":"<serviço>","ev":"...", ...}
// Os serviços opcionais (compras, anúncios, Firebase) só entram no app quando
// o projeto usa o plugin correspondente (pastas zekt/<recurso> x zekt/no<recurso>).
// ============================================================================

/** Quem conversa com o jogo (a MainActivity). */
interface ZeHost {
    val activity: Activity
    val root: FrameLayout
    val web: WebView
    /** Manda {"ze":service,"ev":ev,...} para o jogo. Pode ser chamado de qualquer thread. */
    fun emit(service: String, ev: String, data: JSONObject = JSONObject())
    /** Pede uma permissão em tempo de execução; a resposta chega em ZeService.onPermissionResult. */
    fun requestPermission(permission: String, requestCode: Int)
}

/** Um serviço nativo. Todos os métodos rodam na thread principal. */
interface ZeService {
    val name: String
    fun handle(cmd: String, msg: JSONObject)
    fun onResume() {}
    fun onPause() {}
    fun onDestroy() {}
    /** true = a permissão era deste serviço */
    fun onPermissionResult(requestCode: Int, granted: Boolean): Boolean = false
    fun onNewIntent(intent: Intent) {}
}

/** Erros do nativo viram texto curto para o jogo (sem stack trace). */
fun errorText(e: Throwable?): String =
    (e?.message ?: e?.javaClass?.simpleName ?: "erro").take(500)
