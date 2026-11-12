package com.bamnat.core.recipe

import com.bamnat.core.layout.Contrast
import kotlin.math.roundToInt

/**
 * 2.0.0 [RVS-06] 그릴 때 지키는 바닥선 (검토 SD-02·SN-05·SN-06·SN-12(1)·RCP-18·I09ba-03, 스튜디오 조사 5장 S2, 디자인 품질 조건 4·9·12·14·15):
 * a maker may set anything, and it is kept as set; only where it is drawn, a part keeps
 * - 글자 대비: its letters read on what is under them, 4.5:1 for body text and 3:1 for small helper text in a cell and big letters
 *   ([BODY], [SMALL]); short of it, the letters' own colour is moved toward black or white (same hue), never swapped for another;
 * - 글자 크기: not under 12sp, 10sp for the small lines in a cell ([BODY_SP], [CELL_SP]); the phone's text size multiplies on top;
 * - 누르는 부품 보이기: a part a finger presses ([PRESSED]) and the few a screen is for ([ESSENTIAL]) is never hidden, nor fainter than 30;
 * - 움직임 예산: a part drawn many times on one screen ([REPEATED]) moves only on today's or the picked one;
 * - 공간: 바깥 간격 + 안 여백 within a third of the part's short side ([space], for callers that know the size).
 * Where it changed something, [Floored.fixes] says what, so the studio can say "이렇게 조금 바꿔 그려요" (SI-02).
 */
object PartFloor {
    const val BODY = 4.5
    const val SMALL = 3.0
    const val BODY_SP = 12.0
    const val CELL_SP = 10.0
    const val SHOW_ALPHA = 30

    /** What [floor] changed. */
    enum class Fix(val say: String) {
        INK("글자가 읽히게 색을 조금 옮겨 그려요"),
        SIZE("글자가 너무 작아 읽히는 크기로 그려요"),
        SHOW("누르는 곳이라 숨기지 않고 그려요"),
        ALPHA("누르는 곳이라 보이게 조금 진하게 그려요"),
        MOTION("한 화면에 여러 번 나오는 부품이라 오늘·고른 것만 움직여요"),
        SPACE("간격이 칸보다 커서 줄여 그려요"),
        ABOVE("달력 첫 줄이 먼저 보이게 머리·요일 줄을 조금 줄여 그려요"),
    }

    class Floored(val style: PartStyle, val fixes: Set<Fix>)

    /** Parts a finger presses (탭·단추·칩·칸·줄): hiding them or drawing them near clear leaves a place that acts but cannot be seen. */
    val PRESSED: Set<String> = setOf(
        "app.tabs", "app.tab", "app.button", "app.chip",
        "cal.head.nav", "cal.head.today", "cal.cell", "cal.band.slot", "cal.line.row", "cal.weekly.day", "cal.year.month",
        "memo.card", "memo.board.col", "memo.table.row", "memo.cal.cell",
        "money.row", "money.table.row", "money.cal.cell", "money.board.col",
        "pay.row", "widget.cell", "widget.task", "widget.task.check", "widget.task.add", "night.button",
    )

    /** The letters a screen is for: a calendar without its dates or a pay screen without its sum is not a look, it is broken. */
    val ESSENTIAL: Set<String> = setOf("cal.cell.date", "cal.head.title", "money.amount", "pay.total", "night.clock", "widget.task")

    /** Parts drawn many times on one screen: each moving on its own makes the screen busy and costs the battery (SN-03). */
    val REPEATED: Set<String> = setOf(
        "cal.cell", "cal.band.slot", "cal.line.row", "cal.line.bar", "cal.weekly.day", "cal.year.month", "cal.week.day",
        "memo.card", "memo.prop", "memo.board.col", "memo.table.row", "memo.cal.cell",
        "money.day", "money.row", "money.board.col", "money.table.row", "money.cal.cell",
        "pay.row", "widget.cell", "widget.task", "app.tab", "app.chip",
    )

    /** Whether [id] is one of [REPEATED] or inside one (a cell's date, a memo card's title). */
    fun repeated(id: String): Boolean {
        var p: String? = id
        while (p != null) {
            if (p in REPEATED) return true
            p = BlueprintParts.parentOf(p)
        }
        return false
    }

    /** The small helper letters of a cell or a row: 3:1 and 10sp. The rest is body text: 4.5:1 and 12sp. */
    fun small(id: String): Boolean =
        id != "cal.cell.date" && (id.startsWith("cal.cell.") || id.startsWith("memo.cal.cell") || id.startsWith("money.cal.cell") ||
            id in setOf("cal.band.slot", "cal.line.bar", "cal.weekly.day", "cal.year.month", "cal.week.day", "cal.foot", "widget.cell", "memo.card.date", "memo.prop"))

