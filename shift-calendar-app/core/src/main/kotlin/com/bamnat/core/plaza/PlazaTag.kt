package com.bamnat.core.plaza

import com.bamnat.core.model.ShiftType
import com.bamnat.core.model.WorkPattern
import com.bamnat.core.pay.PayProfile
import com.bamnat.core.pay.WorkForm
import com.bamnat.core.pay.WorkForms
import com.bamnat.core.work.WorkStyle
import java.time.LocalDateTime

/**
 * 1.39 [PLZC-10] 근무 태그 (사용자 2026-11-07 "유형별 태그를 달아 어떤 근무를 하는지 사람들이 볼 수 있게 … 준 블라인드 … 인증은 없지만
 * 실제 달력을 그렇게 사용하고 있을 거니깐"): what a post in a kind of work's 광장 says about how its writer works, worked out on the
 * phone from the calendar's own setup, never typed. Its words come from fixed lists only (the server takes nothing else), so a tag
 * can only say what a calendar says. [months] is the server's (the first day it saw this phone), never the phone's.
 * Design: 프로젝트 `계획/1.39-광장-커뮤니티-설계.md` 3장.
 */
data class PlazaTag(
    val style: WorkStyle,
    /** The 근무형태 the calendar runs ([WorkForms.guess]), when it is shown and the calendar says enough. */
    val form: WorkForm? = null,
    /** An [IndustryGroups] key the writer chose to show; null when off. */
    val industry: String? = null,
    /** Months since the server first saw this phone (0: this month); null when the writer hides it or the server sent none. */
    val months: Int? = null,
    /** Whether the writer was at work by their own calendar when they wrote. */
    val now: Now? = null,
) {
    enum class Now(val key: String, val mark: String, val words: String) {
        NIGHT("night", "🌙", "야간 근무 중"),
        DAY("day", "☀", "근무 중"),
        ;

        companion object {
            fun of(key: String?): Now? = entries.firstOrNull { it.key == key }
        }
    }

    /** The words in order: 교대 · 4조 2교대 · 반도체·전자 · 밤낮 7개월. */
    fun words(): List<String> = listOfNotNull(
        PlazaTags.styleWord(style),
        form?.label,
        industry?.let { IndustryGroups.byKey(it)?.name },
        months?.let { if (it <= 0) "밤낮 처음" else "밤낮 ${it}개월" },
    )

    fun line(): String = words().joinToString(" · ")

    /** What TalkBack reads: the words, whether at work, and that it comes from the calendar. */
    fun speech(): String = (words() + listOfNotNull(now?.words) + "달력 기준").joinToString(", ")

    /** What the phone sends with a post: keys only (s, f, i, n); the server adds m. */
    fun fields(): Map<String, String> = buildMap {
        put("s", style.name)
        form?.let { put("f", it.name) }
        industry?.let { put("i", it) }
        now?.let { put("n", it.key) }
    }

    companion object {
        /** A tag as the server sends it ({s, f?, i?, m?, n?}); anything not on the lists is left out, a bad style makes none. */
        fun of(get: (String) -> Any?): PlazaTag? {
            val style = (get("s") as? String)?.let { s -> WorkStyle.entries.firstOrNull { it.name == s } } ?: return null
            val form = (get("f") as? String)?.let { f -> WorkForm.entries.firstOrNull { it.name == f } }?.takeIf { it in PlazaTags.formsFor(style) }
            val industry = (get("i") as? String)?.takeIf { IndustryGroups.byKey(it) != null && style in PlazaTags.INDUSTRY_STYLES }
            val months = (get("m") as? Number)?.toInt()?.takeIf { it in 0..600 }
            return PlazaTag(style, form, industry, months, Now.of(get("n") as? String)?.takeIf { style in PlazaTags.NOW_STYLES })
        }
    }
}

