package com.bamnat.core.formula

import com.bamnat.core.recipe.RecipeText

/**
 * [RUL-03][RUL-04] 1.36 883 버튼 · 884 규칙 엔진: the 'logic' piece of a recipe (수식·규칙·버튼), as the app keeps and runs it.
 *
 * - A [LogicButton] sits on the day screen, the memo screen or above the calendar; a press does one [LogicAct].
 * - A [LogicRule] is 언제 ([LogicRule.on]) → 조건 (a formula, [LogicRule.cond]) → 무엇을 ([LogicRule.act]), like 단축어·Home Assistant.
 * - Only the safe verbs in [LogicVerb] exist. Never 지우기, 밖으로 보내기, 결제, 알람 끄기, 잠금 or 동의 (계획 1장 2항). The verbs that write
 *   data ([LogicVerb.writes]) are said first when the recipe is put on and can be taken back.
 */
data class Logic(
    val buttons: List<LogicButton> = emptyList(),
    val rules: List<LogicRule> = emptyList(),
    /** 1.39 [FX-03] 값 칸: the numbers this work counts ([LogicValue]). */
    val values: List<LogicValue> = emptyList(),
) {
    val isEmpty: Boolean get() = buttons.isEmpty() && rules.isEmpty() && values.isEmpty()

    /** 1.39 [FX-03] The 값 칸 named [name]. */
    fun value(name: String): LogicValue? = values.firstOrNull { it.name == name }

    /** Does anything in it write the user's data (메모 만들기, 가계부 넣기, 체크 다시)? */
    val writes: Boolean get() = buttons.any { b -> b.acts.any { it.verb.writes } } || rules.any { r -> r.acts.any { it.verb.writes } }

    fun toValues(): Map<String, Any?> = buildMap {
        if (buttons.isNotEmpty()) put("buttons", buttons.map { it.toMap() })
        if (rules.isNotEmpty()) put("rules", rules.map { it.toMap() })
        if (this@Logic.values.isNotEmpty()) put("values", this@Logic.values.map { it.toMap() })
    }

    /**
     * 1.36 [SEC-12] 보안 점검 M4: these as they come in from someone else's recipe: a 가계부 amount is held to [RECEIVED_MAX_WON]
     * (a prank or a mistake cannot put 1억 원 in each day). The phone's own buttons and rules are never held.
     */
    fun received(): Logic = Logic(
        buttons.map { it.copy(act = it.act.held(), more = it.more.map(LogicAct::held), otherwise = it.otherwise.map(LogicAct::held)) },
        rules.map { it.copy(act = it.act.held(), more = it.more.map(LogicAct::held), otherwise = it.otherwise.map(LogicAct::held)) }, values)

    /** Whether [received] would hold an amount down. */
    val overReceived: Boolean get() = received() != this

    /** 1.36 [SEC-12] The rules that write data and run by themselves or by a tap (not a press of a button the user put there). */
    val writingRules: List<LogicRule> get() = rules.filter { r -> r.acts.any { it.verb.writes } }

    /**
     * PC D6: [other]'s rules and buttons that do the same as one of these (same 언제·조건·무엇을, the name aside) and so are kept once
     * by [plus], as "theirs = mine" names, to say so.
     */
    fun same(other: Logic): List<Pair<String, String>> =
        other.rules.mapNotNull { r -> rules.firstOrNull { it.key == r.key }?.let { r.name to it.name } } +
            other.buttons.mapNotNull { b -> buttons.firstOrNull { it.toMap() == b.toMap() }?.let { b.label to it.label } }

    /** PC D6: what [plus] leaves out for the limits (8 buttons, 12 rules). */
    fun overLimit(other: Logic): Int {
        val p = plus(other)
        val wanted = buttons.size + rules.size + other.buttons.size + other.rules.size - same(other).size
        return (wanted - p.buttons.size - p.rules.size).coerceAtLeast(0)
    }

    /** One line for [same] and [overLimit], or null: "‘A 야간 알림’은 내 ‘B 내 야간 규칙’과 하는 일이 같아 하나로 뒀어요". */
    fun mergeSay(other: Logic): String? {
        val s = same(other)
        val over = overLimit(other)
        return listOfNotNull(
            // PC 03111522 ('‘커피’은'): 은/는 and 과/와 by the word's last letter.
            s.takeIf { it.isNotEmpty() }?.let { l -> l.joinToString(", ") { (t, m) -> "‘$t’" + com.bamnat.core.recipe.RecipeLint.topic(t) + (if (t != m) " 내 ‘$m’" + (if (com.bamnat.core.recipe.RecipeLint.topic(m) == "은") "과" else "와") else " 이미 있는 것과") } + " 하는 일이 같아 하나로 뒀어요" },
            over.takeIf { it > 0 }?.let { "한도(단추 ${MAX_BUTTONS}개·규칙 ${MAX_RULES}개)를 넘는 ${it}개는 넣지 않았어요" },
        ).joinToString(". ").ifEmpty { null }
    }

    /** [RUL-07] 보관함 빼기: these without [other]'s buttons and rules (what [plus] added). */
    fun minus(other: Logic): Logic = Logic(
        buttons.filter { b -> other.buttons.none { it.toMap() == b.toMap() } },
        rules.filter { r -> other.rules.none { it.key == r.key } },
        values.filter { v -> other.values.none { it.name == v.name } },
    )

    /** 누락 점검 N8: the button a 꾸밈 항목's `tap` names. */
    fun button(id: String): LogicButton? = buttons.firstOrNull { it.id == id }

    /** [RUL-07] [other]'s buttons and rules after these (one already here is not added again), within the limits. */
    fun plus(other: Logic): Logic = Logic(
        (buttons + other.buttons.filter { b -> buttons.none { it.toMap() == b.toMap() } }).take(MAX_BUTTONS),
        (rules + other.rules.filter { r -> rules.none { it.key == r.key } }).take(MAX_RULES),
        // 1.39 [FX-03] A 값 칸 of the same name is the same number (two works counting 물 잔 count one).
        (values + other.values.filter { v -> values.none { it.name == v.name } }).take(LogicValue.MAX),
    )

    companion object {
        const val MAX_BUTTONS = 8
        const val MAX_RULES = 12
        /** 1.36 [SEC-12] The most one act of a received recipe puts in the 가계부. */
        const val RECEIVED_MAX_WON = 1_000_000L

        /** From the piece's values as [com.bamnat.core.recipe.RecipeCodec] checked them; what the app cannot use is left out. */
        @Suppress("UNCHECKED_CAST")
        fun from(values: Map<String, Any?>?): Logic {
            if (values == null) return Logic()
            val b = (values["buttons"] as? List<Map<String, Any?>>).orEmpty().mapNotNull(LogicButton::from).take(MAX_BUTTONS)
            val r = (values["rules"] as? List<Map<String, Any?>>).orEmpty().mapNotNull(LogicRule::from).take(MAX_RULES)
            val v = (values["values"] as? List<Map<String, Any?>>).orEmpty().mapNotNull(LogicValue::from).distinctBy { it.name }.take(LogicValue.MAX)
            return Logic(b, r, v)
        }

        /** As the setting keeps it (the piece's values, written as the codec writes them). */
        fun encode(l: Logic): String = if (l.isEmpty) "" else com.bamnat.core.recipe.RecipeCodec.canonical(l.toValues())

        @Suppress("UNCHECKED_CAST")
        fun decode(s: String?): Logic = if (s.isNullOrBlank()) Logic() else
            runCatching { from(com.bamnat.core.remote.Json.parse(s) as? Map<String, Any?>) }.getOrDefault(Logic())

        /** The checks the catalog runs on a formula field: it reads, and holds no mail, phone, link or chat (금액은 수식에 써도 돼요). */
        fun formulaProblem(src: String): String? = if (src.isEmpty()) null else Formula.problem(src) ?: guard(src)

        /** A text with {formulas} in it ([FormulaText]). */
        fun textProblem(src: String): String? = if (src.isEmpty()) null else FormulaText.problem(src) ?: guard(src)

        private fun guard(src: String): String? = RecipeText.problemExcept(src, "amount") ?: if (RecipeText.investAdvice(src)) "invest_advice" else null
    }
}

