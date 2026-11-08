package com.bamnat.core.memo

/**
 * [MPH-01][MPH-02] 1.38 메모 사진 (사용자 2026-11-05 "메모장에 사진도 넣을 수 있게, 크기 조정도, 사진 옆에 글자를 놓게 —
 * 파워포인트나 한글처럼 자유자재로"). A photo sits in the memo's body as a line of its own, so the database, the undo history, the
 * backup and every calendar keep working with the body as they always did (no new column, no new DB version):
 *
 *     ⟦사진 1f0c…e2.jpg 60 왼쪽⟧                  a photo 60 % of the memo's width, at the left
 *     ⟦사진 1f0c…e2.jpg 45 오른쪽 옆⟧             a photo with words beside it (text wrapping), the words on the lines after it,
 *     장 볼 것: 우유, 달걀                         up to the closing line
 *     ⟦/사진⟧
 *
 * Only the file's name is in the text (the photo itself is in the app's own folder, files/memo-photos); everything that shows the memo
 * as words ([plain]) leaves the lines out, so a preview, a search, a share or a template never shows them.
 */
object MemoPhotos {
    enum class Align(val word: String) {
        LEFT("왼쪽"), CENTER("가운데"), RIGHT("오른쪽");

        companion object {
            fun of(word: String): Align? = entries.firstOrNull { it.word == word }
        }
    }

    sealed interface Block

    data class Text(val text: String) : Block

    /**
     * One photo. [width] is % of the memo's width ([MIN_WIDTH]..100). [side] is the words beside it (null: the photo stands alone and
     * the words go on below it); words go beside only a photo at the left or right.
     */
    data class Photo(val name: String, val width: Int = DEFAULT_WIDTH, val align: Align = Align.CENTER, val side: String? = null) : Block {
        val wraps: Boolean get() = side != null && align != Align.CENTER
    }

    /**
     * [MPH-05] 1.38 메모 종이 (사용자 2026-11-05 결정 "자유 판까지 전부 1.38"): ruled lines, a grid or dots behind the words, drawn at the
     * words' own line height so they sit on the lines. One line `⟦종이 줄⟧` at the top of the body; none is a plain page (or the theme's,
     * 스튜디오 부품 '메모 종이').
     */
    enum class PaperKind(val word: String) {
        LINES("줄"), GRID("모눈"), DOTS("점");

        companion object {
            fun of(word: String): PaperKind? = entries.firstOrNull { it.word == word }
        }
    }

    data class Paper(val kind: PaperKind) : Block

    /** [MPH-07] 1.38 자유 판의 한 조각: a photo, a text box or a sticker. */
    enum class BoardKind(val code: Char) {
        PHOTO('p'), TEXT('t'), STICKER('s');

        companion object {
            fun of(c: Char): BoardKind? = entries.firstOrNull { it.code == c }
        }
    }

    /**
     * One piece on a 자유 판. [x]·[y] are its centre in ‰ of the board's width and height and [w] its width in ‰ of the board's width, so a
     * phone of any size puts it in the same place; [rot] turns it, degrees clockwise. [text] is the photo's file name, the sticker's emoji
     * or the text box's words. [size] is a text box's letters in sp; [ink] and [fill] its letters' and box's colours (ARGB, null for the
     * app's own). Pieces later in the list lie over earlier ones.
     */
    data class BoardItem(
        val kind: BoardKind,
        val text: String,
        val x: Int = 500,
        val y: Int = 500,
        val w: Int = 400,
        val rot: Int = 0,
        val size: Int = 18,
        val ink: Long? = null,
        val fill: Long? = null,
    ) {
        fun fixed(): BoardItem = copy(
            x = x.coerceIn(0, 1000), y = y.coerceIn(0, 1000), w = w.coerceIn(MIN_ITEM_W, 1000), rot = ((rot % 360) + 360) % 360,
            size = size.coerceIn(8, 72), text = text.take(if (kind == BoardKind.TEXT) MAX_ITEM_TEXT else 64),
        )
    }

    /**
     * [MPH-07] 1.38 자유 판 (파워포인트·한글처럼): a board in the memo where photos, text boxes and stickers are put anywhere, made bigger,
     * turned and laid over each other by a finger. [ratio] is its height in % of its width. One line in the body:
     * `⟦자유판 75 p,500,300,600,0,18,-,-,<name>;t,…⟧`.
     */
    data class Board(val ratio: Int = DEFAULT_RATIO, val items: List<BoardItem> = emptyList()) : Block

