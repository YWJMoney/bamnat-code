package com.bamnat.core

import com.bamnat.core.pay.OcrLine
import com.bamnat.core.pay.SlipRole
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * [PHOTO-07] 1.29 AQ: Korean payslips in the shapes companies print them (24 꼴), as a phone's ML Kit gives them back: each word
 * (element) with its box, the line it was on with its slope, the photo tilted, boxes a few pixels off, some names a letter off
 * (아간·군로·보협·국민연굼), some digits misread (3↔8, 0↔6, O for 0), commas read as dots, a 원 lost, two words read as one or one
 * as two, now and then a word lost. Every page carries its truth: each line's kind, name and amount, the totals.
 *
 * ML Kit itself does not run on a computer; what it hands the app (words, boxes, slope) is what is made here, and both what the
 * app read in 1.28 (lines joined into text) and what it reads now (the table from where words stand) are scored on it.
 */
object SlipCorpus {
    data class Item(val name: String, val role: SlipRole, val amount: Long, val minutes: Int? = null)

    data class Truth(val earnings: List<Item>, val deductions: List<Item>, val gross: Long, val dedTotal: Long, val net: Long, val extra: List<Item> = emptyList()) {
        val all: List<Item> get() = earnings + deductions + extra + listOf(
            Item("지급합계", SlipRole.GROSS, gross), Item("공제합계", SlipRole.DEDUCTION_TOTAL, dedTotal), Item("실지급액", SlipRole.NET, net),
        )
    }

    /** One printed word on the page, level, in pixels: [x] is its left edge. */
    data class Word(val text: String, val x: Double, val y: Double, val h: Double)

    data class Page(val layout: String, val words: List<Word>, val truth: Truth, val lineGap: Double = 1.6)

    data class Noise(val tilt: Double, val digit: Double, val comma: Double, val name: Double, val glue: Double, val lose: Double, val won: Double) {
        companion object {
            val CLEAN = Noise(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
        }
    }

    // ---- what the page says ----

    private val NAME_SLIPS = listOf(
        "야간" to "아간", "근로" to "군로", "휴일" to "퓨일", "보험" to "보협", "연금" to "연굼", "합계" to "함계", "공제" to "공재",
        "요양" to "오양", "소득" to "소독", "지급" to "지금", "연장" to "언장", "수당" to "수딩", "식대" to "식데", "교통" to "교퉁",
        "직책" to "직잭", "근속" to "근솎", "자격" to "자걱", "위험" to "위헙", "교대" to "교데", "기본급" to "기본려",
    )

    fun width(c: Char, h: Double): Double = h * when {
        c in '가'..'힣' -> 1.0
        c == ',' || c == '.' -> 0.3
        c == ' ' -> 0.35
        c.isLetterOrDigit() -> 0.58
        else -> 0.5
    }

    fun width(s: String, h: Double): Double = s.sumOf { width(it, h) }

    fun money(v: Long, style: String = "comma"): String = when (style) {
        "dot" -> "%,d".format(v).replace(',', '.')
        "plain" -> v.toString()
        "won" -> "%,d원".format(v)
        "wonSpace" -> "%,d 원".format(v)
        "paren" -> if (v < 0) "(%,d)".format(-v) else "%,d".format(v)
        "tri" -> if (v < 0) "△%,d".format(-v) else "%,d".format(v)
        else -> if (v < 0) "-%,d".format(-v) else "%,d".format(v)
    }

    // ---- truth ----