/** What a button or a rule does. [arg] is the 메모 틀's title, the screen, or the words (with {formulas}); [amount] for 가계부. */
data class LogicAct(
    val verb: LogicVerb,
    val arg: String = "",
    val amount: Long? = null,
    /** 1.39 [FX-03] 값 더하기·정하기: by how much (−1 takes one back), or what to set it to. */
    val by: Double? = null,
    /** 1.39 [FX-05] 알림 단추: the buttons ([LogicButton.id]) an 알림 carries, at most [MAX_THEN] ([AGAIN] ids: '10분 뒤 다시'). */
    val then: List<String> = emptyList(),
    /** 1.39 [FX-15] 기다렸다 하기: done this many minutes later (1~[MAX_AFTER]), or at once. */
    val after: Int? = null,
    /** 1.39 [FX-13] 값에 글 적기: the words (with {formulas}), [MAX_TEXT] letters. */
    val text: String = "",
) {
    fun toMap(): Map<String, Any?> = buildMap {
        put("do", verb.key)
        if (arg.isNotEmpty()) put("arg", arg)
        amount?.let { put("amount", it) }
        by?.let { put("by", it) }
        if (then.isNotEmpty()) put("then", then)
        after?.let { put("after", it.toLong()) }
        if (text.isNotEmpty()) put("text", text)
    }

    /** This act right away (a 기다렸다 하기 that came due). */
    fun now(): LogicAct = copy(after = null)

    /** 1.36 [SEC-12] This act with a 가계부 amount held to [Logic.RECEIVED_MAX_WON]. */
    fun held(): LogicAct = if ((verb == LogicVerb.ADD_MONEY || verb == LogicVerb.ADD_INCOME || verb == LogicVerb.ENVELOPE) && (amount ?: 0L) > Logic.RECEIVED_MAX_WON)
        copy(amount = Logic.RECEIVED_MAX_WON) else this

    /** "‘인수인계’ 메모 틀 열기", "‘식비 8,000원’ 가계부에 넣기". */
    fun say(): String = when (verb) {
        LogicVerb.NOTIFY -> "‘${arg.ifEmpty { "알림" }}’ 알림"
        LogicVerb.MEMO_TEMPLATE -> "‘${arg.ifEmpty { "메모" }}’ 메모 틀 열기"
        LogicVerb.NEW_MEMO -> "‘${arg.ifEmpty { "메모" }}’ 메모 만들기"
        // PC D8 (사용성 중): 제목이 비면 그날의 모든 목록이에요 (이름 있는 목록처럼 읽히지 않게).
        LogicVerb.CHECK_RESET -> if (arg.isEmpty()) "그날 모든 체크 목록 다시 비우기" else "‘$arg’ 체크 다시 비우기"
        LogicVerb.ADD_MONEY -> "가계부에 ‘${arg.ifEmpty { "기타" }}${amount?.let { " " + won(it) } ?: ""}’ 넣기"
        LogicVerb.ADD_INCOME -> "가계부 수입에 ‘${arg.ifEmpty { "기타" }}${amount?.let { " " + won(it) } ?: ""}’ 넣기"
        LogicVerb.MEMO_LINE -> "그날 메모에 ‘${arg.ifEmpty { "한 줄" }}’ 더하기"
        LogicVerb.ENVELOPE -> (if (arg.isEmpty()) "맨 위 봉투에" else "‘$arg’ 봉투에") + "${amount?.let { " " + won(it) } ?: ""} 넣기"
        LogicVerb.SCREEN -> "${LogicVerb.SCREENS[arg] ?: "화면"} 열기"
        LogicVerb.VAL_ADD -> (by ?: 1.0).let { b -> "‘${arg.ifEmpty { "값" }}’에 " + (if (b < 0) "${Formula.numText(-b)} 빼기" else "${Formula.numText(b)} 더하기") }
        LogicVerb.VAL_SET -> "‘${arg.ifEmpty { "값" }}’을 ${Formula.numText(by ?: 0.0)}으로"
        LogicVerb.VAL_STAMP -> "‘${arg.ifEmpty { "값" }}’에 지금 시각 적기"
        LogicVerb.VAL_TEXT -> "‘${arg.ifEmpty { "값" }}’에 ‘${text.take(12)}’ 적기"
    } + (if (then.isNotEmpty() && verb == LogicVerb.NOTIFY) " (단추 ${then.size}개)" else "") +
        (after?.let { " · ${afterWords(it)} 뒤에" } ?: "")

    /**
     * [RVM-20] PC-61 (설정의 규칙 글이 기계 말투): what it does in a few words, for a line a person reads ("‘집중’ +1", "50분 뒤 알림",
     * "가계부 ‘카페 4,500원’"). [say] stays for where the whole act is told.
     */
    fun brief(): String = (after?.let { "${afterWords(it)} 뒤 " } ?: "") + when (verb) {
        LogicVerb.NOTIFY -> "알림" + (if (then.isNotEmpty()) "(단추 ${then.size}개)" else "")
        LogicVerb.VAL_ADD -> (by ?: 1.0).let { b -> "‘${arg.ifEmpty { "값" }}’ " + (if (b < 0) "−${Formula.numText(-b)}" else "+${Formula.numText(b)}") }
        LogicVerb.VAL_SET -> "‘${arg.ifEmpty { "값" }}’ ${Formula.numText(by ?: 0.0)}으로"
        LogicVerb.VAL_STAMP -> "‘${arg.ifEmpty { "값" }}’ 시각 적기"
        LogicVerb.VAL_TEXT -> "‘${arg.ifEmpty { "값" }}’ 적기"
        LogicVerb.ADD_MONEY -> "가계부 ‘${arg.ifEmpty { "기타" }}${amount?.let { " " + won(it) } ?: ""}’"
        LogicVerb.ADD_INCOME -> "수입 ‘${arg.ifEmpty { "기타" }}${amount?.let { " " + won(it) } ?: ""}’"
        LogicVerb.MEMO_LINE -> "메모에 한 줄"
        else -> say()
    }

    companion object {
        const val MAX_THEN = 2
        const val MAX_BY = 1_000_000.0
        const val MAX_AFTER = 720
        const val MAX_TEXT = 24
        /** 1.39 [FX-15] 알림 단추 '10분 뒤 다시'·'1시간 뒤 다시': ids no button takes ([LogicButton.newId] starts with b). */
        val AGAIN = linkedMapOf("again10" to 10, "again60" to 60)

        fun afterWords(m: Int): String = when {
            m % 60 == 0 -> "${m / 60}시간"
            m > 60 -> "${m / 60}시간 ${m % 60}분"
            else -> "${m}분"
        }

        /** The acts of a 동작 줄 ("more", "else"), each as [from] reads it, at most [max]. */
        @Suppress("UNCHECKED_CAST")
        fun list(v: Any?, max: Int): List<LogicAct> = (v as? List<Map<String, Any?>>).orEmpty().mapNotNull(::from).take(max)

        @Suppress("UNCHECKED_CAST")
        fun from(m: Map<String, Any?>): LogicAct? {
            val verb = LogicVerb.of(m["do"] as? String) ?: return null
            val arg = (m["arg"] as? String).orEmpty()
            if (verb == LogicVerb.SCREEN && arg !in LogicVerb.SCREENS) return null
            if (verb.value && !LogicValue.isName(arg.trim())) return null
            val then = (m["then"] as? List<Any?>).orEmpty().mapNotNull { (it as? String)?.takeIf(LogicButton.ID::matches) }.distinct().take(MAX_THEN)
            return LogicAct(verb, if (verb.value) arg.trim() else arg, (m["amount"] as? Number)?.toLong(),
                (m["by"] as? Number)?.toDouble()?.coerceIn(-MAX_BY, MAX_BY), if (verb == LogicVerb.NOTIFY) then else emptyList(),
                (m["after"] as? Number)?.toInt()?.takeIf { it in 1..MAX_AFTER },
                if (verb == LogicVerb.VAL_TEXT) (m["text"] as? String).orEmpty().take(MAX_TEXT) else "")
        }

        internal fun won(v: Long) = v.toString().reversed().chunked(3).joinToString(",").reversed() + "원"
    }
}