    const val DEFAULT_RATIO = 75
    const val MIN_RATIO = 30
    const val MAX_RATIO = 250
    const val MIN_ITEM_W = 40
    const val MAX_BOARD_ITEMS = 40
    const val MAX_ITEM_TEXT = 500

    const val MIN_WIDTH = 20
    const val DEFAULT_WIDTH = 100

    /** Beside words take room only next to a photo that leaves at least this much of the width. */
    const val MAX_SIDE_WIDTH = 70

    /** Photos one memo can hold: each is about 0.3~0.6 MB (1600 px JPEG), so a memo stays under ~20 MB. */
    const val MAX_PER_MEMO = 30

    private const val MARK = "⟦"
    private const val OPEN = "⟦사진 "
    private const val PAPER_OPEN = "⟦종이 "
    private const val BOARD_OPEN = "⟦자유판 "
    private const val CLOSE = "⟦/사진⟧"
    private const val SIDE = "옆"
    private val NAME = Regex("^[A-Za-z0-9-]{1,64}\\.jpg$")
    private val LINE = Regex("^⟦사진 ([A-Za-z0-9-]{1,64}\\.jpg)(?: (\\d{1,3}))?(?: (왼쪽|가운데|오른쪽))?( 옆)?⟧$")

    fun validName(name: String): Boolean = NAME.matches(name)

    /** The line a photo starts with. */
    fun line(p: Photo): String = buildString {
        append(OPEN).append(p.name).append(' ').append(p.width.coerceIn(MIN_WIDTH, 100)).append(' ').append(p.align.word)
        if (p.wraps) append(' ').append(SIDE)
        append('⟧')
    }

    private fun photoOf(line: String): Photo? {
        val m = LINE.matchEntire(line) ?: return null
        val width = m.groupValues[2].toIntOrNull()?.coerceIn(MIN_WIDTH, 100) ?: DEFAULT_WIDTH
        val align = Align.of(m.groupValues[3]) ?: Align.CENTER
        return Photo(m.groupValues[1], width, align, if (m.groupValues[4].isNotEmpty() && align != Align.CENTER) "" else null)
    }

    fun paperLine(kind: PaperKind): String = "$PAPER_OPEN${kind.word}⟧"

    private fun paperOf(line: String): Paper? =
        if (line.startsWith(PAPER_OPEN) && line.endsWith("⟧")) PaperKind.of(line.removePrefix(PAPER_OPEN).removeSuffix("⟧"))?.let(::Paper) else null

    private fun enc(t: String) = java.net.URLEncoder.encode(t, "UTF-8")
    private fun dec(t: String) = runCatching { java.net.URLDecoder.decode(t, "UTF-8") }.getOrNull()
    private fun hexOrDash(c: Long?) = c?.let { (it and 0xFFFFFFFFL).toString(16).uppercase().padStart(8, '0') } ?: "-"
    private fun colorOf(t: String): Long? = if (t == "-") null else t.takeIf { it.length == 8 }?.toLongOrNull(16)

    fun boardLine(b: Board): String = buildString {
        append(BOARD_OPEN).append(b.ratio.coerceIn(MIN_RATIO, MAX_RATIO))
        if (b.items.isNotEmpty()) append(' ')
        append(b.items.take(MAX_BOARD_ITEMS).joinToString(";") { raw ->
            val i = raw.fixed()
            "${i.kind.code},${i.x},${i.y},${i.w},${i.rot},${i.size},${hexOrDash(i.ink)},${hexOrDash(i.fill)},${enc(i.text)}"
        })
        append('⟧')
    }

    private fun boardOf(line: String): Board? {
        if (!line.startsWith(BOARD_OPEN) || !line.endsWith("⟧")) return null
        val rest = line.removePrefix(BOARD_OPEN).removeSuffix("⟧")
        val ratio = rest.substringBefore(' ').toIntOrNull() ?: return null
        val list = rest.substringAfter(' ', "")
        val items = if (list.isEmpty()) emptyList() else list.split(';').mapNotNull { one ->
            val f = one.split(',')
            if (f.size != 9) return@mapNotNull null
            val kind = f[0].singleOrNull()?.let(BoardKind::of) ?: return@mapNotNull null
            val text = dec(f[8]) ?: return@mapNotNull null
            if (kind == BoardKind.PHOTO && !validName(text)) return@mapNotNull null
            BoardItem(kind, text, f[1].toIntOrNull() ?: 500, f[2].toIntOrNull() ?: 500, f[3].toIntOrNull() ?: 400,
                f[4].toIntOrNull() ?: 0, f[5].toIntOrNull() ?: 18, colorOf(f[6]), colorOf(f[7])).fixed()
        }.take(MAX_BOARD_ITEMS)
        return Board(ratio.coerceIn(MIN_RATIO, MAX_RATIO), items)
    }

