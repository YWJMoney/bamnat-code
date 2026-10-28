package com.bamnat.core

import com.bamnat.core.pay.OcrLine
import com.bamnat.core.pay.SlipRole
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * [PHOTO-07] 1.29 AQ: the two real photos the user sent (통합 2026-10-27, 화학 공장 현장직 명세서와 계산 방법, 모니터를 휴대폰으로
 * 찍어 모아레·대각선 워터마크·원근 기울기), as ML Kit gives them back: each cell's words with their boxes, where they sit in
 * the photo (measured on the 4000×3000 picture, a 1600×1200 copy × 2.5). The photos themselves are not kept: the name, the
 * company's watermark id and the IP address are left out or made up (OOO, abcde, 10.11.222.33).
 *
 * Things the photos do that the reader has to live with: the 금액 column is a separate column to the right of the names and
 * sits half a row lower; the 공제 side is a second table to the right, again with its amounts in their own column; the
 * page leans (the right side about 20 px lower over 1000 px); the watermark runs across at about 20°; the browser and the
 * task bar are in the picture; the 근태 table puts its heads on one line and the values on the next.
 */
object PlantSample {
    data class Item(val name: String, val role: Set<SlipRole>, val amount: Long)

    /** 정답 (통합이 사진에서 옮겨 적고 합계 검산함). */
    val EARNINGS = listOf(
        Item("기본급I", setOf(SlipRole.BASE), 2_397_500), Item("기본급II", setOf(SlipRole.BASE), 158_330),
        Item("자격수당", setOf(SlipRole.ALLOWANCE), 57_500), Item("위험예지훈련/LMS", setOf(SlipRole.ALLOWANCE), 66_598),
        Item("상여금", setOf(SlipRole.BONUS), 2_555_830), Item("휴일근로수당", setOf(SlipRole.HOLIDAY), 2_626_592),
        Item("휴일연장수당", setOf(SlipRole.HOLIDAY, SlipRole.OVERTIME), 1_383_555), Item("야간근무수당", setOf(SlipRole.NIGHT), 691_778),
        Item("향군수당", setOf(SlipRole.ALLOWANCE), 129_709), Item("소급분(지급)", setOf(SlipRole.ALLOWANCE), 259_417),
    )
    val DEDUCTIONS = listOf(
        Item("소득세", setOf(SlipRole.DEDUCTION), 1_731_400), Item("주민세", setOf(SlipRole.DEDUCTION), 173_130),
        Item("건강보험", setOf(SlipRole.DEDUCTION), 280_730), Item("고용보험", setOf(SlipRole.DEDUCTION), 62_110),
        Item("국민연금", setOf(SlipRole.DEDUCTION), 313_020), Item("볼링회(남부공장)", setOf(SlipRole.DEDUCTION), 3_000),
        Item("축구회(AB/CD)", setOf(SlipRole.DEDUCTION), 10_000), Item("한맥산악회(남부공장)", setOf(SlipRole.DEDUCTION), 3_000),
        Item("OO화학 노동조합", setOf(SlipRole.DEDUCTION), 25_558), Item("남부_사택관리비", setOf(SlipRole.DEDUCTION), 580_890),
        Item("소급분(공제)", setOf(SlipRole.DEDUCTION), 37_750),
    )
    val TOTALS = listOf(
        Item("지급계", setOf(SlipRole.GROSS, SlipRole.TOTAL), 10_326_809), Item("공제계", setOf(SlipRole.DEDUCTION_TOTAL), 3_220_588),
        Item("차감지급액", setOf(SlipRole.NET), 7_106_221),
    )
    val ALL = EARNINGS + DEDUCTIONS + TOTALS

    /** 근태 정보(현장직): hours of the lines they belong to. */
    val HOURS = mapOf("휴일근로수당" to 81 * 60, "휴일연장수당" to 32 * 60, "야간근무수당" to 64 * 60, "향군수당" to 6 * 60)

    /** One cell as printed: its text, its centre in the 1600-wide copy, its letter height there. */
    private data class Cell(val text: String, val x: Double, val y: Double, val h: Double = 13.0)