/** The safe verbs. [writes] changes the user's data, so it is said and agreed to first and can be taken back. */
enum class LogicVerb(val key: String, val label: String, val writes: Boolean) {
    NOTIFY("notify", "알림 띄우기", false),
    MEMO_TEMPLATE("memoTpl", "메모 틀 열기", false),
    SCREEN("screen", "화면 열기", false),
    NEW_MEMO("newMemo", "메모 만들기", true),
    CHECK_RESET("checkReset", "체크 목록 다시 비우기", true),
    ADD_MONEY("addMoney", "가계부에 넣기", true),
    /** 사용자 2026-11-01 "메모와 달력을 연동": 그날 메모에 수식으로 만든 한 줄 (예: "야간 {이번주야간}번째, 이달 지출 {원(이달지출)}"). */
    MEMO_LINE("memoLine", "그날 메모에 한 줄 더하기", true),
    /** 가계부 수입 쪽 (예: 야간 근무 끝에 야간 수당 기록). */
    ADD_INCOME("addIncome", "가계부에 수입 넣기", true),
    /** 1.37 [LEDH-12] 모으기 봉투에 넣기 (예: 야간 근무 끝마다 '여행' 봉투에 1만 원). 이름이 없거나 없는 봉투면 첫 봉투. */
    ENVELOPE("envelope", "봉투에 넣기", true),
    /** 1.39 [FX-03] 값 칸에 더하기 (프로 기능 장치): '+1건', '물 한 잔'. The work's own number, not the user's records. */
    VAL_ADD("valAdd", "값 더하기", false),
    /** 1.39 [FX-03] 값 칸을 정하기 (프로 기능 장치): 0으로 되돌리기, 오늘 목표로. */
    VAL_SET("valSet", "값 정하기", false),
    /** 1.39 [FX-13] 값 칸에 지금 시각 (프로 기능 장치): '약 먹음', 출근 도장. `지난시간("약")`으로 몇 시간 지났나. */
    VAL_STAMP("valStamp", "지금 시각 적기", false),
    /** 1.39 [FX-13] 값 칸에 짧은 글 (프로 기능 장치): '오늘 기분', 마지막 손님. */
    VAL_TEXT("valText", "글 적기", false);

