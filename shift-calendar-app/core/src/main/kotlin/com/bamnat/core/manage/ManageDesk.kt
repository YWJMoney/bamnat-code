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
        /** 2.0.0 [RVJ-68] (총괄 HQ-107) Of [danger], the 주 52시간 weeks: the law's, a 위험; the rest are the team's 기준, a 주의. */
        val law: Int = 0,
    )

    /** One shop's month (last month's ledger, the one paid now) as the 관리 화면 sees it. */
    data class ShopGlance(
        val id: String, val name: String, val staff: Int,
        val fivePlusKnown: Boolean,
        val lastClosed: Boolean, val lastLines: Int, val lastPaid: Int, val lastSent: Int,
        val payDate: LocalDate?, val warnings: Int,
        /** 2.0.0 [RVJ-95] 검토 U05c-01·I05ca-03: 신고·납부 not ticked 했어요, within [DUE_DAYS] days or past (the 지급일 itself is the 닫기·지급 lines'). */
        val dues: List<DueSoon> = emptyList(),
        /** [RVJ-95] 근로계약서 links sent more than [CONTRACT_DAYS] days ago and not signed yet. */
        val unsigned: List<Unsigned> = emptyList(),
        /** [RVJ-95] 대타 asks someone said yes to, not put in yet. */
        val coverYes: List<CoverYes> = emptyList(),
    )

    /** 2.0.0 [RVJ-95] A 신고·지급 line of [com.bamnat.core.boss.BossFilings.upcoming]: its 했어요 key, its day (moved off a 휴일) and title. */
    data class DueSoon(val key: String, val date: LocalDate, val title: String)

    /** [RVJ-95] A 근로계약서 waiting on [name]'s signature since [sentOn]; [expired]: the link ran out first. */
    data class Unsigned(val staffId: String, val name: String, val sentOn: LocalDate, val expired: Boolean)

    /** [RVJ-95] A 대타 ask for [absent]'s [date] that [yes] said yes to. */
    data class CoverYes(val callId: String, val date: LocalDate, val absent: String, val yes: List<String>)

    /** Where a to-do opens: the 급여 대장 as it is, its 다가올 신고·지급, a staff member's 근로계약서, or the 대타 card of a month. */
    enum class Open { PLACE, FILINGS, CONTRACT, COVER }

    /** 2.0.0 [RVJ-95] 검토 U05c-01: a 신고 기한 is a to-do a week ahead (I05ca-03), a 3일 안 one a 주의. */
    const val DUE_DAYS = 7L
    /** [RVJ-95] A contract still unsigned this many days after it was sent is asked about again. */
    const val CONTRACT_DAYS = 3L

    enum class Where { TEAM, SHOP }

    /**
     * A to-do: [urgent] ones come first; [key] stays the same while it is the same job, for 숨기기 later. 2.0.0 [RVJ-50] 검토 PC-118
     * (조건 6): an urgent one is a 주의 (a date close, a choice to make); only [danger], a team's 주 52시간 weeks (2.0.0 [RVJ-68] HQ-107:
     * the law; 휴식·연속 are the team's 기준, a 주의 like the board's), is a 위험.
     */
    data class Todo(val key: String, val where: Where, val id: String, val text: String, val urgent: Boolean, val danger: Boolean = false,
                    /** 2.0.0 [RVJ-95] What pressing it opens; [ref] the staff id ([Open.CONTRACT]) or the 대타 call id ([Open.COVER]), [month] its month. */
                    val open: Open = Open.PLACE, val ref: String? = null, val month: YearMonth? = null)

    fun todos(today: LocalDate, teams: List<TeamGlance>, shops: List<ShopGlance>): List<Todo> {
        val now = YearMonth.from(today)
        val next = now.plusMonths(1)
        val out = ArrayList<Todo>()
        teams.filter { it.members > 0 }.forEach { t ->
            // 2.0.0 [RVJ-68] (HQ-107) The words of the board's 확인할 것: 주 52시간 넘김 (the law) and 기준 넘김 (휴식·연속).
            if (t.danger > 0) out += Todo("team-danger-${t.id}-$now", Where.TEAM, t.id, "${t.name} ${now.monthValue}월: " +
                listOfNotNull(t.law.takeIf { it > 0 }?.let { "주 52시간 넘김 ${it}건" }, (t.danger - t.law).takeIf { it > 0 }?.let { "기준 넘김 ${it}건(휴식·연속)" })
                    .joinToString(" · "), urgent = true, danger = t.law > 0)
            if (!t.confirmed && t.painted > 0) out += Todo("team-confirm-${t.id}-$now", Where.TEAM, t.id,
                "${t.name} ${now.monthValue}월 근무표가 확정 전이에요" + (if (t.empty > 0) " · 빈칸 ${t.empty}" else ""), urgent = true)
            // 2.0.0 [RVJ-73] (PC-176) A month not painted at all (a team made for next month) has every day short: not a to-do until painted.
            if (t.short > 0 && t.painted > 0) out += Todo("team-short-${t.id}-$now", Where.TEAM, t.id, "${t.name} ${now.monthValue}월: 인원이 모자란 날 ${t.short}일", urgent = false)
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
            // 2.0.0 [RVJ-95] 검토 U05c-01: a 대타 someone said yes to waits on one press (the day is close by nature).
            s.coverYes.filter { !it.date.isBefore(today) }.forEach { k ->
                out += Todo("shop-cover-${s.id}-${k.callId}", Where.SHOP, s.id,
                    "${s.name} ${k.date.monthValue}/${k.date.dayOfMonth} ${k.absent}님 대타: ${names(k.yes)} 들어갈 수 있어요", urgent = true,
                    open = Open.COVER, ref = k.callId, month = YearMonth.from(k.date))
            }
            // [RVJ-95] U05c-01·I05ca-03: the 신고 기한 of the coming week (and one past, not ticked), its day moved off a 휴일 already.
            s.dues.forEach { d ->
                val n = java.time.temporal.ChronoUnit.DAYS.between(today, d.date)
                if (n > DUE_DAYS) return@forEach
                out += Todo("shop-due-${s.id}-${d.key}", Where.SHOP, s.id, "${s.name}: ${d.title} · ${d.date.monthValue}월 ${d.date.dayOfMonth}일" +
                    when { n < 0 -> "까지였어요 · ${-n}일 지났어요"; n == 0L -> " 오늘까지"; else -> "까지 · ${n}일 남음" },
                    urgent = n <= 3, open = Open.FILINGS)
            }
            // [RVJ-95] U05c-01: a 근로계약서 sent and not signed (작성·교부는 근로기준법 제17조): asked about once it has waited a few days.
            s.unsigned.filter { java.time.temporal.ChronoUnit.DAYS.between(it.sentOn, today) > CONTRACT_DAYS }.forEach { u ->
                out += Todo("shop-contract-${s.id}-${u.staffId}", Where.SHOP, s.id,
                    "${s.name}: ${u.name}님 근로계약서 " + if (u.expired) "서명 링크 기한이 지났어요 · 다시 보내요" else
                        "서명을 ${java.time.temporal.ChronoUnit.DAYS.between(u.sentOn, today)}일째 기다려요",
                    urgent = false, open = Open.CONTRACT, ref = u.staffId)
            }
        }
        return out.sortedWith(compareByDescending<Todo> { it.urgent })
    }

    /** "김하늘님이", "김하늘·이바다님이", "김하늘님 외 2명이". */
    private fun names(yes: List<String>): String = when {
        yes.size <= 2 -> yes.joinToString("·") + "님이"
        else -> "${yes.first()}님 외 ${yes.size - 1}명이"
    }
}
