// 밤낮 색 아이콘 도구: 96 격자, 두 색 그라데이션 + 빛 한 줄 + 옅은 그림자. 남의 그림을 쓰지 않고 여기서 직접 그려요.
export const P = {
  pink: ['#FFA3BE', '#FF4F7B'], red: ['#FF9A8F', '#E5394E'], orange: ['#FFC57F', '#FF7A2F'], yellow: ['#FFEC8F', '#FFB21E'],
  green: ['#A9EE8E', '#2DB262'], mint: ['#93F1D8', '#17B49C'], sky: ['#A3DBFF', '#3A8DFF'], blue: ['#9AB4FF', '#4C5BFF'],
  purple: ['#CDAEFF', '#7A4BFF'], brown: ['#DDA97F', '#8A5532'], tan: ['#F5DDBE', '#D6A877'], gray: ['#EEF1F7', '#B4BDCE'],
  white: ['#FFFFFF', '#E8ECF6'], dark: ['#646B88', '#2C3048'], gold: ['#FFE68C', '#E8A317'], cream: ['#FFF8E6', '#FFE2B0'],
  lav: ['#E6DBFF', '#B9A2F5'], peach: ['#FFD6C2', '#FF9F80'], teal: ['#7FE3E0', '#159AA8'], wine: ['#E57A9C', '#9B2253'],
};
let n = 0, defs = [];
export function reset() { n = 0; defs = []; }
/** 그라데이션 하나: P 이름이나 [a,b]. */
export function g(c, ang = 135) {
  const [a, b] = Array.isArray(c) ? c : P[c];
  const id = `g${n++}`;
  defs.push(`<linearGradient id="${id}" x1="0" y1="0" x2="${Math.cos((ang - 90) * Math.PI / 180) * 0.5 + 0.5}" y2="1" gradientUnits="objectBoundingBox"><stop offset="0" stop-color="${a}"/><stop offset="1" stop-color="${b}"/></linearGradient>`);
  return `url(#${id})`;
}
export function rg(c) {
  const [a, b] = Array.isArray(c) ? c : P[c];
  const id = `g${n++}`;
  defs.push(`<radialGradient id="${id}" cx=".35" cy=".3" r=".8"><stop offset="0" stop-color="${a}"/><stop offset="1" stop-color="${b}"/></radialGradient>`);
  return `url(#${id})`;
}
/** 흰 빛 한 줄(둥근 끝). */
export const hl = (d, w = 4, o = 0.7) => `<path d="${d}" fill="none" stroke="#fff" stroke-width="${w}" stroke-linecap="round" stroke-linejoin="round" opacity="${o}"/>`;
export const dot = (x, y, r, f = '#fff', o = 1) => `<circle cx="${x}" cy="${y}" r="${r}" fill="${f}" opacity="${o}"/>`;
export const C = (x, y, r, f, extra = '') => `<circle cx="${x}" cy="${y}" r="${r}" fill="${f}" ${extra}/>`;
export const E = (x, y, rx, ry, f, extra = '') => `<ellipse cx="${x}" cy="${y}" rx="${rx}" ry="${ry}" fill="${f}" ${extra}/>`;
export const R = (x, y, w, h, r, f, extra = '') => `<rect x="${x}" y="${y}" width="${w}" height="${h}" rx="${r}" fill="${f}" ${extra}/>`;
export const Pth = (d, f, extra = '') => `<path d="${d}" fill="${f}" ${extra}/>`;
export const L = (d, s, w = 5, extra = '') => `<path d="${d}" fill="none" stroke="${s}" stroke-width="${w}" stroke-linecap="round" stroke-linejoin="round" ${extra}/>`;
/** 귀여운 얼굴(눈 둘, 볼, 입). */
export const face = (x, y, s = 1, ink = '#3A2E4A') =>
  `${C(x - 9 * s, y, 3.2 * s, ink)}${C(x + 9 * s, y, 3.2 * s, ink)}${dot(x - 8 * s, y - 1.2 * s, 1 * s)}${dot(x + 10 * s, y - 1.2 * s, 1 * s)}` +
  `${E(x - 15 * s, y + 6 * s, 4 * s, 2.6 * s, '#FF8FA8', 'opacity=".55"')}${E(x + 15 * s, y + 6 * s, 4 * s, 2.6 * s, '#FF8FA8', 'opacity=".55"')}` +
  L(`M${x - 4 * s} ${y + 5 * s}q${4 * s} ${4 * s} ${8 * s} 0`, ink, 2.4 * s);
export function wrap(body) {
  const d = defs.join('');
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 96 96"><defs><filter id="s" x="-25%" y="-25%" width="150%" height="160%"><feDropShadow dx="0" dy="2.5" stdDeviation="2.6" flood-color="#2a1a4a" flood-opacity=".2"/></filter>${d}</defs><g filter="url(#s)">${body}</g></svg>`;
}
export const star = (cx, cy, R1, R2, k = 5, rot = -90) => {
  let s = '';
  for (let i = 0; i < k * 2; i++) {
    const r = i % 2 ? R2 : R1, a = (rot + i * 180 / k) * Math.PI / 180;
    s += `${i ? 'L' : 'M'}${(cx + r * Math.cos(a)).toFixed(1)} ${(cy + r * Math.sin(a)).toFixed(1)}`;
  }
  return s + 'Z';
};
export const petalRing = (cx, cy, k, r, pr, f, rot = 0) => Array.from({ length: k }, (_, i) => {
  const a = (rot + i * 360 / k) * Math.PI / 180;
  return E((cx + r * Math.cos(a)).toFixed(1), (cy + r * Math.sin(a)).toFixed(1), pr, pr * 0.72, f, `transform="rotate(${rot + i * 360 / k} ${(cx + r * Math.cos(a)).toFixed(1)} ${(cy + r * Math.sin(a)).toFixed(1)})"`);
}).join('');
