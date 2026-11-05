package com.bamnat.core.imports

import java.io.ByteArrayOutputStream
import java.util.zip.Inflater

/**
 * [READ-32] 1.38 (사용자 "PDF 같은 것 등 어떠한 형식으로든 … 완벽하게 인식"): the words of a PDF that has its text in it (saved from
 * Excel, 한글, a 근태 system or a hospital's 듀티 program), with where each stands on the page, read from the file itself — not
 * from a picture of it. A word is the exact text, so a roster saved as PDF reads like the spreadsheet it was made from; only
 * a scanned PDF (pictures of paper, no text) is read by OCR from its rendered pages (app `imports/AnyFile`).
 *
 * A small reader of what such files use: objects and object streams, Flate-compressed content, the text operators
 * (BT·Tf·Td·TD·Tm·T*·Tj·TJ·'·"·Tc·Tw·Tz·TL, cm with q/Q), and fonts by their ToUnicode map and widths (simple fonts and
 * Identity-H CID fonts). Encrypted files are not read. Words come in the page's points × [SCALE], top-left origin, as OCR
 * words of a 288 dpi picture would, so [DutyRoster] places them the same way.
 */
object PdfText {
    const val SCALE = 4.0
    private const val MAX_PAGES = 20
    private const val MAX_STREAM = 16 * 1024 * 1024

    class PdfException(message: String) : Exception(message)

    /** Each page's words, or null when the file has no text to read (scanned) or cannot be read here. */
    /**
     * [READ-32] A page's exact words as the rows and cells of the table they stand in, for the spreadsheet readers
     * ([RosterSheet], [TeamSheet], [StaffSheets]). Columns are where the words' middles gather (a program's PDF has columns
     * of different widths — 1~9 narrower than 10~31 — which a photo's evenly spaced columns never assume); lines are words
     * on one baseline. A long title stands in a column of its own and reads as a row of one cell.
     */
    fun table(words: List<RosterWord>): List<List<String>> {
        if (words.isEmpty()) return emptyList()
        val h = words.map { it.height }.sorted()[words.size / 2].toDouble()
        val lines = mutableListOf<MutableList<RosterWord>>()
        for (w in words.sortedBy { it.centerY }) {
            val line = lines.lastOrNull()
            if (line != null && kotlin.math.abs(line.map { it.centerY }.average() - w.centerY) <= h * 0.5) line += w else lines += mutableListOf(w)
        }
        val xs = words.map { it.centerX.toDouble() }.sorted()
        val anchors = mutableListOf<MutableList<Double>>()
        for (x in xs) { val a = anchors.lastOrNull(); if (a != null && x - a.last() <= h * 0.6) a += x else anchors += mutableListOf(x) }
        val mids = anchors.map { it.average() }
        return lines.map { line ->
            val cells = sortedMapOf<Int, String>()
            for (w in line.sortedBy { it.left }) {
                val c = mids.indices.minBy { kotlin.math.abs(mids[it] - w.centerX) }
                cells[c] = cells[c]?.let { "$it ${w.text}" } ?: w.text
            }
            List((cells.keys.maxOrNull() ?: -1) + 1) { cells[it].orEmpty() }
        }.filter { r -> r.any { it.isNotEmpty() } }
    }

    /** [READ-32] Each page of a PDF with text as a sheet, for [Sheets.book]'s readers; null for a scanned PDF. */
    fun sheets(bytes: ByteArray): List<Sheets.Sheet>? = pages(bytes)?.mapIndexed { i, w -> Sheets.Sheet("${i + 1}쪽", table(w)) }

    fun pages(bytes: ByteArray): List<List<RosterWord>>? = runCatching { Reader(bytes).pages() }.getOrNull()?.takeIf { p -> p.any { it.isNotEmpty() } }

    // ------------------------------------------------------------------ objects

