package com.bamnat.core.recipe

import com.bamnat.core.work.WorkStyle

/**
 * [FREE-09] 1.35 추천 테마 (사용자 2026-10-30 15:47 "하이엔드 테마를 각 근무유형별로 5~10개씩 만들어 놔서… 이렇게도 테마를 바꿀 수 있구나를
 * 인지"): eight themes for each kind of work, made only of look pieces (화면 색·배경, 칸 모양·패턴, 달력 색, 메모장·가계부·급여 모양, 위젯,
 * 모서리). A theme never brings 알람, 급여 규칙, 메모 틀 or a 근무 주기, so putting one on or taking it off never touches a record
 * (사용자 2026-10-30 15:56 "테마는 데이터의 순서 위치 보여지는 방식을 바꾸는 것이지 데이터 자체를 건들 수는 없어").
 * The app shows them in 화면 꾸미기 and the 광장; `docs/recipes/theme-recipes.json` is the same set for the operator's 보여주기.
 */
object BuiltInThemes {
    /** The pieces a theme may carry: looks only (ThemeLease runs on these and only these). */
    val LOOK_KINDS: Set<PieceKind> = setOf(
        PieceKind.SCREEN_LOOK, PieceKind.CAL_VIEW, PieceKind.CAL_THEME, PieceKind.MEMO_LOOK, PieceKind.MONEY_LOOK, PieceKind.WIDGET,
        PieceKind.PHOTO_LOOK, PieceKind.LAYOUT, PieceKind.CARDS,
    )

    private class Family(
        val name: String, val emoji: String, val mood: String,
        val bg: Long, val card: Long, val ink: Long, val accent: Long,
        val today: Long, val sat: Long, val sun: Long,
        /** Cell looks, the pattern and today's own look. */
        val cells: List<String>, val pattern: String, val todayLook: Int?,
        val memo: MemoLook, val money: MoneyLook, val pay: PayLook,
        val widgetAlpha: Long, val widgetTheme: String, val corners: String, val dark: Boolean = false,
        /** The calendar's cell height; 교대 takes one step taller for the marks. */
        val size: String = "normal",
    )

