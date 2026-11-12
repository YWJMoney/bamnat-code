package com.bamnat.core.recipe

import com.bamnat.core.work.WorkStyle

/**
 * [RCP-02] 조각 사전 (설계서 4-1): every kind of piece a recipe may hold, each field with its type and range, and the kinds of
 * work it fits (유형 렌즈). The catalog *is* the format: a field not listed here is dropped on the phone and on the server
 * (결정 D106), a value out of range refuses the recipe. The server's `RECIPE_PIECES` (server/company-worker/src/recipes.js)
 * is the same table; `docs/recipe-cases.json` holds the two together.
 */
sealed interface Field {
    val key: String
    /** Absent is allowed (and means "leave mine as it is"). */
    val optional: Boolean

    /** A line of text; [lines] keeps line breaks. */
    data class Text(
        override val key: String, val max: Int, val min: Int = 0, val lines: Boolean = false, override val optional: Boolean = false,
        /** May be the receiver's answer ([InputKind.TEXT]). */
        val input: Boolean = false,
        /** A further check on the cleaned text: null passes, else the refusal's code. */
        val check: ((String) -> String?)? = null,
        /** Checked for email, phone, amount, link, chat ([RecipeText.problem]); off only for a company file. */
        val guard: Boolean = true,
    ) : Field

    /** A number; [decimals] 0 is a whole number. */
    data class Num(
        override val key: String, val lo: Double, val hi: Double, val decimals: Int = 0, override val optional: Boolean = false,
        val input: Boolean = false,
    ) : Field

    /** An amount of won: always the receiver's to decide unless the maker keeps it ([InputExtract]). */
    data class Money(override val key: String, val lo: Long = 0, val hi: Long = 100_000_000, override val optional: Boolean = false) : Field

    /** An ARGB colour, or a theme token's ([Ref.Token]), or the receiver's ([InputKind.COLOR]). */
    data class Color(override val key: String, override val optional: Boolean = false) : Field

    /** One emoji the app knows ([RecipeText.oneEmoji]). */
    data class Emoji(override val key: String, override val optional: Boolean = true, val input: Boolean = true) : Field

    data class Flag(override val key: String, override val optional: Boolean = true, val input: Boolean = true) : Field

    /** One of [values]. */
    data class Pick(override val key: String, val values: List<String>, override val optional: Boolean = false, val input: Boolean = false) : Field

    /** HH:mm. */
    data class Clock(override val key: String, override val optional: Boolean = false, val input: Boolean = true) : Field

    /** A list of short texts, each 1..[itemMax] letters. */
    data class Texts(override val key: String, val max: Int, val itemMax: Int, override val optional: Boolean = true) : Field

    /** A list of numbers. */
    data class Nums(override val key: String, val max: Int, val lo: Double, val hi: Double, val decimals: Int = 0, override val optional: Boolean = true) : Field

    /** A list of days of the week (MONDAY..SUNDAY), each once. */
    data class Days(override val key: String, override val optional: Boolean = true) : Field

    /** A list of objects of [fields], at most [max]; [check] looks at the whole cleaned list (null passes, else the refusal's code). */
    data class Items(
        override val key: String, val max: Int, val fields: List<Field>, val min: Int = 0, override val optional: Boolean = false,
        val check: ((List<Map<String, Any?>>) -> String?)? = null,
    ) : Field
}

/** 1.39 [FX-11] One act of a 동작 줄 ("more", "else"): the fields a button's or a rule's own act has. */
internal val LOGIC_ACT_MORE: List<Field> = listOf(
    Field.Texts("then", com.bamnat.core.formula.LogicAct.MAX_THEN, 12),
    Field.Num("after", 1.0, com.bamnat.core.formula.LogicAct.MAX_AFTER.toDouble(), optional = true),
    Field.Text("text", com.bamnat.core.formula.LogicAct.MAX_TEXT, optional = true, guard = false, check = com.bamnat.core.formula.Logic::textProblem),
)
internal val LOGIC_ACT: List<Field> = listOf(
    Field.Pick("do", com.bamnat.core.formula.LogicVerb.KEYS),
    Field.Text("arg", 120, optional = true, guard = false, check = com.bamnat.core.formula.Logic::textProblem),
    Field.Money("amount", optional = true),
    Field.Num("by", -com.bamnat.core.formula.LogicAct.MAX_BY, com.bamnat.core.formula.LogicAct.MAX_BY, decimals = 2, optional = true),
) + LOGIC_ACT_MORE

/** The kinds of piece, each with the kinds of work it fits and the screen it comes from. */
enum class PieceKind(val key: String, val label: String, val styles: Set<WorkStyle>, val fields: List<Field>) {
    /** [RCP-08] 근무 꾸미기: names, letters, colours and emoji of the kinds of day. */
    SHIFT_LOOK(
        "shiftLook", "근무 꾸미기", setOf(WorkStyle.SHIFT, WorkStyle.DAY, WorkStyle.HOURLY),
        listOf(
            Field.Items(
                "items", 12, min = 1,
                fields = listOf(
                    // 2.0.0 [RVD-28] SD-08: a name and a 근무 글자 by the 근무 종류 한 규칙표 (ShiftRules), as the servers read them.
                    Field.Text("name", com.bamnat.core.model.ShiftRules.NAME_LETTERS, min = 1),
                    Field.Pick("role", ShiftRole.KEYS),
                    Field.Text("label", com.bamnat.core.model.ShiftRules.LABEL_LETTERS, min = 1,
                        check = { if (RecipeText.graphemes(it) > com.bamnat.core.model.ShiftRules.LABEL_LETTERS) "label" else null }),
                    Field.Color("color"),
                    Field.Emoji("emoji"),
                ),
            ),
        ),
    ),

    /** [RCP-09] 달력 테마: the calendar's own colours and how a day with a shift is drawn. */
    CAL_THEME(
        "calTheme", "달력 테마", WorkStyle.entries.toSet(),
        listOf(
            Field.Color("today", optional = true),
            Field.Color("sat", optional = true),
            Field.Color("sun", optional = true),
            Field.Color("holiday", optional = true),
            Field.Pick("cell", CalendarTheme.CELLS, optional = true),
        ),
    ),

