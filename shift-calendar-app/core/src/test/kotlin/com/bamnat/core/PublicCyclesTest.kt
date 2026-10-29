package com.bamnat.core

import com.bamnat.core.pay.Occupation
import com.bamnat.core.pay.PayProfile
import com.bamnat.core.pay.Unknowns
import com.bamnat.core.pay.WorkForm
import com.bamnat.core.plaza.CycleOrders
import com.bamnat.core.plaza.CycleTrust
import com.bamnat.core.plaza.OrderState
import com.bamnat.core.plaza.PublicCycles
import com.bamnat.core.plaza.PublicPay
import com.bamnat.core.plaza.PublicSetup
import com.bamnat.core.remote.RemoteFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.LocalTime

/** [SPAT-01][SPAT-02][SPAT-03][SPAT-04] The public shift cycles: every row read, converted where it can be, searched. */
class PublicCyclesTest {
    private val all = PublicCycles.all

    @Test fun everyRowOfTheResearchIsRead() {
        assertEquals(148, all.size)
        assertEquals(148, all.map { it.id }.toSet().size)
        val f = RemoteFiles.bundled(RemoteFiles.CYCLES)!!
        assertEquals("cycles", f.kind)
        assertTrue(RemoteFiles.CYCLES in RemoteFiles.KINDS)
        // The same 148 rows as the CSV the file was built from, column by column.
        val csv = File("../tools/data/company-cycles.csv").readLines(Charsets.UTF_8).drop(1).filter { it.isNotBlank() }
        assertEquals(148, csv.size)
        all.zip(csv).forEach { (c, line) -> assertTrue("${c.id} ${c.display}", line.startsWith(c.display.let { if (',' in it) "\"$it" else it })) }
    }

    @Test fun convertedAndFormOnlyCountsAreThePlans() {
        // 계획/1.26-AC-개발계획.md 2장: 36 spelled out, 28 filled with the usual order of a count, 84 형태만 (6 monthly rosters).
        assertEquals(36, all.count { it.orderState == OrderState.EXACT })
        assertEquals(28, all.count { it.orderState == OrderState.USUAL })
        assertEquals(84, all.count { it.orderState == OrderState.NONE })
        assertEquals(6, all.count { it.roster })
        assertEquals(139, all.count { it.shifts.isNotEmpty() })
        all.forEach { c ->
            // Every working code of an order has a shift with times.
            c.codes.filter { it in "DENX" }.forEach { code -> assertTrue("${c.id} $code", c.shifts.any { it.code == code }) }
            if (c.weekly) assertEquals(c.id, 7, c.codes.length)
            assertEquals(c.id, c.hasOrder, c.orderState != OrderState.NONE)
        }
    }

    @Test fun ordersReadAsTheSourceSaysThem() {
        fun row(company: String, site: String = "") = all.first { it.company == company && site in it.site }
        // 2222 = 주2·휴2·야2·휴2 (k-labor 코드표).
        assertEquals("DDOONNOO", row("GS EPS").codes)
        assertEquals(OrderState.EXACT, row("GS EPS").orderState)
        assertEquals("DDDDOOOONNNNOOOO", row("유한킴벌리").codes)
        assertEquals("주주주주휴휴휴휴야야야야휴휴휴휴", CycleOrders.spoken(row("유한킴벌리").codes))
        // 당비비 and 주야비휴.
        assertEquals("XBO", all.first { it.orderText.startsWith("당(24시간)") }.codes)
        assertEquals("DNBO", all.first { it.company.startsWith("경찰청") }.codes)
        // 4조3교대 06/14/22 times, 형태만 when no order is given.
        val c = all.first { it.formKey == "4조3교대" && it.orderText == "5근2휴" }
        assertEquals(OrderState.NONE, c.orderState)
        assertEquals(listOf(LocalTime.of(6, 0), LocalTime.of(14, 0), LocalTime.of(22, 0)), c.shifts.map { it.start })
        assertFalse(c.shifts.any { it.usual })
        // A count (6근2휴) is the usual order, for the user to confirm.
        val six = all.first { it.formKey == "4조3교대" && it.orderText == "6근2휴 (8일)" }
        assertEquals(OrderState.USUAL, six.orderState)
        assertEquals("DDEENNOO", six.codes)
        // 12시간 only: the usual 07~19 / 19~07, marked usual.
        assertTrue(row("GS EPS").shifts.all { it.usual })
    }

