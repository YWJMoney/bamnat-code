package com.bamnat.core.stats

import com.bamnat.core.work.WorkStyle

/**
 * 1.39 [SETUP-20] (사용자 결정 2026-11-07, 발굴 G6 4-1): '처음 1분'을 숫자로. 처음 깐 휴대폰이 첫 설정을 저장할 때까지 화면을
 * 누른 횟수를 [BUCKETS]의 위쪽 끝으로 올려 하나만 남기고, 고른 일하는 방식과 함께 사용 통계에 한 번 실어요(`setup: {style, taps}`).
 * 무엇을 눌렀는지·시각·휴대폰 구별은 없어요. 서버(usage.js SETUP_TAP_BUCKETS)가 같은 값만 받아요.
 */
object SetupTaps {
    /** 150 means more than 100. */
    val BUCKETS = listOf(5, 10, 15, 20, 25, 30, 40, 50, 70, 100, 150)

    fun bucket(taps: Int): Int = BUCKETS.firstOrNull { taps <= it } ?: BUCKETS.last()

    /** What the bundle carries for [style] after [taps]: "SHIFT,20". */
    fun encode(style: WorkStyle, taps: Int): String = "${style.name},${bucket(taps)}"

    /** (style name, bucket) back, or null for anything else. */
    fun decode(text: String?): Pair<String, Int>? {
        val p = text?.split(',') ?: return null
        if (p.size != 2) return null
        val style = WorkStyle.entries.firstOrNull { it.name == p[0] } ?: return null
        val b = p[1].toIntOrNull()?.takeIf { it in BUCKETS } ?: return null
        return style.name to b
    }
}
