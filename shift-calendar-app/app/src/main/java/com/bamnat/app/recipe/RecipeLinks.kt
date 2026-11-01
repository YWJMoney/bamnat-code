package com.bamnat.app.recipe

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * [RCP-22] 링크로 받기: a recipe's short code (8 letters, no 0·O·1·I), its web link (the public page, which opens the app) and
 * `bamnat://r/CODE`. A pasted message works too: the code is found inside it.
 */
object RecipeLinks {
    const val WEB = "https://ywjmoney.github.io/-/r/?c="
    private val CODE = Regex("(?<![A-Za-z0-9])([2-9A-HJ-NP-Z]{8})(?![A-Za-z0-9])")
    private val LINK = Regex("""(?:bamnat://r/|/r/\?c=|/r/)([2-9A-HJ-NP-Za-hj-np-z]{8})(?![A-Za-z0-9])""")

    /** The code in [text]: from a link first, else a bare code (letters as typed, made upper case). */
    fun find(text: String?): String? {
        val t = text?.trim().orEmpty()
        if (t.isEmpty()) return null
        LINK.find(t)?.let { return it.groupValues[1].uppercase().takeIf { c -> CODE.matches(c) } }
        return CODE.find(t.uppercase())?.groupValues?.get(1)
    }

    fun web(code: String) = WEB + code
    fun app(code: String) = "bamnat://r/$code"

    /** What 링크 보내기 sends: the title, the link and the code to type in. */
    fun shareText(title: String, code: String) = "‘$title’ 레시피 · 밤낮 마을 장터\n${web(code)}\n코드: $code"

    /**
     * [PRF-11] 1.36 프로필 링크: the company server's small public page of a maker (`/m/<id>`, which opens the app with
     * `bamnat://m/<id>`), and finding the id in a tapped link.
     */
    fun profile(maker: String) = com.bamnat.app.BuildConfig.COMPANY_BASE_URL.trimEnd('/') + "/m/" + maker
    private val MAKER = Regex("""(?:bamnat://m/|/m/)([0-9a-f]{12}|operator)(?![A-Za-z0-9])""")
    fun findMaker(text: String?): String? = text?.let { MAKER.find(it)?.groupValues?.get(1) }
    private val _openingMaker = MutableStateFlow<String?>(null)
    val openingMaker: StateFlow<String?> = _openingMaker.asStateFlow()
    fun openMaker(id: String?) { if (id != null) _openingMaker.value = id }
    fun openedMaker() { _openingMaker.value = null }

    /** A link tapped outside the app, waiting for the app to open the recipe. */
    private val _opening = MutableStateFlow<String?>(null)
    val opening: StateFlow<String?> = _opening.asStateFlow()
    fun open(code: String?) { if (code != null) _opening.value = code }
    fun opened() { _opening.value = null }

    /** [RCP-19] '이 설정 나누기' on a settings screen: 만들기 opens with that piece chosen. */
    private val _make = MutableStateFlow<com.bamnat.core.recipe.PieceKind?>(null)
    val make: StateFlow<com.bamnat.core.recipe.PieceKind?> = _make.asStateFlow()
    fun share(kind: com.bamnat.core.recipe.PieceKind) { _make.value = kind }
    fun shared() { _make.value = null }

    /** 고쳐 만들기: the written recipe and its code, handed to 만들기 once (in memory). */
    var remake: Pair<String, String>? = null

    /** [THM-01] The key of one of my themes being sent from 스튜디오: once it is up, it is marked as in 장터 (no days, no ad). */
    var ownTheme: String? = null
    const val NEW_OWN = "new"

    val DISLIKE_REASONS = linkedMapOf(
        "thin" to "설명이 부족해요", "odd" to "내 달력에서 이상해요", "same" to "비슷한 게 이미 있어요", "hard" to "쓰기 어려워요", "other" to "그 밖의 것",
    )
    val FLAG_REASONS = linkedMapOf(
        "personal" to "개인 정보가 있어요", "fake" to "사칭·거짓이에요", "harm" to "해롭거나 불쾌해요", "spam" to "광고·도배예요",
        "copy" to "남의 것을 베꼈어요", "other" to "그 밖의 것",
    )
    val TALK_KINDS = linkedMapOf("question" to "질문", "opinion" to "의견", "idea" to "이렇게 바꿔 봐요", "review" to "써 본 후기")
}