    private val families = listOf(
        Family("벚꽃 파스텔", "🌸", "연분홍 바탕에 근무 색이 은은하게 번지는",
            0xFFFFF5F7, 0xFFFFFFFF, 0xFF4A2C36, 0xFFB0305A, 0xFFB0305A, 0xFF3949AB, 0xFFC62828,
            listOf("bg:shift|ba:22|cr:12|dp:tl|dw:b|mk:dot|mp:bottom|pd:3", "bg:FFFFE4EC|ba:60|cr:12|dp:tl|mk:none|pd:3",
                "bg:FFB0305A|cr:12|dp:mc|ds:l|dw:b|dc:FFFFFFFF|mk:dot|mp:bottom|pd:2"), "shift:off=1", 2,
            MemoLook(layout = "sticky", card = "own", corner = 4, lines = 6, font = "hand"),
            MoneyLook(rows = "roomy", amount = "l", dayHead = "band", income = 0xFF2E7D32, expense = 0xFFC2185B),
            PayLook(total = "xl", plus = 0xFFFFE4EC, rows = "roomy"), 92, "light", "round"),
        Family("미드나잇 네온", "🌙", "깊은 남색 화면에 보랏빛 선이 빛나는",
            0xFF0E1020, 0xFF1A1D33, 0xFFE8EAF6, 0xFF8C7CFF, 0xFFB3A8FF, 0xFF8FA8FF, 0xFFFF8A9A,
            listOf("bg:shift|ba:30|bd:thin|bc:FF2E3358|cr:6|dp:tc|mk:line|mp:bottom|pd:2", "bg:FF14172A|bd:thin|bc:FF2E3358|cr:6|dp:tc|mk:line|mp:bottom|pd:2",
                "bg:FF8C7CFF|ba:35|bd:thick|bc:FF8C7CFF|cr:6|dp:tc|dw:b|mk:line|mp:bottom|pd:2"), "cycle:2", 2,
            MemoLook(layout = "grid2", card = "one", cardColor = 0xFF1A1D33, border = "thin", corner = 10, lines = 5, font = "mono"),
            MoneyLook(rows = "compact", amount = "m", mark = "dot", income = 0xFF69F0AE, expense = 0xFFFF8A80, dayHead = "line"),
            PayLook(total = "xl", plus = 0xFF262A4A, minus = 0xFFFF8A80), 70, "dark", "soft", dark = true),
        Family("종이 다이어리", "📔", "미색 종이에 점선 칸, 명조체 메모가 어울리는",
            0xFFFBF6EC, 0xFFFFFDF7, 0xFF3E3328, 0xFF6D4C41, 0xFF6D4C41, 0xFF3949AB, 0xFFB71C1C,
            listOf("bg:none|bd:dash|bc:FFBCAAA4|cr:0|dp:tl|ds:s|mk:small|mp:below|pd:2", "bg:FFF3E9D7|bd:dash|bc:FFBCAAA4|cr:0|dp:tl|ds:s|mk:small|mp:below|pd:2"),
            "week:0,0,0,0,0,1,1", null,
            MemoLook(layout = "diary", card = "none", font = "serif", lines = 8, bg = 0xFFFBF6EC),
            MoneyLook(rows = "normal", amount = "m", mark = "none", dayHead = "big"),
            PayLook(total = "l", rows = "normal", open = true), 95, "light", "square"),
        Family("숲속 민트", "🌿", "칸 전체를 근무 색으로 칠해 다음 근무가 한눈에 보이는",
            0xFFEFF7F2, 0xFFFFFFFF, 0xFF1F3A2D, 0xFF2E7D5B, 0xFF2E7D5B, 0xFF1565C0, 0xFFC62828,
            listOf("mk:fill|ba:80|cr:8|dp:mc|ds:l|dw:b|pd:2", "bg:FFE3F1E8|cr:8|dp:mc|ds:l|mk:none|pd:2"), "shift:off=1", null,
            MemoLook(layout = "grid2", card = "own", corner = 14, lines = 4),
            MoneyLook(rows = "roomy", amount = "l", mark = "emoji", income = 0xFF2E7D5B, expense = 0xFFC62828, dayHead = "band"),
            PayLook(total = "xl", plus = 0xFFE3F1E8, rows = "roomy", days = true), 90, "light", "round"),
        Family("모노 미니멀", "⚪", "흑백만 남겨 숫자와 근무 표시만 또렷한",
            0xFFFFFFFF, 0xFFF6F6F6, 0xFF111111, 0xFF111111, 0xFF111111, 0xFF3949AB, 0xFFB00020,
            listOf("bg:none|bd:none|cr:0|dp:tc|ds:s|mk:small|mp:below|pd:1", "bg:none|cr:0|dp:tc|ds:s|dsh:circle|df:FF111111|dc:FFFFFFFF|dw:b|mk:small|mp:below|pd:1"),
            "one", 1,
            MemoLook(layout = "compact", card = "none", lines = 1, date = false),
            MoneyLook(rows = "compact", amount = "s", mark = "none", dayHead = "line"),
            PayLook(total = "m", rows = "compact"), 100, "light", "square"),
        Family("선셋 노을", "🌅", "노을빛 세 가지 칸이 번갈아 물드는",
            0xFFFFF3E8, 0xFFFFFFFF, 0xFF3D2314, 0xFFBF360C, 0xFFBF360C, 0xFF1565C0, 0xFFC62828,
            listOf("bg:FFFFE0CC|ba:70|cr:16|dp:tc|dsh:circle|df:FFFFFFFF|mk:dot|mp:bottom|pd:2",
                "bg:FFFFD6B8|ba:70|cr:16|dp:tc|dsh:circle|df:FFFFFFFF|mk:dot|mp:bottom|pd:2",
                "bg:FFFFC9A3|ba:70|cr:16|dp:tc|dsh:circle|df:FFFFFFFF|mk:dot|mp:bottom|pd:2"), "cycle:3", null,
            MemoLook(layout = "grid2", card = "one", cardColor = 0xFFFFFFFF, corner = 16, lines = 5, title = "l", bold = true),
            MoneyLook(rows = "roomy", amount = "l", dayHead = "band", income = 0xFF2E7D32, expense = 0xFFD84315),
            PayLook(total = "xl", plus = 0xFFFFE0CC), 92, "light", "round"),
        Family("오션 블루", "🌊", "맑은 하늘색 바탕에 근무 표시가 크게 보이는",
            0xFFEAF4FB, 0xFFFFFFFF, 0xFF0D2B45, 0xFF1565C0, 0xFF1565C0, 0xFF1565C0, 0xFFC62828,
            listOf("bg:shift|ba:35|bd:thin|bc:FFBBDEFB|cr:8|dp:tl|dw:b|mk:big|mp:side|pd:2", "bg:FF1565C0|cr:8|dp:tl|dw:b|dc:FFFFFFFF|mk:big|mp:side|pd:2"),
            "one", 1,
            MemoLook(layout = "list", card = "own", border = "thin", corner = 12, lines = 3),
            MoneyLook(rows = "normal", amount = "m", mark = "emoji", income = 0xFF00796B, expense = 0xFFD32F2F, dayHead = "band"),
            PayLook(total = "l", rows = "normal", open = true), 90, "light", "soft"),
        Family("다크 골드", "✨", "검은 바탕에 금빛 테두리가 고급스러운",
            0xFF121212, 0xFF1E1E1E, 0xFFF5E6C8, 0xFFD4AF37, 0xFFD4AF37, 0xFF9FB4FF, 0xFFFF8A80,
            listOf("bg:FF1E1E1E|bd:thin|bc:FF6B5A2A|cr:6|dp:tl|dsh:square|df:FF2A2A2A|mk:dot|mp:bottom|pd:2",
                "bg:FF2A2414|bd:thick|bc:FFD4AF37|cr:6|dp:tl|dw:b|dsh:square|df:FFD4AF37|dc:FF121212|mk:dot|mp:bottom|pd:2"), "one", 1,
            MemoLook(layout = "list", card = "one", cardColor = 0xFF1E1E1E, border = "thin", corner = 6, lines = 4, title = "l", font = "serif"),
            MoneyLook(rows = "roomy", amount = "l", dayHead = "big", income = 0xFFA5D6A7, expense = 0xFFFF8A80),
            PayLook(total = "xl", plus = 0xFF2A2414, minus = 0xFFFF8A80), 75, "dark", "soft", dark = true),
    )

