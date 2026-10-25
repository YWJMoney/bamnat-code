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
) {
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
            )
        }
    }
}
