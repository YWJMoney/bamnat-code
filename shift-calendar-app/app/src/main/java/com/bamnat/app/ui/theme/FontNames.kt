package com.bamnat.app.ui.theme

/**
 * [MAT-06] 1.36 PC 확인 6차: what a font file says of itself, read on the phone the way the server reads it (server
 * `recipe-fonts.js` fontInfo·fontSold): its maker, copyright, licence words and OS/2 vendor, and the OS/2 class and PANOSE that say
 * what it looks like. So a font a paid foundry made is told at once (it was sent, turned away and shown as 'not open'), and the
 * app font that stands in for it is the one most like it (a handwriting font was standing in as 기본 고딕).
 */
internal object FontNames {
    data class Info(
        val family: String? = null, val copyright: String? = null, val maker: String? = null, val license: String? = null,
        val licenseUrl: String? = null, val vendor: String? = null,
        /** OS/2 sFamilyClass's class (1–5·7 serif, 8 sans, 10 script, 12 symbol), 0 for none. */
        val familyClass: Int = 0,
        /** PANOSE bFamilyType (2 text, 3 hand written, 4 decorative) and bSerifStyle (11–13 sans), 0 for any. */
        val panoseFamily: Int = 0, val panoseSerif: Int = 0,
        val fixedPitch: Boolean = false,
    )

    /** The same list as the server's FONT_SOLD_BY. */
    private val SOLD_BY = Regex("sandoll|산돌|yoon ?design|윤디자인|hanyang|한양정보|rix ?font|릭스|fontrix|폰트릭스|monotype|linotype|microsoft|apple inc|apple computer|bitstream|font ?bureau|hoefler", RegexOption.IGNORE_CASE)
    private val OFL = Regex("open font licen[cs]e|scripts\\.sil\\.org/ofl|openfontlicense\\.org|\\bOFL\\b", RegexOption.IGNORE_CASE)
    private val APACHE = Regex("apache\\.org/licenses|apache licen[cs]e", RegexOption.IGNORE_CASE)

    /** Whether its own words name OFL or Apache. */
    fun saysOpen(i: Info): Boolean = "${i.license.orEmpty()} ${i.licenseUrl.orEmpty()}".let { OFL.containsMatchIn(it) || APACHE.containsMatchIn(it) }

    /** Whether a foundry that sells fonts made it and its words do not say OFL·Apache (the server turns it away). */
    fun sold(i: Info): Boolean = SOLD_BY.containsMatchIn("${i.maker.orEmpty()} ${i.copyright.orEmpty()} ${i.vendor.orEmpty()}") && !saysOpen(i)

    /** The app font most like it by its looks: 손글씨, 명조, 고정폭, else null (the name decides). */
    fun lookalike(i: Info): String? = when {
        i.fixedPitch -> "mono"
        i.panoseFamily == 3 || i.familyClass == 10 -> "hand"
        i.familyClass in setOf(1, 2, 3, 4, 5, 7) || i.panoseSerif in 2..10 -> "serif"
        i.familyClass == 8 || i.panoseSerif in 11..13 -> "sans"
        else -> null
    }

    fun read(b: ByteArray): Info {
        val t = tables(b) ?: return Info()
        val out = HashMap<String, String>()
        val rank = HashMap<String, Int>()
        t["name"]?.let { (base, size) ->
            val count = u16(b, base + 2)
            val strings = base + u16(b, base + 4)
            val want = mapOf(0 to "copyright", 1 to "family", 8 to "maker", 13 to "license", 14 to "licenseUrl")
            for (k in 0 until count) {
                val r = base + 6 + k * 12
                if (r + 12 > base + size || r + 12 > b.size) break
                val platform = u16(b, r)
                val lang = u16(b, r + 4)
                val key = want[u16(b, r + 6)] ?: continue
                val len = u16(b, r + 8)
                val off = strings + u16(b, r + 10)
                if (off + len > b.size || platform !in setOf(0, 1, 3)) continue
                val score = if (platform == 3 && lang == 0x0412) 3 else if (platform == 3 && lang == 0x0409) 2 else 1
                if ((rank[key] ?: 0) >= score) continue
                val text = if (platform == 1) String(b, off, len, Charsets.ISO_8859_1) else String(b, off, len, Charsets.UTF_16BE)
                val clean = text.replace(Regex("[\\u0000-\\u001f\\u007f]"), " ").trim().take(200)
                if (clean.isEmpty()) continue
                rank[key] = score
                out[key] = clean
            }
        }
        var vendor: String? = null
        var cls = 0
        var pFamily = 0
        var pSerif = 0
        t["OS/2"]?.let { (o, size) ->
            if (size >= 62 && o + 62 <= b.size) {
                cls = b[o + 30].toInt() and 0xFF
                pFamily = b[o + 32].toInt() and 0xFF
                pSerif = b[o + 33].toInt() and 0xFF
                vendor = String(b, o + 58, 4, Charsets.ISO_8859_1).filter { it in ' '..'~' }.trim().ifEmpty { null }
            }
        }
        val fixed = t["post"]?.let { (o, size) -> size >= 16 && o + 16 <= b.size && u32(b, o + 12) != 0L } ?: false
        return Info(out["family"], out["copyright"], out["maker"], out["license"], out["licenseUrl"], vendor, cls, pFamily, pSerif, fixed)
    }