    /** [RCP-10] 알람 묶음: alarms before each kind of shift, added to the receiver's (never one taken away, S3). */
    ALARMS(
        "alarms", "알람 묶음", setOf(WorkStyle.SHIFT, WorkStyle.DAY, WorkStyle.HOURLY),
        listOf(
            Field.Items(
                "byRole", 6,
                fields = listOf(Field.Pick("role", ShiftRole.KEYS), Field.Nums("before", 4, 0.0, 720.0, optional = false)),
            ),
            Field.Num("snooze", 1.0, 30.0, optional = true),
            Field.Num("ring", 1.0, 30.0, optional = true),
            Field.Flag("gradual"),
            Field.Flag("vibrate"),
            Field.Pick("mission", listOf("none", "math", "shake"), optional = true),
        ),
    ),

    /** [RCP-11] 메모 틀: memos to start from (제목·체크 목록·반복). They are added as 틀, never as memos (S3). */
    MEMO_TEMPLATES(
        "memoTpl", "메모 틀", WorkStyle.entries.toSet(),
        listOf(
            Field.Items(
                "items", 8, min = 1,
                fields = listOf(
                    Field.Text("title", 30, min = 1),
                    Field.Emoji("emoji"),
                    Field.Texts("check", 20, 60),
                    Field.Text("body", 300, lines = true, optional = true),
                    Field.Text("repeat", 4, optional = true, check = { if (it.isEmpty() || REPEAT.matches(it)) null else "repeat" }),
                    // [CVW-10] 1.35 입력 칸: asked one by one when a memo starts from the template, written as "칸: 값" lines.
                    Field.Items(
                        "asks", 8, optional = true,
                        fields = listOf(
                            Field.Text("label", 20, min = 1),
                            Field.Pick("type", com.bamnat.core.memo.TemplateAsk.TYPES),
                            Field.Texts("choices", 8, 16),
                        ),
                    ),
                ),
            ),
        ),
    ),

    /** [RCP-12] 알바 근무지 규칙: how a part-time job pays, the 시급 always the receiver's. */
    HOURLY_RULES(
        "hourly", "알바 근무지 규칙", setOf(WorkStyle.HOURLY),
        listOf(
            Field.Money("wage", 0, 1_000_000, optional = true),
            Field.Flag("five"),
            Field.Flag("weeklyPay"),
            Field.Pick("weekStart", WEEK_DAYS, optional = true),
            Field.Pick("breakPay", listOf("UNPAID", "PAID", "AS_WORK"), optional = true),
            Field.Flag("legalBreaks"),
            Field.Num("night", 0.0, 200.0, optional = true),
            Field.Flag("partTime"),
            Field.Pick("rounding", listOf("HOURLY_FIRST", "AT_END", "AT_END_UP", "AT_END_DOWN"), optional = true),
            Field.Pick("tax", listOf("NONE", "WITHHOLDING_3_3", "INSURANCE_4"), optional = true),
        ),
    ),

    /** [RCP-13] 현장 단가표 틀: how a field calendar counts its work (FieldRules), the 일당 the receiver's; 작업·경비 이름 become 분류. */
    FIELD_SHEET(
        "field", "현장 단가표 틀", setOf(WorkStyle.FIELD),
        listOf(
            Field.Pick("mode", listOf("DAY_RATE", "PER_JOB", "PLATFORM")),
            Field.Money("dayRate", 0, 5_000_000, optional = true),
            Field.Nums("chips", 5, 0.1, 5.0, decimals = 2),
            Field.Items("ends", 6, optional = true, fields = listOf(Field.Clock("end"), Field.Num("units", 0.1, 5.0, decimals = 2))),
            Field.Texts("kinds", 12, 20),
            Field.Texts("costs", 12, 20),
        ),
    ),

    /** [RCP-14] 가게 영업 틀: open days and hours (ShopRules), card fee and settlement (ShopMoney), 마감 체크 and 경비 분류. */
    SHOP(
        "shop", "가게 영업 틀", setOf(WorkStyle.BUSINESS),
        listOf(
            Field.Days("open"),
            Field.Clock("opens", optional = true),
            Field.Clock("closes", optional = true),
            Field.Flag("holidaysClosed"),
            Field.Num("cardFee", 0.0, 5.0, decimals = 2, optional = true),
            Field.Num("settleDays", 0.0, 10.0, optional = true),
            Field.Flag("simpleTax"),
            Field.Num("valueAdded", 5.0, 40.0, optional = true),
            Field.Texts("closing", 20, 60),
            Field.Texts("costs", 20, 20),
        ),
    ),

    /** [RCP-15] 투자 원칙·일지 틀: 원칙 글 and the journal's fields. Never a stock pick (S6, 결정 AI-D8). */
    INVEST(
        "invest", "투자 원칙·일지 틀", setOf(WorkStyle.INVEST),
        listOf(
            Field.Items(
                "rules", 10, min = 1,
                fields = listOf(Field.Text("text", 80, min = 2, check = { if (RecipeText.investAdvice(it)) "invest_advice" else null })),
            ),
            Field.Texts("journal", 8, 20),
        ),
    ),

    /** [RCP-16] 근무 주기·급여 방식: the company file as the rule plaza shares it (bamnat-company), checked the same way. */
    COMPANY(
        "company", "근무 주기·급여 방식", setOf(WorkStyle.SHIFT, WorkStyle.DAY),
        listOf(Field.Text("setup", 24_000, min = 2, lines = true, guard = false, check = { CompanyPiece.check(it) })),
    ),

    /** [RCX-11] 1.35 묶음: other recipes by their codes, put in one after another (each piece still chosen on its own). */
    BUNDLE(
        "bundle", "작품 묶음", WorkStyle.entries.toSet(),
        listOf(
            Field.Items(
                "items", 8, min = 2,
                fields = listOf(
                    Field.Text("code", 8, min = 8, guard = false, check = { if (RecipeCodec.CODE.matches(it)) null else "code" }),
                    Field.Text("note", 40, optional = true),
                ),
            ),
        ),
    ),