    internal class Ref(val num: Int)
    internal class Name(val v: String) { override fun equals(other: Any?) = other is Name && other.v == v; override fun hashCode() = v.hashCode(); override fun toString() = "/$v" }
    internal class PStr(val bytes: ByteArray)
    internal class Op(val v: String)
    internal class PStream(val dict: Map<String, Any?>, val raw: ByteArray)

    /** A tokenizer and object parser over the file's bytes as Latin-1 characters (one char a byte). */
    internal class Lexer(val s: String, var i: Int = 0) {
        private fun ws(c: Char) = c == ' ' || c == '\n' || c == '\r' || c == '\t' || c == '\u000C' || c == '\u0000'
        private fun delim(c: Char) = c in "()<>[]{}/%"

        fun skip() {
            while (i < s.length) {
                val c = s[i]
                if (ws(c)) i++ else if (c == '%') { while (i < s.length && s[i] != '\n' && s[i] != '\r') i++ } else break
            }
        }

        fun next(): Any? {
            skip()
            if (i >= s.length) return null
            val c = s[i]
            when {
                c == '/' -> { val st = ++i; while (i < s.length && !ws(s[i]) && !delim(s[i])) i++; return Name(hexNames(s.substring(st, i))) }
                c == '(' -> return PStr(literal())
                c == '<' && s.getOrNull(i + 1) == '<' -> { i += 2; return dict() }
                c == '<' -> { val st = ++i; while (i < s.length && s[i] != '>') i++; val h = s.substring(st, i).filter { it.isLetterOrDigit() }; i++; return PStr(hex(h)) }
                c == '[' -> { i++; val out = mutableListOf<Any?>(); while (true) { skip(); if (i >= s.length) break; if (s[i] == ']') { i++; break }; out += value() }; return out }
                c == ']' || c == '>' -> { i++; return Op(c.toString()) }
                c == '{' || c == '}' -> { i++; return Op(c.toString()) }
                else -> {
                    val st = i
                    while (i < s.length && !ws(s[i]) && !delim(s[i])) i++
                    if (i == st) { i++; return Op(c.toString()) }
                    val t = s.substring(st, i)
                    t.toDoubleOrNull()?.let { return it }
                    return when (t) { "true" -> true; "false" -> false; "null" -> null; else -> Op(t) }
                }
            }
        }

        /** A value, with `n g R` read as a reference. */
        fun value(): Any? {
            val v = next()
            if (v is Double) {
                val save = i
                val g = next()
                if (g is Double) {
                    val save2 = i
                    val r = next()
                    if (r is Op && r.v == "R") return Ref(v.toInt())
                    i = save2
                }
                i = save
            }
            return v
        }

        fun dict(): Map<String, Any?> {
            val out = HashMap<String, Any?>()
            while (true) {
                skip()
                if (i >= s.length) break
                if (s.startsWith(">>", i)) { i += 2; break }
                val k = next() as? Name ?: continue
                out[k.v] = value()
            }
            return out
        }

        private fun literal(): ByteArray {
            i++
            val out = ByteArrayOutputStream()
            var depth = 1
            while (i < s.length) {
                val c = s[i++]
                when (c) {
                    '\\' -> {
                        val e = s.getOrNull(i++) ?: break
                        when (e) {
                            'n' -> out.write(10); 'r' -> out.write(13); 't' -> out.write(9); 'b' -> out.write(8); 'f' -> out.write(12)
                            '\r' -> if (s.getOrNull(i) == '\n') i++
                            '\n' -> {}
                            in '0'..'7' -> { var v = e - '0'; repeat(2) { if (s.getOrNull(i) in '0'..'7') v = v * 8 + (s[i++] - '0') }; out.write(v and 0xFF) }
                            else -> out.write(e.code and 0xFF)
                        }
                    }
                    '(' -> { depth++; out.write(c.code) }
                    ')' -> { depth--; if (depth == 0) return out.toByteArray(); out.write(c.code) }
                    else -> out.write(c.code and 0xFF)
                }
            }
            return out.toByteArray()
        }

