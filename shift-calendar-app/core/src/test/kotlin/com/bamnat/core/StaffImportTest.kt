package com.bamnat.core

import com.bamnat.core.boss.StaffImport
import com.bamnat.core.boss.StaffMember
import com.bamnat.core.boss.StaffTax
import com.bamnat.core.boss.WageForm
import com.bamnat.core.imports.RosterWord
import com.bamnat.core.imports.Sheets
import com.bamnat.core.imports.StaffRow
import com.bamnat.core.imports.StaffSheets
import com.bamnat.core.imports.WordTable
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 1.38 직원 명단 가져오기 (사용자 2026-11-05 "어떤 형식이든"). */
class StaffImportTest {
    private val on = LocalDate.of(2026, 11, 5)

    @Test fun aListBecomesStaffAndSameNamesAreNotAddedTwice() {
        val rows = listOf(
            StaffRow("김 알바", WageForm.HOUR, 11_000, mapOf(DayOfWeek.MONDAY to (LocalTime.of(9, 0) to LocalTime.of(15, 0))), job = "홀"),
            StaffRow("이주방", null, null),
            StaffRow("박매니저", WageForm.MONTH, 2_500_000, doubtful = setOf("base")),
        )
        val l = StaffImport.lines(rows, listOf(StaffMember("s0", "김알바")), on)
        assertTrue("같은 이름은 이미 있음", l[0].already)
        assertEquals(LocalTime.of(15, 0), l[0].member.weekly[DayOfWeek.MONDAY]?.end)
        assertEquals("홀", l[0].member.job)
        // 2.0.0 [RVJ-40] 검토 PC-102: an empty sum stays empty (0) and is asked for, not the 최저시급.
        assertEquals("빈 금액은 비워 두고 물어요", 0L, l[1].member.base)
        assertTrue(l[1].noAmount)
        assertEquals(WageForm.HOUR, l[1].member.form)
        assertEquals(listOf("금액이 없어요 · 적어 주세요", "시급·월급·일급", "근무 요일·시간이 없어요"), l[1].check)
        assertEquals(listOf("금액", "근무 요일·시간이 없어요"), l[2].check)
    }

    private fun w(t: String, x: Int, y: Int) = RosterWord(t, x, y, x + t.length * 20, y + 24)

    @Test fun aPhotoOfAListIsReadAsItsColumns() {
        val words = listOf(
            w("이름", 10, 10), w("시급", 200, 10), w("근무요일", 400, 12), w("출근", 600, 10), w("퇴근", 760, 9),
            w("김민지", 10, 60), w("10,500", 200, 62), w("월~금", 400, 60), w("09:00", 600, 61), w("18:00", 760, 60),
            // 시급 칸이 비어 있어도 다음 칸이 밀리지 않아요.
            w("박준", 10, 110), w("토", 400, 110), w("10:00", 600, 111), w("16:00", 760, 110),
        )
        val rows = WordTable.rows(words)
        assertEquals(listOf("박준", "", "토", "10:00", "16:00"), rows[2])
        val sheet = StaffSheets.read(rows, on)!!
        assertEquals(10_500L, sheet.rows[0].base)
        assertEquals(5, sheet.rows[0].weekly.size)
        assertEquals(setOf(DayOfWeek.SATURDAY), sheet.rows[1].weekly.keys)
    }

    // ---- 2.0.0 [RVJ-40] 검토 PC-102: 일급·월급 칸, 소득세·외국인 칸, 못 읽은 칸 ----

    private fun sheet(vararg lines: String) = StaffSheets.read(lines.map { it.split(',') }, on)!!

    /** '이름,시급,일급,월급' lists: each person's pay is in the column of its kind, and that kind is their form. */
    @Test fun eachPayColumnGivesItsOwnForm() {
        val s = sheet("이름,시급,일급,월급", "김알바,10500,,", "이일용,,150000,", "박매니저,-,-,2500000")
        assertEquals(listOf(WageForm.HOUR, WageForm.DAY, WageForm.MONTH), s.rows.map { it.form })
        assertEquals(listOf(10_500L, 150_000L, 2_500_000L), s.rows.map { it.base })
        assertTrue(s.rows.all { it.doubtful.isEmpty() && it.unreadCells.isEmpty() })
        val l = StaffImport.lines(s.rows, emptyList(), on)
        assertEquals(listOf(10_500L, 150_000L, 2_500_000L), l.map { it.member.base })
        assertTrue("일급은 일용으로 떼요", l[1].member.insurance.tax == StaffTax.DAILY)
    }

