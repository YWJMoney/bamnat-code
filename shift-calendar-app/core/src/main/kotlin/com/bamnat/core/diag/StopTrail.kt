package com.bamnat.core.diag

import com.bamnat.core.ops.InquiryTopics
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 1.32.1 [STAB-10] (사용자 2026-10-29 "멈춘 상태를 어플이 인지해서 문의로 보고하면 해결할 수 있게"): the plain rules of the
 * 멈춤 관찰 기록 the app keeps on the phone. One line is a time, a kind ([Kind]) and a detail made only of codes and numbers
 * (화면 크기 dp, 메모리 MB, 코드 줄) — never anything a person typed. The phone keeps the newest [KEEP_LINES] of the last
 * [KEEP_DAYS] days, out of every backup; they leave the phone only inside an inquiry the person sends with 점검 기록 ticked,
 * and only once 처리방침 3.6 lists them (flag `diag.observe`).
 */
object StopTrail {
    const val KEEP_LINES = 300
    const val KEEP_DAYS = 3L
    /** How many of the newest lines a report carries. */
    const val REPORT_LINES = 40
    private const val DAY_MS = 24L * 60 * 60 * 1000

    /** What happened; the code is what is stored, the word is what a person reads. */
    enum class Kind(val code: String, val words: String) {
        START("start", "앱 시작"),
        MADE("made", "화면 새로 만듦"),
        FRONT("front", "앱 앞으로"),
        BACK("back", "앱 뒤로"),
        FOCUS("focus", "창 초점"),
        LAYOUT("layout", "화면 크기 바뀜"),
        SCREEN_OFF("off", "화면 꺼짐"),
        SCREEN_ON("on", "화면 켜짐"),
        UNLOCKED("unlock", "잠금 풀림"),
        TRIM("trim", "메모리 정리 요청"),
        MEMORY("mem", "메모리"),
        DRAW_SLOW("slowdraw", "다시 그리기 느림"),
        NO_DRAW("nodraw", "화면 안 그려짐"),
        TAPS("taps", "눌러도 화면 그대로"),
        STALL("stall", "화면 멈춤"),
        STALL_END("stall_end", "멈춤 풀림"),
        CRASH("crash", "앱 멈춤"),
        ;

        companion object {
            fun of(code: String): Kind? = entries.firstOrNull { it.code == code }
        }
    }

    /** Kinds that mean the person saw the app not answer. */
    val STUCK = setOf(Kind.STALL, Kind.NO_DRAW, Kind.TAPS)

    data class Line(val atMillis: Long, val kind: Kind, val detail: String)

    private val SAFE = Regex("[A-Za-z0-9_=,.:;/+#@$\\- ]{0,160}")

    /** "<millis>\t<code>\t<detail>"; a detail with anything but codes and numbers keeps only its length. */
    fun encode(line: Line): String = "${line.atMillis}\t${line.kind.code}\t${clean(line.detail)}"

    fun decode(raw: String): Line? {
        val parts = raw.split('\t')
        if (parts.size < 2) return null
        val at = parts[0].toLongOrNull() ?: return null
        val kind = Kind.of(parts[1]) ?: return null
        return Line(at, kind, clean(parts.getOrElse(2) { "" }))
    }

    fun clean(detail: String): String = if (SAFE.matches(detail)) detail else "text${detail.length}"

    /** The lines worth keeping: inside [KEEP_DAYS] of [now], the newest [KEEP_LINES], oldest first. */
    fun kept(lines: List<Line>, now: Long): List<Line> =
        lines.filter { now - it.atMillis < KEEP_DAYS * DAY_MS && it.atMillis <= now + DAY_MS }.sortedBy { it.atMillis }.takeLast(KEEP_LINES)

    /** How the app ended last time, by Android's own record, as the START line's `last=` code says it. */
    val EXIT_WORDS = mapOf(
        "crash" to "앱 멈춤", "anr" to "응답 없음(ANR)", "native" to "기기 쪽 멈춤", "lowmem" to "메모리 부족으로 닫힘",
        "user" to "사용자가 닫음", "swipe" to "최근 앱에서 밀어 닫음", "kill" to "시스템이 닫음", "freezer" to "멈춰 둔 채 닫힘",
        "update" to "업데이트", "resource" to "자원을 너무 씀", "other" to "그 밖의 이유",
    )

    /** "10-29 14:56:03 화면 켜짐" or "… 메모리 heap=212/256 native=80"; `last=user` reads "지난번 끝: 사용자가 닫음". */
    fun readable(line: Line, zone: ZoneId): String {
        val at = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss").format(Instant.ofEpochMilli(line.atMillis).atZone(zone))
        val detail = line.detail.split(' ').filter { it.isNotBlank() }.joinToString(" ") { part ->
            if (part.startsWith("last=")) "지난번 끝:" + (EXIT_WORDS[part.removePrefix("last=")] ?: part.removePrefix("last=")) else part
        }
        return if (detail.isBlank()) "$at ${line.kind.words}" else "$at ${line.kind.words} $detail"
    }

