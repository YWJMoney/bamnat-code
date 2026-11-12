package com.bamnat.core.money

/**
 * [SHOP-08] Notifications that are a shop's sales rather than its spending: an order from a delivery
 * app's 사장님 app, or an approval on a card terminal's app. On a shop calendar they go into that day's
 * sales (배달앱 or 카드) instead of the ledger. A bank deposit from a card company is the card sales
 * coming in ([settlement]), not new income. Only the apps listed here are read, by their name or
 * package, and only texts that clearly say an order or approval with an amount: anything unclear is
 * left to the usual payment reading.
 */
object ShopCapture {
    enum class Bucket(val label: String) { CARD("카드"), DELIVERY("배달앱") }

    /** A sale read from a notification: +amount, or −amount for a cancellation. */
    data class Sale(val bucket: Bucket, val amount: Long, val app: String)

    /** 사장님 apps of delivery services, by the name the phone shows. */
    private val DELIVERY_NAMES = listOf("배민사장님", "배달의민족 사장님", "배민 사장님", "쿠팡이츠 스토어", "쿠팡이츠스토어", "요기요 사장님", "요기요사장님", "땡겨요 사장님")
    /** Card terminal and POS apps, by the name the phone shows. */
    private val TERMINAL_NAMES = listOf("토스 플레이스", "토스플레이스", "토스포스", "토스 포스", "페이히어", "캐시노트")

    /** Pieces a package name must all have; app ids are not published, so these follow the makers' names. */
    private val DELIVERY_PACKAGES = listOf(listOf("woowahan", "ceo"), listOf("baemin", "ceo"), listOf("coupang", "eats", "store"), listOf("yogiyo", "owner"), listOf("yogiyo", "ceo"))
    private val TERMINAL_PACKAGES = listOf(listOf("toss", "place"), listOf("toss", "pos"), listOf("payhere"))

    private val AMOUNT = Regex("(\\d{1,3}(?:,\\d{3})+|\\d{3,9})\\s*원")
    private val ORDER = Regex("주문")
    private val ORDER_IN = Regex("접수|수락|완료|들어왔|도착|신규")
    private val APPROVED = Regex("승인|결제\\s*완료|결제됐|결제되었")
    private val CANCEL = Regex("취소|환불")

    /** Which kind of shop app [pkg] / [appName] is; null for any other app. */
    fun source(pkg: String, appName: String): Bucket? {
        val p = pkg.lowercase()
        val n = appName.replace(" ", "")
        return when {
            DELIVERY_NAMES.any { n.contains(it.replace(" ", "")) } || DELIVERY_PACKAGES.any { parts -> parts.all { p.contains(it) } } -> Bucket.DELIVERY
            TERMINAL_NAMES.any { n.contains(it.replace(" ", "")) } || TERMINAL_PACKAGES.any { parts -> parts.all { p.contains(it) } } -> Bucket.CARD
            else -> null
        }
    }

    /** True for a text from a listed app that should be read even though it does not look like a payment. */
    fun looksLikeSale(pkg: String, appName: String, text: String): Boolean = read(text, pkg, appName) != null

    /** The sale in [text] from [pkg] / [appName]; null when the app is not a shop app or the text is not clear. */
    fun read(text: String, pkg: String, appName: String): Sale? {
        val bucket = source(pkg, appName) ?: return null
        val amount = AMOUNT.find(text)?.groupValues?.get(1)?.replace(",", "")?.toLongOrNull()?.takeIf { it > 0 } ?: return null
        val cancel = CANCEL.containsMatchIn(text)
        val clear = when (bucket) {
            Bucket.DELIVERY -> ORDER.containsMatchIn(text) && (ORDER_IN.containsMatchIn(text) || cancel)
            Bucket.CARD -> APPROVED.containsMatchIn(text) || cancel
        }
        if (!clear) return null
        return Sale(bucket, if (cancel) -amount else amount, appName.ifBlank { pkg })
    }

    // ---- 2.0.0 [RVK-40] (검토 MONEY-52·T06b-03) 한 주문은 한 번 ----

    private val ORDER_NO = Regex("주문\\s*번호\\s*[:：]?\\s*([A-Za-z0-9][A-Za-z0-9-]{3,19})|#\\s?([A-Za-z0-9][A-Za-z0-9-]{2,19})")
    private val STAGE_NEW = Regex("접수|신규|새\\s*주문|들어왔|도착")
    private val STAGE_LATER = Regex("수락|완료|조리|픽업|배달\\s*(?:시작|중|출발)")

    /** A sale already counted from a delivery app: its 주문번호 when the text had one, its amount (− for a cancellation), when. */
    data class Counted(val no: String?, val won: Long, val at: Long) {
        fun encode(): String = listOf(no.orEmpty(), won.toString(), at.toString()).joinToString("\t")

        companion object {
            /** How many are kept; a day's orders at a busy shop fit. */
            const val MAX = 300

            fun decode(line: String): Counted? {
                val p = line.split('\t')
                if (p.size != 3) return null
                return Counted(p[0].ifBlank { null }, p[1].toLongOrNull() ?: return null, p[2].toLongOrNull() ?: return null)
            }
        }
    }