    private val SLIP = listOf(
        // Browser and page chrome.
        Cell("XXXX HR", 125.0, 156.0, 11.0), Cell("hr.example.com/cm/service/leftMenuOnly.ncd#noback", 145.0, 200.0, 12.0),
        Cell("급여명세서", 70.0, 277.0, 12.0), Cell("전체 닫기", 1108.0, 302.0, 11.0), Cell("00:58:39", 1310.0, 308.0, 11.0), Cell("OOO", 1452.0, 312.0, 12.0),
        // The sums at the top: each name with its amount under it to the right.
        Cell("총지급액", 108.0, 310.0), Cell("10,326,809", 483.0, 336.0, 17.0), Cell("공제총액", 646.0, 325.0),
        Cell("3,220,588", 995.0, 350.0, 17.0), Cell("차감지급액", 1163.0, 337.0), Cell("7,106,221", 1493.0, 361.0, 17.0),
        // Heads.
        Cell("지급내역", 255.0, 387.0), Cell("금액", 632.0, 398.0), Cell("공제내역", 1003.0, 407.0), Cell("금액", 1366.0, 413.0),
        // 지급: names in their column, amounts right-aligned at x≈765 and about half a row lower.
        Cell("기본급I", 255.0, 425.0), Cell("2,397,500", 737.0, 440.0, 12.0),
        Cell("기본급II", 255.0, 467.0), Cell("158,330", 742.0, 482.0, 12.0),
        Cell("자격수당", 257.0, 509.0), Cell("57,500", 745.0, 522.0, 12.0),
        Cell("위험예지훈련/LMS", 258.0, 552.0), Cell("66,598", 745.0, 563.0, 12.0),
        Cell("상여금", 258.0, 593.0), Cell("2,555,830", 735.0, 603.0, 12.0),
        Cell("휴일근로수당", 259.0, 634.0), Cell("2,626,592", 735.0, 643.0, 12.0),
        Cell("휴일연장수당", 259.0, 675.0), Cell("1,383,555", 735.0, 683.0, 12.0),
        Cell("야간근무수당", 260.0, 716.0), Cell("691,778", 739.0, 723.0, 12.0),
        Cell("향군수당", 261.0, 756.0), Cell("129,709", 739.0, 763.0, 12.0),
        Cell("소급분(지급)", 262.0, 836.0), Cell("259,417", 736.0, 842.0, 12.0),
        Cell("지급계", 263.0, 875.0), Cell("10,326,809", 724.0, 881.0, 12.0),
        // 공제: the second table, its amounts right-aligned at x≈1495.
        Cell("소득세", 998.0, 444.0), Cell("1,731,400", 1467.0, 454.0, 12.0),
        Cell("주민세", 997.0, 485.0), Cell("173,130", 1470.0, 495.0, 12.0),
        Cell("건강보험", 995.0, 526.0), Cell("280,730", 1468.0, 536.0, 12.0),
        Cell("고용보험", 994.0, 567.0), Cell("62,110", 1470.0, 577.0, 12.0),
        Cell("국민연금", 992.0, 607.0), Cell("313,020", 1462.0, 617.0, 12.0),
        Cell("볼링회 ( 남부공장 )", 990.0, 648.0), Cell("3,000", 1466.0, 658.0, 12.0),
        Cell("축구회 (AB/CD)", 989.0, 689.0), Cell("10,000", 1460.0, 698.0, 12.0),
        Cell("한맥산악회 (남부공장)", 987.0, 728.0), Cell("3,000", 1461.0, 738.0, 12.0),
        Cell("OO화학 노동조합", 985.0, 768.0), Cell("25,558", 1462.0, 777.0, 12.0),
        Cell("남부_사택관리비", 985.0, 807.0), Cell("580,890", 1449.0, 817.0, 12.0),
        Cell("소급분(공제)", 984.0, 846.0), Cell("37,750", 1458.0, 856.0, 12.0),
        Cell("공제계", 983.0, 885.0), Cell("3,220,588", 1437.0, 894.0, 12.0),
        // 근태 정보(현장직): heads, then their values on the next line.
        Cell("근태 정보(현장직)", 140.0, 936.0, 15.0),
        Cell("근로일수", 205.0, 978.0), Cell("휴업일수", 445.0, 981.0), Cell("휴일근로시간수", 683.0, 983.0), Cell("평일연장근로시간수", 918.0, 987.0),
        Cell("휴일연장근로시간수", 1155.0, 991.0), Cell("야간근로시간수", 1390.0, 994.0),
        Cell("31.00", 205.0, 1012.0, 12.0), Cell("81.00", 683.0, 1017.0, 12.0), Cell("32.00", 1155.0, 1024.0, 12.0), Cell("64.00", 1387.0, 1028.0, 12.0),
        Cell("명절 휴일 근로", 205.0, 1047.0), Cell("연차 휴가 보상일수", 445.0, 1050.0), Cell("교육수당 시간수", 683.0, 1053.0),
        Cell("향군수당 시간수", 918.0, 1055.0), Cell("당직 시간수", 1152.0, 1059.0), Cell("6.00", 918.0, 1090.0, 12.0),
        // The task bar.
        Cell("기에 입력하십시", 50.0, 1106.0, 12.0), Cell("Global HR ...", 310.0, 1109.0, 11.0), Cell("XXXX HR - C...", 450.0, 1109.0, 11.0),
        Cell("오후 4:25:47", 1490.0, 1114.0, 10.0), Cell("2026-10-25", 1490.0, 1130.0, 10.0),
    )