    /** Every name a pay column goes by, one at a time. */
    @Test fun everyNameOfAPayColumnIsRead() {
        val names = mapOf(
            WageForm.HOUR to listOf("시급", "시간급", "시간당", "시간당 급여"),
            WageForm.DAY to listOf("일급", "일당", "하루 급여"),
            WageForm.MONTH to listOf("월급", "월급여", "월급액", "월 기본급", "월임금"),
        )
        for ((form, l) in names) for (n in l) {
            val r = sheet("이름,$n", "김직원,123456").rows.single()
            assertEquals("$n 칸", form, r.form)
            assertEquals("$n 칸", 123_456L, r.base)
        }
        // A column of any kind, with the form said beside it.
        for (n in listOf("급여", "임금", "기본급", "금액", "통상임금", "급여액", "시급/월급")) {
            val r = sheet("이름,급여형태,$n", "김직원,일급,130000").rows.single()
            assertEquals("$n 칸", WageForm.DAY, r.form)
            assertEquals("$n 칸", 130_000L, r.base)
        }
        // 형태 칸이 말하면 그 칸을 골라요 (시급·월급 둘 다 적힌 줄).
        val both = sheet("이름,형태,시급,월급", "박매니저,월급,10320,2500000").rows.single()
        assertEquals(WageForm.MONTH, both.form)
        assertEquals(2_500_000L, both.base)
        val unsure = sheet("이름,시급,월급", "박매니저,10320,2500000").rows.single()
        assertTrue("둘 다 적혔는데 형태 칸이 없으면 물어요", "form" in unsure.doubtful)
    }

    /** A cell the reader cannot read is said as it is, and the person comes in with no sum to be typed, not the 최저시급. */
    @Test fun aCellThatCannotBeReadIsSaidAndNotGuessed() {
        val s = sheet("이름,일급,월급", "이일용,15만원?,")
        val r = s.rows.single()
        assertNull(r.base)
        assertEquals(listOf("일급 칸 ‘15만원?’"), r.unreadCells)
        val l = StaffImport.lines(s.rows, emptyList(), on).single()
        assertTrue(l.noAmount)
        assertEquals(0L, l.member.base)
        assertEquals("금액을 못 읽었어요 · 적어 주세요", l.check.first())
        assertTrue(l.check.contains("일급 칸 ‘15만원?’ 못 읽음"))
        val generic = sheet("이름,급여", "김직원,협의").rows.single()
        assertEquals(listOf("급여 칸 ‘협의’"), generic.unreadCells)
        // 최저시급보다 적은 시급은 넣되 말해요.
        val low = StaffImport.lines(sheet("이름,시급", "김직원,9000").rows, emptyList(), on).single()
        // [RVJ-48] 검토 PC-117: the warning names the year whose minimum it holds the wage to.
        assertTrue(low.check.toString(), low.check.any { it.startsWith("${on.year}년 최저시급") })
    }

    /** 소득세 칸: 3.3%는 4대보험 없이, 일용은 고용보험부터, 근로는 법대로; every name the column goes by. */
    @Test fun theTaxColumnSetsHowTaxComesOff() {
        val week = "월~금,09:00,18:00"
        for (n in listOf("소득세", "세금", "원천징수", "원천세", "소득구분", "세금 구분", "신고구분")) {
            val s = sheet("이름,시급,근무요일,출근,퇴근,$n", "가,10320,$week,3.3%", "나,10320,$week,일용", "다,10320,$week,근로소득", "라,10320,$week,안 뗌")
            assertEquals("$n 칸", listOf(StaffTax.BIZ, StaffTax.DAILY, StaffTax.WAGE, StaffTax.NONE), s.rows.map { it.tax })
            val l = StaffImport.lines(s.rows, emptyList(), on)
            assertFalse("3.3%는 4대보험이 없어요", l[0].member.insurance.any)
            assertEquals(StaffTax.BIZ, l[0].member.insurance.tax)
            assertTrue(l[1].member.insurance.employment && !l[1].member.insurance.pension)
            assertTrue(l[2].member.insurance.pension && l[2].member.insurance.health && l[2].member.insurance.employment)
        }
        assertEquals(StaffTax.BIZ, StaffSheets.taxOf("사업소득"))
        assertEquals(StaffTax.BIZ, StaffSheets.taxOf("프리랜서"))
        assertEquals(StaffTax.WAGE, StaffSheets.taxOf("4대보험"))
        val odd = sheet("이름,시급,소득세", "가,10320,모름").rows.single()
        assertNull(odd.tax)
        assertTrue("tax" in odd.doubtful)
        assertEquals(listOf("소득세 칸 ‘모름’"), odd.unreadCells)
        assertTrue(StaffImport.lines(listOf(odd), emptyList(), on).single().check.contains("소득세 떼는 법"))
    }