    /** The 주문번호 in [text] ("주문번호 B1A2C3", "#1234"), or null. */
    fun orderNo(text: String): String? = ORDER_NO.find(text)?.let { m -> (m.groups[1] ?: m.groups[2])?.value?.uppercase() }

    /** What to remember of [sale] read from [text] at [at] (millis). */
    fun counted(text: String, sale: Sale, at: Long) = Counted(orderNo(text), sale.amount, at)

    /**
     * Whether [sale] from [text] at [at] is an order already counted in [recent]. A delivery app tells one order several times
     * (접수 · 수락 · 조리 완료 · 배달 완료), each with the amount: the same 주문번호 within [ORDER_HOURS] hours is once; without a number,
     * a later step (수락·완료, no 접수·신규 in it) of an amount counted within [FOLLOW_UP_MINUTES] minutes is that order again. A new
     * order (접수·신규) without a number always counts, so two orders of the same price both go in. Card terminals tell each payment once.
     */
    fun isRepeat(text: String, sale: Sale, at: Long, recent: List<Counted>): Boolean {
        if (sale.bucket != Bucket.DELIVERY) return false
        fun within(c: Counted, minutes: Long) = at - c.at in 0..minutes * 60_000
        val no = orderNo(text)
        if (no != null) return recent.any { it.no == no && (it.won < 0) == (sale.amount < 0) && within(it, ORDER_HOURS * 60) }
        if (sale.amount > 0 && (STAGE_NEW.containsMatchIn(text) || !STAGE_LATER.containsMatchIn(text))) return false
        return recent.any { it.won == sale.amount && within(it, FOLLOW_UP_MINUTES) }
    }

    /** [list] with [c] at the end, no more than [Counted.MAX]. */
    fun remember(list: List<Counted>, c: Counted): List<Counted> = (list + c).takeLast(Counted.MAX)

    const val ORDER_HOURS = 6L
    const val FOLLOW_UP_MINUTES = 90L

    /**
     * [DAILY-13] 결정 D7-3: with 확인하고 적기 on, a sale read from a notice waits here until the user
     * says 적기 or 버리기, like a payment line in 확인 대기함. [key] is the notice it came from.
     */
    data class Pending(val key: String, val date: java.time.LocalDate, val sale: Sale) {
        fun encode(): String = listOf(key.replace("\t", " "), date.toString(), sale.bucket.name, sale.amount.toString(), sale.app.replace("\t", " ").replace("\n", " "))
            .joinToString("\t")

        companion object {
            /** At most this many wait; the oldest go first. */
            const val MAX = 60

            fun decode(line: String): Pending? {
                val p = line.split('\t')
                if (p.size != 5) return null
                val bucket = Bucket.entries.firstOrNull { it.name == p[2] } ?: return null
                val date = runCatching { java.time.LocalDate.parse(p[1]) }.getOrNull() ?: return null
                val amount = p[3].toLongOrNull()?.takeIf { it != 0L } ?: return null
                return Pending(p[0], date, Sale(bucket, amount, p[4]))
            }

            /** [list] with [p] at the end, the same notice only once, and no more than [MAX]. */
            fun add(list: List<Pending>, p: Pending): List<Pending> =
                if (list.any { it.key == p.key }) list else (list + p).takeLast(MAX)
        }
    }

    /** [sales] of a day with [sale] added to its bucket; a cancellation never takes a bucket below zero. */
    fun add(day: DailySales?, date: java.time.LocalDate, sale: Sale): DailySales {
        val base = day ?: DailySales(date, 0)
        val next = when (sale.bucket) {
            Bucket.CARD -> base.copy(card = (base.card + sale.amount).coerceAtLeast(0))
            Bucket.DELIVERY -> base.copy(delivery = (base.delivery + sale.amount).coerceAtLeast(0))
        }
        // The day's total moves by the sale itself: 외상 and a total written without a split stay in it,
        // and it never drops below what the payment methods add up to.
        val added = next.byMethod - base.byMethod
        return next.copy(sales = (base.sales + added).coerceAtLeast(next.byMethod))
    }

    private val CARD_COMPANY = Regex("카드")
    private val SETTLED = Regex("입금|정산")

    /**
     * [SHOP-08] The amount of a card company's deposit (e.g. "신한카드 입금 1,234,000원", "카드대금 정산"),
     * read by the payment parser as a deposit; null for any other text.
     */
    /** 1.39.1 [R391-09] The 이체 line of a card company's deposit that fits no day's card sales. */
    const val CARD_DEPOSIT = "카드 매출 입금"

    fun settlement(text: String, parsed: PaymentParser.Parsed?): Long? {
        if (parsed == null || parsed.type != PaymentParser.Type.DEPOSIT) return null
        val amount = parsed.amount?.takeIf { it > 0 } ?: return null
        val from = parsed.merchant + " " + text
        return if (CARD_COMPANY.containsMatchIn(from) && SETTLED.containsMatchIn(text)) amount else null
    }
}