    /** [RCX-12] 1.35 화면 배치: the bottom tabs' order ([com.bamnat.core.layout.TabOrder]) and the calendar page's parts. */
    LAYOUT(
        "layout", "화면 배치", WorkStyle.entries.toSet(),
        listOf(
            // 1.35 [RCX-29] six tabs since 광장 got one.
            Field.Texts("tabs", 6, 10),
            Field.Text("calendar", 400, optional = true, guard = false, check = ::layoutLine),
        ),
    ),

    /** [RCX-13] 1.35 통계·가계부·메모 카드: each page's parts in order and shown or not ([com.bamnat.core.layout.PageLayout]). */
    CARDS(
        "cards", "통계·가계부 카드", WorkStyle.entries.toSet(),
        listOf(
            Field.Text("stats", 400, optional = true, guard = false, check = ::layoutLine),
            Field.Text("money", 400, optional = true, guard = false, check = ::layoutLine),
            Field.Text("memo", 400, optional = true, guard = false, check = ::layoutLine),
        ),
    ),

    /** [RCX-14] 1.35 위젯 모양: how see-through the widgets are, light or dark, and the memo line. */
    WIDGET(
        "widget", "위젯 모양", WorkStyle.entries.toSet(),
        listOf(
            Field.Num("alpha", 30.0, 100.0, optional = true),
            Field.Pick("theme", listOf("system", "light", "dark"), optional = true),
            Field.Flag("memo"),
            // [STU-38] 1.36 위젯 꾸미기 넓히기.
            Field.Color("bg", optional = true),
            Field.Color("ink", optional = true),
            Field.Num("corner", 0.0, 28.0, optional = true),
            Field.Pick("text", WidgetLook.TEXTS, optional = true),
        ),
    ),

    /**
     * [RCX-30] 1.35 그림 꾸미기 (사용자 2026-10-30 "사진을 사용해서 버튼 모양이나 등등의 것들의 모습을 바꿀수 있게"): pictures for the
     * calendar's background and its top, each kind of day's mark and the widgets' background, by the ids of pictures the server
     * keeps (each looked at before anyone else sees it), how dim the background is drawn and how round the app's corners are.
     */
    PHOTO_LOOK(
        "photoLook", "그림 꾸미기", WorkStyle.entries.toSet(),
        listOf(
            Field.Text("bg", 16, optional = true, guard = false, check = PhotoLook::idProblem),
            Field.Num("dim", 0.0, 80.0, optional = true),
            Field.Text("head", 16, optional = true, guard = false, check = PhotoLook::idProblem),
            Field.Text("widget", 16, optional = true, guard = false, check = PhotoLook::idProblem),
            Field.Pick("corners", PhotoLook.CORNERS, optional = true),
            Field.Items(
                "icons", 6, optional = true,
                fields = listOf(Field.Pick("role", ShiftRole.KEYS), Field.Text("image", 16, min = 16, guard = false, check = PhotoLook::idProblem)),
            ),
            // [MAT-03][MAT-04] 1.36 그림 조정 (배경·머리 그림) and 틀 모양 (근무 그림·머리 그림).
            Field.Text("adjust", 60, optional = true, guard = false, check = PhotoAdjust::problem),
            Field.Text("frame", 30, optional = true, guard = false, check = PhotoFrame::problem),
        ),
    ),

    /**
     * [CVW-01] 1.35 달력 모양 (사용자 2026-10-30 "달력을 커스텀 하는부분도 자유도를 크게해서 메모중점의 달력을 만들게"): how tall the
     * month's cells are, how big the day's mark is, where the date sits, the cells' text size and lines, how many memo lines a
     * cell shows, which lines show under the mark and in what order.
     */
    CAL_VIEW(
        "calView", "달력 모양", WorkStyle.entries.toSet(),
        listOf(
            Field.Pick("size", CalendarView.SIZES, optional = true),
            Field.Pick("badge", CalendarView.BADGES, optional = true),
            Field.Pick("date", CalendarView.DATES, optional = true),
            Field.Pick("text", CalendarView.TEXTS, optional = true),
            Field.Pick("grid", CalendarView.GRIDS, optional = true),
            Field.Num("memo", 0.0, CalendarView.MAX_MEMO.toDouble(), optional = true),
            Field.Flag("hours"),
            Field.Flag("lunar"),
            Field.Flag("spend"),
            Field.Text("order", 80, optional = true, guard = false, check = CalendarView::orderProblem),
            // [FREE-01][FREE-02] 1.35 칸 디자이너: the cells' own looks, how they take turns, today's own.
            Field.Text("looks", 2400, optional = true, guard = false, check = CellLook::looksProblem),
            Field.Text("pattern", 80, optional = true, guard = false, check = CellPattern::problem),
            Field.Num("today", 0.0, 5.0, optional = true),
        ),
    ),

    /**
     * [CVW-07] 1.35 앱 동작 (사용자 2026-10-30 "상호작용부분 입력하는 부분등 모든 부분에서 자유도"): how the whole app looks and answers
     * a finger — 밝기, 기기 색, 강조색, 글자 크기, 간격, 12시간제, what a tap and a long press on a date do, the day view's order and
     * the calendar's helpers. Never the alarm's 끄기, 잠금, 동의, 결제·광고 or where a backup goes.
     */
    APP_FEEL(
        "appFeel", "앱 동작", WorkStyle.entries.toSet(),
        listOf(
            Field.Pick("theme", AppFeel.THEMES, optional = true),
            Field.Flag("dynamic"),
            Field.Color("accent", optional = true),
            Field.Num("textStep", 0.0, 4.0, optional = true),
            Field.Flag("compact"),
            Field.Flag("clock12"),
            Field.Pick("tap", AppFeel.TAPS, optional = true),
            Field.Pick("hold", AppFeel.HOLDS, optional = true),
            Field.Text("dayOrder", 120, optional = true, guard = false, check = AppFeel::dayOrderProblem),
            Field.Flag("flow"),
            Field.Flag("rest"),
            Field.Flag("payPeriod"),
            // [MAT-07] 1.36 움직임.
            Field.Text("motion", 60, optional = true, guard = false, check = Motion::problem),
        ),
    ),

