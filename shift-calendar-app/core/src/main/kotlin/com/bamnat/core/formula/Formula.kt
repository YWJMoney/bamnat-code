package com.bamnat.core.formula

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.pow

/**
 * [RUL-01] 1.36 881 수식 엔진 (사용자 2026-11-01 "수식을 넣어서 보다 뛰어난 상호작용, 조건부도 넣을 수 있게(다중 조건식도), 다만 편하게"):
 * a small, closed formula language that a recipe may carry as a *value* (결정 D106을 '값 + 제한된 수식'으로 넓힘).
 *
 * - Only the functions in [Fn] exist: no loop, no variable you can set, no address, no file, no clock of its own. What it reads comes
 *   from the [FormulaScope] the app hands it (the day's shift, memos, money …), so another person's formula can only *compute*.
 * - Limits: [MAX_LEN] letters, [MAX_NODES] parts, [MAX_DEPTH] nesting, [MAX_STEPS] steps a run, lists of [MAX_LIST], texts of
 *   [MAX_TEXT]. A formula past them is refused when read; a run past them fails, and the place that used it keeps its own value.
 * - Korean names first (만약, 그리고, 포함 …), English too (if, and, contains …); `꼬리표.포함("병원")` is `포함(꼬리표, "병원")`.
 * - The server reads the same language (server/company-worker/src/formula.js) and both hold to `docs/formula-cases.json`.
 */
object Formula {
    const val MAX_LEN = 600
    const val MAX_NODES = 200
    const val MAX_DEPTH = 16
    const val MAX_STEPS = 4000
    const val MAX_LIST = 60
    const val MAX_TEXT = 300

    /** The formula read once; [Parsed.Bad] says what is wrong and where (letters [FormulaError.from]..[FormulaError.to]). */
    fun parse(src: String): Parsed = try {
        Parsed.Ok(Compiled(src, Parser(src).parseAll()))
    } catch (e: FormulaError) {
        Parsed.Bad(e)
    }

    /** null when [src] reads, else the refusal's code (for the recipe catalog's checks). */
    fun problem(src: String): String? = (parse(src) as? Parsed.Bad)?.error?.code

    /** The value of [src] in [scope], or null when it does not read or fails while running. */
    fun eval(src: String, scope: FormulaScope): Value? = (parse(src) as? Parsed.Ok)?.formula?.run(scope)

    /** How a number is written as text, the same on the phone and the server: whole numbers bare, else up to 4 decimals. */
    fun numText(d: Double): String {
        if (d.isNaN() || d.isInfinite()) return "0"
        val r = roundHalfAway(d, 4)
        if (r == floor(r) && abs(r) < 1e15) return r.toLong().toString()
        var s = String.format(java.util.Locale.ROOT, "%.4f", r)
        s = s.trimEnd('0').trimEnd('.')
        return if (s == "-0") "0" else s
    }

    internal fun roundHalfAway(d: Double, digits: Int): Double {
        val m = 10.0.pow(digits)
        val v = floor(abs(d) * m + 0.5) / m
        return if (d < 0) -v else v
    }
}

sealed interface Parsed {
    data class Ok(val formula: Compiled) : Parsed
    data class Bad(val error: FormulaError) : Parsed
}

/** Why a formula did not read or run: a short [code], a Korean [message] and where, as letter positions in the source. */
class FormulaError(val code: String, val message0: String, val from: Int = 0, val to: Int = from) : Exception("$code @$from: $message0") {
    val messageKo: String get() = message0
}

/** A value a formula works with. [None] is "없음" (empty); a run never yields a Kotlin null inside. */
sealed interface Value {
    data class Num(val v: Double) : Value
    data class Text(val v: String) : Value
    data class Bool(val v: Boolean) : Value
    data class Many(val items: List<Value>) : Value
    data object None : Value

    val truthy: Boolean
        get() = when (this) {
            is Num -> v != 0.0
            is Text -> v.isNotEmpty()
            is Bool -> v
            is Many -> items.isNotEmpty()
            None -> false
        }

    /** As the formula writes it ("3", "야간", "참", "병원, 은행"). */
    val text: String
        get() = when (this) {
            is Num -> Formula.numText(v)
            is Text -> v
            is Bool -> if (v) "참" else "거짓"
            is Many -> items.joinToString(", ") { it.text }
            None -> ""
        }