    /** [MPH-05] The memo's own paper (the first 종이 line), or null for a plain page. */
    fun paper(body: String): PaperKind? = if (PAPER_OPEN !in body) null else parse(body).firstNotNullOfOrNull { (it as? Paper)?.kind }

    /** [MPH-05] [body] on paper [kind] (null: plain), the line kept at the very top. */
    fun withPaper(body: String, kind: PaperKind?): String {
        val rest = parse(body).filterNot { it is Paper }
        val blocks = if (kind == null) rest else listOf(Paper(kind)) + rest
        return join(merged(blocks)).let { if (kind != null && rest.isEmpty()) it + "\n" else it }
    }

    /** True when the body holds a photo (quick, before [parse]). */
    fun has(body: String): Boolean = MARK in body && parse(body).any { it is Photo }

    /**
     * The body as text and photos, in order. Text blocks are the runs of lines between photos (an empty line between two photos is a
     * Text("")); [join] gives the same body back.
     */
    fun parse(body: String): List<Block> {
        if (MARK !in body) return listOf(Text(body))
        val out = ArrayList<Block>()
        val text = ArrayList<String>()
        fun flush() {
            if (text.isNotEmpty()) out += Text(text.joinToString("\n"))
            text.clear()
        }
        val lines = body.split('\n')
        var i = 0
        while (i < lines.size) {
            val photo = if (lines[i].startsWith(MARK)) photoOf(lines[i]) else null
            if (photo == null) {
                val other = if (lines[i].startsWith(MARK)) paperOf(lines[i]) ?: boardOf(lines[i]) else null
                if (other != null) { flush(); out += other } else text += lines[i]
                i++; continue
            }
            flush()
            if (photo.side != null) {
                val side = ArrayList<String>()
                i++
                while (i < lines.size && lines[i] != CLOSE) { side += lines[i]; i++ }
                if (i < lines.size) i++ // the closing line
                out += photo.copy(side = side.joinToString("\n"))
            } else {
                out += photo; i++
            }
        }
        flush()
        return out
    }

    fun join(blocks: List<Block>): String = blocks.joinToString("\n") { b ->
        when (b) {
            is Text -> b.text
            is Photo -> when {
                b.wraps -> line(b) + "\n" + b.side.orEmpty().replace(CLOSE, "/사진") + "\n" + CLOSE
                // Words that were beside a photo now in the middle go on below it, never lost.
                !b.side.isNullOrEmpty() -> line(b) + "\n" + b.side
                else -> line(b)
            }
            is Paper -> paperLine(b.kind)
            is Board -> boardLine(b)
        }
    }

    /** The memo's words without the photo lines (the words beside a photo stay, in their place). */
    fun plain(body: String): String {
        if (MARK !in body) return body
        return parse(body).mapNotNull { b ->
            when (b) {
                is Text -> b.text
                is Photo -> b.side?.takeIf { it.isNotEmpty() }
                is Paper -> null
                // [MPH-07] A board's text boxes are the memo's words too (search, preview).
                is Board -> b.items.filter { it.kind == BoardKind.TEXT && it.text.isNotBlank() }.joinToString("\n") { it.text }.ifEmpty { null }
            }
        }.joinToString("\n")
    }

    /** The photos' file names, in order (one used twice is listed once). */
    fun names(body: String): List<String> =
        if (MARK !in body) emptyList() else parse(body).flatMap { b ->
            when (b) {
                is Photo -> listOf(b.name)
                is Board -> b.items.filter { it.kind == BoardKind.PHOTO }.map { it.text }
                else -> emptyList()
            }
        }.distinct()

    /** Photos in the body, the boards' too (what [MAX_PER_MEMO] counts). */
    fun count(body: String): Int = if (MARK !in body) 0 else parse(body).sumOf { b ->
        when (b) {
            is Photo -> 1
            is Board -> b.items.count { it.kind == BoardKind.PHOTO }
            else -> 0
        }
    }

