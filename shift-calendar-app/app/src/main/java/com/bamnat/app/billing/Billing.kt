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
    fun manageUrl(packageName: String, sku: String = PRO) = "https://play.google.com/store/account/subscriptions?sku=$sku&package=$packageName"
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
    /** 1.38 Phase 3 요금: the 팀·직원 요금제 offers Play has now (empty before the products are on Play). */
    val work: StateFlow<List<WorkOffer>> get() = NO_WORK
    /**
     * 1.38 Phase 3 요금: Play's sheet for [offer]. A band of the same kind already subscribed is changed in place with Play's own proration
     * (a bigger band charges the difference now, a smaller one takes effect with the time left), so nobody pays twice.
     */
    fun buyWork(activity: Activity, offer: WorkOffer): Boolean = false
}

/**
 * 1.38 Phase 3 요금: one way to pay for a band of a 팀·직원 요금제 as Play offers it now. [yearly] carries the month's free trial when
 * Play has it for this account; [pass] is the band's 1년 이용권 (one payment, no automatic payment).
 */
data class WorkOffer(
    val kind: com.bamnat.core.tier.WorkPlanKind,
    val cap: Int,
    val way: Way,
    val price: String,
    val priceMicros: Long,
    val trial: Boolean = false,
    val offerToken: String = "",
) {
    enum class Way { MONTHLY, YEARLY, PASS }
}

private val NO_WORK: StateFlow<List<WorkOffer>> = MutableStateFlow(emptyList())

/** No Play here (a test, a phone without the Play Store): no plans, nothing to buy. */
class NoBilling(plans: List<Plan> = emptyList()) : Billing {
    override val plans: StateFlow<List<Plan>> = MutableStateFlow(plans)
    override val state: StateFlow<BillingState> = MutableStateFlow(if (plans.isEmpty()) BillingState.UNAVAILABLE else BillingState.READY)
    override suspend fun refresh() = Unit
    override fun buy(activity: Activity, plan: Plan): Boolean = false
}

/**
 * 1.36 첫 실행 멈춤 (PC 확인 C12: AppContainer.playBilling 4.8초가 화면 스레드에서): a screen that only shows the plans
 * ([rememberPlans], 프로 잠금 카드) reads [plans] and [state] here without building Play's client. The real billing is
 * made by [make] on [scope]'s thread the first time something asks Play ([refresh], or [buy] before that), never on the
 * main thread, and a screen asking while it is being made waits for it on that thread. If it cannot be made (no Play),
 * it is [BillingState.UNAVAILABLE] like [NoBilling].
 */
class DeferredBilling(private val scope: CoroutineScope, private val make: () -> Billing) : Billing {
    private val _work = MutableStateFlow<List<WorkOffer>>(emptyList())
    override val work: StateFlow<List<WorkOffer>> = _work.asStateFlow()
    // 1.38 검토: the 1년 이용권 was never passed on from the real billing, so the 프로 화면 never showed it (841, GROW-04).
    private val _pass = MutableStateFlow<PassOffer?>(null)
    override val pass: StateFlow<PassOffer?> = _pass.asStateFlow()
    private val _plans = MutableStateFlow<List<Plan>>(emptyList())
    override val plans: StateFlow<List<Plan>> = _plans.asStateFlow()
    private val _state = MutableStateFlow(BillingState.UNKNOWN)
    override val state: StateFlow<BillingState> = _state.asStateFlow()
    private val made = kotlinx.coroutines.CompletableDeferred<Billing>()
    private val started = java.util.concurrent.atomic.AtomicBoolean(false)

    /** True once the real billing has been made (for the startup budget test). */
    val built: Boolean get() = made.isCompleted

