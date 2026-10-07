package com.w2a.runtime

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import org.json.JSONArray
import org.json.JSONObject

object IapFactory {
    fun create(host: ZeHost): List<ZeService> = listOf(IapService(host))
}

/**
 * Compras no app (Google Play Billing 8). Mesmos eventos do plugin "Mobile IAP"
 * da ZEngine: product, registered, purchase{state}, owned, expired, refunded,
 * restore{state}, initialized, error.
 *
 * Sem servidor de validação: a compra é reconhecida (acknowledge) ou consumida
 * na hora, para o Google não estornar depois de 3 dias.
 */
class IapService(private val host: ZeHost) : ZeService, PurchasesUpdatedListener {

    override val name = "iap"

    private val main = Handler(Looper.getMainLooper())
    private val ctx: Context = host.activity.applicationContext
    private val prefs = ctx.getSharedPreferences("ze_iap", Context.MODE_PRIVATE)

    /** id → tipo da ZEngine (consumable, non-consumable, paid-subscription…) */
    private val types = LinkedHashMap<String, String>()
    private val details = HashMap<String, ProductDetails>()
    private val handled = HashSet<String>()
    private var client: BillingClient? = null
    private var connecting = false
    private val waiting = ArrayList<() -> Unit>()
    private var buying: String? = null

    private fun isSubs(type: String?) = type == "paid-subscription" || type == "free-subscription"
    private fun isConsumable(id: String) = types[id] == "consumable"
    private fun ui(block: () -> Unit) { main.post(block) }

    private fun billing(): BillingClient = client ?: BillingClient.newBuilder(ctx)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build().also { client = it }

