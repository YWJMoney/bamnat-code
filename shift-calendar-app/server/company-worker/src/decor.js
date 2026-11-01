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
const DECOR_ON_RE = /^(today|sat|sun|holiday|dim|role\.(day|eve|night|long|off|other)|d([1-9]|[12][0-9]|3[01])|w[1-7]|m([1-9]|1[0-2])d([1-9]|[12][0-9]|3[01]))$/;
const DECOR_FONT_RE = /^([a-z][a-z0-9-]{1,39}|u\+[0-9a-f]{16})$/;
export const decorOn = (s) => (s === "" || DECOR_ON_RE.test(s) ? null : "on");
export const decorFont = (s) => (s === "" || DECOR_FONT_RE.test(s) ? null : "font");