    private val RULES = listOf(
        Cell("※ 당월 급여산정을 위한 근태는 「HR Portal▶HR Service▶근태/복리후생▶근태」 메뉴에서 확인하시기 바랍니다.", 330.0, 130.0, 12.0),
        Cell("(사무직 : 근태신청 보상휴가 내역 / 현장직 : 개인근태조회 에서 확인)", 210.0, 154.0, 12.0),
        Cell("세금 산정 기준", 70.0, 216.0, 15.0), Cell("부양가족 정보", 669.0, 256.0), Cell("원천징수 비율", 1458.0, 280.0),
        Cell("본인", 88.0, 314.0), Cell("배우자", 216.0, 313.0), Cell("직계존속", 345.0, 312.0), Cell("직계비속", 476.0, 311.0),
        Cell("연말기준 만8세이", 605.0, 290.0), Cell("상 만20세이하 자", 605.0, 310.0), Cell("녀", 605.0, 330.0),
        Cell("1", 88.0, 371.0), Cell("1", 217.0, 370.0), Cell("0", 346.0, 368.0), Cell("1", 476.0, 367.0), Cell("0", 606.0, 366.0), Cell("100%", 1457.0, 355.0),
        Cell("계산 방법", 60.0, 441.0, 15.0), Cell("구분", 415.0, 483.0), Cell("산출방법", 1190.0, 475.0),
        Cell("휴일근로수당", 416.0, 521.0), Cell("해당시간 × 1.5 × ( 통상임금 / 시수 )", 1192.0, 514.0),
        Cell("평일연장수당", 416.0, 565.0), Cell("해당시간 × 1.5 × ( 통상임금 / 시수 )", 1192.0, 558.0),
        Cell("휴일연장수당", 417.0, 608.0), Cell("해당시간 × 2 × ( 통상임금 / 시수 )", 1192.0, 602.0),
        Cell("야간근로수당", 418.0, 651.0), Cell("해당시간 × 0.5 × ( 통상임금 / 시수 )", 1192.0, 646.0),
        Cell("보상휴가금액(사무직限)", 419.0, 694.0), Cell("해당시간 × 1.5 × ( 기준급 / 시수 ) ※사무직 평일연장/야간/휴일근로에 대해 보상휴가로 부여함", 1192.0, 688.0),
        Cell("휴가보상", 420.0, 738.0), Cell("해당일수 × 1.5 × ( 기준급 / 시수 × 8 )", 1192.0, 732.0),
        Cell("이전 미공제액", 421.0, 781.0), Cell("직전 지급건 중에 미공제 된 금액으로 이번 지급건에서 공제된 금액", 1192.0, 775.0),
        Cell("다음 공제 예정액", 421.0, 824.0), Cell("이번 지급건 중에 미공제 된 금액으로 다음 지급건에서 공제될 금액", 1192.0, 818.0),
        Cell("기준급", 422.0, 866.0), Cell("기본급, 직책수당, 자격수당, 기술전수교육수당", 1192.0, 861.0),
        Cell("통상임금", 423.0, 909.0), Cell("기본급, 직책수당, 자격수당, 기술전수교육수당 등 소정근로대가성, 정기성, 일률성, 고정성이 인정되는 임금 일체", 1192.0, 904.0),
        Cell("오후 4:25:0", 1550.0, 972.0, 10.0),
    )

    /** The watermark, 20° up to the right, a few times across the page (made-up id and address). */
    private val WATERMARK = "XX Chem abcde 2026-10-25 16:25 10.11.222.33"
    private val MARKS = listOf(30.0 to 525.0, 945.0 to 550.0, 160.0 to 930.0, 940.0 to 1030.0, 260.0 to 460.0, 1080.0 to 920.0)