    /**
     * 1.36 [SEC-10] M8 (보안 점검): where the font's own bytes end — its headers and every table of every face, rounded up to 4, as
     * the server reads it (`recipe-fonts.js` fontEnd) — or null when the directory does not hold together.
     */
    fun end(b: ByteArray): Int? {
        if (b.size < 12) return null
        val ttc = String(b, 0, 4, Charsets.ISO_8859_1) == "ttcf"
        val n = if (ttc) u32(b, 8).toInt() else 1
        if (n !in 1..16 || (ttc && 12 + n * 4 > b.size)) return null
        var end = if (ttc) 12L + n * 4 else 0L
        if (ttc && u16(b, 4) == 2 && 12 + n * 4 + 12 <= b.size) {
            end += 12
            val len = u32(b, 12 + n * 4 + 4)
            val off = u32(b, 12 + n * 4 + 8)
            if (off != 0L && off + len <= b.size) end = maxOf(end, off + len)
        }
        for (k in 0 until n) {
            val at = if (ttc) u32(b, 12 + k * 4).toInt() else 0
            if (at < 0 || at + 12 > b.size) return null
            val num = u16(b, at + 4)
            if (num !in 1..64 || at + 12 + num * 16 > b.size) return null
            end = maxOf(end, at + 12L + num * 16)
            for (i in 0 until num) {
                val r = at + 12 + i * 16
                val off = u32(b, r + 8)
                val len = u32(b, r + 12)
                if (off + len > b.size) return null
                end = maxOf(end, off + len)
            }
        }
        return ((end + 3) / 4 * 4).toInt()
    }

    /**
     * [b] without bytes past its last table: they are not part of the font (the server turns such a file away, and they only change
     * its id), so the font goes in, and goes along with a recipe, as the font it is.
     */
    fun trimmed(b: ByteArray): ByteArray = end(b)?.takeIf { it < b.size }?.let { b.copyOf(it) } ?: b

    /**
     * 1.36 PC 확인 7차 (사용성 제안 "한글 없는 글꼴은 넣을 때 알려 주기"): whether its cmap draws 가 and 한; null when the cmap does not
     * read (then nothing is said).
     */
    fun hangul(b: ByteArray): Boolean? {
        val cmap = tables(b)?.get("cmap") ?: return null
        val (base, size) = cmap
        if (size < 4) return null
        val count = u16(b, base + 2)
        var best = -1
        var bestScore = 0
        for (i in 0 until count) {
            val r = base + 4 + i * 8
            if (r + 8 > base + size) break
            val p = u16(b, r)
            val e = u16(b, r + 2)
            val off = base + u32(b, r + 4).toInt()
            val format = u16(b, off)
            val score = when {
                format == 12 && (p == 3 && e == 10 || p == 0) -> 3
                format == 4 && (p == 3 && e == 1 || p == 0) -> 2
                else -> 0
            }
            if (score > bestScore) { best = off; bestScore = score }
        }
        if (best < 0) return null
        return listOf(0xAC00, 0xD55C).all { glyphOf(b, best, it) != 0 }
    }

    private fun glyphOf(b: ByteArray, at: Int, c: Int): Int = when (u16(b, at)) {
        4 -> {
            val segs = u16(b, at + 6) / 2
            val ends = at + 14
            val starts = ends + segs * 2 + 2
            val deltas = starts + segs * 2
            val ranges = deltas + segs * 2
            var g = 0
            for (k in 0 until segs) {
                val end = u16(b, ends + k * 2)
                if (end < c) continue
                val start = u16(b, starts + k * 2)
                if (start > c) break
                val delta = u16(b, deltas + k * 2)
                val ro = u16(b, ranges + k * 2)
                g = if (ro == 0) (c + delta) and 0xFFFF
                    else u16(b, ranges + k * 2 + ro + 2 * (c - start)).let { if (it == 0) 0 else (it + delta) and 0xFFFF }
                break
            }
            g
        }
        12 -> {
            val groups = u32(b, at + 12).toInt()
            var g = 0
            for (k in 0 until groups.coerceAtMost(100_000)) {
                val r = at + 16 + k * 12
                if (r + 12 > b.size) break
                val start = u32(b, r)
                val end = u32(b, r + 4)
                if (c in start..end) { g = (u32(b, r + 8) + (c - start)).toInt(); break }
            }
            g
        }
        else -> 0
    }

    /** The first face's tables by tag: offset and length, or null when the file does not read as one. */
    private fun tables(b: ByteArray): Map<String, Pair<Int, Int>>? {
        if (b.size < 12) return null
        var at = 0
        if (String(b, 0, 4, Charsets.ISO_8859_1) == "ttcf") {
            if (b.size < 16) return null
            at = u32(b, 12).toInt()
            if (at < 0 || at + 12 > b.size) return null
        }
        val num = u16(b, at + 4)
        if (num !in 1..64 || at + 12 + num * 16 > b.size) return null
        val out = HashMap<String, Pair<Int, Int>>()
        for (k in 0 until num) {
            val r = at + 12 + k * 16
            val tag = String(b, r, 4, Charsets.ISO_8859_1)
            val off = u32(b, r + 8)
            val len = u32(b, r + 12)
            if (off < 12 || off + len > b.size) return null
            out[tag] = off.toInt() to len.toInt()
        }
        return out
    }

    private fun u16(b: ByteArray, i: Int): Int = if (i + 2 > b.size || i < 0) 0 else ((b[i].toInt() and 0xFF) shl 8) or (b[i + 1].toInt() and 0xFF)
    private fun u32(b: ByteArray, i: Int): Long = if (i + 4 > b.size || i < 0) 0L else
        ((b[i].toLong() and 0xFF) shl 24) or ((b[i + 1].toLong() and 0xFF) shl 16) or ((b[i + 2].toLong() and 0xFF) shl 8) or (b[i + 3].toLong() and 0xFF)
}
