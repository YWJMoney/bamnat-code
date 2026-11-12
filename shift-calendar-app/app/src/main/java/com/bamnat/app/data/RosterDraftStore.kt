package com.bamnat.app.data

import com.bamnat.core.imports.DutyCell
import com.bamnat.core.imports.RosterRow
import com.bamnat.core.journal.RosterFrom
import com.bamnat.core.model.LeaveKind
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

/**
 * 2.0.0 [RVL-20] (검토 SCAN-22·U07b-15, 조건 10) 한 달 근무 넣기 in progress: what was typed, read and answered, kept as a small file
 * in no-backup storage (one per calendar), so the app closed in the background or the screen made again does not lose it. Written
 * whole (a new file renamed over the old), cleared once put on the calendar or thrown away. The photo itself and how it was read
 * word by word ([RosterRow.raw]) stay in memory only, as before.
 */
class RosterDraftStore(private val dir: File) {
    data class Draft(
        val month: YearMonth,
        val rows: List<RosterRow>?,
        val picked: Int,
        val edits: Map<Int, DutyCell?>,
        val answers: Map<String, DutyCell>,
        val cursor: Int,
        val from: RosterFrom,
        val named: YearMonth?,
        val chosen: Boolean,
        val withOutside: Boolean,
        val at: Instant,
        /** 2.0.0 [RVL-38] '그 날은 그대로 두기'. */
        val keepMine: Boolean = false,
    )

    fun read(calendarId: String, now: Instant = Instant.now()): Draft? = runCatching {
        val f = fileOf(calendarId).takeIf { it.exists() } ?: return null
        decode(JSONObject(f.readText())).takeIf { it.at.isAfter(now.minusSeconds(KEEP_DAYS * 86_400)) }
    }.getOrNull()

    fun write(calendarId: String, d: Draft) {
        runCatching {
            dir.mkdirs()
            val f = fileOf(calendarId)
            val part = File(dir, f.name + ".part")
            part.writeText(encode(d).toString())
            if (!part.renameTo(f)) {
                f.writeText(encode(d).toString())
                part.delete()
            }
        }
    }

    fun clear(calendarId: String) {
        fileOf(calendarId).delete()
    }

    private fun fileOf(calendarId: String) = File(dir, calendarId.replace(Regex("[^A-Za-z0-9_-]"), "_") + ".json")

    companion object {
        /** A draft older than this is not brought back (that month's roster has moved on). */
        const val KEEP_DAYS = 7L

        fun encode(d: Draft): JSONObject = JSONObject()
            .put("month", d.month.toString())
            .putOpt("rows", d.rows?.let { rs -> JSONArray().apply { rs.forEach { put(row(it)) } } })
            .put("picked", d.picked)
            .put("edits", JSONObject().apply { d.edits.forEach { (day, c) -> put(day.toString(), c?.let(::cell) ?: JSONObject.NULL) } })
            .put("answers", JSONObject().apply { d.answers.forEach { (k, c) -> put(k, cell(c)) } })
            .put("cursor", d.cursor)
            .put("from", d.from.name)
            .putOpt("named", d.named?.toString())
            .put("chosen", d.chosen)
            .put("withOutside", d.withOutside)
            .put("keepMine", d.keepMine)
            .put("at", d.at.toString())

        fun decode(o: JSONObject): Draft = Draft(
            month = YearMonth.parse(o.getString("month")),
            rows = o.optJSONArray("rows")?.let { a -> (0 until a.length()).map { row(a.getJSONObject(it)) } },
            picked = o.optInt("picked"),
            edits = o.optJSONObject("edits")?.let { e ->
                e.keys().asSequence().associate { k -> k.toInt() to (if (e.isNull(k)) null else cell(e.getString(k))) }
            }.orEmpty(),
            answers = o.optJSONObject("answers")?.let { e -> e.keys().asSequence().associateWith { k -> cell(e.getString(k)) } }.orEmpty(),
            cursor = o.optInt("cursor", 1),
            from = runCatching { RosterFrom.valueOf(o.getString("from")) }.getOrDefault(RosterFrom.TYPED),
            named = o.optString("named").takeIf { it.isNotEmpty() }?.let(YearMonth::parse),
            chosen = o.optBoolean("chosen", true),
            withOutside = o.optBoolean("withOutside", true),
            at = Instant.parse(o.getString("at")),
            keepMine = o.optBoolean("keepMine", false),
        )

        /** One cell as a short word: S:<kind id>, O, L:<leave kind>:<quarters>:<from start>, U:<leave word>:<what was read>. */
        fun cell(c: DutyCell): String = when (c) {
            is DutyCell.Shift -> "S:${c.shiftTypeId}"
            DutyCell.Off -> "O"
            is DutyCell.Leave -> "L:${c.kind.name}:${c.quarters}:${if (c.fromStart) 1 else 0}"
            is DutyCell.Unknown -> "U:${if (c.leave) 1 else 0}:${c.raw}"
        }

        fun cell(s: String): DutyCell = when {
            s == "O" -> DutyCell.Off
            s.startsWith("S:") -> DutyCell.Shift(s.substring(2).toLong())
            s.startsWith("L:") -> s.split(':').let { p -> DutyCell.Leave(LeaveKind.valueOf(p[1]), p[2].toInt(), p[3] == "1") }
            s.startsWith("U:") -> DutyCell.Unknown(s.substring(4), leave = s[2] == '1')
            else -> DutyCell.Unknown(s)
        }

        private fun ints(s: Set<Int>) = JSONArray().apply { s.sorted().forEach { put(it) } }
        private fun ints(a: JSONArray?): Set<Int> = a?.let { (0 until it.length()).map(it::getInt).toSet() }.orEmpty()

        private fun row(r: RosterRow): JSONObject = JSONObject()
            .put("label", r.label)
            .put("cells", JSONObject().apply { r.cells.forEach { (d, c) -> put(d.toString(), cell(c)) } })
            .put("guessed", ints(r.guessed))
            .put("notes", JSONObject().apply { r.notes.forEach { (d, n) -> put(d.toString(), n) } })
            .put("unsure", ints(r.unsure))
            .put("contested", ints(r.contested))
            .put("line", r.line)
            .put("outside", JSONObject().apply { r.outside.forEach { (d, c) -> put(d.toString(), cell(c)) } })
            .put("y", r.y)
            .put("filled", ints(r.filled))

        private fun row(o: JSONObject): RosterRow = RosterRow(
            label = o.optString("label"),
            cells = o.getJSONObject("cells").let { c -> c.keys().asSequence().associate { k -> k.toInt() to cell(c.getString(k)) } },
            guessed = ints(o.optJSONArray("guessed")),
            notes = o.optJSONObject("notes")?.let { n -> n.keys().asSequence().associate { k -> k.toInt() to n.getString(k) } }.orEmpty(),
            unsure = ints(o.optJSONArray("unsure")),
            contested = ints(o.optJSONArray("contested")),
            line = o.optInt("line"),
            outside = o.optJSONObject("outside")?.let { n -> n.keys().asSequence().associate { k -> LocalDate.parse(k) to cell(n.getString(k)) } }.orEmpty(),
            y = o.optInt("y"),
            filled = ints(o.optJSONArray("filled")),
        )
    }
}