    val number: Double?
        get() = when (this) {
            is Num -> v
            is Text -> v.replace(",", "").trim().removeSuffix("원").trim().let { if (NUMBER.matches(it)) it.toDouble() else null }
            is Bool -> if (v) 1.0 else 0.0
            None -> 0.0
            is Many -> null
        }

    companion object {
        private val NUMBER = Regex("^-?[0-9]+(\\.[0-9]+)?$")

        fun of(x: Any?): Value = when (x) {
            null -> None
            is Value -> x
            is Boolean -> Bool(x)
            is Number -> Num(x.toDouble())
            is String -> Text(x)
            is Collection<*> -> Many(x.map { of(it) })
            else -> Text(x.toString())
        }
    }
}

/** What a formula may read: names (근무, 지출 …) and the receiver's answers by their label (칸("시급")). */
fun interface FormulaScope {
    /** The value of the name (already its main spelling, see [FormulaNames]), or null when there is no such name here. */
    fun get(name: String): Value?

    /** The receiver's answer to the field labelled [label]; none by default. */
    fun input(label: String): Value? = null

    /** 1.39 [FX-03] 값 칸: what a work counted and kept under [name] (`값("물 잔")`); none by default. */
    fun value(name: String): Value? = null

    /** 1.39 [FX-13] 값 칸 [name] with its past (지난값·연속·값합계·지난시간); none by default. */
    fun series(name: String): ValueSeries? = null

    companion object {
        val EMPTY = FormulaScope { null }
        fun of(map: Map<String, Any?>, inputs: Map<String, Any?> = emptyMap()): FormulaScope = object : FormulaScope {
            override fun get(name: String): Value? = if (map.containsKey(name)) Value.of(map[name]) else null
            override fun input(label: String): Value? = if (inputs.containsKey(label)) Value.of(inputs[label]) else null
        }
    }
}

/** A formula read once, ready to run many times (each day cell of a month). */
class Compiled internal constructor(val source: String, internal val root: Node) {
    /** The names it reads (main spellings), for the 쉬운 보기 and "이 규칙이 보는 것". */
    val names: Set<String> by lazy { buildSet { collect(root, this) } }

    /** 1.39 [FX-01] The functions it calls (main Korean names), so 프로 문 can tell a formula that reads `값(…)`. */
    val calls: Set<String> by lazy { buildSet { calls(root, this) } }

    private fun calls(n: Node, out: MutableSet<String>) {
        when (n) {
            is Node.Call -> { out += n.fn.ko; n.args.forEach { calls(it, out) } }
            is Node.Un -> calls(n.x, out)
            is Node.Bin -> { calls(n.a, out); calls(n.b, out) }
            else -> Unit
        }
    }

    private fun collect(n: Node, out: MutableSet<String>) {
        when (n) {
            is Node.Ref -> out += n.name
            is Node.Call -> n.args.forEach { collect(it, out) }
            is Node.Un -> collect(n.x, out)
            is Node.Bin -> { collect(n.a, out); collect(n.b, out) }
            is Node.Lit -> Unit
        }
    }

    /** The value in [scope], or null when the run failed (a limit, dividing by 0, a name that is not here). */
    fun run(scope: FormulaScope): Value? = try {
        Eval(scope).eval(root, 0)
    } catch (e: FormulaError) {
        null
    }

    /** As [run] but says why it failed, for the editor's "지금 이 날에는" line. */
    fun runOrError(scope: FormulaScope): Pair<Value?, FormulaError?> = try {
        Eval(scope).eval(root, 0) to null
    } catch (e: FormulaError) {
        null to e
    }
}

internal sealed interface Node {
    val at: Int
    data class Lit(val v: Value, override val at: Int) : Node
    data class Ref(val name: String, override val at: Int, val end: Int) : Node
    data class Call(val fn: Fn, val args: List<Node>, override val at: Int, val end: Int) : Node
    data class Un(val op: String, val x: Node, override val at: Int) : Node
    data class Bin(val op: String, val a: Node, val b: Node, override val at: Int) : Node
}

// ---- reading -------------------------------------------------------------------------------------------------------------

private data class Tok(val kind: Char, val text: String, val at: Int, val end: Int, val num: Double = 0.0)
// kinds: n number, s string, i name, o operator, ( ) , . e(end)

