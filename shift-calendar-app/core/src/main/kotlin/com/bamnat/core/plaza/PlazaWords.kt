package com.bamnat.core.plaza

/**
 * 2.0.0 [RVN-18] 검토 COMP-19·I10aa-04: 광장·회사 화면의 낱말 사전. One thing has one name, so a first-time reader never takes two words
 * for two features: '맞았어요/맞아요', '마을 이름/별명', '가린 사람/안 보기/차단', '판/버전/v3' were all on the same screens.
 *
 * The app's company screens say these ([com.bamnat.app.company.PlazaWordsTest] in the app reads them for [OTHER_NAMES]). '차단' stays the
 * name of 마을 › 팔로잉 › 차단, the one list where 광장's 안 보기 and 장터's 차단 meet; the act in 광장 is 안 보기.
 */
object PlazaWords {
    /** The vote on a setup, a rate, a proposal (and what it is called afterwards: ‘맞아요’라고 알려 주셨어요). */
    const val AGREE = "맞아요"
    const val DISAGREE = "달라요"
    /** The one name a person has in 마을 (광장 글, 장터 작품, 프로필). */
    const val NAME = "마을 이름"
    /** Not seeing one writer's posts any more (this phone only). */
    const val HIDE = "이 사람 글 안 보기"
    const val HIDDEN = "안 보는 사람"
    /** One company's rules as one sent them; the adopted one is the 대표 판. */
    const val EDITION = "판"

    /** "3판": an edition's number as said on a chip or a line (before, "v3"). */
    fun edition(n: Int): String = "${n}판"

    /** " · 3판" after a name, or nothing without a number. */
    fun editionAfter(n: Int?): String = n?.let { " · ${edition(it)}" } ?: ""

    /**
     * Words for the same thing said another way, never on the company screens: the word, and the one to say instead. A word that is
     * right in another sense (the app's own 버전 in "앱을 최신 버전으로") is not here.
     */
    val OTHER_NAMES: Map<String, String> = linkedMapOf(
        "맞았어요" to AGREE,
        "별명" to NAME,
        "가린 사람" to HIDDEN,
        "가린 분" to HIDDEN,
        "다음 버전" to "다음 판",
        "새 버전으로 바뀌" to "새 판으로 바뀌",
        "지난 버전" to "지난 판",
        "이전 버전" to "이전 판",
        "채택된 버전" to "채택된 판",
    )

    /** The other names in [text] (empty when it says each thing by its one name). */
    fun others(text: String): List<String> = OTHER_NAMES.keys.filter { it in text }
}
