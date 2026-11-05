package com.bamnat.core.money

import java.time.LocalDate

/**
 * 1.37 [LEDH-28] 한 줄로 적기: "점심 9천 카드", "어제 택시 12,500", "+ 용돈 5만" read into the 돈 적기 sheet's fields. Only fills what it
 * is sure of; the sheet shows the rest as before. Words it does not use become the place.
 */
object QuickLine {
    data class Draft(
        val amount: Long?,
        val kind: MoneyKind,
        /** Days before today ("어제" 1, "그제" 2). */
        val daysAgo: Int,
        /** One of the categories given, when a word names it. */
        val category: String?,
        /** The account given whose name a word names, else null. */
        val accountId: Long?,
        /** "현금", "체크", "신용" said without a name: the sheet picks the kind of account. */
        val accountType: AccountType?,
        /** "카드" said without a name: a check or credit card, whichever is used most. */
        val anyCard: Boolean = false,
        val place: String,
    )

    private val AMOUNT = Regex("(\\d+(?:\\.\\d+)?)\\s*만\\s*(?:(\\d)\\s*천)?(?:\\s*(\\d{1,3})\\s*백)?\\s*원?|(\\d+)\\s*천(?:\\s*(\\d)\\s*백)?\\s*원?|(\\d{1,3}(?:,\\d{3})+|\\d+)\\s*원?")
    private val INCOME = setOf("수입", "입금", "받음", "받았어", "용돈", "월급", "팁", "환급")
    private val DAYS = mapOf("오늘" to 0, "어제" to 1, "그제" to 2, "그저께" to 2)
    private val TYPES = mapOf("신용" to AccountType.CREDIT, "신용카드" to AccountType.CREDIT,
        "체크" to AccountType.CHECK, "체크카드" to AccountType.CHECK, "현금" to AccountType.CASH)

    fun parse(text: String, categories: List<String>, accounts: List<MoneyAccount> = emptyList()): Draft? {
        var rest = text.trim()
        if (rest.isEmpty()) return null
        var kind = MoneyKind.EXPENSE
        if (rest.startsWith("+") || rest.startsWith("＋")) { kind = MoneyKind.INCOME; rest = rest.drop(1).trim() }
        else if (rest.startsWith("-") || rest.startsWith("－")) rest = rest.drop(1).trim()
        // The amount: a unit (만·천·원) or a comma marks it; else the longest bare number of three digits or more.
        val all = AMOUNT.findAll(rest).toList()
        val m = all.firstOrNull { Regex("[원만천,]").containsMatchIn(it.value) } ?: all.filter { it.value.trim().length >= 3 }.maxByOrNull { it.value.length }
        val amount = m?.let { amountOf(it) }?.takeIf { it > 0 }
        if (m != null && amount != null) rest = (rest.substring(0, m.range.first) + " " + rest.substring(m.range.last + 1)).trim()
        val words = rest.split(Regex("\\s+")).filter { it.isNotEmpty() }.toMutableList()
        var daysAgo = 0
        var category: String? = null
        var accountId: Long? = null
        var type: AccountType? = null
        var anyCard = false
        val left = mutableListOf<String>()
        for (w in words) {
            val bare = when { w.endsWith("으로") -> w.dropLast(2); w.endsWith("로") && w.length > 1 -> w.dropLast(1); else -> w }
            when {
                DAYS.containsKey(w) -> daysAgo = DAYS.getValue(w)
                // 25f2364b PC 확인 ('+ 용돈 3만5천'이 급여로 저장됨): an income word that is a category is that category too.
                w in INCOME -> { kind = MoneyKind.INCOME; if (category == null && categories.contains(w)) category = w; left += w }
                accountId == null && accounts.any { a -> !a.hidden && a.name.isNotBlank() && (a.name == w || a.name == bare) } ->
                    accountId = accounts.first { a -> !a.hidden && (a.name == w || a.name == bare) }.id
                type == null && TYPES.containsKey(bare) -> type = TYPES.getValue(bare)
                bare == "카드" -> anyCard = true
                category == null && categories.contains(w) -> { category = w; left += w }
                else -> left += w
            }
        }
        if (amount == null && left.isEmpty()) return null
        return Draft(amount, kind, daysAgo, category, accountId, type.takeIf { accountId == null },
            anyCard = anyCard && accountId == null && type == null, place = left.joinToString(" ").take(40))
    }

    private fun amountOf(m: MatchResult): Long? {
        val g = m.groupValues
        return when {
            g[1].isNotEmpty() -> Math.round(g[1].toDouble() * 10_000) + (g[2].toLongOrNull() ?: 0) * 1000 + (g[3].toLongOrNull() ?: 0) * 100
            g[4].isNotEmpty() -> g[4].toLong() * 1000 + (g[5].toLongOrNull() ?: 0) * 100
            else -> g[6].replace(",", "").toLongOrNull()
        }
    }

    /** The account a [Draft] means: the one named, else the most used open one of the kind it said. */
    fun account(d: Draft, accounts: List<MoneyAccount>, entries: List<MoneyEntry>): Long? {
        d.accountId?.let { return it }
        val types = d.accountType?.let { setOf(it) } ?: if (d.anyCard) setOf(AccountType.CREDIT, AccountType.CHECK) else return null
        return accounts.filter { !it.hidden && it.type in types }.maxByOrNull { a -> entries.count { it.accountId == a.id } }?.id
    }
}
