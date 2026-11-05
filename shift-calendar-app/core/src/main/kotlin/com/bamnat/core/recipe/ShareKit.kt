package com.bamnat.core.recipe

import com.bamnat.core.work.WorkStyle

/**
 * 1.37 묶음 E (사용자 2026-11-04 "달력 공유 시 테마와 설정까지 함께"): what a 근무표 링크 may carry besides the days, as two recipes
 * the receiving app reads with [RecipeCodec] like any other.
 *
 * - [SHR-31] 테마: the look pieces only ([BuiltInThemes.LOOK_KINDS]); the sender takes the photos out first.
 * - [SHR-32] 근무 설정: [KINDS] only — 근무 꾸미기 (names, letters, colours), 알람 시각 틀 (minutes before each kind of shift) and the
 *   근무 주기 (a company file with no `pay` part at all). Never 급여 규칙, 기본급, an amount, a memo or a photo: a kit whose 근무 주기
 *   carries a pay part loses that piece on both sides (the share server refuses it too).
 *
 * Each side keeps only what it can read: a piece the codec refuses is dropped, not the whole kit.
 */
object ShareKit {
    /** The share server's limits for each text, in UTF-16 units as it counts them. */
    const val LOOK_CHARS = 32 * 1024
    const val KIT_CHARS = 24 * 1024

    /** The 근무 설정 a link can carry, in the order the receiver sees them. */
    val KINDS: List<PieceKind> = listOf(PieceKind.SHIFT_LOOK, PieceKind.ALARMS, PieceKind.COMPANY)

    /** The kinds of [KINDS] that fit [style] (유형 렌즈); the 근무 주기 only with [cycle] (the main calendar, no company setup sent). */
    fun kindsFor(style: WorkStyle?, cycle: Boolean): List<PieceKind> =
        KINDS.filter { (style == null || style in it.styles) && (cycle || it != PieceKind.COMPANY) }

    /** Whether a link of [style] can carry 근무 설정 at all. */
    fun kitFits(style: WorkStyle?): Boolean = kindsFor(style, cycle = true).isNotEmpty()

    /** [r]'s looks as a link carries them; null when there is no look or it is too big. */
    fun lookText(r: Recipe): String? = text(r.copy(pieces = r.pieces.filter { it.kind in BuiltInThemes.LOOK_KINDS }), LOOK_CHARS)

    /** [r]'s 근무 설정 as a link carries them; null when there is nothing or it is too big. */
    fun kitText(r: Recipe): String? = text(r.copy(pieces = r.pieces.filter { it.kind in KINDS && !paid(it) }), KIT_CHARS)

    /** A link's 테마 as the receiver may try it on; null when it holds no look. */
    fun readLook(text: String?): Recipe? = read(text, LOOK_CHARS) { it.kind in BuiltInThemes.LOOK_KINDS }

    /** A link's 근무 설정 as the receiver may pick from; null when it holds none of [KINDS]. */
    fun readKit(text: String?): Recipe? = read(text, KIT_CHARS) { it.kind in KINDS && !paid(it) }

    /** A 근무 주기 that carries any part of the pay (a company file's `pay`), which a link never takes. */
    fun paid(p: Piece): Boolean {
        if (p.kind != PieceKind.COMPANY) return false
        val setup = p.values["setup"] as? String ?: return true
        val m = runCatching { com.bamnat.core.remote.Json.parse(setup) }.getOrNull() as? Map<*, *> ?: return true
        return m.containsKey("pay")
    }

    private fun text(r: Recipe, max: Int): String? {
        // Each piece on its own first: one the codec would refuse (a name it takes for a phone number…) stays home, the rest go.
        val ok = r.pieces.filter { p -> runCatching { RecipeCodec.read(RecipeCodec.write(r.copy(pieces = listOf(p)))) }.isSuccess }
        if (ok.isEmpty()) return null
        val t = RecipeCodec.write(r.copy(pieces = ok))
        return t.takeIf { it.length <= max && runCatching { RecipeCodec.read(it) }.isSuccess }
    }

    private fun read(text: String?, max: Int, keep: (Piece) -> Boolean): Recipe? {
        if (text.isNullOrEmpty() || text.length > max) return null
        val r = runCatching { RecipeCodec.read(text) }.getOrNull() ?: return null
        return r.copy(pieces = r.pieces.filter(keep)).takeIf { it.pieces.isNotEmpty() }
    }
}