    /** [FREE-04] 1.35 메모장 모양: the memo screen's layout, cards, font and background. */
    MEMO_LOOK(
        "memoLook", "메모장 모양", WorkStyle.entries.toSet(),
        listOf(
            Field.Pick("layout", MemoLook.LAYOUTS, optional = true),
            Field.Pick("card", MemoLook.CARDS, optional = true),
            Field.Color("cardColor", optional = true),
            Field.Pick("border", MemoLook.BORDERS, optional = true),
            Field.Num("corner", 0.0, 24.0, optional = true),
            Field.Num("lines", 1.0, MemoLook.MAX_LINES.toDouble(), optional = true),
            Field.Pick("title", MemoLook.TITLES, optional = true),
            Field.Flag("bold"),
            // [MAT-05] 1.36 any font of [FontCatalog] (the four of 1.35 among them) or the user's own.
            Field.Text("font", 40, optional = true, guard = false, check = FontCatalog::idProblem),
            Field.Flag("date"),
            Field.Color("bg", optional = true),
        ),
    ),

    /** [FREE-06] 1.35 가계부 모양: how the money list reads. */
    MONEY_LOOK(
        "moneyLook", "가계부 모양", WorkStyle.entries.toSet(),
        listOf(
            Field.Pick("rows", MoneyLook.ROWS, optional = true),
            Field.Pick("amount", MoneyLook.AMOUNTS, optional = true),
            Field.Pick("mark", MoneyLook.MARKS, optional = true),
            Field.Color("income", optional = true),
            Field.Color("expense", optional = true),
            Field.Pick("dayHead", MoneyLook.DAY_HEADS, optional = true),
        ),
    ),

    /**
     * [FREE-05][FREE-07] 1.35 화면 색·배경 and 급여 모양: every screen's colours and background (pictures by the server's id, looked at
     * before others see them), and 급여's look by kind of work.
     */
    SCREEN_LOOK(
        "screenLook", "화면 색·배경", WorkStyle.entries.toSet(),
        listOf(
            Field.Text("screens", 4000, optional = true, guard = false, check = ScreenLooks::problem),
            Field.Text("pay", 400, optional = true, guard = false, check = PayLook::problem),
        ),
    ),

    /** [BLU-01] 1.36 부품 꾸미기: every part of a screen with the same style and layout fields (docs/blueprint-format.md). */
    PARTS("parts", "부품 꾸미기", WorkStyle.entries.toSet(), BlueprintParts.FIELDS),


    /**
     * [RUL-03][RUL-04] 1.36 883·884 수식·규칙·버튼 (사용자 2026-11-01 "수식을 넣어서 보다 뛰어난 상호작용, 조건부도(다중 조건식도)"): buttons
     * that do one safe thing, and rules 언제 → 조건(수식) → 무엇을. Formulas are values the app runs ([com.bamnat.core.formula.Formula]);
     * only the safe verbs ([com.bamnat.core.formula.LogicVerb]) exist.
     */
    LOGIC(
        "logic", "수식·규칙·버튼", WorkStyle.entries.toSet(),
        listOf(
            Field.Items(
                "buttons", com.bamnat.core.formula.Logic.MAX_BUTTONS, optional = true,
                fields = listOf(
                    // 누락 점검 N8: what a 꾸밈 항목 tap names (LogicButton.ID; server the same).
                    Field.Text("id", 12, optional = true, guard = false, check = { s -> if (com.bamnat.core.formula.LogicButton.ID.matches(s)) null else "id" }),
                    Field.Text("label", 16, min = 1),
                    Field.Emoji("emoji", input = false),
                    Field.Pick("where", com.bamnat.core.formula.ButtonPlace.KEYS),
                    Field.Pick("do", com.bamnat.core.formula.LogicVerb.KEYS),
                    Field.Text("arg", 120, optional = true, guard = false, check = com.bamnat.core.formula.Logic::textProblem),
                    Field.Money("amount", optional = true),
                    Field.Text("show", com.bamnat.core.formula.Formula.MAX_LEN, optional = true, guard = false, check = com.bamnat.core.formula.Logic::formulaProblem),
                    // 1.39 [FX-03] 값 더하기·정하기: by how much / to what.
                    Field.Num("by", -com.bamnat.core.formula.LogicAct.MAX_BY, com.bamnat.core.formula.LogicAct.MAX_BY, decimals = 2, optional = true),
                    *LOGIC_ACT_MORE.toTypedArray(),
                    // 1.39 [FX-11] 그리고/아니면, 동작 줄.
                    Field.Text("if", com.bamnat.core.formula.Formula.MAX_LEN, optional = true, guard = false, check = com.bamnat.core.formula.Logic::formulaProblem),
                    Field.Items("more", com.bamnat.core.formula.MAX_MORE, LOGIC_ACT, optional = true),
                    Field.Items("else", com.bamnat.core.formula.MAX_ELSE, LOGIC_ACT, optional = true),
                ),
            ),
            Field.Items(
                "rules", com.bamnat.core.formula.Logic.MAX_RULES, optional = true,
                fields = listOf(
                    Field.Text("name", 20, min = 1),
                    Field.Pick("on", com.bamnat.core.formula.RuleTrigger.KEYS),
                    Field.Num("min", -720.0, 720.0, optional = true, input = true),
                    Field.Clock("at", optional = true, input = false),
                    Field.Pick("role", ShiftRole.KEYS, optional = true),
                    Field.Text("when", com.bamnat.core.formula.Formula.MAX_LEN, optional = true, guard = false, check = com.bamnat.core.formula.Logic::formulaProblem),
                    Field.Pick("do", com.bamnat.core.formula.LogicVerb.KEYS),
                    Field.Text("arg", 120, optional = true, guard = false, check = com.bamnat.core.formula.Logic::textProblem),
                    Field.Money("amount", optional = true),
                    Field.Num("by", -com.bamnat.core.formula.LogicAct.MAX_BY, com.bamnat.core.formula.LogicAct.MAX_BY, decimals = 2, optional = true),
                    // 1.39 [FX-05] 알림 단추: the buttons an 알림 carries.
                    Field.Texts("then", com.bamnat.core.formula.LogicAct.MAX_THEN, 12),
                    Field.Num("after", 1.0, com.bamnat.core.formula.LogicAct.MAX_AFTER.toDouble(), optional = true),
                    Field.Text("text", com.bamnat.core.formula.LogicAct.MAX_TEXT, optional = true, guard = false, check = com.bamnat.core.formula.Logic::textProblem),
                    // 1.39 [FX-11] 동작 줄·아니면, [FX-12] 값이 바뀌면, [FX-15] 몇 분마다.
                    Field.Items("more", com.bamnat.core.formula.MAX_MORE, LOGIC_ACT, optional = true),
                    Field.Items("else", com.bamnat.core.formula.MAX_ELSE, LOGIC_ACT, optional = true),
                    Field.Text("of", 12, optional = true, check = { s -> if (com.bamnat.core.formula.LogicValue.isName(s)) null else "name" }),
                    Field.Pick("edge", listOf("goal"), optional = true),
                    Field.Clock("until", optional = true, input = false),
                    Field.Pick("during", listOf("work"), optional = true),
                ),
            ),
            // 1.39 [FX-03] 값 칸.
            Field.Items(
                "values", com.bamnat.core.formula.LogicValue.MAX, optional = true,
                fields = listOf(
                    Field.Text("name", 12, min = 1, check = { s -> if (com.bamnat.core.formula.LogicValue.isName(s)) null else "name" }),
                    Field.Pick("reset", com.bamnat.core.formula.ValueReset.KEYS, optional = true),
                    Field.Num("start", -com.bamnat.core.formula.LogicAct.MAX_BY, com.bamnat.core.formula.LogicAct.MAX_BY, decimals = 2, optional = true),
                    // 1.39 [FX-19] 받는 사람이 정하는 목표 (칸).
                    Field.Num("goal", 0.0, com.bamnat.core.formula.LogicAct.MAX_BY, decimals = 2, optional = true, input = true),
                    Field.Text("unit", com.bamnat.core.formula.LogicValue.MAX_UNIT, optional = true),
                    Field.Pick("kind", com.bamnat.core.formula.ValueKind.KEYS, optional = true),
                ),
            ),
        ),
    ),

