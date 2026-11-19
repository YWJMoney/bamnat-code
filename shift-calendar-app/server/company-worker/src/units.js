
export const HOUR = 60 * 60 * 1000;
export const DAY = 24 * HOUR;

/**
 * 2.0.0 [RVN-24] 검토 SVC-106: 관리자 앱이 보여 주는 설치 표시(해시 앞 몇 자). 6자는 설치가 몇천 대만 돼도 둘 이상 맞았어요('여러 사람이 찾아져요'),
 * 그래서 8자예요. 찾기는 전에 적어 둔 6자도 받아요.
 */
export const MARK_LEN = 8;
export const deviceMark = (d) => String(d ?? "").slice(0, MARK_LEN);
/** 해시 앞 [mark]로 찾을 범위 [이상, 미만]: LIKE 'abc%'는 색인을 못 타 표 전체를 읽어요. 해시는 소문자 16진수라 'g'가 끝이에요. */
export const markRange = (mark) => [mark, `${mark}g`];