        private fun hex(h: String): ByteArray {
            val t = if (h.length % 2 == 1) h + "0" else h
            return ByteArray(t.length / 2) { k -> t.substring(k * 2, k * 2 + 2).toInt(16).toByte() }
        }

        private fun hexNames(n: String) = if ('#' !in n) n else Regex("""#([0-9A-Fa-f]{2})""").replace(n) { it.groupValues[1].toInt(16).toChar().toString() }
    }

    private class Reader(val bytes: ByteArray) {
        val text = String(bytes, Charsets.ISO_8859_1)
        val objects = HashMap<Int, Any?>()

        init {
            if (text.contains("/Encrypt")) throw PdfException("암호가 걸린 PDF")
            for (m in Regex("""(?<![0-9])(\d+)\s+(\d+)\s+obj\b""").findAll(text)) {
                val num = m.groupValues[1].toInt()
                val lx = Lexer(text, m.range.last + 1)
                val v = lx.value()
                lx.skip()
                if (v is Map<*, *> && text.startsWith("stream", lx.i)) {
                    var st = lx.i + 6
                    if (text.getOrNull(st) == '\r') st++
                    if (text.getOrNull(st) == '\n') st++
                    @Suppress("UNCHECKED_CAST") val d = v as Map<String, Any?>
                    val len = (d["Length"] as? Double)?.toInt()
                    val end = if (len != null && st + len <= bytes.size && text.startsWith("endstream", skipWs(st + len))) st + len
                    else text.indexOf("endstream", st).let { e -> if (e < 0) bytes.size else e }.let { e -> var k = e; while (k > st && (text[k - 1] == '\n' || text[k - 1] == '\r')) k--; k }
                    objects[num] = PStream(d, bytes.copyOfRange(st, end))
                } else objects[num] = v
            }
            // Objects packed in object streams (PDF 1.5).
            for (o in objects.values.toList()) {
                val st = o as? PStream ?: continue
                if ((st.dict["Type"] as? Name)?.v != "ObjStm") continue
                val data = String(decode(st) ?: continue, Charsets.ISO_8859_1)
                val n = (st.dict["N"] as? Double)?.toInt() ?: continue
                val first = (st.dict["First"] as? Double)?.toInt() ?: continue
                val head = Lexer(data)
                val pairs = (0 until n).mapNotNull { (head.next() as? Double)?.toInt()?.let { num -> (head.next() as? Double)?.toInt()?.let { num to it } } }
                for ((num, off) in pairs) if (!objects.containsKey(num)) objects[num] = Lexer(data, first + off).value()
            }
        }

        private fun skipWs(k: Int): Int { var j = k; while (j < text.length && text[j].isWhitespace()) j++; return j }

        fun get(v: Any?): Any? { var x = v; var guard = 0; while (x is Ref && guard++ < 20) x = objects[x.num]; return x }
        @Suppress("UNCHECKED_CAST") fun dict(v: Any?): Map<String, Any?>? = when (val x = get(v)) { is Map<*, *> -> x as Map<String, Any?>; is PStream -> x.dict; else -> null }
        fun num(v: Any?): Double? = get(v) as? Double

        fun decode(st: PStream): ByteArray? {
            val f = get(st.dict["Filter"])
            val filters = when (f) { is Name -> listOf(f.v); is List<*> -> f.mapNotNull { (get(it) as? Name)?.v }; else -> emptyList() }
            var data = st.raw
            for (name in filters) {
                data = when (name) {
                    "FlateDecode", "Fl" -> inflate(data) ?: return null
                    "ASCIIHexDecode", "AHx" -> String(data, Charsets.ISO_8859_1).substringBefore('>').filter { it.isLetterOrDigit() }.let { h -> ByteArray(h.length / 2) { k -> h.substring(k * 2, k * 2 + 2).toInt(16).toByte() } }
                    "ASCII85Decode", "A85" -> ascii85(data)
                    else -> return null
                }
            }
            return data
        }