    /** 외국인 칸 (예·O·✓) and 국적 칸 (한국이 아니면 외국인). */
    @Test fun theForeignColumnIsRead() {
        val yes = sheet("이름,시급,외국인", "가,10320,예", "나,10320,O", "다,10320,", "라,10320,아니오")
        assertEquals(listOf(true, true, null, false), yes.rows.map { it.foreign })
        val byCountry = sheet("이름,시급,국적", "가,10320,베트남", "나,10320,대한민국", "다,10320,한국")
        assertEquals(listOf(true, false, false), byCountry.rows.map { it.foreign })
        val l = StaffImport.lines(byCountry.rows, emptyList(), on)
        assertTrue(l[0].member.insurance.foreign)
        assertFalse(l[1].member.insurance.foreign)
        val odd = sheet("이름,시급,외국인 여부", "가,10320,글쎄").rows.single()
        assertNull(odd.foreign)
        assertTrue(odd.unreadCells.single().startsWith("외국인 여부 칸"))
    }

    /** The list's own columns that are not read are named; 연락처·주민번호 are never read and never named. */
    @Test fun theColumnsNotReadAreNamed() {
        val s = sheet("이름,시급,비고,휴게,연락처,주민번호,", "가,10320,수습,30분,010-0000-0000,900101-1000000,")
        assertEquals(listOf("비고", "휴게"), s.unread)
        assertFalse(s.rows.single().toString().contains("010-"))
    }

    /** The 예시 파일 is read back whole, with nothing to look at. */
    @Test fun theExampleFileReadsBackWhole() {
        val s = com.bamnat.core.imports.StaffSheet.read(Sheets.book(("\uFEFF" + StaffSheets.EXAMPLE_CSV).toByteArray()), on)!!
        assertEquals(listOf("김알바", "이일용", "박매니저", "최디자인", "응우옌"), s.rows.map { it.name })
        assertEquals(listOf(WageForm.HOUR, WageForm.DAY, WageForm.MONTH, WageForm.HOUR, WageForm.HOUR), s.rows.map { it.form })
        assertEquals(listOf(10_320L, 150_000L, 2_500_000L, 12_000L, 10_320L), s.rows.map { it.base })
        assertEquals(listOf(StaffTax.WAGE, StaffTax.DAILY, StaffTax.WAGE, StaffTax.BIZ, StaffTax.WAGE), s.rows.map { it.tax })
        assertEquals(true, s.rows[4].foreign)
        assertEquals("홀", s.rows[0].job)
        assertEquals(LocalDate.of(2026, 3, 2), s.rows[0].hiredOn)
        assertTrue(s.rows.all { it.doubtful.isEmpty() && it.unreadCells.isEmpty() && it.weekly.isNotEmpty() })
        assertTrue(s.unread.isEmpty())
        // 읽는 칸 한 줄과 예시 파일의 칸이 같아요.
        listOf("시급", "일급", "월급", "입사일", "소득세", "외국인").forEach { assertTrue(it, StaffSheets.READS.contains(it)) }
    }

    /** A photo goes the same way: a 일급 column on paper is read as one in a file. */
    @Test fun aPhotoReadsTheSameColumns() {
        val words = listOf(
            w("이름", 10, 10), w("일급", 200, 10), w("월급", 400, 10), w("소득세", 600, 10),
            w("이일용", 10, 60), w("150,000", 200, 62), w("일용", 600, 60),
            w("박매니저", 10, 110), w("250만", 400, 110), w("근로", 600, 111),
        )
        val s = StaffSheets.read(WordTable.rows(words), on)!!
        assertEquals(listOf(WageForm.DAY, WageForm.MONTH), s.rows.map { it.form })
        assertEquals(listOf(150_000L, 2_500_000L), s.rows.map { it.base })
        assertEquals(listOf(StaffTax.DAILY, StaffTax.WAGE), s.rows.map { it.tax })
    }
}