    fun truth(r: Random, family: String): Truth {
        val earn = mutableListOf<Item>()
        fun add(name: String, role: SlipRole, amount: Long, minutes: Int? = null, p: Int = 100) { if (r.nextInt(100) < p) earn += Item(name, role, amount, minutes) }
        val base = 2_000_000L + r.nextInt(40) * 25_000
        when (family) {
            "public" -> {
                add("봉급", SlipRole.BASE, base)
                add("정액급식비", SlipRole.ALLOWANCE, 140_000)
                add("시간외근무수당", SlipRole.OVERTIME, 100_000L + r.nextInt(300) * 1000, 60 * (5 + r.nextInt(30)))
                add("직급보조비", SlipRole.ALLOWANCE, 175_000, p = 70)
                add("가족수당", SlipRole.ALLOWANCE, 40_000L * (1 + r.nextInt(3)), p = 50)
            }
            "hospital" -> {
                add("기본급", SlipRole.BASE, base)
                add("야간근로수당", SlipRole.NIGHT, 150_000L + r.nextInt(400) * 1000, 60 * (16 + r.nextInt(50)))
                add("휴일근로수당", SlipRole.HOLIDAY, 80_000L + r.nextInt(200) * 1000, 60 * (8 + r.nextInt(16)), p = 80)
                add("간호수당", SlipRole.ALLOWANCE, 100_000)
                add("위험수당", SlipRole.ALLOWANCE, 50_000, p = 60)
                add("교대수당", SlipRole.ALLOWANCE, 10_000L * (8 + r.nextInt(10)), p = 70)
                add("식대", SlipRole.ALLOWANCE, 200_000)
            }
            "hourly" -> {
                val hours = 120 + r.nextInt(60)
                val rate = 10_030L + r.nextInt(30) * 100
                add("기본급", SlipRole.BASE, hours * rate, hours * 60)
                add("주휴수당", SlipRole.WEEKLY_HOLIDAY, rate * 8 * 4)
                add("연장근로수당", SlipRole.OVERTIME, (rate * 15 / 10) * (2 + r.nextInt(10)), p = 70)
                add("교통비", SlipRole.ALLOWANCE, 50_000, p = 50)
            }
            "bonus" -> {
                add("기본급", SlipRole.BASE, base)
                add("상여금", SlipRole.BONUS, base)
                add("명절상여", SlipRole.BONUS, 500_000, p = 60)
                add("연장근로수당", SlipRole.OVERTIME, 100_000L + r.nextInt(500) * 1000)
                add("근속수당", SlipRole.ALLOWANCE, 50_000)
                add("식대", SlipRole.ALLOWANCE, 200_000)
            }
            "dense" -> {
                add("기본급", SlipRole.BASE, base)
                add("연장근로수당", SlipRole.OVERTIME, 100_000L + r.nextInt(500) * 1000, 60 * (5 + r.nextInt(30)))
                add("야간근로수당", SlipRole.NIGHT, 50_000L + r.nextInt(300) * 1000, 60 * (8 + r.nextInt(40)))
                add("휴일근로수당", SlipRole.HOLIDAY, 80_000L + r.nextInt(300) * 1000)
                add("주휴수당", SlipRole.WEEKLY_HOLIDAY, 300_000L + r.nextInt(100) * 1000, p = 40)
                add("식대", SlipRole.ALLOWANCE, 200_000)
                add("교통비", SlipRole.ALLOWANCE, 100_000)
                add("직책수당", SlipRole.ALLOWANCE, 150_000)
                add("근속수당", SlipRole.ALLOWANCE, 30_000L * (1 + r.nextInt(4)))
                add("자격수당", SlipRole.ALLOWANCE, 50_000)
                add("위험수당", SlipRole.ALLOWANCE, 70_000)
                add("교대수당", SlipRole.ALLOWANCE, 10_000L * (5 + r.nextInt(10)))
            }
            else -> {
                add("기본급", SlipRole.BASE, base)
                add(listOf("연장근로수당", "연장수당", "시간외수당")[r.nextInt(3)], SlipRole.OVERTIME, 100_000L + r.nextInt(900) * 1000, 60 * (5 + r.nextInt(40)), p = 85)
                add(listOf("야간근로수당", "야간수당")[r.nextInt(2)], SlipRole.NIGHT, 50_000L + r.nextInt(500) * 1000, 60 * (10 + r.nextInt(60)), p = 75)
                add(listOf("휴일근로수당", "휴일수당")[r.nextInt(2)], SlipRole.HOLIDAY, 80_000L + r.nextInt(300) * 1000, 60 * (8 + r.nextInt(16)), p = 60)
                add("식대", SlipRole.ALLOWANCE, 200_000, p = 80)
                add(listOf("교대수당", "자격수당", "직책수당", "근속수당", "위험수당")[r.nextInt(5)], SlipRole.ALLOWANCE, 30_000L + r.nextInt(20) * 10_000, p = 80)
                add("교통비", SlipRole.ALLOWANCE, 100_000, p = 30)
            }
        }
        val gross = earn.sumOf { it.amount }
        val ded = mutableListOf(
            Item(if (family == "public") "일반기여금" else "국민연금", SlipRole.DEDUCTION, gross * 45 / 1000 / 10 * 10),
            Item("건강보험", SlipRole.DEDUCTION, gross * 3545 / 100000 / 10 * 10),
            Item(if (r.nextBoolean()) "장기요양보험" else "장기요양", SlipRole.DEDUCTION, gross * 3545 / 100000 * 1295 / 10000 / 10 * 10),
            Item("고용보험", SlipRole.DEDUCTION, gross * 9 / 1000 / 10 * 10),
            Item("소득세", SlipRole.DEDUCTION, 30_000L + r.nextInt(150) * 1000 + r.nextInt(10) * 10),
        )
        ded += Item("지방소득세", SlipRole.DEDUCTION, ded.last().amount / 10 / 10 * 10)
        val dedTotal = ded.sumOf { it.amount }
        return Truth(earn, ded, gross, dedTotal, gross - dedTotal)
    }