        private fun ascii85(d: ByteArray): ByteArray {
            val s = String(d, Charsets.ISO_8859_1).substringBefore("~>").removePrefix("<~").filter { !it.isWhitespace() }
            val out = ByteArrayOutputStream()
            var k = 0
            while (k < s.length) {
                if (s[k] == 'z') { repeat(4) { out.write(0) }; k++; continue }
                val group = s.substring(k, minOf(k + 5, s.length))
                k += group.length
                val padded = group.padEnd(5, 'u')
                var v = 0L
                for (c in padded) v = v * 85 + (c.code - 33)
                val bytes = byteArrayOf((v shr 24).toByte(), (v shr 16).toByte(), (v shr 8).toByte(), v.toByte())
                out.write(bytes, 0, group.length - 1)
            }
            return out.toByteArray()
        }

        private fun inflate(d: ByteArray): ByteArray? = runCatching {
            val inf = Inflater()
            inf.setInput(d)
            val out = ByteArrayOutputStream()
            val buf = ByteArray(64 * 1024)
            while (!inf.finished()) {
                val n = inf.inflate(buf)
                if (n == 0 && (inf.needsInput() || inf.needsDictionary())) break
                out.write(buf, 0, n)
                if (out.size() > MAX_STREAM) break
            }
            inf.end()
            out.toByteArray()
        }.getOrNull()

        fun pages(): List<List<RosterWord>> {
            val root = dict(Regex("""/Root\s+(\d+)\s+\d+\s+R""").findAll(text).lastOrNull()?.groupValues?.get(1)?.toInt()?.let(::Ref))
                ?: objects.values.firstNotNullOfOrNull { o -> (o as? Map<*, *>)?.takeIf { (it["Type"] as? Name)?.v == "Catalog" } }?.let { @Suppress("UNCHECKED_CAST") (it as Map<String, Any?>) }
            val list = mutableListOf<Map<String, Any?>>()
            fun walk(node: Any?, inherited: Map<String, Any?>, depth: Int) {
                val d = dict(node) ?: return
                if (depth > 30 || list.size >= MAX_PAGES) return
                val inh = inherited + listOf("Resources", "MediaBox").mapNotNull { k -> d[k]?.let { k to it } }
                if ((d["Type"] as? Name)?.v == "Pages" || d.containsKey("Kids")) (get(d["Kids"]) as? List<*>)?.forEach { walk(it, inh, depth + 1) }
                else list += inh + d
            }
            if (root != null) walk(root["Pages"], emptyMap(), 0)
            if (list.isEmpty()) objects.keys.sorted().mapNotNull { k -> dict(Ref(k))?.takeIf { (it["Type"] as? Name)?.v == "Page" } }.take(MAX_PAGES).forEach(list::add)
            return list.map(::page)
        }

        private fun page(p: Map<String, Any?>): List<RosterWord> {
            val box = (get(p["MediaBox"]) as? List<*>)?.mapNotNull { num(it) }
            val height = if (box != null && box.size == 4) box[3] - box[1] else 842.0
            val bottom = box?.getOrNull(1) ?: 0.0
            val left = box?.getOrNull(0) ?: 0.0
            val res = dict(p["Resources"]).orEmpty()
            val fonts = dict(res["Font"]).orEmpty().mapValues { (_, v) -> Font.of(this, dict(v).orEmpty()) }
            val contents = when (val c = get(p["Contents"])) { is PStream -> listOf(c); is List<*> -> c.mapNotNull { get(it) as? PStream }; else -> emptyList() }
            val data = contents.mapNotNull { decode(it) }.joinToString(" ") { String(it, Charsets.ISO_8859_1) }
            val glyphs = Interpreter(fonts, this, res).run(data)
            return words(glyphs.map { g -> g.copy(x0 = g.x0 - left, x1 = g.x1 - left, y = height - (g.y - bottom)) })
        }
    }

    // ------------------------------------------------------------------ fonts

