package com.bamnat.core.work

/**
 * 2.0.0 [RVH-24] 검토 뿌리 고침 5번 '유형 규칙 한 곳과 낱말 검사' (T30-75~105, 보고서 8장 표): the words a screen must not show
 * to each kind of work, in one place, so a screen that forgets to ask the calendar's kind is caught by a test instead of a
 * user. 교대 has none: the app's words were written for it first.
 *
 * How other parts use it (갈래 I·J·K·O·P·N):
 * - In a test, [leaks] of a screen's text for a style lists the words that slipped through; the app's
 *   `TypeWordsScreenTest` renders the main screens for each kind and fails on a new one. A word that is right on one
 *   screen (a boss's '급여 대장' for a shop) goes in [allowed] with the phrase around it, never by taking the word out.
 * - In code, show a style's own word ([words], [dayKindNoun], [scheduleWord], [moneyTab]) instead of a shift word; when no
 *   word fits, hide the line for that style.
 * - The user's own names (a 근무 종류 called '야간', a memo) are not the app's words: callers drop them before asking.
 */
object TypeWords {
    private val NIGHT = listOf("연속 야간", "야간 뒤", "야간이면", "한 달 야간", "야간 전")
    private val ROTA = listOf("교대", "전공의", "근무 주기", "조 변경", "모든 조", "주주휴휴")
    private val EMPLOYEE = listOf("연차", "대근", "주휴", "퇴근", "근무·급여", "근무 알람", "근무마다", "근무별", "회사 사정", "근무표")

    /** What must not show for [style]; nothing for 교대 (and a calendar from before kinds of work, which is 교대). */
    fun forbidden(style: WorkStyle?): List<String> = when (style) {
        null, WorkStyle.SHIFT -> emptyList()
        WorkStyle.DAY -> ROTA + NIGHT
        WorkStyle.HOURLY -> ROTA + NIGHT + listOf("52시간", "대근", "근무·급여")
        WorkStyle.FIELD -> ROTA + NIGHT + EMPLOYEE
        WorkStyle.BUSINESS -> ROTA + NIGHT + EMPLOYEE + listOf("출근", "연속 근무", "야간", "쉬는 날")
        WorkStyle.INVEST -> ROTA + NIGHT + EMPLOYEE + listOf("출근", "연속 근무", "야간", "근무", "명세서", "급여", "회사")
    }

    /**
     * Phrases that hold a forbidden word but are right for that kind of work: the app's own name for the kind of work
     * ('근무 유형'), a choice of kinds that names them all, and a shop owner's or a boss's staff pay. Matched before [forbidden].
     */
    fun allowed(style: WorkStyle?): List<String> = ALL_KINDS + when (style) {
        WorkStyle.BUSINESS -> listOf("직원 급여", "가게 급여", "급여 대장", "직원 근무표", "직원 연차", "직원 출근", "알바 출근", "매달 쉬는 날")
        WorkStyle.INVEST -> listOf("회사 규칙 광장", "회사 정보")
        else -> emptyList()
    }

    /** Names of the kinds of work and of the app, right everywhere (a picker lists every kind). */
    private val ALL_KINDS = listOf("근무 유형", "교대·일근", "교대 광장", "일근 광장")

    /**
     * The forbidden words of [style] in [text], in the order of [forbidden], each once; [own] are the user's own names
     * (근무 종류, 메모 제목) taken out first, and [allowed] phrases after them.
     */
    fun leaks(style: WorkStyle?, text: String, own: Collection<String> = emptyList()): List<String> {
        val words = forbidden(style)
        if (words.isEmpty() || text.isBlank()) return emptyList()
        var rest = text
        for (o in own.filter { it.isNotBlank() }.sortedByDescending { it.length }) rest = rest.replace(o, " ")
        for (a in allowed(style).sortedByDescending { it.length }) rest = rest.replace(a, " ")
        return words.filter { it in rest }.distinct()
    }
}