    /** 1.39 [FX-03][FX-13] Works on a 값 칸 named by its arg. */
    val value: Boolean get() = this == VAL_ADD || this == VAL_SET || this == VAL_STAMP || this == VAL_TEXT

    companion object {
        val KEYS = entries.map { it.key }
        fun of(key: String?): LogicVerb? = entries.firstOrNull { it.key == key }

        /** The screens a button or a rule may open. */
        val SCREENS = linkedMapOf("calendar" to "달력", "day" to "그날 화면", "memo" to "메모장", "money" to "가계부", "pay" to "급여", "stats" to "통계", "alarm" to "알람",
            // 1.39 [FX-21] 새 화면 (프로 기능 장치): 수면 기록, 밤 화면.
            "sleep" to "수면", "night" to "밤 화면")
        /** 1.39 [FX-21] Screens opened from 1.39 on (프로, [FxPro.Part.REACH]). */
        val NEW_SCREENS = setOf("sleep", "night")
    }
}

/** Where a button sits. */
enum class ButtonPlace(val key: String, val label: String) {
    DAY("day", "하루 화면"), MEMO("memo", "메모장"), CALENDAR("cal", "달력 화면"),
    /** 사용자 2026-11-01 "가계부와 연동도": 가계부 화면에도 단추. */
    MONEY("money", "가계부 화면"),
    /** 누락 점검 N8: 꾸밈 층의 그림을 누르면 (갈래 C의 꾸밈 항목 `tap`이 단추 [LogicButton.id]를 담아요). 칩 줄에는 안 나와요. */
    DECOR("decor", "꾸밈 층"),
    /** 1.39 [FX-04] 단추 위젯 (프로 기능 장치): 홈 화면에서 앱을 열지 않고 눌러요. */
    WIDGET("widget", "단추 위젯"),
    /** 1.39 [FX-04] 빠른 설정 타일 (프로 기능 장치): 알림 막대를 내려 눌러요. */
    TILE("tile", "빠른 설정"),
    /** 1.39 [FX-04] 앱 바로가기 (프로 기능 장치): 앱 아이콘을 길게 눌러요. */
    SHORTCUT("shortcut", "앱 바로가기");