private class Lexer(val src: String) {
    fun all(): List<Tok> {
        if (src.length > Formula.MAX_LEN) throw FormulaError("long", "수식이 너무 길어요(${Formula.MAX_LEN}자까지).", Formula.MAX_LEN, src.length)
        val out = ArrayList<Tok>()
        var i = 0
        while (i < src.length) {
            val c = src[i]
            when {
                c.isWhitespace() -> i++
                c in '0'..'9' -> {
                    val s = i
                    while (i < src.length && src[i] in '0'..'9') i++
                    if (i < src.length && src[i] == '.' && i + 1 < src.length && src[i + 1] in '0'..'9') {
                        i++
                        while (i < src.length && src[i] in '0'..'9') i++
                    }
                    val t = src.substring(s, i)
                    out += Tok('n', t, s, i, t.toDouble())
                }
                c == '"' || c == '“' || c == '”' || c == '\'' || c == '‘' || c == '’' -> {
                    val s = i
                    val close = when (c) { '“' -> '”'; '‘' -> '’'; else -> c }
                    i++
                    val b = StringBuilder()
                    while (i < src.length && src[i] != close && !(close == '”' && src[i] == '"') && !(close == '"' && src[i] == '”')) {
                        b.append(src[i]); i++
                    }
                    if (i >= src.length) throw FormulaError("quote", "따옴표가 닫히지 않았어요.", s, src.length)
                    i++
                    out += Tok('s', b.toString(), s, i)
                }
                isNameStart(c) -> {
                    val s = i
                    while (i < src.length && isNamePart(src[i])) i++
                    out += Tok('i', src.substring(s, i), s, i)
                }
                c == '(' || c == ')' || c == ',' || c == '.' -> { out += Tok(c, c.toString(), i, i + 1); i++ }
                else -> {
                    val two = if (i + 1 < src.length) src.substring(i, i + 2) else ""
                    val op = when (two) {
                        ">=", "<=", "!=", "<>", "==", "&&", "||", "≥", "≤" -> two
                        else -> when (c) {
                            '+', '-', '*', '/', '%', '>', '<', '=', '!', '&', '×', '÷', '≥', '≤', '≠' -> c.toString()
                            else -> throw FormulaError("char", "'$c'는 수식에 쓸 수 없는 글자예요.", i, i + 1)
                        }
                    }
                    out += Tok('o', op, i, i + op.length)
                    i += op.length
                }
            }
        }
        out += Tok('e', "", src.length, src.length)
        return out
    }

    private fun isNameStart(c: Char) = c.isLetter() || c == '_'
    private fun isNamePart(c: Char) = isNameStart(c) || c in '0'..'9'
}

private class Parser(val src: String) {
    private val toks = Lexer(src).all()
    private var p = 0
    private var nodes = 0

    private fun peek() = toks[p]
    private fun next() = toks[p++]
    private fun count(at: Int) {
        if (++nodes > Formula.MAX_NODES) throw FormulaError("big", "수식이 너무 복잡해요(${Formula.MAX_NODES}개 부분까지).", at, at)
    }

    private fun isWord(t: Tok, vararg w: String) = t.kind == 'i' && w.any { it.equals(t.text, ignoreCase = true) }
    private fun isOp(t: Tok, vararg o: String) = t.kind == 'o' && t.text in o

    fun parseAll(): Node {
        if (src.isBlank()) throw FormulaError("empty", "수식이 비어 있어요.", 0, 0)
        val n = or(0)
        val t = peek()
        if (t.kind != 'e') {
            val msg = if (t.kind == ')') "닫는 괄호가 하나 더 있어요." else "'${t.text}' 앞에서 수식이 끝났어요. 쉼표나 연산자가 빠졌나요?"
            throw FormulaError("extra", msg, t.at, t.end)
        }
        return n
    }

    private fun depth(d: Int, at: Int) {
        if (d > Formula.MAX_DEPTH) throw FormulaError("deep", "괄호나 함수가 너무 깊이 겹쳐 있어요(${Formula.MAX_DEPTH}겹까지).", at, at)
    }

    private fun or(d: Int): Node {
        var a = and(d)
        while (isWord(peek(), "또는", "or") || isOp(peek(), "||")) {
            val t = next(); count(t.at)
            a = Node.Bin("or", a, and(d), t.at)
        }
        return a
    }

    private fun and(d: Int): Node {
        var a = not(d)
        while (isWord(peek(), "그리고", "and") || isOp(peek(), "&&")) {
            val t = next(); count(t.at)
            a = Node.Bin("and", a, not(d), t.at)
        }
        return a
    }

    private fun not(d: Int): Node {
        val t = peek()
        if ((isWord(t, "아님", "not") && toks[p + 1].kind != '(') || isOp(t, "!")) {
            next(); count(t.at); depth(d + 1, t.at)
            return Node.Un("not", not(d + 1), t.at)
        }
        return cmp(d)
    }

