package com.bamnat.core.formula

/**
 * 누락 점검 N7 · 계획 5장 쓰는 법 2 '블록' (숙련자용, 표를 쌓기 어려운 수식): a formula as blocks that fit into each other — 만약 [조건]
 * 이면 [값] 아니면 [값], [값] [=] [값], [블록] 그리고 [블록], 함수 [이름]([값]…). A block becomes the same text the 고급 보기 shows
 * ([text]), and any formula that reads opens as blocks ([read]), so 쉬운 보기 · 블록 · 직접 쓰기 are three ways to one value.
 * An empty slot is a [Block.Hole]; a formula with one is not finished ([Block.complete]).
 */
sealed interface Block {
    /** A value name (근무, 지출, …), main spelling. */
    data class Name(val name: String) : Block
    data class Num(val v: Double) : Block
    data class Text(val s: String) : Block
    data class Bool(val v: Boolean) : Block
    data object None : Block
    /** [op]: "and" "or" (그리고·또는), "=" "!=" ">" "<" ">=" "<=", "+" "-" "*" "/" "%" "&". */
    data class Op(val op: String, val a: Block, val b: Block) : Block
    data class Not(val x: Block) : Block
    data class Neg(val x: Block) : Block
    /** A function by its main (Korean) name. */
    data class Call(val fn: String, val args: List<Block>) : Block
    /** A slot still to fill. */
    data object Hole : Block

    /** Every slot filled. */
    val complete: Boolean get() = when (this) {
        Hole -> false
        is Op -> a.complete && b.complete
        is Not -> x.complete
        is Neg -> x.complete
        is Call -> args.all { it.complete }
        else -> true
    }

    /** The formula text (as 직접 쓰기 shows it); a [Hole] is "□" so the text says where. */
    fun text(): String = FormulaBlocks.write(this, 0)

    /** The words on the block itself, for its chip and for a screen reader. */
    fun label(): String = when (this) {
        is Name -> name
        is Num -> Formula.numText(v)
        is Text -> "“$s”"
        is Bool -> if (v) "참" else "거짓"
        None -> "없음"
        is Op -> FormulaBlocks.opWord(op)
        is Not -> "아님"
        is Neg -> "빼기"
        is Call -> fn
        Hole -> "빈칸"
    }
}

object FormulaBlocks {
    const val HOLE = "□"

    /** The comparison ops, their words in the block. */
    val COMPARE = linkedMapOf("=" to "같음", "!=" to "다름", ">" to "보다 큼", "<" to "보다 작음", ">=" to "이상", "<=" to "이하")
    val MATH = linkedMapOf("+" to "더하기", "-" to "빼기", "*" to "곱하기", "/" to "나누기", "%" to "나머지", "&" to "글 잇기")
    val JOIN = linkedMapOf("and" to "그리고", "or" to "또는")

    fun opWord(op: String): String = COMPARE[op] ?: MATH[op] ?: JOIN[op] ?: op

    /** The blocks of [src]; [Block.Hole] for an empty one, null when it does not read (고급 보기에서 고치기). */
    fun read(src: String): Block? {
        if (src.isBlank()) return Block.Hole
        val ok = Formula.parse(src) as? Parsed.Ok ?: return null
        return of(ok.formula.root)
    }

    internal fun of(n: Node): Block = when (n) {
        is Node.Lit -> when (val v = n.v) {
            is Value.Num -> Block.Num(v.v)
            is Value.Text -> Block.Text(v.v)
            is Value.Bool -> Block.Bool(v.v)
            else -> Block.None
        }
        is Node.Ref -> Block.Name(n.name)
        is Node.Un -> if (n.op == "not") Block.Not(of(n.x)) else Block.Neg(of(n.x))
        is Node.Bin -> Block.Op(n.op, of(n.a), of(n.b))
        is Node.Call -> Block.Call(n.fn.ko, n.args.map(::of))
    }

    /** A new block of each kind, for the 블록 넣기 list. */
    fun compare(): Block = Block.Op("=", Block.Hole, Block.Hole)
    fun join(op: String): Block = Block.Op(op, Block.Hole, Block.Hole)
    fun call(fn: String): Block {
        val f = Fn.of(fn) ?: return Block.Hole
        // 만약: one 조건·값 and the 나머지, the way a person starts it.
        val n = if (f == Fn.IF) 3 else f.min.coerceAtLeast(1).coerceAtMost(3)
        return Block.Call(f.ko, List(n) { Block.Hole })
    }

    /** The function's place in the list (min, max) so the editor can add or take away a value. */
    fun arity(fn: String): IntRange? = Fn.of(fn)?.let { it.min..it.max }

    private fun prec(b: Block): Int = when (b) {
        is Block.Op -> when (b.op) {
            "or" -> 1; "and" -> 2
            in COMPARE -> 4
            "+", "-", "&" -> 5
            else -> 6
        }
        is Block.Not -> 3
        is Block.Neg -> 7
        else -> 8
    }

    internal fun write(b: Block, outer: Int): String {
        val p = prec(b)
        val s = when (b) {
            is Block.Name -> b.name
            is Block.Num -> Formula.numText(b.v)
            is Block.Text -> if ('"' in b.s) "'" + b.s.replace("'", "") + "'" else "\"" + b.s + "\""
            is Block.Bool -> if (b.v) "참" else "거짓"
            Block.None -> "없음"
            Block.Hole -> HOLE
            is Block.Not -> "아님 " + write(b.x, p)
            is Block.Neg -> "-" + write(b.x, p)
            is Block.Call -> b.fn + "(" + b.args.joinToString(", ") { write(it, 0) } + ")"
            is Block.Op -> {
                val word = when (b.op) { "and" -> "그리고"; "or" -> "또는"; else -> b.op }
                // Left-leaning like the reader: the right side of the same level is in brackets (a - (b - c)), a comparison never chains.
                val left = write(b.a, if (p == 4) p + 1 else p)
                val right = write(b.b, p + 1)
                "$left $word $right"
            }
        }
        return if (p < outer) "($s)" else s
    }
}
