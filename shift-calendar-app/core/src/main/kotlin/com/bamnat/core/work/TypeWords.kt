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
        // [RVJ-44] 검토 PC-106: a shop's 사장님 has no '회사 규칙' (the 급여 대장 said '회사 규칙에 넣지 않은 … 가산').
        WorkStyle.BUSINESS -> ROTA + NIGHT + EMPLOYEE + listOf("출근", "연속 근무", "야간", "쉬는 날", "회사 규칙")
        WorkStyle.INVEST -> ROTA + NIGHT + EMPLOYEE + listOf("출근", "연속 근무", "야간", "근무", "명세서", "급여", "회사")
    }

    /**
     * Phrases that hold a forbidden word but are right for that kind of work: the app's own name for the kind of work
     * ('근무 유형'), a choice of kinds that names them all, and a shop owner's or a boss's staff pay. Matched before [forbidden].
     */
    fun allowed(style: WorkStyle?): List<String> = ALL_KINDS + when (style) {
        WorkStyle.BUSINESS -> listOf(
            "직원 급여", "가게 급여", "급여 대장", "직원 근무표", "직원 연차", "직원 출근", "알바 출근", "매달 쉬는 날",
            // 2.0.0 [RVH-41] A boss's staff pay and the staff's schedule (직원 인건비 › 근무표 짜기).
            "주휴수당", "주휴·연장·야간", "근무표 짜기",
            // [RVJ-44] The 직원 급여's 야간 가산 (근로기준법 제56조 제3항) is the 사장님's to pay, as 직원 연차 is; the 광장's name stays.
            "회사 규칙 광장", "야간·휴일 가산", "야간 가산",
        )
        WorkStyle.INVEST -> listOf("회사 규칙 광장", "회사 정보")
        else -> emptyList()
    }

    /** Names of the kinds of work and of the app, right everywhere (a picker lists every kind). */
    private val ALL_KINDS = listOf("근무 유형", "교대·일근", "교대 광장", "일근 광장")

    /**
     * 2.0.0 [RVH-41] 검토 T30-102 [text] written for 교대, said in [style]'s words where a word of another kind would show: the shared
     * phrases (탭 이름 '근무·급여', '근무표', '근무 알람', '근무마다', '근무 주기', 가게의 '쉬는 날', 알바의 '대근') become the kind's own.
     * Only a word [forbidden] for the kind is touched, so a text already right stays as it is. What a swap cannot say well is written
     * for the kind where the text lives (FeatureRegistry `said`), never left to this.
     */
    fun say(style: WorkStyle?, text: String): String {
        val bad = forbidden(style)
        if (bad.isEmpty() || text.isBlank()) return text
        val w = style.words()
        // A phrase right for the kind ('직원 근무표', '주휴수당') is kept as it is while the words around it change.
        val keep = allowed(style).sortedByDescending { it.length }.filter { it in text }
        var out = text
        keep.forEachIndexed { i, a -> out = out.replace(a, "\u0000$i\u0000") }
        fun swap(from: String, to: String) { if (bad.any { it in from } && from in out) out = out.replace(from, to) }
        swap("근무·급여", w.tab)
        swap("근무표", scheduleWord(style))
        swap("근무 알람", "알람")
        swap("근무 주기", w.pattern)
        when (style) {
            WorkStyle.FIELD -> { swap("근무마다", "일정마다"); swap("근무별", "일정별"); swap("퇴근 뒤", "일 끝난 뒤"); swap("퇴근 후", "일 끝난 뒤"); swap("퇴근길", "돌아가는 길"); swap("퇴근", "일 끝") }
            WorkStyle.BUSINESS -> { swap("근무마다", "영업일마다"); swap("근무별", "날별"); swap("퇴근 뒤", "마감 뒤"); swap("퇴근길", "돌아가는 길"); swap("퇴근", "마감"); swap("쉬는 날", w.offDay) }
            WorkStyle.INVEST -> { swap("근무마다", "날마다"); swap("근무별", "날별"); swap("퇴근 뒤", "장 마감 뒤"); swap("퇴근", "장 마감"); swap("근무", w.workDay) }
            WorkStyle.HOURLY -> swap("대근", "대타")
            else -> {}
        }
        keep.forEachIndexed { i, a -> out = out.replace("\u0000$i\u0000", a) }
        return out
    }

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