    private fun cmp(d: Int): Node {
        val a = add(d)
        val t = peek()
        if (t.kind == 'o' && t.text in CMP) {
            next(); count(t.at)
            val op = when (t.text) { "==" -> "="; "<>", "≠" -> "!="; "≥" -> ">="; "≤" -> "<="; else -> t.text }
            val b = add(d)
            val u = peek()
            if (u.kind == 'o' && u.text in CMP) throw FormulaError("chain", "비교는 한 번에 하나만 돼요. '그리고'로 나눠 주세요.", u.at, u.end)
            return Node.Bin(op, a, b, t.at)
        }
        return a
    }

    private fun add(d: Int): Node {
        var a = mul(d)
        while (isOp(peek(), "+", "-", "&")) {
            val t = next(); count(t.at)
            a = Node.Bin(t.text, a, mul(d), t.at)
        }
        return a
    }

    private fun mul(d: Int): Node {
        var a = unary(d)
        while (isOp(peek(), "*", "/", "%", "×", "÷")) {
            val t = next(); count(t.at)
            val op = when (t.text) { "×" -> "*"; "÷" -> "/"; else -> t.text }
            a = Node.Bin(op, a, unary(d), t.at)
        }
        return a
    }

    private fun unary(d: Int): Node {
        val t = peek()
        if (isOp(t, "-")) {
            next(); count(t.at); depth(d + 1, t.at)
            return Node.Un("-", unary(d + 1), t.at)
        }
        return postfix(d)
    }

    private fun postfix(d: Int): Node {
        var x = primary(d)
        while (peek().kind == '.') {
            val dot = next()
            val nm = next()
            if (nm.kind != 'i') throw FormulaError("dot", "'.' 다음에는 함수 이름이 와야 해요(예: 꼬리표.포함(\"병원\")).", dot.at, nm.end)
            val fn = Fn.of(nm.text) ?: throw FormulaError("fn", "'${nm.text}'라는 함수는 없어요.", nm.at, nm.end)
            val args = mutableListOf(x)
            var end = nm.end
            if (peek().kind == '(') {
                val (more, e) = args(d + 1, nm)
                args += more; end = e
            }
            count(nm.at)
            x = call(fn, args, x.at, end, nm)
        }
        return x
    }

    private fun args(d: Int, name: Tok): Pair<List<Node>, Int> {
        depth(d, name.at)
        next() // (
        val out = ArrayList<Node>()
        if (peek().kind == ')') return out to next().end
        while (true) {
            out += or(d)
            val t = next()
            when (t.kind) {
                ',' -> continue
                ')' -> return out to t.end
                'e' -> throw FormulaError("paren", "'${name.text}(' 괄호가 닫히지 않았어요.", name.at, t.at)
                else -> throw FormulaError("comma", "'${t.text}' 앞에 쉼표가 빠졌어요.", t.at, t.end)
            }
        }
    }

    private fun call(fn: Fn, args: List<Node>, at: Int, end: Int, name: Tok): Node {
        if (args.size < fn.min || args.size > fn.max) {
            val want = if (fn.max == Int.MAX_VALUE) "${fn.min}개 이상" else if (fn.min == fn.max) "${fn.min}개" else "${fn.min}~${fn.max}개"
            throw FormulaError("args", "${fn.ko}에는 값이 $want 들어가요(지금 ${args.size}개).", name.at, end)
        }
        return Node.Call(fn, args, at, end)
    }