    /**
     * [s] as drawn for [id] in [states]. [ground] is the colour under the part when the look itself paints it (the parts above it with
     * a fill, laid over [screen]); null when something else paints it (a 칸 꾸미기 piece, a photo), so letters are only moved where what is
     * under them is known (the part's own fill, or [ground]). Nothing is changed that already meets its floor.
     */
    fun floor(id: String, s: PartStyle, ground: Long?, states: Set<String> = emptySet(), screen: Long = Contrast.WHITE): Floored {
        if (s.isEmpty) return Floored(s, emptySet())
        val fixes = LinkedHashSet<Fix>()
        var out = s
        if (id in PRESSED || id in ESSENTIAL) {
            if (out.hide == true) { out = out.copy(hide = null); fixes += Fix.SHOW }
            if (out.alpha != null && out.alpha!! < SHOW_ALPHA) { out = out.copy(alpha = SHOW_ALPHA); fixes += Fix.ALPHA }
        }
        val isSmall = small(id)
        val minSp = if (isSmall) CELL_SP else BODY_SP
        if (out.size != null && out.size!! < minSp) { out = out.copy(size = minSp); fixes += Fix.SIZE }
        // Its own fill, laid over what is under it; over [screen] only when the fill is strong enough that what is under it hardly shows.
        val under = ground?.let { groundOf(out, it) } ?: screen.takeIf { fillStrength(out) >= 0.5 }?.let { groundOf(out, it) }
        if (under != null) out.ink?.let { ink ->
            val size = out.size ?: 14.0
            val big = size >= 24 || (size >= 18.5 && (out.weight ?: 400) >= 700)
            val fixed = readable(ink, under, if (isSmall || big) SMALL else BODY)
            if (fixed != ink) { out = out.copy(ink = fixed); fixes += Fix.INK }
        }
        if (out.motion != null && out.motion != "none" && repeated(id) && "today" !in states && "sel" !in states) {
            out = out.copy(motion = null)
            fixes += Fix.MOTION
        }
        return Floored(out, fixes)
    }

    /**
     * 2.0.0 [RVS-14] 첫 줄 먼저 (총괄 HQ-56, 디자인 품질 조건 1, 스튜디오 조사 5장 S2): the parts above the month's first row — 달력 머리
     * (and its 달 이름·이동·오늘 단추) and 요일 줄 — add at most [ABOVE_DP] to what the app's own head and weekday row take. Past it, the
     * 달 이름 is drawn at most [HEAD_TITLE_SP] and the 요일 글자 at most [WEEK_DAY_SP], then their 바깥 간격 and 위아래 안 여백 are made smaller by
     * the same share until it fits. 1,034 market works: 66 go past it (60dp at most), E9 '16조각' takes 32.
     */
    const val ABOVE_DP = 32
    const val HEAD_TITLE_SP = 34.0
    const val WEEK_DAY_SP = 16.0
    val ABOVE: Set<String> = setOf("cal.head", "cal.head.title", "cal.head.nav", "cal.head.today", "cal.week", "cal.week.day")
    private const val LINE = 1.3

    /** The app's own: a head row of 48dp buttons, a 22sp 달 이름, a weekday row 6dp top and bottom around 12sp letters. */
    private const val HEAD_ROW_DP = 48.0
    private const val TITLE_SP = 22.0
    private const val WEEK_PAD_DP = 6
    private const val WEEK_SP = 12.0

    /** How many dp [styles] (by part id, as drawn) add above the first row beyond the app's own; 0 or more. */
    fun abovePush(styles: Map<String, PartStyle>): Int {
        fun around(s: PartStyle?): Int = s?.let { 2 * ((it.out ?: 0) + (it.padV ?: 0)) } ?: 0
        val title = styles["cal.head.title"]?.let { maxOf(0.0, (it.size ?: TITLE_SP) * LINE + around(it) - HEAD_ROW_DP) } ?: 0.0
        val row = maxOf(title, around(styles["cal.head.nav"]).toDouble(), around(styles["cal.head.today"]).toDouble())
        val head = around(styles["cal.head"]) + row
        val week = styles["cal.week"]?.let { 2 * (it.out ?: 0) + 2 * ((it.padV ?: WEEK_PAD_DP) - WEEK_PAD_DP) } ?: 0
        val day = styles["cal.week.day"]?.let { maxOf(0.0, ((it.size ?: WEEK_SP) - WEEK_SP) * LINE) + around(it) } ?: 0.0
        return kotlin.math.ceil(maxOf(0.0, head + week + day)).toInt()
    }

