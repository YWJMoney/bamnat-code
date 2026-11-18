package com.bamnat.app.data

import com.bamnat.app.data.db.decodeParts
import com.bamnat.app.data.db.encodeParts
import com.bamnat.core.money.MoneyEntry
import com.bamnat.core.money.MoneyKind
import com.bamnat.core.money.MoneySource
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime

/** 2.0.0 [RVK-13] A ledger line as JSON, for what '자동으로 정리한 것' keeps to put back. Every field goes both ways. */
internal object MoneyEntryJson {
    fun encode(e: MoneyEntry): JSONObject = JSONObject()
        .put("id", e.id).put("date", e.date.toString()).put("kind", e.kind.name).put("amount", e.amount).put("category", e.category)
        .put("note", e.note).put("source", e.source.name).put("merchant", e.merchant).putOpt("time", e.time?.toString())
        .put("card", e.cardLast4).put("reviewed", e.reviewed).putOpt("account", e.accountId).putOpt("to", e.toAccountId)
        .put("memo", e.memo).put("tags", JSONArray(e.tags)).put("parts", encodeParts(e.parts)).put("installments", e.installments)
        .putOpt("fixed", e.fixedId).put("proof", e.proof).put("receipt", e.receipt).put("rate", e.installmentRate)

    fun decode(o: JSONObject): MoneyEntry = MoneyEntry(
        id = o.optLong("id"),
        date = LocalDate.parse(o.getString("date")),
        kind = MoneyKind.valueOf(o.getString("kind")),
        amount = o.getLong("amount"),
        category = o.optString("category"),
        note = o.optString("note"),
        source = runCatching { MoneySource.valueOf(o.optString("source")) }.getOrDefault(MoneySource.NOTIFICATION),
        merchant = o.optString("merchant"),
        time = o.optString("time").takeIf { it.isNotEmpty() }?.let { runCatching { LocalTime.parse(it) }.getOrNull() },
        cardLast4 = o.optString("card"),
        reviewed = o.optBoolean("reviewed", false),
        accountId = if (o.has("account")) o.optLong("account") else null,
        toAccountId = if (o.has("to")) o.optLong("to") else null,
        memo = o.optString("memo"),
        tags = o.optJSONArray("tags")?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty(),
        parts = decodeParts(o.optString("parts")),
        installments = o.optInt("installments", 1),
        fixedId = if (o.has("fixed")) o.optLong("fixed") else null,
        proof = o.optString("proof"),
        receipt = o.optString("receipt"),
        installmentRate = o.optDouble("rate", 0.0),
    )
}
