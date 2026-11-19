package com.bamnat.core.stats

import com.bamnat.core.diag.AppFrames

/**
 * 2.0.0 [RVL-90] (검토 I12c-03 '멈춤 신호를 문의 없이 개발로', 처리방침 3.14 문장표 6 '앱이 멈춘 곳 횟수', 1-9항 ⑩, 깃발
 * `stats.crash`). 문의를 쓰는 사람은 일부라, 사용 통계를 켠 휴대폰만 앱이 닫히거나 응답이 없었던 곳을 하루 횟수로 실어요.
 *
 * What a mark is, as 3.14 says it and no more: the kind (닫힘 [CRASH] or 응답 없음 [ANR]), where in the app's own code it
 * happened (its top [FRAMES] lines of ours, [AppFrames.ours], each "class.method:line": a renamed name in the release build,
 * which the build's mapping turns back), a short value of that place ([sig]) and how many times that day. No message, no
 * phone model, no install id, no time of day; at most [PER_DAY] places a day, the most frequent kept.
 */
object CrashMarks {
    const val CRASH = "crash"
    const val ANR = "anr"
    val KINDS = setOf(CRASH, ANR)

    /** Lines of ours a mark keeps (3.14: '위 다섯 줄'). */
    const val FRAMES = 5
    /** Places a day (3.14: '하루 5가지까지'). */
    const val PER_DAY = 5
    /** A line longer than this is cut (a renamed line is short; a named one is a class and a method). */
    const val MAX_LINE = 120
    /** Days a mark waits for the bundle, like [UsageBook]'s week. */
    const val KEEP_DAYS = 7L
    /** One place's count a day goes no higher (a loop of stops is one bug). */
    const val MAX_COUNT = 50

    data class Mark(val day: Long, val kind: String, val at: List<String>, val count: Int) {
        val sig: String get() = sig(kind, at)
    }

    /** The top [FRAMES] lines of ours of a stop, root cause first: "PayViewModel.save:212" or a renamed "a.b.c.d:12". */
    fun at(error: Throwable): List<String> {
        var root: Throwable = error
        val seen = HashSet<Throwable>()
        while (root.cause != null && seen.add(root)) root = root.cause!!
        return root.stackTrace.filter { AppFrames.ours(it.className) }
            .map { AppFrames.short(it.className, it.methodName, it.lineNumber).take(MAX_LINE) }.take(FRAMES)
    }

    /**
     * The lines of ours of Android's ANR trace for the main thread ("at com.bamnat.app.X.y(X.kt:12)" or "at a.b.c(SourceFile:3)"),
     * written the same way as [at].
     */
    fun atTrace(lines: Sequence<String>): List<String> =
        lines.dropWhile { !it.startsWith("\"main\"") }.drop(1).takeWhile { it.isNotBlank() }
            .filter { it.trimStart().startsWith("at ") }
            .mapNotNull { raw ->
                val t = raw.trim().removePrefix("at ")
                val call = t.substringBefore('(')
                val cls = call.substringBeforeLast('.')
                if (!AppFrames.ours(cls)) return@mapNotNull null
                val line = t.substringAfter('(', "").substringBefore(')').substringAfterLast(':', "").toIntOrNull() ?: -1
                AppFrames.short(cls, call.substringAfterLast('.'), line).take(MAX_LINE)
            }.take(FRAMES).toList()

    /** 8 hex characters of the place: the same kind and lines on any phone give the same value, nothing else goes in. */
    fun sig(kind: String, at: List<String>): String {
        var h = 0xcbf29ce484222325uL
        for (b in (kind + "\n" + at.joinToString("\n")).encodeToByteArray()) {
            h = h xor b.toUByte().toULong()
            h *= 0x100000001b3uL
        }
        return (h xor (h shr 32)).toUInt().toString(16).padStart(8, '0')
    }

    /** [marks] with one more stop of [kind] at [at] on [day]: same place adds up, a day keeps its [PER_DAY] most frequent. */
    fun add(marks: List<Mark>, day: Long, kind: String, at: List<String>): List<Mark> {
        if (kind !in KINDS || at.isEmpty()) return marks
        val i = marks.indexOfFirst { it.day == day && it.kind == kind && it.at == at }
        val next = if (i >= 0) marks.toMutableList().also { it[i] = it[i].copy(count = (it[i].count + 1).coerceAtMost(MAX_COUNT)) }
        else marks + Mark(day, kind, at, 1)
        return next.groupBy { it.day }.toSortedMap().values.flatMap { d -> d.sortedByDescending { it.count }.take(PER_DAY) }
            .filter { it.day > day - KEEP_DAYS }
    }

    /** Marks of days before [today] (a day still running waits), oldest first. */
    fun due(marks: List<Mark>, today: Long): List<Mark> = marks.filter { it.day < today }

    /** One mark per line: "day\tkind\tcount\tline|line". */
    fun encode(marks: List<Mark>): String = marks.joinToString("\n") { "${it.day}\t${it.kind}\t${it.count}\t${it.at.joinToString("|")}" }

    fun decode(text: String?, keepFrom: Long): List<Mark> = text.orEmpty().lineSequence().mapNotNull { line ->
        val p = line.split('\t')
        if (p.size != 4) return@mapNotNull null
        val day = p[0].toLongOrNull()?.takeIf { it >= keepFrom } ?: return@mapNotNull null
        val kind = p[1].takeIf { it in KINDS } ?: return@mapNotNull null
        val count = p[2].toIntOrNull()?.takeIf { it in 1..MAX_COUNT } ?: return@mapNotNull null
        val at = p[3].split('|').filter { it.isNotBlank() }.map { it.take(MAX_LINE) }.take(FRAMES)
        if (at.isEmpty()) null else Mark(day, kind, at, count)
    }.toList()
}