    private class Font(val twoByte: Boolean, val map: Map<Int, String>, val widths: Map<Int, Double>, val defaultWidth: Double, val latin: Boolean) {
        fun codes(b: ByteArray): List<Int> = if (twoByte) (0 until b.size / 2).map { ((b[it * 2].toInt() and 0xFF) shl 8) or (b[it * 2 + 1].toInt() and 0xFF) } else b.map { it.toInt() and 0xFF }
        fun text(code: Int): String? = map[code] ?: if (latin || !twoByte) code.toChar().toString().takeIf { code in 32..126 || code in 0xA0..0xFF } else null
        fun width(code: Int): Double = (widths[code] ?: defaultWidth) / 1000.0

        companion object {
            fun of(r: Reader, d: Map<String, Any?>): Font {
                val sub = (r.get(d["Subtype"]) as? Name)?.v
                val type0 = sub == "Type0"
                val map = (r.get(d["ToUnicode"]) as? PStream)?.let { r.decode(it) }?.let { cmap(String(it, Charsets.ISO_8859_1)) }.orEmpty()
                val widths = HashMap<Int, Double>()
                var dw = 500.0
                if (type0) {
                    val desc = r.dict((r.get(d["DescendantFonts"]) as? List<*>)?.firstOrNull()).orEmpty()
                    dw = r.num(desc["DW"]) ?: 1000.0
                    val w = r.get(desc["W"]) as? List<*> ?: emptyList<Any?>()
                    var k = 0
                    while (k < w.size) {
                        val c = (r.num(w[k]) ?: break).toInt()
                        val nx = r.get(w.getOrNull(k + 1))
                        if (nx is List<*>) { nx.forEachIndexed { j, v -> r.num(v)?.let { widths[c + j] = it } }; k += 2 }
                        else { val c2 = (r.num(nx) ?: break).toInt(); val v = r.num(w.getOrNull(k + 2)) ?: dw; for (x in c..minOf(c2, c + 65535)) widths[x] = v; k += 3 }
                    }
                } else {
                    val first = (r.num(d["FirstChar"]) ?: 0.0).toInt()
                    (r.get(d["Widths"]) as? List<*>)?.forEachIndexed { j, v -> r.num(v)?.let { widths[first + j] = it } }
                    val base = (r.get(d["BaseFont"]) as? Name)?.v.orEmpty()
                    dw = if (base.contains("Courier")) 600.0 else 500.0
                }
                val enc = (r.get(d["Encoding"]) as? Name)?.v.orEmpty()
                return Font(type0 && (enc.isEmpty() || enc.startsWith("Identity") || enc.contains("UCS2") || enc.contains("UTF16")), map, widths, dw, latin = !type0)
            }

            /** A ToUnicode CMap: bfchar and bfrange lines, codes to text. */
            fun cmap(s: String): Map<Int, String> {
                val out = HashMap<Int, String>()
                fun hexCode(h: String) = h.toLong(16).toInt()
                fun uni(h: String): String = runCatching { String(ByteArray(h.length / 2) { k -> h.substring(k * 2, k * 2 + 2).toInt(16).toByte() }, Charsets.UTF_16BE) }.getOrDefault("")
                for (block in Regex("""beginbfchar(.*?)endbfchar""", RegexOption.DOT_MATCHES_ALL).findAll(s)) {
                    for (m in Regex("""<([0-9A-Fa-f]+)>\s*<([0-9A-Fa-f]*)>""").findAll(block.groupValues[1])) out[hexCode(m.groupValues[1])] = uni(m.groupValues[2])
                }
                for (block in Regex("""beginbfrange(.*?)endbfrange""", RegexOption.DOT_MATCHES_ALL).findAll(s)) {
                    for (m in Regex("""<([0-9A-Fa-f]+)>\s*<([0-9A-Fa-f]+)>\s*(<([0-9A-Fa-f]+)>|\[([^\]]*)])""").findAll(block.groupValues[1])) {
                        val a = hexCode(m.groupValues[1]); val b = hexCode(m.groupValues[2])
                        if (b - a > 65535 || b < a) continue
                        if (m.groupValues[4].isNotEmpty()) {
                            val start = m.groupValues[4]
                            val base = start.toLong(16)
                            for (c in a..b) out[c] = uni((base + (c - a)).toString(16).padStart(start.length, '0'))
                        } else {
                            Regex("""<([0-9A-Fa-f]+)>""").findAll(m.groupValues[5]).forEachIndexed { j, x -> out[a + j] = uni(x.groupValues[1]) }
                        }
                    }
                }
                return out
            }
        }
    }

