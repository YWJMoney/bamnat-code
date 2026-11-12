package com.bamnat.app.data

import androidx.room.withTransaction
import com.bamnat.app.data.db.AppDatabase
import com.bamnat.app.data.db.PayrollLineEntity
import com.bamnat.app.data.db.PayrollMonthEntity
import com.bamnat.app.data.db.StaffEntity
import com.bamnat.app.data.db.StaffWorkEntity
import com.bamnat.app.data.db.TeamCellEntity
import com.bamnat.app.data.db.TeamEntity
import com.bamnat.app.data.db.TeamMemberEntity
import com.bamnat.app.data.db.TeamMonthEntity
import com.bamnat.app.data.db.WorkplaceEntity
import com.bamnat.core.boss.Insurance
import com.bamnat.core.boss.LedgerCsv
import com.bamnat.core.boss.LedgerLine
import com.bamnat.core.boss.StaffAllowance
import com.bamnat.core.boss.StaffContract
import com.bamnat.core.boss.StaffMember
import com.bamnat.core.boss.StaffWork
import com.bamnat.core.boss.WageForm
import com.bamnat.core.boss.WorkKind
import com.bamnat.core.boss.Workplace
import com.bamnat.core.team.CellSource
import com.bamnat.core.team.MonthState
import com.bamnat.core.team.Skill
import com.bamnat.core.team.Team
import com.bamnat.core.team.TeamBoard
import com.bamnat.core.team.TeamCell
import com.bamnat.core.team.TeamMember
import com.bamnat.core.team.TeamMonth
import com.bamnat.core.team.TeamShift
import com.bamnat.core.work.StaffShift
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

/**
 * 1.38 [P3-01][TEAM-09][BOSS-01] Phase 3's tables (DB 27) as the core's models: the 리더 팀 근무표 and the 사장님 급여 대장. Everything
 * stays on this phone; the screens that use it are off until their flag (`team.leader`, `boss.payroll`).
 */
class PhaseThreeStore(private val db: AppDatabase, private val clock: () -> Long = com.bamnat.core.time.Today::millis) {
    private val teams get() = db.teamDao()
    private val pay get() = db.payrollDao()

    // ---- 팀 ----

    val teamList: Flow<List<Team>> = teams.observeTeams().map { l -> l.map { it.toModel() } }

    fun members(teamId: String): Flow<List<TeamMember>> = teams.observeMembers(teamId).map { l -> l.map { it.toModel() } }

    fun board(teamId: String, month: YearMonth): Flow<TeamBoard> =
        combine(members(teamId), teams.observeCells(teamId, month.atDay(1).toEpochDay(), month.atEndOfMonth().toEpochDay())) { m, c ->
            TeamBoard.of(month, m, c.map { it.toModel() })
        }

    fun month(teamId: String, month: YearMonth): Flow<Pair<TeamMonth, Map<Pair<String, LocalDate>, Long>>> =
        teams.observeMonth(teamId, month.toString()).map { e ->
            (e?.toModel() ?: TeamMonth(teamId, month)) to (e?.confirmed?.let(::decodeConfirmed) ?: emptyMap())
        }

    suspend fun boardNow(teamId: String, month: YearMonth): TeamBoard = TeamBoard.of(month,
        teams.members().filter { it.teamId == teamId }.map { it.toModel() },
        teams.cells(teamId, month.atDay(1).toEpochDay(), month.atEndOfMonth().toEpochDay()).map { it.toModel() })

    suspend fun saveTeam(t: Team) = teams.upsertTeam(t.toEntity())

    suspend fun teamNow(id: String): Team? = teams.teamById(id)?.toModel()

    /**
     * 1.38 검토(데이터 보존): the team row as it is now, changed by [f] and written back in one go, so 신청 가져오기 and 결원 답 가져오기
     * running at once never write over each other's part of the row with a copy read before.
     */
    suspend fun updateTeam(id: String, f: (Team) -> Team): Team? = db.withTransaction {
        teams.teamById(id)?.toModel()?.let(f)?.also { teams.upsertTeam(it.toEntity()) }
    }

    suspend fun saveMembers(list: List<TeamMember>) = teams.upsertMembers(list.map { it.toEntity() })

    suspend fun removeMember(id: String) = db.withTransaction { teams.deleteMemberCells(id); teams.deleteMemberWishes(id); teams.deleteMember(id) }

    /** [TEAM-19] The month's 희망 휴무. */
    fun wishes(teamId: String, month: YearMonth): Flow<List<com.bamnat.core.team.TeamWish>> =
        teams.observeWishes(teamId, month.atDay(1).toEpochDay(), month.atEndOfMonth().toEpochDay()).map { l ->
            l.mapNotNull { e ->
                val (kind, shift) = com.bamnat.core.team.WishKind.parse(e.kind) ?: return@mapNotNull null
                com.bamnat.core.team.TeamWish(e.memberId, LocalDate.ofEpochDay(e.epochDay), kind, shift, e.receivedAt.takeIf { e.via == VIA_LINK })
            }
        }

