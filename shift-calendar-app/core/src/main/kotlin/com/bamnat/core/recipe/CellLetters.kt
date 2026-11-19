package com.bamnat.core.recipe

/**
 * 2.0.0 [PC-33] 칸의 근무 글자 (PC 통합 1차, 야간 회복 스테이션: 칸의 주·야·휴 글자가 사라지고 점만 남아 주간·야간을 색으로만 가려야 해요,
 * 디자인 품질 조건 14). A 달력 칸 디자인 whose shift mark is a colour alone (점만·선·근무 색 꽉 채우기) is said in the 넣기 창, and
 * '글자 남기기' puts it in with the shift's letter kept: the same look, its mark the small letter badge in the shift's colour.
 */
object CellLetters {
    /** The marks that show a day's shift by its colour alone. */
    val COLOUR_ONLY = setOf("dot", "line", "fill")

    private fun looks(r: Recipe): String? = r.pieces.firstOrNull { it.kind == PieceKind.CAL_VIEW }?.values?.get("looks") as? String

    /** The colour-only mark [r]'s cells show a shift with ("dot", "line" or "fill"), or null when they keep its letter. */
    fun colourOnly(r: Recipe): String? {
        val all = looks(r)?.split('/') ?: return null
        // Each look's own mark; one that names none uses 달력 모양's badge (the app's letter badge unless it says otherwise).
        val badge = r.pieces.firstOrNull { it.kind == PieceKind.CAL_VIEW }?.values?.get("badge") as? String
        return all.map { CellLook.decode(it).mark ?: badge }.firstOrNull { it in COLOUR_ONLY }
    }

    /** [looks] with every colour-only mark as the small letter badge; a 꽉 채우기 keeps its colour as the cell's own (bg:shift). */
    fun keepLetters(looks: String): String = looks.split('/').joinToString("/") { look ->
        val parts = look.split('|').filter { it.isNotEmpty() }
        val mark = parts.firstOrNull { it.startsWith("mk:") }?.removePrefix("mk:")
        if (mark !in COLOUR_ONLY) look
        else buildList {
            parts.forEach { p -> add(if (p.startsWith("mk:")) "mk:small" else p) }
            if (mark == "fill" && parts.none { it.startsWith("bg:") }) {
                add("bg:shift")
                if (parts.none { it.startsWith("ba:") }) add("ba:85")
            }
        }.joinToString("|")
    }

    /** [r] with its 칸 디자인 keeping the shift letters ([keepLetters]); a badge of 달력 모양 that is a colour alone becomes small too. */
    fun keep(r: Recipe): Recipe = r.copy(pieces = r.pieces.map { p ->
        if (p.kind != PieceKind.CAL_VIEW) p
        else {
            var v = p.values
            (v["looks"] as? String)?.let { v = v + ("looks" to keepLetters(it)) }
            if ((v["badge"] as? String) in COLOUR_ONLY) v = v + ("badge" to "small")
            p.copy(values = v)
        }
    })

    /** How the 넣기 창 says it: "칸의 근무 글자 대신 색 점만 보여요". */
    fun said(mark: String): String = "칸의 근무 글자 대신 " + when (mark) {
        "dot" -> "색 점만"
        "line" -> "색 줄만"
        else -> "칸 색만"
    } + " 보여요"
}
