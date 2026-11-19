package com.bamnat.core.recipe

// 2.0.0 [RVS-18] 만든 파일이에요. 손으로 고치지 마세요. server/company-worker/src/parts-since.js와 같은 표(RecipeVocab.since).
// 다시 만들기: BAMNAT_WRITE_PARTS_SINCE=1 ./gradlew -p core test --tests '*PartsSinceExportTest*' --rerun (낡으면 그 시험이 실패해요)
/** Each name an older app does not read, by category ([RecipeVocab.KINDS]), with the first versionCode that reads it. */
internal object PartsSinceTable {
    val MAP: Map<String, Map<String, Int>> = mapOf(
        "ids" to mapOf(
            "memo.page" to 52, "memo.page.rule" to 52, "memo.photo" to 52,
            "night" to 53, "night.button" to 53, "night.card" to 53, "night.clock" to 53, "night.date" to 53,
            "night.line" to 53, "night.morning" to 53, "widget.task" to 53, "widget.task.add" to 53, "widget.task.check" to 53,
        ),
        "states" to mapOf(
            "cover" to 53, "done" to 53, "edited" to 53, "extra" to 53, "late" to 53,
            "leave" to 53,
        ),
        "decor" to mapOf(
            "cover" to 53, "edited" to 53, "extra" to 53, "leave" to 53,
        ),
        "fonts" to emptyMap(),
        "motions" to emptyMap(),
        "fills" to emptyMap(),
        "pieces" to mapOf(
            "ledger" to 51,
        ),
        "fields" to mapOf(
            "ledger.budgets" to 51, "ledger.budgets.amount" to 51, "ledger.budgets.category" to 51, "ledger.costs" to 51, "ledger.envelopes" to 51,
            "ledger.envelopes.emoji" to 51, "ledger.envelopes.name" to 51, "ledger.envelopes.share" to 51, "ledger.envelopes.target" to 51, "ledger.incomes" to 51,
            "logic.buttons.after" to 53, "logic.buttons.by" to 53, "logic.buttons.else" to 53, "logic.buttons.else.after" to 53, "logic.buttons.else.amount" to 53,
            "logic.buttons.else.arg" to 53, "logic.buttons.else.by" to 53, "logic.buttons.else.do" to 53, "logic.buttons.else.text" to 53, "logic.buttons.else.then" to 53,
            "logic.buttons.if" to 53, "logic.buttons.more" to 53, "logic.buttons.more.after" to 53, "logic.buttons.more.amount" to 53, "logic.buttons.more.arg" to 53,
            "logic.buttons.more.by" to 53, "logic.buttons.more.do" to 53, "logic.buttons.more.text" to 53, "logic.buttons.more.then" to 53, "logic.buttons.text" to 53,
            "logic.buttons.then" to 53, "logic.rules.after" to 53, "logic.rules.by" to 53, "logic.rules.during" to 53, "logic.rules.edge" to 53,
            "logic.rules.else" to 53, "logic.rules.else.after" to 53, "logic.rules.else.amount" to 53, "logic.rules.else.arg" to 53, "logic.rules.else.by" to 53,
            "logic.rules.else.do" to 53, "logic.rules.else.text" to 53, "logic.rules.else.then" to 53, "logic.rules.more" to 53, "logic.rules.more.after" to 53,
            "logic.rules.more.amount" to 53, "logic.rules.more.arg" to 53, "logic.rules.more.by" to 53, "logic.rules.more.do" to 53, "logic.rules.more.text" to 53,
            "logic.rules.more.then" to 53, "logic.rules.of" to 53, "logic.rules.text" to 53, "logic.rules.then" to 53, "logic.rules.until" to 53,
            "logic.values" to 53, "logic.values.goal" to 53, "logic.values.kind" to 53, "logic.values.name" to 53, "logic.values.reset" to 53,
            "logic.values.start" to 53, "logic.values.unit" to 53,
        ),
        "picks" to mapOf(
            "logic.buttons.do=envelope" to 51, "logic.rules.do=envelope" to 51,
            "parts.parts.id=memo.page" to 52, "parts.parts.id=memo.page.rule" to 52, "parts.parts.id=memo.photo" to 52,
            "logic.buttons.do=valAdd" to 53, "logic.buttons.do=valSet" to 53, "logic.buttons.do=valStamp" to 53, "logic.buttons.do=valText" to 53, "logic.buttons.else.do=addIncome" to 53,
            "logic.buttons.else.do=addMoney" to 53, "logic.buttons.else.do=checkReset" to 53, "logic.buttons.else.do=envelope" to 53, "logic.buttons.else.do=memoLine" to 53, "logic.buttons.else.do=memoTpl" to 53,
            "logic.buttons.else.do=newMemo" to 53, "logic.buttons.else.do=notify" to 53, "logic.buttons.else.do=screen" to 53, "logic.buttons.else.do=valAdd" to 53, "logic.buttons.else.do=valSet" to 53,
            "logic.buttons.else.do=valStamp" to 53, "logic.buttons.else.do=valText" to 53, "logic.buttons.more.do=addIncome" to 53, "logic.buttons.more.do=addMoney" to 53, "logic.buttons.more.do=checkReset" to 53,
            "logic.buttons.more.do=envelope" to 53, "logic.buttons.more.do=memoLine" to 53, "logic.buttons.more.do=memoTpl" to 53, "logic.buttons.more.do=newMemo" to 53, "logic.buttons.more.do=notify" to 53,
            "logic.buttons.more.do=screen" to 53, "logic.buttons.more.do=valAdd" to 53, "logic.buttons.more.do=valSet" to 53, "logic.buttons.more.do=valStamp" to 53, "logic.buttons.more.do=valText" to 53,
            "logic.buttons.where=shortcut" to 53, "logic.buttons.where=tile" to 53, "logic.buttons.where=widget" to 53, "logic.rules.do=valAdd" to 53, "logic.rules.do=valSet" to 53,
            "logic.rules.do=valStamp" to 53, "logic.rules.do=valText" to 53, "logic.rules.during=work" to 53, "logic.rules.edge=goal" to 53, "logic.rules.else.do=addIncome" to 53,
            "logic.rules.else.do=addMoney" to 53, "logic.rules.else.do=checkReset" to 53, "logic.rules.else.do=envelope" to 53, "logic.rules.else.do=memoLine" to 53, "logic.rules.else.do=memoTpl" to 53,
            "logic.rules.else.do=newMemo" to 53, "logic.rules.else.do=notify" to 53, "logic.rules.else.do=screen" to 53, "logic.rules.else.do=valAdd" to 53, "logic.rules.else.do=valSet" to 53,
            "logic.rules.else.do=valStamp" to 53, "logic.rules.else.do=valText" to 53, "logic.rules.more.do=addIncome" to 53, "logic.rules.more.do=addMoney" to 53, "logic.rules.more.do=checkReset" to 53,
            "logic.rules.more.do=envelope" to 53, "logic.rules.more.do=memoLine" to 53, "logic.rules.more.do=memoTpl" to 53, "logic.rules.more.do=newMemo" to 53, "logic.rules.more.do=notify" to 53,
            "logic.rules.more.do=screen" to 53, "logic.rules.more.do=valAdd" to 53, "logic.rules.more.do=valSet" to 53, "logic.rules.more.do=valStamp" to 53, "logic.rules.more.do=valText" to 53,
            "logic.rules.on=alarmOff" to 53, "logic.rules.on=every" to 53, "logic.rules.on=value" to 53, "logic.values.kind=num" to 53, "logic.values.kind=text" to 53,
            "logic.values.kind=time" to 53, "logic.values.reset=day" to 53, "logic.values.reset=month" to 53, "logic.values.reset=never" to 53, "logic.values.reset=week" to 53,
            "parts.parts.id=night" to 53, "parts.parts.id=night.button" to 53, "parts.parts.id=night.card" to 53, "parts.parts.id=night.clock" to 53, "parts.parts.id=night.date" to 53,
            "parts.parts.id=night.line" to 53, "parts.parts.id=night.morning" to 53, "parts.parts.id=widget.task" to 53, "parts.parts.id=widget.task.add" to 53, "parts.parts.id=widget.task.check" to 53,
            "parts.parts.on=cover" to 53, "parts.parts.on=done" to 53, "parts.parts.on=edited" to 53, "parts.parts.on=extra" to 53, "parts.parts.on=late" to 53,
            "parts.parts.on=leave" to 53,
        ),
    )
}