    private fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            val real = runCatching(make).getOrElse { NoBilling() }
            launch { real.plans.collect { _plans.value = it } }
            launch { real.state.collect { _state.value = it } }
            launch { real.pass.collect { _pass.value = it } }
            launch { real.work.collect { _work.value = it } }
            made.complete(real)
        }
    }

    override suspend fun refresh() {
        start()
        made.await().refresh()
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override fun buy(activity: Activity, plan: Plan): Boolean {
        start()
        // Plans to pick come only from a refresh, so the real billing is there by then.
        return if (made.isCompleted) made.getCompleted().buy(activity, plan) else false
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override fun buyPass(activity: Activity): Boolean {
        start()
        return if (made.isCompleted) made.getCompleted().buyPass(activity) else false
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override fun buyWork(activity: Activity, offer: WorkOffer): Boolean {
        start()
        return if (made.isCompleted) made.getCompleted().buyWork(activity, offer) else false
    }
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
    /** 1.38 Phase 3 요금: the bands the server set, for the 1년 이용권 products to ask Play about. */
    private val workPlans: () -> com.bamnat.core.tier.WorkPlanTable = { com.bamnat.core.tier.WorkPlanTable.DEFAULT },
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
    private val _work = MutableStateFlow<List<WorkOffer>>(emptyList())
    override val work: StateFlow<List<WorkOffer>> = _work.asStateFlow()
    /** 1.38 Phase 3 요금: Play's details of the two subscriptions and the bands' 1년 이용권, by product. */
    @Volatile private var workDetails: Map<String, ProductDetails> = emptyMap()
    /** The token of each 팀·직원 요금제 this account holds, so a band change replaces it instead of adding a second one. */
    @Volatile private var workTokens: Map<String, String> = emptyMap()
    @Volatile private var buyingWork: WorkOffer? = null
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
        loadWork()
        val (ok, purchases) = queryPurchases()
        if (ok) {
            take(purchases, fromQuery = true)
            takeWork(purchases, fromQuery = true)
        }
        val (passOk, passPurchases) = queryPurchases(BillingClient.ProductType.INAPP)
        if (passOk) {
            takePasses(passPurchases, bought = false)
            takeWorkPasses(passPurchases)
        }
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
                    val work = all.filter { p -> p.products.any { com.bamnat.core.tier.WorkPlanKind.ofProduct(it) != null } }
                    val rest = all - work.toSet()
                    if (work.isNotEmpty()) { takeWork(work, fromQuery = false); takeWorkPasses(work, bought = true) }
                    if (rest.any { ProductIds.PASS_1Y in it.products }) takePasses(rest, bought = true) else if (rest.isNotEmpty()) take(rest, fromQuery = false)
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

    /** 1.38 Phase 3 요금: the two subscriptions (월·연 base plan per band) and each band's 1년 이용권, as Play has them now. */
    private suspend fun loadWork() {
        val table = workPlans()
        val subs = com.bamnat.core.tier.WorkPlanKind.entries.map { it.product to BillingClient.ProductType.SUBS }
        val passes = com.bamnat.core.tier.WorkPlanKind.entries.flatMap { k -> table.bands(k).map { k.passProduct(it.cap) to BillingClient.ProductType.INAPP } }
        val found = HashMap<String, ProductDetails>()
        for ((type, list) in (subs + passes).groupBy({ it.second }, { it.first })) {
            val params = QueryProductDetailsParams.newBuilder().setProductList(list.map {
                QueryProductDetailsParams.Product.newBuilder().setProductId(it).setProductType(type).build()
            }).build()
            val got = suspendCancellableCoroutine<List<ProductDetails>> { cont ->
                client.queryProductDetailsAsync(params) { result, l ->
                    if (cont.isActive) cont.resume(if (result.responseCode == BillingClient.BillingResponseCode.OK) l.productDetailsList else emptyList())
                }
            }
            got.forEach { found[it.productId] = it }
        }
        workDetails = found
        _work.value = workOffersOf(found.values, table)
    }

    override fun buyWork(activity: Activity, offer: WorkOffer): Boolean {
        val productId = if (offer.way == WorkOffer.Way.PASS) offer.kind.passProduct(offer.cap) else offer.kind.product
        val d = workDetails[productId] ?: return false
        buyingWork = offer
        val item = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(d)
        if (offer.way != WorkOffer.Way.PASS) item.setOfferToken(offer.offerToken)
        val builder = BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(item.build()))
        // A band of the same kind is held: Play changes it in place and works out the money (계획 8장 "Play의 비례 계산").
        val old = workTokens[offer.kind.product]
        val held = store.current.work[offer.kind.product]
        if (offer.way != WorkOffer.Way.PASS && old != null) {
            val oldCap = workPlans().capOf(offer.kind, held?.basePlan) ?: 0
            val mode = if (offer.cap > oldCap) BillingFlowParams.SubscriptionUpdateParams.ReplacementMode.CHARGE_PRORATED_PRICE
                else BillingFlowParams.SubscriptionUpdateParams.ReplacementMode.WITH_TIME_PRORATION
            builder.setSubscriptionUpdateParams(BillingFlowParams.SubscriptionUpdateParams.newBuilder()
                .setOldPurchaseToken(old).setSubscriptionReplacementMode(mode).build())
        }
        val result = client.launchBillingFlow(activity, builder.build())
        log("billing_work_open ${result.responseCode}")
        return result.responseCode == BillingClient.BillingResponseCode.OK
    }

    /** 1.38 Phase 3 요금: each 팀·직원 요금제 subscription Play reports, kept by product (one per kind; a band change replaces it). */
    private suspend fun takeWork(purchases: List<Purchase>, fromQuery: Boolean) {
        val tokens = HashMap(workTokens)
        for (kind in com.bamnat.core.tier.WorkPlanKind.entries) {
            val p = purchases.firstOrNull { kind.product in it.products && it.purchaseState == Purchase.PurchaseState.PURCHASED }
            if (p == null) {
                if (fromQuery && purchases.none { kind.product in it.products }) { store.setWork(kind.product, null); tokens.remove(kind.product) }
                continue
            }
            tokens[kind.product] = p.purchaseToken
            if (!p.isAcknowledged) acknowledge(p)
            val now = clock()
            val before = store.current.work[kind.product]?.takeIf { it.purchasedAt.toEpochMilli() == p.purchaseTime }
            val bought = buyingWork?.takeIf { !fromQuery && it.kind == kind && it.way != WorkOffer.Way.PASS }?.also { buyingWork = null }
            val boughtPlan = bought?.let { if (it.way == WorkOffer.Way.YEARLY) "c${it.cap}-yearly" else "c${it.cap}-monthly" }
            var record = SubRecord(
                product = kind.product, basePlan = boughtPlan ?: before?.basePlan, state = PlaySub.State.ACTIVE,
                expiry = maxOf(before?.expiry ?: now, now.plus(SubRecord.PHONE_HOLD)), trial = bought?.trial ?: before?.trial ?: false,
                autoRenew = p.isAutoRenewing, purchasedAt = Instant.ofEpochMilli(p.purchaseTime), verified = false, checkedAt = now,
                consentAt = before?.consentAt, stoppedAt = before?.stoppedAt,
            )
            val checked = try {
                api().let { a -> if (a.enabled) a.purchase(device(), kind.product, p.purchaseToken) else null }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            if (checked?.verified == true) {
                record = record.copy(state = checked.state ?: record.state, expiry = checked.expiry ?: record.expiry, trial = checked.trial,
                    autoRenew = checked.autoRenew, verified = true, consentAt = checked.consentAt ?: record.consentAt, stoppedAt = checked.stoppedAt,
                    basePlan = checked.basePlan ?: record.basePlan)
            }
            store.setWork(kind.product, record)
            if (bought != null) runCatching { onBought(record.trial) }
        }
        workTokens = tokens
    }

    /** 1.38 Phase 3 요금: a band's 1년 이용권, kept by order like 841 and used up from 30 days before its end so it can be bought again. */
    private suspend fun takeWorkPasses(purchases: List<Purchase>, bought: Boolean = false) {
        for (p in purchases.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }) {
            val product = p.products.firstOrNull { id -> com.bamnat.core.tier.WorkPlanKind.ofProduct(id) != null && id != com.bamnat.core.tier.WorkPlanKind.ofProduct(id)?.product } ?: continue
            if (!p.isAcknowledged) acknowledge(p)
            val order = p.orderId ?: p.purchaseToken.takeLast(24)
            store.addWorkPass(order, product, Instant.ofEpochMilli(p.purchaseTime))
            try {
                val checked = api().let { a -> if (a.enabled) a.purchase(device(), product, p.purchaseToken) else null }
                if (checked?.verified == true && checked.state == PlaySub.State.EXPIRED) { store.removeWorkPass(order); continue }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log("billing_work_pass_check_failed")
            }
            val mine = store.current.workPasses.values.filter { it.first == product }.map { it.second }
            if (com.bamnat.core.tier.YearPass.rebuy(com.bamnat.core.tier.WorkPlanRules.passUntil(mine), clock())) consume(p)
        }
        if (bought) runCatching { onBought(false) }
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
         * 1.38 Phase 3 요금: the offers of the two subscriptions and the bands' passes. A base plan that is not one of the table's bands
         * (`c<cap>-monthly`, `c<cap>-yearly`) is left out; the free month only ever rides the yearly one (계획 8장).
         */
        fun workOffersOf(details: Collection<ProductDetails>, table: com.bamnat.core.tier.WorkPlanTable): List<WorkOffer> = details.flatMap { d ->
            val kind = com.bamnat.core.tier.WorkPlanKind.ofProduct(d.productId) ?: return@flatMap emptyList()
            if (d.productId != kind.product) {
                val cap = table.capOf(kind, d.productId) ?: return@flatMap emptyList()
                val o = d.oneTimePurchaseOfferDetails ?: return@flatMap emptyList()
                return@flatMap listOf(WorkOffer(kind, cap, WorkOffer.Way.PASS, o.formattedPrice, o.priceAmountMicros))
            }
            d.subscriptionOfferDetails.orEmpty().groupBy { it.basePlanId }.mapNotNull { (base, list) ->
                val cap = table.capOf(kind, base) ?: return@mapNotNull null
                val yearly = base.endsWith("-yearly")
                val baseOffer = list.firstOrNull { it.offerId == null } ?: return@mapNotNull null
                val recurring = baseOffer.pricingPhases.pricingPhaseList.lastOrNull() ?: return@mapNotNull null
                val trialOffer = list.firstOrNull { o -> yearly && o.offerId != null && o.pricingPhases.pricingPhaseList.any { it.priceAmountMicros == 0L } }
                WorkOffer(kind, cap, if (yearly) WorkOffer.Way.YEARLY else WorkOffer.Way.MONTHLY, recurring.formattedPrice, recurring.priceAmountMicros,
                    trial = trialOffer != null, offerToken = (trialOffer ?: baseOffer).offerToken)
            }
        }.sortedWith(compareBy({ it.kind }, { it.cap }, { it.way }))

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
