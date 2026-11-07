package com.bamnat.core.formula

/**
 * 1.39 [FX-20] 예시로 시작 (무료로 넣어 보고 고쳐요): 만들기가 막막할 때 값 칸·단추·규칙을 한꺼번에 넣는 기능 예시. 넣은 뒤에는 보통 작품처럼
 * 고치고, ✦ 장치는 프로 문([FxPro])을 그대로 따라요.
 */
object FxExamples {
    data class Example(val key: String, val emoji: String, val title: String, val say: String, val logic: Logic)

    val ALL: List<Example> = listOf(
        Example("water", "💧", "물 8잔", "단추 위젯으로 한 잔씩 세고, 8잔이 되면 알려 줘요",
            Logic(
                buttons = listOf(LogicButton("물 한 잔", "💧", ButtonPlace.WIDGET, LogicAct(LogicVerb.VAL_ADD, "물 잔", by = 1.0))),
                rules = listOf(LogicRule("물 다 마심", RuleTrigger.VALUE, of = "물 잔", goal = true, act = LogicAct(LogicVerb.NOTIFY, "오늘 물 8잔을 다 마셨어요 🎉"))),
                values = listOf(LogicValue("물 잔", ValueReset.DAY, goal = 8.0, unit = "잔")),
            )),
        Example("delivery", "🛵", "배달 건수 위젯", "홈 화면에서 +1, 근무가 끝나면 오늘 건수를 알려 줘요",
            Logic(
                buttons = listOf(LogicButton("배달 +1", "🛵", ButtonPlace.WIDGET, LogicAct(LogicVerb.VAL_ADD, "배달", by = 1.0))),
                rules = listOf(LogicRule("배달 정리", RuleTrigger.END, act = LogicAct(LogicVerb.NOTIFY, "오늘 배달 {값(\"배달\")}건이에요"))),
                values = listOf(LogicValue("배달", ValueReset.DAY, unit = "건")),
            )),
        Example("pill", "💊", "약 먹은 지 몇 시간", "먹었어요를 누르면 시각을 적고, 12시간이 지나면 알려 줘요",
            Logic(
                buttons = listOf(LogicButton("약 먹었어요", "💊", ButtonPlace.DAY, LogicAct(LogicVerb.VAL_STAMP, "약"))),
                rules = listOf(LogicRule("약 챙기기", RuleTrigger.EVERY, min = 60, at = "08:00", until = "22:00", cond = "지난시간(\"약\") >= 12",
                    act = LogicAct(LogicVerb.NOTIFY, "약 먹은 지 {지난시간(\"약\")}시간 지났어요", then = listOf("again10")))),
                values = listOf(LogicValue("약", ValueReset.NEVER, kind = ValueKind.TIME)),
            )),
        Example("stretch", "🙆", "근무 중 2시간마다 스트레칭", "근무 시간에만 2시간마다 쉬어 가라고 알려 줘요",
            Logic(rules = listOf(LogicRule("스트레칭", RuleTrigger.EVERY, min = 120, inWork = true,
                act = LogicAct(LogicVerb.NOTIFY, "어깨 한 번 펴고 물 한 모금 마셔요 🙆", then = listOf("again10")))))),
        Example("timer", "🍜", "라면 타이머", "단추를 누르면 4분 뒤에 알려 줘요",
            Logic(buttons = listOf(LogicButton("라면 4분", "🍜", ButtonPlace.SHORTCUT, LogicAct(LogicVerb.NOTIFY, "라면 다 됐어요 🍜", after = 4))))),
        Example("nights", "🌙", "이달 야간 세기", "야간이 끝날 때마다 세고 그날 메모에 몇 번째인지 적어요",
            Logic(
                rules = listOf(LogicRule("야간 세기", RuleTrigger.END, role = "night", act = LogicAct(LogicVerb.VAL_ADD, "야간", by = 1.0),
                    more = listOf(LogicAct(LogicVerb.MEMO_LINE, "이달 야간 {값(\"야간\")}번째")))),
                values = listOf(LogicValue("야간", ValueReset.MONTH, unit = "번")),
            )),
    )

    /**
     * [e] put into [l]: 값 칸 of the same name are kept as they are (not twice), buttons get ids of their own, and nothing goes past
     * [Logic.MAX_BUTTONS]·[Logic.MAX_RULES]·[LogicValue.MAX]. Returns null when there is no room.
     */
    fun into(l: Logic, e: Example): Logic? {
        val values = l.values + e.logic.values.filter { v -> l.values.none { it.name == v.name } }
        if (values.size > LogicValue.MAX || l.buttons.size + e.logic.buttons.size > Logic.MAX_BUTTONS || l.rules.size + e.logic.rules.size > Logic.MAX_RULES) return null
        val buttons = e.logic.buttons.fold(l.buttons) { acc, b -> acc + b.copy(givenId = LogicButton.newId(acc.map { it.id })) }
        return l.copy(buttons = buttons, rules = l.rules + e.logic.rules, values = values)
    }
}
