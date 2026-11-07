package com.bamnat.core.formula

/**
 * 1.39 [FX-01] 프로 문 (사용자 2026-11-07 17:31·17:35·17:38 "꾸미는 것은 무료로 마음껏, 기능으로 바꿔 쓰는 것은 프로 … 무료 프로의 구분은
 * 확실히"): the one place that says which parts of a work's 규칙·단추 are the 1.39 기능 장치 that need 프로. Screens, the runner, the
 * 장터 card and the server (`fxPartsOf` in recipes.js, the same list) all ask here and nowhere else.
 *
 * 한 줄 규칙: **1.38까지 있던 것은 무료 그대로, 1.39부터 새로 여는 기능 장치는 프로.** 모양은 늘 무료. 만들기·미리 보기·입혀 보기는 무료,
 * 내 앱에 켜기·장터 올리기·받아 쓰기는 프로(작품마다 하루 써 보기 한 번). 프로가 끝나면 [free]만 돌아요: 옛 장치는 그대로 무료로 돌고,
 * 새 장치만 쉬어요. 값 칸의 숫자는 지우지 않아요.
 */
object FxPro {
    /** The app version (versionCode) that first reads these; the server keeps a work that has one from older apps (`min_app`). */
    const val SINCE_APP = 53

    enum class Part(val key: String, val label: String) {
        /** [FX-03] The work keeps numbers of its own. */
        VALUES("values", "값 칸"),
        /** [FX-03] A button or rule adds to or sets a 값 칸. */
        VALUE_ACT("valueAct", "값 더하기·정하기"),
        /** [FX-03] A formula reads a 값 칸 (`값("물 잔")`). */
        VALUE_READ("valueRead", "수식으로 값 읽기"),
        /** [FX-04] A button pressed outside the app: 단추 위젯, 빠른 설정, 앱 바로가기. */
        OUTSIDE("outside", "단추 위젯·빠른 설정·바로가기"),
        /** [FX-05] An 알림 carrying buttons. */
        NOTIFY_BUTTONS("notifyButtons", "알림 단추"),
        ;

        companion object {
            val KEYS = entries.map { it.key }
            fun of(key: String?): Part? = entries.firstOrNull { it.key == key }
        }
    }

    /** The 프로 기능 장치 [l] uses, in [Part] order; empty when everything in it is free. */
    fun uses(l: Logic): Set<Part> = buildSet {
        if (l.values.isNotEmpty()) add(Part.VALUES)
        if (l.buttons.any { it.act.verb.pro } || l.rules.any { it.act.verb.pro }) add(Part.VALUE_ACT)
        if (l.buttons.any { reads(it) } || l.rules.any { reads(it) }) add(Part.VALUE_READ)
        if (l.buttons.any { it.place.outside }) add(Part.OUTSIDE)
        if (l.buttons.any { it.act.then.isNotEmpty() } || l.rules.any { it.act.then.isNotEmpty() }) add(Part.NOTIFY_BUTTONS)
    }.let { s -> Part.entries.filter { it in s }.toSet() }

    /** Whether [l] needs 프로 to run as made. */
    fun needs(l: Logic): Boolean = uses(l).isNotEmpty()

    /**
     * What runs without 프로: the buttons and rules that use no 1.39 기능 장치, the 알림 without its buttons. The 값 칸 list stays so its
     * numbers are kept and shown as they were, but nothing adds to them.
     */
    fun free(l: Logic): Logic = Logic(
        buttons = l.buttons.filter { !it.place.outside && !it.act.verb.pro && !reads(it) }.map { it.copy(act = it.act.copy(then = emptyList())) },
        rules = l.rules.filter { !it.act.verb.pro && !reads(it) }.map { it.copy(act = it.act.copy(then = emptyList())) },
        values = l.values,
    )

    /** "값 칸·단추 위젯·빠른 설정·바로가기" for the 장터 card and the 프로 문 line. */
    fun say(parts: Collection<Part>): String = parts.joinToString(" · ") { it.label }

    /** How many buttons and rules of [l] rest without 프로 (for "프로가 끝나 2개가 쉬고 있어요"). */
    fun resting(l: Logic): Int {
        val f = free(l)
        return (l.buttons.size - f.buttons.size) + (l.rules.size - f.rules.size) +
            l.buttons.count { it.act.then.isNotEmpty() && !it.place.outside && !it.act.verb.pro && !reads(it) } +
            l.rules.count { it.act.then.isNotEmpty() && !it.act.verb.pro && !reads(it) }
    }

    private fun reads(b: LogicButton): Boolean = readsValue(b.show) || readsValueText(b.act.arg)
    private fun reads(r: LogicRule): Boolean = readsValue(r.cond) || readsValueText(r.act.arg)

    /** Does formula [src] call `값(…)`? */
    fun readsValue(src: String): Boolean =
        src.isNotEmpty() && (Formula.parse(src) as? Parsed.Ok)?.formula?.calls?.contains(Fn.VALUE.ko) == true

    /** Does text [src] with {formulas} call `값(…)` in one of them? */
    fun readsValueText(src: String): Boolean = '{' in src &&
        FormulaText.parse(src).getOrNull()?.any { it is FormulaText.Part.Calc && Fn.VALUE.ko in it.formula.calls } == true
}

/** 1.39 [FX-01] A verb that came with 1.39 (프로 기능 장치). */
val LogicVerb.pro: Boolean get() = this == LogicVerb.VAL_ADD || this == LogicVerb.VAL_SET
