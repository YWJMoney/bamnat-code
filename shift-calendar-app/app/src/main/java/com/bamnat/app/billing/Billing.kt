package com.bamnat.app.billing

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.bamnat.app.appContainer
import com.bamnat.app.tier.ProStore
import com.bamnat.app.tier.SubRecord
import com.bamnat.app.tier.TierApi
import com.bamnat.core.tier.PlaySub
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import kotlin.coroutines.resume
import com.bamnat.core.time.Today

/** [MON-03] The one subscription and its plans as entered in Play Console (계획/1.19 3장). */
object ProductIds {
    const val PRO = "bamnat_pro"
    const val MONTHLY = "monthly"
    const val YEARLY = "yearly"
    const val TRIAL = "trial-1m"
    /** 1.36 841 1년 이용권: a one-time product, no automatic payment. */
    const val PASS_1Y = com.bamnat.core.tier.YearPass.PRODUCT
    /** Play's own page for the user's subscriptions, where they cancel [MON-05]. */
    fun manageUrl(packageName: String) = "https://play.google.com/store/account/subscriptions?sku=$PRO&package=$packageName"
}

/**
 * [MON-03] A way to pay for 프로 as Play offers it to this account now: the recurring [price] in the
 * account's currency, its [period] (P1M, P1Y) and whether the month's free trial is open to it.
 */
data class Plan(
    val basePlan: String,
    val price: String,
    val priceMicros: Long,
    val currency: String,
    val period: String,
    val trial: Boolean,
    val offerToken: String,
) {
    val yearly: Boolean get() = basePlan == ProductIds.YEARLY
    /** "월 4,990원" or "연 29,990원", as said beside the button and in the consent. */
    val priceWords: String get() = (if (yearly) "연 " else "월 ") + price
}

enum class BillingState { UNKNOWN, READY, UNAVAILABLE }

/** 1.36 841 1년 이용권 as Play offers it now: a one-time product, no automatic payment. */
data class PassOffer(val price: String, val priceMicros: Long, val currency: String)

private val NO_PASS: StateFlow<PassOffer?> = MutableStateFlow(null)

/** [MON-03] Play billing as the screens see it; screenshots and tests put a fake in its place. */
interface Billing {
    val plans: StateFlow<List<Plan>>
    val state: StateFlow<BillingState>
    /** Reads the plans and this account's purchases again; acknowledges and checks a new one with the server. */
    suspend fun refresh()
    /** Opens Play's own purchase sheet; false when it could not. */
    fun buy(activity: Activity, plan: Plan): Boolean
    /** 841: the 1년 이용권 when Play offers it. */
    val pass: StateFlow<PassOffer?> get() = NO_PASS
    /** 841: Play's purchase sheet for the 1년 이용권; false when it could not. */
    fun buyPass(activity: Activity): Boolean = false
}

/** No Play here (a test, a phone without the Play Store): no plans, nothing to buy. */
class NoBilling(plans: List<Plan> = emptyList()) : Billing {
    override val plans: StateFlow<List<Plan>> = MutableStateFlow(plans)
    override val state: StateFlow<BillingState> = MutableStateFlow(if (plans.isEmpty()) BillingState.UNAVAILABLE else BillingState.READY)
    override suspend fun refresh() = Unit
    override fun buy(activity: Activity, plan: Plan): Boolean = false
}

/**
 * [MON-03] Google Play Billing Library 9.1.0. Play on the phone says whether this account holds the
 * subscription; the company server, with the owner's Play key, adds its end date, trial and consent
 * (POST /api/purchases). A purchase is acknowledged here within Play's three days, or Play refunds it.
 */
