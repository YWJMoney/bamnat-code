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

    /**
     * fc374a23 PC 확인 (가계부 틀의 예산·봉투 목표가 보낸 사람 금액 그대로 감): an amount inside a list ([Field.Items]: 예산, 봉투) is the
     * maker's too. It is called by the item's name ("식비 예산", "여행 목표").
     */
    private val ITEM_LABELS = mapOf("amount" to "예산", "target" to "목표")

    /** Every amount written in [r]'s pieces as a plain value (not yet an input), in lists too. */
    fun found(r: Recipe): List<Found> = r.pieces.flatMap { p ->
        p.kind.fields.flatMap { f ->
            when (f) {
                is Field.Money -> listOfNotNull((p.values[f.key] as? Long)?.let { Found(p.kind, f.key, LABELS[f.key] ?: f.key, it) })
                is Field.Items -> items(p.values[f.key]).flatMapIndexed { i, item ->
                    f.fields.filterIsInstance<Field.Money>().mapNotNull { m ->
                        (item[m.key] as? Long)?.let { Found(p.kind, "${f.key}.$i.${m.key}", itemLabel(f, item, i, m), it) }
                    }
                }
                else -> emptyList()
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun items(v: Any?): List<Map<String, Any?>> = (v as? List<*>).orEmpty().filterIsInstance<Map<String, Any?>>()

    /** "식비 예산": the item's first words and what the amount is, short enough for an input's name. */
    private fun itemLabel(f: Field.Items, item: Map<String, Any?>, i: Int, m: Field.Money): String {
        val name = f.fields.filterIsInstance<Field.Text>().firstNotNullOfOrNull { (item[it.key] as? String)?.trim()?.takeIf { s -> s.isNotEmpty() } }
            ?: "${i + 1}번"
        return "${name.take(12)} ${ITEM_LABELS[m.key] ?: m.key}"
    }

    /**
     * [r] with each amount turned into an input, except the fields named in [keep] (`<piece key>.<field>`). [legal] gives the
     * default the receiver starts from (최저임금 for a 시급), or null to leave it empty and required.
     */
    fun extract(r: Recipe, keep: Set<String> = emptySet(), legal: (PieceKind, String) -> Long? = { _, _ -> null }): Recipe {
        val inputs = r.inputs.toMutableList()
        var next = (inputs.mapNotNull { it.id.removePrefix("i").toIntOrNull() }.maxOrNull() ?: 0) + 1
        fun input(label: String, f: Field.Money, def: Long?, item: Boolean): Ref.Input {
            val id = "i${next++}"
            inputs += Input(
                id, label, InputKind.MONEY, default = def, min = f.lo.toDouble(), max = f.hi.toDouble(),
                // An amount in a list may stay empty: 예산 없이 분류만, 목표 없는 봉투.
                required = def == null && !item,
                hint = when {
                    def != null -> "처음 값은 법정 기준이에요. 내 금액으로 바꿔 주세요"
                    item -> "비워 두면 금액 없이 넣어요"
                    else -> ""
                },
            )
            return Ref.Input(id)
        }
        val pieces = r.pieces.map { p ->
            val values = LinkedHashMap(p.values)
            for (f in p.kind.fields) {
                when (f) {
                    is Field.Money -> {
                        if (values[f.key] !is Long || "${p.kind.key}.${f.key}" in keep || next > RecipeCodec.MAX_INPUTS) continue
                        values[f.key] = input(LABELS[f.key] ?: f.key, f, legal(p.kind, f.key), item = false)
                    }
                    is Field.Items -> {
                        val list = values[f.key] as? List<*> ?: continue
                        val money = f.fields.filterIsInstance<Field.Money>()
                        if (money.isEmpty()) continue
                        values[f.key] = list.mapIndexed { i, x ->
                            @Suppress("UNCHECKED_CAST")
                            val item = x as? Map<String, Any?> ?: return@mapIndexed x
                            val out = LinkedHashMap(item)
                            for (m in money) {
                                if (item[m.key] !is Long || "${p.kind.key}.${f.key}.$i.${m.key}" in keep || next > RecipeCodec.MAX_INPUTS) continue
                                out[m.key] = input(itemLabel(f, item, i, m), m, null, item = true)
                            }
                            out
                        }
                    }
                    else -> {}
                }
            }
            Piece(p.kind, values)
        }
        return r.copy(pieces = pieces, inputs = inputs)
    }

    /** [r] with input [id] made a fixed value again ([value]): the maker said this amount is the same for everyone. In lists too. */
    @Suppress("UNCHECKED_CAST")
    fun keep(r: Recipe, id: String, value: Any): Recipe = r.copy(
        pieces = r.pieces.map { p -> Piece(p.kind, swap(p.values, Ref.Input(id), value) as Map<String, Any?>) },
        inputs = r.inputs.filter { it.id != id },
    )

    private fun swap(v: Any?, ref: Ref.Input, value: Any): Any? = when (v) {
        ref -> value
        is Map<*, *> -> v.entries.associate { (k, x) -> k.toString() to swap(x, ref, value) }
        is List<*> -> v.map { swap(it, ref, value) }
        else -> v
    }

    /** Where input [id] sits in [r] (`<piece key>.<field>`, or `<piece key>.<list>.<n>.<field>`), for [extract]'s keep. */
    fun keyOf(r: Recipe, id: String): String? {
        val ref = Ref.Input(id)
        for (p in r.pieces) for (f in p.kind.fields) {
            val v = p.values[f.key]
            if (v == ref) return "${p.kind.key}.${f.key}"
            if (f is Field.Items) items(v).forEachIndexed { i, item -> item.entries.firstOrNull { it.value == ref }?.let { return "${p.kind.key}.${f.key}.$i.${it.key}" } }
        }
        return null
    }
}
