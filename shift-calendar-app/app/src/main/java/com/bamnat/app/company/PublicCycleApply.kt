package com.bamnat.app.company

import com.bamnat.app.AppContainer
import com.bamnat.app.data.BackupManager
import com.bamnat.app.data.CompanyShare
import com.bamnat.core.plaza.CycleOrders
import com.bamnat.core.plaza.CycleShift
import com.bamnat.core.plaza.OrderState
import com.bamnat.core.plaza.PublicCycle
import com.bamnat.core.plaza.PublicCycles
import com.bamnat.core.plaza.PublicSetup
import com.bamnat.core.plaza.ShareAsk
import com.bamnat.core.setup.SetupDraft
import com.bamnat.core.time.Today
import org.json.JSONArray
import org.json.JSONObject

/**
 * [SPAT-03][SPAT-05] The public row this phone's cycle came from, and how far the way on has gone: applied → pay set →
 * put on the plaza. [cycleId] is "" for a first provider's own company (검색에 없는 회사). [codes] is the order the user
 * ended with, [picked] whether they chose or changed it rather than taking the row's own. On this phone only, like the
 * other invitation memories (X-D5): a backup does not carry it.
 */
data class PublicUse(
    val cycleId: String,
    val company: String,
    val formKey: String,
    val codes: String,
    val picked: Boolean,
    val at: Long,
    /** "다음에" on the pay nudge: it does not come back. */
    val payClosed: Boolean = false,
    val inviteLaters: Int = 0,
    val inviteDue: Long = 0,
    val inviteNever: Boolean = false,
    /** The report sent from it, and the plaza page it went to. */
    val sentId: String? = null,
    val companyId: String? = null,
) {
    val cycle: PublicCycle?
        get() = PublicCycles.byId(cycleId) ?: if (cycleId.isEmpty()) PublicCycles.provided(company, formKey) else null

    /** [SPAT-05] The invitation may show: not sent, not ended with 그만 볼게요, past its 14·30·90-day wait. */
    fun inviteDue(now: Long = Today.millis()): Boolean = sentId == null && ShareAsk.due(inviteNever, inviteDue, now, usesCompanySetup = false)

    fun later(now: Long = Today.millis()): PublicUse = copy(inviteLaters = inviteLaters + 1, inviteDue = ShareAsk.nextDue(inviteLaters + 1, now))

    fun json(): String = JSONObject()
        .put("cycle", cycleId).put("company", company).put("form", formKey).put("codes", codes).put("picked", picked).put("at", at)
        .put("payClosed", payClosed).put("laters", inviteLaters).put("due", inviteDue).put("never", inviteNever)
        .putOpt("sent", sentId).putOpt("companyId", companyId).toString()

    companion object {
        fun read(s: String?): PublicUse? = s?.let {
            runCatching {
                val o = JSONObject(it)
                PublicUse(
                    o.getString("cycle"), o.getString("company"), o.optString("form"), o.optString("codes"), o.optBoolean("picked"), o.optLong("at"),
                    o.optBoolean("payClosed"), o.optInt("laters"), o.optLong("due"), o.optBoolean("never"),
                    o.optString("sent").takeIf { s -> s.isNotEmpty() }, o.optString("companyId").takeIf { s -> s.isNotEmpty() },
                )
            }.getOrNull()
        }
    }
}

/**
 * [SPAT-03] A public row (or a first provider's company) with the order the user ended with, as a company settings file,
 * so it is applied the way a coworker's file is (CompanyShare.apply: shifts merge by name, the order from today with the
 * past kept) and read by the first setup the same way. It names the company; it holds no pay, date or person.
 */
object PublicCycleFiles {
    fun text(cycle: PublicCycle, codes: String): String {
        val plan = PublicSetup.plan(cycle, codes)
        val root = JSONObject()
            .put("app", BackupManager.APP_ID).put("format", CompanyShare.FORMAT).put("v", CompanyShare.BASE_VERSION)
            .put("company", cycle.company.take(40)).put("public", cycle.id)
            .put("shiftTypes", JSONArray(plan.types.map { t ->
                JSONObject().put("name", t.name).put("label", t.label).put("color", t.colorArgb)
                    .putOpt("start", t.start?.toString()).putOpt("end", t.end?.toString()).put("break", t.breakMinutes).put("off", t.isOff)
            }))
        if (cycle.roster) root.put("roster", true)
        if (plan.order.isNotEmpty()) root.put("rotation", JSONObject().put("steps", JSONArray(plan.order.map { it ?: JSONObject.NULL })))
        if (plan.weekly.size == 7) root.put("weekly", JSONArray(plan.weekly.map { it ?: JSONObject.NULL }))
        return root.toString()
    }

    /** Whether the order the user ended with is theirs rather than the row's own as the source spells it. */
    fun picked(cycle: PublicCycle, codes: String): Boolean = codes != cycle.codes || cycle.orderState != OrderState.EXACT

    /**
     * [SPAT-03] Puts [cycle] with [codes] on the main calendar from today, today being day [todayStep] of the order, and
     * remembers where it came from for the pay and plaza steps. The caller asks [com.bamnat.app.ui.guide.WriteGate] first.
     */
    suspend fun apply(c: AppContainer, cycle: PublicCycle, codes: String, todayStep: Int?): CompanyShare.Applied {
        val r = c.companyShare.apply(text(cycle, codes), rotation = true, todayStep = todayStep)
        remember(c, cycle, codes)
        c.telemetry.event("public_cycle_apply", if (cycle.id.isEmpty()) "provided" else cycle.orderState.name.lowercase())
        return r
    }

    /**
     * [SPAT-03] The order a first setup saved, back in the row's codes: each day's shift by its name (the row's own, or the
     * usual one of its code), 휴무/비번 and any other off kind as 휴/비. A shift the user renamed counts as a day of work.
     */
    fun codesOf(cycle: PublicCycle, d: SetupDraft): String {
        val weekly = d.rotation.isEmpty() && (cycle.weekly || cycle.formKey in CycleOrders.WEEKLY_FORMS)
        val ids = if (weekly) d.weekly else d.rotation
        if (ids.isEmpty() || (weekly && ids.all { it == null })) return ""
        val three = cycle.formKey in PublicCycle.THREE_FORMS
        val byName = PublicCycle.WORK.associateBy { CycleShift.usual(it, three).name } + cycle.shifts.associate { it.name to it.code }
        return ids.joinToString("") { id ->
            val t = d.type(id)
            when {
                t == null -> "O"
                t.name == PublicSetup.AFTER -> "B"
                t.isOff -> "O"
                else -> (byName[t.name] ?: 'D').toString()
            }
        }
    }

    fun remember(c: AppContainer, cycle: PublicCycle, codes: String) {
        // One invitation: this row's way on, not the hand-made one's as well.
        c.companyStore.setDraft(null)
        c.companyStore.setHandMade(false)
        c.companyStore.setPublicUse(PublicUse(cycle.id, cycle.company, cycle.formKey, codes, picked(cycle, codes), Today.millis()))
    }
}