    /** [TEAM-19] Wishes in: one a member and day, the newest wins. [via] is LEADER or TEXT. */
    suspend fun addWishes(teamId: String, list: List<com.bamnat.core.team.TeamWish>, via: String) =
        teams.upsertWishes(list.map { com.bamnat.app.data.db.TeamWishEntity(teamId, it.memberId, it.date.toEpochDay(), it.kind.code(it.shiftId), it.at ?: clock(), via) })

    /**
     * 1.38 [P3-L-14] What a 신청 링크 round brought, in place of what it brought before (a request taken back goes too). A request
     * the leader typed or pasted for the same member and day stays: the leader's hand wins.
     */
    suspend fun putLinkWishes(teamId: String, month: YearMonth, list: List<com.bamnat.core.team.TeamWish>) = db.withTransaction {
        val from = month.atDay(1).toEpochDay()
        val to = month.atEndOfMonth().toEpochDay()
        teams.deleteWishesVia(teamId, from, to, VIA_LINK)
        val mine = teams.wishes().filter { it.teamId == teamId && it.epochDay in from..to }.map { it.memberId to it.epochDay }.toSet()
        teams.upsertWishes(list.filter { (it.memberId to it.date.toEpochDay()) !in mine }
            .map { com.bamnat.app.data.db.TeamWishEntity(teamId, it.memberId, it.date.toEpochDay(), it.kind.code(it.shiftId), it.at ?: clock(), VIA_LINK) })
    }

    suspend fun removeWish(memberId: String, date: LocalDate) = teams.deleteWish(memberId, date.toEpochDay())

    suspend fun deleteTeam(id: String) = teams.deleteTeam(id)

    /** [TEAM-11] The board as it is now, the whole month in one write (so 되돌리기 never leaves half a step). */
    suspend fun saveBoard(teamId: String, b: TeamBoard) =
        teams.replaceMonth(teamId, b.month.atDay(1).toEpochDay(), b.month.atEndOfMonth().toEpochDay(), b.cells.values.map { it.toEntity(teamId) })

    /**
     * 1.38 Phase 3 요금: every month this phone ever confirmed, any team, deleted ones included ([freeMonth] keeps the free one after
     * its team is gone), for "첫 달 확정까지 무료".
     */
    suspend fun confirmedMonths(): Set<YearMonth> =
        teams.months().filter { it.confirmedAt != null }.mapNotNull { runCatching { YearMonth.parse(it.month) }.getOrNull() }.toSet() + listOfNotNull(freeMonth())

    /** Where the free month is remembered (null in tests: then only the months on record count). */
    var prefsOf: (() -> android.content.SharedPreferences)? = null
    private val prefs by lazy { prefsOf?.invoke() }
    fun freeMonth(): YearMonth? = prefs?.getString("teamFreeMonth", null)?.let { runCatching { YearMonth.parse(it) }.getOrNull() }
    fun keepFreeMonth(m: YearMonth) { if (freeMonth() == null) prefs?.edit()?.putString("teamFreeMonth", m.toString())?.apply() }

    /** [TEAM-13] 확정: the month CONFIRMED one version up, and the board as it was confirmed, for 바뀐 칸 next time. */
    suspend fun confirm(teamId: String, b: TeamBoard, was: TeamMonth): TeamMonth {
        val m = was.copy(state = MonthState.CONFIRMED, version = was.version + 1, confirmedAt = clock())
        teams.upsertMonth(TeamMonthEntity(teamId, b.month.toString(), m.state.name, m.version, m.confirmedAt, encodeConfirmed(b)))
        return m
    }

    // ---- 사업장 ----

    val workplaces: Flow<List<Workplace>> = pay.observeWorkplaces().map { l -> l.map { it.toModel() } }

    fun staff(placeId: String): Flow<List<StaffMember>> = pay.observeStaff(placeId).map { l -> l.map { it.toModel() } }

    fun work(placeId: String, month: YearMonth): Flow<List<StaffWork>> =
        pay.observeWork(placeId, month.atDay(1).toEpochDay(), month.atEndOfMonth().toEpochDay()).map { l -> l.mapNotNull { it.toModel() } }

    /**
     * 2.0.0 [RVG-02] 검토 PAYU-02: the days a month's ledger needs. The pay engine reads the whole week that holds the 1st and gives
     * 주휴 to the week that ends in the month, so a 결근 on 9/29 belongs to October's 9/28 week; the 6 days on either side come too.
     * Lists of the month's own days filter by date ([MonthDays] does).
     */
    fun workAround(placeId: String, month: YearMonth): Flow<List<StaffWork>> =
        pay.observeWork(placeId, month.atDay(1).minusDays(EDGE_DAYS).toEpochDay(), month.atEndOfMonth().plusDays(EDGE_DAYS).toEpochDay()).map { l -> l.mapNotNull { it.toModel() } }

