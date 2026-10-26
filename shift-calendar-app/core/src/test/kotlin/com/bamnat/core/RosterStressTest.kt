package com.bamnat.core

import com.bamnat.core.imports.*
import kotlin.math.*
import kotlin.random.Random
import org.junit.Test

/**
 * [READ-01][READ-02] 1.25 통합 직접 시험 (사용자 2026-10-25 "사진 인식은 어떤 양식이든"): 800가지 듀티표 사진을 흉내 내요 —
 * 기울기 −3°~4°, 코드 넷(D·E·N·O, 데이·이브닝·나이트·오프, 주·야·비·휴, 데·이·나·휴), 요일 줄, 이름이 왼쪽 또는 오른쪽 끝,
 * 글자 크기 0.6~1.6배, 글자 위치 흔들림, 3~7명, 30·31일. 글자 인식이 깨끗하면 모든 칸이 맞아야 하고, 글자를 잘못 읽거나
 * 빠뜨린 사진(칸의 6%를 비슷한 글자로, 4%를 지움)에서는 틀린 칸이 하나도 '확인 없이' 들어가면 안 돼요(노란 칸·물음으로 남음).
 */
class RosterStressTest {
    private val types = listOf(DutyType(1, "데이", "D", false), DutyType(2, "이브닝", "E", false),
        DutyType(3, "나이트", "N", false, night = true), DutyType(4, "휴무", "O", true))
    private val codeSets = mapOf(
        "DEN" to listOf("D", "E", "N", "O"),
        "kor" to listOf("데이", "이브닝", "나이트", "오프"),
        "police" to listOf("주", "야", "비", "휴"),
        "short" to listOf("데", "이", "나", "휴"),
    )
    private val confuse = mapOf("O" to "0", "D" to "0", "E" to "F", "N" to "H", "데이" to "대이", "이브닝" to "이브님", "나이트" to "나이드",
        "오프" to "오 프", "주" to "추", "야" to "아", "비" to "미", "휴" to "후", "데" to "대", "이" to "01", "나" to "니")
    data class Case(val words: List<RosterWord>, val truth: Map<String, Map<Int, Int>>)

    private fun make(r: Random, noise: Boolean, dropped: MutableSet<String>, deg: Double, set: List<String>, weekdays: Boolean, jitter: Int, names: Int, nameRight: Boolean, days: Int, scale: Double): Case {
        val words = mutableListOf<RosterWord>()
        val a = Math.toRadians(deg)
        val cell = (60 + r.nextInt(50)) * scale; val rowH = (50 + r.nextInt(40)) * scale; val h = (26 * scale).toInt()
        fun add(t: String, x: Double, y: Double, w: Int) {
            val jj = (jitter * scale).toInt(); val jx = if (jj > 0) r.nextInt(-jj, jj + 1) else 0; val jy = if (jj > 0) r.nextInt(-jj, jj + 1) else 0
            val cx = x + w / 2.0 + jx; val cy = y + h / 2.0 + jy
            val rx = cx * cos(a) - cy * sin(a) + 400; val ry = cx * sin(a) + cy * cos(a) + 400
            words += RosterWord(t, (rx - w / 2).toInt(), (ry - h / 2).toInt(), (rx + w / 2).toInt(), (ry + h / 2).toInt())
        }
        val left = if (nameRight) 40.0 else 260 * scale
        add("2026년 10월 근무표", left + 300, 0.0, (260 * scale).toInt())
        for (d in 1..days) add("$d", left + (d - 1) * cell, 80 * scale, ((if (d < 10) 14 else 26) * scale).toInt())
        val wd = listOf("목", "금", "토", "일", "월", "화", "수")
        if (weekdays) for (d in 1..days) add(wd[(d - 1) % 7], left + (d - 1) * cell, 80 * scale + rowH * 0.7, (16 * scale).toInt())
        val first = 80 * scale + rowH * (if (weekdays) 1.6 else 1.0)
        val pool = listOf("김민지", "이수진", "박하늘", "최유리", "정다은", "한서연", "오지훈", "윤가람")
        val truth = mutableMapOf<String, Map<Int, Int>>()
        for (i in 0 until names) {
            val n = pool[i]; val y = first + i * rowH
            add(n, if (nameRight) left + days * cell + 30 else 40.0, y, (70 * scale).toInt())
            val m = mutableMapOf<Int, Int>()
            for (d in 1..days) {
                val k = r.nextInt(4); m[d] = k
                var t = set[k]
                if (noise && r.nextInt(100) < 4) { dropped += "$n/$d"; continue }
                if (noise && r.nextInt(100) < 6) t = (confuse[t] ?: t)
                add(t, left + (d - 1) * cell, y, (max(14, t.length * 14) * scale).toInt())
            }
            truth[n] = m
        }
        return Case(words.shuffled(r), truth)
    }

