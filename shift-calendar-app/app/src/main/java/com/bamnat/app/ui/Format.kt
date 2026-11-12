package com.bamnat.app.ui

import com.bamnat.core.model.WorkTime
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

object Fmt {
    private val korean = Locale.KOREAN
    private val time = DateTimeFormatter.ofPattern("HH:mm")

    /**
     * G 1.32 [WTYP-07] 609 설정 › 화면과 글자 › 오전/오후로 보기: "오후 3:05" instead of "15:05". Set from the settings when the
     * app and the widgets start and when it is changed; what wrote a time redraws on a change.
     */
    // 2.0.0 [RVP-21] (검토 UIX-42) As Compose state, like [masked]: the times on screen redraw where they are, instead of the whole
    // app being made again (which closed 설정 and went back to the calendar).
    private val clock12State = androidx.compose.runtime.mutableStateOf(false)
    var clock12: Boolean
        get() = clock12State.value
        set(v) { if (clock12State.value != v) clock12State.value = v }

    /**
     * 1.37 [LEDH-22] 금액 가리기: every amount Fmt writes shows as dots, for opening the app on a bus or at work. Set from
     * the settings with [clock12]; the eye on 가계부 turns it on and off, and the widgets follow.
     */
    // 1.37 PC 5차: a plain field did not redraw what was already on screen (예산 22,000/500,000, 봉투, 최근 기록 stayed),
    // so a screen only changed where it read 가리기 itself. As Compose state every amount Fmt wrote redraws.
    private val maskedState = androidx.compose.runtime.mutableStateOf(false)
    var masked: Boolean
        get() = maskedState.value
        set(v) { if (maskedState.value != v) maskedState.value = v }
    const val MASK = "•••••"

    private fun twelve(t: LocalTime): String {
        val h = t.hour % 12
        return "${if (t.hour < 12) "오전" else "오후"} ${if (h == 0) 12 else h}:${"%02d".format(t.minute)}"
    }

    // PC 확인 f6b585f8 (멈춤 2,099ms: 달력 칸마다 dayOfWeekShort): the locale's display names were looked up on the main thread for every
    // cell, and the first lookup loads the locale data; the seven Korean names are fixed.
    private val weekdays = arrayOf("월", "화", "수", "목", "금", "토", "일")
    fun dayOfWeekShort(d: DayOfWeek): String = weekdays[d.value - 1]

    fun time(t: LocalTime?): String = t?.let { if (clock12) twelve(it) else it.format(time) } ?: "--:--"

    fun timeRange(start: LocalTime, end: LocalTime): String =
        "${time(start)} – ${time(end)}" + if (WorkTime.isOvernight(start, end)) " (다음날)" else ""

    /** PC 확인 Z11 1.36 (위젯 2×1에서 '19:00 – 07:00 (다음…' 잘림): the short form for small places, "19:00–07:00(+1)". */
    fun timeRangeShort(start: LocalTime, end: LocalTime): String =
        "${time(start)}–${time(end)}" + if (WorkTime.isOvernight(start, end)) "(+1)" else ""

    fun month(m: YearMonth): String = "${m.year}년 ${m.monthValue}월"

    fun monthShort(m: YearMonth): String = "${m.monthValue}월"

    /** "10월 14일 (수)" */
    fun dayTitle(d: LocalDate): String = "${d.monthValue}월 ${d.dayOfMonth}일 (${dayOfWeekShort(d.dayOfWeek)})"

    /** "2026. 10. 14. (수)" */
    fun fullDate(d: LocalDate): String =
        "${d.year}. ${d.monthValue}. ${d.dayOfMonth}. (${dayOfWeekShort(d.dayOfWeek)})"

    fun shortFullDate(d: LocalDate): String = "${d.year}.${d.monthValue}.${d.dayOfMonth}"

    fun shortDate(d: LocalDate): String = "${d.monthValue}/${d.dayOfMonth}"

    private val wonFormat = java.text.NumberFormat.getIntegerInstance(korean)

    /**
     * 1.37 PC 4차 (금액 가리기에서 '하루 1,545원 더 써요'가 그대로 보였어요): a sentence made outside Fmt (core) with its amounts
     * hidden the same way, "하루 •••••원 더 써요"; as it is when 가리기 is off.
     */
    fun hide(text: String): String = if (!masked) text else wonInText.replace(text, MASK + "원")
    /** 1.37 PC 6차: also short amounts in 만·억·천 ("365.7만", "−37.1만"), for cells that hold only money. */
    fun hideShort(text: String): String = if (!masked) text else shortInText.replace(hide(text), "•••")
    private val shortInText = Regex("""[-+−]?\d[\d,.]*\s*(?:억|만|천)(?:\s*\d[\d,.]*\s*(?:만|천))?\s*원?""")
    private val wonInText = Regex("""[-+−]?\d[\d,.]*\s*(?:억\s*)?(?:\d[\d,.]*\s*)?(?:만|천)?\s*원""")