    /** What a month's ledger is worked out from; [frozen] when it is a closed month read back as it was closed ([close]). */
    data class MonthInputs(val place: Workplace, val staff: List<StaffMember>, val work: List<StaffWork>, val month: YearMonth, val frozen: Boolean) {
        val ledger: com.bamnat.core.boss.Ledger by lazy { com.bamnat.core.boss.PayrollLedger.month(place, staff, month, work) }
    }

    /**
     * 2.0.0 [RVG-01] 검토 PAYU-01: a closed month stays as it was closed. The 대장, 임금대장 CSV, 명세서 and '지난달과 비교' all read
     * these, so raising a 시급 in November no longer moves a closed October. A month closed before 2.0.0 kept no inputs and is worked
     * out from today's rows as before.
     */
    fun inputs(place: Workplace, month: YearMonth): Flow<MonthInputs> = combine(
        staff(place.id), workAround(place.id, month), pay.observeMonth(place.id, month.toString()), pay.observeLines(place.id, month.toString()),
    ) { staff, work, m, lines ->
        (if (m?.state == "CLOSED") frozen(place, lines, month) else null) ?: MonthInputs(place, staff, work, month, false)
    }

    /** One go of [inputs] (신고 달력, 관리 첫 화면). */
    suspend fun ledgerOf(place: Workplace, month: YearMonth): com.bamnat.core.boss.Ledger = inputs(place, month).first().ledger

    /** 1.38 [P3-X-05] Every 연차 day each staff member took, for 연차 남은 날. */
    fun leaveDays(placeId: String): Flow<Map<String, Map<LocalDate, Double>>> =
        pay.observeWork(placeId, Long.MIN_VALUE, Long.MAX_VALUE).map { l ->
            l.mapNotNull { it.toModel() }.filter { it.kind == com.bamnat.core.boss.WorkKind.LEAVE }.groupBy { it.staffId }.mapValues { (_, w) -> w.associate { it.date to 1.0 } }
        }

    fun payrollMonth(placeId: String, month: YearMonth): Flow<PayrollMonthEntity?> = pay.observeMonth(placeId, month.toString())

    suspend fun saveWorkplace(p: Workplace) = pay.upsertWorkplace(p.toEntity(pay.workplaces().firstOrNull { it.id == p.id }?.createdAt ?: clock()))

    suspend fun workplaceNow(id: String): Workplace? = pay.workplaces().firstOrNull { it.id == id }?.toModel()

    /** The place as it is now, changed by [f] in one go (대타 요청 and 사업장 설정 never write over each other). */
    suspend fun updateWorkplace(id: String, f: (Workplace) -> Workplace): Workplace? = db.withTransaction {
        pay.workplaces().firstOrNull { it.id == id }?.let { e -> f(e.toModel()).also { pay.upsertWorkplace(it.toEntity(e.createdAt)) } }
    }

    /** The row as edited; its 근로계약서 stays as the phone has it (only [updateContract] changes that, so a pull and an edit never clash). */
    suspend fun saveStaff(s: StaffMember, placeId: String, sort: Int = 0) = db.withTransaction {
        val kept = pay.contractOf(s.id)
        pay.upsertStaff(s.toEntity(placeId, sort).let { e -> if (kept != null) e.copy(contract = kept) else e })
    }

    /** 1.38 [P3-X-06] The staff member's 근로계약서 as it is now, changed by [f] in one go; null clears it. */
    suspend fun updateContract(staffId: String, f: (StaffContract.Sent?) -> StaffContract.Sent?): StaffContract.Sent? = db.withTransaction {
        val now = pay.contractOf(staffId) ?: return@withTransaction null
        f(decodeContract(now)).also { pay.setContract(staffId, encodeContract(it)) }
    }

    suspend fun deleteStaff(id: String) = pay.deleteStaff(id)

    /** 1.38 [P3-B-11] 출퇴근 기록 of a place in [month] (by the press's day in Korea, a night's 퇴근 the next morning included). */
    fun punches(placeId: String, month: YearMonth): Flow<List<com.bamnat.core.boss.StaffPunch>> {
        val z = com.bamnat.core.boss.StaffClock.ZONE
        val from = month.atDay(1).atStartOfDay(z).toInstant().toEpochMilli()
        val to = month.plusMonths(1).atDay(1).atTime(12, 0).atZone(z).toInstant().toEpochMilli()
        return pay.observePunches(placeId, from, to).map { l -> l.map { com.bamnat.core.boss.StaffPunch(it.staffId, java.time.Instant.ofEpochMilli(it.at), it.kind == "IN") } }
    }

    /** 1.38 [P3-B-11] Keeps [got] and returns the presses this phone had not seen yet (the ones to tell the boss). */
    suspend fun addPunches(staffId: String, got: List<com.bamnat.core.boss.StaffPunch>): List<com.bamnat.core.boss.StaffPunch> {
        val rows = got.map { com.bamnat.app.data.db.StaffPunchEntity(staffId, it.at.toEpochMilli(), if (it.inside) "IN" else "OUT") }
        val ids = pay.insertPunches(rows)
        return got.filterIndexed { i, _ -> ids.getOrNull(i)?.let { it != -1L } == true }
    }

