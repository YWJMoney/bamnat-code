// 1.36 [MAT-01..07] 꾸미기 재료 (스튜디오 완전 자유도 867~873): 채우기·선·글꼴·사진 조정·사진 틀·움직임, as core Fill.kt, FontCatalog.kt,
// PhotoStyle.kt and Motion.kt read them (same words, same limits). `docs/recipe-cases.json` "materials" holds the two together.

const MAT_HEX = "[0-9A-F]{8}";
const MAT_DEG = "(3[0-5][0-9]|[12]?[0-9]?[0-9])";
const MAT_PCT = "(100|[1-9]?[0-9])";
const MAT_STOP = `${MAT_HEX}(@${MAT_PCT})?`;
const MAT_STOPS = `${MAT_STOP}(,${MAT_STOP}){1,4}`;
const MAT_SIZE = "(300|[12][0-9][0-9]|[5-9][0-9])";
const MAT_RAD = "(150|1[0-4][0-9]|[1-9][0-9])";
const MAT_BLUR = "(40|[1-3]?[0-9])";
const MAT_SPEED = "(10|[0-9])";

export const MAT_PATTERNS = ["dots", "bigdots", "stripes", "thin", "check", "grid", "wave", "zigzag", "heart", "star", "cross", "diamond", "brick",
  "scale", "plaid", "confetti"];
export const MAT_TEXTURES = ["paper", "linen", "noise", "grain", "canvas", "kraft", "felt", "concrete"];
export const MAT_SHADERS = ["aurora", "mesh", "ripple", "plasma", "flow", "sunset"];

const MAT_FILL = new RegExp(
  `^(so\\.${MAT_HEX}|lg\\.${MAT_DEG}\\.${MAT_STOPS}|rg\\.${MAT_PCT}\\.${MAT_PCT}\\.${MAT_RAD}\\.${MAT_STOPS}|sg\\.${MAT_PCT}\\.${MAT_PCT}\\.${MAT_DEG}\\.${MAT_STOPS}|` +
  `pt\\.(${MAT_PATTERNS.join("|")})\\.${MAT_HEX}\\.${MAT_HEX}\\.${MAT_SIZE}\\.${MAT_DEG}|tx\\.(${MAT_TEXTURES.join("|")})\\.${MAT_HEX}\\.${MAT_PCT}|` +
  `gl\\.${MAT_HEX}\\.${MAT_BLUR}|sh\\.(${MAT_SHADERS.join("|")})\\.${MAT_HEX}(,${MAT_HEX}){1,2}\\.${MAT_SPEED})$`,
);
export const fillProblem = (s) => (typeof s === "string" && s.length <= 80 && MAT_FILL.test(s) ? null : "fill");

const MAT_STROKE = /^(solid|dash|dot|double)\.([5-9]|[1-3][0-9]|40)\.[0-9A-F]{8}\.(100|[1-9]?[0-9])$/;
export const strokeProblem = (s) => (MAT_STROKE.test(s) ? null : "stroke");

// core FontCatalog.IDS (system four, open Korean fonts, digits·Latin fonts) and the user's own (u + 16 hex).
export const FONT_IDS = [
  "sans", "serif", "mono", "hand",
  "notosanskr", "nanumgothic", "gothica1", "ibmplexsanskr", "gowundodum", "orbit", "sunflower", "notoserifkr", "nanummyeongjo", "gowunbatang",
  "songmyung", "hahmlet", "diphylleia", "jua", "dongle", "cutefont", "stylish", "gamjaflower", "himelody", "poorstory", "singleday", "nanumpen",
  "nanumbrush", "gaegu", "yeonsung", "dokdo", "eastseadokdo", "kiranghaerang", "bwpicture", "blackhansans", "dohyeon", "gugi", "bagelfatone",
  "gasoekone", "moiraione", "grandifloraone", "nanumgothiccoding",
  "bebasneue", "oswald", "anton", "archivoblack", "montserrat", "poppins", "quicksand", "comfortaa", "fredoka", "nunito", "rubik", "josefinsans",
  "playfair", "lora", "abrilfatface", "dmserif", "pacifico", "lobster", "dancingscript", "caveat", "permanentmarker", "shadowsintolight",
  "righteous", "bungee", "monoton", "shrikhand", "orbitron", "pressstart", "silkscreen", "vt323", "spacemono", "robotomono", "jetbrainsmono",
];
const MAT_FONT_SET = new Set(FONT_IDS);
const MAT_USER_FONT = /^u[0-9a-f]{16}$/;
export const fontId = (s) => (MAT_FONT_SET.has(s) || MAT_USER_FONT.test(s) ? null : "font");
export const isUserFont = (s) => MAT_USER_FONT.test(s);

export const MAT_BLENDS = ["normal", "multiply", "screen", "overlay", "darken", "lighten", "softlight", "color", "luminosity"];
const MAT_N100 = "(-?(100|[1-9]?[0-9]))";
const MAT_ADJUST_PART = new RegExp(`^(b${MAT_N100}|c${MAT_N100}|s${MAT_N100}|g1|t[0-9A-F]{8}|a(100|[1-9]?[0-9])|u(2[0-5]|1?[0-9])|m(${MAT_BLENDS.join("|")}))$`);
export const photoAdjust = (s) => {
  if (s === "") return null;
  const parts = s.split(",");
  if (!parts.every((x) => MAT_ADJUST_PART.test(x))) return "adjust";
  return new Set(parts.map((x) => x[0])).size === parts.length ? null : "adjust";
};

export const MAT_FRAMES = ["rect", "round", "circle", "star", "drop", "heart", "hexagon", "arch", "cloud", "flower", "ticket"];
const MAT_FRAME = new RegExp(`^(${MAT_FRAMES.join("|")}|t(-[0-9a-f]{2,5}){1,4})$`);
export const photoFrame = (s) => (s === "" || MAT_FRAME.test(s) ? null : "frame");

const MAT_MOTION_PART = /^(tn:(slide|fade|flip|zoom|stack|none)|td:(none|pulse|glow|ring|bounce)|wr:(fade|wipe|circle|none)|sp:[snf]|of:1)$/;
export const motionProblem = (s) => {
  if (s === "") return null;
  const parts = s.split("|");
  if (!parts.every((x) => MAT_MOTION_PART.test(x))) return "motion";
  return new Set(parts.map((x) => x.split(":")[0])).size === parts.length ? null : "motion";
};

// 1.36 parts the older look lines take (core CellLook.extraOk, ScreenLook.partOk).
const MAT_CELL_EXTRA = /^(bw:([5-9]|[1-3][0-9]|40)|bo:(100|[1-9]?[0-9]))$/;
export const cellExtra = (p) => MAT_CELL_EXTRA.test(p) || (p.startsWith("fl:") && fillProblem(p.slice(3)) === null) ||
  (p.startsWith("ff:") && fontId(p.slice(3)) === null);
export const screenExtra = (p) => (p.startsWith("fl:") && fillProblem(p.slice(3)) === null) ||
  (p.startsWith("pa:") && p.length > 3 && photoAdjust(p.slice(3)) === null) || (p.startsWith("ft:") && fontId(p.slice(3)) === null);

/** The checker for each kind of shared case ("materials" in docs/recipe-cases.json). */
export const MATERIAL_CHECKS = { fill: fillProblem, stroke: strokeProblem, font: fontId, adjust: photoAdjust, frame: photoFrame, motion: motionProblem };
