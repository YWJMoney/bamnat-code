// [LAY-01][LAY-02] 1.36 꾸밈 층 (core recipe/Decor.kt, DecorGlyphs.kt): the shapes and icons a decoration may be, the parts it may be
// pinned to and the days it may stand on. Same ids as core; core DecorGlyphsTest reads this file to hold the two together.

export const DECOR_SHAPES = [
  "rect", "round", "circle", "pill", "tri", "rtri", "diamond", "pent", "hex", "oct", "semi", "quarter", "arch", "ring", "frame",
  "para", "trap", "plus", "cross", "star4", "star5", "star6", "star8", "burst", "sparkle", "seal", "heart", "drop", "moon",
  "sun", "cloud", "leaf", "flower", "wave", "mountain", "snow", "blob1", "blob2", "blob3", "bubble", "bubble_r", "think", "tag",
  "ribbon", "bookmark", "shield", "badge", "pin", "check", "tape", "corner", "line_h", "line_v", "line_d", "dash_h", "zigzag",
  "squiggle", "arrow_r", "arrow_l", "arrow_u", "arrow_d", "chevron", "underline", "circle_mark",
];

export const DECOR_ICONS = [
  "favorite", "favorite_border", "star", "star_border", "emoji_emotions", "mood", "mood_bad", "thumb_up", "thumb_down",
  "auto_awesome", "celebration", "cake", "card_giftcard", "emoji_events", "military_tech", "diamond", "wb_sunny", "dark_mode",
  "light_mode", "nights_stay", "bedtime", "cloud", "umbrella", "ac_unit", "thunderstorm", "water_drop", "local_florist", "spa",
  "park", "eco", "grass", "pets", "beach_access", "terrain", "waves", "bolt", "local_fire_department", "whatshot",
  "emoji_nature", "filter_vintage", "local_cafe", "coffee", "restaurant", "local_pizza", "icecream", "bakery_dining",
  "lunch_dining", "ramen_dining", "local_bar", "wine_bar", "sports_bar", "local_drink", "work", "business_center", "engineering",
  "construction", "handyman", "build", "agriculture", "factory", "store", "storefront", "local_hospital", "medical_services",
  "school", "computer", "badge", "local_shipping", "attach_money", "savings", "payments", "credit_card",
  "account_balance_wallet", "shopping_cart", "shopping_bag", "receipt", "trending_up", "trending_down", "show_chart",
  "pie_chart", "bar_chart", "calculate", "redeem", "local_offer", "alarm", "schedule", "timer", "hourglass_empty", "event",
  "calendar_month", "today", "event_note", "push_pin", "bookmark", "flag", "label", "notifications", "notifications_active",
  "checklist", "task_alt", "hotel", "king_bed", "weekend", "bathtub", "self_improvement", "fitness_center", "directions_run",
  "directions_walk", "directions_bike", "pool", "sports_soccer", "sports_esports", "medication", "healing", "monitor_heart",
  "psychology", "directions_car", "directions_bus", "directions_subway", "train", "flight", "sailing", "rocket_launch",
  "location_on", "map", "explore", "home", "apartment", "music_note", "headphones", "photo_camera", "palette", "brush", "movie",
  "menu_book", "edit", "lightbulb", "science", "chat", "mail", "person", "people", "groups", "face", "child_care",
  "family_restroom", "support_agent", "volunteer_activism", "check_circle", "cancel", "add_circle", "info", "warning", "error",
  "help", "lock", "key", "verified", "workspace_premium", "recycling", "arrow_forward", "arrow_back", "arrow_upward",
  "arrow_downward",
];

