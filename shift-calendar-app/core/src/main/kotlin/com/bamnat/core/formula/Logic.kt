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
data class Logic(val buttons: List<LogicButton> = emptyList(), val rules: List<LogicRule> = emptyList()) {
    val isEmpty: Boolean get() = buttons.isEmpty() && rules.isEmpty()

    /** Does anything in it write the user's data (메모 만들기, 가계부 넣기, 체크 다시)? */
    val writes: Boolean get() = buttons.any { it.act.verb.writes } || rules.any { it.act.verb.writes }

    fun toValues(): Map<String, Any?> = buildMap {
        if (buttons.isNotEmpty()) put("buttons", buttons.map { it.toMap() })
        if (rules.isNotEmpty()) put("rules", rules.map { it.toMap() })
    }

    /**
     * 1.36 [SEC-12] 보안 점검 M4: these as they come in from someone else's recipe: a 가계부 amount is held to [RECEIVED_MAX_WON]
     * (a prank or a mistake cannot put 1억 원 in each day). The phone's own buttons and rules are never held.
     */
    fun received(): Logic = Logic(buttons.map { it.copy(act = it.act.held()) }, rules.map { it.copy(act = it.act.held()) })

    /** Whether [received] would hold an amount down. */
    val overReceived: Boolean get() = buttons.any { it.act.held() != it.act } || rules.any { it.act.held() != it.act }

