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

    companion object {
        const val MAX_BUTTONS = 8
        const val MAX_RULES = 12

        /** From the piece's values as [com.bamnat.core.recipe.RecipeCodec] checked them; what the app cannot use is left out. */
        @Suppress("UNCHECKED_CAST")
        fun from(values: Map<String, Any?>?): Logic {
            if (values == null) return Logic()
            val b = (values["buttons"] as? List<Map<String, Any?>>).orEmpty().mapNotNull(LogicButton::from).take(MAX_BUTTONS)
            val r = (values["rules"] as? List<Map<String, Any?>>).orEmpty().mapNotNull(LogicRule::from).take(MAX_RULES)
            return Logic(b, r)
        }

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

    /** "‘인수인계’ 메모 틀 열기", "‘식비 8,000원’ 가계부에 넣기". */
    fun say(): String = when (verb) {
        LogicVerb.NOTIFY -> "‘${arg.ifEmpty { "알림" }}’ 알림"
        LogicVerb.MEMO_TEMPLATE -> "‘${arg.ifEmpty { "메모" }}’ 메모 틀 열기"
        LogicVerb.NEW_MEMO -> "‘${arg.ifEmpty { "메모" }}’ 메모 만들기"
        LogicVerb.CHECK_RESET -> "‘${arg.ifEmpty { "체크 목록" }}’ 체크 다시 비우기"
        LogicVerb.ADD_MONEY -> "가계부에 ‘${arg.ifEmpty { "기타" }}${amount?.let { " " + won(it) } ?: ""}’ 넣기"
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
    ADD_MONEY("addMoney", "가계부에 넣기", true);

    companion object {
        val KEYS = entries.map { it.key }
        fun of(key: String?): LogicVerb? = entries.firstOrNull { it.key == key }

        /** The screens a button or a rule may open. */
        val SCREENS = linkedMapOf("calendar" to "달력", "day" to "그날 화면", "memo" to "메모장", "money" to "가계부", "pay" to "급여", "stats" to "통계", "alarm" to "알람")
    }
}

/** Where a button sits. */
enum class ButtonPlace(val key: String, val label: String) {
    DAY("day", "하루 화면"), MEMO("memo", "메모장 위"), CALENDAR("cal", "달력 위");

    companion object {
        val KEYS = entries.map { it.key }
        fun of(key: String?): ButtonPlace = entries.firstOrNull { it.key == key } ?: DAY
    }
}

/** A button: [show] is a formula; when it is there and false for the day, the button hides. */
data class LogicButton(val label: String, val emoji: String? = null, val place: ButtonPlace = ButtonPlace.DAY, val act: LogicAct, val show: String = "") {
    fun toMap(): Map<String, Any?> = buildMap {
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
            return LogicButton(label, m["emoji"] as? String, ButtonPlace.of(m["where"] as? String), act, (m["show"] as? String).orEmpty())
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
    OPEN("open", "하루 처음 앱을 열면");

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
        }
    }

    /** One line before it is turned on (사용성 제안: "이 규칙은 야간 근무 전날 밤 9시에 메모 틀을 열어요"). */
    fun say(): String {
        val c = if (cond.isEmpty()) "" else (EasyWhen.read(cond)?.say() ?: "조건이 맞으면").let { " $it 이면".replace("면 이면", "면") }
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
