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
        assertEquals(161, all.size)
        assertEquals(161, all.map { it.id }.toSet().size)
        val f = RemoteFiles.bundled(RemoteFiles.CYCLES)!!
        assertEquals("cycles", f.kind)
        assertTrue(RemoteFiles.CYCLES in RemoteFiles.KINDS)
        // The same 161 rows (148 of 2026-10-25 + 6 checked in 1.38 [CYC-01] + 7 [CYC-05]) as the CSV the file was built from, column by column.
        val csv = File("../tools/data/company-cycles.csv").readLines(Charsets.UTF_8).drop(1).filter { it.isNotBlank() }
        assertEquals(161, csv.size)
        all.zip(csv).forEach { (c, line) -> assertTrue("${c.id} ${c.display}", line.startsWith(c.display.let { if (',' in it) "\"$it" else it })) }
    }

    @Test fun convertedAndFormOnlyCountsAreThePlans() {
        // 계획/1.26-AC-개발계획.md 2장: 36 spelled out, 28 filled with the usual order of a count, 84 형태만 (6 monthly rosters).
        // [CYC-01] 1.38 added 4 spelled out (관제·교정 변형 4부제·소방 당비비·서울교통공사), 1 usual (교정 4부제), 1 roster (병원).
        // [CYC-05] 1.38 added 5 spelled out (지구대·검역·세관·SR·대구 택시 8부제) and 2 형태만 (서울 구급대·서울 택시).
        assertEquals(45, all.count { it.orderState == OrderState.EXACT })
        assertEquals(29, all.count { it.orderState == OrderState.USUAL })
        assertEquals(87, all.count { it.orderState == OrderState.NONE })
        assertEquals(7, all.count { it.roster })
        assertEquals(149, all.count { it.shifts.isNotEmpty() })
        all.forEach { c ->
            // Every working code of an order has a shift with times.
            c.codes.filter { it in "DENX" }.forEach { code -> assertTrue("${c.id} $code", c.shifts.any { it.code == code }) }
            if (c.weekly) assertEquals(c.id, 7, c.codes.length)
            assertEquals(c.id, c.hasOrder, c.orderState != OrderState.NONE)
        }
    }

    @Test fun theRowsAddedIn138ReadAsTheirSources() {
        // [CYC-01] 서울신문 2017-11-06: 변형 4부제는 주간·야간·비번·주간·주간·야간·비번·휴무.
        val variant = all.first { it.form == "변형 4부제" }
        assertEquals("DNBDDNBO", variant.codes)
        assertEquals(OrderState.EXACT, variant.orderState)
        // 뉴시스 2019-04-24: 3조1교대는 당번·비번·비번.
        assertEquals("XBO", all.first { it.industry == "소방" && it.form == "3조1교대" }.codes)
        // 광진구 공고: 주간 08-18 · 야간 18-08.
        val cctv = all.first { it.company == "광진구 CCTV 통합관제센터" }
        assertEquals("DNBO", cctv.codes)
        assertEquals(listOf("08:00", "18:00"), cctv.shifts.map { it.start.toString() })
    }

    @Test fun theRowsOfCyc05ReadAsTheirSources() {
        // 경북매일 2020-02-20: 주간·야간·비번·휴무, 야간 17시~다음날 8시.
        val police = all.first { it.company == "포항남부경찰서 상대지구대" }
        assertEquals("DNBO", police.codes)
        assertEquals(LocalTime.of(17, 0), police.shifts.first { it.code == 'N' }.start)
        assertFalse(police.shifts.first { it.code == 'N' }.usual)
        // 파이낸셜뉴스 2015-08-05: 24시간 일하면 이틀 쉼. 서울신문 2017-11-20: 세관은 24시간 근무 후 하루 쉼.
        assertEquals("XBO", all.first { it.industry == "공항 검역" }.codes)
        assertEquals("XB", all.first { it.industry == "세관" }.codes)
        // 매일신문 2004-01-14: 8부제, 이레 일하고 하루 쉼. 서울 2인 1차는 순서 없음.
        assertEquals("DDDDDDDO", all.first { it.company == "대구 법인택시" }.codes)
        assertEquals(OrderState.NONE, all.first { it.company == "서울 법인택시" }.orderState)
        assertEquals(2, all.count { it.industry == "택시" })
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
        // [CYC-05] +3 in 1.38 (상대지구대·서울 구급대·SR).
        assertEquals(46, four.count { it.formKey == "4조2교대" })
        assertTrue(four.all { it.formKey == "4조2교대" || "4조2교대" in PublicCycles.norm(it.form + it.display) })
        assertEquals(four, PublicCycles.search("4조2교대"))
        assertEquals(all.count { it.industry == "발전" }, PublicCycles.search("", industry = "발전").size)
        assertEquals(7, PublicCycles.search("반도체", form = "4조3교대").size)
        assertTrue(PublicCycles.search("가").isEmpty())
        assertTrue(PublicCycles.search("없는회사이름").isEmpty())
        assertEquals("제조", PublicCycles.industries.first())
        assertEquals("4조2교대", PublicCycles.forms.first().first)
    }

    @Test fun industryGroupsGatherTheRowsAndSayTheirDays() {
        // [CYC-09] Every row is in some group; most are in a named one, and a chip's rows are the search's.
        val counts = com.bamnat.core.plaza.IndustryGroups.counts(all)
        assertEquals(all.size, counts.sumOf { it.second })
        val other = counts.firstOrNull { it.first == com.bamnat.core.plaza.IndustryGroups.OTHER }?.second ?: 0
        assertTrue("그 밖의 업종 $other", other <= 5)
        assertEquals(com.bamnat.core.plaza.IndustryGroups.OTHER, counts.last().first.takeIf { other > 0 } ?: com.bamnat.core.plaza.IndustryGroups.OTHER)
        fun g(i: String) = com.bamnat.core.plaza.IndustryGroups.of(i).key
        assertEquals("coast", g("해양경찰"))
        assertEquals("police", g("경찰"))
        assertEquals("semi", g("반도체 소재·화학"))
        assertEquals("chem", g("화학·환경"))
        assertEquals("steel", g("철강 물류"))
        assertEquals("airport", g("공항 보안"))
        assertEquals("maker", g("의료용품"))
        assertEquals("hospital", g("요양병원"))
        assertEquals("transit", g("택시"))
        val fire = PublicCycles.search("", group = "fire")
        assertEquals(all.count { it.industry == "소방" }, fire.size)
        // [CYC-04] The days the law names, never on another date.
        assertEquals("소방의 날", com.bamnat.core.plaza.IndustryGroups.dayOn("fire", LocalDate.of(2026, 11, 9))?.name)
        assertEquals("경찰의 날", com.bamnat.core.plaza.IndustryGroups.dayOn("police", LocalDate.of(2027, 10, 21))?.name)
        assertEquals(null, com.bamnat.core.plaza.IndustryGroups.dayOn("fire", LocalDate.of(2026, 11, 10)))
        assertEquals(null, com.bamnat.core.plaza.IndustryGroups.dayOn("semi", LocalDate.of(2026, 11, 9)))
        assertEquals(null, com.bamnat.core.plaza.IndustryGroups.dayOn(null, LocalDate.of(2026, 11, 9)))
        assertTrue(com.bamnat.core.plaza.IndustryGroups.ALL.all { it.note.isNotBlank() })
    }

    @Test fun theOrderAsSaidFindsItsRows() {
        // [CYC-10] 순서로 찾기: any day first, spaces and · ignored; a name is not an order.
        val dnbo = PublicCycles.search("주야비휴")
        assertTrue(dnbo.size >= 5)
        assertTrue(dnbo.all { it.codes.length == 4 && PublicCycles.sameRotation(it.codes, "DNBO") })
        assertEquals(dnbo.toSet(), PublicCycles.search("야 비 휴 주").toSet())
        assertEquals(dnbo.toSet(), PublicCycles.search("주·야·비·휴").toSet())
        assertTrue(PublicCycles.search("당비휴").any { it.industry == "공항 검역" })
        // 012815b6 PC 1차: "당비비" is how 소방 says 당·비·휴 (a 비 after a 비 is a day off), so it finds the same rows.
        val xbo = PublicCycles.search("당비비")
        assertTrue(xbo.isNotEmpty())
        assertEquals(PublicCycles.search("당비휴").toSet(), xbo.toSet())
        assertTrue(xbo.any { it.group.key == "fire" })
        assertTrue(PublicCycles.sameRotation("XBOO", "XBBB"))
        assertTrue(PublicCycles.sameRotation("DNBO", "DNBB"))
        assertFalse(PublicCycles.sameRotation("DNBO", "DDNB"))
        assertEquals(null, PublicCycles.orderCodes("휴휴"))
        assertEquals(null, PublicCycles.orderCodes("포스코"))
        assertEquals("포스코", PublicCycles.search("ㅍㅅㅋ").first().company)
        // An old source is marked by its year.
        assertEquals(22, PublicCycles.yearsOld(all.first { it.company == "대구 법인택시" }, LocalDate.of(2026, 11, 6)))
    }

    @Test fun controlRoomVariantsAreAmongTheCandidates() {
        // [CYC-08] 상황실·소방 변형: 4조1교대 당비비비·주당비휴, 3조2교대 주야비, 5조3교대 주주야비휴.
        val duty = PublicCycles.provided("어느 상황실", "4조1교대")
        assertEquals(listOf("XBOO", "DXBO", "XBO"), CycleOrders.candidates(duty).map { it.codes })
        assertEquals("당비휴휴", CycleOrders.spoken("XBOO"))
        assertTrue(CycleOrders.candidates(PublicCycles.provided("가나", "3조2교대")).any { it.codes == "DNB" })
        assertTrue(CycleOrders.candidates(PublicCycles.provided("가나", "5조3교대")).any { it.codes == "DDNBO" })
        assertTrue("4조1교대" in PublicCycles.PROVIDE_FORMS)
        assertEquals("4조 1교대", PublicCycles.formName("4조1교대"))
        // [CYC-06] No public row says its teams yet: the field is empty, not made up.
        assertTrue(all.all { it.teams.isEmpty() })
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

    /** 1.38 통합 11차: every row of the file has work to put in, even with no order and no time (병원이 근무표 짬). */
    @Test fun everyRowHasWorkEvenWithoutAnOrder() {
        all.forEach { c ->
            val plan = PublicSetup.plan(c, c.codes)
            assertTrue("${c.id} ${c.display}: only 휴무", plan.types.any { !it.isOff })
        }
        val ward = all.first { it.roster && it.shifts.isEmpty() && it.formKey == "3교대" }
        assertEquals(listOf("주간", "오후", "야간", "휴무"), PublicSetup.plan(ward, "").types.map { it.name })
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