    // ------------------------------------------------------------------ text operators

    internal data class Glyph(val text: String, val x0: Double, val x1: Double, val y: Double, val size: Double)

    private class Interpreter(val fonts: Map<String, Font>, val r: Reader, val res: Map<String, Any?>) {
        // Matrices as [a b c d e f].
        var ctm = doubleArrayOf(1.0, 0.0, 0.0, 1.0, 0.0, 0.0)
        val stack = ArrayDeque<DoubleArray>()
        var tm = doubleArrayOf(1.0, 0.0, 0.0, 1.0, 0.0, 0.0)
        var tlm = tm.copyOf()
        var font: Font? = null
        var size = 10.0
        var tc = 0.0; var tw = 0.0; var tz = 1.0; var tl = 0.0
        val out = mutableListOf<Glyph>()

        fun mul(m: DoubleArray, n: DoubleArray) = doubleArrayOf(
            m[0] * n[0] + m[1] * n[2], m[0] * n[1] + m[1] * n[3], m[2] * n[0] + m[3] * n[2], m[2] * n[1] + m[3] * n[3],
            m[4] * n[0] + m[5] * n[2] + n[4], m[4] * n[1] + m[5] * n[3] + n[5],
        )

        fun run(content: String, depth: Int = 0): List<Glyph> {
            val lx = Lexer(content)
            val args = mutableListOf<Any?>()
            while (true) {
                val t = lx.value()
                if (t == null && lx.i >= content.length) break
                if (t !is Op) { args += t; continue }
                // An inline picture's bytes are not operators.
                if (t.v == "ID") { val e = content.indexOf("EI", lx.i); lx.i = if (e < 0) content.length else e + 2; args.clear(); continue }
                fun n(k: Int) = (args.getOrNull(k) as? Double) ?: 0.0
                when (t.v) {
                    "q" -> stack.addLast(ctm.copyOf())
                    "Q" -> stack.removeLastOrNull()?.let { ctm = it }
                    "cm" -> if (args.size >= 6) ctm = mul(DoubleArray(6) { n(it) }, ctm)
                    "BT" -> { tm = doubleArrayOf(1.0, 0.0, 0.0, 1.0, 0.0, 0.0); tlm = tm.copyOf() }
                    "Tf" -> { font = (args.getOrNull(0) as? Name)?.let { fonts[it.v] }; size = n(1) }
                    "Tc" -> tc = n(0)
                    "Tw" -> tw = n(0)
                    "Tz" -> tz = n(0) / 100.0
                    "TL" -> tl = n(0)
                    "Td" -> { tlm = mul(doubleArrayOf(1.0, 0.0, 0.0, 1.0, n(0), n(1)), tlm); tm = tlm.copyOf() }
                    "TD" -> { tl = -n(1); tlm = mul(doubleArrayOf(1.0, 0.0, 0.0, 1.0, n(0), n(1)), tlm); tm = tlm.copyOf() }
                    "Tm" -> if (args.size >= 6) { tlm = DoubleArray(6) { n(it) }; tm = tlm.copyOf() }
                    "T*" -> { tlm = mul(doubleArrayOf(1.0, 0.0, 0.0, 1.0, 0.0, -tl), tlm); tm = tlm.copyOf() }
                    "Tj" -> (args.lastOrNull() as? PStr)?.let { show(it.bytes) }
                    "'" -> { tlm = mul(doubleArrayOf(1.0, 0.0, 0.0, 1.0, 0.0, -tl), tlm); tm = tlm.copyOf(); (args.lastOrNull() as? PStr)?.let { show(it.bytes) } }
                    "\"" -> { tw = n(0); tc = n(1); tlm = mul(doubleArrayOf(1.0, 0.0, 0.0, 1.0, 0.0, -tl), tlm); tm = tlm.copyOf(); (args.lastOrNull() as? PStr)?.let { show(it.bytes) } }
                    "TJ" -> (args.lastOrNull() as? List<*>)?.forEach { e ->
                        when (e) { is PStr -> show(e.bytes); is Double -> tm = mul(doubleArrayOf(1.0, 0.0, 0.0, 1.0, -e / 1000.0 * size * tz, 0.0), tm) }
                    }
                    "Do" -> if (depth < 3) (args.lastOrNull() as? Name)?.let { nm ->
                        // A form XObject (a table drawn once and placed) is read as part of the page.
                        val xo = r.get(r.dict(res["XObject"])?.get(nm.v)) as? PStream
                        if (xo != null && (xo.dict["Subtype"] as? Name)?.v == "Form") r.decode(xo)?.let { body ->
                            val save = ctm
                            (r.get(xo.dict["Matrix"]) as? List<*>)?.mapNotNull { r.num(it) }?.takeIf { it.size == 6 }?.let { ctm = mul(it.toDoubleArray(), ctm) }
                            run(String(body, Charsets.ISO_8859_1), depth + 1)
                            ctm = save
                        }
                    }
                }
                args.clear()
            }
            return out
        }

