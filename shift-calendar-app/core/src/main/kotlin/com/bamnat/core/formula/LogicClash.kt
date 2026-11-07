package com.bamnat.core.formula

/**
 * 1.39 [FX-25] 넣기 전 부딪힘 검사: a received work's 규칙·단추·값 칸 against mine, said in a line each before 넣기 (넣는 것은 막지 않아요).
 * - 같은 이름 값 칸인데 뜻이 다름(매일 vs 매주, 숫자 vs 시각): 같은 숫자를 함께 써요.
 * - 같은 때 같은 가계부·값 칸을 바꾸는 규칙: 두 번 적히거나 두 번 세요.
 * - 같은 이름 단추가 같은 자리에: 둘 다 보여 헷갈려요.
 * Rules that are the same ([LogicRule.key]) are kept once by [Logic.plus] and are not a clash.
 */
object LogicClash {
    fun check(mine: Logic, got: Logic): List<String> = buildList {
        for (v in got.values) {
            val m = mine.values.firstOrNull { it.name == v.name } ?: continue
            if (m.kind != v.kind || m.reset != v.reset)
                add("‘${v.name}’ 값 칸이 이미 있어요(내 것 ${m.reset.label}·${m.kind.label}, 받는 것 ${v.reset.label}·${v.kind.label}). 같은 숫자를 함께 써요")
        }
        val mineKeys = mine.rules.map { it.key }.toSet()
        for (r in got.rules) {
            if (r.key in mineKeys) continue
            for (m in mine.rules) {
                if (!sameTime(m, r)) continue
                val money = targets(m).filter { it.startsWith("money") }.intersect(targets(r).toSet())
                val values = targets(m).filter { it.startsWith("value:") }.intersect(targets(r).toSet())
                if (money.isNotEmpty()) add("내 ‘${m.name}’ 규칙과 같은 때 가계부에 적어요. 두 번 적힐 수 있어요")
                else if (values.isNotEmpty()) add("내 ‘${m.name}’ 규칙과 같은 때 ‘${values.first().removePrefix("value:")}’ 값을 바꿔요. 두 번 셀 수 있어요")
            }
        }
        for (b in got.buttons) {
            val m = mine.buttons.firstOrNull { it.label == b.label && it.place == b.place && it.toMap() != b.toMap() } ?: continue
            add("‘${m.label}’ 단추가 ${m.place.label}에 이미 있어요. 둘 다 보여요")
        }
    }.distinct()

    private fun sameTime(a: LogicRule, b: LogicRule): Boolean = a.on == b.on && when (a.on) {
        RuleTrigger.START, RuleTrigger.END -> a.min == b.min && (a.role == null || b.role == null || a.role == b.role)
        RuleTrigger.DAILY -> a.at == b.at
        RuleTrigger.EVERY -> true
        RuleTrigger.VALUE -> a.of == b.of
        else -> true
    }

    /** What [r]'s acts change: "money" for the 가계부, "value:<name>" for a 값 칸. */
    private fun targets(r: LogicRule): List<String> = r.acts.mapNotNull { a ->
        when {
            a.verb == LogicVerb.ADD_MONEY || a.verb == LogicVerb.ADD_INCOME || a.verb == LogicVerb.ENVELOPE -> "money"
            a.verb.value -> "value:${a.arg}"
            else -> null
        }
    }
}
