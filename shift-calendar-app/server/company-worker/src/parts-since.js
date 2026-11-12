// 손으로 고치지 말고 core 시험으로 만들어요(갈래 M: BAMNAT_WRITE_PARTS_SINCE=1, FlagCatalogExportTest 방식). 그 시험이 생기기 전까지만 손으로 둬요.
//
// 2.0.0 [RVD-24] SN-07 (스튜디오 제작 오류 조사, 설계 S3): 판 호환표. Every name an older app's RecipeCodec does not know makes it refuse the
// whole recipe, so a maker's recipe that uses one is listed only to apps at least that new (recipes.js partsMinApp → min_app).
// Each map: name → the first versionCode that reads it (52 = 1.38.0, 53 = 1.39.0, 54 = 2.0.0). A name not listed is read by every app.
//   ids     a 부품 줄's id                                   (parts.parts[].id)
//   states  a 부품 줄's state                                (parts.parts[].on)
//   decor   a 꾸밈 항목's day                                (decor.items[].on, an item with at)
//   fonts   a font id                                        (any font field, and the ff:·ft: pieces of looks·screens)
//   motions one motion piece, as written                     (motion: "tn:slide|td:pulse" → "tn:slide", "td:pulse")
//   fills   a fill's kind: pt.<무늬>·tx.<질감>·sh.<셰이더>, else its first word (so·lg·rg·sg·gl)  (paint·linePaint, fl: pieces)
export const PARTS_SINCE = {
  ids: {
    // [MPH-06] 1.38 메모 쓰기 화면: 종이, 종이 줄, 메모 사진.
    "memo.page": 52, "memo.page.rule": 52, "memo.photo": 52,
    // 1.39 [FLOW-21] 밤 화면과 아침 한 장.
    "night": 53, "night.date": 53, "night.clock": 53, "night.line": 53, "night.card": 53, "night.button": 53, "night.morning": 53,
    // 1.39 [TASK-07] 할 일 위젯.
    "widget.task": 53, "widget.task.check": 53, "widget.task.add": 53,
  },
  // 1.39 [LINK-01] 근무 입력 상태 (연차·잔업·특근·대근·직접 바꾼 날), [TASK-07] 늦음·끝냄.
  states: { late: 53, done: 53, leave: 53, extra: 53, cover: 53, edited: 53 },
  decor: { leave: 53, extra: 53, cover: 53, edited: 53 },
  fonts: {},
  motions: {},
  fills: {},
};