    /**
     * [LAY-01] 1.36 꾸밈 층: shapes, icons, emoji and text boxes placed freely over the screens' backgrounds, the calendar's top, its
     * weekday line, its day cells (pinned to days or kinds of work too) and the widgets ([Decor]). Only values, drawn by the app.
     */
    DECOR(
        "decor", "꾸밈 층", WorkStyle.entries.toSet(),
        listOf(Field.Items("items", Decor.MAX, fields = Decor.FIELDS, min = 1)),
    ),

    /**
     * [TAB-07] 1.36 탭 보기 (사용자 2026-11-01 "탭마다 엄청난 자유도"): the board the calendar is laid on (월·목록·근무 띠·타임라인), how the
     * 메모장 shows its memos (목록·보드·표·갤러리, the board's 묶음 and the order) and the 가계부's board (목록·달력·분류 보드·표). Only how
     * records are shown; never a record ([com.bamnat.core.layout.TabViews]).
     */
    TAB_VIEW(
        "tabView", "탭 보기", WorkStyle.entries.toSet(),
        listOf(
            Field.Pick("cal", com.bamnat.core.layout.CalendarBoard.KEYS, optional = true),
            Field.Pick("memo", com.bamnat.core.memo.MemoView.VIEWS, optional = true),
            Field.Text("memoBy", 22, optional = true, guard = false, check = { if (com.bamnat.core.memo.MemoView.validBy(it)) null else "memoBy" }),
            Field.Text("memoSort", 23, optional = true, guard = false, check = { if (com.bamnat.core.memo.MemoView.validSort(it)) null else "memoSort" }),
            Field.Pick("money", com.bamnat.core.money.LedgerBoard.KEYS, optional = true),
        ),
    ),

    /**
     * 1.37 [LEDH-18] 가계부 틀: the categories, monthly budgets and 모으기 봉투 someone set their 가계부 up with (자취 첫 달, 야간 간호사,
     * 배달 라이더 장부 …). Never a line written, a balance or an account; a budget or a goal's amount is the receiver's to decide
     * ([Field.Money]), and an envelope comes in empty.
     */
    LEDGER(
        "ledger", "가계부 틀", setOf(WorkStyle.SHIFT, WorkStyle.DAY, WorkStyle.HOURLY, WorkStyle.FIELD, WorkStyle.BUSINESS),
        listOf(
            Field.Texts("costs", 20, 20),
            Field.Texts("incomes", 12, 20),
            Field.Items("budgets", 12, optional = true, fields = listOf(Field.Text("category", 20, min = 1), Field.Money("amount", 0, 50_000_000))),
            Field.Items(
                "envelopes", 6, optional = true,
                fields = listOf(
                    Field.Text("name", 20, min = 1),
                    Field.Money("target", 0, 1_000_000_000, optional = true),
                    Field.Num("share", 0.0, 100.0, optional = true),
                    Field.Emoji("emoji"),
                ),
            ),
        ),
    );

    companion object {
        fun of(key: String): PieceKind? = entries.firstOrNull { it.key == key }
        val DAYS get() = WEEK_DAYS
    }
}

/** The days of the week as a recipe writes them. */
val WEEK_DAYS = listOf("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY")

/** A memo template's 반복: M<day of month> or W<1 Monday..7 Sunday> (Memo.repeat), or none. */
private val REPEAT = Regex("^(M([1-9]|[12][0-9]|3[01])|W[1-7])$")

/** A page's parts as [com.bamnat.core.layout.PageLayout] writes them ("hours,-rest,memos"): ids only, at most 40. */
private val LAYOUT_LINE = Regex("^-?[A-Za-z][A-Za-z0-9]{0,23}(,-?[A-Za-z][A-Za-z0-9]{0,23}){0,39}$")
internal fun layoutLine(s: String): String? = if (s.isEmpty() || LAYOUT_LINE.matches(s)) null else "layout"