    /**
     * What a reading of the photo does to it. [PHOTO-27] 1.30 AV: the axes a photo really changes along (R3 A-1, 사용자
     * 2026-10-27 "사진마다 읽는 부분이 다르다"), on top of 1.29's letters and boxes:
     * - [tilt]: the whole page turned, degrees (positive: lines go down to the right).
     * - [perspective]: taken from one side (a keystone): the far side smaller, rows converging, each row at its own slant and
     *   the rows closer together on the far side. 0.06 is a light angle, 0.12 a strong one.
     * - [scale]: the letters' size (taken from near or far), ×0.6~1.4.
     * - [split]: ML Kit's words cut and joined differently at another size ("기본급" "I", "위험예지훈련" "/LMS", "볼링회(", "2,397," "500").
     * - [cropTop]/[cropBottom]: the photo cut above the 지급내역 heads (the sums at the top gone) or under the 공제계 (근태 gone).
     * - [headLost]: the 지급내역·공제내역 heads not read (or read as other letters, [headGarbled]).
     */
    data class Noise(
        val digit: Double = 0.0, val comma: Double = 0.0, val roman: Double = 0.0, val mark: Double = 0.0, val jitter: Int = 0, val lose: Double = 0.0,
        val angles: Boolean = true,
        val tilt: Double = 0.0, val perspective: Double = 0.0, val scale: Double = 1.0, val split: Double = 0.0,
        val cropTop: Boolean = false, val cropBottom: Boolean = false, val headLost: Boolean = false, val headGarbled: Boolean = false,
    ) {
        companion object { val CLEAN = Noise() }
    }

    fun payslip(noise: Noise, r: Random, lines: Boolean = false): List<OcrLine> = ocr(SLIP, noise, r, lines)
    fun rules(noise: Noise, r: Random, lines: Boolean = true): List<OcrLine> = ocr(RULES, noise, r, lines)

    /** [PHOTO-27] What a reading with [noise] can still find: the sums at the top cut away take 차감지급액 with them. */
    fun expected(noise: Noise): List<Item> = if (noise.cropTop) ALL.filter { it.name != "차감지급액" } else ALL

    /** [PHOTO-27] Hours a reading with [noise] can still find: the 근태 table cut away takes them all. */
    fun expectedHours(noise: Noise): Map<String, Int> = if (noise.cropBottom) emptyMap() else HOURS

    private fun charW(c: Char, h: Double) = when {
        c in '가'..'힣' -> h * 1.0
        c == ' ' -> h * 0.35
        c == ',' || c == '.' -> h * 0.3
        else -> h * 0.6
    }

    /** [PHOTO-27] Where a point of the 1600-wide copy lands in the photo taken with [n]: keystone, then turned, then sized. */
    private class Lens(n: Noise) {
        private val p = n.perspective
        private val q = n.perspective * 0.5
        private val t = Math.toRadians(n.tilt)
        private val k = 2.5 * n.scale
        fun at(x: Double, y: Double): Pair<Double, Double> {
            val u = (x - 800.0) / 1600.0
            val v = (y - 640.0) / 1600.0
            val d = 1 + p * u + q * v
            val pu = u / d
            val pv = v / d
            val ru = pu * cos(t) - pv * sin(t)
            val rv = pu * sin(t) + pv * cos(t)
            return (800.0 + 1600.0 * ru) * k to (640.0 + 1600.0 * rv) * k
        }
    }