export const DECOR_SCREENS = ["calendar", "memo", "money", "stats", "pay", "plaza", "settings"];
export const DECOR_ANCHORS = ["app", ...DECOR_SCREENS.map((s) => `bg.${s}`), "cal.head", "cal.week", "cal.cell", "widget"];
export const DECOR_LINES = ["none", "solid", "dash", "dot", "double"];
export const DECOR_MAX = 40;
const DECOR_ON_RE = /^(today|sat|sun|holiday|dim|role\.(day|eve|night|long|off|other)|leave|extra|cover|edited|d([1-9]|[12][0-9]|3[01])|w[1-7]|m([1-9]|1[0-2])d([1-9]|[12][0-9]|3[01]))$/;
const DECOR_FONT_RE = /^([a-z][a-z0-9-]{1,39}|u\+[0-9a-f]{16})$/;
export const decorOn = (s) => (s === "" || DECOR_ON_RE.test(s) ? null : "on");
export const decorFont = (s) => (s === "" || DECOR_FONT_RE.test(s) ? null : "font");
// [LAY-10] 1.36 꾸밈 사진: a 16-hex picture id from POST /api/recipes/images (photo-guard.js imagesOf finds it like any other).
const DECOR_PHOTO_RE = /^[0-9a-f]{16}$/;
export const decorPhoto = (s) => (s === "" || DECOR_PHOTO_RE.test(s) ? null : "photo");
// [LAY-11] 1.36 꾸밈 단추: a 규칙 단추 id (갈래 D logic buttons).
const DECOR_TAP_RE = /^[a-z0-9]{1,12}$/;
export const decorTap = (s) => (s === "" || DECOR_TAP_RE.test(s) ? null : "tap");

// 2.0.0 [RVM-23] RCP-17·STU-19 (보이지 않는 꾸밈 단추, 칸 전체를 덮는 꾸밈 단추가 날짜 누르기를 가로챔): core DecorTapRule, the same numbers
// and rule. A decoration takes a tap only when one can see it (not hidden, drawn at least `seen` % strong), it is not a whole-screen
// picture, it is button-sized (at most maxW × maxH dp), and at most perPlace are on one part. A work with one that may not is refused
// with "decor_tap" (the app's 올리기 점검 says so first and 고치기 leaves it a picture), so 1.36~1.39 phones never get one.
export const DECOR_TAP = { seen: 40, maxW: 160, maxH: 64, perPlace: 3 };
export const DECOR_STROKES = ["ring", "frame", "line_h", "line_v", "line_d", "dash_h", "zigzag", "squiggle", "check", "underline", "circle_mark", "snow"];

const alphaOf = (c) => (c === undefined || c === null ? 1 : ((Number(c) >>> 24) & 0xff) / 255);

function decorKind(i) {
  if (typeof i.text === "string" && i.text !== "") return "text";
  if (typeof i.emoji === "string") return "emoji";
  if (typeof i.icon === "string") return "icon";
  if (typeof i.shape === "string") return "shape";
  return "photo";
}

/** core DecorTapRule.seen: the item as drawn can be seen. */
export function decorSeen(i) {
  if (i.hide === true) return false;
  const a = (i.alpha ?? 100) / 100;
  const enough = DECOR_TAP.seen / 100;
  if (a < enough) return false;
  const fill = alphaOf(i.fill) * (i.fillA ?? 100) / 100 * a;
  const line = i.line !== undefined && i.line !== null && i.line !== "none" && (i.lineW ?? 0) >= 1 && alphaOf(i.lineC) * a >= enough;
  switch (decorKind(i)) {
    case "shape":
      if (DECOR_STROKES.includes(i.shape)) return (i.lineC !== undefined && i.lineC !== null ? alphaOf(i.lineC) * a : fill) >= enough;
      return fill >= enough || line;
    case "icon": return alphaOf(i.ink ?? i.fill) * a >= enough;
    case "text": return alphaOf(i.ink) * a >= enough || ((i.fill !== undefined && i.fill !== null) && fill >= enough) || line;
    default: return true;
  }
}

/** The index of the first item whose tap presses nothing (core DecorTapRule.taps), or -1. */
export function decorTapProblem(items) {
  const n = new Map();
  for (let k = 0; k < items.length; k++) {
    const i = items[k];
    if (!i || i.tap === undefined || i.tap === null || i.tap === "") continue;
    const ok = i.full !== true && (i.w ?? 24) <= DECOR_TAP.maxW && (i.h ?? 24) <= DECOR_TAP.maxH && decorSeen(i);
    if (!ok) return k;
    const c = (n.get(i.at) ?? 0) + 1;
    n.set(i.at, c);
    if (c > DECOR_TAP.perPlace) return k;
  }
  return -1;
}
