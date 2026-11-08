package com.bamnat.core.stats

import com.bamnat.core.diag.DiagEvents

/**
 * The events that "사용 통계 보내기" counts [STAT-01, STAT-03, STAT-04].
 * The server keeps the same list (company-worker `EVENTS`) and a test checks both agree.
 * Only names and daily counts leave the phone: never amounts, memo text, names or a phone id.
 */
enum class UsageEvent(val key: String) {
    OPEN("open"),
    PAY_VIEW("pay_view"),
    PAY_SETUP("pay_setup"),
    PAY_REPORT("pay_report"),
    PAYWALL_VIEW("paywall_view"),
    TRIAL_START("trial_start"),
    SUBSCRIBE("subscribe"),
    PRO_SUB("pro_sub"),
    SHARE_MADE("share_made"),
    SHARE_RECEIVED("share_received"),
    PLAZA_POST("plaza_post"),
    PLAZA_APPLY("plaza_apply"),
    COMPANY_REPORT("company_report"),
    INQUIRY("inquiry"),
    BACKUP("backup"),
    ALARM_RANG("alarm_rang"),
    ICS_EXPORT("ics_export"),
    NOTICE_OPEN("notice_open"),
    // [FREE-10] 1.35 테마 기간: a theme opened or extended with no ad because none could come, by why (처리방침 3.9 1-9항, never who).
    THEME_FREE_NO_FILL("theme_free_no_fill"),
    THEME_FREE_TIMEOUT("theme_free_timeout"),
    THEME_FREE_SHOW_FAILED("theme_free_show_failed"),
    THEME_FREE_NOT_READY("theme_free_not_ready"),
    THEME_FREE_OTHER("theme_free_other"),
    // 1.38 Phase 3 분석판 (사용자 2026-11-05 "관리자 앱에도 넣어서 분석할 수 있게"): how often, never which team, shop, name or amount.
    MANAGE_OPEN("manage_open"),
    TEAM_MADE("team_made"),
    TEAM_CONFIRM("team_confirm"),
    TEAM_LINKS("team_links"),
    LEDGER_CLOSED("ledger_closed"),
    SLIP_SENT("slip_sent"),
    PLAN_VIEW_TEAM("plan_view_team"),
    PLAN_VIEW_STAFF("plan_view_staff"),
    PLAN_BUY_TAP("plan_buy_tap"),
    MANAGE_ERROR("manage_error"),
    // P3-OPS-07 기능 안내: shown and opened, by no one in particular.
    MANAGE_HINT_SHOWN("manage_hint_shown"),
    MANAGE_HINT_OPEN("manage_hint_open"),
    // 1.38 가져오기: a roster or staff list put in from a file or photo (어느 쪽인지·몇 칸인지는 보내지 않음).
    MANAGE_IMPORT("manage_import"),
    // 1.39 [STAT2-02] 진입·머묾·회복 (깃발 stats.life, 처리방침 3.12): that day was the first open, the 1st·7th·30th day after
    // install, or an open after 7·30 days away. Counted like the rest, never the install date or a phone id ([LifeMarks]).
    LIFE_NEW("life_new"),
    LIFE_D1("life_d1"),
    LIFE_D7("life_d7"),
    LIFE_D30("life_d30"),
    LIFE_BACK7("life_back7"),
    LIFE_BACK30("life_back30"),
    ;

    companion object {
        private val byKey = entries.associateBy { it.key }

        /**
         * 1.39 [STAT2-02] The names that come with 처리방침 3.12 (깃발 stats.life). The 3.11 page counts the rest ("36가지"); the
         * phone keeps these off until the flag is on and the server drops them before it, so 3.11 stays true until 3.12 says 42.
         */
        val POLICY_312: Set<UsageEvent> = setOf(LIFE_NEW, LIFE_D1, LIFE_D7, LIFE_D30, LIFE_BACK7, LIFE_BACK30)

        fun of(key: String): UsageEvent? = byKey[key]

        /**
         * Which counted event an on-device log line is, or null for the many lines that are not counted.
         * New events are logged under their own key; older log names are mapped here.
         */
        fun fromLog(name: String, detail: String): UsageEvent? = when (name) {
            DiagEvents.APP_OPEN -> OPEN
            DiagEvents.SCREEN -> if (detail == "pay") PAY_VIEW else null
            DiagEvents.BACKUP_EXPORT -> BACKUP
            DiagEvents.ICS_EXPORT -> ICS_EXPORT
            DiagEvents.ALARM_FIRED -> ALARM_RANG
            "pay_setup_done" -> PAY_SETUP
            "company_post" -> PLAZA_POST
            "company_apply" -> PLAZA_APPLY
            "share_link_create" -> SHARE_MADE
            "received_add" -> SHARE_RECEIVED
            "plaza_rate" -> if (detail == "applied") PLAZA_APPLY else PLAZA_POST
            else -> byKey[name]
        }
    }
}
