package com.w2a.runtime

import android.app.Service
import android.content.Intent
import android.os.IBinder

/** Projeto sem Firebase (sem google-services.json). */
object FirebaseFactory {
    fun create(host: ZeHost): List<ZeService> = emptyList()
}

/** Classe vazia: o AndroidManifest declara o serviço de push nos dois casos. */
class ZEMessagingService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
}