    // ---- layouts ----

    val LAYOUTS = listOf(
        "list", "side", "three", "headRow", "stacked", "colon", "hours", "noteAfter", "formulaInline", "wonSpace", "dotted", "plain",
        "negative", "attendTop", "public", "hospital", "hourly", "leaders", "numbered", "netTop", "sectionCell", "spaced", "bonus", "dense",
    )

    private fun familyOf(layout: String) = when (layout) {
        "public" -> "public"
        "hospital" -> "hospital"
        "hourly" -> "hourly"
        "bonus" -> "bonus"
        "dense" -> "dense"
        else -> "general"
    }

    fun page(layout: String, seed: Int): Page {
        val r = Random(seed * 31 + LAYOUTS.indexOf(layout))
        var t = truth(r, familyOf(layout))
        if (layout == "negative") {
            // 연말정산 환급: a negative 공제 line.
            val refund = -(10_000L + r.nextInt(80) * 1000)
            val ded = t.deductions + Item("연말정산", SlipRole.DEDUCTION, refund)
            val dt = ded.sumOf { it.amount }
            t = t.copy(deductions = ded, dedTotal = dt, net = t.gross - dt)
        }
        val b = PageBuilder(if (layout == "dense") 24.0 else 30.0)
        val style = when (layout) {
            "wonSpace" -> "wonSpace"; "dotted" -> "dot"; "plain" -> "plain"; "negative" -> if (seed % 2 == 0) "paren" else "tri"
            else -> if (seed % 3 == 0) "won" else "comma"
        }
        fun m(v: Long) = money(v, style)
        b.title("2026년 ${1 + seed % 12}월분 급여명세서")
        b.row(listOf(Cell("성명", 120.0), Cell("홍길동", 250.0), Cell("부서", 820.0), Cell("생산1팀", 950.0)))
        val e = t.earnings
        val d = t.deductions
        val gross = "지급합계"
        val dedT = if (seed % 2 == 0) "공제합계" else "공제총액"
        val net = if (seed % 2 == 0) "실지급액" else "차인지급액"
        when (layout) {
            "list", "wonSpace", "dotted", "plain", "public", "bonus" -> {
                b.row(listOf(Cell("지급항목", 120.0), Cell("금액", 820.0, right = true)))
                e.forEach { b.row(listOf(Cell(it.name, 120.0), Cell(m(it.amount), 900.0, right = true))) }
                b.row(listOf(Cell(gross, 120.0), Cell(m(t.gross), 900.0, right = true)))
                b.row(listOf(Cell("공제항목", 120.0)))
                d.forEach { b.row(listOf(Cell(it.name, 120.0), Cell(m(it.amount), 900.0, right = true))) }
                b.row(listOf(Cell(dedT, 120.0), Cell(m(t.dedTotal), 900.0, right = true)))
                b.row(listOf(Cell(net, 120.0), Cell(m(t.net), 900.0, right = true)))
            }
            "side", "negative", "hospital" -> {
                b.row(listOf(Cell("지급항목", 120.0), Cell("금액", 700.0, right = true), Cell("공제항목", 820.0), Cell("금액", 1450.0, right = true)))
                for (i in 0 until maxOf(e.size, d.size)) b.row(listOfNotNull(
                    e.getOrNull(i)?.let { Cell(it.name, 120.0) }, e.getOrNull(i)?.let { Cell(m(it.amount), 700.0, right = true) },
                    d.getOrNull(i)?.let { Cell(it.name, 820.0) }, d.getOrNull(i)?.let { Cell(m(it.amount), 1450.0, right = true) },
                ))
                b.row(listOf(Cell(if (seed % 4 == 1) "계" else gross, 120.0), Cell(m(t.gross), 700.0, right = true),
                    Cell(if (seed % 4 == 1) "계" else dedT, 820.0), Cell(m(t.dedTotal), 1450.0, right = true)))
                b.row(listOf(Cell(net, 820.0), Cell(m(t.net), 1450.0, right = true)))
            }
            "three" -> {
                b.row(listOf(Cell("지급내역", 80.0), Cell("공제내역", 600.0), Cell("근태", 1150.0)))
                val att = listOf("근로일수" to "22일", "총근로시간" to "209", "연장근로시간" to "${(e.firstOrNull { it.role == SlipRole.OVERTIME }?.minutes ?: 0) / 60}",
                    "야간근로시간" to "${(e.firstOrNull { it.role == SlipRole.NIGHT }?.minutes ?: 0) / 60}")
                for (i in 0 until maxOf(e.size, d.size, att.size)) b.row(listOfNotNull(
                    e.getOrNull(i)?.let { Cell(it.name, 80.0) }, e.getOrNull(i)?.let { Cell(m(it.amount), 520.0, right = true) },
                    d.getOrNull(i)?.let { Cell(it.name, 600.0) }, d.getOrNull(i)?.let { Cell(m(it.amount), 1050.0, right = true) },
                    att.getOrNull(i)?.let { Cell(it.first, 1150.0) }, att.getOrNull(i)?.let { Cell(it.second, 1520.0, right = true) },
                ))
                b.row(listOf(Cell(gross, 80.0), Cell(m(t.gross), 520.0, right = true), Cell(dedT, 600.0), Cell(m(t.dedTotal), 1050.0, right = true)))
                b.row(listOf(Cell(net, 600.0), Cell(m(t.net), 1050.0, right = true)))
            }
            "headRow" -> {
                fun block(items: List<Pair<String, Long>>) {
                    items.chunked(6).forEach { chunk ->
                        b.row(chunk.mapIndexed { i, it -> Cell(it.first, 80.0 + i * 250, center = true, cw = 230.0) })
                        b.row(chunk.mapIndexed { i, it -> Cell(m(it.second), 80.0 + i * 250, center = true, cw = 230.0) })
                    }
                }
                block(e.map { it.name to it.amount } + (gross to t.gross))
                block(d.map { it.name to it.amount } + (dedT to t.dedTotal))
                block(listOf(net to t.net))
            }
            "stacked" -> {
                val cells = e.map { it.name to it.amount } + (gross to t.gross) + d.map { it.name to it.amount } + (dedT to t.dedTotal) + (net to t.net)
                cells.chunked(4).forEach { chunk ->
                    b.row(chunk.mapIndexed { i, it -> Cell(it.first, 80.0 + i * 380) })
                    b.row(chunk.mapIndexed { i, it -> Cell(m(it.second), 420.0 + i * 380, right = true) })
                }
            }
            "colon" -> {
                e.forEach { b.row(listOf(Cell("${it.name} : ${m(it.amount)}", 120.0))) }
                b.row(listOf(Cell("$gross : ${m(t.gross)}", 120.0)))
                d.forEach { b.row(listOf(Cell("${it.name} : ${m(it.amount)}", 120.0))) }
                b.row(listOf(Cell("$dedT : ${m(t.dedTotal)}", 120.0)))
                b.row(listOf(Cell("$net : ${m(t.net)}", 120.0)))
            }
            "hours" -> {
                b.row(listOf(Cell("항목", 120.0), Cell("시간", 600.0, right = true), Cell("금액", 1000.0, right = true)))
                e.forEach { b.row(listOfNotNull(Cell(it.name, 120.0), it.minutes?.let { mm -> Cell(hours(mm, seed), 600.0, right = true) }, Cell(m(it.amount), 1000.0, right = true))) }
                b.row(listOf(Cell(gross, 120.0), Cell(m(t.gross), 1000.0, right = true)))
                d.forEach { b.row(listOf(Cell(it.name, 120.0), Cell(m(it.amount), 1000.0, right = true))) }
                b.row(listOf(Cell(dedT, 120.0), Cell(m(t.dedTotal), 1000.0, right = true)))
                b.row(listOf(Cell(net, 120.0), Cell(m(t.net), 1000.0, right = true)))
            }
            "noteAfter" -> {
                b.row(listOf(Cell("항목", 120.0), Cell("금액", 700.0, right = true), Cell("산출근거", 800.0)))
                e.forEach { b.row(listOfNotNull(Cell(it.name, 120.0), Cell(m(it.amount), 700.0, right = true),
                    it.minutes?.let { mm -> Cell("${mm / 60}시간 × 12,000 × 1.5", 800.0) })) }
                b.row(listOf(Cell(gross, 120.0), Cell(m(t.gross), 700.0, right = true)))
                d.forEach { b.row(listOf(Cell(it.name, 120.0), Cell(m(it.amount), 700.0, right = true))) }
                b.row(listOf(Cell(dedT, 120.0), Cell(m(t.dedTotal), 700.0, right = true)))
                b.row(listOf(Cell(net, 120.0), Cell(m(t.net), 700.0, right = true)))
            }
            "formulaInline" -> {
                e.forEach { b.row(listOf(Cell(it.name, 120.0), Cell((it.minutes?.let { mm -> "${mm / 60}시간 × 12,000 × 1.5 = " } ?: "") + m(it.amount), 1300.0, right = true))) }
                b.row(listOf(Cell(gross, 120.0), Cell(m(t.gross), 1300.0, right = true)))
                d.forEach { b.row(listOf(Cell(it.name, 120.0), Cell(m(it.amount), 1300.0, right = true))) }
                b.row(listOf(Cell(dedT, 120.0), Cell(m(t.dedTotal), 1300.0, right = true)))
                b.row(listOf(Cell(net, 120.0), Cell(m(t.net), 1300.0, right = true)))
            }
            "attendTop" -> {
                b.row(listOf(Cell("근로일수", 120.0), Cell("22", 420.0, right = true), Cell("총근로시간", 520.0), Cell("209", 900.0, right = true)))
                b.row(listOf(Cell("연장근로시간", 120.0), Cell("${(e.firstOrNull { it.role == SlipRole.OVERTIME }?.minutes ?: 0) / 60}", 420.0, right = true),
                    Cell("야간근로시간", 520.0), Cell("${(e.firstOrNull { it.role == SlipRole.NIGHT }?.minutes ?: 0) / 60}", 900.0, right = true)))
                b.row(listOf(Cell("지급항목", 120.0), Cell("공제항목", 820.0)))
                for (i in 0 until maxOf(e.size, d.size)) b.row(listOfNotNull(
                    e.getOrNull(i)?.let { Cell(it.name, 120.0) }, e.getOrNull(i)?.let { Cell(m(it.amount), 700.0, right = true) },
                    d.getOrNull(i)?.let { Cell(it.name, 820.0) }, d.getOrNull(i)?.let { Cell(m(it.amount), 1450.0, right = true) },
                ))
                b.row(listOf(Cell(gross, 120.0), Cell(m(t.gross), 700.0, right = true), Cell(dedT, 820.0), Cell(m(t.dedTotal), 1450.0, right = true)))
                b.row(listOf(Cell(net, 820.0), Cell(m(t.net), 1450.0, right = true)))
            }
            "hourly" -> {
                val rate = e.first().amount / ((e.first().minutes ?: 60) / 60)
                t = t.copy(extra = listOf(Item("기본시급", SlipRole.HOURLY, rate)))
                b.row(listOf(Cell("기본시급", 120.0), Cell(m(rate), 700.0, right = true), Cell("근로시간", 820.0), Cell("${(e.first().minutes ?: 0) / 60}", 1450.0, right = true)))
                b.row(listOf(Cell("지급항목", 120.0), Cell("금액", 700.0, right = true), Cell("공제항목", 820.0), Cell("금액", 1450.0, right = true)))
                for (i in 0 until maxOf(e.size, d.size)) b.row(listOfNotNull(
                    e.getOrNull(i)?.let { Cell(it.name, 120.0) }, e.getOrNull(i)?.let { Cell(m(it.amount), 700.0, right = true) },
                    d.getOrNull(i)?.let { Cell(it.name, 820.0) }, d.getOrNull(i)?.let { Cell(m(it.amount), 1450.0, right = true) },
                ))
                b.row(listOf(Cell(gross, 120.0), Cell(m(t.gross), 700.0, right = true), Cell(dedT, 820.0), Cell(m(t.dedTotal), 1450.0, right = true)))
                b.row(listOf(Cell(net, 820.0), Cell(m(t.net), 1450.0, right = true)))
            }
            "leaders" -> {
                fun lead(name: String, v: Long) = b.row(listOf(Cell(name, 120.0), Cell(".".repeat(14), 470.0), Cell(m(v), 1000.0, right = true)))
                e.forEach { lead(it.name, it.amount) }; lead(gross, t.gross)
                d.forEach { lead(it.name, it.amount) }; lead(dedT, t.dedTotal); lead(net, t.net)
            }
            "numbered" -> {
                var n = 1
                fun num(name: String, v: Long) = b.row(listOf(Cell("${n++}", 60.0), Cell(name, 140.0), Cell(m(v), 900.0, right = true)))
                e.forEach { num(it.name, it.amount) }; num(gross, t.gross)
                d.forEach { num(it.name, it.amount) }; num(dedT, t.dedTotal); num(net, t.net)
            }
            "netTop" -> {
                b.row(listOf(Cell("이번 달 $net", 120.0), Cell(m(t.net), 1100.0, right = true)))
                b.row(listOf(Cell("지급", 120.0), Cell("공제", 820.0)))
                for (i in 0 until maxOf(e.size, d.size)) b.row(listOfNotNull(
                    e.getOrNull(i)?.let { Cell(it.name, 120.0) }, e.getOrNull(i)?.let { Cell(m(it.amount), 700.0, right = true) },
                    d.getOrNull(i)?.let { Cell(it.name, 820.0) }, d.getOrNull(i)?.let { Cell(m(it.amount), 1450.0, right = true) },
                ))
                b.row(listOf(Cell(gross, 120.0), Cell(m(t.gross), 700.0, right = true), Cell(dedT, 820.0), Cell(m(t.dedTotal), 1450.0, right = true)))
            }
            "sectionCell" -> {
                e.forEachIndexed { i, it -> b.row(listOfNotNull(if (i == 0) Cell("지급", 60.0) else null, Cell(it.name, 200.0), Cell(m(it.amount), 900.0, right = true))) }
                b.row(listOf(Cell(gross, 200.0), Cell(m(t.gross), 900.0, right = true)))
                d.forEachIndexed { i, it -> b.row(listOfNotNull(if (i == 0) Cell("공제", 60.0) else null, Cell(it.name, 200.0), Cell(m(it.amount), 900.0, right = true))) }
                b.row(listOf(Cell(dedT, 200.0), Cell(m(t.dedTotal), 900.0, right = true)))
                b.row(listOf(Cell(net, 200.0), Cell(m(t.net), 900.0, right = true)))
            }
            "spaced" -> {
                fun sp(s: String) = if (s.length <= 4) s.toList().joinToString(" ") else s
                for (i in 0 until maxOf(e.size, d.size)) b.row(listOfNotNull(
                    e.getOrNull(i)?.let { Cell(sp(it.name), 120.0) }, e.getOrNull(i)?.let { Cell(m(it.amount), 700.0, right = true) },
                    d.getOrNull(i)?.let { Cell(sp(it.name), 820.0) }, d.getOrNull(i)?.let { Cell(m(it.amount), 1450.0, right = true) },
                ))
                b.row(listOf(Cell(sp("지급합계"), 120.0), Cell(m(t.gross), 700.0, right = true), Cell(sp("공제합계"), 820.0), Cell(m(t.dedTotal), 1450.0, right = true)))
                b.row(listOf(Cell(sp("실지급액"), 820.0), Cell(m(t.net), 1450.0, right = true)))
            }
            "dense" -> {
                val half = (e.size + 1) / 2
                b.row(listOf(Cell("지급항목", 60.0), Cell("지급항목", 560.0), Cell("공제항목", 1080.0)))
                for (i in 0 until maxOf(half, d.size)) b.row(listOfNotNull(
                    e.getOrNull(i)?.let { Cell(it.name, 60.0) }, e.getOrNull(i)?.let { Cell(m(it.amount), 500.0, right = true) },
                    e.getOrNull(half + i)?.let { Cell(it.name, 560.0) }, e.getOrNull(half + i)?.let { Cell(m(it.amount), 1000.0, right = true) },
                    d.getOrNull(i)?.let { Cell(it.name, 1080.0) }, d.getOrNull(i)?.let { Cell(m(it.amount), 1540.0, right = true) },
                ))
                b.row(listOf(Cell(gross, 560.0), Cell(m(t.gross), 1000.0, right = true), Cell(dedT, 1080.0), Cell(m(t.dedTotal), 1540.0, right = true)))
                b.row(listOf(Cell(net, 1080.0), Cell(m(t.net), 1540.0, right = true)))
            }
            else -> error(layout)
        }
        return Page(layout, b.words, t)
    }

