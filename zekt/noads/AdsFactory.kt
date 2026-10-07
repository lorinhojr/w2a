package com.w2a.runtime

/** Projeto sem anúncios: nada do SDK de anúncios no APK. */
object AdsFactory {
    fun create(host: ZeHost): List<ZeService> = emptyList()
}