    /** 1.36 [SEC-12] The rules that write data and run by themselves or by a tap (not a press of a button the user put there). */
    val writingRules: List<LogicRule> get() = rules.filter { it.act.verb.writes }

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
            s.takeIf { it.isNotEmpty() }?.let { l -> l.joinToString(", ") { (t, m) -> "‘$t’" + (if (t != m) "은 내 ‘$m’과" else "은 이미 있는 것과") } + " 하는 일이 같아 하나로 뒀어요" },
            over.takeIf { it > 0 }?.let { "한도(단추 ${MAX_BUTTONS}개·규칙 ${MAX_RULES}개)를 넘는 ${it}개는 넣지 않았어요" },
        ).joinToString(". ").ifEmpty { null }
    }

    /** [RUL-07] 보관함 빼기: these without [other]'s buttons and rules (what [plus] added). */
    fun minus(other: Logic): Logic = Logic(
        buttons.filter { b -> other.buttons.none { it.toMap() == b.toMap() } },
        rules.filter { r -> other.rules.none { it.key == r.key } },
    )

    /** 누락 점검 N8: the button a 꾸밈 항목's `tap` names. */
    fun button(id: String): LogicButton? = buttons.firstOrNull { it.id == id }

    /** [RUL-07] [other]'s buttons and rules after these (one already here is not added again), within the limits. */
    fun plus(other: Logic): Logic = Logic(
        (buttons + other.buttons.filter { b -> buttons.none { it.toMap() == b.toMap() } }).take(MAX_BUTTONS),
        (rules + other.rules.filter { r -> rules.none { it.key == r.key } }).take(MAX_RULES),
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
            return Logic(b, r)
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
data class LogicAct(val verb: LogicVerb, val arg: String = "", val amount: Long? = null) {
    fun toMap(): Map<String, Any?> = buildMap {
        put("do", verb.key)
        if (arg.isNotEmpty()) put("arg", arg)
        amount?.let { put("amount", it) }
    }

    /** 1.36 [SEC-12] This act with a 가계부 amount held to [Logic.RECEIVED_MAX_WON]. */
    fun held(): LogicAct = if ((verb == LogicVerb.ADD_MONEY || verb == LogicVerb.ADD_INCOME) && (amount ?: 0L) > Logic.RECEIVED_MAX_WON)
        copy(amount = Logic.RECEIVED_MAX_WON) else this

    /** "‘인수인계’ 메모 틀 열기", "‘식비 8,000원’ 가계부에 넣기". */
    fun say(): String = when (verb) {
        LogicVerb.NOTIFY -> "‘${arg.ifEmpty { "알림" }}’ 알림"
        LogicVerb.MEMO_TEMPLATE -> "‘${arg.ifEmpty { "메모" }}’ 메모 틀 열기"
        LogicVerb.NEW_MEMO -> "‘${arg.ifEmpty { "메모" }}’ 메모 만들기"
        LogicVerb.CHECK_RESET -> "‘${arg.ifEmpty { "체크 목록" }}’ 체크 다시 비우기"
        LogicVerb.ADD_MONEY -> "가계부에 ‘${arg.ifEmpty { "기타" }}${amount?.let { " " + won(it) } ?: ""}’ 넣기"
        LogicVerb.ADD_INCOME -> "가계부 수입에 ‘${arg.ifEmpty { "기타" }}${amount?.let { " " + won(it) } ?: ""}’ 넣기"
        LogicVerb.MEMO_LINE -> "그날 메모에 ‘${arg.ifEmpty { "한 줄" }}’ 더하기"
        LogicVerb.SCREEN -> "${LogicVerb.SCREENS[arg] ?: "화면"} 열기"
    }

    companion object {
        fun from(m: Map<String, Any?>): LogicAct? {
            val verb = LogicVerb.of(m["do"] as? String) ?: return null
            val arg = (m["arg"] as? String).orEmpty()
            if (verb == LogicVerb.SCREEN && arg !in LogicVerb.SCREENS) return null
            return LogicAct(verb, arg, (m["amount"] as? Number)?.toLong())
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
    ADD_INCOME("addIncome", "가계부에 수입 넣기", true);

    companion object {
        val KEYS = entries.map { it.key }
        fun of(key: String?): LogicVerb? = entries.firstOrNull { it.key == key }

        /** The screens a button or a rule may open. */
        val SCREENS = linkedMapOf("calendar" to "달력", "day" to "그날 화면", "memo" to "메모장", "money" to "가계부", "pay" to "급여", "stats" to "통계", "alarm" to "알람")
    }
}

/** Where a button sits. */
enum class ButtonPlace(val key: String, val label: String) {
    DAY("day", "하루 화면"), MEMO("memo", "메모장"), CALENDAR("cal", "달력 화면"),
    /** 사용자 2026-11-01 "가계부와 연동도": 가계부 화면에도 단추. */
    MONEY("money", "가계부 화면"),
    /** 누락 점검 N8: 꾸밈 층의 그림을 누르면 (갈래 C의 꾸밈 항목 `tap`이 단추 [LogicButton.id]를 담아요). 칩 줄에는 안 나와요. */
    DECOR("decor", "꾸밈 층");

    companion object {
        val KEYS = entries.map { it.key }
        fun of(key: String?): ButtonPlace = entries.firstOrNull { it.key == key } ?: DAY
    }
}

/** A button: [show] is a formula; when it is there and false for the day, the button hides. */
data class LogicButton(val label: String, val emoji: String? = null, val place: ButtonPlace = ButtonPlace.DAY, val act: LogicAct, val show: String = "",
    /** 누락 점검 N8: what a 꾸밈 항목's `tap` names, [ID] form. Blank: [idOf] the label (예전 단추). */
    val givenId: String = "") {
    /** The id other places point at (꾸밈 층 `tap`). */
    val id: String get() = givenId.takeIf { ID.matches(it) } ?: idOf(label)

    fun toMap(): Map<String, Any?> = buildMap {
        put("id", id)
        put("label", label)
        emoji?.let { put("emoji", it) }
        put("where", place.key)
        putAll(act.toMap())
        if (show.isNotEmpty()) put("show", show)
    }

    fun say(): String = "${place.label}에 ‘${emoji?.let { "$it " }.orEmpty()}$label’ 단추: 누르면 ${act.say()}" +
        (if (show.isNotEmpty()) " (${EasyWhen.read(show)?.say() ?: "조건이 맞는 날"}에만 보여요)" else "")

    companion object {
        fun from(m: Map<String, Any?>): LogicButton? {
            val label = (m["label"] as? String)?.takeIf { it.isNotBlank() } ?: return null
            val act = LogicAct.from(m) ?: return null
            return LogicButton(label, m["emoji"] as? String, ButtonPlace.of(m["where"] as? String), act, (m["show"] as? String).orEmpty(),
                (m["id"] as? String)?.takeIf { ID.matches(it) }.orEmpty())
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
    HOLD("hold", "날짜를 길게 누르면");

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
) {
    /** A stable key for this rule (its on/off switch and run count stay with it across edits of its words). */
    val key: String get() = "${on.key}|$min|${at.orEmpty()}|${role.orEmpty()}|${cond.hashCode()}|${act.verb.key}|${act.arg.hashCode()}"

    fun toMap(): Map<String, Any?> = buildMap {
        put("name", name)
        put("on", on.key)
        if (min != 0) put("min", min.toLong())
        at?.let { put("at", it) }
        role?.let { put("role", it) }
        if (cond.isNotEmpty()) put("when", cond)
        putAll(act.toMap())
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
        }
    }

    /** One line before it is turned on (사용성 제안: "이 규칙은 야간 근무 전날 밤 9시에 메모 틀을 열어요"). */
    fun say(): String {
        val c = if (cond.isEmpty()) "" else (EasyWhen.read(cond)?.say() ?: "조건이 맞으면").let { " " + ifWord(it) }
        return "${sayWhen()},$c ${act.say()}"
    }

    companion object {
        val ROLE_WORDS = mapOf("day" to "주간", "eve" to "저녁", "night" to "야간", "long" to "당직", "off" to "쉬는 날", "other" to "기타")

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
            return LogicRule(
                name = (m["name"] as? String).orEmpty().ifBlank { act.say() },
                on = on,
                min = (m["min"] as? Number)?.toInt()?.coerceIn(-720, 720) ?: 0,
                at = m["at"] as? String,
                role = (m["role"] as? String)?.takeIf { it in ROLE_WORDS },
                cond = cond,
                act = act,
            )
        }
    }
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
