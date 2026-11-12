package com.bamnat.app.ui.pay

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bamnat.app.appContainer
import com.bamnat.app.data.ScheduleSnapshot
import com.bamnat.app.report.ReportShare
import com.bamnat.app.tier.Feature
import com.bamnat.app.tier.Gate
import com.bamnat.app.ui.components.SectionCard
import com.bamnat.app.ui.money.ProPill
import com.bamnat.app.ui.pro.openPro
import com.bamnat.core.pay.MonthPay
import com.bamnat.core.pay.PayProfile
import com.bamnat.core.report.PayReport
import kotlinx.coroutines.launch
import java.time.LocalDate
import com.bamnat.core.time.Today

/**
 * [PROV-01] 급여 리포트 (프로): the month on screen, or its year, as a PDF to keep or show the company, and
 * as a CSV for a spreadsheet. Made on the phone and handed to the share sheet; without 프로 the buttons
 * open the 프로 screen.
 */
@Composable
internal fun PayReportCard(vm: PayViewModel, profile: PayProfile, snapshot: ScheduleSnapshot, shown: MonthPay, closed: Boolean, gate: Gate,
    onEditSetup: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    val open = gate.allows(Feature.PAY_REPORT)
    val month = shown.month
    // [EASY-07] The guessed values a report would carry, asked about once before it leaves; none, it goes straight out.
    var checking by remember { mutableStateOf<Pair<List<com.bamnat.core.profile.Fact<*>>, () -> Unit>?>(null) }
    val profiles = remember { context.appContainer.profiles }
    fun run(what: String, block: suspend () -> Unit) {
        busy = true
        scope.launch {
            try {
                // 2.0.0 [RVP-69] (검토 REST-11·U14-08) The same words and 다시 하기 as every other file, not a toast of its own.
                if (ReportShare.orTell(block)) vm.reportMade(what)
            } finally {
                busy = false
            }
        }
    }
    fun make(what: String, block: suspend () -> Unit) {
        if (!open && !com.bamnat.app.ui.pro.tryFeature(Feature.PAY_REPORT)) { openPro(com.bamnat.app.tier.Feature.PAY_REPORT); return }
        val feature = if (what == "counsel_zip") "pay.counsel" else "pay.report"
        scope.launch {
            val guessed = runCatching { com.bamnat.core.profile.ExportCheck.estimated(profiles.facts(vm.calendarId), feature) }.getOrDefault(emptyList())
            if (guessed.isEmpty()) run(what, block) else checking = guessed to { run(what, block) }
        }
    }
    checking?.let { (facts, go) ->
        com.bamnat.app.ui.guide.ExportCheckSheet(
            facts = facts,
            onConfirm = { f ->
                scope.launch { profiles.mark(vm.calendarId, f.key, com.bamnat.core.profile.Source.TYPED, why = "보내기 전에 확인") }
                val left = facts - f
                checking = if (left.isEmpty()) null.also { go() } else left to go
            },
            onFix = { checking = null; onEditSetup() },
            onGo = { checking = null; go() },
            onDismiss = { checking = null },
        )
    }
    val who = remember(profile, month) { vm.reportWho(profile, month) }
    suspend fun year() = vm.yearPays(profile, snapshot, month.year)
    val shift = remember { context.appContainer.settingsRepository.current.workStyle.let { it == null || it == com.bamnat.core.work.WorkStyle.SHIFT } }
    fun go(r: Report) = when (r) {
        Report.MONTH_PDF -> make("month_pdf") { ReportShare.pdf(context, PayReport.month(shown, who, Today.date(), closed), PayReport.fileName(month, "pdf")) }
        Report.MONTH_CSV -> make("month_csv") { ReportShare.csv(context, PayReport.monthCsv(shown), PayReport.fileName(month, "csv"), "${month.year}년 ${month.monthValue}월 급여") }
        Report.YEAR_PDF -> make("year_pdf") {
            val pays = year()
            ReportShare.pdf(context, PayReport.year(month.year, pays, who, Today.date(), vm.closed.value.keys), PayReport.fileName(month.year, "pdf"))
        }
        Report.YEAR_CSV -> make("year_csv") { ReportShare.csv(context, PayReport.yearCsv(year(), vm.closed.value.keys), PayReport.fileName(month.year, "csv"), "${month.year}년 급여") }
        // G 1.32 [WTYP-06] 608 근무 기록 증빙: each day's start·end·휴게·일한 시간 and each week against 52시간.
        Report.PROOF_MONTH, Report.PROOF_3 -> make("work_proof") {
            val from = month.minusMonths(if (r == Report.PROOF_3) 2L else 0L).atDay(1); val to = month.atEndOfMonth()
            val doc = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                val days = com.bamnat.core.report.WorkProof.days(from, to, snapshot.resolver::resolve)
                com.bamnat.core.report.WorkProof.doc(who.job, from, to, days, Today.date())
            }
            ReportShare.pdf(context, doc, "bamnat-근무기록-$from-$to.pdf")
        }
        // 1.32 [WTYP-10] 연간 교대 리포트: 야간·공휴일·쉬는 날 근무·시간·예상 급여, a year on one page.
        Report.YEAR_SHIFT -> make("year_shift") {
            val pays = year().associateBy { it.month }
            val today = Today.date()
            val through = if (month.year == today.year) today else LocalDate.of(month.year, 12, 31)
            val doc = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                val months = com.bamnat.core.report.YearShiftReport.months(month.year, through, snapshot.resolver::resolve) { m -> pays[m]?.total }
                com.bamnat.core.report.YearShiftReport.doc(month.year, who.job, months, today)
            }
            ReportShare.pdf(context, doc, "bamnat-교대리포트-${month.year}.pdf")
        }
        // [PAYX-45] Everything a 노무사 or the 1350 상담 asks for, as files a person reads, in one zip.
        Report.COUNSEL -> make("counsel_zip") {
            val today = Today.date()
            val bytes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                val pays = (0L..2L).map { month.minusMonths(it) }.reversed().mapNotNull { m -> if (m == month) shown else vm.monthPay(profile, snapshot, m) }
                val files = linkedMapOf(
                    PayReport.fileName(month, "pdf") to ReportShare.pdfBytes(PayReport.month(shown, who, today, closed)),
                    PayReport.fileName(month, "csv") to PayReport.monthCsv(shown).toByteArray(Charsets.UTF_8),
                    "주별_근무시간_최근3개월.csv" to com.bamnat.core.report.CounselBundle.weeksCsv(pays).toByteArray(Charsets.UTF_8),
                )
                vm.leave.value?.let { files[com.bamnat.core.leave.LeaveExport.fileName(today)] = com.bamnat.core.leave.LeaveExport.csv(it).toByteArray(Charsets.UTF_8) }
                files[com.bamnat.core.report.CounselBundle.README] =
                    com.bamnat.core.report.CounselBundle.readme(today, files.keys.toList(), shown.ratesVersion).toByteArray(Charsets.UTF_8)
                com.bamnat.core.report.CounselBundle.zip(files)
            }
            ReportShare.zip(context, bytes, "bamnat-상담자료-$month.zip", "${month.year}년 ${month.monthValue}월 상담 자료")
        }
    }
    // 2.0.0 [RVJ-22] 검토 HQ-11 (조건 2 '같은 무게의 단추가 줄지어 있으면 통과하지 못해요'): the card had eight 테두리 단추 in a row
    // (다섯 for 일근·알바), which at 글자 200% stood one under another down the whole screen. The one most made (이 달 PDF) stays one
    // press away on the card; the other seven are a list behind '다른 리포트', each with a line saying what it is.
    var choosing by rememberSaveable { mutableStateOf(false) }
    SectionCard(title = "급여 리포트", trailing = { if (!busy) ProPill(locked = !open) else if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) }) {
        Text("계산 풀이와 날짜별 근무를 PDF로 남겨요. 회사에 물어볼 때 근거로 보여 줄 수 있어요.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            OutlinedButton(onClick = { go(Report.MONTH_PDF) }, enabled = !busy) {
                Icon(Icons.Outlined.Description, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(Report.MONTH_PDF.title(month))
            }
            TextButton(onClick = { choosing = true }, enabled = !busy) { Text(OTHER_REPORTS) }
        }
    }
    if (choosing) ReportSheet(month, shift, onPick = { choosing = false; go(it) }, onDismiss = { choosing = false })
}

