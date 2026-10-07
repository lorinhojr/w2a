package com.w2a.runtime

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAd
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAdLoadCallback
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import org.json.JSONObject

object AdsFactory {
    fun create(host: ZeHost): List<ZeService> = listOf(AdsService(host))
}

/**
 * Anúncios AdMob (Google Mobile Ads) com consentimento (UMP), no formato do
 * plugin "Mobile Advert" da ZEngine. O ID do app AdMob vai no AndroidManifest
 * (vem das propriedades do plugin, no build).
 *
 * Eventos: configured, configFailed, consent, bannerLoaded/Failed/Shown/Hidden,
 * interstitialLoaded/Failed/Complete/Cancelled, rewardedLoaded/Failed/Complete/Cancelled,
 * rewardedInterstitialLoaded/Failed/Complete/Cancelled, idfa.
 */
class AdsService(private val host: ZeHost) : ZeService {

    override val name = "ads"

    private val main = Handler(Looper.getMainLooper())
    private val ctx: Context = host.activity.applicationContext
    private val consent: ConsentInformation = UserMessagingPlatform.getConsentInformation(host.activity)

    private var testMode = false
    private var configured = false
    private var configuring = false
    /** Pedidos feitos antes do consentimento/configuração terminar */
    private val queue = ArrayList<Pair<String, JSONObject>>()

    private var banner: AdView? = null
    private var bannerVisible = false
    private var bannerOverlap = true
    private var interstitial: InterstitialAd? = null
    private var rewarded: RewardedAd? = null
    private var rewardedInterstitial: RewardedInterstitialAd? = null

    private fun emit(ev: String, data: JSONObject = JSONObject()) = host.emit(name, ev, data)
    private fun ui(block: () -> Unit) { main.post(block) }

    override fun handle(cmd: String, msg: JSONObject) {
        when (cmd) {
            "configure" -> configure(msg)
            "consentDialog" -> UserMessagingPlatform.showPrivacyOptionsForm(host.activity) { err ->
                ui {
                    if (err != null) emit("consentFailed", JSONObject().put("error", err.message))
                    emit("consent", consentJson())
                }
            }
            "requestIdfa" -> emit("idfa", JSONObject().put("status", "not-determined"))
            "settings" -> applySettings(msg)
            else -> {
                if (!configured) {
                    if (queue.size < 20) queue.add(cmd to msg)
                    if (!configuring) configure(JSONObject())
                    return
                }
                run(cmd, msg)
            }
        }
    }

    private fun run(cmd: String, msg: JSONObject) {
        when (cmd) {
            "createBanner" -> createBanner(msg)
            "showBanner" -> showBanner()
            "hideBanner" -> hideBanner()
            "createInterstitial" -> createInterstitial(msg.optString("unit"), msg.optBoolean("show"))
            "showInterstitial" -> showInterstitial()
            "createRewarded" -> createRewarded(msg.optString("unit"), msg.optBoolean("show"))
            "showRewarded" -> showRewarded()
            "createRewardedInterstitial" -> createRewardedInterstitial(msg.optString("unit"), msg.optBoolean("show"))
            "showRewardedInterstitial" -> showRewardedInterstitial()
        }
    }

    // ── Configuração + consentimento ─────────────────────────────────────────

    private fun consentJson(): JSONObject {
        val st = when (consent.consentStatus) {
            ConsentInformation.ConsentStatus.NOT_REQUIRED -> "NOT_REQUIRED"
            ConsentInformation.ConsentStatus.REQUIRED -> "REQUIRED"
            ConsentInformation.ConsentStatus.OBTAINED -> "OBTAINED"
            else -> "UNKNOWN"
        }
        return JSONObject().put("status", st).put("eea", consent.consentStatus != ConsentInformation.ConsentStatus.NOT_REQUIRED)
            .put("canRequestAds", consent.canRequestAds())
    }

    private fun configure(msg: JSONObject) {
        if (configured || configuring) return
        configuring = true
        testMode = msg.optBoolean("testMode", false)
        val showOnStart = msg.optBoolean("showOnStart", true)
        applySettings(msg)

        val params = ConsentRequestParameters.Builder()
        if (testMode) {
            val geo = when (msg.optString("debugLocation")) {
                "inside-eea" -> ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA
                "outside-eea" -> ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_NOT_EEA
                else -> ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_DISABLED
            }
            params.setConsentDebugSettings(ConsentDebugSettings.Builder(host.activity).setDebugGeography(geo).build())
            consent.reset()
        }
        consent.requestConsentInfoUpdate(host.activity, params.build(), {
            ui {
                if (showOnStart) {
                    UserMessagingPlatform.loadAndShowConsentFormIfRequired(host.activity) { err ->
                        ui {
                            if (err != null) emit("consentFailed", JSONObject().put("error", err.message))
                            startSdk()
                        }
                    }
                } else startSdk()
            }
        }, { err ->
            // Sem resposta do servidor de consentimento: segue (anúncios não personalizados dependem do Google)
            ui {
                emit("consentFailed", JSONObject().put("error", err.message))
                startSdk()
            }
        })
    }