/** 1.39 [PLZC-10] How a tag is worked out, and which words each kind of work may show. */
object PlazaTags {
    /** What the writer chose to show (설정 › 마을 · 광장 글 쓰기 창 '내 근무 태그'). The kind of work is always shown. */
    data class Scope(
        val form: Boolean = true,
        /** The [IndustryGroups] key to show; null keeps it off (it can narrow a company down, 설계 3-3). */
        val industry: String? = null,
        val months: Boolean = true,
        val now: Boolean = true,
    )

    /** A calendar with no pattern needs this many worked days in the last [WINDOW_DAYS] before its 근무형태 is said. */
    const val MIN_WORKED = 10
    const val WINDOW_DAYS = 28

    /** The kinds of work for which 업종 means something (a company's line of work). */
    val INDUSTRY_STYLES = setOf(WorkStyle.SHIFT, WorkStyle.DAY)

    /** The kinds of work that are 'at work' at an hour (not 가게, whose day is its own, nor 투자). */
    val NOW_STYLES = setOf(WorkStyle.SHIFT, WorkStyle.DAY, WorkStyle.FIELD, WorkStyle.HOURLY)

    /** The 근무형태 words a kind of work may carry: 교대 its rotations, 일근 주5일·당직. Others carry none in 1.39. */
    fun formsFor(style: WorkStyle): Set<WorkForm> = when (style) {
        WorkStyle.SHIFT -> setOf(WorkForm.THREE_SHIFT, WorkForm.TWO_SHIFT, WorkForm.FOUR_TEAM, WorkForm.NURSE, WorkForm.ON_CALL,
            WorkForm.DAY_ON_DAY_OFF, WorkForm.PUBLIC)
        WorkStyle.DAY -> setOf(WorkForm.OFFICE, WorkForm.ON_CALL)
        else -> emptySet()
    }

    /** The kind of work as one short word on a tag. */
    fun styleWord(style: WorkStyle): String = when (style) {
        WorkStyle.SHIFT -> "교대"
        WorkStyle.DAY -> "일근"
        WorkStyle.HOURLY -> "알바"
        WorkStyle.FIELD -> "현장"
        WorkStyle.BUSINESS -> "가게"
        WorkStyle.INVEST -> "투자"
    }

    /**
     * The tag of a calendar of [style] as [scope] allows. The 근무형태 only when the calendar runs a pattern or [worked] (days with
     * a shift in the last [WINDOW_DAYS]) reaches [MIN_WORKED], and only one its kind of work may carry; otherwise the kind alone.
     */
    fun compute(
        style: WorkStyle,
        shiftTypes: List<ShiftType>,
        pattern: WorkPattern?,
        profile: PayProfile?,
        worked: Int,
        scope: Scope,
        now: PlazaTag.Now? = null,
    ): PlazaTag {
        val enough = pattern != null || worked >= MIN_WORKED
        val form = if (scope.form && enough) WorkForms.guess(shiftTypes, pattern, profile)?.takeIf { it in formsFor(style) } else null
        val industry = scope.industry?.takeIf { style in INDUSTRY_STYLES && IndustryGroups.byKey(it) != null && it != IndustryGroups.OTHER.key }
        return PlazaTag(style, form, industry, null, now.takeIf { scope.now && style in NOW_STYLES })
    }

    /** At work at [at] for a shift from [from] to [to]: 야간 between 22시 and 6시, else 근무 중; null outside the shift. */
    fun nowOf(from: LocalDateTime?, to: LocalDateTime?, at: LocalDateTime): PlazaTag.Now? {
        if (from == null || to == null || at.isBefore(from) || !at.isBefore(to)) return null
        return if (at.hour >= 22 || at.hour < 6) PlazaTag.Now.NIGHT else PlazaTag.Now.DAY
    }

    /** [PLZC-21] A shift starting at [startHour] is a night (from 17시, or before 4시) or a day one. */
    fun shiftOf(startHour: Int): PlazaTag.Now = if (startHour >= 17 || startHour < 4) PlazaTag.Now.NIGHT else PlazaTag.Now.DAY

    /** The industry groups a writer may pick, 그 밖의 업종 left out (it says nothing). */
    fun industries(): List<IndustryGroups.Group> = IndustryGroups.ALL
}