    private fun hours(minutes: Int, seed: Int): String {
        val h = minutes / 60.0
        return when (seed % 3) {
            0 -> if (h == Math.floor(h)) "${h.toInt()}" else "$h"
            1 -> if (h == Math.floor(h)) "${h.toInt()}시간" else "${h}시간"
            else -> if (h == Math.floor(h)) "${h.toInt()}h" else "${h}h"
        }
    }

    data class Cell(val text: String, val x: Double, val right: Boolean = false, val center: Boolean = false, val cw: Double = 0.0)

    private class PageBuilder(val h: Double) {
        val words = mutableListOf<Word>()
        var y = 80.0

        fun title(t: String) { place(t, 520.0, y, h * 1.3); y += h * 2.4 }

        fun row(cells: List<Cell>) {
            for (c in cells) {
                val w = width(c.text, h)
                val left = when {
                    c.right -> c.x - w
                    c.center -> c.x + (c.cw - w) / 2
                    else -> c.x
                }
                place(c.text, left, y, h)
            }
            y += h * 1.7
        }

        /** A printed cell as the words in it, each with its own left edge. */
        fun place(text: String, left: Double, top: Double, hh: Double) {
            var x = left
            for (part in Regex("""\S+|\s+""").findAll(text)) {
                if (part.value.isNotBlank()) words += Word(part.value, x, top, hh)
                x += width(part.value, hh)
            }
        }
    }