    private fun startSdk() {
        if (configured) return
        try {
            MobileAds.initialize(host.activity)
        } catch (e: Exception) {
            configuring = false
            emit("configFailed", JSONObject().put("error", errorText(e)))
            return
        }
        configured = true
        configuring = false
        emit("consent", consentJson())
        emit("configured", consentJson())
        val list = ArrayList(queue)
        queue.clear()
        list.forEach { (c, m) -> run(c, m) }
    }

    private fun applySettings(msg: JSONObject) {
        val b = MobileAds.getRequestConfiguration().toBuilder()
        var changed = false
        when (msg.optString("maxRating")) {
            "G" -> { b.setMaxAdContentRating(RequestConfiguration.MAX_AD_CONTENT_RATING_G); changed = true }
            "PG" -> { b.setMaxAdContentRating(RequestConfiguration.MAX_AD_CONTENT_RATING_PG); changed = true }
            "T" -> { b.setMaxAdContentRating(RequestConfiguration.MAX_AD_CONTENT_RATING_T); changed = true }
            "MA" -> { b.setMaxAdContentRating(RequestConfiguration.MAX_AD_CONTENT_RATING_MA); changed = true }
        }
        if (msg.has("childDirected")) {
            @Suppress("DEPRECATION")
            b.setTagForChildDirectedTreatment(when (msg.optString("childDirected")) {
                "true" -> RequestConfiguration.TAG_FOR_CHILD_DIRECTED_TREATMENT_TRUE
                "false" -> RequestConfiguration.TAG_FOR_CHILD_DIRECTED_TREATMENT_FALSE
                else -> RequestConfiguration.TAG_FOR_CHILD_DIRECTED_TREATMENT_UNSPECIFIED
            })
            changed = true
        }
        if (msg.has("underAge")) {
            @Suppress("DEPRECATION")
            b.setTagForUnderAgeOfConsent(when (msg.optString("underAge")) {
                "true" -> RequestConfiguration.TAG_FOR_UNDER_AGE_OF_CONSENT_TRUE
                "false" -> RequestConfiguration.TAG_FOR_UNDER_AGE_OF_CONSENT_FALSE
                else -> RequestConfiguration.TAG_FOR_UNDER_AGE_OF_CONSENT_UNSPECIFIED
            })
            changed = true
        }
        if (changed) MobileAds.setRequestConfiguration(b.build())
    }

    // ── IDs de teste oficiais do Google (modo de teste) ──────────────────────

    /** ID do bloco (modo de teste: IDs de teste do Google). Vazio fora do teste = sem anúncio. */
    private fun unit(given: String, test: String): String? {
        val u = given.trim()
        return if (testMode) test else u.ifEmpty { null }
    }

    private fun noUnit(kind: String): Boolean {
        emit("${kind}Failed", JSONObject().put("error", "ID do bloco de anúncio vazio.").put("code", -1))
        return true
    }

    private fun request(): AdRequest = AdRequest.Builder().build()

    private fun err(e: AdError?): JSONObject = JSONObject().put("error", e?.message ?: "erro").put("code", e?.code ?: -1)

    // ── Banner ───────────────────────────────────────────────────────────────

    @Suppress("DEPRECATION")
    private fun bannerSize(kind: String): AdSize {
        val dm = host.activity.resources.displayMetrics
        val widthDp = (dm.widthPixels / dm.density).toInt()
        return when (kind) {
            "banner" -> AdSize.BANNER
            "large" -> AdSize.LARGE_BANNER
            "medium" -> AdSize.MEDIUM_RECTANGLE
            "full" -> AdSize.FULL_BANNER
            "leaderboard" -> AdSize.LEADERBOARD
            "landscape" -> AdSize.getLandscapeAnchoredAdaptiveBannerAdSize(ctx, widthDp)
            "portrait" -> AdSize.getPortraitAnchoredAdaptiveBannerAdSize(ctx, widthDp)
            else -> AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(ctx, widthDp)
        }
    }

