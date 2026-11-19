package com.bamnat.app.data.db

import androidx.sqlite.db.SupportSQLiteDatabase
import com.bamnat.core.money.CategoryIds
import com.bamnat.core.money.MoneyKind

/**
 * 2.0.0 [RVK-60] (검토 I06ab-01, DB 37) Gives every category, line, split part and repeating line without a fixed id the one its name
 * stands for now: the user's category of that name, else the built-in one, else the id the name itself makes
 * ([CategoryIds.of]). Run by the step to 37 and after a backup from before 37 is restored; rows that have an id keep it.
 */
object CategoryIdFill {
    fun fill(db: SupportSQLiteDatabase) {
        db.query("SELECT kind, name FROM money_categories WHERE uid = ''").use { c ->
            val rows = ArrayList<Pair<String, String>>()
            while (c.moveToNext()) rows += c.getString(0) to c.getString(1)
            rows
        }.forEach { (kind, name) ->
            val k = kindOf(kind) ?: return@forEach
            val uid = CategoryIds.of(k, name)
            db.execSQL("UPDATE money_categories SET uid = ?, role = ?, std = ? WHERE kind = ? AND name = ? AND uid = ''",
                arrayOf<Any?>(uid, CategoryIds.role(uid)?.name.orEmpty(), CategoryIds.standard(uid), kind, name))
        }
        val known = HashMap<Pair<String, String>, String>()
        db.query("SELECT kind, name, uid FROM money_categories").use { c ->
            while (c.moveToNext()) known[c.getString(0) to c.getString(1)] = c.getString(2)
        }
        fun idOf(kind: String, name: String): String? = known[kind to name]?.takeIf { it.isNotEmpty() } ?: kindOf(kind)?.let { CategoryIds.of(it, name) }

        for (table in listOf("money_entries", "fixed_costs")) {
            db.query("SELECT DISTINCT kind, category FROM `$table` WHERE categoryId = ''").use { c ->
                val names = ArrayList<Pair<String, String>>()
                while (c.moveToNext()) names += c.getString(0) to c.getString(1)
                names
            }.forEach { (kind, name) ->
                val id = idOf(kind, name) ?: return@forEach
                db.execSQL("UPDATE `$table` SET categoryId = ? WHERE categoryId = '' AND kind = ? AND category = ?", arrayOf<Any?>(id, kind, name))
            }
        }
        db.query("SELECT id, kind, parts FROM money_entries WHERE parts != '' AND partIds = ''").use { c ->
            val rows = ArrayList<Triple<Long, String, String>>()
            while (c.moveToNext()) rows += Triple(c.getLong(0), c.getString(1), c.getString(2))
            rows
        }.forEach { (id, kind, parts) ->
            val list = decodeParts(parts).map { p -> p.copy(id = idOf(kind, p.category).orEmpty()) }
            // Only the ids are written: the parts text stays as it was, line for line ([encodePartIds] follows [decodeParts]' lines).
            if (list.isNotEmpty() && parts.lines().count { it.indexOf('\t') > 0 } == list.size) {
                db.execSQL("UPDATE money_entries SET partIds = ? WHERE id = ?", arrayOf<Any?>(encodePartIds(list), id))
            }
        }
    }

    private fun kindOf(name: String): MoneyKind? = MoneyKind.entries.firstOrNull { it.name == name }
}