class PlayBilling(
    context: Context,
    private val store: ProStore,
    private val api: () -> TierApi,
    private val device: () -> String,
    private val scope: CoroutineScope,
    private val log: (String) -> Unit = {},
    private val clock: () -> Instant = Today::instant,
    /** [STAT-04] A purchase made just now in the app: true for a free trial, false for a paid start. */
    private val onBought: (trial: Boolean) -> Unit = {},
) : Billing, PurchasesUpdatedListener {
    private val client: BillingClient = BillingClient.newBuilder(context.applicationContext)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()
    private val _plans = MutableStateFlow<List<Plan>>(emptyList())
    override val plans: StateFlow<List<Plan>> = _plans.asStateFlow()
    private val _state = MutableStateFlow(BillingState.UNKNOWN)
    override val state: StateFlow<BillingState> = _state.asStateFlow()
    private var details: ProductDetails? = null
    private var passDetails: ProductDetails? = null
    private val _pass = MutableStateFlow<PassOffer?>(null)
    override val pass: StateFlow<PassOffer?> = _pass.asStateFlow()
    private val lock = Mutex()
    /** The plan being bought: Play's purchase says neither the base plan nor whether it began with the trial. */
    @Volatile private var buying: Plan? = null

    override suspend fun refresh() = lock.withLock {
        if (!connect()) {
            _state.value = BillingState.UNAVAILABLE
            return@withLock
        }
        _state.value = BillingState.READY
        loadPlans()
        loadPass()
        val (ok, purchases) = queryPurchases()
        if (ok) take(purchases, fromQuery = true)
        val (passOk, passPurchases) = queryPurchases(BillingClient.ProductType.INAPP)
        if (passOk) takePasses(passPurchases, bought = false)
    }

    override fun buyPass(activity: Activity): Boolean {
        val d = passDetails ?: return false
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(d).build()))
            .build()
        val result = client.launchBillingFlow(activity, params)
        log("billing_pass_open ${result.responseCode}")
        return result.responseCode == BillingClient.BillingResponseCode.OK
    }

    private suspend fun loadPass() {
        val params = QueryProductDetailsParams.newBuilder().setProductList(listOf(
            QueryProductDetailsParams.Product.newBuilder().setProductId(ProductIds.PASS_1Y).setProductType(BillingClient.ProductType.INAPP).build(),
        )).build()
        val found = suspendCancellableCoroutine<ProductDetails?> { cont ->
            client.queryProductDetailsAsync(params) { result, list ->
                val d = list.productDetailsList.firstOrNull { it.productId == ProductIds.PASS_1Y }
                if (cont.isActive) cont.resume(d.takeIf { result.responseCode == BillingClient.BillingResponseCode.OK })
            }
        } ?: return
        passDetails = found
        _pass.value = found.oneTimePurchaseOfferDetails?.let { PassOffer(it.formattedPrice, it.priceAmountMicros, it.priceCurrencyCode) }
    }

    /**
     * 841: the 1년 이용권 Play reports. Each bought one is kept by its order (they stack); acknowledged at once (Play refunds after three
     * days otherwise), checked with the server when it can, and used up (consumed) only from 30 days before the end, so a new phone
     * with the same account finds it until then and it can be bought again after.
     */
    private suspend fun takePasses(purchases: List<Purchase>, bought: Boolean) {
        for (p in purchases.filter { ProductIds.PASS_1Y in it.products && it.purchaseState == Purchase.PurchaseState.PURCHASED }) {
            if (!p.isAcknowledged) acknowledge(p)
            val order = p.orderId ?: p.purchaseToken.takeLast(24)
            store.addYearPass(order, Instant.ofEpochMilli(p.purchaseTime))
            try {
                val checked = api().let { a -> if (a.enabled) a.purchase(device(), ProductIds.PASS_1Y, p.purchaseToken) else null }
                // Refunded on Play: the year goes.
                if (checked?.verified == true && checked.state == PlaySub.State.EXPIRED) { store.removeYearPass(order); continue }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log("billing_pass_check_failed")
            }
            if (com.bamnat.core.tier.YearPass.rebuy(store.current.yearPassUntil, clock())) consume(p)
        }
        if (bought) runCatching { onBought(false) }
    }

    private suspend fun consume(p: Purchase) {
        val params = com.android.billingclient.api.ConsumeParams.newBuilder().setPurchaseToken(p.purchaseToken).build()
        suspendCancellableCoroutine { cont ->
            client.consumeAsync(params) { result, _ ->
                log("billing_pass_consume ${result.responseCode}")
                if (cont.isActive) cont.resume(Unit)
            }
        }
    }

    override fun buy(activity: Activity, plan: Plan): Boolean {
        val d = details ?: return false
        buying = plan
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(
                BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(d).setOfferToken(plan.offerToken).build(),
            ))
            .build()
        val result = client.launchBillingFlow(activity, params)
        log("billing_open ${result.responseCode}")
        return result.responseCode == BillingClient.BillingResponseCode.OK
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        log("billing_result ${result.responseCode}")
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> scope.launch {
                lock.withLock {
                    val all = purchases.orEmpty()
                    if (all.any { ProductIds.PASS_1Y in it.products }) takePasses(all, bought = true) else take(all, fromQuery = false)
                }
            }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> scope.launch { refresh() }
            else -> Unit
        }
    }

    private suspend fun connect(): Boolean {
        if (client.isReady) return true
        return suspendCancellableCoroutine { cont ->
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (cont.isActive) cont.resume(result.responseCode == BillingClient.BillingResponseCode.OK)
                }

                override fun onBillingServiceDisconnected() {
                    if (cont.isActive) cont.resume(false)
                }
            })
        }
    }

    private suspend fun loadPlans() {
        val params = QueryProductDetailsParams.newBuilder().setProductList(listOf(
            QueryProductDetailsParams.Product.newBuilder().setProductId(ProductIds.PRO).setProductType(BillingClient.ProductType.SUBS).build(),
        )).build()
        val found = suspendCancellableCoroutine<ProductDetails?> { cont ->
            client.queryProductDetailsAsync(params) { result, list ->
                val d = list.productDetailsList.firstOrNull { it.productId == ProductIds.PRO }
                if (cont.isActive) cont.resume(d.takeIf { result.responseCode == BillingClient.BillingResponseCode.OK })
            }
        } ?: return
        details = found
        _plans.value = plansOf(found)
    }

    private suspend fun queryPurchases(type: String = BillingClient.ProductType.SUBS): Pair<Boolean, List<Purchase>> = suspendCancellableCoroutine { cont ->
        client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(type).build()) { result, list ->
            if (cont.isActive) cont.resume((result.responseCode == BillingClient.BillingResponseCode.OK) to list)
        }
    }

    /**
     * The subscription Play reports now. From a full query, none means none (ended, refunded); a
     * purchase still pending payment gives nothing yet.
     */
    private suspend fun take(purchases: List<Purchase>, fromQuery: Boolean) {
        val p = purchases.firstOrNull { ProductIds.PRO in it.products && it.purchaseState == Purchase.PurchaseState.PURCHASED }
        if (p == null) {
            if (fromQuery && purchases.none { ProductIds.PRO in it.products }) store.setSub(null)
            return
        }
        if (!p.isAcknowledged) acknowledge(p)
        val now = clock()
        val before = store.current.sub?.takeIf { it.purchasedAt.toEpochMilli() == p.purchaseTime }
        val bought = buying?.takeIf { !fromQuery }?.also { buying = null }
        var record = SubRecord(
            product = ProductIds.PRO,
            basePlan = bought?.basePlan ?: before?.basePlan,
            state = PlaySub.State.ACTIVE,
            expiry = maxOf(before?.expiry ?: now, now.plus(SubRecord.PHONE_HOLD)),
            trial = bought?.trial ?: before?.trial ?: false,
            autoRenew = p.isAutoRenewing,
            purchasedAt = Instant.ofEpochMilli(p.purchaseTime),
            verified = false,
            checkedAt = now,
            consentAt = before?.consentAt,
            stoppedAt = before?.stoppedAt,
        )
        val checked = try {
            api().let { a -> if (a.enabled) a.purchase(device(), ProductIds.PRO, p.purchaseToken) else null }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        if (checked?.verified == true) {
            record = record.copy(
                state = checked.state ?: record.state, expiry = checked.expiry ?: record.expiry, trial = checked.trial,
                autoRenew = checked.autoRenew, verified = true, consentAt = checked.consentAt ?: record.consentAt, stoppedAt = checked.stoppedAt,
            )
        }
        store.setSub(record)
        if (bought != null) runCatching { onBought(record.trial) }
    }

    private suspend fun acknowledge(p: Purchase) {
        val params = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(p.purchaseToken).build()
        suspendCancellableCoroutine { cont ->
            client.acknowledgePurchase(params) { result ->
                log("billing_ack ${result.responseCode}")
                if (cont.isActive) cont.resume(Unit)
            }
        }
    }

    companion object {
        /**
         * One plan per base plan: its recurring price, and the free trial when Play offers it to this
         * account (Play leaves out offers the account is not eligible for).
         */
        fun plansOf(d: ProductDetails): List<Plan> {
            val offers = d.subscriptionOfferDetails.orEmpty()
            return offers.groupBy { it.basePlanId }.mapNotNull { (base, list) ->
                val baseOffer = list.firstOrNull { it.offerId == null } ?: return@mapNotNull null
                val recurring = baseOffer.pricingPhases.pricingPhaseList.lastOrNull() ?: return@mapNotNull null
                // 1.36 840 (수익 3판: 체험은 연간에만): a free month on the monthly plan is not offered even if Play has one.
                val trialOffer = list.firstOrNull { o -> base == ProductIds.YEARLY && o.offerId != null && o.pricingPhases.pricingPhaseList.any { it.priceAmountMicros == 0L } }
                Plan(
                    basePlan = base, price = recurring.formattedPrice, priceMicros = recurring.priceAmountMicros,
                    currency = recurring.priceCurrencyCode, period = recurring.billingPeriod,
                    trial = trialOffer != null, offerToken = (trialOffer ?: baseOffer).offerToken,
                )
            }.sortedBy { if (it.yearly) 1 else 0 }
        }
    }
}

/** The plans Play offers now, for a screen. */
@Composable
fun rememberPlans(): List<Plan> {
    val billing = LocalContext.current.appContainer.billing
    val plans by billing.plans.collectAsStateWithLifecycle()
    return plans
}