    /** 1.39 [FX-04] Outside the app: pressed without opening it, shown on no screen's chip row. */
    val outside: Boolean get() = this == WIDGET || this == TILE || this == SHORTCUT

    companion object {
        val KEYS = entries.map { it.key }
        fun of(key: String?): ButtonPlace = entries.firstOrNull { it.key == key } ?: DAY
    }
}

/** A button: [show] is a formula; when it is there and false for the day, the button hides. */
data class LogicButton(val label: String, val emoji: String? = null, val place: ButtonPlace = ButtonPlace.DAY, val act: LogicAct, val show: String = "",
    /** 누락 점검 N8: what a 꾸밈 항목's `tap` names, [ID] form. Blank: [idOf] the label (예전 단추). */
    val givenId: String = "",
    /** 1.39 [FX-11] 그리고/아니면: when this holds the press does [act] and [more]; when it does not, [otherwise]. Empty: always. */
    val cond: String = "",
    /** 1.39 [FX-11] 동작 줄: what follows [act], in order. */
    val more: List<LogicAct> = emptyList(),
    /** 1.39 [FX-11] '아니면' 줄. */
    val otherwise: List<LogicAct> = emptyList()) {
    /** Every act it may do. */
    val acts: List<LogicAct> get() = listOf(act) + more + otherwise

    /** What a press does when [cond] [holds]. */
    fun steps(holds: Boolean): List<LogicAct> = if (holds || cond.isEmpty()) listOf(act) + more else otherwise
    /** The id other places point at (꾸밈 층 `tap`). */
    val id: String get() = givenId.takeIf { ID.matches(it) } ?: idOf(label)

    fun toMap(): Map<String, Any?> = buildMap {
        put("id", id)
        put("label", label)
        emoji?.let { put("emoji", it) }
        put("where", place.key)
        putAll(act.toMap())
        if (show.isNotEmpty()) put("show", show)
        if (cond.isNotEmpty()) put("if", cond)
        if (more.isNotEmpty()) put("more", more.map { it.toMap() })
        if (otherwise.isNotEmpty()) put("else", otherwise.map { it.toMap() })
    }

    /** [RVM-20] PC-61: "누르면 ‘집중’ +1, 50분 뒤 알림" (the 단추's name and place are on the line above). */
    fun brief(): String = (if (cond.isEmpty()) "누르면 " else "누르면 조건에 따라 ") + briefSteps(listOf(act) + more)

    fun say(): String = "${place.label}에 ‘${emoji?.let { "$it " }.orEmpty()}$label’ 단추: 누르면 " +
        sayChain(cond, listOf(act) + more, otherwise) +
        (if (show.isNotEmpty()) " (${EasyWhen.read(show)?.say() ?: "조건이 맞는 날"}에만 보여요)" else "")

    companion object {
        fun from(m: Map<String, Any?>): LogicButton? {
            val label = (m["label"] as? String)?.takeIf { it.isNotBlank() } ?: return null
            val act = LogicAct.from(m) ?: return null
            val cond = (m["if"] as? String).orEmpty().takeIf { it.isEmpty() || Formula.problem(it) == null } ?: return null
            return LogicButton(label, m["emoji"] as? String, ButtonPlace.of(m["where"] as? String), act, (m["show"] as? String).orEmpty(),
                (m["id"] as? String)?.takeIf { ID.matches(it) }.orEmpty(), cond,
                LogicAct.list(m["more"], MAX_MORE), if (cond.isEmpty()) emptyList() else LogicAct.list(m["else"], MAX_ELSE))
        }

        /** 영문 소문자·숫자 1~12자 (server recipes.js the same). */
        val ID = Regex("^[a-z0-9]{1,12}$")

        /** The same id for the same label every time: "b" + 6 base-36 of its hash. */
        fun idOf(label: String): String = "b" + Integer.toUnsignedString(label.hashCode(), 36).padStart(7, '0').takeLast(6)

        /** A new button's id, not one of [taken]. */
        fun newId(taken: Collection<String>, seed: Long = System.nanoTime()): String {
            var n = seed
            while (true) {
                val id = "b" + java.lang.Long.toUnsignedString(n, 36).takeLast(4).padStart(4, '0')
                if (id !in taken) return id
                n = n * 31 + 7
            }
        }
    }
}