    @Test fun trustKeepsItsKinds() {
        assertTrue(all.any { it.linkCheck })
        assertEquals(15, all.count { it.linkCheck })
        assertTrue(all.filter { it.trustText == "커뮤니티" }.all { it.weak && it.trust == setOf(CycleTrust.COMMUNITY) })
        val both = all.first { it.trustText == "기사+노무사이트" }
        assertEquals(setOf(CycleTrust.ARTICLE, CycleTrust.LABOR), both.trust)
        assertEquals("기사 · 노무사이트", both.trustLine)
        assertTrue(all.first { it.company == "SK하이닉스" }.partner)
        assertTrue(all.all { it.source.startsWith("http") })
    }

    @Test fun searchFindsByNameChoseongIndustryAndForm() {
        assertEquals("유한킴벌리", PublicCycles.search("유한").first().company)
        assertEquals("유한킴벌리", PublicCycles.search("(주) 유한 킴벌리").first().company)
        assertEquals("포스코", PublicCycles.search("ㅍㅅㅋ").first().company)
        assertTrue("포스코 광양", PublicCycles.search("포스코 광양").let { r -> r.isNotEmpty() && r.all { "광양" in it.site } })
        // The form as written, with or without a space; a row moving to 4조2교대 (3조2교대 → 4조2교대 예정) is found too.
        val four = PublicCycles.search("4조 2교대")
        assertEquals(41, four.count { it.formKey == "4조2교대" })
        assertTrue(four.all { it.formKey == "4조2교대" || "4조2교대" in PublicCycles.norm(it.form + it.display) })
        assertEquals(four, PublicCycles.search("4조2교대"))
        assertEquals(all.count { it.industry == "발전" }, PublicCycles.search("", industry = "발전").size)
        assertEquals(7, PublicCycles.search("반도체", form = "4조3교대").size)
        assertTrue(PublicCycles.search("가").isEmpty())
        assertTrue(PublicCycles.search("없는회사이름").isEmpty())
        assertEquals("제조", PublicCycles.industries.first())
        assertEquals("4조2교대", PublicCycles.forms.first().first)
    }

    @Test fun formOnlyRowsGetTheUsualOrdersOfTheirForm() {
        val c = all.first { it.formKey == "4조2교대" && it.orderState == OrderState.NONE }
        val cands = CycleOrders.candidates(c)
        assertEquals(listOf("DDOONNOO", "DNBO", "DDDOOONNNOOO", "DDDDOOOONNNNOOOO", "DDNNOOOO"), cands.map { it.codes })
        // A row with its own order has it first, once.
        val own = all.first { it.company == "GS EPS" }
        assertEquals("DDOONNOO", CycleOrders.candidates(own).first().codes)
        assertEquals(1, CycleOrders.candidates(own).count { it.codes == "DDOONNOO" })
        assertTrue(CycleOrders.candidates(all.first { it.roster }).isEmpty())
        // The editor turns one day and adds one past the end.
        assertEquals("DNNO", CycleOrders.set("DDNO", 1, 'N'))
        assertEquals("DDNOO", CycleOrders.set("DDNO", 4, 'O'))
        assertEquals(listOf('D', 'N', 'O'), CycleOrders.choices(c, "DDOONNOO"))
    }