    /**
     * [text] with photo [name] put in at [caret] on a line of its own: the words before the caret stay above it and the words after it
     * go below. With [caret] null it goes at the end.
     */
    fun insert(text: String, caret: Int?, name: String, width: Int = DEFAULT_WIDTH, align: Align = Align.CENTER): Insert {
        val at = (caret ?: text.length).coerceIn(0, text.length)
        val before = text.substring(0, at).removeSuffix("\n")
        val after = text.substring(at).removePrefix("\n")
        val photo = line(Photo(name, width, align))
        val head = if (before.isEmpty() && at == 0) "" else before + "\n"
        val result = head + photo + "\n" + after
        return Insert(result, head.length + photo.length + 1)
    }

    /**
     * [MPH-01] Several photos chosen at once, one under the other at [caret], in the order picked (PC 확인 3dd61e00: two picked, one went
     * in). Put in by one edit, so one step undoes them all.
     */
    fun insertAll(text: String, caret: Int?, names: List<String>): Insert {
        var at = Insert(text, (caret ?: text.length).coerceIn(0, text.length))
        for (n in names) at = insert(at.text, at.caret, n)
        return at
    }

    /** [MPH-07] [text] with a new 자유 판 put in at [caret], as [insert] puts a photo. */
    fun insertBoard(text: String, caret: Int?, board: Board = Board()): Insert {
        val at = (caret ?: text.length).coerceIn(0, text.length)
        val before = text.substring(0, at).removeSuffix("\n")
        val after = text.substring(at).removePrefix("\n")
        val line = boardLine(board)
        val head = if (before.isEmpty() && at == 0) "" else before + "\n"
        return Insert(head + line + "\n" + after, head.length + line.length + 1)
    }

    /** [MPH-07] [body] with the board at block [index] made [board]. */
    fun updateBoard(body: String, index: Int, board: Board): String {
        val blocks = parse(body).toMutableList()
        if (blocks.getOrNull(index) !is Board) return body
        blocks[index] = board.copy(items = board.items.take(MAX_BOARD_ITEMS).map { it.fixed() }, ratio = board.ratio.coerceIn(MIN_RATIO, MAX_RATIO))
        return join(blocks)
    }

    /** What [insert] gives: the new text and where the caret goes (the line after the photo). */
    data class Insert(val text: String, val caret: Int)

    /** [body] with the photo at block [index] changed by [change]. */
    fun update(body: String, index: Int, change: (Photo) -> Photo): String {
        val blocks = parse(body).toMutableList()
        val p = blocks.getOrNull(index) as? Photo ?: return body
        blocks[index] = change(p).let { it.copy(width = it.width.coerceIn(MIN_WIDTH, 100)) }
        return join(blocks)
    }

    /**
     * [body] without the photo at block [index]. Its beside words stay, as text where it stood; the text around it is joined back so no
     * empty line is left in its place.
     */
    fun remove(body: String, index: Int): String {
        val blocks = parse(body).toMutableList()
        val b = blocks.getOrNull(index)?.takeIf { it is Photo || it is Board } ?: return body
        blocks.removeAt(index)
        val keep = when (b) {
            is Photo -> b.side
            // [MPH-07] A board taken out leaves its words, so nothing written is lost.
            is Board -> b.items.filter { it.kind == BoardKind.TEXT && it.text.isNotBlank() }.joinToString("\n") { it.text }
            else -> null
        }
        if (!keep.isNullOrEmpty()) blocks.add(index, Text(keep))
        return join(merged(blocks))
    }

    /** [body] with the photo at block [index] no longer beside its words: they go on under it, as they were. */
    fun unwrap(body: String, index: Int): String {
        val blocks = parse(body).toMutableList()
        val p = blocks.getOrNull(index) as? Photo ?: return body
        blocks[index] = p.copy(side = null)
        if (!p.side.isNullOrEmpty()) blocks.add(index + 1, Text(p.side))
        return join(merged(blocks))
    }

    /** [body] with the photo at block [index] moved one place up ([by] -1) or down (+1), past one text or photo block. */
    fun move(body: String, index: Int, by: Int): String {
        val blocks = parse(body).toMutableList()
        val to = index + by
        if ((blocks.getOrNull(index) !is Photo && blocks.getOrNull(index) !is Board) || to !in blocks.indices) return body
        val p = blocks.removeAt(index)
        blocks.add(to, p)
        return join(merged(blocks))
    }

    /** Two text blocks side by side become one (so the body's lines read back the same). */
    private fun merged(blocks: List<Block>): List<Block> {
        val out = ArrayList<Block>()
        for (b in blocks) {
            val last = out.lastOrNull()
            if (b is Text && last is Text) out[out.lastIndex] = Text(last.text + "\n" + b.text) else out += b
        }
        return out
    }
}