    /** Megabytes, rounded down. */
    fun mb(bytes: Long): Long = bytes / (1024 * 1024)

    /** "heap=212/256 native=80": the Java heap in use of its limit and the native heap, in MB. */
    fun memory(heapUsed: Long, heapMax: Long, native: Long): String = "heap=${mb(heapUsed)}/${mb(heapMax)} native=${mb(native)}"

    private val HEAP = Regex("""heap=(\d+)/(\d+)""")

    /** The heap's use in percent of its limit, read back from a line; null when the line has none. */
    fun heapPercent(detail: String): Int? {
        val m = HEAP.find(detail) ?: return null
        val max = m.groupValues[2].toLong().takeIf { it > 0 } ?: return null
        return (m.groupValues[1].toLong() * 100 / max).toInt()
    }

    private val MS = Regex("""ms=(\d+)""")

    fun millisOf(detail: String): Long? = MS.find(detail)?.groupValues?.get(1)?.toLongOrNull()

    /**
     * The times the app ended while it did not answer: a [START] whose line before it is one of [STUCK] (no 멈춤 풀림 or
     * 앞뒤 change in between). The stuck line is returned; the person likely closed the app or the phone did.
     */
    fun stuckEnds(lines: List<Line>): List<Line> =
        lines.zipWithNext().filter { (a, b) -> b.kind == Kind.START && a.kind in STUCK }.map { it.first }

    /** The newest time the app was seen stuck (멈춤, 안 그려짐, 눌러도 그대로), 0 when never. */
    fun lastStuckAt(lines: List<Line>): Long = lines.lastOrNull { it.kind in STUCK }?.atMillis ?: 0L

    /** A few lines that sum up [lines] before the lines themselves: counts, the longest freeze, the fullest heap. */
    fun summary(lines: List<Line>): List<String> {
        if (lines.isEmpty()) return listOf("기록 없음")
        fun n(k: Kind) = lines.count { it.kind == k }
        val longest = lines.filter { it.kind == Kind.STALL_END }.mapNotNull { millisOf(it.detail) }.maxOrNull()
        val stuckEnds = stuckEnds(lines).size
        val fullest = lines.mapNotNull { l -> heapPercent(l.detail)?.let { it to l } }.maxByOrNull { it.first }
        val trims = lines.filter { it.kind == Kind.TRIM }.mapNotNull { Regex("""level=(\d+)""").find(it.detail)?.groupValues?.get(1)?.toIntOrNull() }
        return buildList {
            add("최근 ${KEEP_DAYS}일: 화면 멈춤 ${n(Kind.STALL)}번" + (longest?.let { " (가장 길게 ${"%.1f".format(it / 1000.0)}초)" } ?: "") +
                " · 화면 안 그려짐 ${n(Kind.NO_DRAW)}번 · 눌러도 그대로 ${n(Kind.TAPS)}번 · 멈춘 채 끝남 ${stuckEnds}번")
            add("화면 꺼짐 ${n(Kind.SCREEN_OFF)}번 · 잠금 풀림 ${n(Kind.UNLOCKED)}번 · 화면 크기 바뀜 ${n(Kind.LAYOUT)}번 · " +
                "메모리 정리 요청 ${trims.size}번" + (trims.maxOrNull()?.let { " (가장 높은 단계 $it)" } ?: ""))
            fullest?.let { (p, l) -> add("메모리를 가장 많이 쓴 때: ${l.detail.let { d -> HEAP.find(d)?.value }} (${p}%)" + if (p >= 90) " · 거의 가득" else "") }
        }
    }

    /** [STAB-14] The words that say the app itself stopped, froze or closed (not an alarm that did not ring). */
    val STOP_WORDS = Regex("""멈|닫혀|닫힘|닫혔|꺼져|꺼짐|튕|정지|먹통|굳어|렉|강제\s*종료|응답\s*없""")

    /** What screens open 문의하기 with when the app stopped; their inquiries put 앱 멈춤 first. */
    val STOP_ABOUTS = setOf("앱이 갑자기 닫힘", "앱이 멈췄어요")

    /**
     * [STAB-14] An inquiry about the app stopping: opened from a stop's notice or card, or its words say so and are about
     * the app (not 알람이 멈췄어요). Its 점검 기록 then starts with 앱 멈춤, and the server sorts it under 앱 동작.
     */
    fun aboutStop(about: String?, text: String): Boolean =
        about in STOP_ABOUTS || STOP_ABOUTS.any { text.contains("[$it]") } ||
            (STOP_WORDS.containsMatchIn(text) && InquiryTopics.of(text) in setOf("app", InquiryTopics.OTHER))
}
