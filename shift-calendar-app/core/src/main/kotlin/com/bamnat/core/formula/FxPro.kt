package com.bamnat.core.formula

/**
 * 1.39 [FX-01] 프로 문 (사용자 2026-11-07 17:31·17:35·17:38 "꾸미는 것은 무료로 마음껏, 기능으로 바꿔 쓰는 것은 프로 … 무료 프로의 구분은
 * 확실히"): the one place that says which parts of a work's 규칙·단추 are the 1.39 기능 장치 that need 프로. Screens, the runner, the
 * 장터 card and the server (`fxPartsOf` in recipes.js, the same list) all ask here and nowhere else.
 *
 * 한 줄 규칙: **1.38까지 있던 것은 무료 그대로, 1.39부터 새로 여는 것은 모양이든 기능이든 프로**(사용자 18:37 "프로의 메리트를 강화";
 * 1.38까지의 모양은 무료 그대로, 1.39 새 화면 모양 부품은 [NEW_LOOKS]). 만들기·미리 보기·입혀 보기는 무료,
 * 내 앱에 켜기·장터 올리기·받아 쓰기는 프로(작품마다 하루 써 보기 한 번). 프로가 끝나면 [free]만 돌아요: 옛 장치는 그대로 무료로 돌고,
 * 새 장치만 쉬어요. 값 칸의 숫자는 지우지 않아요.
 */
object FxPro {
    /** The app version (versionCode) that first reads these; the server keeps a work that has one from older apps (`min_app`). */
    const val SINCE_APP = 53

    enum class Part(val key: String, val label: String) {
        /** [FX-03] The work keeps numbers of its own (one 숫자 값 칸 is free: [TASTE]). */
        VALUES("values", "값 칸"),
        /** [FX-03] A button or rule adds to, sets or writes a 값 칸. */
        VALUE_ACT("valueAct", "값 더하기·정하기"),
        /** [FX-03] A formula reads a 값 칸 (`값("물 잔")`). */
        VALUE_READ("valueRead", "수식으로 값 읽기"),
        /** [FX-04] A button pressed outside the app: 단추 위젯, 빠른 설정, 앱 바로가기 (one widget button is free). */
        OUTSIDE("outside", "단추 위젯·빠른 설정·바로가기"),
        /** [FX-05] An 알림 carrying buttons ('10분 뒤 다시' alone is free). */
        NOTIFY_BUTTONS("notifyButtons", "알림 단추"),
        /** [FX-11] 동작 줄, 그리고/아니면. */
        CHAIN("chain", "동작 줄·아니면"),
        /** [FX-12] 값이 바뀌면·목표에 닿으면. */
        VALUE_RULE("valueRule", "값이 바뀌면"),
        /** [FX-13] 값 칸의 기록 (지난값·연속·값합계·값평균·지난시간). */
        HISTORY("history", "값 기록"),
        /** [FX-15] 몇 분마다·기다렸다 하기. */
        TIME("time", "몇 분마다·기다렸다"),
        /** [FX-21] 1.39 새 언제(근무 알람을 끄면)·새 화면(수면·밤 화면 열기). */
        REACH("reach", "알람·수면과 잇기"),
        /** [FX-22] 1.39 새 화면 모양 부품 ([NEW_LOOKS]: 수면·할 일 화면, G8 밤 화면·하루 이야기 줄). */
        LOOK("look", "1.39 새 화면 모양"),
        ;

        companion object {
            val KEYS = entries.map { it.key }
            fun of(key: String?): Part? = entries.firstOrNull { it.key == key }
        }
    }

    /**
     * 사용자 2026-11-07 18:38 '하나씩 무료 맛보기': a free phone keeps one 숫자 값 칸 (the first) and one 단추 위젯 button that counts it
     * (or does a 1.38 thing) for good; the second of either is 프로. Everything else new stays 프로.
     */
    object TASTE {
        /** The free 값 칸 of [l]: its first, when it is a number. */
        fun value(l: Logic): String? = l.values.firstOrNull()?.takeIf { it.kind == ValueKind.NUM }?.name
        /** The free 단추 위젯 button of [l]: the first widget button. */
        fun widget(l: Logic): String? = l.buttons.firstOrNull { it.place == ButtonPlace.WIDGET }?.id
    }

