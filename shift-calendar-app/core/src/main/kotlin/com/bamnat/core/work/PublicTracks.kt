package com.bamnat.core.work

import com.bamnat.core.model.ExtraKind
import com.bamnat.core.model.ExtraKinds
import com.bamnat.core.model.ExtraTiming
import com.bamnat.core.model.OffDayTreatment
import java.time.LocalTime

/**
 * 1.32 [WTYP-25] 조사 대조 W31 (근무유형 조사 8장 "교사(방과후·학부모 상담), 군인 등 직렬별 미리 정한 값 — 확인 필요"): one tap fills
 * what a 직렬 starts from, so a 교사 or 군인 does not set each value by hand. Only what was checked is filled:
 * - 교사: 교원도 「공무원수당 등에 관한 규정」의 시간외 규칙(평일 1시간 공제, 월 57시간, 근무 15일 이상인 달 정액분 10시간)을
 *   따른다 (한국교육신문 [똑똑 교직상식] 시간외근무, 2025-05-07; 인사혁신처 2026-09-15 월 57시간 유지).
 * - 군인: 시간외 인정 상한·단가를 확인하지 못해 상한은 비워 두고, 부대 지침대로 정하라고 말한다.
 * No 교원·군인 봉급표 is built in (별표 11·13 are not checked here): 기본급 stays the user's own.
 */
object PublicTracks {
    /** One extra-work kind a 직렬 starts with: its name, cell mark and colour. */
    data class Kind(val name: String, val short: String, val color: Long)

    enum class Track(val label: String, val capHours: Int?, val kinds: List<Kind>, val note: String) {
        GENERAL("일반 행정", DayWork.DEFAULT_CAP_HOURS, emptyList(),
            "평일은 하루 1시간을 빼고 세고, 시간외는 한 달 57시간까지예요."),
        TEACHER("교사", DayWork.DEFAULT_CAP_HOURS,
            listOf(Kind("학부모 상담", "상", 0xFF6A1B9A), Kind("생활지도", "생", 0xFF00838F), Kind("평가 출제", "평", 0xFF4E342E)),
            "교원도 공무원 수당 규정대로 평일은 1시간을 빼고 세고 한 달 57시간까지예요. 근무일이 15일 이상인 달은 정액분 10시간이 따로 나와요. " +
                "방과후 수업 강사료는 시간외수당과 따로 받는 돈이라 추가 근무로 넣지 않아요(메모로 남겨 두세요)."),
        MILITARY("군인", null,
            listOf(Kind("당직", "당", 0xFF37474F), Kind("훈련", "훈", 0xFF2E7D32)),
            "군인의 시간외 인정 상한과 단가는 부대 지침을 따라서 밤낮이 정해 두지 않았어요. 지침대로 상한을 정해 주세요. " +
                "당직근무비처럼 당직마다 받는 돈은 급여 › 수당에서 ‘특정 근무마다’로 넣어요."),
    }

    /** 경찰·소방 교대·현업 set theirs on 급여 › 공무원 봉급표 (교대·현업 근무), said under the tracks. */
    const val ON_SITE_NOTE = "경찰·소방 교대·현업은 급여 › 공무원 봉급표의 ‘교대·현업 근무’를 켜면 상한 없이 일한 시간 그대로 세요."

    /** [kinds] with [track]'s kinds added (a kind of the same name is shown again, not doubled). */
    fun withKinds(kinds: ExtraKinds, track: Track): ExtraKinds = track.kinds.fold(kinds) { acc, k ->
        val there = acc.kinds.firstOrNull { it.name == k.name }
        acc.put(there?.copy(hidden = false) ?: ExtraKind(acc.nextId(), k.name, k.short, k.color,
            ExtraTiming.Band(LocalTime.of(16, 30), LocalTime.of(18, 30)), offDay = OffDayTreatment.BY_DAY))
    }

    /** The kinds [track] would add that are not there yet (or hidden). */
    fun missing(kinds: ExtraKinds, track: Track): List<Kind> = track.kinds.filter { k -> kinds.kinds.none { it.name == k.name && !it.hidden } }
}