    private fun createBanner(msg: JSONObject) {
        if (banner != null && !msg.optBoolean("reload", false)) {
            if (msg.optBoolean("show")) showBanner()
            return
        }
        removeBanner()
        val top = msg.optString("position") == "top"
        val offsetPx = (msg.optDouble("offset", 0.0) * host.activity.resources.displayMetrics.density).toFloat()
        bannerOverlap = msg.optBoolean("overlap", true)
        val show = msg.optBoolean("show", true)
        val size = bannerSize(msg.optString("size"))
        val v = AdView(host.activity)
        v.adUnitId = unit(msg.optString("unit"), "ca-app-pub-3940256099942544/9214589741") ?: run { v.destroy(); noUnit("banner"); return }
        v.setAdSize(size)
        v.visibility = View.GONE
        v.translationY = if (top) offsetPx else -offsetPx
        v.adListener = object : AdListener() {
            override fun onAdLoaded() = ui {
                emit("bannerLoaded")
                if (show) showBanner()
            }
            override fun onAdFailedToLoad(e: LoadAdError) = ui { emit("bannerFailed", err(e)) }
        }
        val lp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER_HORIZONTAL or (if (top) Gravity.TOP else Gravity.BOTTOM))
        host.root.addView(v, lp)
        banner = v
        v.loadAd(request())
    }

    private fun showBanner() {
        val v = banner ?: return
        v.visibility = View.VISIBLE
        bannerVisible = true
        if (!bannerOverlap) fitWeb(v)
        emit("bannerShown")
    }

    /** Sem sobrepor: o jogo encolhe para caber junto com o banner. */
    private fun fitWeb(v: AdView) {
        val h = v.adSize?.getHeightInPixels(host.activity) ?: return
        val lp = host.web.layoutParams as FrameLayout.LayoutParams
        val top = ((v.layoutParams as FrameLayout.LayoutParams).gravity and Gravity.TOP) == Gravity.TOP
        lp.topMargin = if (top) h else 0
        lp.bottomMargin = if (top) 0 else h
        host.web.layoutParams = lp
    }

    private fun resetWeb() {
        val lp = host.web.layoutParams as FrameLayout.LayoutParams
        if (lp.topMargin != 0 || lp.bottomMargin != 0) {
            lp.topMargin = 0
            lp.bottomMargin = 0
            host.web.layoutParams = lp
        }
    }

    private fun removeBanner() {
        val v = banner ?: return
        banner = null
        bannerVisible = false
        host.root.removeView(v)
        v.destroy()
        resetWeb()
    }

    private fun hideBanner() {
        if (banner == null) return
        removeBanner()
        emit("bannerHidden")
    }

    // ── Tela cheia ───────────────────────────────────────────────────────────

    private fun fullScreen(kind: String, onDone: (Boolean) -> Unit) = object : FullScreenContentCallback() {
        override fun onAdShowedFullScreenContent() = ui { emit("${kind}Shown") }
        override fun onAdDismissedFullScreenContent() = ui { onDone(true) }
        override fun onAdFailedToShowFullScreenContent(e: AdError) = ui {
            emit("${kind}Cancelled", err(e))
            onDone(false)
        }
    }

    private fun createInterstitial(unitId: String, show: Boolean) {
        val id = unit(unitId, "ca-app-pub-3940256099942544/1033173712") ?: run { noUnit("interstitial"); return }
        InterstitialAd.load(host.activity, id, request(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) = ui {
                    interstitial = ad
                    emit("interstitialLoaded")
                    if (show) showInterstitial()
                }
                override fun onAdFailedToLoad(e: LoadAdError) = ui {
                    interstitial = null
                    emit("interstitialFailed", err(e))
                }
            })
    }

    private fun showInterstitial() {
        val ad = interstitial ?: return
        interstitial = null
        ad.fullScreenContentCallback = fullScreen("interstitial") { ok -> if (ok) emit("interstitialComplete") }
        ad.show(host.activity)
    }

    private fun createRewarded(unitId: String, show: Boolean) {
        val id = unit(unitId, "ca-app-pub-3940256099942544/5224354917") ?: run { noUnit("rewarded"); return }
        RewardedAd.load(host.activity, id, request(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) = ui {
                    rewarded = ad
                    emit("rewardedLoaded")
                    if (show) showRewarded()
                }
                override fun onAdFailedToLoad(e: LoadAdError) = ui {
                    rewarded = null
                    emit("rewardedFailed", err(e))
                }
            })
    }

    private fun showRewarded() {
        val ad = rewarded ?: return
        rewarded = null
        var reward: JSONObject? = null
        ad.fullScreenContentCallback = fullScreen("rewarded") { ok ->
            if (!ok) return@fullScreen
            val r = reward
            if (r != null) emit("rewardedComplete", r) else emit("rewardedCancelled")
        }
        ad.show(host.activity) { item -> reward = JSONObject().put("type", item.type).put("amount", item.amount) }
    }

    private fun createRewardedInterstitial(unitId: String, show: Boolean) {
        val id = unit(unitId, "ca-app-pub-3940256099942544/5354046379") ?: run { noUnit("rewardedInterstitial"); return }
        RewardedInterstitialAd.load(host.activity, id, request(),
            object : RewardedInterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedInterstitialAd) = ui {
                    rewardedInterstitial = ad
                    emit("rewardedInterstitialLoaded")
                    if (show) showRewardedInterstitial()
                }
                override fun onAdFailedToLoad(e: LoadAdError) = ui {
                    rewardedInterstitial = null
                    emit("rewardedInterstitialFailed", err(e))
                }
            })
    }

    private fun showRewardedInterstitial() {
        val ad = rewardedInterstitial ?: return
        rewardedInterstitial = null
        var reward: JSONObject? = null
        ad.fullScreenContentCallback = fullScreen("rewardedInterstitial") { ok ->
            if (!ok) return@fullScreen
            val r = reward
            if (r != null) emit("rewardedInterstitialComplete", r) else emit("rewardedInterstitialCancelled")
        }
        ad.show(host.activity) { item -> reward = JSONObject().put("type", item.type).put("amount", item.amount) }
    }

    override fun onPause() { banner?.pause() }
    override fun onResume() { banner?.resume() }
    override fun onDestroy() {
        banner?.destroy()
        banner = null
    }
}
