package com.bamnat.core.money

/**
 * 2.0.0 [RVK-02] (검토 MONEY-57) 말로 적은 금액 하나를 원으로 읽어요: "9천", "3만5천", "1.5만", "15천원", "2천만", "3백만",
 * "1억 2천만", "12,500", "1,500만". 한 줄로 적기와 메모 금액이 같은 읽기를 써요(예전에는 '2천만'이 2,000원, '15천원'이 5,000원).
 *
 * 읽는 법은 우리말 수 읽기 그대로예요: 천·백은 만 아래 자리를 채우고, 만·억이 나오면 그때까지 모인 값에 곱해요.
 */
object KoreanAmount {
    /** 금액으로 보이는 한 덩어리. [unit]은 억·만·천·백·원이나 천 단위 쉼표가 있었는지(맨 숫자 '2026'과 가르려고). */
    data class Found(val range: IntRange, val value: Long, val text: String, val unit: Boolean, val units: String)

    private const val NUM = "(?:\\d{1,3}(?:,\\d{3})+|\\d+(?:\\.\\d+)?)"
    private const val UNIT = "(?:억|천|백|만)"
    private val TOKEN = Regex("$NUM|억|천|백|만")
    private val FULL = Regex("$NUM\\s*$UNIT(?:\\s*(?:$NUM\\s*)?$UNIT)*(?:\\s*\\d+(?![\\d.,]))?\\s*원?|\\d{1,3}(?:,\\d{3})+\\s*원?|\\d+\\s*원?|(?<![가-힣\\d])(?:천|만)\\s*원")

    fun find(text: String): List<Found> = FULL.findAll(text).mapNotNull { m ->
        val raw = m.value
        val v = valueOf(raw) ?: return@mapNotNull null
        val units = buildString { raw.forEach { c -> if (c in "억천백만원") append(c) } }
        Found(m.range, v, raw, unit = units.isNotEmpty() || raw.contains(','), units = units)
    }.toList()

    /** "2천만" → 20,000,000. 읽을 수 없으면 null. */
    fun valueOf(raw: String): Long? {
        val s = raw.replace("원", "").trim()
        if (s.isEmpty()) return null
        if (!s.any { it in "억천백만" }) return s.replace(",", "").toLongOrNull()?.takeIf { it > 0 }
        var total = 0.0
        var section = 0.0
        var pending: Double? = null
        for (t in TOKEN.findAll(s)) {
            when (val w = t.value) {
                "천", "백" -> { section += (pending ?: 1.0) * (if (w == "천") 1_000 else 100); pending = null }
                // "2천만": 만·억 앞에 모인 천·백 자리 전체에 곱해요.
                "만", "억" -> {
                    val head = (section + (pending ?: 0.0)).takeIf { it > 0 } ?: 1.0
                    total += head * (if (w == "만") 10_000.0 else 100_000_000.0); section = 0.0; pending = null
                }
                else -> pending = w.replace(",", "").toDoubleOrNull() ?: return null
            }
        }
        total += section + (pending ?: 0.0)
        return Math.round(total).takeIf { it > 0 }
    }
}