/**
 * [RCP-08] What a kind of day is, so a recipe's 근무 꾸미기 meets the receiver's own (주간 ↔ 데이 ↔ D): its name first, then this.
 */
enum class ShiftRole(val key: String, val label: String) {
    DAY("day", "주간"), EVENING("eve", "오후·저녁"), NIGHT("night", "야간"), LONG("long", "24시간·당직"), OFF("off", "휴무·비번"), OTHER("other", "그 밖");

    companion object {
        val KEYS = entries.map { it.key }
        fun of(key: String?): ShiftRole = entries.firstOrNull { it.key == key } ?: OTHER

        private val NAMES = listOf(
            NIGHT to Regex("야|나이트|night|^n$|밤|심야", RegexOption.IGNORE_CASE),
            OFF to Regex("휴|비번|off|쉬|오프|^o$|^x$", RegexOption.IGNORE_CASE),
            LONG to Regex("당직|24|맞교대|당번|full", RegexOption.IGNORE_CASE),
            EVENING to Regex("오후|저녁|이브|eve|^e$|석|중간", RegexOption.IGNORE_CASE),
            DAY to Regex("주|데이|day|^d$|오전|아침|일근|평일|근무", RegexOption.IGNORE_CASE),
        )

        /** A kind of day's role from its name, times and whether it is off (the 근무 꾸미기 piece matches by it). */
        fun guess(name: String, off: Boolean, startHour: Int?, minutes: Int): ShiftRole {
            if (off) return OFF
            if (minutes >= 20 * 60) return LONG
            NAMES.firstOrNull { it.second.containsMatchIn(name.trim()) }?.let { if (it.first != OFF) return it.first }
            return when (startHour) {
                null -> OTHER
                in 4..10 -> DAY
                in 11..16 -> EVENING
                else -> NIGHT
            }
        }
    }
}

/**
 * [RCP-09] 달력 테마 (1.34 새 기능): the calendar's own colours for today, 토·일·공휴일 and how a working day's cell is drawn. Kept
 * in 설정 as one line ([encode]); empty is the app's own look. The colours are as chosen for the light calendar; the dark one lifts
 * them itself so they read on black.
 */