/** 언제. [min] moves start·end (−: before), [at] is 매일's time, [role] keeps start·end to one kind of shift. */
enum class RuleTrigger(val key: String, val label: String) {
    TAP("tap", "날짜를 누르면"),
    START("start", "근무 시작"),
    END("end", "근무 끝"),
    DAILY("daily", "매일"),
    MEMO("memo", "메모를 저장하면"),
    OPEN("open", "하루 처음 앱을 열면"),
    /** 사용자 2026-11-01 "가계부와 연동도": 가계부에 적으면 (그날 값으로 조건, 예: 이달지출 > 500000이면 알림). */
    MONEY("money", "가계부에 적으면"),
    /** 누락 점검 N8: 날짜를 길게 누르면. 화면·메모 틀을 여는 규칙이 맞으면 빠른 설정 대신 그것을 열어요. */
    HOLD("hold", "날짜를 길게 누르면"),
    /** 1.39 [FX-12] 값 칸 [LogicRule.of]가 바뀌면 (프로 기능 장치); [LogicRule.goal]이면 목표에 처음 닿을 때만. */
    VALUE("value", "값이 바뀌면"),
    /** 1.39 [FX-15] [LogicRule.min]분마다 (프로 기능 장치), [LogicRule.at]~[LogicRule.until] 사이나 근무 중에만. */
    EVERY("every", "몇 분마다"),
    /** 1.39 [FX-21] 근무 알람을 끄면 (메모 일정 알람·다시 확인은 빼고), 그날 값으로 조건 (프로 기능 장치). */
    ALARM_OFF("alarmOff", "근무 알람을 끄면");

