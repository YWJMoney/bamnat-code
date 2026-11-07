package com.bamnat.core.guide

import com.bamnat.core.work.WorkStyle

/**
 * [TIER-06][TIER-07] 1.25 묶음 Y (U18): every pro feature as the guide knows it ([FeatureRegistry] id) and
 * the app's tier row it needs (the name of the app's `Feature`, e.g. "AUTO_BACKUP"). The 프로 badge, the
 * 기능 찾기 list and 설정 › 프로 › 프로 기능 모아보기 all read this one list, and the app's test checks that
 * every tier row sold is here. A new pro feature adds its line here and its [AppFeature] in the registry.
 */
object ProCatalog {
    val tiers: Map<String, String> = linkedMapOf(
        "pay.slipAudit" to "PAYSLIP_MATCH",
        "pay.ruleImport" to "PAY_RULE_IMPORT",
        "pay.bulkEdit" to "PAY_BULK_EDIT",
        "pay.report" to "PAY_REPORT",
        "pay.counsel" to "PAY_REPORT",
        "stats.compare" to "WORK_COMPARE",
        "leave.payout" to "LEAVE_PAYOUT",
        "calendar.phone" to "DEVICE_CALENDAR",
        "share.feed" to "ICS_FEED",
        "data.autoBackup" to "AUTO_BACKUP",
        "share.moreLinks" to "MORE_LINKS",
        "share.rosterPdf" to "ROSTER_PDF",
        "alarm.mission" to "SURE_ALARM",
        "alarm.wakeCheck" to "SURE_ALARM",
        "alarm.missionPhoto" to "SURE_ALARM",
        "money.stats" to "MONEY_STATS",
        "calendars.multi" to "MORE_CALENDARS",
        // 1.25 묶음 W (결정 W-D4): 세후·실수령, 지난 월급, 사업자 세금은 한 줄(TAX_FORECAST)로 잠겨요.
        "money.takeHome" to "TAX_FORECAST",
        "money.payRecords" to "TAX_FORECAST",
        "money.bizTax" to "TAX_FORECAST",
        // 1.32 조사 대조: 리포트 묶음(PAY_REPORT)의 새 PDF들.
        "shift.yearReport" to "PAY_REPORT",
        "field.statementPdf" to "PAY_REPORT",
        "field.daysProof" to "PAY_REPORT",
        // 1.35 [FREE-10] 테마 옷장.
        "look.wardrobe" to "THEME_WARDROBE",
        // 1.36 [PRF-12] 창작자 꾸미기.
        "recipe.creatorPro" to "CREATOR_PRO",
        // 1.39 [TASK-07] 할 일 위젯 1개는 무료, 2개째부터 프로 (사용자 2026-11-07 "무료 프로 구분은 확실히").
        "widget.tasksMore" to "MORE_TASK_WIDGETS",
        // 1.39 [FX-01] 기능 작품: 1.39부터 새로 여는 기능 장치 (formula/FxPro).
        "recipe.fx" to "FX_WORKS",
    )

    fun isPro(id: String): Boolean = id in tiers

    /** The tier row [id] needs, or null for a free feature. */
    fun tierOf(id: String): String? = tiers[id]

    /** Every pro feature, the ones that suit [style] first, each group in the registry's order. */
    fun of(style: WorkStyle?): List<AppFeature> {
        val pro = FeatureRegistry.all.filter { isPro(it.id) }
        return pro.filter { it.fits(style) } + pro.filterNot { it.fits(style) }
    }
}