    // ---- as ML Kit gives it back ----

    /**
     * One reading of [page]: words with boxes after [noise]. [lines] true gives ML Kit's lines (the words of a cell, or of
     * cells close together, as one) as 1.28 read them; false gives the words (elements) as 1.29 reads them.
     */
    fun read(page: Page, noise: Noise, r: Random, lines: Boolean = false): List<OcrLine> {
        val words = page.words.mapNotNull { w ->
            if (r.nextDouble() < noise.lose && w.text.any { it.isDigit() }.not()) return@mapNotNull null
            w.copy(text = garble(w.text, noise, r))
        }.toMutableList()
        // Two words read as one, or one as two.
        val out = mutableListOf<Word>()
        var i = 0
        while (i < words.size) {
            val w = words[i]
            val next = words.getOrNull(i + 1)
            if (next != null && next.y == w.y && r.nextDouble() < noise.glue && next.x - (w.x + width(w.text, w.h)) < w.h * 1.0) {
                out += w.copy(text = w.text + next.text); i += 2; continue
            }
            out += w; i++
        }
        val groups: List<List<Word>> = if (!lines) out.map { listOf(it) } else {
            // ML Kit lines: words on one row less than 1.2 letters apart.
            val g = mutableListOf<MutableList<Word>>()
            for (w in out.sortedWith(compareBy({ it.y }, { it.x }))) {
                val last = g.lastOrNull()?.last()
                if (last != null && last.y == w.y && w.x - (last.x + width(last.text, last.h)) < w.h * 1.2) g.last() += w else g += mutableListOf(w)
            }
            g
        }
        val cx = 800.0; val cy = 1000.0
        val rad = Math.toRadians(noise.tilt)
        return groups.map { g ->
            val text = g.joinToString(" ") { it.text }
            val left = g.first().x
            val right = g.last().x + width(g.last().text, g.last().h)
            val top = g.first().y
            val bottom = top + g.first().h
            // The box of the turned rectangle, a few pixels off.
            val pts = listOf(left to top, right to top, left to bottom, right to bottom).map { (x, y) ->
                val dx = x - cx; val dy = y - cy
                (cx + dx * cos(rad) - dy * sin(rad)) to (cy + dx * sin(rad) + dy * cos(rad))
            }
            fun j() = if (noise.tilt == 0.0 && noise.digit == 0.0) 0 else r.nextInt(-2, 3)
            OcrLine(text, pts.minOf { it.first }.roundToInt() + j(), pts.minOf { it.second }.roundToInt() + j(),
                pts.maxOf { it.first }.roundToInt() + j(), pts.maxOf { it.second }.roundToInt() + j(), angle = noise.tilt.toFloat())
        }
    }

    private val DIGIT_LIKE = mapOf('3' to '8', '8' to '3', '1' to '7', '7' to '1', '0' to '6', '6' to '0', '5' to '6')

    private fun garble(t: String, n: Noise, r: Random): String {
        var s = t
        if (s.any { it.isDigit() }) {
            if (r.nextDouble() < n.digit) {
                val idx = s.indices.filter { s[it].isDigit() }
                val i = idx[r.nextInt(idx.size)]
                val to = if (r.nextInt(4) == 0 && s[i] == '0') 'O' else DIGIT_LIKE[s[i]] ?: s[i]
                s = s.substring(0, i) + to + s.substring(i + 1)
            }
            if (r.nextDouble() < n.comma) s = s.replace(',', '.')
            if (r.nextDouble() < n.won) s = s.replace("원", if (r.nextBoolean()) "" else "윈")
        } else if (r.nextDouble() < n.name) {
            val slip = NAME_SLIPS.filter { it.first in s }
            if (slip.isNotEmpty()) slip[r.nextInt(slip.size)].let { (a, b) -> s = s.replaceFirst(a, b) }
        }
        return s
    }
}