    companion object {
        val KEYS = entries.map { it.key }
        fun of(key: String?): RuleTrigger? = entries.firstOrNull { it.key == key }
    }
}

data class LogicRule(
    val name: String,
    val on: RuleTrigger,
    val min: Int = 0,
    val at: String? = null,
    val role: String? = null,
    val cond: String = "",
    val act: LogicAct,
    /** 1.39 [FX-11] 동작 줄: what follows [act] when [cond] holds. */
    val more: List<LogicAct> = emptyList(),
    /** 1.39 [FX-11] '아니면': what it does when [cond] does not hold (nothing when empty). */
    val otherwise: List<LogicAct> = emptyList(),
    /** 1.39 [FX-12] 값이 바뀌면: the 값 칸's name. */
    val of: String? = null,
    /** 1.39 [FX-12] 목표에 처음 닿으면 (once a period). */
    val goal: Boolean = false,
    /** 1.39 [FX-15] 몇 분마다: until when (HH:mm), [at] being from when. */
    val until: String? = null,
    /** 1.39 [FX-15] 몇 분마다: only while a shift is on. */
    val inWork: Boolean = false,
) {
    /** A stable key for this rule (its on/off switch and run count stay with it across edits of its words). */
    val key: String get() = "${on.key}|$min|${at.orEmpty()}|${role.orEmpty()}|${cond.hashCode()}|${act.verb.key}|${act.arg.hashCode()}" +
        (of?.let { "|$it${if (goal) "!" else ""}" } ?: "") + (until?.let { "|$it" } ?: "") + (if (inWork) "|w" else "")

    /** Every act it may do. */
    val acts: List<LogicAct> get() = listOf(act) + more + otherwise

    /** What it does when [cond] [holds]: its 동작 줄, or its '아니면' (maybe none). */
    fun steps(holds: Boolean): List<LogicAct> = if (holds) listOf(act) + more else otherwise

    fun toMap(): Map<String, Any?> = buildMap {
        put("name", name)
        put("on", on.key)
        if (min != 0) put("min", min.toLong())
        at?.let { put("at", it) }
        role?.let { put("role", it) }
        if (cond.isNotEmpty()) put("when", cond)
        putAll(act.toMap())
        if (more.isNotEmpty()) put("more", more.map { it.toMap() })
        if (otherwise.isNotEmpty()) put("else", otherwise.map { it.toMap() })
        of?.let { put("of", it) }
        if (goal) put("edge", "goal")
        until?.let { put("until", it) }
        if (inWork) put("during", "work")
    }

    /** 언제, in words: "야간 근무 시작 3시간 전", "매일 21:00". */
    fun sayWhen(): String {
        val r = role?.let { ROLE_WORDS[it] }?.let { "$it " }.orEmpty()
        fun offset(base: String): String = when {
            min == 0 -> base
            min < 0 -> "$base ${span(-min)} 전"
            else -> "$base ${span(min)} 뒤"
        }
        return when (on) {
            RuleTrigger.TAP -> "날짜를 누르면"
            RuleTrigger.START -> offset("${r}근무 시작")
            RuleTrigger.END -> offset("${r}근무 끝")
            RuleTrigger.DAILY -> "매일 ${at ?: "09:00"}"
            RuleTrigger.MEMO -> "메모를 저장하면"
            RuleTrigger.OPEN -> "하루 처음 앱을 열면"
            RuleTrigger.MONEY -> "가계부에 적으면"
            RuleTrigger.HOLD -> "날짜를 길게 누르면"
            RuleTrigger.VALUE -> "‘${of.orEmpty()}’" + (if (goal) " 목표에 처음 닿으면" else " 값이 바뀌면")
            RuleTrigger.ALARM_OFF -> "근무 알람을 끄면"
            RuleTrigger.EVERY -> (if (inWork) "근무 중 " else if (at != null || until != null) "${at ?: "00:00"}~${until ?: "24:00"} " else "") +
                "${span(min.coerceAtLeast(EVERY_MIN))}마다"
        }
    }

    /** One line before it is turned on (사용성 제안: "이 규칙은 야간 근무 전날 밤 9시에 메모 틀을 열어요"). */
    /** [RVM-20] PC-61: "근무 중 2시간마다 · 알림" (the rule's name is on the line above). */
    fun brief(): String = sayWhen() + (if (cond.isEmpty()) "" else " · 조건이 맞으면") + " · " + briefSteps(listOf(act) + more)

    fun say(): String {
        if (more.isNotEmpty() || otherwise.isNotEmpty()) return "${sayWhen()}, ${sayChain(cond, listOf(act) + more, otherwise)}"
        val c = if (cond.isEmpty()) "" else (EasyWhen.read(cond)?.say() ?: "조건이 맞으면").let { " " + ifWord(it) }
        return "${sayWhen()},$c ${act.say()}"
    }

    companion object {
        val ROLE_WORDS = mapOf("day" to "주간", "eve" to "저녁", "night" to "야간", "long" to "당직", "off" to "쉬는 날", "other" to "기타")
        /** 1.39 [FX-15] 몇 분마다: 30분~4시간, a day at most [EVERY_DAY_MAX] times. */
        const val EVERY_MIN = 30
        const val EVERY_MAX = 240
        const val EVERY_DAY_MAX = 48

        private fun span(m: Int): String = when {
            m % 60 == 0 -> "${m / 60}시간"
            m > 60 -> "${m / 60}시간 ${m % 60}분"
            else -> "${m}분"
        }

        fun from(m: Map<String, Any?>): LogicRule? {
            val on = RuleTrigger.of(m["on"] as? String) ?: return null
            val act = LogicAct.from(m) ?: return null
            val cond = (m["when"] as? String).orEmpty()
            if (cond.isNotEmpty() && Formula.problem(cond) != null) return null
            val of = (m["of"] as? String)?.trim()?.takeIf(LogicValue::isName)
            if (on == RuleTrigger.VALUE && of == null) return null
            val min = (m["min"] as? Number)?.toInt()?.coerceIn(-720, 720) ?: 0
            return LogicRule(
                name = (m["name"] as? String).orEmpty().ifBlank { act.say() },
                on = on,
                min = if (on == RuleTrigger.EVERY) min.coerceIn(EVERY_MIN, EVERY_MAX) else min,
                at = m["at"] as? String,
                role = (m["role"] as? String)?.takeIf { it in ROLE_WORDS },
                cond = cond,
                act = act,
                more = LogicAct.list(m["more"], MAX_MORE),
                otherwise = if (cond.isEmpty()) emptyList() else LogicAct.list(m["else"], MAX_ELSE),
                of = if (on == RuleTrigger.VALUE) of else null,
                goal = on == RuleTrigger.VALUE && m["edge"] == "goal",
                until = (m["until"] as? String)?.takeIf { on == RuleTrigger.EVERY },
                inWork = on == RuleTrigger.EVERY && m["during"] == "work",
            )
        }
    }
}