        fun show(b: ByteArray) {
            val f = font ?: return
            for (code in f.codes(b)) {
                val w = f.width(code) * size
                val adv = (w + tc + if (code == 32 && !f.twoByte) tw else 0.0) * tz
                val m = mul(tm, ctm)
                val x0 = m[4]; val y = m[5]
                val scale = kotlin.math.hypot(m[0], m[1]).coerceAtLeast(1e-6)
                val x1 = x0 + w * tz * scale
                val txt = f.text(code)
                if (txt != null) out += Glyph(txt, x0, x1, y, size * kotlin.math.hypot(m[2], m[3]).coerceAtLeast(1e-6))
                tm = mul(doubleArrayOf(1.0, 0.0, 0.0, 1.0, adv, 0.0), tm)
            }
        }
    }

    /**
     * Glyphs into words: the same baseline, each glyph starting where the last ended (a gap under a third of the letter size);
     * a space ends a word. In [SCALE]× page pixels, top-left origin.
     */
    internal fun words(glyphs: List<Glyph>): List<RosterWord> {
        val out = mutableListOf<RosterWord>()
        val lines = glyphs.filter { it.text.isNotEmpty() }.groupBy { Math.round(it.y * 2) }
        for ((_, line) in lines) {
            var cur = StringBuilder(); var x0 = 0.0; var x1 = 0.0; var y = 0.0; var sz = 0.0
            fun flush() {
                val t = cur.toString().trim()
                if (t.isNotEmpty()) out += RosterWord(t, (x0 * SCALE).toInt(), ((y - sz * 0.8) * SCALE).toInt(), (x1 * SCALE).toInt().coerceAtLeast((x0 * SCALE).toInt() + 1), ((y + sz * 0.2) * SCALE).toInt())
                cur = StringBuilder()
            }
            for (g in line.sortedBy { it.x0 }) {
                val blank = g.text.isBlank()
                if (cur.isNotEmpty() && (blank || g.x0 - x1 > g.size * 0.3 || g.x0 < x0)) flush()
                if (blank) continue
                if (cur.isEmpty()) { x0 = g.x0; y = g.y; sz = g.size }
                cur.append(g.text)
                x1 = maxOf(x1.takeIf { cur.length > g.text.length } ?: g.x1, g.x1)
            }
            flush()
        }
        return out.sortedWith(compareBy({ it.top }, { it.left }))
    }
}
