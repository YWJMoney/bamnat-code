package com.bamnat.core.flags

/**
 * [NXT-03] 1.29 AU: the one name table of the 프로 features a `free.<FEATURE>` row can open, code → 한국어 이름, in the
 * order the 프로 screen lists them. Before 1.29 the developer app named them from its own copy (server FREE_FEATURES)
 * and the phone's 진단 showed the bare code; now [FlagCatalog.free] fills the feature's name from here, the server's
 * copy is made from it (`CORE_FEATURES` in flag-catalog.js, by FlagCatalogExportTest) and the app's FeatureGate.Feature
 * takes its labels from it, so the three always say the same.
 *
 * Only features sold with 프로 belong here; a code the table does not know keeps its code as its name.
 */
object ProFeatures {
    val NAMES: Map<String, String> = linkedMapOf(
        "NO_ADS" to "광고 없음",
        "PAYSLIP_MATCH" to "명세서 대조",
        "PAY_RULE_IMPORT" to "임금 규칙 자동 인식",
        "PAY_BULK_EDIT" to "값 일괄 적용",
        "MONEY_STATS" to "돈 통계",
        "MORE_CALENDARS" to "달력 여러 개",
        "PAY_REPORT" to "급여 리포트 PDF·CSV",
        "WORK_COMPARE" to "근무·급여 비교",
        "DEVICE_CALENDAR" to "휴대폰 캘린더에 근무 쓰기",
        "ICS_FEED" to "캘린더 구독 주소",
        "AUTO_BACKUP" to "매일 자동 백업",
        "MORE_LINKS" to "공유 링크 여러 개, 1년까지",
        "ROSTER_PDF" to "근무표 PDF",
        "SURE_ALARM" to "끄기 미션·일어났는지 확인",
        "LEAVE_PAYOUT" to "미사용 연차수당 계산",
        "TAX_FORECAST" to "세금 예측·실수령",
    )

    /** The feature's 한국어 이름, or [code] itself for a code this table does not know. */
    fun name(code: String): String = NAMES[code] ?: code
}
