package com.w2a.runtime

/** Projeto sem compras no app: nada de Play Billing no APK. */
object IapFactory {
    fun create(host: ZeHost): List<ZeService> = emptyList()
}