    /** The 프로 기능 장치 [l] uses beyond [TASTE], in [Part] order; empty when everything in it is free. */
    fun uses(l: Logic): Set<Part> = buildSet {
        if (l.values.size > 1 || (l.values.isNotEmpty() && TASTE.value(l) == null)) add(Part.VALUES)
        l.buttons.forEach { addAll(parts(l, it)) }
        l.rules.forEach { addAll(parts(l, it)) }
    }.let { s -> Part.entries.filter { it in s }.toSet() }

    /** What of [b] is 프로 (the [TASTE] allowance taken off). */
    fun parts(l: Logic, b: LogicButton): Set<Part> = buildSet {
        val taste = TASTE.value(l)
        if (b.place == ButtonPlace.TILE || b.place == ButtonPlace.SHORTCUT || (b.place == ButtonPlace.WIDGET && b.id != TASTE.widget(l))) add(Part.OUTSIDE)
        acts(b.act, taste, this)
        (b.more + b.otherwise).forEach { acts(it, null, this) }
        if (b.more.isNotEmpty() || b.otherwise.isNotEmpty() || b.cond.isNotEmpty()) add(Part.CHAIN)
        formula(b.show, this); formula(b.cond, this)
    }

    fun parts(l: Logic, r: LogicRule): Set<Part> = buildSet {
        (r.acts).forEach { acts(it, null, this) }
        if (r.more.isNotEmpty() || r.otherwise.isNotEmpty()) add(Part.CHAIN)
        if (r.on == RuleTrigger.VALUE) add(Part.VALUE_RULE)
        if (r.on == RuleTrigger.EVERY) add(Part.TIME)
        if (r.on == RuleTrigger.ALARM_OFF) add(Part.REACH)
        formula(r.cond, this)
    }

    /** [a]'s 프로 parts; [taste] is the 값 칸 a press may count for free. */
    private fun acts(a: LogicAct, taste: String?, out: MutableSet<Part>) {
        if (a.verb.pro && !(taste != null && a.arg == taste && (a.verb == LogicVerb.VAL_ADD || a.verb == LogicVerb.VAL_SET))) out += Part.VALUE_ACT
        if (a.then.any { it !in LogicAct.AGAIN }) out += Part.NOTIFY_BUTTONS
        if (a.after != null) out += Part.TIME
        if (a.verb == LogicVerb.SCREEN && a.arg in LogicVerb.NEW_SCREENS) out += Part.REACH
        if (a.verb == LogicVerb.NOTIFY || a.verb == LogicVerb.MEMO_LINE || a.verb == LogicVerb.NEW_MEMO || a.verb == LogicVerb.VAL_TEXT) {
            text(a.arg, out); text(a.text, out)
        }
    }

    private fun formula(src: String, out: MutableSet<Part>) {
        val calls = callsOf(src) ?: return
        if (Fn.VALUE.ko in calls) out += Part.VALUE_READ
        if (calls.any { it in HISTORY_FNS }) out += Part.HISTORY
    }

    private fun text(src: String, out: MutableSet<Part>) {
        if ('{' !in src) return
        val calls = FormulaText.parse(src).getOrNull()?.filterIsInstance<FormulaText.Part.Calc>()?.flatMap { it.formula.calls }?.toSet() ?: return
        if (Fn.VALUE.ko in calls) out += Part.VALUE_READ
        if (calls.any { it in HISTORY_FNS }) out += Part.HISTORY
    }

    private fun callsOf(src: String): Set<String>? = if (src.isEmpty()) null else (Formula.parse(src) as? Parsed.Ok)?.formula?.calls

    private val HISTORY_FNS = setOf(Fn.PAST.ko, Fn.STREAK.ko, Fn.TOTAL.ko, Fn.MEAN.ko, Fn.SINCE.ko)

