package com.bamnat.core.recipe

/**
 * [RCP-05] 입력 칸 자동 추출 (설계서 3-2 2번, 개인정보 기본 보호): a recipe made from the maker's own settings carries their
 * 시급·일당 and other amounts. Before anything else, each amount becomes "받는 사람이 정할 칸": an [Input] whose default is
 * empty or the legal value (최저임금 for a 시급), so a maker's pay never leaves the phone unless they turn the field back into a
 * fixed value on purpose ([keep], 회사 공통 정액처럼).
 */
object InputExtract {
    /** What a money field is called on the receiver's screen. */
    private val LABELS = mapOf("wage" to "시급", "dayRate" to "일당")

    /** One amount found: where, its label, and the value the maker had. */
    data class Found(val piece: PieceKind, val field: String, val label: String, val value: Long)

    /** Every amount written in [r]'s pieces as a plain value (not yet an input). */
    fun found(r: Recipe): List<Found> = r.pieces.flatMap { p ->
        p.kind.fields.filterIsInstance<Field.Money>().mapNotNull { f ->
            val v = p.values[f.key] as? Long ?: return@mapNotNull null
            Found(p.kind, f.key, LABELS[f.key] ?: f.key, v)
        }
    }

    /**
     * [r] with each amount turned into an input, except the fields named in [keep] (`<piece key>.<field>`). [legal] gives the
     * default the receiver starts from (최저임금 for a 시급), or null to leave it empty and required.
     */
    fun extract(r: Recipe, keep: Set<String> = emptySet(), legal: (PieceKind, String) -> Long? = { _, _ -> null }): Recipe {
        val inputs = r.inputs.toMutableList()
        var next = (inputs.mapNotNull { it.id.removePrefix("i").toIntOrNull() }.maxOrNull() ?: 0) + 1
        val pieces = r.pieces.map { p ->
            val values = LinkedHashMap(p.values)
            for (f in p.kind.fields.filterIsInstance<Field.Money>()) {
                if (values[f.key] !is Long || "${p.kind.key}.${f.key}" in keep || next > RecipeCodec.MAX_INPUTS) continue
                val def = legal(p.kind, f.key)
                val id = "i${next++}"
                inputs += Input(
                    id, LABELS[f.key] ?: f.key, InputKind.MONEY, default = def, min = f.lo.toDouble(), max = f.hi.toDouble(),
                    required = def == null, hint = if (def != null) "처음 값은 법정 기준이에요. 내 금액으로 바꿔 주세요" else "",
                )
                values[f.key] = Ref.Input(id)
            }
            Piece(p.kind, values)
        }
        return r.copy(pieces = pieces, inputs = inputs)
    }

    /** [r] with input [id] made a fixed value again ([value]): the maker said this amount is the same for everyone. */
    fun keep(r: Recipe, id: String, value: Any): Recipe = r.copy(
        pieces = r.pieces.map { p -> Piece(p.kind, p.values.mapValues { (_, v) -> if (v == Ref.Input(id)) value else v }) },
        inputs = r.inputs.filter { it.id != id },
    )
}
