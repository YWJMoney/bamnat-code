package com.bamnat.core.tax

import java.time.LocalDate
import java.time.YearMonth

/**
 * [TAX-01] One month's pay as it really came: what reached the account ([net]) and, when known, the pay before
 * anything came off ([gross]). It is an observation (관측치), never a second income line: the money tab counts the
 * month's pay once (결정 W-D1), and these teach [TaxLearner] what this person's payslip really takes off.
 */
data class PayObservation(
    val id: Long = 0,
    /** The pay month the money is for (근무한 달), not always the month it arrived in. */
    val payMonth: YearMonth,
    /** 실수령: what reached the account. */
    val net: Long,
    /** 세전 as the payslip or the user said; null when only the deposit is known (the pay engine's 세전 is used then). */
    val gross: Long? = null,
    /** The day it arrived, or the payslip's month's last day for a typed one. */
    val date: LocalDate,
    val source: Source = Source.TYPED,
    val kind: IncomeKind = IncomeKind.SALARY,
    /** The ledger line it was seen in; null for a typed or read one. */
    val entryId: Long? = null,
    /** [TAX-04] 시급제: hours worked that month, for 시간 × 시급 = 세전 in the record table. */
    val hours: Double? = null,
    /** [TAX-04] 일당: days worked that month. */
    val days: Int? = null,
    val note: String = "",
    val createdAt: Long = 0,
) {
    enum class Source(val label: String) {
        /** A 급여 deposit the ledger saw ([SalaryDeposits]). */
        DEPOSIT("입금 알림"),
        TYPED("직접 적음"),
        PAYSLIP("명세서 사진"),
    }

    /** Deducted, when [gross] is known. */
    val deducted: Long? get() = gross?.let { (it - net).coerceAtLeast(0) }
}

/** [TAX-04] How the pay is taxed, which decides what a record asks for and how 실수령 is worked out. */
enum class IncomeKind(val label: String, val hint: String) {
    SALARY("월급 (4대 보험)", "세전과 실수령을 적어요"),
    HOURLY("시급 파트타임", "일한 시간·시급과 실수령을 적어요"),
    FREELANCE("프리랜서 3.3%", "받기로 한 돈과 들어온 돈을 적어요"),
    DAILY("일당 (일용직)", "일한 날·일당을 적어요"),
}