    private fun word(s: WorkStyle) = when (s) {
        WorkStyle.SHIFT -> "교대"; WorkStyle.DAY -> "일근"; WorkStyle.HOURLY -> "알바"
        WorkStyle.FIELD -> "현장"; WorkStyle.BUSINESS -> "가게"; WorkStyle.INVEST -> "투자"
    }

    private fun who(s: WorkStyle) = when (s) {
        WorkStyle.SHIFT -> "교대 근무로 다음 근무를 자주 확인하는 분"
        WorkStyle.DAY -> "평일 출근과 주말이 또렷하길 바라는 일근직"
        WorkStyle.HOURLY -> "근무 날과 주휴·급여를 한눈에 보고 싶은 알바생"
        WorkStyle.FIELD -> "현장 날짜와 받을 돈을 크게 보고 싶은 분"
        WorkStyle.BUSINESS -> "영업일과 매출을 산뜻하게 보고 싶은 사장님"
        WorkStyle.INVEST -> "매매 날과 일지를 차분하게 정리하는 분"
    }

    /** Each kind of work's own touch on a family: 교대 marks by kind of day, 가게 by the week, 투자 by turns. */
    private fun patternFor(f: Family, s: WorkStyle): String = when {
        f.cells.size - (if (f.todayLook != null) 1 else 0) < 2 -> f.pattern
        s == WorkStyle.SHIFT && f.pattern.startsWith("week") -> "shift:off=1"
        s != WorkStyle.SHIFT && f.pattern.startsWith("shift") -> "week:0,0,0,0,0,1,1"
        else -> f.pattern
    }