    private fun primary(d: Int): Node {
        val t = next()
        count(t.at)
        return when (t.kind) {
            'n' -> Node.Lit(Value.Num(t.num), t.at)
            's' -> {
                if (t.text.length > Formula.MAX_TEXT) throw FormulaError("long", "글이 너무 길어요(${Formula.MAX_TEXT}자까지).", t.at, t.end)
                Node.Lit(Value.Text(t.text), t.at)
            }
            '(' -> {
                depth(d + 1, t.at)
                val x = or(d + 1)
                val c = next()
                if (c.kind != ')') throw FormulaError("paren", "괄호가 닫히지 않았어요.", t.at, c.at)
                x
            }
            'i' -> {
                when {
                    isWord(t, "참", "true", "예") -> Node.Lit(Value.Bool(true), t.at)
                    isWord(t, "거짓", "false", "아니오") -> Node.Lit(Value.Bool(false), t.at)
                    isWord(t, "없음", "none", "null") -> Node.Lit(Value.None, t.at)
                    peek().kind == '(' -> {
                        val fn = Fn.of(t.text) ?: throw FormulaError("fn", "'${t.text}'라는 함수는 없어요." + (FormulaNames.near(t.text)?.let { " '$it'를 쓰려던 건가요?" } ?: ""), t.at, t.end)
                        val (a, end) = args(d + 1, t)
                        call(fn, a, t.at, end, t)
                    }
                    else -> {
                        val name = FormulaNames.main(t.text)
                            ?: throw FormulaError("name", "'${t.text}'${Josa.irang(t.text)}는 값은 없어요." + (FormulaNames.near(t.text)?.let { " '$it'${Josa.eulreul(it)} 쓰려던 건가요?" } ?: ""), t.at, t.end)
                        Node.Ref(name, t.at, t.end)
                    }
                }
            }
            'e' -> throw FormulaError("end", "수식이 덜 끝났어요.", t.at, t.at)
            else -> throw FormulaError("unexpected", "'${t.text}'가 여기 올 수 없어요.", t.at, t.end)
        }
    }

    companion object {
        val CMP = setOf("=", "==", "!=", "<>", ">", "<", ">=", "<=", "≥", "≤", "≠")
    }
}

// ---- running -------------------------------------------------------------------------------------------------------------

internal class Eval(val scope: FormulaScope) {
    private var steps = 0

    fun eval(n: Node, d: Int): Value {
        if (++steps > Formula.MAX_STEPS) throw FormulaError("steps", "계산이 너무 길어요.", n.at)
        return when (n) {
            is Node.Lit -> n.v
            is Node.Ref -> scope.get(n.name) ?: Value.None
            is Node.Un -> when (n.op) {
                "not" -> Value.Bool(!eval(n.x, d).truthy)
                else -> Value.Num(-num(eval(n.x, d), n))
            }
            is Node.Bin -> bin(n, d)
            is Node.Call -> call(n, d)
        }
    }

    private fun num(v: Value, n: Node): Double = v.number ?: throw FormulaError("type", "숫자가 와야 해요.", n.at)

    private fun limitText(s: String, n: Node): Value.Text =
        if (s.length > Formula.MAX_TEXT) throw FormulaError("long", "글이 너무 길어졌어요.", n.at) else Value.Text(s)

    private fun bin(n: Node.Bin, d: Int): Value {
        when (n.op) {
            "and" -> return Value.Bool(eval(n.a, d).truthy && eval(n.b, d).truthy)
            "or" -> return Value.Bool(eval(n.a, d).truthy || eval(n.b, d).truthy)
        }
        val a = eval(n.a, d)
        val b = eval(n.b, d)
        return when (n.op) {
            "&" -> limitText(a.text + b.text, n)
            "+" -> if ((a is Value.Text && a.number == null) || (b is Value.Text && b.number == null)) limitText(a.text + b.text, n)
            else Value.Num(num(a, n) + num(b, n))
            "-" -> Value.Num(num(a, n) - num(b, n))
            "*" -> Value.Num(num(a, n) * num(b, n))
            "/" -> {
                val y = num(b, n)
                if (y == 0.0) throw FormulaError("div0", "0으로 나눌 수 없어요.", n.at)
                Value.Num(num(a, n) / y)
            }
            "%" -> {
                val y = num(b, n)
                if (y == 0.0) throw FormulaError("div0", "0으로 나눌 수 없어요.", n.at)
                Value.Num(num(a, n).mod(y))
            }
            "=" -> Value.Bool(same(a, b))
            "!=" -> Value.Bool(!same(a, b))
            else -> {
                val c = order(a, b)
                Value.Bool(
                    when (n.op) {
                        ">" -> c > 0
                        "<" -> c < 0
                        ">=" -> c >= 0
                        else -> c <= 0
                    },
                )
            }
        }
    }

    fun call(n: Node.Call, d: Int): Value {
        val fn = n.fn
        if (fn.lazy) return lazy(n, d)
        val a = n.args.map { eval(it, d + 1) }
        return fn.apply(this, a, n)
    }

