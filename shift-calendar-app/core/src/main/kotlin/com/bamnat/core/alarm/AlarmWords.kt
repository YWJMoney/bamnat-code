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

    /** "주간 근무 알람", "영업 알람". */
    fun alarm(name: String): String = "${work(name)} 알람"
}