    suspend fun allStaff(): List<StaffMember> = pay.staff().map { it.toModel() }

    suspend fun forgetClock(staffId: String) = pay.forgetClock(staffId)

    /** [BOSS-03] A day that went differently; null puts the day back to the usual week. */
    suspend fun setWork(staffId: String, date: LocalDate, work: StaffWork?) {
        if (work == null) pay.deleteWork(staffId, date.toEpochDay()) else pay.upsertWork(work.toEntity())
    }

    /**
     * [BOSS-04] 대장 닫기: each line as it is now (the 임금대장 row), kept even when the rules move on.
     * 2.0.0 [RVG-01]: each line also keeps what it was worked out from (사업장 계산 칸, the staff member, their days around the month)
     * in `deduct`, a column no screen used, so [inputs] reads a closed month back without a new table.
     */
    suspend fun close(place: Workplace, month: YearMonth, lines: List<LedgerLine>, rateVersion: String, work: Collection<StaffWork> = emptyList()) = pay.close(
        PayrollMonthEntity(place.id, month.toString(), "CLOSED", rateVersion, clock()),
        lines.map { l ->
            PayrollLineEntity(place.id, month.toString(), l.staff.id, l.gross,
                items = JSONArray(LedgerCsv.cells(l)).toString(),
                method = JSONArray().apply { l.lines.forEach { put(JSONObject().put("label", it.label).put("amount", it.amount).put("formula", it.formula)) } }.toString(),
                deduct = snapshot(place, l.staff, work.filter { it.staffId == l.staff.id }), net = null, paidAt = null, slipSentAt = null)
        },
    )

    /** [BOSS-10] 지급 체크·명세서 보냄, per staff member of a month; a month not closed keeps a line of its own for it. */
    fun lines(placeId: String, month: YearMonth): Flow<List<PayrollLineEntity>> = pay.observeLines(placeId, month.toString())

    suspend fun mark(placeId: String, month: YearMonth, line: LedgerLine, paid: Boolean? = null, sent: Boolean? = null) = db.withTransaction {
        val now = clock()
        val was = pay.linesOf(placeId, month.toString()).firstOrNull { it.staffId == line.staff.id }
            ?: PayrollLineEntity(placeId, month.toString(), line.staff.id, line.gross, JSONArray(LedgerCsv.cells(line)).toString(), "[]", "", null, null, null)
        pay.upsertLines(listOf(was.copy(
            paidAt = when (paid) { null -> was.paidAt; true -> was.paidAt ?: now; false -> null },
            slipSentAt = when (sent) { null -> was.slipSentAt; true -> now; false -> null })))
    }

    suspend fun reopen(placeId: String, month: YearMonth, rateVersion: String) =
        pay.upsertMonth(PayrollMonthEntity(placeId, month.toString(), "OPEN", rateVersion, null))

    /** [BOSS-06] SHOP-11 직원 인건비 into a 사업장 once, when there is none yet. The old roster stays where it is. */
    suspend fun adoptRoster(roster: com.bamnat.core.work.StaffRoster, placeId: String): Workplace? = db.withTransaction {
        if (pay.workplaces().isNotEmpty() || roster.staff.isEmpty()) return@withTransaction null
        val (place, staff) = com.bamnat.core.boss.PayrollLedger.fromRoster(roster, placeId)
        pay.upsertWorkplace(place.toEntity(clock()))
        staff.forEachIndexed { i, s -> pay.upsertStaff(s.toEntity(place.id, i)) }
        place
    }