/** 2.0.0 [RVJ-22] What 급여 리포트 makes: the month and the year as PDF·CSV, the 근무 기록 증빙, the 교대 리포트 and the 상담용 묶음. */
internal enum class Report(val group: String, val pdf: Boolean, val what: String) {
    MONTH_PDF("급여", true, "계산 풀이와 날짜별 근무까지"),
    MONTH_CSV("급여", false, "엑셀에서 여는 표"),
    YEAR_PDF("급여", true, "달마다 한 줄과 한 해 합계"),
    YEAR_CSV("급여", false, "한 해를 엑셀에서 여는 표"),
    PROOF_MONTH("근무 기록", true, "날짜별 시작·끝·휴게와 주 52시간 넘은 주"),
    PROOF_3("근무 기록", true, "석 달 치를 한 파일로"),
    YEAR_SHIFT("근무 기록", true, "야간·공휴일·쉬는 날 근무와 예상 급여를 한 장에"),
    COUNSEL("상담", false, "노무사나 고용노동부 상담(1350)에 보여 줄 파일을 한 번에");

    fun title(month: java.time.YearMonth): String = when (this) {
        MONTH_PDF -> "${month.monthValue}월 PDF"
        MONTH_CSV -> "${month.monthValue}월 CSV"
        YEAR_PDF -> "${month.year}년 PDF"
        YEAR_CSV -> "${month.year}년 CSV"
        PROOF_MONTH -> "${month.monthValue}월 근무 기록 증빙"
        PROOF_3 -> "최근 3개월 근무 기록"
        YEAR_SHIFT -> "${month.year}년 교대 리포트"
        COUNSEL -> "상담용 묶음 (급여·근무시간·연차)"
    }
}

internal const val OTHER_REPORTS = "다른 리포트"

/** 2.0.0 [RVJ-22] The list behind '다른 리포트': every report by what it is for, the month's PDF first. One press makes it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReportSheet(month: java.time.YearMonth, shift: Boolean, onPick: (Report) -> Unit, onDismiss: () -> Unit) {
    com.bamnat.app.ui.components.AppSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 16.dp).navigationBarsPadding()) {
            Text("어떤 리포트를 만들까요?", style = MaterialTheme.typography.titleLarge)
            Text("휴대폰에서 만들고, 보낼 곳은 직접 골라요.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            Report.entries.filter { shift || it != Report.YEAR_SHIFT }.groupBy { it.group }.forEach { (group, reports) ->
                Text(group, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp).semantics { heading() })
                reports.forEach { r ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button) { onPick(r) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (r.pdf) Icons.Outlined.Description else if (r == Report.COUNSEL) Icons.Outlined.FolderZip else Icons.Outlined.TableChart, null,
                            Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(r.title(month), style = MaterialTheme.typography.bodyLarge)
                            Text(r.what, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
