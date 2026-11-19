package com.bamnat.core.recipe

import kotlin.math.roundToInt

/**
 * 2.0.0 [RVS-07] 작품 크기 (검토 SD-04 ①③·SN-08·SI-04, 스튜디오 조사 5장 S4): how much of what 올리기 takes a work uses, said before
 * it is too late. A draft may grow past it ([RecipeCodec.readDraft]); only 올리기 keeps the limits, and then the studio says which
 * pieces to take out first instead of a bare "조각을 줄여 주세요".
 */
object WorkSize {
    /** From this share of a limit on, the studio's 작품 크기 chip says it is nearly full (주의). */
    const val NEAR = 0.8

    class Of(val pieces: Int, val bytes: Int) {
        /** The fuller of the two, 0..: 1.0 is the limit. */
        val share: Double get() = maxOf(pieces.toDouble() / RecipeCodec.MAX_PIECES, bytes.toDouble() / RecipeCodec.MAX_BYTES)
        val over: Boolean get() = pieces > RecipeCodec.MAX_PIECES || bytes > RecipeCodec.MAX_BYTES
        val near: Boolean get() = !over && share >= NEAR
        /** 용량 as a share of [RecipeCodec.MAX_BYTES], in whole percent. */
        val percent: Int get() = (bytes * 100.0 / RecipeCodec.MAX_BYTES).roundToInt()

        /** The chip's words: '조각 12/16 · 용량 40%'. */
        val line: String get() = "조각 $pieces/${RecipeCodec.MAX_PIECES} · 용량 $percent%"

        /** What is over, in a maker's words; null when it fits. */
        val overLine: String?
            get() = when {
                pieces > RecipeCodec.MAX_PIECES && bytes > RecipeCodec.MAX_BYTES ->
                    "조각이 ${pieces}개, 용량이 $percent%예요. 장터에는 조각 ${RecipeCodec.MAX_PIECES}개, 용량 100%까지 올라가요"
                pieces > RecipeCodec.MAX_PIECES -> "조각이 ${pieces}개예요. 장터에는 ${RecipeCodec.MAX_PIECES}개까지 올라가요 · ${pieces - RecipeCodec.MAX_PIECES}개를 빼 주세요"
                bytes > RecipeCodec.MAX_BYTES -> "용량이 $percent%예요. 장터에는 100%까지 올라가요 · 사진·무늬·줄이 많은 조각부터 줄여 주세요"
                else -> null
            }
    }

    /** [r] as 올리기 would send it. */
    fun of(r: Recipe): Of = Of(r.pieces.size, RecipePlatform.utf8(RecipeCodec.write(r)).size)

    /**
     * The pieces to take out first when [r] is over, so it fits with the least lost: one just as the app has it ([plain], it changes
     * nothing on a phone that wears it) first; then, while it is too big, the biggest, and while it only has too many pieces, the
     * smallest. Only as many as it takes; empty when it fits. The maker picks; this is what the studio offers first.
     */
    fun takeOut(r: Recipe, plain: List<Piece> = emptyList()): List<PieceKind> {
        fun bytes(p: Piece) = RecipePlatform.utf8(RecipeCodec.write(r.copy(pieces = listOf(p)))).size
        val sizes = r.pieces.associateWith { bytes(it) }
        val out = ArrayList<PieceKind>()
        val left = r.pieces.toMutableList()
        while (left.isNotEmpty()) {
            val now = of(r.copy(pieces = left))
            if (!now.over) break
            val p = left.firstOrNull { p -> plain.any { it.kind == p.kind && it.values == p.values } }
                ?: if (now.bytes > RecipeCodec.MAX_BYTES) left.maxBy { sizes.getValue(it) } else left.minBy { sizes.getValue(it) }
            out += p.kind
            left.remove(p)
        }
        return out
    }

    /** [takeOut]'s pieces said as a line: '먼저 빼 볼 조각: 위젯·메모 틀'. */
    fun takeOutLine(kinds: List<PieceKind>): String? = kinds.takeIf { it.isNotEmpty() }?.joinToString("·", prefix = "먼저 빼 볼 조각: ") { it.label }
}
