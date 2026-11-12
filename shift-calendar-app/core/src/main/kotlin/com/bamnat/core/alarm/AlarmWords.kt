package com.bamnat.core.alarm

/**
 * 1.39 [SHOP-13] 8차 PC 식당('영업 근무 알람 설정을 따라요'): what a kind of day's alarm is called. A shift is
 * "주간 근무", but a shop's own kinds of day (영업·단축영업) are not 근무, and a name that already ends in 근무
 * ("주간근무") does not get it twice. Used on the day sheet, the next-alarm line, the ringing screen, its
 * notification and the watch.
 */
object AlarmWords {
    /** "주간 근무", "영업", "단축영업", "주간근무". */
    fun work(name: String): String = if (name.endsWith("영업") || name.endsWith("근무")) name else "$name 근무"

    /**
     * 2.0.0 [RVG-09] 검토 TYPE-01: the word under the title — "10:00 영업 시작", "오늘 영업" — for a shop's day, "근무" otherwise.
     * Read from the day's name as [work] does, so the notice, the ringing screen and the watch all say the same.
     */
    fun noun(name: String): String = if (name.endsWith("영업")) "영업" else "근무"

    /** 2.0.0 [RVH-41] 검토 T30-75: [noun] by the kind of work — a shop's '영업', a field worker's or an investor's '일정'. */
    fun noun(name: String, style: com.bamnat.core.work.WorkStyle?): String = when (style) {
        com.bamnat.core.work.WorkStyle.BUSINESS -> "영업"
        com.bamnat.core.work.WorkStyle.FIELD, com.bamnat.core.work.WorkStyle.INVEST -> "일정"
        else -> noun(name)
    }

    /** 2.0.0 [RVH-41] [work] in [style]'s words: a field worker's, a shop's or an investor's own kinds of day are their names. */
    fun work(name: String, style: com.bamnat.core.work.WorkStyle?): String = when (style) {
        com.bamnat.core.work.WorkStyle.FIELD, com.bamnat.core.work.WorkStyle.BUSINESS, com.bamnat.core.work.WorkStyle.INVEST -> name
        else -> work(name)
    }

    /** "주간 근무 알람", "영업 알람". */
    fun alarm(name: String): String = "${work(name)} 알람"

    /**
     * 2.0.0 [RVH-41] 검토 T30-99: a field worker's, a shop's or an investor's own kinds of day are not 근무 ("오후 알람"); 교대·일근·알바
     * read [alarm].
     */
    fun alarm(name: String, style: com.bamnat.core.work.WorkStyle?): String = when (style) {
        com.bamnat.core.work.WorkStyle.FIELD, com.bamnat.core.work.WorkStyle.BUSINESS, com.bamnat.core.work.WorkStyle.INVEST -> "$name 알람"
        else -> alarm(name)
    }
}