    @Test fun aRowBecomesTheShiftsAndOrderOfACompanyFile() {
        val c = all.first { it.company == "GS EPS" }
        val plan = PublicSetup.plan(c, c.codes)
        assertEquals(listOf("주간", "야간", "휴무"), plan.types.map { it.name })
        assertEquals(listOf("주간", "주간", "휴무", "휴무", "야간", "야간", "휴무", "휴무"), plan.order)
        assertTrue(plan.types.last().isOff)
        // 당비비 brings 비번 too.
        val x = all.first { it.orderText.startsWith("당(24시간)") }
        assertEquals(listOf("당번", "휴무", "비번"), PublicSetup.plan(x, x.codes).types.map { it.name })
        // A weekly row is days of the week.
        val w = all.first { it.weekly && it.codes == "DDDDDOO" }
        val wp = PublicSetup.plan(w, w.codes)
        assertTrue(wp.order.isEmpty())
        assertEquals(7, wp.weekly.size)
        // A 형태만 row with a picked order fills missing shifts with the usual ones.
        val none = all.first { it.formKey == "4조3교대" && it.shifts.isEmpty().not() && it.orderState == OrderState.NONE }
        assertEquals(3, PublicSetup.plan(none, "DDEENNOO").types.count { !it.isOff })
    }

    @Test fun theNoteSaysWhatTheUserChecked() {
        val none = all.first { it.formKey == "4조2교대" && it.orderState == OrderState.NONE && it.asOf.isNotEmpty() }
        val n = PublicSetup.note(none, "DDOONNOO", picked = true)
        assertTrue(n, n.startsWith("공개 자료 확인·고침: 4조 2교대 · 순서 주주휴휴야야휴휴 (공개 순서 없음, 직접 골랐어요)"))
        val own = all.first { it.company == "GS EPS" }
        assertTrue(PublicSetup.note(own, "DNBO", true).contains("공개 자료와 달라서 고쳤어요"))
        assertTrue(PublicSetup.note(PublicCycles.provided("예시정밀", "4조2교대"), "DDOONNOO", true).startsWith("처음 제공: 4조 2교대 · 순서 주주휴휴야야휴휴"))
        // No 5-digit number, mail or link: the report's own checks let it through.
        all.forEach { c -> assertFalse(c.id, Regex("\\d{5,}|@|http").containsMatchIn(PublicSetup.note(c, c.codes.ifEmpty { "DDOONNOO" }, true))) }
    }

    @Test fun payStartsFromTheIndustrysUsualWay() {
        val factory = all.first { it.company == "GS EPS" }
        assertEquals(WorkForm.FOUR_TEAM, PublicPay.form(factory))
        assertEquals(Occupation.MANUFACTURING, PublicPay.occupation(factory))
        assertEquals(WorkForm.PUBLIC, PublicPay.form(all.first { it.company.startsWith("경찰청") }))
        assertEquals(Occupation.POLICE, PublicPay.occupation(all.first { it.company.startsWith("경찰청") }))
        // 소방 당비비 is paid by 공무원수당 규정; a 24시간 격일 경비 is 맞교대.
        assertEquals(WorkForm.PUBLIC, PublicPay.form(all.first { it.orderText.startsWith("당(24시간)") }))
        assertEquals(WorkForm.DAY_ON_DAY_OFF, PublicPay.form(all.first { it.orderText.startsWith("근무·휴무 (2일)") }))
        val since = LocalDate.of(2026, 10, 1)
        val p = PublicPay.prefill(PayProfile(occupation = Occupation.MANUFACTURING, wageType = com.bamnat.core.pay.WageType.MONTHLY, base = com.bamnat.core.pay.ValueTimeline(listOf(com.bamnat.core.pay.DatedValue(since, 3_000_000)))), factory, since)
        assertEquals(listOf("교대 수당", "야간 근무 수당"), p.items.map { it.name })
        assertTrue(p.items.all { Unknowns.item(it.id) in p.estimatedFields })
        // Prefilling again adds nothing twice, and keeps what the user set.
        val again = PublicPay.prefill(p, factory, since)
        assertEquals(p.items, again.items)
        assertNotNull(PublicPay.usualItems(factory).firstOrNull())
    }
}