data class CalendarTheme(
    val today: Long? = null,
    val sat: Long? = null,
    val sun: Long? = null,
    val holiday: Long? = null,
    /** One of [CELLS]; null is the app's own (tint). */
    val cell: String? = null,
) {
    val isDefault: Boolean get() = this == CalendarTheme()

    fun encode(): String = if (isDefault) "" else listOfNotNull(
        today?.let { "today=" + hex(it) }, sat?.let { "sat=" + hex(it) }, sun?.let { "sun=" + hex(it) },
        holiday?.let { "holiday=" + hex(it) }, cell?.let { "cell=$it" },
    ).joinToString(";")

    companion object {
        /** tint: a light wash with the shift's badge (the app's own); fill: a stronger wash; dot: no wash, the badge only. */
        val CELLS = listOf("fill", "tint", "dot")

        private fun hex(c: Long) = (c and 0xFFFFFFFFL).toString(16).uppercase().padStart(8, '0')

        fun decode(s: String): CalendarTheme {
            if (s.isBlank()) return CalendarTheme()
            val m = s.split(';').mapNotNull { kv -> kv.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap()
            fun color(k: String) = m[k]?.toLongOrNull(16)?.let { it or 0xFF000000L }
            return CalendarTheme(color("today"), color("sat"), color("sun"), color("holiday"), m["cell"]?.takeIf { it in CELLS })
        }
    }
}

/**
 * [CVW-01] 1.35 달력 모양, as the settings keep it ("size=tall;badge=small;date=left;text=large;grid=line;memo=3;order=memo,hours"):
 * each part null is the app's own. Which lines show is still the cells' own switches (근무 시간, 음력, 쓴 돈, 메모); [order] puts the
 * shown ones in order, and [memo] is how many memo lines a cell gives (null: the first few letters of one memo, as before).
 */
data class CalendarView(
    val size: String? = null,
    val badge: String? = null,
    val date: String? = null,
    val text: String? = null,
    val grid: String? = null,
    val memo: Int? = null,
    val order: List<String>? = null,
    /** [FREE-01] The cells' own designs, at most [CellLook.MAX]; empty draws every cell by the choices above. */
    val looks: List<CellLook> = emptyList(),
    /** [FREE-02] How [looks] take turns. */
    val pattern: CellPattern = CellPattern(),
    /** [FREE-02] Today's own look among [looks], or null for the pattern's. */
    val todayLook: Int? = null,
) {
    val isDefault: Boolean get() = this == CalendarView()

    /** [FREE-02] The look of a day, or null when there are none (or it is the app's own). */
    fun lookFor(epochDay: Long, dayOfWeek: Int, role: String?, today: Boolean): CellLook? {
        if (looks.isEmpty()) return null
        val i = todayLook?.takeIf { today && it in looks.indices } ?: pattern.indexFor(epochDay, dayOfWeek, role, looks.size)
        return looks[i].takeIf { !it.isDefault }
    }

    /** The lines under the mark in this view's order: the ones it names first, then the rest in the app's own order. */
    val lines: List<String> get() = (order.orEmpty().filter { it in LINES } + LINES).distinct()

    /**
     * 2.0.0 [RVP-61] (검토 REST-02) This view with look [i] taken out, as the editor says: the days that took it take look 1, and every
     * other day keeps its own look. The pattern's numbers past [i] and [todayLook] move down by one; '몇 칸마다' takes no more than the
     * looks left. Before, only the look went, so the days of look 3 became look 2 and 수요일's look 3 fell back to look 1.
     */
    fun withoutLook(i: Int): CalendarView {
        if (i !in looks.indices) return this
        val left = looks.filterIndexed { n, _ -> n != i }
        fun moved(n: Int) = when { n == i -> 0; n > i -> n - 1; else -> n }
        val p = when (pattern.mode) {
            CellPattern.WEEK -> pattern.copy(week = pattern.week.map(::moved))
            CellPattern.SHIFT -> pattern.copy(shift = pattern.shift.mapValues { moved(it.value) })
            CellPattern.CYCLE -> if (left.size >= 2) pattern.copy(n = pattern.n.coerceAtMost(left.size).coerceAtLeast(2)) else CellPattern()
            else -> pattern
        }
        val today = todayLook?.let { if (it == i) null else moved(it) }
        return copy(looks = if (left.size <= 1 && left.firstOrNull()?.isDefault != false) emptyList() else left,
            pattern = if (left.size <= 1) CellPattern() else p, todayLook = today?.takeIf { left.size > 1 })
    }

    fun encode(): String = if (isDefault) "" else listOfNotNull(
        size?.let { "size=$it" }, badge?.let { "badge=$it" }, date?.let { "date=$it" }, text?.let { "text=$it" }, grid?.let { "grid=$it" },
        memo?.let { "memo=$it" }, order?.takeIf { it.isNotEmpty() }?.let { "order=" + it.joinToString(",") },
        looks.takeIf { it.isNotEmpty() }?.let { "looks=" + CellLook.encodeAll(it) }, pattern.takeIf { !it.isDefault }?.let { "pattern=" + it.encode() },
        todayLook?.let { "today=$it" },
    ).joinToString(";")

    companion object {
        /** short ×1.0, normal ×1.3 (the app's own), tall ×1.9, huge ×2.7 of a cell's width. */
        val SIZES = listOf("short", "normal", "tall", "huge")
        /** big: the 22dp badge (the app's own); small: 16dp; line: a bar in the day's colour, leaving the room to the lines. */
        val BADGES = listOf("big", "small", "line")
        val DATES = listOf("center", "left")
        val TEXTS = listOf("small", "normal", "large")
        /** none: cells float (the app's own); line: a thin line round each cell, like a paper diary. */
        val GRIDS = listOf("none", "line")
        const val MAX_MEMO = 4
        /** What can stand under a day's mark, in the app's own order. */
        val LINES = listOf("holiday", "memo", "money", "hours", "spend", "lunar")

        /**
         * [CELL-02] 1.39 (사용자 2026-11-07 "테마를 입으면 달력에 적어 둔 것이 안 보여요"): what a 달력 모양 piece says about *what* the cells
         * show (메모 줄 수, 줄 순서, 근무 시간·음력·지출) rather than how they look. Someone else's theme leaves these as the user set them.
         */
        val CONTENT_KEYS = setOf("memo", "order", "hours", "lunar", "spend")

        fun orderProblem(s: String): String? =
            if (s.isEmpty() || s.split(',').let { l -> l.all { it in LINES } && l.size == l.distinct().size }) null else "order"

        fun decode(s: String): CalendarView {
            if (s.isBlank()) return CalendarView()
            val m = s.split(';').mapNotNull { kv -> kv.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap()
            return CalendarView(
                size = m["size"]?.takeIf { it in SIZES }, badge = m["badge"]?.takeIf { it in BADGES }, date = m["date"]?.takeIf { it in DATES },
                text = m["text"]?.takeIf { it in TEXTS }, grid = m["grid"]?.takeIf { it in GRIDS },
                memo = m["memo"]?.toIntOrNull()?.coerceIn(0, MAX_MEMO),
                order = m["order"]?.split(',')?.filter { it in LINES }?.distinct()?.takeIf { it.isNotEmpty() },
                looks = m["looks"]?.takeIf { CellLook.looksProblem(it) == null }?.let(CellLook::decodeAll).orEmpty(),
                pattern = CellPattern.decode(m["pattern"]),
                todayLook = m["today"]?.toIntOrNull()?.takeIf { it in 0 until CellLook.MAX },
            )
        }

        /**
         * Ready-made looks, so a first try is one tap: 메모 중심 (tall cells, the date at the left, a bar for the shift and three
         * memo lines first), 다이어리 (huge cells with lines, four memo lines), 한눈에 (short cells, big marks) and the app's own.
         */
        val PRESETS: List<Pair<String, CalendarView>> = listOf(
            "기본" to CalendarView(),
            "메모 중심" to CalendarView(size = "tall", badge = "line", date = "left", memo = 3, order = listOf("memo", "holiday")),
            "다이어리" to CalendarView(size = "huge", badge = "small", date = "left", grid = "line", memo = 4, order = listOf("memo", "holiday", "hours")),
            "한눈에" to CalendarView(size = "short", badge = "big", text = "small"),
            "큰 글씨" to CalendarView(size = "tall", badge = "big", text = "large"),
        )
    }
}

/**
 * [CELL-02] 1.39 Someone else's theme as a look only: its 달력 모양 keeps how the cells look (크기·표시·날짜 자리·칸 디자인·패턴) and leaves
 * what they show ([CalendarView.CONTENT_KEYS]) as the user has it, so memos and jobs written on the calendar stay in sight.
 */
fun Recipe.keepMyCellContent(): Recipe = copy(pieces = pieces.map { p ->
    if (p.kind == PieceKind.CAL_VIEW) p.copy(values = p.values - CalendarView.CONTENT_KEYS) else p
})

/** [CVW-07] 1.35 앱 동작's choices, as a recipe writes them. */
object AppFeel {
    val THEMES = listOf("system", "light", "dark", "night")
    val TAPS = listOf("preview", "sheet", "week")
    /** menu: the quick menu (the app's own); memo: a memo for that day at once; open: the whole day. */
    val HOLDS = listOf("menu", "memo", "open")
    /** The day view's groups (DayGroupKey). */
    val DAY_GROUPS = listOf("work", "extra", "leave", "alarm", "memo", "money", "body", "record")

    fun dayOrderProblem(s: String): String? =
        if (s.isEmpty() || s.split(',').let { l -> l.all { it in DAY_GROUPS } && l.size == l.distinct().size }) null else "dayOrder"
}

/** [RCP-16] The company file inside a recipe: the phone's own reader checks it whole when it is previewed. */
object CompanyPiece {
    /** A quick check of the text's shape (JSON object of format bamnat-company); null passes. */
    fun check(text: String): String? {
        val parsed = runCatching { com.bamnat.core.remote.Json.parse(text) }.getOrNull() as? Map<*, *> ?: return "setup"
        return if (parsed["format"] == "bamnat-company") null else "setup"
    }
}

/**
 * [RCX-14] 1.35 위젯 모양, as the settings keep it ("alpha=80;theme=dark"): how opaque the widgets' background is (30..100 %) and
 * whether it follows the phone's light or dark screen. Empty is the app's own (100 %, following the phone).
 */
data class WidgetLook(
    val alpha: Int = 100,
    val theme: String = "system",
    /** [STU-38] 1.36 위젯 꾸미기 넓히기: the background's own colour (null: the theme's), the text's, the corners (0..28 dp) and the text size. */
    val bg: Long? = null,
    val ink: Long? = null,
    val corner: Int? = null,
    val text: String? = null,
) {
    val isDefault: Boolean get() = this == WidgetLook()

    fun encode(): String = if (isDefault) "" else (listOf("alpha=$alpha", "theme=$theme") + listOfNotNull(
        bg?.let { "bg=" + hex(it) }, ink?.let { "ink=" + hex(it) }, corner?.let { "corner=$it" }, text?.let { "text=$it" },
    )).joinToString(";")

    /** [STU-38] How much bigger or smaller the widget's text is drawn. */
    val textScale: Float get() = when (text) { "s" -> 0.88f; "l" -> 1.15f; else -> 1f }

    companion object {
        val THEMES = listOf("system", "light", "dark")
        val TEXTS = listOf("s", "m", "l")
        private fun hex(c: Long) = (c and 0xFFFFFFFFL).toString(16).uppercase().padStart(8, '0')
        private fun color(v: String?): Long? = v?.takeIf { Regex("^[0-9A-Fa-f]{8}$").matches(it) }?.toLongOrNull(16)?.let { it or 0xFF000000L }

        fun decode(s: String): WidgetLook {
            if (s.isBlank()) return WidgetLook()
            val m = s.split(';').mapNotNull { kv -> kv.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap()
            return WidgetLook(m["alpha"]?.toIntOrNull()?.coerceIn(30, 100) ?: 100, m["theme"]?.takeIf { it in THEMES } ?: "system",
                color(m["bg"]), color(m["ink"]), m["corner"]?.toIntOrNull()?.takeIf { it in 0..28 }, m["text"]?.takeIf { it in TEXTS })
        }
    }
}

/**
 * [RCX-30] 1.35 그림 꾸미기, as the settings keep it ("bg=…;dim=30;head=…;widget=…;corners=soft;icon.12=…"): pictures by id
 * (16 hex: a file the phone keeps under looks/, the server's id when it came in a recipe), the background's dimming (0..80 %)
 * and the corners. [icons] is by the kind of day on this phone, keyed with its calendar ([R200-01] [com.bamnat.core.model.ShiftRef]).
 * Empty is the app's own look.
 */
data class PhotoLook(
    val bg: String? = null,
    val dim: Int = DIM,
    val head: String? = null,
    val widget: String? = null,
    val corners: String? = null,
    val icons: Map<String, String> = emptyMap(),
    /** [MAT-03] 1.36 배경·머리 그림 조정. */
    val adjust: PhotoAdjust = PhotoAdjust(),
    /** [MAT-04] 1.36 근무 그림·머리 그림의 틀 모양 ([PhotoFrame]); null is the app's own (둥근 네모). */
    val frame: String? = null,
) {
    val isDefault: Boolean get() = this == PhotoLook()

    /** Every picture it uses, so the ones nothing uses can be let go. */
    val images: Set<String> get() = setOfNotNull(bg, head, widget) + icons.values

    /** [R200-01] The picture of [calendarId]'s kind of day [shiftId] (null calendar: the main one). */
    fun iconFor(calendarId: String?, shiftId: Long): String? = icons[com.bamnat.core.model.ShiftRef.key(calendarId, shiftId)]

    fun encode(): String = if (isDefault) "" else listOfNotNull(
        bg?.let { "bg=$it" }, "dim=$dim".takeIf { dim != DIM }, head?.let { "head=$it" }, widget?.let { "widget=$it" },
        corners?.let { "corners=$it" }, adjust.takeIf { !it.isDefault }?.let { "adjust=" + it.encode() }, frame?.let { "frame=$it" },
    ).plus(icons.entries.sortedWith(compareBy({ com.bamnat.core.model.ShiftRef.calendarOf(it.key) != com.bamnat.core.model.ShiftRef.MAIN }, { it.key.length }, { it.key })).map { (t, id) -> "icon.$t=$id" }).joinToString(";")

    companion object {
        const val DIM = 30
        /** round: very round; soft: a little (the app's own, between); square: almost none. */
        val CORNERS = listOf("round", "soft", "square")
        private val ID = Regex("^[0-9a-f]{16}$")

        fun isId(s: String?): Boolean = s != null && ID.matches(s)
        fun idProblem(s: String): String? = if (s.isEmpty() || ID.matches(s)) null else "image"

        fun decode(s: String): PhotoLook {
            if (s.isBlank()) return PhotoLook()
            val m = s.split(';').mapNotNull { kv -> kv.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }
            val one = m.toMap()
            return PhotoLook(
                bg = one["bg"]?.takeIf(::isId), dim = one["dim"]?.toIntOrNull()?.coerceIn(0, 80) ?: DIM,
                head = one["head"]?.takeIf(::isId), widget = one["widget"]?.takeIf(::isId),
                corners = one["corners"]?.takeIf { it in CORNERS },
                icons = m.mapNotNull { (k, v) -> k.removePrefix("icon.").takeIf { k.startsWith("icon.") && com.bamnat.core.model.ShiftRef.isKey(it) && isId(v) }?.let { it to v } }.toMap(),
                adjust = PhotoAdjust.decode(one["adjust"]), frame = one["frame"]?.takeIf { it.isNotEmpty() && PhotoFrame.problem(it) == null },
            )
        }
    }
}