    companion object {
        /** [RVG-02] Days read on either side of a month: a week is 7 days, so 6 reach every week that holds the 1st or the last day. */
        const val EDGE_DAYS = 6L

        /** [RVG-01] What a closed line was worked out from, as JSON; no 출퇴근 열쇠 or 계약서 (not needed to count, and kept out of copies). */
        fun snapshot(place: Workplace, s: StaffMember, work: List<StaffWork>): String = JSONObject().put("v", 1)
            .put("place", PhaseThreeBackup.placeJson(place.toEntity(0)))
            .put("staff", PhaseThreeBackup.staffJson(s.copy(clockToken = null, contract = null).toEntity(place.id, 0)))
            .put("work", JSONArray().apply { work.sortedBy { it.date }.forEach { put(PhaseThreeBackup.workJson(it.toEntity())) } })
            .toString()

        /**
         * [RVG-01] A closed month's inputs read back from its lines; null when any line has none (closed before 2.0.0). The 사업장's
         * name, 대타 요청 and team stay as they are now: only the fields that count are the closed ones.
         */
        fun frozen(live: Workplace, lines: List<PayrollLineEntity>, month: YearMonth): MonthInputs? {
            if (lines.isEmpty()) return null
            val got = lines.map { l -> runCatching { JSONObject(l.deduct).takeIf { it.optInt("v") >= 1 } }.getOrNull() ?: return null }
            val p = runCatching { PhaseThreeBackup.placeFrom(got.first().getJSONObject("place")).toModel() }.getOrNull() ?: return null
            val place = live.copy(fivePlus = p.fivePlus, payday = p.payday, legalBreaks = p.legalBreaks, weekStart = p.weekStart, minuteUnit = p.minuteUnit, industry = p.industry)
            val staff = got.mapNotNull { o -> runCatching { PhaseThreeBackup.staffFrom(o.getJSONObject("staff")).toModel() }.getOrNull() }
            if (staff.size != lines.size) return null
            val work = got.flatMap { o -> o.optJSONArray("work")?.let { a -> (0 until a.length()).mapNotNull { a.optJSONArray(it) } }.orEmpty() }
                .mapNotNull { runCatching { PhaseThreeBackup.workFrom(it).toModel() }.getOrNull() }
            return MonthInputs(place, staff, work, month, true)
        }

        // ---- 팀 rows ----

        const val VIA_LINK = "LINK"

        fun TeamEntity.toModel() = Team(id, name, leaderName, decodeShifts(shifts), createdAt, archived, decodeNeed(need), showTogether, decodeAsk(ask), decodeCalls(calls), decodeSwap(swap))
        fun Team.toEntity() = TeamEntity(id, name, leaderName, encodeShifts(shifts), encodeNeed(need), createdAt, archived, showTogether, encodeAsk(ask), encodeCalls(calls), encodeSwap(swap))

        /** 1.38 [P3-X-01] 결원 메우기 asks; [withToken] false leaves their keys out (a plain backup). */
        fun encodeCalls(l: List<com.bamnat.core.team.TeamCall>, withToken: Boolean = true): String = if (l.isEmpty()) "" else JSONArray().apply {
            l.forEach { c ->
                put(JSONObject().put("id", c.id).putOpt("token", c.token.takeIf { withToken })
                    .put("gap", JSONObject().put("m", c.gap.memberId).put("d", c.gap.date.toString()).put("s", c.gap.shiftId))
                    .put("people", JSONArray(c.people)).put("urls", JSONArray(c.urls))
                    .put("answers", JSONObject(c.answers.mapValues { it.value })).put("seen", JSONArray(c.seen.toList()))
                    .putOpt("taken", c.taken).put("created", c.createdAt).put("expires", c.expiresAt))
            }
        }.toString()

        fun decodeCalls(t: String): List<com.bamnat.core.team.TeamCall> = if (t.isBlank()) emptyList() else runCatching {
            val a = JSONArray(t)
            fun strings(x: JSONArray?) = x?.let { y -> (0 until y.length()).map { y.getString(it) } }.orEmpty()
            (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map { o ->
                val g = o.getJSONObject("gap")
                com.bamnat.core.team.TeamCall(o.getString("id"), o.optString("token").takeIf { it.isNotEmpty() },
                    com.bamnat.core.team.TeamCover.Gap(g.getString("m"), LocalDate.parse(g.getString("d")), g.getLong("s")),
                    strings(o.optJSONArray("people")), strings(o.optJSONArray("urls")),
                    o.optJSONObject("answers")?.let { x -> x.keys().asSequence().associateWith { x.getBoolean(it) } }.orEmpty(),
                    strings(o.optJSONArray("seen")).toSet(), o.optString("taken").takeIf { it.isNotEmpty() }, o.optLong("created"), o.optLong("expires"))
            }
        }.getOrDefault(emptyList())

        /** 1.38 [P3-X-02] A 바꾸기 round as the `team.swap` column keeps it; [withToken] false leaves its key out (a plain backup). */
        fun encodeSwap(r: com.bamnat.core.team.SwapRound?, withToken: Boolean = true): String = if (r == null) "" else JSONObject()
            .put("id", r.id).putOpt("token", r.token.takeIf { withToken }).put("month", r.month.toString())
            .put("people", JSONArray(r.people)).put("links", JSONArray(r.links))
            .put("asks", JSONArray().apply { r.asks.forEach { a -> put(JSONObject().put("f", a.from).put("t", a.to).put("d", a.date.toString()).put("s", a.state.name).put("at", a.at)) } })
            .put("opened", r.openedAt).putOpt("synced", r.syncedAt).toString()

        fun decodeSwap(t: String): com.bamnat.core.team.SwapRound? = if (t.isBlank()) null else runCatching {
            val o = JSONObject(t)
            fun strings(x: JSONArray?) = x?.let { y -> (0 until y.length()).map { y.getString(it) } }.orEmpty()
            val asks = o.optJSONArray("asks")?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it) } }.orEmpty().mapNotNull { x ->
                val st = com.bamnat.core.team.TeamSwap.State.entries.firstOrNull { it.name == x.optString("s") } ?: return@mapNotNull null
                com.bamnat.core.team.TeamSwap.Ask(x.getString("f"), x.getString("t"), LocalDate.parse(x.getString("d")), st, x.optLong("at"))
            }
            com.bamnat.core.team.SwapRound(o.getString("id"), o.optString("token").takeIf { it.isNotEmpty() }, YearMonth.parse(o.getString("month")),
                strings(o.optJSONArray("people")), strings(o.optJSONArray("links")), asks, o.optLong("opened"), if (o.has("synced")) o.optLong("synced") else null)
        }.getOrNull()

        /** 1.38 [P3-L-14] A 신청 받기 round as the `team.ask` column keeps it; [withToken] false leaves its key out (a plain backup). */
        fun encodeAsk(a: com.bamnat.core.team.AskRound?, withToken: Boolean = true): String = if (a == null) "" else JSONObject()
            .put("id", a.id).putOpt("token", a.token.takeIf { withToken }).put("month", a.month.toString())
            .put("rules", JSONObject().put("weekday", a.rules.weekdayCap).put("weekend", a.rules.weekendCap).put("person", a.rules.perPerson)
                .putOpt("closesAt", a.rules.closesAt).put("kinds", JSONArray(a.rules.kinds.map { it.name })).put("firstCome", a.rules.firstCome)
                .put("holidays", JSONArray(a.rules.holidays.map { it.toString() })).put("blocked", JSONArray(a.rules.blocked.map { it.toString() }))
                .put("names", a.rules.showNames))
            // In the order the server numbered the members (its requests say "m": 0, 1, …).
            .put("links", JSONArray().apply { a.links.forEach { (k, v) -> put(JSONArray().put(k).put(v)) } }).put("notes", JSONObject(a.notes)).put("seen", JSONArray(a.seen.toList()))
            .put("closed", a.closed).put("opened", a.openedAt).putOpt("synced", a.syncedAt).toString()

        fun decodeAsk(t: String): com.bamnat.core.team.AskRound? = if (t.isBlank()) null else runCatching {
            val o = JSONObject(t)
            val r = o.getJSONObject("rules")
            fun strings(a: JSONArray?) = a?.let { x -> (0 until x.length()).map { x.getString(it) } }.orEmpty()
            fun map(m: JSONObject?) = m?.let { x -> x.keys().asSequence().associateWith { x.getString(it) } }.orEmpty()
            com.bamnat.core.team.AskRound(
                id = o.getString("id"), token = o.optString("token").takeIf { it.isNotEmpty() }, month = YearMonth.parse(o.getString("month")),
                rules = com.bamnat.core.team.AskRules(
                    weekdayCap = r.optInt("weekday", 2), weekendCap = r.optInt("weekend", 3), perPerson = r.optInt("person", 0),
                    closesAt = if (r.has("closesAt") && !r.isNull("closesAt")) r.getLong("closesAt") else null,
                    kinds = strings(r.optJSONArray("kinds")).mapNotNull { k -> com.bamnat.core.team.WishKind.entries.firstOrNull { it.name == k } }.toSet(),
                    firstCome = r.optBoolean("firstCome", true),
                    holidays = strings(r.optJSONArray("holidays")).map(LocalDate::parse).toSet(),
                    blocked = strings(r.optJSONArray("blocked")).map(LocalDate::parse).toSet(),
                    showNames = r.optBoolean("names")),
                links = o.optJSONArray("links")?.let { x -> (0 until x.length()).mapNotNull { x.optJSONArray(it) }.associateTo(LinkedHashMap()) { it.getString(0) to it.getString(1) } }.orEmpty(),
                notes = map(o.optJSONObject("notes")), seen = strings(o.optJSONArray("seen")).toSet(),
                closed = o.optBoolean("closed"), openedAt = o.optLong("opened"), syncedAt = if (o.has("synced") && !o.isNull("synced")) o.getLong("synced") else null)
        }.getOrNull()
        fun TeamMemberEntity.toModel() = TeamMember(id, teamId, name, role, runCatching { Skill.valueOf(skill) }.getOrDefault(Skill.REG),
            colorArgb, sort, linkId, linkToken, active, hiredOn?.let(LocalDate::ofEpochDay))
        fun TeamMember.toEntity() = TeamMemberEntity(id, teamId, name, role, skill.name, colorArgb, sort, linkId, linkToken, active, hiredOn?.toEpochDay())
        fun TeamCellEntity.toModel() = TeamCell(memberId, LocalDate.ofEpochDay(epochDay), shiftId,
            runCatching { CellSource.valueOf(source) }.getOrDefault(CellSource.MANUAL), locked)
        fun TeamCell.toEntity(teamId: String) = TeamCellEntity(teamId, memberId, date.toEpochDay(), shiftId, source.name, locked)
        fun TeamMonthEntity.toModel() = TeamMonth(teamId, YearMonth.parse(month),
            runCatching { MonthState.valueOf(state) }.getOrDefault(MonthState.DRAFT), version, confirmedAt)

        fun encodeShifts(l: List<TeamShift>): String = JSONArray().apply {
            l.forEach { s ->
                put(JSONObject().put("id", s.id).put("label", s.label).put("name", s.name).put("color", s.colorArgb).put("off", s.isOff)
                    .putOpt("start", s.start?.toString()).putOpt("end", s.end?.toString()))
            }
        }.toString()

        fun decodeShifts(text: String): List<TeamShift> = runCatching {
            JSONArray(text).let { a -> (0 until a.length()).map { a.getJSONObject(it) } }.map { o ->
                TeamShift(o.getLong("id"), o.optString("label"), o.optString("name"), o.optLong("color"), o.optBoolean("off"),
                    o.optString("start").takeIf { it.isNotEmpty() }?.let(LocalTime::parse), o.optString("end").takeIf { it.isNotEmpty() }?.let(LocalTime::parse))
            }
        }.getOrDefault(emptyList())

        fun encodeNeed(m: Map<Long, Int>): String = if (m.isEmpty()) "" else JSONObject().apply { m.forEach { (k, v) -> put(k.toString(), v) } }.toString()
        fun decodeNeed(t: String): Map<Long, Int> = if (t.isBlank()) emptyMap() else runCatching {
            JSONObject(t).let { o -> o.keys().asSequence().associate { it.toLong() to o.getInt(it) } }
        }.getOrDefault(emptyMap())

        fun encodeConfirmed(b: TeamBoard): String = JSONObject().apply {
            b.cells.values.forEach { c -> put("${c.memberId}:${c.date.toEpochDay()}", c.shiftId) }
        }.toString()

        fun decodeConfirmed(t: String): Map<Pair<String, LocalDate>, Long> = if (t.isBlank()) emptyMap() else runCatching {
            JSONObject(t).let { o ->
                o.keys().asSequence().mapNotNull { k ->
                    val i = k.lastIndexOf(':').takeIf { it > 0 } ?: return@mapNotNull null
                    (k.substring(0, i) to LocalDate.ofEpochDay(k.substring(i + 1).toLong())) to o.getLong(k)
                }.toMap()
            }
        }.getOrDefault(emptyMap())

        // ---- 사업장 rows ----

        fun WorkplaceEntity.toModel() = Workplace(id, name, fivePlus, payday, legalBreaks, DayOfWeek.of(weekStart.coerceIn(1, 7)), teamId, minuteUnit.coerceIn(1, 60), decodeCalls(calls), industry)
        fun Workplace.toEntity(createdAt: Long) = WorkplaceEntity(id, name, fivePlus, payday, legalBreaks, weekStart.value, teamId, createdAt, minuteUnit, encodeCalls(calls), industry)

        fun StaffEntity.toModel() = StaffMember(
            id = id, name = name, form = runCatching { WageForm.valueOf(form) }.getOrDefault(WageForm.HOUR), base = base,
            allowances = decodeAllowances(allowances), hiredOn = hiredOn?.let(LocalDate::ofEpochDay), leftOn = leftOn?.let(LocalDate::ofEpochDay),
            birth = birth?.let(LocalDate::ofEpochDay), job = job, insurance = decodeInsurance(insurance), weeklyHolidayPay = weeklyHolidayPay,
            weekly = decodeWeekly(weekly), memberId = memberId, dayRates = decodeDayRates(weekly), leaves = decodeLeaves(weekly),
            clockId = clockId, clockToken = clockToken, contract = decodeContract(contract),
        )

        fun StaffMember.toEntity(placeId: String, sort: Int) = StaffEntity(
            id, placeId, name, form.name, base, encodeAllowances(allowances), hiredOn?.toEpochDay(), leftOn?.toEpochDay(), birth?.toEpochDay(),
            job, encodeInsurance(insurance), weeklyHolidayPay, encodeWeekly(weekly, dayRates, leaves), memberId, sort, clockId, clockToken,
            encodeContract(contract),
        )

        fun encodeContract(c: StaffContract.Sent?): String = c?.let {
            JSONObject().put("title", it.title).put("items", JSONArray().apply { it.items.forEach { i -> put(JSONObject().put("t", i.title).put("b", i.body)) } })
                .put("sent", it.sentAt).putOpt("id", it.linkId).putOpt("token", it.token).putOpt("url", it.url).putOpt("expires", it.expiresAt)
                .putOpt("seen", it.seenAt).putOpt("name", it.signedName).putOpt("signed", it.signedAt).put("paper", it.paper)
                .apply { if (it.past.isNotEmpty()) put("past", JSONArray().apply { it.past.forEach { p -> put(JSONObject(encodeContract(p))) } }) }.toString()
        }.orEmpty()

        fun decodeContract(t: String): StaffContract.Sent? = if (t.isBlank()) null else runCatching {
            val o = JSONObject(t)
            fun str(k: String) = o.optString(k).takeIf { o.has(k) && !o.isNull(k) && it.isNotEmpty() }
            fun num(k: String) = if (o.has(k) && !o.isNull(k)) o.getLong(k) else null
            val a = o.optJSONArray("items") ?: JSONArray()
            StaffContract.Sent(o.optString("title"), (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map { StaffContract.Item(it.optString("t"), it.optString("b")) },
                o.optLong("sent"), str("id"), str("token"), str("url"), num("expires"), num("seen"), str("name"), num("signed"), o.optBoolean("paper"),
                o.optJSONArray("past")?.let { p -> (0 until p.length()).mapNotNull { p.optJSONObject(it)?.let { x -> decodeContract(x.toString()) } } }.orEmpty())
        }.getOrNull()

        fun StaffWorkEntity.toModel(): StaffWork? = runCatching {
            StaffWork(staffId, LocalDate.ofEpochDay(epochDay), WorkKind.valueOf(kind),
                startMinute?.let { LocalTime.ofSecondOfDay(it * 60L % 86_400) }, endMinute?.let { LocalTime.ofSecondOfDay(it * 60L % 86_400) })
        }.getOrNull()

        fun StaffWork.toEntity() = StaffWorkEntity(staffId, date.toEpochDay(), kind.name, start?.let { it.hour * 60 + it.minute }, end?.let { it.hour * 60 + it.minute })

        fun encodeAllowances(l: List<StaffAllowance>) = JSONArray().apply {
            l.forEach { put(JSONObject().put("name", it.name).put("amount", it.amount).put("perDay", it.perDay)) }
        }.toString()

        fun decodeAllowances(t: String): List<StaffAllowance> = runCatching {
            JSONArray(t).let { a -> (0 until a.length()).map { a.getJSONObject(it) } }
                .map { StaffAllowance(it.optString("name"), it.optLong("amount").coerceAtLeast(0), it.optBoolean("perDay")) }
        }.getOrDefault(emptyList())

        fun encodeInsurance(i: Insurance) = JSONObject().put("pension", i.pension).put("health", i.health).put("employment", i.employment).put("tax", i.tax.name).toString()
        fun decodeInsurance(t: String): Insurance = runCatching {
            JSONObject(t).let { Insurance(it.optBoolean("pension"), it.optBoolean("health"), it.optBoolean("employment"),
                runCatching { com.bamnat.core.boss.StaffTax.valueOf(it.optString("tax", "WAGE")) }.getOrDefault(com.bamnat.core.boss.StaffTax.WAGE)) }
        }.getOrDefault(Insurance())

        /** The week as "1".."7" → [start, end] minutes, and 1.38 요일별 시급 as "r1".."r7" → won beside it (no DB change). */
        /** 1.38: the 요일별 시급 ("r1".."r7") and 휴직 기간 ("leaves": [[start, end|null]] epoch days) ride in the same column, so no new table. */
        fun encodeWeekly(m: Map<DayOfWeek, StaffShift>, rates: Map<DayOfWeek, Long> = emptyMap(), leaves: List<com.bamnat.core.boss.StaffLeave> = emptyList()) = JSONObject().apply {
            m.forEach { (d, s) -> put(d.value.toString(), JSONArray().put(s.start.hour * 60 + s.start.minute).put(s.end.hour * 60 + s.end.minute)) }
            rates.forEach { (d, r) -> put("r${d.value}", r) }
            if (leaves.isNotEmpty()) put("leaves", JSONArray().apply { leaves.forEach { put(JSONArray().put(it.start.toEpochDay()).put(it.end?.toEpochDay() ?: JSONObject.NULL)) } })
        }.toString()

        fun decodeLeaves(t: String): List<com.bamnat.core.boss.StaffLeave> = runCatching {
            val a = JSONObject(t).optJSONArray("leaves") ?: return@runCatching emptyList()
            (0 until a.length()).mapNotNull { i -> a.optJSONArray(i)?.let { p ->
                com.bamnat.core.boss.StaffLeave(LocalDate.ofEpochDay(p.getLong(0)), if (p.isNull(1)) null else LocalDate.ofEpochDay(p.getLong(1))) } }
        }.getOrDefault(emptyList())

        fun decodeDayRates(t: String): Map<DayOfWeek, Long> = runCatching {
            JSONObject(t).let { o -> (1..7).mapNotNull { d -> o.optLong("r$d", -1).takeIf { it > 0 }?.let { DayOfWeek.of(d) to it } }.toMap() }
        }.getOrDefault(emptyMap())

        fun decodeWeekly(t: String): Map<DayOfWeek, StaffShift> = runCatching {
            JSONObject(t).let { o ->
                o.keys().asSequence().mapNotNull { k ->
                    val a = o.optJSONArray(k) ?: return@mapNotNull null
                    val d = k.toIntOrNull()?.takeIf { it in 1..7 } ?: return@mapNotNull null
                    DayOfWeek.of(d) to StaffShift(LocalTime.of(a.getInt(0) / 60 % 24, a.getInt(0) % 60), LocalTime.of(a.getInt(1) / 60 % 24, a.getInt(1) % 60))
                }.toMap()
            }
        }.getOrDefault(emptyMap())
    }
}