    private fun lazy(n: Node.Call, d: Int): Value {
        val args = n.args
        return when (n.fn) {
            Fn.IF -> {
                var i = 0
                while (i + 1 < args.size) {
                    if (eval(args[i], d + 1).truthy) return eval(args[i + 1], d + 1)
                    i += 2
                }
                if (i < args.size) eval(args[i], d + 1) else Value.None
            }
            Fn.SWITCH -> {
                val x = eval(args[0], d + 1)
                var i = 1
                while (i + 1 < args.size) {
                    if (same(x, eval(args[i], d + 1))) return eval(args[i + 1], d + 1)
                    i += 2
                }
                if (i < args.size) eval(args[i], d + 1) else Value.None
            }
            Fn.AND -> Value.Bool(args.all { eval(it, d + 1).truthy })
            Fn.OR -> Value.Bool(args.any { eval(it, d + 1).truthy })
            Fn.IFNONE -> eval(args[0], d + 1).let { if (isEmpty(it)) eval(args[1], d + 1) else it }
            Fn.INPUT -> {
                val label = eval(args[0], d + 1).text.trim()
                scope.input(label) ?: (if (args.size > 1) eval(args[1], d + 1) else Value.None)
            }
            Fn.VALUE -> {
                val name = eval(args[0], d + 1).text.trim()
                scope.value(name) ?: (if (args.size > 1) eval(args[1], d + 1) else Value.None)
            }
            // 1.39 [FX-13] 값 칸의 지난 기록: none when the phone keeps no such 값 칸 (the server, an old work).
            Fn.PAST, Fn.STREAK, Fn.TOTAL, Fn.MEAN, Fn.SINCE -> {
                val s = scope.series(eval(args[0], d + 1).text.trim()) ?: return Value.None
                val k = if (args.size > 1) (eval(args[1], d + 1) as? Value.Num)?.v?.toInt() else null
                when (n.fn) {
                    Fn.PAST -> s.back((k ?: 1).coerceIn(0, ValueBook.PAST))?.let { Value.Num(it) } ?: Value.None
                    Fn.STREAK -> Value.Num(s.streak().toDouble())
                    Fn.TOTAL -> Value.Num(s.total(k ?: 7))
                    Fn.MEAN -> (k ?: 7).coerceIn(1, ValueBook.PAST + 1).let { m -> Value.Num(s.total(m) / m) }
                    else -> s.hoursSince()?.let { Value.Num(it) } ?: Value.None
                }
            }
            else -> Value.None
        }
    }

    fun step(k: Int, n: Node) {
        steps += k
        if (steps > Formula.MAX_STEPS) throw FormulaError("steps", "계산이 너무 길어요.", n.at)
    }

    fun numOf(v: Value, n: Node) = num(v, n)
    fun textOf(s: String, n: Node) = limitText(s, n)

    companion object {
        fun isEmpty(v: Value): Boolean = when (v) {
            Value.None -> true
            is Value.Text -> v.v.isEmpty()
            is Value.Many -> v.items.isEmpty()
            else -> false
        }

        fun same(a: Value, b: Value): Boolean {
            if (a is Value.Many || b is Value.Many) return a.text == b.text
            if (a is Value.None || b is Value.None) return isEmpty(a) && isEmpty(b)
            if (a is Value.Num && b is Value.Num) return a.v == b.v
            if (a is Value.Bool && b is Value.Bool) return a.v == b.v
            val x = a.number
            val y = b.number
            if ((a is Value.Num || b is Value.Num) && x != null && y != null) return x == y
            return a.text == b.text
        }

        fun order(a: Value, b: Value): Int {
            val x = a.number
            val y = b.number
            if (x != null && y != null) return x.compareTo(y)
            return a.text.compareTo(b.text)
        }
    }
}

/**
 * The functions there are: [ko] the main name, [en] the other, [min]..[max] values, [lazy] when not all values are worked out
 * (만약 runs only the side it picks). [help] and [example] are for the editor's list.
 */