    /** How [abovePush] is kept within [ABOVE_DP]: the letter caps and the share [k] of 바깥 간격·위아래 안 여백 kept; null when it fits. */
    class Above(val k: Double, val pushed: Int) {
        fun apply(id: String, s: PartStyle): PartStyle {
            if (id !in ABOVE || s.isEmpty) return s
            var o = s
            if (id == "cal.head.title" && (o.size ?: 0.0) > HEAD_TITLE_SP) o = o.copy(size = HEAD_TITLE_SP)
            if (id == "cal.week.day" && (o.size ?: 0.0) > WEEK_DAY_SP) o = o.copy(size = WEEK_DAY_SP)
            if (k < 1.0) {
                o.out?.let { o = o.copy(out = (it * k).toInt()) }
                // Only the top and bottom: 위아래 안 여백 as its own, the sides kept as set.
                o.padV?.let { v -> o = o.copy(padY = (v * k).toInt(), padX = o.padH) }
            }
            return o
        }
    }

    fun above(styles: Map<String, PartStyle>): Above? {
        val pushed = abovePush(styles)
        if (pushed <= ABOVE_DP) return null
        var k = 1.0
        while (k > 0.0) {
            val a = Above(k, pushed)
            if (abovePush(styles.mapValues { (id, s) -> a.apply(id, s) }) <= ABOVE_DP) return a
            k = (k - 0.05).times(100).roundToInt() / 100.0
        }
        return Above(0.0, pushed)
    }

    /** What is under [s]'s letters: its own fill laid over [ground] as strong as it is drawn, else [ground]. */
    fun groundOf(s: PartStyle, ground: Long): Long {
        val fill = s.fill ?: s.paint?.average ?: return ground or 0xFF000000L
        return mix(ground or 0xFF000000L, fill or 0xFF000000L, fillStrength(s))
    }

    /** How strongly [s]'s own fill is drawn, 0 (none) to 1. */
    fun fillStrength(s: PartStyle): Double {
        val fill = s.fill ?: s.paint?.average ?: return 0.0
        return ((fill ushr 24) and 0xFF) / 255.0 * ((s.fillA ?: 100).coerceIn(0, 100) / 100.0)
    }

    /**
     * [ink] if it reads on [ground] at [min] or better; else the same colour moved a step at a time toward whichever of black and white
     * reads better there, until it does (at worst black or white itself).
     */
    fun readable(ink: Long, ground: Long, min: Double): Long {
        val g = ground or 0xFF000000L
        val c = ink or 0xFF000000L
        if (Contrast.ratio(c, g) >= min) return ink
        val toward = if (Contrast.ratio(Contrast.BLACK, g) >= Contrast.ratio(Contrast.WHITE, g)) Contrast.BLACK else Contrast.WHITE
        for (step in 1..20) {
            val x = mix(c, toward, step / 20.0)
            if (Contrast.ratio(x, g) >= min) return x
        }
        return toward
    }

    /**
     * 공간: [out] (바깥 간격) and the padding [padH]·[padV] as drawn on a part whose short side is [shortDp]: together within a third of
     * it, each made smaller by the same share when they are not. Null when they already fit.
     */
    fun space(out: Int?, padH: Int?, padV: Int?, shortDp: Float): Triple<Int, Int, Int>? {
        val o = out ?: 0
        val h = padH ?: 0
        val v = padV ?: 0
        val room = shortDp / 3f
        val used = o + maxOf(h, v)
        if (used <= room || used == 0) return null
        val k = room / used
        return Triple((o * k).toInt(), (h * k).toInt(), (v * k).toInt())
    }

    /**
     * SN-05: the colours a screen draws besides its ground and letters, made from the two a look sets (앱 전체의 바탕 [ground], 글자 [ink]),
     * so 보조 글자·테두리·입력 칸·칩 follow a new ground instead of staying the app's own (light grey lines on a dark ground). Each
     * keeps its floor: letters 4.5:1, helper letters 4.5:1, lines a finger looks for 3:1 (WCAG 1.4.11).
     */
    class Roles(val on: Long, val onVariant: Long, val outline: Long, val outlineVariant: Long, val field: Long, val raised: Long, val sunk: Long)

    fun roles(ground: Long, ink: Long? = null): Roles {
        val g = ground or 0xFF000000L
        val plain = if (Contrast.ratio(Contrast.BLACK, g) >= Contrast.ratio(Contrast.WHITE, g)) Contrast.DARK_INK else Contrast.WHITE
        val on = readable(ink ?: plain, g, BODY) or 0xFF000000L
        return Roles(
            on = on,
            onVariant = readable(mix(on, g, 0.30), g, BODY),
            outline = readable(mix(on, g, 0.55), g, SMALL),
            outlineVariant = mix(on, g, 0.80),
            field = mix(g, on, 0.08),
            raised = mix(g, on, 0.05),
            sunk = mix(g, on, 0.03),
        )
    }

    private fun mix(a: Long, b: Long, t: Double): Long {
        fun ch(v: Long, s: Int) = ((v shr s) and 0xFF).toInt()
        fun m(s: Int) = (ch(a, s) + (ch(b, s) - ch(a, s)) * t).roundToInt().coerceIn(0, 255).toLong()
        return 0xFF000000L or (m(16) shl 16) or (m(8) shl 8) or m(0)
    }
}