    private fun theme(f: Family, s: WorkStyle): Recipe {
        val screens = ScreenLooks()
            .with(ScreenLooks.ALL, ScreenLook(bg = f.bg, card = f.card, ink = f.ink, accent = f.accent))
            .with(ScreenLooks.PLAZA, ScreenLook(emoji = f.emoji))
        val pattern = patternFor(f, s)
        val m = f.memo
        val pieces = listOf(
            Piece(PieceKind.SCREEN_LOOK, mapOf("screens" to screens.encode(), "pay" to PayLook.encodeAll(mapOf(s to f.pay)))),
            Piece(PieceKind.CAL_VIEW, listOfNotNull(
                "looks" to f.cells.joinToString("/"), "pattern" to pattern, f.todayLook?.let { "today" to it.toLong() },
                "size" to (if (s == WorkStyle.SHIFT) "tall" else f.size),
            ).toMap()),
            Piece(PieceKind.CAL_THEME, mapOf("today" to f.today, "sat" to f.sat, "sun" to f.sun)),
            Piece(PieceKind.MEMO_LOOK, mapOf("layout" to m.layout, "card" to m.card, "cardColor" to m.cardColor, "border" to m.border,
                "corner" to m.corner?.toLong(), "lines" to m.lines?.toLong(), "title" to m.title, "bold" to m.bold, "font" to m.font,
                "date" to m.date, "bg" to m.bg).filterValues { it != null }),
            Piece(PieceKind.MONEY_LOOK, mapOf("rows" to f.money.rows, "amount" to f.money.amount, "mark" to f.money.mark,
                "income" to f.money.income, "expense" to f.money.expense, "dayHead" to f.money.dayHead).filterValues { it != null }),
            Piece(PieceKind.WIDGET, mapOf("alpha" to f.widgetAlpha, "theme" to f.widgetTheme)),
            Piece(PieceKind.PHOTO_LOOK, mapOf("corners" to f.corners)),
        )
        val looksCount = f.cells.size
        val desc = "누구에게 좋아요: ${who(s)}\n" +
            "적용하면 바뀌는 것:\n" +
            "· 앱 전체 바탕·카드·글자·강조 색${if (f.dark) " (어두운 화면)" else ""}\n" +
            "· 달력 칸 모양 ${looksCount}가지, ${patternWords(pattern)}${f.todayLook?.let { ", 오늘 칸 따로" }.orEmpty()}\n" +
            "· 메모장 ${RecipeDescribe.FREE_NAMES["layout.${m.layout}"]}, 가계부·급여 화면 모양\n" +
            "· 광장 ${f.emoji}, 위젯·모서리\n" +
            "주의할 점: 모양만 바뀌어요. 근무·메모·돈 기록과 알람은 그대로예요"
        val cover = Cover(f.accent or 0xFF000000L, f.bg or 0xFF000000L, 2, f.emoji)
        return Recipe("${f.name} ${word(s)}", "${f.mood} ${word(s)} 테마", desc, setOf(s), cover, pieces = pieces)
    }

    private fun patternWords(p: String) = when {
        p.startsWith("cycle:") -> "${p.substringAfter(':')}칸씩 번갈아"
        p.startsWith("week") -> "평일·주말 따로"
        p.startsWith("shift") -> "쉬는 날 따로"
        else -> "모든 칸 같게"
    }

    /** Every theme, eight for each kind of work. */
    val all: List<Recipe> by lazy { WorkStyle.entries.flatMap { s -> families.map { theme(it, s) } } }

    /** The themes for [style] (all of them for no kind of work). */
    fun of(style: WorkStyle?): List<Recipe> = if (style == null) all.filter { it.styles == setOf(WorkStyle.SHIFT) } else all.filter { style in it.styles }

    /** Whether a recipe is only looks, so it is a theme ([LOOK_KINDS]). */
    fun isTheme(r: Recipe): Boolean = r.pieces.isNotEmpty() && r.pieces.all { it.kind in LOOK_KINDS } &&
        r.pieces.any { it.kind == PieceKind.SCREEN_LOOK || it.kind == PieceKind.CAL_VIEW }
}
