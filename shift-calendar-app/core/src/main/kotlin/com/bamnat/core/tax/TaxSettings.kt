package com.bamnat.core.tax

/**
 * [TAX-06] The tax answers as key and text (DB 21 `tax_settings`), so a key this version does not know is kept aside
 * and a backup carries them as they are. [TAX-01] also keeps the senders confirmed as the employer, and the deposits
 * the user said were not pay (so they are never taken again).
 */
data class TaxSettings(
    val business: BusinessTax.Profile = BusinessTax.Profile(),
    val payers: Set<String> = emptySet(),
    val notPay: Set<Long> = emptySet(),
    /** F 1.32 [TAXP-03] The 연말정산 amounts the user typed for [yearEndYear] (0 = take the ledger's). */
    val yearEnd: com.bamnat.core.pay.YearEndPreview.Extras = com.bamnat.core.pay.YearEndPreview.Extras(),
    val yearEndYear: Int = 0,
    /** F 1.32 [TAXP-03] The refund last told by the weekly 다시 계산, and when, so the next one says what changed. */
    val yearEndLastRefund: Long? = null,
    val yearEndLastAt: java.time.LocalDate? = null,
    /** F 1.32 [TAXP-03] 매주 다시 계산 알림 (프로 구독, Oct~Jan). */
    val yearEndWeekly: Boolean = false,
) {
    /** The typed amounts for [year], none when they were for another year. */
    fun yearEndFor(year: Int): com.bamnat.core.pay.YearEndPreview.Extras =
        if (yearEndYear == year) yearEnd else com.bamnat.core.pay.YearEndPreview.Extras()

    fun encode(): Map<String, String> = mapOf(
        "biz.vat" to business.vat.name,
        "biz.industry" to business.industry.name,
        "biz.simple" to (business.simpleRate?.toString() ?: ""),
        "biz.standard" to (business.standardRate?.toString() ?: ""),
        "biz.lastIncome" to (business.lastYearIncome?.toString() ?: ""),
        "biz.lastTax" to (business.lastYearTax?.toString() ?: ""),
        "biz.family" to business.family.toString(),
        "biz.sincere" to business.sincere.toString(),
        "biz.new" to business.newBusiness.toString(),
        "pay.payers" to payers.sorted().joinToString("\n") { it.replace('\n', ' ') },
        "pay.notPay" to notPay.sorted().joinToString(","),
        "ye.year" to yearEndYear.toString(),
        "ye.credit" to yearEnd.credit.toString(),
        "ye.debit" to yearEnd.debit.toString(),
        "ye.market" to yearEnd.market.toString(),
        "ye.insurance" to yearEnd.insurance.toString(),
        "ye.medical" to yearEnd.medical.toString(),
        "ye.education" to yearEnd.education.toString(),
        "ye.donation" to yearEnd.donation.toString(),
        "ye.rent" to yearEnd.rent.toString(),
        "ye.pension" to yearEnd.pensionSavings.toString(),
        "ye.irp" to yearEnd.irp.toString(),
        "ye.lastRefund" to (yearEndLastRefund?.toString() ?: ""),
        "ye.lastAt" to (yearEndLastAt?.toString() ?: ""),
        "ye.weekly" to yearEndWeekly.toString(),
    )

    companion object {
        /** A value this version cannot read falls back to the default, never an error. */
        fun decode(m: Map<String, String>): TaxSettings {
            val d = BusinessTax.Profile()
            fun rate(k: String) = m[k]?.toDoubleOrNull()?.takeIf { it in 0.0..100.0 }
            fun won(k: String) = m[k]?.toLongOrNull()?.takeIf { it >= 0 }
            return TaxSettings(
                business = BusinessTax.Profile(
                    vat = BusinessTax.VatType.entries.firstOrNull { it.name == m["biz.vat"] } ?: d.vat,
                    industry = BusinessTax.Industry.entries.firstOrNull { it.name == m["biz.industry"] } ?: d.industry,
                    simpleRate = rate("biz.simple"),
                    standardRate = rate("biz.standard"),
                    lastYearIncome = won("biz.lastIncome"),
                    lastYearTax = won("biz.lastTax"),
                    family = m["biz.family"]?.toIntOrNull()?.coerceIn(1, 20) ?: d.family,
                    sincere = m["biz.sincere"] == "true",
                    newBusiness = m["biz.new"] == "true",
                ),
                payers = m["pay.payers"].orEmpty().split('\n').map { it.trim() }.filter { it.isNotEmpty() }.toSet(),
                notPay = m["pay.notPay"].orEmpty().split(',').mapNotNull { it.trim().toLongOrNull() }.toSet(),
                yearEnd = com.bamnat.core.pay.YearEndPreview.Extras(
                    credit = won("ye.credit") ?: 0, debit = won("ye.debit") ?: 0, market = won("ye.market") ?: 0,
                    insurance = won("ye.insurance") ?: 0, medical = won("ye.medical") ?: 0, education = won("ye.education") ?: 0,
                    donation = won("ye.donation") ?: 0, rent = won("ye.rent") ?: 0, pensionSavings = won("ye.pension") ?: 0, irp = won("ye.irp") ?: 0,
                ),
                yearEndYear = m["ye.year"]?.toIntOrNull() ?: 0,
                yearEndLastRefund = m["ye.lastRefund"]?.toLongOrNull(),
                yearEndLastAt = m["ye.lastAt"]?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() },
                yearEndWeekly = m["ye.weekly"] == "true",
            )
        }
    }
}