    private fun ocr(cells: List<Cell>, n: Noise, r: Random, lines: Boolean): List<OcrLine> {
        val out = mutableListOf<OcrLine>()
        val lens = Lens(n)
        // The page leans: about 0.7° (the right side lower); each line's angle says so, as ML Kit's does.
        val lean = if (n.angles) 0.7 else 0.0
        fun box(text: String, x0: Double, y0: Double, x1: Double, y1: Double, angle: Double): OcrLine {
            val corners = listOf(lens.at(x0, y0), lens.at(x1, y0), lens.at(x0, y1), lens.at(x1, y1))
            // ML Kit's boxes miss by a share of the letters' size: a far photo's by fewer pixels (the same as 1.29 at ×1).
            val jit = (n.jitter * n.scale).roundToInt()
            fun j() = if (jit == 0) 0 else r.nextInt(-jit, jit + 1)
            // The slant ML Kit gives a line: the page's own and what the lens did to the line's course.
            val (ax, ay) = lens.at(x0, (y0 + y1) / 2)
            val (bx, by) = lens.at(x1, (y0 + y1) / 2)
            val turned = if (n.angles) Math.toDegrees(atan2(by - ay, bx - ax)) else 0.0
            return OcrLine(text, corners.minOf { it.first }.roundToInt() + j(), corners.minOf { it.second }.roundToInt() + j(),
                corners.maxOf { it.first }.roundToInt() + j(), corners.maxOf { it.second }.roundToInt() + j(), angle = (angle + turned).toFloat())
        }
        for (c in cells) {
            if (n.cropTop && c.y < 372) continue
            if (n.cropBottom && c.y > 915) continue
            if (n.headLost && c.text in HEADS) continue
            val printed = if (n.headGarbled) HEADS[c.text] ?: c.text else c.text
            val text = garble(printed, n, r)
            if (text.isBlank()) continue
            if (r.nextDouble() < n.lose && text.none { it.isDigit() }) continue
            val words = pieces(if (lines) listOf(text) else text.split(' ').filter { it.isNotEmpty() }, n.split, r)
            val full = text.sumOf { charW(it, c.h) }
            var x = c.x - full / 2
            for ((w, spaceAfter) in words) {
                val ww = w.sumOf { charW(it, c.h) }
                out += box(w, x, c.y - c.h / 2, x + ww, c.y + c.h / 2, lean)
                x += ww + if (spaceAfter) charW(' ', c.h) else 0.0
            }
        }
        // The watermark: words in boxes that stand on their corner, read now and then.
        if (n.mark > 0) for ((sx, sy) in MARKS) {
            if (r.nextDouble() > n.mark) continue
            val h = 16.0
            val a = Math.toRadians(-20.0)
            var along = 0.0
            val wordsW = WATERMARK.split(' ')
            val parts = if (lines) listOf(WATERMARK) else wordsW
            for (w in parts) {
                val len = w.sumOf { charW(it, h) }
                val x0 = sx + along * cos(a)
                val y0 = sy + along * sin(a)
                val x1 = x0 + len * cos(a)
                val y1 = y0 + len * sin(a)
                if (r.nextDouble() < 0.8) {
                    val b = box(w, minOf(x0, x1), minOf(y0, y1) - h / 2, maxOf(x0, x1), maxOf(y0, y1) + h / 2, 0.0)
                    // Its own slant, -20°, and the lens's turn of the page.
                    out += b.copy(angle = if (n.angles) -20f + (n.tilt.toFloat()) else -20f)
                }
                along += len + charW(' ', h)
            }
        }
        return out.shuffled(r)
    }

    /** The heads of the two tables, and how a photo misreads them ([Noise.headGarbled]). */
    private val HEADS = mapOf("지급내역" to "지금내역", "공제내역" to "공제내 역")

    /**
     * [PHOTO-27] Words as ML Kit's elements cut them at one size and not at another: a word cut in two (at a comma for an
     * amount), or two words of a cell run together. Each piece says whether a space followed it.
     */
    private fun pieces(words: List<String>, split: Double, r: Random): List<Pair<String, Boolean>> {
        if (split <= 0.0) return words.map { it to true }
        val out = mutableListOf<Pair<String, Boolean>>()
        var i = 0
        while (i < words.size) {
            var w = words[i]
            // Two words of one cell read as one ("볼링회" "(" → "볼링회(").
            if (i + 1 < words.size && r.nextDouble() < split / 2) { w += words[i + 1]; i++ }
            val at = when {
                r.nextDouble() >= split -> -1
                w.any { it.isDigit() } -> w.indices.filter { w[it] == ',' }.let { cs -> if (cs.isEmpty()) -1 else cs[r.nextInt(cs.size)] + if (r.nextBoolean()) 0 else 1 }
                w.length >= 2 -> 1 + r.nextInt(w.length - 1)
                else -> -1
            }
            if (at in 1 until w.length) { out += w.substring(0, at) to false; out += w.substring(at) to true } else out += w to true
            i++
        }
        return out
    }

    private val ROMAN = mapOf("기본급I" to listOf("기본급l", "기본급1", "기본급|"), "기본급II" to listOf("기본급ll", "기본급Il", "기본급11", "기본급||"))

    private fun garble(t: String, n: Noise, r: Random): String {
        var s = t
        ROMAN[s]?.let { if (r.nextDouble() < n.roman) s = it[r.nextInt(it.size)] }
        if (s.any { it.isDigit() } && s.count { it == ',' } >= 1) {
            if (r.nextDouble() < n.comma) s = if (r.nextBoolean()) s.replaceFirst(',', '.') else s.replaceFirst(",", "")
            if (r.nextDouble() < n.digit) {
                val zeros = s.indices.filter { s[it] == '0' }
                if (zeros.isNotEmpty()) { val i = zeros[r.nextInt(zeros.size)]; s = s.substring(0, i) + "O" + s.substring(i + 1) }
            }
        }
        return s
    }
}
