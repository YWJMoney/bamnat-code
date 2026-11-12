package com.bamnat.core.manage

import java.time.LocalDate
import java.time.YearMonth

/**
 * 1.38 관리 화면 (사용자 2026-11-05 "리더·사장님에게 보이는 페이지는 일반 사용자 페이지와 달라야 해"): what a leader or a boss
 * has to do now, across every team and shop, worked out from plain numbers so the 관리 화면 can put the most urgent on top.
 * Nothing here is sent anywhere.
 */
object ManageDesk {
    /** One team's month as the 관리 화면 sees it. */
    data class TeamGlance(
        val id: String, val name: String, val members: Int,
        /** This month: confirmed or not, version, blank cells, dangerous signs (휴식·연속·주 52시간). */
        val confirmed: Boolean, val version: Int, val empty: Int, val danger: Int, val short: Int,
        /** Next month, for 미리 짜기. */
        val nextConfirmed: Boolean, val nextEmpty: Int,
        /** Cells painted this month: a team made for next month has none, and this month is not asked of it. */
        val painted: Int = 1,
        /** 2.0.0 [RVJ-31] false for a 5인 미만 사업장, whose signs hold no 주 52시간 (근로기준법 제11조). */
        val weekLimit: Boolean = true,
    )

    /** One shop's month (last month's ledger, the one paid now) as the 관리 화면 sees it. */
    data class ShopGlance(
        val id: String, val name: String, val staff: Int,
        val fivePlusKnown: Boolean,
        val lastClosed: Boolean, val lastLines: Int, val lastPaid: Int, val lastSent: Int,
        val payDate: LocalDate?, val warnings: Int,
    )

    enum class Where { TEAM, SHOP }

    /**
     * A to-do: [urgent] ones come first; [key] stays the same while it is the same job, for 숨기기 later. 2.0.0 [RVJ-50] 검토 PC-118
     * (조건 6): an urgent one is a 주의 (a date close, a choice to make); only [danger], a team's 휴식·연속·주 52시간 signs, is a 위험.
     */
    data class Todo(val key: String, val where: Where, val id: String, val text: String, val urgent: Boolean, val danger: Boolean = false)

    fun todos(today: LocalDate, teams: List<TeamGlance>, shops: List<ShopGlance>): List<Todo> {
        val now = YearMonth.from(today)
        val next = now.plusMonths(1)
        val out = ArrayList<Todo>()
        teams.filter { it.members > 0 }.forEach { t ->
            if (t.danger > 0) out += Todo("team-danger-${t.id}-$now", Where.TEAM, t.id, "${t.name} ${now.monthValue}월: 위험 ${t.danger}개(휴식·연속${if (t.weekLimit) "·주 52시간" else ""})", urgent = true, danger = true)
            if (!t.confirmed && t.painted > 0) out += Todo("team-confirm-${t.id}-$now", Where.TEAM, t.id,
                "${t.name} ${now.monthValue}월 근무표가 확정 전이에요" + (if (t.empty > 0) " · 빈칸 ${t.empty}" else ""), urgent = true)
            if (t.short > 0) out += Todo("team-short-${t.id}-$now", Where.TEAM, t.id, "${t.name} ${now.monthValue}월: 인원이 모자란 날 ${t.short}일", urgent = false)
            // From the 15th the next month is due; from the 25th it is late.
            if (today.dayOfMonth >= 15 && !t.nextConfirmed) out += Todo("team-next-${t.id}-$next", Where.TEAM, t.id,
                "${t.name} ${next.monthValue}월 근무표 짜기" + (if (t.nextEmpty > 0) " · 빈칸 ${t.nextEmpty}" else ""), urgent = today.dayOfMonth >= 25)
        }
        val last = now.minusMonths(1)
        shops.forEach { s ->
            if (!s.fivePlusKnown && s.staff > 0) out += Todo("shop-five-${s.id}", Where.SHOP, s.id, "${s.name}: 5인 이상인지 골라 주세요(가산 계산이 달라져요)", urgent = true)
            if (s.warnings > 0) out += Todo("shop-warn-${s.id}-$last", Where.SHOP, s.id, "${s.name} ${last.monthValue}월 대장: 확인할 것 ${s.warnings}개", urgent = false)
            if (s.staff > 0 && !s.lastClosed) {
                val due = s.payDate?.let { !today.isBefore(it.minusDays(3)) } ?: (today.dayOfMonth >= 5)
                out += Todo("shop-close-${s.id}-$last", Where.SHOP, s.id,
                    "${s.name} ${last.monthValue}월 대장을 닫기 전이에요" + (s.payDate?.let { " · 지급일 ${it.monthValue}/${it.dayOfMonth}" } ?: ""), urgent = due)
            }
            if (s.lastClosed && s.lastPaid < s.lastLines) out += Todo("shop-paid-${s.id}-$last", Where.SHOP, s.id,
                "${s.name} ${last.monthValue}월: 지급 표시 ${s.lastLines - s.lastPaid}명 남았어요", urgent = s.payDate?.let { !today.isBefore(it) } == true)
            if (s.lastClosed && s.lastSent < s.lastLines) out += Todo("shop-sent-${s.id}-$last", Where.SHOP, s.id,
                "${s.name} ${last.monthValue}월: 명세서를 아직 안 보낸 직원 ${s.lastLines - s.lastSent}명(지급 때 꼭 줘요, 근로기준법 제48조)", urgent = false)
        }
        return out.sortedWith(compareByDescending<Todo> { it.urgent })
    }
}