    /** "1,250,000원" */
    fun won(amount: Long): String = if (masked) MASK + "원" else wonFormat.format(amount) + "원"

    /**
     * Amounts for a calendar cell: "125만", "12.5만", "1.25만", "1.2만", "8.5천", "500" [WORK-02]. Cut down, never up.
     * 1.38 PC 7차 (12,000과 12,500이 모두 '1만'): under 100만 the 만 keeps its decimals (two under 10만, one up to 100만), and 천 one,
     * with trailing zeros dropped, so amounts that differ read differently.
     */
    fun wonShort(amount: Long): String {
        if (masked) return "•••"
        val a = kotlin.math.abs(amount)
        val sign = if (amount < 0) "-" else ""
        return sign + when {
            // 2.0.0 [RVP-28] (검토 UIX-51) Cut down like 만, never up: 199,999,999 is 1.9억, not 2억.
            a >= 100_000_000 -> "%.1f억".format(java.util.Locale.KOREA, (a / 10_000_000) / 10.0).replace(".0억", "억")
            a >= 1_000_000 -> "${a / 10_000}만"
            a >= 100_000 -> cut(a, 1_000, 10, "만")
            a >= 10_000 -> cut(a, 100, 100, "만")
            a >= 1_000 -> cut(a, 100, 10, "천")
            else -> a.toString()
        }
    }

    /** [a] counted in [step]s, [per] of them to the unit: whole part, then the decimals without trailing zeros. */
    private fun cut(a: Long, step: Long, per: Long, unit: String): String {
        val n = a / step
        val frac = (n % per).toString().padStart(per.toString().length - 1, '0').trimEnd('0')
        return "${n / per}" + (if (frac.isEmpty()) "" else ".$frac") + unit
    }

    /** "10월 17일" */
    fun monthDay(d: LocalDate): String = "${d.monthValue}월 ${d.dayOfMonth}일"

    /** [DAY-03] "을" or "를" after [word]: 대근을, 잔업 2시간을, 휴가를. */
    fun obj(word: String): String = if (batchim(word) != 0) "을" else "를"

    /** [DAY-03] "으로" or "로" after [word]: 야간으로, 데이로, 10월 17일로. */
    fun ro(word: String): String = batchim(word).let { if (it == 0 || it == RIEUL) "로" else "으로" }

    /** [SETUP-01] "이에요" or "예요" after [word]: 야간이에요, 데이예요. */
    fun ieyo(word: String): String = if (batchim(word) != 0) "이에요" else "예요"

    /** [SETUP-01] "은" or "는" after [word]: 오늘은, 10월 17일은. */
    fun eun(word: String): String = if (batchim(word) != 0) "은" else "는"

    /** 1.36 U14: "과" or "와" after [word]: 야간과, 데이와. */
    fun wa(word: String): String = if (batchim(word) != 0) "과" else "와"

    /** [SETUP-01] "이" or "가" after [word]: 야간이, 데이가. */
    fun iga(word: String): String = if (batchim(word) != 0) "이" else "가"

    private const val RIEUL = 8

    /** The final consonant index of [word]'s last sound (0 for none), reading digits and a few letters as Korean says them. */
    private fun batchim(word: String): Int {
        // 1.36 U14: a name in quotes or brackets ("‘오전 알바’") is read by its last letter.
        val t = word.trimEnd { it.isWhitespace() || it in "’'\")]}」』>" }
        val c = t.lastOrNull() ?: return 0
        return when {
            c in '가'..'힣' -> (c - '가') % 28
            // 영 일 삼 육 칠 팔 end in a consonant; 이 사 오 구 do not.
            c.isDigit() -> when (c) { '0' -> 21; '1', '7', '8' -> RIEUL; '3' -> 16; '6' -> 1; else -> 0 }
            c in "LlRr" -> RIEUL
            // 2.0.0 [RVP-28] (검토 UIX-51 'Evening를') -ng ends in ㅇ.
            c in "Gg" && t.dropLast(1).lastOrNull()?.let { it in "Nn" } == true -> 21
            c in "MmNn" -> 4
            else -> 0
        }
    }

    /** "시작 30분 전", "시작 7시간 뒤", "시작 시각": [offset] always said from the start. */
    fun startOffset(minutes: Int): String = offset(minutes).let { if (it.startsWith("시작")) it else "시작 $it" }

    fun offset(minutes: Int): String = when {
        minutes == 0 -> "시작 시각"
        // [EASE-18] An alarm after the start: "시작 7시간 뒤".
        minutes < 0 -> "시작 " + offset(-minutes).removeSuffix(" 전") + " 뒤"
        minutes % 60 == 0 -> "${minutes / 60}시간 전"
        minutes > 60 -> "${minutes / 60}시간 ${minutes % 60}분 전"
        else -> "${minutes}분 전"
    }
}