    @Test fun stress() {
        val r = Random(7)
        val codes = DutyCodes(types)
        var cleanPerfect = 0; var silentWrong = 0
        for (noise in listOf(false, true)) repeat(400) { n ->
            val deg = listOf(0.0, 0.5, -1.0, 2.0, -3.0, 4.0, 1.5)[n % 7]
            val setName = codeSets.keys.toList()[n / 7 % 4]
            val weekdays = n % 3 == 0; val jitter = listOf(0, 2, 4)[n / 3 % 3]; val names = 3 + n % 5
            val nameRight = n % 11 == 0; val days = if (n % 13 == 0) 30 else 31; val scale = listOf(0.6, 1.0, 1.6)[n / 5 % 3]
            val c = make(r, noise, mutableSetOf(), deg, codeSets.getValue(setName), weekdays, jitter, names, nameRight, days, scale)
            val rows = DutyRoster.read(c.words, codes)
            var ok = true
            for ((name, truth) in c.truth) {
                val row = rows.firstOrNull { it.label.contains(name) }
                for ((d, k) in truth) {
                    // 비(비번) is a day off after a night, as 휴.
                    val off = k == 3 || setName == "police" && k == 2
                    val want = if (setName == "police" && k == 1) 3L else (k + 1).toLong()
                    val got = row?.cells?.get(d)
                    val cellOk = when (got) {
                        is DutyCell.Shift -> if (off) got.shiftTypeId == 4L else got.shiftTypeId == want
                        is DutyCell.Off -> off
                        else -> false
                    }
                    if (!cellOk) ok = false
                    // Wrong without a sign: a different kind on a day the reader did not mark for a look.
                    if (!cellOk && got != null && got !is DutyCell.Unknown && row != null && d !in row.doubtful(days)) {
                        silentWrong++
                    }
                }
            }
            if (ok && !noise) cleanPerfect++
        }
        org.junit.Assert.assertEquals("clean photos: every cell of every line", 400, cleanPerfect)
        org.junit.Assert.assertEquals("OCR mistakes never go in quietly", 0, silentWrong)
    }