    /** Roda quando a conexão com a Play Store estiver pronta. */
    private fun ready(onFail: (String) -> Unit, block: () -> Unit) {
        val c = billing()
        if (c.isReady) { block(); return }
        waiting.add(block)
        if (connecting) return
        connecting = true
        c.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) = ui {
                connecting = false
                val list = ArrayList(waiting)
                waiting.clear()
                if (result.responseCode == BillingClient.BillingResponseCode.OK) list.forEach { it() }
                else onFail(storeError(result))
            }
            override fun onBillingServiceDisconnected() = ui { connecting = false }
        })
    }

    private fun storeError(r: BillingResult): String = when (r.responseCode) {
        BillingClient.BillingResponseCode.BILLING_UNAVAILABLE -> "A Play Store deste aparelho não aceita compras (conta do Google ou país sem suporte)."
        BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE,
        BillingClient.BillingResponseCode.NETWORK_ERROR -> "Sem conexão com a Play Store. Tente de novo."
        BillingClient.BillingResponseCode.ITEM_UNAVAILABLE -> "Produto não encontrado na Play Store."
        BillingClient.BillingResponseCode.DEVELOPER_ERROR -> "Configuração da loja inválida (o app precisa ter sido enviado à Play Store e o produto estar ativo)."
        else -> "Erro da Play Store (${r.responseCode}) ${r.debugMessage}".trim()
    }

    private fun fail(e: String) = host.emit(name, "error", JSONObject().put("error", e))

    // ── Comandos do jogo ─────────────────────────────────────────────────────

    override fun handle(cmd: String, msg: JSONObject) {
        when (cmd) {
            "init" -> {
                val arr = msg.optJSONArray("products") ?: JSONArray()
                for (i in 0 until minOf(arr.length(), 200)) {
                    val o = arr.optJSONObject(i) ?: continue
                    val id = o.optString("id").trim()
                    if (id.isNotEmpty() && id.length <= 150) types[id] = o.optString("type", "consumable")
                }
                ready(::fail) {
                    queryDetails {
                        host.emit(name, "initialized", JSONObject().put("count", details.size))
                        queryOwned(report = true, restoring = false)
                    }
                }
            }
            "refresh" -> ready(::fail) { queryDetails { queryOwned(report = true, restoring = false) } }
            "restore" -> ready({ e -> host.emit(name, "restore", JSONObject().put("state", "failed").put("error", e)) }) {
                queryOwned(report = true, restoring = true)
            }
            "purchase" -> purchase(msg.optString("id"), msg.optString("oldSku"), msg.optInt("prorationMode", -1))
            "manageSubscriptions", "manageBilling" -> {
                val sku = types.keys.firstOrNull { isSubs(types[it]) && owned().contains(it) }
                var url = "https://play.google.com/store/account/subscriptions?package=${Uri.encode(ctx.packageName)}"
                if (sku != null) url += "&sku=${Uri.encode(sku)}"
                open(url)
            }
            "redeemPromo" -> open("https://play.google.com/redeem")
        }
    }

    private fun open(url: String) {
        try {
            host.activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) { fail("Não consegui abrir a Play Store.") }
    }

    // ── Produtos ─────────────────────────────────────────────────────────────

    private fun queryDetails(done: () -> Unit) {
        val inapp = types.filter { !isSubs(it.value) }.keys.toList()
        val subs = types.filter { isSubs(it.value) }.keys.toList()
        val jobs = listOf(BillingClient.ProductType.INAPP to inapp, BillingClient.ProductType.SUBS to subs).filter { it.second.isNotEmpty() }
        if (jobs.isEmpty()) { done(); return }
        var left = jobs.size
        for ((kind, ids) in jobs) {
            val params = QueryProductDetailsParams.newBuilder().setProductList(ids.map {
                QueryProductDetailsParams.Product.newBuilder().setProductId(it).setProductType(kind).build()
            }).build()
            billing().queryProductDetailsAsync(params) { result, res ->
                ui {
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                        for (pd in res.productDetailsList) {
                            details[pd.productId] = pd
                            host.emit(name, "registered", JSONObject().put("ok", true).put("product", productJson(pd)))
                            host.emit(name, "product", JSONObject().put("product", productJson(pd)))
                        }
                        for (u in res.unfetchedProductList) {
                            host.emit(name, "registered", JSONObject().put("ok", false).put("id", u.productId)
                                .put("error", "Produto \"${u.productId}\" não encontrado na Play Store (confira o ID e se ele está ativo)."))
                        }
                    } else fail(storeError(result))
                    if (--left == 0) done()
                }
            }
        }
    }

    /** Oferta usada na compra de uma assinatura: o plano básico (sem oferta especial) ou a primeira. */
    private fun subOffer(pd: ProductDetails): ProductDetails.SubscriptionOfferDetails? {
        val offers = pd.subscriptionOfferDetails ?: return null
        return offers.firstOrNull { it.offerId == null } ?: offers.firstOrNull()
    }

    private fun productJson(pd: ProductDetails): JSONObject {
        val id = pd.productId
        val o = JSONObject()
            .put("id", id)
            .put("type", types[id] ?: if (pd.productType == BillingClient.ProductType.SUBS) "paid-subscription" else "non-consumable")
            .put("title", pd.name)
            .put("description", pd.description)
            .put("isLoaded", "true")
            .put("canPurchase", "true")
            .put("platform", "android")
            .put("state", if (owned().contains(id)) "owned" else "valid")
        if (pd.productType == BillingClient.ProductType.SUBS) {
            val offer = subOffer(pd)
            val phases = offer?.pricingPhases?.pricingPhaseList ?: emptyList()
            val regular = phases.lastOrNull()
            if (regular != null) {
                o.put("price", regular.formattedPrice)
                    .put("priceMicros", regular.priceAmountMicros)
                    .put("priceValue", regular.priceAmountMicros / 1_000_000.0)
                    .put("currency", regular.priceCurrencyCode)
                    .put("billingPeriod", regular.billingPeriod)
            }
            if (phases.size > 1) {
                val intro = phases.first()
                o.put("introPrice", intro.formattedPrice).put("introPriceMicros", intro.priceAmountMicros)
                    .put("introPricePeriod", intro.billingPeriod)
            }
            o.put("offerIds", (pd.subscriptionOfferDetails ?: emptyList()).mapNotNull { it.offerId }.joinToString(","))
        } else {
            val one = pd.oneTimePurchaseOfferDetails
            if (one != null) {
                o.put("price", one.formattedPrice)
                    .put("priceMicros", one.priceAmountMicros)
                    .put("priceValue", one.priceAmountMicros / 1_000_000.0)
                    .put("currency", one.priceCurrencyCode)
            }
        }
        return o
    }

    // ── Compras ──────────────────────────────────────────────────────────────

    private fun purchaseEvent(id: String, state: String, error: String? = null, p: Purchase? = null) {
        val prod = details[id]?.let { productJson(it) } ?: JSONObject().put("id", id)
        if (p != null) addPurchase(prod, p)
        val o = JSONObject().put("id", id).put("state", state).put("product", prod)
        if (error != null) o.put("error", error)
        host.emit(name, "purchase", o)
    }

    private fun addPurchase(o: JSONObject, p: Purchase) {
        o.put("purchaseToken", p.purchaseToken)
            .put("orderId", p.orderId ?: "")
            .put("transactionId", p.orderId ?: "")
            .put("purchaseDate", p.purchaseTime)
            .put("isAutoRenewing", if (p.isAutoRenewing) "true" else "false")
            .put("isAcknowledged", if (p.isAcknowledged) "true" else "false")
            .put("isPending", if (p.purchaseState == Purchase.PurchaseState.PENDING) "true" else "false")
            .put("quantity", p.quantity)
            .put("receipt", p.originalJson)
            .put("signature", p.signature)
    }

    private fun purchase(idRaw: String, oldSku: String, prorationMode: Int) {
        val id = idRaw.trim()
        if (id.isEmpty()) return
        ready({ e -> purchaseEvent(id, "failed", e) }) {
            val pd = details[id]
            if (pd == null) {
                purchaseEvent(id, "failed", "Produto \"$id\" não carregou da Play Store (registre antes de inicializar e confira o ID).")
                return@ready
            }
            val pp = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(pd)
            if (pd.productType == BillingClient.ProductType.SUBS) {
                val offer = subOffer(pd)
                if (offer == null) { purchaseEvent(id, "failed", "A assinatura \"$id\" não tem plano ativo na Play Store."); return@ready }
                pp.setOfferToken(offer.offerToken)
            }
            val launch = { oldToken: String? ->
                val flow = BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(pp.build()))
                if (oldToken != null) {
                    val mode = when (prorationMode) {
                        0 -> BillingFlowParams.SubscriptionUpdateParams.ReplacementMode.DEFERRED
                        1 -> BillingFlowParams.SubscriptionUpdateParams.ReplacementMode.CHARGE_PRORATED_PRICE
                        2 -> BillingFlowParams.SubscriptionUpdateParams.ReplacementMode.WITHOUT_PRORATION
                        else -> BillingFlowParams.SubscriptionUpdateParams.ReplacementMode.WITH_TIME_PRORATION
                    }
                    flow.setSubscriptionUpdateParams(
                        BillingFlowParams.SubscriptionUpdateParams.newBuilder()
                            .setOldPurchaseToken(oldToken)
                            .setSubscriptionReplacementMode(mode)
                            .build()
                    )
                }
                buying = id
                purchaseEvent(id, "initiated")
                val r = billing().launchBillingFlow(host.activity, flow.build())
                if (r.responseCode != BillingClient.BillingResponseCode.OK) {
                    buying = null
                    if (r.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) purchaseEvent(id, "cancelled")
                    else purchaseEvent(id, "failed", storeError(r))
                }
            }
            val old = oldSku.trim()
            if (old.isNotEmpty() && pd.productType == BillingClient.ProductType.SUBS) {
                val params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()
                billing().queryPurchasesAsync(params) { _, list ->
                    ui { launch(list.firstOrNull { it.products.contains(old) }?.purchaseToken) }
                }
            } else launch(null)
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) = ui {
        val id = buying
        buying = null
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> purchases?.forEach { process(it, fromFlow = true) }
            BillingClient.BillingResponseCode.USER_CANCELED -> if (id != null) purchaseEvent(id, "cancelled")
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                if (id != null) purchaseEvent(id, "failed", "ITEM_ALREADY_OWNED")
                ready(::fail) { queryOwned(report = true, restoring = false) }
            }
            else -> if (id != null) purchaseEvent(id, "failed", storeError(result))
        }
    }

    /** Compra paga: avisa o jogo e reconhece/consome. */
    private fun process(p: Purchase, fromFlow: Boolean) {
        for (id in p.products) {
            when (p.purchaseState) {
                Purchase.PurchaseState.PENDING -> purchaseEvent(id, "pending", null, p)
                Purchase.PurchaseState.PURCHASED -> {
                    if (isConsumable(id)) {
                        if (!handled.add(p.purchaseToken)) continue
                        val params = ConsumeParams.newBuilder().setPurchaseToken(p.purchaseToken).build()
                        billing().consumeAsync(params) { r, _ ->
                            ui {
                                if (r.responseCode == BillingClient.BillingResponseCode.OK) {
                                    purchaseEvent(id, "succeeded", null, p)
                                    details[id]?.let { host.emit(name, "product", JSONObject().put("product", productJson(it))) }
                                } else {
                                    handled.remove(p.purchaseToken)
                                    if (fromFlow) purchaseEvent(id, "failed", storeError(r), p)
                                }
                            }
                        }
                    } else {
                        if (fromFlow) purchaseEvent(id, "succeeded", null, p)
                        if (p.isAcknowledged) markOwned(id, p)
                        else {
                            val params = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(p.purchaseToken).build()
                            billing().acknowledgePurchase(params) { r ->
                                ui {
                                    if (r.responseCode == BillingClient.BillingResponseCode.OK) markOwned(id, p)
                                    else fail(storeError(r))
                                }
                            }
                        }
                    }
                }
                else -> {}
            }
        }
    }

    private fun owned(): Set<String> = prefs.getStringSet("owned", emptySet()) ?: emptySet()

    private fun markOwned(id: String, p: Purchase) {
        val set = HashSet(owned())
        if (set.add(id)) prefs.edit().putStringSet("owned", set).apply()
        val prod = details[id]?.let { productJson(it) } ?: JSONObject().put("id", id).put("type", types[id] ?: "non-consumable")
        addPurchase(prod, p)
        prod.put("state", "owned")
        host.emit(name, "owned", JSONObject().put("id", id).put("product", prod))
    }

    /**
     * Lê o que o usuário tem na Play Store (compras + assinaturas ativas).
     * O que tinha antes e sumiu: assinatura → "expired", compra → "refunded".
     */
    private fun queryOwned(report: Boolean, restoring: Boolean) {
        val all = ArrayList<Purchase>()
        var left = 2
        var error: String? = null
        for (kind in listOf(BillingClient.ProductType.INAPP, BillingClient.ProductType.SUBS)) {
            val params = QueryPurchasesParams.newBuilder().setProductType(kind).build()
            billing().queryPurchasesAsync(params) { r, list ->
                ui {
                    if (r.responseCode == BillingClient.BillingResponseCode.OK) all.addAll(list) else error = storeError(r)
                    if (--left == 0) {
                        if (error == null) {
                            val now = HashSet<String>()
                            for (p in all) if (p.purchaseState == Purchase.PurchaseState.PURCHASED) now.addAll(p.products.filter { !isConsumable(it) })
                            val before = owned()
                            for (id in before) if (id !in now) {
                                val ev = if (isSubs(types[id])) "expired" else "refunded"
                                host.emit(name, ev, JSONObject().put("id", id))
                            }
                            prefs.edit().putStringSet("owned", before.intersect(now)).apply()
                            if (report) all.forEach { process(it, fromFlow = false) }
                        } else if (!restoring) fail(error!!)
                        if (restoring) {
                            if (error == null) host.emit(name, "restore", JSONObject().put("state", "finished"))
                            else host.emit(name, "restore", JSONObject().put("state", "failed").put("error", error))
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        // Compras feitas fora do app (ou pendentes que foram pagas) aparecem ao voltar
        val c = client ?: return
        if (c.isReady && types.isNotEmpty()) queryOwned(report = true, restoring = false)
    }

    override fun onDestroy() {
        client?.endConnection()
        client = null
    }
}
