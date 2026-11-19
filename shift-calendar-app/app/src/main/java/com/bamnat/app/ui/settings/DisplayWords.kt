package com.bamnat.app.ui.settings

import com.bamnat.core.work.WorkStyle

/**
 * 2.0.0 [RVO-19] 검토 SET-06·T11a-08: 설정 › 화면과 글자 spoke 교대 to everyone ('야간 근무 중 어둡게', '야간 근무 중 새벽에…',
 * '주 근무시간은…', '근무시간 (8h)'). These are those lines in each kind's own words, with nothing to say where the kind has none.
 * The rows go through [said] too, but [com.bamnat.core.work.TypeWords.say] only swaps words, and a 투자 '야간 매매 중' or a 일근 '야간
 * 근무' is a sentence to write again, not a word to swap.
 */
internal object DisplayWords {
    /** Work that reaches into the night (22~06), or null where the kind has none to speak of (일근·현장·투자). */
    fun night(style: WorkStyle?): String? = when (style) {
        null, WorkStyle.SHIFT, WorkStyle.HOURLY -> "야간 근무"
        WorkStyle.BUSINESS -> "밤 영업"
        WorkStyle.DAY, WorkStyle.FIELD, WorkStyle.INVEST -> null
    }

    /** The theme chip that darkens the app through the night's work; offered where [night] has a word, kept where it was chosen. */
    fun nightTheme(style: WorkStyle?): String = (night(style) ?: "밤 일정") + " 중 어둡게"

    /** What that chip does, said once it is chosen. */
    fun nightThemeHint(style: WorkStyle?): String = "22~06시에 걸친 ${night(style) ?: "일정"} 중에는 어둡게, 그 밖에는 휴대폰 따라"

    /** The hours under a date ("근무시간", "영업시간"); null for an investor, whose days have no hours to show. */
    fun hours(style: WorkStyle?): String? = when (style) {
        WorkStyle.BUSINESS -> "영업시간"
        WorkStyle.FIELD -> "일정 시간"
        WorkStyle.INVEST -> null
        else -> "근무시간"
    }

    /** 하루 시작 시각: where a small-hours note goes, said for the night work the kind has (or for none). */
    fun dayStartHint(style: WorkStyle?): String = when (style) {
        null, WorkStyle.SHIFT, WorkStyle.HOURLY -> "야간 근무 중 새벽에 적은 돈·메모·잠은 그 야간 날에 붙어요. 근무가 없는 날은 이 시각 전까지 어제로 쳐요"
        WorkStyle.BUSINESS -> "새벽까지 이어진 영업 중에 적은 돈·메모·잠은 그 영업일에 붙어요. 영업하지 않은 날은 이 시각 전까지 어제로 쳐요"
        WorkStyle.DAY, WorkStyle.FIELD, WorkStyle.INVEST -> "새벽에 적은 돈·메모·잠은 이 시각 전까지 어제로 쳐요"
    }

    /**
     * 2.0.0 [RVO-19] 검토 T11a-16·SET-11-5: what a calendar holds, as the object of a sentence ("근무를", "영업일을"), for 데이터's
     * 옮기기·내보내기 rows.
     */
    fun daysObject(style: WorkStyle?): String = when (style) {
        WorkStyle.BUSINESS -> "영업일을"
        WorkStyle.FIELD -> "일정을"
        WorkStyle.INVEST -> "매매 일정을"
        else -> "근무를"
    }

    /** 2.0.0 [RVO-19] 검토 T11a-16: the backup's contents in the kind's words (a shop's 매출, an 알바's 시급 설정; no 급여 설정 without wages). */
    fun backupHolds(style: WorkStyle?): String = when (style) {
        WorkStyle.BUSINESS -> "영업일, 매출·경비, 메모, 돈 기록, 수면 기록, 앱 설정"
        WorkStyle.FIELD -> "일정, 일감·받을 돈, 메모, 돈 기록, 수면 기록, 앱 설정"
        WorkStyle.INVEST -> "매매 기록, 계좌·원칙, 메모, 돈 기록, 수면 기록, 앱 설정"
        WorkStyle.HOURLY -> "근무, 바꾼 날짜, 메모, 돈 기록, 수면 기록, 시급 설정, 앱 설정"
        else -> "근무, 바꾼 날짜, 메모, 돈 기록, 수면 기록, 급여 설정, 앱 설정"
    }

    /** 한 주의 시작: the week's hours are counted 월~일 only where the app counts them (교대·일근·알바). */
    fun weekStartHint(style: WorkStyle?): String =
        if (style?.usesPayEngine != false) "달력 첫 칸의 요일이에요. 주 근무시간은 늘 월~일로 세요" else "달력 첫 칸의 요일이에요"
}
