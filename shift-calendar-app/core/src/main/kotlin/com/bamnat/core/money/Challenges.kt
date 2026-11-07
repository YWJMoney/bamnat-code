package com.bamnat.core.money

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 1.38 [LEDH-31] 혼자 하는 챌린지 (조사 E2의 휴대폰 안 부분, 결정 6 '보상 없음'): 무지출 날, 배달 줄이기 주간, 야간 도시락 주간,
 * 봉투 채우기 30일. 하루마다 도장(지킴·놓침·아직), 연속 기록, 끝나면 몇 날 지켰는지. 보상은 없어요(기록을 덜 하는 게 이득이 되면
 * 가계부가 망가져요). 모두 휴대폰 안에서 셈하고, 마을 '참가 수'는 마을 방향을 정한 뒤 따로 붙여요.
 */
object Challenges {
    enum class Kind(val label: String, val days: Int, val what: String) {
        NO_SPEND("무지출 날 도전", 7, "7일 동안 반복 지출 말고는 안 쓴 날마다 도장"),
        LESS_DELIVERY("배달 줄이기 주간", 7, "7일 동안 배달에 안 쓴 날마다 도장"),
        NIGHT_LUNCHBOX("야간 도시락 주간", 14, "14일 동안 야간 날에 배달·편의점·야식을 안 사면 도장"),
        ENVELOPE_30("봉투 채우기 30일", 30, "30일 동안 봉투에 넣은 날마다 도장"),
    }

    enum class Stamp { KEPT, MISSED, LATER, NOT_COUNTED }

    data class Run(val kind: Kind, val start: LocalDate)

    data class Status(val run: Run, val stamps: List<Stamp>, val streak: Int, val kept: Int, val counted: Int, val done: Boolean,
        /** Which day of the run today is, from 1. */ val dayNo: Int = 1) {
        val end: LocalDate get() = run.start.plusDays(run.kind.days - 1L)
    }

    /** What 야간 도시락 watches: food bought out. */
    val OUT_FOOD = setOf("배달", "편의점", "야식")

    /** "NO_SPEND|20760" in the settings; null when it does not read. */
    fun decode(text: String): Run? {
        val (k, d) = text.split('|').takeIf { it.size == 2 } ?: return null
        val kind = Kind.entries.firstOrNull { it.name == k } ?: return null
        return Run(kind, LocalDate.ofEpochDay(d.toLongOrNull() ?: return null))
    }

    fun encode(run: Run?): String = run?.let { "${it.kind.name}|${it.start.toEpochDay()}" }.orEmpty()

    /**
     * Each day of [run] up to [today]: kept or missed, a day still going ([today], when not yet missed) as later, and for 야간
     * 도시락 a day that is not a night as not counted ([isNight]).
     */
    fun status(run: Run, entries: List<MoneyEntry>, moves: List<EnvelopeMove>, today: LocalDate, isNight: (LocalDate) -> Boolean = { false }): Status {
        val days = (0 until run.kind.days).map { run.start.plusDays(it.toLong()) }
        val spend = entries.filter { it.kind == MoneyKind.EXPENSE && it.amount > 0 }.groupBy { it.date }
        val putIn = moves.filter { it.amount > 0 }.map { it.date }.toSet()
        val stamps = days.map { d ->
            if (d.isAfter(today)) return@map Stamp.LATER
            val lines = spend[d].orEmpty()
            val missed = when (run.kind) {
                Kind.NO_SPEND -> lines.any { it.source != MoneySource.FIXED && it.fixedId == null }
                Kind.LESS_DELIVERY -> lines.any { it.category == "배달" }
                Kind.NIGHT_LUNCHBOX -> if (!isNight(d)) return@map Stamp.NOT_COUNTED else lines.any { it.category in OUT_FOOD }
                Kind.ENVELOPE_30 -> d !in putIn
            }
            when {
                // 봉투는 넣은 날이 지킨 날; 나머지는 하루가 끝나야 지킨 날이고, 오늘 이미 어겼으면 놓친 날이에요.
                run.kind == Kind.ENVELOPE_30 -> if (!missed) Stamp.KEPT else if (d == today) Stamp.LATER else Stamp.MISSED
                missed -> Stamp.MISSED
                d == today -> Stamp.LATER
                else -> Stamp.KEPT
            }
        }
        val counted = stamps.filter { it == Stamp.KEPT || it == Stamp.MISSED }
        var streak = 0
        for (s in stamps.filter { it != Stamp.NOT_COUNTED && it != Stamp.LATER }.asReversed()) { if (s == Stamp.KEPT) streak++ else break }
        return Status(run, stamps, streak, counted.count { it == Stamp.KEPT }, counted.size, ChronoUnit.DAYS.between(run.start, today) >= run.kind.days,
            (ChronoUnit.DAYS.between(run.start, today) + 1).toInt().coerceIn(1, run.kind.days))
    }

    /**
     * 1.39 G8 발굴 [LEDH-31] 칩에서 도전으로: when 근무별 돈 ([ShiftMoneyChips]) says a night (or the day after one) buys more food out,
     * 야간 도시락 주간 is offered right under it; when it says 배달 goes up on some kind of day, 배달 줄이기 주간. Nothing while a run
     * is going ([active], until its last day has gone by [today]) or when the chips say neither. [isNight] tells a chip's day kind is a night ([TaxPlus] labels).
     */
    fun suggest(rows: ShiftMoneyChips.Rows, active: Run?, today: LocalDate, isNight: (String) -> Boolean): Kind? {
        if (active != null && !today.isAfter(active.start.plusDays(active.kind.days - 1L))) return null
        val more = rows.caution
        return when {
            more.any { isNight(it.dayKind) && it.category in OUT_FOOD } -> Kind.NIGHT_LUNCHBOX
            more.any { it.category == "배달" } -> Kind.LESS_DELIVERY
            else -> null
        }
    }

    /** The line under the stamps. */
    fun words(s: Status): String = when {
        s.done -> "끝났어요. ${s.counted}일 중 ${s.kept}일 지켰어요" + if (s.kept == s.counted && s.counted > 0) ". 다 지켰어요!" else ""
        // 1.38 PC 1차 ('0일 중 0일'): before the first day is stamped, which day it is and when the stamp comes.
        s.counted == 0 -> "오늘 ${s.dayNo}일째 · " + when (s.run.kind) {
            Kind.ENVELOPE_30 -> "봉투에 넣으면 도장"
            Kind.NIGHT_LUNCHBOX -> "야간 날 밤 12시에 도장"
            else -> "밤 12시에 도장"
        } + " · ${s.end.monthValue}/${s.end.dayOfMonth}까지"
        s.streak >= 2 -> "${s.streak}일째 이어 가는 중 · ${s.counted}일 중 ${s.kept}일"
        else -> "${s.counted}일 중 ${s.kept}일 지켰어요 · ${s.end.monthValue}/${s.end.dayOfMonth}까지"
    }
}
