package com.bamnat.app.ui.pay

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * 2.0.0 [RVJ-66] 총괄 HQ-103 (조건 6 '의미색을 아껴요': 빨강은 위험만, 주황은 주의만), 갈래 K의 가계부 주의색 정리(RVK-45)와 같은 일을
 * 급여·팀·사장님·통계 화면에. 빨강(`colorScheme.error`, 달력의 `warning`·`warningContainer`, `bamnat.danger`, `Tone.Danger`, StatTile의
 * `warning =`)은 아래 자리에만 남아요(`WarningBanner`는 2.0.0 RVP-13부터 기본이 주의이고, 빨간 띠는 `tone = Tone.Danger`로 세요): 되돌릴 수 없는 지우기·나가기, 저장·다음을 막는 잘못된 값, 법·규칙을 어긴 근무, 기록이 안 되는 고장.
 * 확인할 일·차이·기준에 가까움·못 읽음·빈자리처럼 챙길 일은 `MaterialTheme.bamnat.caution`(주의)이에요. 새 빨강은 까닭을 여기에 적어야 해요.
 */
class PayRedIsDangerTest {
    private val ui = File("src/main/java/com/bamnat/app/ui")

    private val work = listOf(
        "BossContractParts.kt", "BossCoverParts.kt", "BossFilingParts.kt", "BossLedgerLine.kt", "CommuteScreen.kt", "FieldTools.kt",
        "KeepRecordsDialog.kt", "ManageScreen.kt", "PartTimeTools.kt", "PayrollScreen.kt", "ShopDaysScreen.kt", "ShopMoneySettings.kt",
        "ShopTools.kt", "StaffCostBars.kt", "StaffImportDialog.kt",
    )

    /** Every red left, by file, and why it is danger. */
    private val allowed: Map<String, List<String>> = mapOf(
        "pay/CompanyRulesEditor.kt" to listOf("법정 기준보다 적은 회사 규칙(법 위반 위험)"),
        "pay/CustomItemDialog.kt" to listOf("직접 만든 항목 한도: 더 만들 수 없음(저장을 막음)"),
        "pay/ExtraRuleEditor.kt" to listOf("지우기(되돌릴 수 없음)"),
        "pay/PayQuickSetup.kt" to listOf("기본급 없이 다음으로 못 감", "급여 방식을 안 골라 다음으로 못 감"),
        "pay/PayWizard.kt" to listOf("답 없이 다음으로 못 감"),
        "stats/StatsScreen.kt" to listOf("주 52시간(제53조)·특례 업종 11시간 휴식(제59조 제2항)을 넘은 것을 한 띠에"),
        // [RVJ-68] (HQ-107) The team's 기준 (휴식·연속) are 주의 now; only 주 52시간 (근로기준법 제53조) is red, told in one place.
        "team/LeaderTeamScreen.kt" to listOf("팀원 그래도 지우기", "같은 이름(저장을 막음)"),
        "team/TeamSignParts.kt" to listOf("주 52시간(근로기준법 제53조)을 넘은 줄의 표시: 확인할 것·칸·맞바꾸기·바꾸기 요청·자동 채우기 모두 이 한 곳"),
        // work/CommuteScreen.kt: 위치 권한 띠 둘은 RVP-13부터 주의(WarningBanner 기본) — 설정에서 바로 고칠 수 있는 일이라 빨강이 아니에요.
        "work/FieldTools.kt" to listOf("견적서 지우기"),
        "work/KeepRecordsDialog.kt" to listOf("기록을 지우고 새로 시작"),
        "work/ManageScreen.kt" to listOf("할 일 가운데 팀의 주 52시간 넘김 한 줄(법)"),
        "work/PartTimeTools.kt" to listOf("최저임금보다 적은 시급(법 위반, RVP-13)", "지우기"),
        "work/PayrollScreen.kt" to listOf("저장하지 않고 나가기", "직원 그래도 지우기", "저장하지 않고 나가기(직원 창)",
            "[RVJ-100] 빈 가게 지우기 단추(되돌릴 수 없는 지우기, 직원·닫은 달이 없을 때만 보여요)", "[RVJ-100] 빈 가게 지우기 확인"),
        "work/StaffCostBars.kt" to listOf("주 52시간을 넘은 직원의 막대"),
    )

    private val red = Regex(
        """colorScheme\.error\b|LocalCalendarColors\.current\.warning\b|\bcal\.warning\b|\.warningContainer\b|bamnat\.danger\b|Tone\.Danger\b|\bwarning = [^t]""",
    )

    @Test fun redIsOnlyForDanger() {
        val files = (ui.resolve("pay").walkTopDown() + ui.resolve("team").walkTopDown() + ui.resolve("stats").walkTopDown() +
            work.map { ui.resolve("work/$it") }.asSequence()).filter { it.isFile && it.extension == "kt" }.toList()
        assert(files.size > 40) { "급여·팀·사장님 화면 파일을 못 찾았어요(${files.size})" }
        val found = files.associate { f -> f.relativeTo(ui).invariantSeparatorsPath to f.readLines().count { red.containsMatchIn(it) } }
            .filterValues { it > 0 }
        assertEquals(
            "빨강은 위험만(조건 6). 챙길 일은 MaterialTheme.bamnat.caution으로, 정말 위험이면 까닭을 이 시험의 allowed에 적어요",
            allowed.mapValues { it.value.size }.toSortedMap(), found.toSortedMap(),
        )
    }
}