    /**
     * [READ-16] 1.27 AJ 순번 446: 400 photos read 4 ways each ([PhotoVariants]), every way with its own misreads — a letter
     * like another (6%), a cell lost (5%), a Korean code read in the other alphabet ("OF" for 야, 3%) — plus 원근(뒤쪽 칸이
     * 점점 넓어짐) and small print. Put together ([RosterVote]): photos read clean every way are all right, and with misreads no
     * wrong cell goes in without yellow (조용히 틀린 값 0). Prints how many cells one reading and the put-together get right.
     */
    @Test fun fourReadingsPutTogether() {
        val codes = DutyCodes(types)
        var cleanPerfect = 0; var cleanCases = 0; var silentWrong = 0; var onceRight = 0; var voteRight = 0; var total = 0
        repeat(400) { n ->
            val noise = n % 4 != 0
            val layout = Random(1000 + n)
            val setName = codeSets.keys.toList()[n % 4]
            val set = codeSets.getValue(setName)
            val days = if (n % 9 == 0) 30 else 31
            val names = 3 + n % 4
            val scale = listOf(0.6, 1.0, 1.4)[n / 4 % 3]
            val bend = if (n % 5 == 0) 0.000012 / scale else 0.0
            val deg = listOf(0.0, 1.0, -2.0, 3.0)[n / 3 % 4]
            val truth = (0 until names).associateWith { (1..days).associateWith { layout.nextInt(4) } }
            val cell = (60 + layout.nextInt(40)) * scale; val rowH = (50 + layout.nextInt(30)) * scale; val h = (26 * scale).toInt()
            val reads = (0 until 4).map { way ->
                val r = Random(n * 31 + way)
                val words = mutableListOf<RosterWord>()
                val a = Math.toRadians(deg)
                fun add(t: String, x0: Double, y: Double, w: Int) {
                    val x = x0 + bend * x0 * x0
                    val cx = x + w / 2.0 + r.nextInt(-2, 3); val cy = y + h / 2.0 + r.nextInt(-2, 3)
                    val rx = cx * cos(a) - cy * sin(a) + 400; val ry = cx * sin(a) + cy * cos(a) + 400
                    words += RosterWord(t, (rx - w / 2).toInt(), (ry - h / 2).toInt(), (rx + w / 2).toInt(), (ry + h / 2).toInt())
                }
                val left = 260 * scale
                add("2026년 10월 근무표", left + 300, 0.0, (260 * scale).toInt())
                for (d in 1..days) add("$d", left + (d - 1) * cell, 80 * scale, ((if (d < 10) 14 else 26) * scale).toInt())
                truth.forEach { (i, m) ->
                    val y = 80 * scale + rowH * (1 + i)
                    add(listOf("김민지", "이수진", "박하늘", "최유리", "정다은", "한서연")[i], 40.0, y, (70 * scale).toInt())
                    m.forEach { (d, k) ->
                        var t = set[k]
                        if (noise && r.nextInt(100) < 5) return@forEach
                        if (noise && r.nextInt(100) < 6) t = confuse[t] ?: t
                        else if (noise && setName != "DEN" && r.nextInt(100) < 3) t = listOf("OF", "D", "N", "O")[r.nextInt(4)]
                        add(t, left + (d - 1) * cell, y, (max(14, t.length * 14) * scale).toInt())
                    }
                }
                DutyRoster.analyse(words.shuffled(r), codes)
            }
            val voted = RosterVote.merge(reads)
            fun right(row: RosterRow?, d: Int, k: Int): Boolean {
                val off = k == 3 || setName == "police" && k == 2
                val want = if (setName == "police" && k == 1) 3L else (k + 1).toLong()
                return when (val got = row?.cells?.get(d)) {
                    is DutyCell.Shift -> if (off) got.shiftTypeId == 4L else got.shiftTypeId == want
                    is DutyCell.Off -> off
                    else -> false
                }
            }
            var ok = true
            truth.forEach { (i, m) ->
                val label = listOf("김민지", "이수진", "박하늘", "최유리", "정다은", "한서연")[i]
                val once = reads[0].rows.firstOrNull { it.label.contains(label) }
                val row = voted.rows.firstOrNull { it.label.contains(label) }
                m.forEach { (d, k) ->
                    total++
                    if (right(once, d, k) && (once == null || d !in once.doubtful(days))) onceRight++
                    val good = right(row, d, k)
                    if (good && row != null && d !in row.doubtful(days)) voteRight++
                    if (!good) ok = false
                    val got = row?.cells?.get(d)
                    if (!good && got != null && got !is DutyCell.Unknown && d !in row.doubtful(days)) silentWrong++
                }
            }
            if (!noise) { cleanCases++; if (ok) cleanPerfect++ }
        }
        println("roster 4-way stress: cells=$total onceSure=$onceRight voteSure=$voteRight silentWrong=$silentWrong clean=$cleanPerfect/$cleanCases")
        org.junit.Assert.assertEquals("clean photos read 4 ways: every cell", cleanCases, cleanPerfect)
        org.junit.Assert.assertEquals("misreads never go in quietly after putting together", 0, silentWrong)
        org.junit.Assert.assertTrue("putting together gets more cells sure than one reading", voteRight > onceRight)
    }
}