    /**
     * [FX-22] 1.39에 새로 연 화면 모양 부품: a work's piece kind (or a 설계도 부품 id) that is one of these, or starts with one ending in
     * '.', is [Part.LOOK]. 갈래마다 자기 부품 앞자리를 여기에만 더해요 (서버 recipes.js FX_NEW_LOOKS도 같이).
     */
    val NEW_LOOKS: List<String> = listOf(
        // G8 FLOW-21 밤 화면·아침 한 장 (설계도 부품 night·night.date·night.clock·night.line·night.card·night.button·night.morning).
        "night", "night.",
    )

    fun isNewLook(id: String): Boolean = NEW_LOOKS.any { id == it || (it.endsWith('.') && id.startsWith(it)) }

    /** [Part.LOOK] when any of [ids] (piece kinds, 설계도 부품 ids) is a 1.39 새 모양. */
    fun looks(ids: Collection<String>): Set<Part> = if (ids.any(::isNewLook)) setOf(Part.LOOK) else emptySet()

    /** [Part.LOOK] for one piece: its kind, or for 부품 꾸미기 (`parts`) the 설계도 부품 ids its lines dress. */
    fun looks(kind: String, values: Map<String, Any?>): Set<Part> =
        looks(listOf(kind) + if (kind == "parts") (values["parts"] as? List<*>).orEmpty().mapNotNull { (it as? Map<*, *>)?.get("id") as? String } else emptyList())

    /** Whether [l] needs 프로 to run as made. */
    fun needs(l: Logic): Boolean = uses(l).isNotEmpty()

    /**
     * What runs without 프로: the buttons and rules with no 프로 part (1.38 things and the [TASTE] allowance), each 알림 without its
     * 알림 단추 ('10분 뒤 다시' stays). The 값 칸 list stays so its numbers are kept and shown as they were.
     */
    fun free(l: Logic): Logic = free(l, { false }, { false })

    /**
     * 1.39 [FX-23] 작품마다 하루 써 보기: as [free], but the buttons and rules [openButton]·[openRule] say are of a work being tried run
     * whole (그 작품 것만, 다른 작품의 ✦는 쉬어요).
     */
    fun free(l: Logic, openButton: (LogicButton) -> Boolean, openRule: (LogicRule) -> Boolean): Logic {
        fun bare(a: LogicAct) = a.copy(then = a.then.filter { it in LogicAct.AGAIN })
        fun only(p: Set<Part>) = p.isEmpty() || p == setOf(Part.NOTIFY_BUTTONS)
        return Logic(
            buttons = l.buttons.mapNotNull { b -> if (openButton(b)) b else if (only(parts(l, b))) b.copy(act = bare(b.act)) else null },
            rules = l.rules.mapNotNull { r -> if (openRule(r)) r else if (only(parts(l, r))) r.copy(act = bare(r.act)) else null },
            values = l.values,
        )
    }

    /** "값 칸·단추 위젯·빠른 설정·바로가기" for the 장터 card and the 프로 문 line. */
    fun say(parts: Collection<Part>): String = parts.joinToString(" · ") { it.label }

    /** How many buttons and rules of [l] rest (or lose their 알림 단추) without 프로 (for "프로가 끝나 2개가 쉬고 있어요"). */
    fun resting(l: Logic): Int = l.buttons.count { parts(l, it).isNotEmpty() } + l.rules.count { parts(l, it).isNotEmpty() }

    /** Does formula [src] call `값(…)`? */
    fun readsValue(src: String): Boolean = callsOf(src)?.contains(Fn.VALUE.ko) == true

    /** Does text [src] with {formulas} call `값(…)` in one of them? */
    fun readsValueText(src: String): Boolean = '{' in src &&
        FormulaText.parse(src).getOrNull()?.any { it is FormulaText.Part.Calc && Fn.VALUE.ko in it.formula.calls } == true
}

/** 1.39 [FX-01] A verb that came with 1.39 (프로 기능 장치). */
val LogicVerb.pro: Boolean get() = value
