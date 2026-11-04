package com.bamnat.core.formula

/**
 * [RUL-06] 1.36 쉬운 보기 (사용자 "다만 사용자가 편하게 넣을 수 있어야 한다"): a condition as a row of picks — [근무] [가] [야간] — and a
 * list of them joined by 그리고 or 또는. It is written as an ordinary formula, so the same value opens in the 고급 보기 (직접 쓰기)
 * and back: [read] turns a formula of this shape back into rows, or null when it is more than rows can say.
 */
data class EasyCond(val name: String, val op: Op, val value: String = "") {
    enum class Op(val label: String, val needsValue: Boolean) {
        IS("이(가)", true), IS_NOT("이(가) 아님", true), GT("보다 큼", true), LT("보다 작음", true), GE("이상", true), LE("이하", true),
        HAS("에 들어 있음", true), HAS_NOT("에 없음", true), YES("참", false), NO("거짓", false), FILLED("있음", false), EMPTY("비었음", false);

        companion object {
            /** The ops that fit a name of [type], the first the usual one. */
            fun forType(type: FormulaName.Type): List<Op> = when (type) {
                FormulaName.Type.NUM -> listOf(GE, GT, LE, LT, IS, IS_NOT)
                FormulaName.Type.TEXT -> listOf(IS, IS_NOT, HAS, HAS_NOT, FILLED, EMPTY)
                FormulaName.Type.BOOL -> listOf(YES, NO)
                FormulaName.Type.LIST -> listOf(HAS, HAS_NOT, FILLED, EMPTY)
            }
        }
    }

    private fun lit(): String {
        val t = FormulaNames.of(name)?.type
        val n = value.trim().replace(",", "").removeSuffix("원").toDoubleOrNull()
        return if (t == FormulaName.Type.NUM && n != null) Formula.numText(n) else "\"" + value.replace("\"", "") + "\""
    }

    fun toFormula(): String = when (op) {
        Op.IS -> "$name = ${lit()}"
        Op.IS_NOT -> "$name != ${lit()}"
        Op.GT -> "$name > ${lit()}"
        Op.LT -> "$name < ${lit()}"
        Op.GE -> "$name >= ${lit()}"
        Op.LE -> "$name <= ${lit()}"
        Op.HAS -> "$name.포함(${lit()})"
        Op.HAS_NOT -> "아님 $name.포함(${lit()})"
        Op.YES -> name
        Op.NO -> "아님 $name"
        Op.FILLED -> "아님 비었나($name)"
        Op.EMPTY -> "비었나($name)"
    }

    /** As a person reads it: "근무가 야간", "꼬리표에 인수인계가 들어 있음". */
    fun say(): String {
        val v = value.ifEmpty { "…" }
        return when (op) {
            Op.IS -> "$name${Josa.iga(name)} $v"
            Op.IS_NOT -> "$name${Josa.iga(name)} $v 아님"
            Op.GT -> "$name${Josa.iga(name)} $v 넘음"
            Op.LT -> "$name${Josa.iga(name)} $v 안 됨"
            Op.GE -> "$name${Josa.iga(name)} $v 이상"
            Op.LE -> "$name${Josa.iga(name)} $v 이하"
            Op.HAS -> "$name 에 $v 있음".replace(" 에", "에")
            Op.HAS_NOT -> "$name 에 $v 없음".replace(" 에", "에")
            Op.YES -> name
            Op.NO -> "$name 아님"
            Op.FILLED -> "$name 있음"
            Op.EMPTY -> "$name 없음"
        }
    }
}

data class EasyWhen(val conds: List<EasyCond>, val any: Boolean = false) {
    fun toFormula(): String = when {
        conds.isEmpty() -> "참"
        conds.size == 1 -> conds[0].toFormula()
        else -> conds.joinToString(if (any) " 또는 " else " 그리고 ") { c ->
            c.toFormula().let { if (any && it.contains(" 그리고 ") || !any && it.contains(" 또는 ")) "($it)" else it }
        }
    }

    fun say(): String = if (conds.isEmpty()) "언제나" else conds.joinToString(if (any) " 또는 " else " 그리고 ") { it.say() }

    companion object {
        /** Rows from a formula of their shape, or null (it opens in 고급 보기 only). */
        fun read(src: String): EasyWhen? {
            if (src.isBlank() || src.trim() == "참") return EasyWhen(emptyList())
            val root = (Formula.parse(src) as? Parsed.Ok)?.formula?.root ?: return null
            val leaves = ArrayList<Node>()
            var kind: String? = null
            fun walk(n: Node): Boolean {
                if (n is Node.Bin && (n.op == "and" || n.op == "or")) {
                    if (kind != null && kind != n.op) return false
                    kind = n.op
                    return walk(n.a) && walk(n.b)
                }
                leaves += n
                return true
            }
            if (!walk(root)) return null
            val conds = leaves.map { leaf(it) ?: return null }
            return EasyWhen(conds, any = kind == "or")
        }

        private fun litText(n: Node): String? = (n as? Node.Lit)?.v?.takeIf { it is Value.Num || it is Value.Text }?.text

        private fun leaf(n: Node): EasyCond? = when {
            n is Node.Ref -> EasyCond(n.name, EasyCond.Op.YES)
            n is Node.Un && n.op == "not" && n.x is Node.Ref -> EasyCond(n.x.name, EasyCond.Op.NO)
            n is Node.Un && n.op == "not" -> when (val inner = leaf(n.x)) {
                null -> null
                else -> when (inner.op) {
                    EasyCond.Op.HAS -> inner.copy(op = EasyCond.Op.HAS_NOT)
                    EasyCond.Op.EMPTY -> inner.copy(op = EasyCond.Op.FILLED)
                    else -> null
                }
            }
            n is Node.Call && n.fn == Fn.CONTAINS && n.args[0] is Node.Ref ->
                litText(n.args[1])?.let { EasyCond((n.args[0] as Node.Ref).name, EasyCond.Op.HAS, it) }
            n is Node.Call && n.fn == Fn.EMPTY && n.args[0] is Node.Ref -> EasyCond((n.args[0] as Node.Ref).name, EasyCond.Op.EMPTY)
            n is Node.Bin && n.a is Node.Ref -> litText(n.b)?.let { v ->
                val op = when (n.op) {
                    "=" -> EasyCond.Op.IS; "!=" -> EasyCond.Op.IS_NOT; ">" -> EasyCond.Op.GT; "<" -> EasyCond.Op.LT
                    ">=" -> EasyCond.Op.GE; "<=" -> EasyCond.Op.LE; else -> null
                }
                op?.let { EasyCond(n.a.name, it, v) }
            }
            else -> null
        }
    }
}

/** 이/가 after a word, by its last letter's 받침 (a word ending in a number or Latin letter reads as 가). */
internal object Josa {
    fun iga(w: String): String {
        val c = w.lastOrNull() ?: return "가"
        if (c !in '가'..'힣') return "가"
        return if ((c - '가') % 28 != 0) "이" else "가"
    }
}
