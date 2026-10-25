package com.bamnat.app.ui.calendar

/**
 * [NAV-03] 1.25 Z (U4): what the calendar's 도구 sheet holds, in a fixed order. Whether a tool fits the calendar at all
 * (모든 조 is for shift work, 동료 근무표 받기 needs the share server) decides if it is listed; whether it can be used right
 * now (a received calendar on screen, no shifts yet) only greys it, so a tile never moves into another's place. The
 * ways out of the calendar (its settings, help) come last and apart ([Slot.away]).
 */
internal object CalendarToolSlots {
    const val EDIT = "edit"
    const val TEAMS = "teams"
    const val TYPE_MONTH = "type_month"
    const val SHARE = "share"
    const val YEAR = "year"
    const val RECEIVE = "receive"
    const val PATTERN = "pattern"
    const val ADD = "add"
    const val CUSTOMIZE = "customize"
    const val VIEW_SETTINGS = "view_settings"
    const val HELP = "help"

    data class Slot(val id: String, val label: String, val enabled: Boolean = true, val note: String? = null, val away: Boolean = false)

    fun build(fit: ToolFit): List<Slot> {
        val received = "받은 달력을 보는 중".takeIf { fit.viewingReceived }
        return buildList {
            add(Slot(EDIT, "달력 고치기", enabled = received == null, note = received))
            if (fit.teams) add(Slot(TEAMS, "모든 조 보기", enabled = fit.teamsReady && received == null,
                note = received ?: "근무 순서를 정하면 써요".takeIf { !fit.teamsReady }))
            add(Slot(TYPE_MONTH, "이번 달 전부 넣기", enabled = received == null && fit.hasShiftTypes,
                note = received ?: "근무 종류를 먼저 만들어요".takeIf { !fit.hasShiftTypes }))
            add(Slot(SHARE, "근무표 공유"))
            add(Slot(YEAR, "1년 달력"))
            if (fit.receive) add(Slot(RECEIVE, "동료 근무표 받기"))
            add(Slot(PATTERN, "근무 주기 설정"))
            add(Slot(ADD, "새 달력 만들기"))
            add(Slot(CUSTOMIZE, "화면 꾸미기", enabled = received == null, note = received))
            add(Slot(VIEW_SETTINGS, "달력 보기 설정", away = true))
            add(Slot(HELP, "도움말·문의", away = true))
        }
    }
}

/** [NAV-03] What decides the calendar's tiles; [teamsReady] is filled in once the order is known. */
internal data class ToolFit(
    val teams: Boolean = false,
    val receive: Boolean = false,
    val viewingReceived: Boolean = false,
    val hasShiftTypes: Boolean = true,
    val teamsReady: Boolean = true,
)
