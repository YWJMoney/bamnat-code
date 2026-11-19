package com.bamnat.app.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bamnat.app.appContainer
import com.bamnat.app.ui.Fmt
import com.bamnat.app.ui.components.asColor
import com.bamnat.app.ui.components.contentColorOn
import com.bamnat.core.hours.HoursFormat
import com.bamnat.core.model.ExtraEntry
import com.bamnat.core.model.ExtraKind
import com.bamnat.core.model.ExtraKinds
import com.bamnat.core.model.ExtraTiming
import com.bamnat.core.model.ShiftType
import com.bamnat.core.pay.ExtraMinutes
import com.bamnat.core.schedule.ResolvedDay
import com.bamnat.core.schedule.ResolvedExtra
import java.time.LocalTime

/** [PAYX-09][EXTRA-01] The user's kinds of extra work, provided at the app root. */
val LocalExtraKinds = compositionLocalOf { ExtraKinds() }

/** [EXTRA-01] A square with the kind's mark (잔·특·대 or the company's own) in the kind's colour. */
@Composable
fun ExtraKindMark(kind: ExtraKind, size: Dp = 16.dp, modifier: Modifier = Modifier, asIs: Boolean = false) {
    val color = kind.colorArgb.asColor()
    // 1.39 [WTYP-30] PC 7차: 일근's built-in 잔업 is 야근, so its mark is '야' too ([asIs] keeps the kind's own, as its editor does).
    val mark = if (asIs) kind.mark else ExtraText.mark(kind, ExtraText.styleHere())
    // Sized to the square, not the font setting, so it always fits [VIS-01].
    val fs = with(LocalDensity.current) { (size * if (mark.length > 1) 0.42f else 0.6f).toSp() }
    Box(modifier.size(size).clip(RoundedCornerShape(size / 4)).background(color), contentAlignment = Alignment.Center) {
        Text(mark, color = contentColorOn(color), fontSize = fs, lineHeight = fs, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
    }
}

/**
 * [EXTRA-01] One tap's worth of extra work for a day: what the chip says ("야간 대근 19–07"), the
 * name said back after it ("야간 대근"), and the piece it adds.
 */
data class ExtraChoice(val kind: ExtraKind, val label: String, val name: String, val entry: ExtraEntry)

object ExtraText {
    /** "09–18", "19:30–07:00". */
    fun span(start: LocalTime, end: LocalTime): String = "${hm(start)}–${hm(end)}"

    private fun hm(t: LocalTime) = if (t.minute == 0) "%02d".format(t.hour) else "%02d:%02d".format(t.hour, t.minute)

    /** The kind's name for a covered shift, e.g. "야간 대근"; the kind's own name when it already says so. */
    fun coverName(kind: ExtraKind, type: ShiftType?): String =
        type?.name?.takeUnless { it in kind.name }?.let { "$it ${kind.name}" } ?: kind.name

    /** The piece of a 대근 of [type]: that shift's hours, copied so a later change to the shift leaves it alone. */
    fun cover(kind: ExtraKind, type: ShiftType): ExtraEntry =
        ExtraEntry(kind.id, type.start, type.end, type.breakMinutes, type.breakStart.takeIf { type.breakMinutes > 0 }, coveredShiftId = type.id)

    /**
     * The kind for a 대근 of [type]: one made for that shift, else one for a few shifts with it
     * (모닝·나이트 대근) [EXTRA-07], else the plain 대근.
     */
    fun coverKind(kinds: ExtraKinds, types: List<ShiftType>, type: ShiftType): ExtraKind {
        val timed = types.filter { !it.isOff && it.hasTimes }
        return kinds.visible()
            .mapNotNull { k -> (k.timing as? ExtraTiming.AsShift)?.shifts(timed)?.takeIf { s -> s.any { it.id == type.id } }?.let { k to it.size } }
            .minByOrNull { it.second }?.first
            ?: kinds.of(ExtraKind.COVER_ID)
    }

    /** The piece one tap adds for a kind with fixed hours or a length. */
    fun default(kind: ExtraKind): ExtraEntry? = when (val t = kind.timing) {
        is ExtraTiming.Band -> ExtraEntry(kind.id, t.start, t.end, t.breakMinutes, t.breakStart.takeIf { t.breakMinutes > 0 })
        is ExtraTiming.AfterShift -> ExtraEntry(kind.id, minutes = t.minutes)
        is ExtraTiming.BeforeShift -> ExtraEntry(kind.id, minutes = t.minutes)
        is ExtraTiming.AsShift -> null
    }

    /**
     * Every way to add extra work on [day] with one tap, most likely first: 잔업·조출 next to a shift,
     * a 대근 of each shift (or the shift the kind is for), then fixed hours such as 특근 09–18.
     */
    fun choices(kinds: ExtraKinds, types: List<ShiftType>, day: ResolvedDay): List<ExtraChoice> {
        val timed = types.filter { !it.isOff && it.hasTimes }
        val hasShift = day.isWorkDay && day.shiftFrom != null
        val near = mutableListOf<ExtraChoice>()
        val covers = mutableListOf<ExtraChoice>()
        val bands = mutableListOf<ExtraChoice>()
        for (k in kinds.visible()) {
            when (val t = k.timing) {
                is ExtraTiming.AfterShift, is ExtraTiming.BeforeShift -> {
                    val m = (t as? ExtraTiming.AfterShift)?.minutes ?: (t as ExtraTiming.BeforeShift).minutes
                    val name = "${k.name} ${HoursFormat.korean(m)}"
                    near += ExtraChoice(k, "${k.name} ${if (hasShift) "+" else ""}${HoursFormat.korean(m)}", name, ExtraEntry(k.id, minutes = m))
                }
                is ExtraTiming.Band -> bands += ExtraChoice(k, "${k.name} ${span(t.start, t.end)}", k.name, default(k)!!)
                is ExtraTiming.AsShift -> {
                    // A kind for one shift offers that shift; one for several (모닝·나이트) or every shift
                    // offers each of them but the one the day already works [EXTRA-07].
                    val shifts = t.shiftTypeId?.let { id -> timed.filter { it.id == id } }
                        ?: t.shifts(timed).filter { !(hasShift && it.id == day.shiftType?.id) }
                    for (s in shifts) covers += ExtraChoice(k, "${coverName(k, s)} ${span(s.start!!, s.end!!)}", coverName(k, s), cover(k, s))
                    // Without shifts that have times there is nothing to copy: a day's hours to start from.
                    if (timed.isEmpty()) bands += ExtraChoice(k, "${k.name} 09–18", k.name, ExtraEntry(k.id, NINE, SIX_PM, 60, NOON))
                }
            }
        }
        return if (hasShift) near + covers + bands else covers + bands + near
    }

    /**
     * [CAL-38] The stamps quick input offers: each kind once with its usual piece (잔업 2시간, 특근 09–18), and a
     * 대근 for each shift it covers. Where each goes is [com.bamnat.core.schedule.ExtraStamp]'s rule.
     */
    fun stampChoices(kinds: ExtraKinds, types: List<ShiftType>): List<ExtraChoice> {
        val timed = types.filter { !it.isOff && it.hasTimes }
        return kinds.visible().flatMap { k ->
            when (val t = k.timing) {
                is ExtraTiming.AfterShift -> listOf(ExtraChoice(k, "${k.name} +${HoursFormat.korean(t.minutes)}", "${k.name} ${HoursFormat.korean(t.minutes)}", ExtraEntry(k.id, minutes = t.minutes)))
                is ExtraTiming.BeforeShift -> listOf(ExtraChoice(k, "${k.name} ${HoursFormat.korean(t.minutes)}", "${k.name} ${HoursFormat.korean(t.minutes)}", ExtraEntry(k.id, minutes = t.minutes)))
                is ExtraTiming.Band -> listOf(ExtraChoice(k, "${k.name} ${span(t.start, t.end)}", k.name, default(k)!!))
                is ExtraTiming.AsShift -> (t.shiftTypeId?.let { id -> timed.filter { it.id == id } } ?: t.shifts(timed))
                    .map { s -> ExtraChoice(k, coverName(k, s), coverName(k, s), cover(k, s)) }
            }
        }
    }

    private val NINE: LocalTime = LocalTime.of(9, 0)
    private val NOON: LocalTime = LocalTime.of(12, 0)
    private val SIX_PM: LocalTime = LocalTime.of(18, 0)

    /**
     * Where [day] already has the piece [choice] would add, so a chip shows it chosen and a second tap
     * takes it off: the same kind, and for a 대근 the same shift. -1 when it has none.
     */
    fun indexOf(day: ResolvedDay, choice: ExtraChoice): Int = day.extras.indexOfFirst { e ->
        e.entry.kindId == choice.entry.kindId && e.entry.coveredShiftId == choice.entry.coveredShiftId
    }

    /**
     * The piece's name to say back: "야간 대근", "잔업 2시간", "특근". With [style] 일근 the built-in 잔업 is "야근", the
     * weekday worker's own word ([J133-27]), the same on the chip, the 추가 근무 line and its rows (1.39 [WTYP-30] PC 6차).
     */
    fun name(e: ResolvedExtra, style: com.bamnat.core.work.WorkStyle? = null): String = when {
        e.covered != null -> coverName(e.kind, e.covered)
        namesHours(e) -> "${kindName(e.kind, style)} ${HoursFormat.korean(e.minutes)}"
        else -> kindName(e.kind, style)
    }

    /** The calendar's kind of work, for [name] and [kindName] where a screen has no settings at hand (1.39 [WTYP-30]). */
    @androidx.compose.runtime.Composable
    fun styleHere(): com.bamnat.core.work.WorkStyle? =
        androidx.compose.ui.platform.LocalContext.current.appContainer.settingsRepository.settings
            .collectAsStateWithLifecycle().value.workStyle

    /** [kind]'s one-letter mark: '야' for a weekday worker's built-in 잔업 still marked '잔' ([WTYP-30]). */
    fun mark(kind: ExtraKind, style: com.bamnat.core.work.WorkStyle?): String =
        if (style == com.bamnat.core.work.WorkStyle.DAY && kind.id == ExtraKind.OVERTIME_ID && kind.mark == "잔") "야" else kind.mark

    /** [kind]'s name, "야근" for a weekday worker's built-in 잔업 ([J133-27]). */
    fun kindName(kind: ExtraKind, style: com.bamnat.core.work.WorkStyle?): String = word(kind.name, kind.id, style)

    /** [text] with 잔업 said "야근" when it is the built-in 잔업 of a weekday worker ([J133-27]). */
    /** [EASE-11]·2.0.0 [RVH-07] 검토 CAL-11: what a shift on a day off is called: 대타 on an 알바 calendar, else 대근 ([J133B-01]). */
    fun coverWord(style: com.bamnat.core.work.WorkStyle?): String = if (style == com.bamnat.core.work.WorkStyle.HOURLY) "대타" else "대근"

    fun word(text: String, kindId: Long, style: com.bamnat.core.work.WorkStyle?): String =
        if (style == com.bamnat.core.work.WorkStyle.DAY && kindId == ExtraKind.OVERTIME_ID) text.replace("잔업", "야근") else text

    /** Whether [name] already says how long the piece is, as "잔업 2시간" does. */
    fun namesHours(e: ResolvedExtra): Boolean = e.covered == null && !e.entry.hasTimes

    /** "19:00 – 07:00 (다음날) · 휴게 1시간", "근무 뒤 2시간 · 19:00–21:00", "2시간 · 시각 모름". */
    fun time(e: ResolvedExtra): String {
        val from = e.from
        val to = e.to
        if (from == null || to == null) return "${HoursFormat.korean(e.minutes)} · 시각 모름"
        if (!e.entry.hasTimes && e.covered == null) {
            val where = if (e.kind.timing is ExtraTiming.BeforeShift) "근무 전" else "근무 뒤"
            return "$where ${HoursFormat.korean(e.minutes)} · ${span(from.toLocalTime(), to.toLocalTime())}"
        }
        return Fmt.timeRange(from.toLocalTime(), to.toLocalTime()) +
            if (e.breakMinutes > 0) " · 휴게 ${HoursFormat.korean(e.breakMinutes)}" else ""
    }

    /** What the law makes of the piece [EXTRA-02], in a few words, e.g. "휴무일 근무 · 8시간 넘는 3시간 연장". */
    fun kind(m: ExtraMinutes?, day: ResolvedDay): String? {
        val w = m?.minutes ?: return if (day.isRestDay) "쉬는 날 근무" else null
        return when {
            w.holidayWithin8h + w.holidayOver8h > 0 -> "휴일근로"
            w.restWithin8h + w.restOver8h > 0 -> "휴일근로로 대우"
            day.isRestDay && w.overtime > 0 && w.regular > 0 -> "휴무일 근무 · ${HoursFormat.korean(w.overtime)} 연장"
            day.isRestDay && w.overtime > 0 -> "휴무일 근무 · 연장"
            day.isRestDay -> "휴무일 근무"
            w.overtime > 0 && w.regular > 0 -> "연장 ${HoursFormat.korean(w.overtime)}"
            w.overtime > 0 -> "연장근로"
            else -> "하루 8시간·주 40시간 안"
        }
    }

    /** [RVH-58] Two pieces of extra work that both count the same [minutes] of the clock. */
    data class Twice(val first: ResolvedExtra, val second: ResolvedExtra, val minutes: Int)

    /**
     * 2.0.0 [RVH-58] 검토 PC-109: the time a piece counts, on the clock. A piece with times over the day's own shift counts only what is
     * outside the shift's parts (the resolver took the rest out, PAYX-54), so the shift is never counted twice; one wholly inside counts
     * none.
     */
    internal fun counted(e: ResolvedExtra, day: ResolvedDay): List<ClosedRange<java.time.LocalDateTime>> {
        val f = e.from ?: return emptyList()
        val t = e.to ?: return emptyList()
        if (e.minutes <= 0 || !(e.entry.hasTimes || e.covered != null)) return emptyList()
        if (e.insideShiftMinutes <= 0) return listOf(f..t)
        var pieces = listOf(f..t)
        for (p in day.segments) pieces = pieces.flatMap { r ->
            if (!r.start.isBefore(p.endInclusive) || !r.endInclusive.isAfter(p.start)) listOf(r)
            else listOfNotNull((r.start..p.start).takeIf { r.start.isBefore(p.start) }, (p.endInclusive..r.endInclusive).takeIf { p.endInclusive.isBefore(r.endInclusive) })
        }
        return pieces
    }

    /** [RVH-58] "잔업과 특근이 1시간 겹쳐 두 번 셌어요. 한 번만 일했다면 시각을 고쳐 주세요." */
    fun twiceLine(t: Twice, style: com.bamnat.core.work.WorkStyle? = null): String {
        val a = name(t.first, style)
        val b = name(t.second, style)
        return "$a${com.bamnat.core.text.Josa.gwawa(a)} $b${com.bamnat.core.text.Josa.iga(b)} ${HoursFormat.korean(t.minutes)} 겹쳐 두 번 셌어요. 한 번만 일했다면 시각을 고쳐 주세요."
    }

    /**
     * 2.0.0 [RVH-58] 검토 PC-109 (주간 07~19에 특근 09~18을 넣으면 '근무 시간 안이라 시간은 안 더해요'와 빨간 '겹친 시간도 따로따로 세니'가
     * 함께): only what the hours really count twice. Work inside the shift adds nothing and says so on its own line; two pieces of extra
     * work over the same time (잔업 18~20, 특근 19~21) are both counted, as pay and the 증빙 count them, so that is said with the minutes.
     */
    fun twice(day: ResolvedDay): Twice? {
        val pieces = day.extras.map { it to counted(it, day) }
        for (i in pieces.indices) for (j in i + 1 until pieces.size) {
            val (a, ra) = pieces[i]
            val (b, rb) = pieces[j]
            val m = ra.sumOf { x -> rb.sumOf { y ->
                if (x.start.isBefore(y.endInclusive) && y.start.isBefore(x.endInclusive))
                    java.time.Duration.between(maxOf(x.start, y.start), minOf(x.endInclusive, y.endInclusive)).toMinutes().toInt() else 0
            } }
            if (m > 0) return Twice(a, b, m)
        }
        return null
    }
}
