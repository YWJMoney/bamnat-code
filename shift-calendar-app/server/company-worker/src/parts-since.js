// 2.0.0 [RVS-04] 만든 파일이에요. 손으로 고치지 마세요.
// 원본: core RecipeVocab(지금 앱이 아는 이름) + docs/recipes/vocab/<versionCode>.json(출시판마다 그 판 core가 알던 이름, tools/vocab_snapshot.py).
// 다시 만들기: BAMNAT_WRITE_PARTS_SINCE=1 ./gradlew -p core test --tests '*PartsSinceExportTest*' --rerun (낡으면 그 시험이 실패해요)
//
// [RVD-24] SN-07 (스튜디오 제작 오류 조사, 설계 S3): 판 호환표. Every name an older app's RecipeCodec does not know makes it refuse the
// whole recipe, so a maker's recipe that uses one is listed only to apps at least that new (recipes.js partsMinApp → min_app).
// Each map: name → the first versionCode that reads it (50 = 1.36.1, 51 = 1.37.0, 52 = 1.38.0, 53 = 1.39.0, 54 = 2.0.0). A name not listed
// is read by every app from 1.36.0 (49).
//   ids     a 부품 줄's id                                   (parts.parts[].id)
//   states  a 부품 줄's state                                (parts.parts[].on)
//   decor   a 꾸밈 항목's day                                (decor.items[].on, an item with at)
//   fonts   a font id                                        (any font field, and the ff:·ft: pieces of looks·screens)
//   motions one motion piece, as written                     (motion: "tn:slide|td:pulse" → "tn:slide", "td:pulse"; a part's motion)
//   fills   a fill's kind: pt.<무늬>·tx.<질감>·sh.<셰이더>, else its first word (so·lg·rg·sg·gl)  (paint·linePaint, fl: pieces)
//   pieces  a piece's kind; an older app leaves out a kind it does not know (the rest of the work still opens)
//   fields  <kind>.<field>[.<field of an item>]: an older app drops a field it does not know, and then the work fails its hash
//   picks   <kind>.<field>…=<choice>: an older app refuses the whole work on a choice it does not know
export const PARTS_SINCE = {
  ids: {
    "memo.page": 52, "memo.page.rule": 52, "memo.photo": 52,
    "night": 53, "night.button": 53, "night.card": 53, "night.clock": 53, "night.date": 53, "night.line": 53,
    "night.morning": 53, "widget.task": 53, "widget.task.add": 53, "widget.task.check": 53,
  },
  states: {
    "cover": 53, "done": 53, "edited": 53, "extra": 53, "late": 53, "leave": 53,
  },
  decor: {
    "cover": 53, "edited": 53, "extra": 53, "leave": 53,
  },
  fonts: {},
  motions: {},
  fills: {},
  pieces: {
    "ledger": 51,
  },
  fields: {
    "ledger.budgets": 51, "ledger.budgets.amount": 51, "ledger.budgets.category": 51, "ledger.costs": 51, "ledger.envelopes": 51, "ledger.envelopes.emoji": 51,
    "ledger.envelopes.name": 51, "ledger.envelopes.share": 51, "ledger.envelopes.target": 51, "ledger.incomes": 51,
    "logic.buttons.after": 53, "logic.buttons.by": 53, "logic.buttons.else": 53, "logic.buttons.else.after": 53, "logic.buttons.else.amount": 53, "logic.buttons.else.arg": 53,
    "logic.buttons.else.by": 53, "logic.buttons.else.do": 53, "logic.buttons.else.text": 53, "logic.buttons.else.then": 53, "logic.buttons.if": 53, "logic.buttons.more": 53,
    "logic.buttons.more.after": 53, "logic.buttons.more.amount": 53, "logic.buttons.more.arg": 53, "logic.buttons.more.by": 53, "logic.buttons.more.do": 53, "logic.buttons.more.text": 53,
    "logic.buttons.more.then": 53, "logic.buttons.text": 53, "logic.buttons.then": 53, "logic.rules.after": 53, "logic.rules.by": 53, "logic.rules.during": 53,
    "logic.rules.edge": 53, "logic.rules.else": 53, "logic.rules.else.after": 53, "logic.rules.else.amount": 53, "logic.rules.else.arg": 53, "logic.rules.else.by": 53,
    "logic.rules.else.do": 53, "logic.rules.else.text": 53, "logic.rules.else.then": 53, "logic.rules.more": 53, "logic.rules.more.after": 53, "logic.rules.more.amount": 53,
    "logic.rules.more.arg": 53, "logic.rules.more.by": 53, "logic.rules.more.do": 53, "logic.rules.more.text": 53, "logic.rules.more.then": 53, "logic.rules.of": 53,
    "logic.rules.text": 53, "logic.rules.then": 53, "logic.rules.until": 53, "logic.values": 53, "logic.values.goal": 53, "logic.values.kind": 53,
    "logic.values.name": 53, "logic.values.reset": 53, "logic.values.start": 53, "logic.values.unit": 53,
  },
  picks: {
    "logic.buttons.do=envelope": 51, "logic.rules.do=envelope": 51,
    "parts.parts.id=memo.page": 52, "parts.parts.id=memo.page.rule": 52, "parts.parts.id=memo.photo": 52,
    "logic.buttons.do=valAdd": 53, "logic.buttons.do=valSet": 53, "logic.buttons.do=valStamp": 53, "logic.buttons.do=valText": 53, "logic.buttons.else.do=addIncome": 53, "logic.buttons.else.do=addMoney": 53,
    "logic.buttons.else.do=checkReset": 53, "logic.buttons.else.do=envelope": 53, "logic.buttons.else.do=memoLine": 53, "logic.buttons.else.do=memoTpl": 53, "logic.buttons.else.do=newMemo": 53, "logic.buttons.else.do=notify": 53,
    "logic.buttons.else.do=screen": 53, "logic.buttons.else.do=valAdd": 53, "logic.buttons.else.do=valSet": 53, "logic.buttons.else.do=valStamp": 53, "logic.buttons.else.do=valText": 53, "logic.buttons.more.do=addIncome": 53,
    "logic.buttons.more.do=addMoney": 53, "logic.buttons.more.do=checkReset": 53, "logic.buttons.more.do=envelope": 53, "logic.buttons.more.do=memoLine": 53, "logic.buttons.more.do=memoTpl": 53, "logic.buttons.more.do=newMemo": 53,
    "logic.buttons.more.do=notify": 53, "logic.buttons.more.do=screen": 53, "logic.buttons.more.do=valAdd": 53, "logic.buttons.more.do=valSet": 53, "logic.buttons.more.do=valStamp": 53, "logic.buttons.more.do=valText": 53,
    "logic.buttons.where=shortcut": 53, "logic.buttons.where=tile": 53, "logic.buttons.where=widget": 53, "logic.rules.do=valAdd": 53, "logic.rules.do=valSet": 53, "logic.rules.do=valStamp": 53,
    "logic.rules.do=valText": 53, "logic.rules.during=work": 53, "logic.rules.edge=goal": 53, "logic.rules.else.do=addIncome": 53, "logic.rules.else.do=addMoney": 53, "logic.rules.else.do=checkReset": 53,
    "logic.rules.else.do=envelope": 53, "logic.rules.else.do=memoLine": 53, "logic.rules.else.do=memoTpl": 53, "logic.rules.else.do=newMemo": 53, "logic.rules.else.do=notify": 53, "logic.rules.else.do=screen": 53,
    "logic.rules.else.do=valAdd": 53, "logic.rules.else.do=valSet": 53, "logic.rules.else.do=valStamp": 53, "logic.rules.else.do=valText": 53, "logic.rules.more.do=addIncome": 53, "logic.rules.more.do=addMoney": 53,
    "logic.rules.more.do=checkReset": 53, "logic.rules.more.do=envelope": 53, "logic.rules.more.do=memoLine": 53, "logic.rules.more.do=memoTpl": 53, "logic.rules.more.do=newMemo": 53, "logic.rules.more.do=notify": 53,
    "logic.rules.more.do=screen": 53, "logic.rules.more.do=valAdd": 53, "logic.rules.more.do=valSet": 53, "logic.rules.more.do=valStamp": 53, "logic.rules.more.do=valText": 53, "logic.rules.on=alarmOff": 53,
    "logic.rules.on=every": 53, "logic.rules.on=value": 53, "logic.values.kind=num": 53, "logic.values.kind=text": 53, "logic.values.kind=time": 53, "logic.values.reset=day": 53,
    "logic.values.reset=month": 53, "logic.values.reset=never": 53, "logic.values.reset=week": 53, "parts.parts.id=night": 53, "parts.parts.id=night.button": 53, "parts.parts.id=night.card": 53,
    "parts.parts.id=night.clock": 53, "parts.parts.id=night.date": 53, "parts.parts.id=night.line": 53, "parts.parts.id=night.morning": 53, "parts.parts.id=widget.task": 53, "parts.parts.id=widget.task.add": 53,
    "parts.parts.id=widget.task.check": 53, "parts.parts.on=cover": 53, "parts.parts.on=done": 53, "parts.parts.on=edited": 53, "parts.parts.on=extra": 53, "parts.parts.on=late": 53,
    "parts.parts.on=leave": 53,
  },
};