/** 1.39 [FX-11] 동작 줄 한 묶음 (one more and the 아니면 at most). */
const val MAX_MORE = 3
const val MAX_ELSE = 3

/** "야간이면 A 하고 B, 아니면 C" (조건이 없으면 "A 하고 B"). */
/** [RVM-20] Three steps at most, then '외 N가지'. */
internal fun briefSteps(acts: List<LogicAct>): String =
    acts.take(3).joinToString(", ") { it.brief() } + (if (acts.size > 3) " 외 ${acts.size - 3}가지" else "")

internal fun sayChain(cond: String, acts: List<LogicAct>, otherwise: List<LogicAct>): String {
    val run = acts.joinToString(" → ") { it.say() }
    if (cond.isEmpty()) return run
    val c = ifWord(EasyWhen.read(cond)?.say() ?: "조건이 맞으면")
    return "$c $run" + (if (otherwise.isEmpty()) "" else ", 아니면 " + otherwise.joinToString(" → ") { it.say() })
}

/** "근무종류가 야간" → "근무종류가 야간이면", "… 있음" → "… 있으면", "… 맞으면" stays (자연스러운 말로). */
internal fun ifWord(say: String): String = when {
    say.endsWith("면") -> say
    say.endsWith("있음") -> say.removeSuffix("있음") + "있으면"
    say.endsWith("없음") -> say.removeSuffix("없음") + "없으면"
    say.endsWith("아님") -> say.removeSuffix("아님") + "아니면"
    say.endsWith("임") -> say.removeSuffix("임") + "이면"
    else -> say.lastOrNull()?.let { c -> if (c in '가'..'힣' && (c - '가') % 28 == 0) "${say}면" else "${say}이면" } ?: say
}