internal enum class Fn(val ko: String, val en: String, val min: Int, val max: Int, val lazy: Boolean = false, val help: String, val example: String) {
    IF("만약", "if", 2, 41, true, "조건이 참이면 앞 값, 아니면 다음 조건… 맨 끝 하나는 '나머지'", "만약(지출 > 50000, \"주황\", 근무 = \"야간\", \"남색\", \"없음\")"),
    SWITCH("고르기", "switch", 3, 41, true, "값이 무엇과 같은지에 따라 고르기, 맨 끝 하나는 '나머지'", "고르기(요일, \"토\", \"파랑\", \"일\", \"빨강\", \"검정\")"),
    AND("모두", "all", 1, 20, true, "모두 참이면 참", "모두(주말, 쉬는날)"),
    OR("하나라도", "any", 1, 20, true, "하나라도 참이면 참", "하나라도(공휴일, 주말)"),
    NOT("아님", "not", 1, 1, help = "참과 거짓을 뒤집기", example = "아님(쉬는날)"),
    IFNONE("없으면", "ifEmpty", 2, 2, true, "앞 값이 비었으면 뒤 값", "없으면(근무, \"쉬는 날\")"),
    INPUT("칸", "input", 1, 2, true, "받는 사람이 채운 칸의 값(이름으로)", "칸(\"기준 금액\", 50000)"),
    /** 1.39 [FX-03] 값 칸 (프로 기능 장치): what the work counted, by its name; the second value when it has none yet. */
    VALUE("값", "value", 1, 2, true, "작품이 세어 둔 값(값 칸 이름으로)", "값(\"물 잔\", 0)"),
    /** 1.39 [FX-13] 값 칸의 기록 (프로 기능 장치). */
    PAST("지난값", "pastValue", 1, 2, true, "값 칸의 지난 기간 값(1: 어제·지난주·지난달)", "지난값(\"물 잔\", 1)"),
    STREAK("연속", "valueStreak", 1, 1, true, "값 칸이 목표를 채운 기간이 몇 번 이어졌나", "연속(\"물 잔\")"),
    TOTAL("값합계", "valueSum", 1, 2, true, "값 칸의 최근 몇 기간 합(지금 포함, 기본 7)", "값합계(\"배달\", 7)"),
    MEAN("값평균", "valueAvg", 1, 2, true, "값 칸의 최근 몇 기간 평균(기본 7)", "값평균(\"배달\", 7)"),
    SINCE("지난시간", "hoursSince", 1, 1, true, "시각 값 칸에 적은 때부터 몇 시간 지났나", "지난시간(\"약\")"),
    CONTAINS("포함", "contains", 2, 2, help = "글이나 목록에 들어 있나", example = "꼬리표.포함(\"병원\")"),
    STARTS("시작", "startsWith", 2, 2, help = "글이 이것으로 시작하나", example = "근무.시작(\"야\")"),
    ENDS("끝", "endsWith", 2, 2, help = "글이 이것으로 끝나나", example = "메모.끝(\"!\")"),
    LEN("길이", "len", 1, 1, help = "글자 수나 목록 개수", example = "꼬리표.길이()"),
    COUNT("개수", "count", 1, 2, help = "목록에서 개수(두 번째 값과 같은 것만 셀 수도)", example = "개수(꼬리표, \"병원\")"),
    SUM("합", "sum", 1, 20, help = "더하기(목록도)", example = "합(지출, 10000)"),
    AVG("평균", "avg", 1, 20, help = "평균", example = "평균(지출, 수입)"),
    MAX("최대", "max", 1, 20, help = "가장 큰 값", example = "최대(근무시간, 8)"),
    MIN("최소", "min", 1, 20, help = "가장 작은 값", example = "최소(지출, 100000)"),
    ROUND("반올림", "round", 1, 2, help = "반올림(자릿수를 줄 수도)", example = "반올림(근무시간, 1)"),
    FLOOR("내림", "floor", 1, 1, help = "소수점 버리기", example = "내림(근무시간)"),
    CEIL("올림", "ceil", 1, 1, help = "소수점 올리기", example = "올림(근무시간)"),
    ABS("절댓값", "abs", 1, 1, help = "음수를 양수로", example = "절댓값(수입 - 지출)"),
    BETWEEN("사이", "between", 3, 3, help = "앞 값이 두 값 사이(같아도)인가", example = "사이(근무시간, 8, 12)"),
    TEXT("글자", "text", 1, 1, help = "값을 글로", example = "글자(근무시간) & \"시간\""),
    NUM("숫자", "num", 1, 1, help = "글을 숫자로(안 되면 0)", example = "숫자(\"12,000\")"),
    WON("원", "won", 1, 1, help = "금액 쓰기(쉼표와 원)", example = "원(지출)"),
    EMPTY("비었나", "isEmpty", 1, 1, help = "비어 있으면 참", example = "비었나(메모)"),
    LEFT("앞", "left", 2, 2, help = "글 앞 몇 글자", example = "앞(근무, 1)"),
    REPLACE("바꾸기", "replace", 3, 3, help = "글 속 낱말 바꾸기", example = "바꾸기(메모, \"#\", \"\")"),
    JOIN("잇기", "join", 1, 2, help = "목록을 글로 잇기(사이 글을 줄 수도)", example = "잇기(꼬리표, \" · \")"),
    LIST("목록", "list", 0, 20, help = "값 여러 개를 목록으로", example = "목록(\"병원\", \"은행\")"),
    ANYOF("중하나", "oneOf", 2, 20, help = "앞 값이 뒤 값 중 하나와 같나", example = "중하나(근무, \"야간\", \"당직\")"),
    ;

    fun apply(e: Eval, a: List<Value>, n: Node): Value = when (this) {
        NOT -> Value.Bool(!a[0].truthy)
        CONTAINS -> {
            val x = a[0]
            Value.Bool(
                if (x is Value.Many) x.items.any { Eval.same(it, a[1]) }
                else a[1].text.isNotEmpty() && x.text.contains(a[1].text),
            )
        }
        STARTS -> Value.Bool(a[1].text.isNotEmpty() && a[0].text.startsWith(a[1].text))
        ENDS -> Value.Bool(a[1].text.isNotEmpty() && a[0].text.endsWith(a[1].text))
        LEN -> Value.Num(when (val x = a[0]) { is Value.Many -> x.items.size; Value.None -> 0; else -> x.text.length }.toDouble())
        COUNT -> {
            val items = flat(a.take(1))
            Value.Num((if (a.size > 1) items.count { Eval.same(it, a[1]) } else items.count { !Eval.isEmpty(it) }).toDouble())
        }
        SUM -> Value.Num(nums(e, a, n).sum())
        AVG -> nums(e, a, n).let { if (it.isEmpty()) Value.Num(0.0) else Value.Num(it.sum() / it.size) }
        MAX -> nums(e, a, n).let { if (it.isEmpty()) Value.None else Value.Num(it.max()) }
        MIN -> nums(e, a, n).let { if (it.isEmpty()) Value.None else Value.Num(it.min()) }
        ROUND -> {
            val dg = if (a.size > 1) e.numOf(a[1], n).toInt().coerceIn(0, 6) else 0
            Value.Num(Formula.roundHalfAway(e.numOf(a[0], n), dg))
        }
        FLOOR -> Value.Num(floor(e.numOf(a[0], n)))
        CEIL -> Value.Num(ceil(e.numOf(a[0], n)))
        ABS -> Value.Num(abs(e.numOf(a[0], n)))
        BETWEEN -> {
            val x = e.numOf(a[0], n)
            val lo = e.numOf(a[1], n)
            val hi = e.numOf(a[2], n)
            Value.Bool(x >= minOf(lo, hi) && x <= maxOf(lo, hi))
        }
        TEXT -> e.textOf(a[0].text, n)
        NUM -> Value.Num(a[0].number ?: 0.0)
        WON -> {
            val v = Formula.roundHalfAway(e.numOf(a[0], n), 0).toLong()
            val s = kotlin.math.abs(v).toString().reversed().chunked(3).joinToString(",").reversed()
            Value.Text((if (v < 0) "-" else "") + s + "원")
        }
        EMPTY -> Value.Bool(Eval.isEmpty(a[0]))
        LEFT -> {
            val k = e.numOf(a[1], n).toInt().coerceAtLeast(0)
            val cps = a[0].text.codePoints().toArray()
            Value.Text(String(cps, 0, minOf(k, cps.size)))
        }
        REPLACE -> if (a[1].text.isEmpty()) a[0].let { Value.Text(it.text) } else e.textOf(a[0].text.replace(a[1].text, a[2].text), n)
        JOIN -> e.textOf(flat(a.take(1)).joinToString(if (a.size > 1) a[1].text else ", ") { it.text }, n)
        LIST -> {
            if (a.size > Formula.MAX_LIST) throw FormulaError("list", "목록이 너무 길어요.", n.at)
            Value.Many(a)
        }
        ANYOF -> Value.Bool(a.drop(1).any { Eval.same(a[0], it) })
        else -> Value.None
    }.also { if (it is Value.Many) e.step(it.items.size, n) }

    companion object {
        private val BY = entries.flatMap { listOf(it.ko to it, it.en.lowercase() to it) }.toMap()
        fun of(name: String): Fn? = BY[name] ?: BY[name.lowercase()]

        private fun flat(a: List<Value>): List<Value> = a.flatMap { if (it is Value.Many) it.items else listOf(it) }
        private fun nums(e: Eval, a: List<Value>, n: Node): List<Double> {
            val f = flat(a)
            e.step(f.size, n)
            return f.filter { it !is Value.None }.map { e.numOf(it, n) }
        }
    }
}
