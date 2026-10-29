package com.bamnat.app.ui.pay

import android.graphics.Bitmap
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bamnat.app.ui.company.CompanyWayText
import com.bamnat.app.ui.pay.rulesheet.HourlyCard
import com.bamnat.app.ui.pay.rulesheet.RuleSheetCells
import com.bamnat.app.ui.pay.rulesheet.RuleSheetHost
import com.bamnat.app.ui.pay.rulesheet.RuleSheetState
import com.bamnat.app.ui.pay.rulesheet.SheetAction
import com.bamnat.app.ui.pay.rulesheet.SheetPreview
import com.bamnat.app.ui.pay.rulesheet.SheetPreviewBar
import com.bamnat.core.pay.PayslipRead
import com.bamnat.core.pay.SlipRole

/** [PHOTO-30] The result's two tabs. */
internal enum class ResultTab(val label: String) { SLIP("명세서 줄"), RULES("회사 규칙") }

/**
 * [PHOTO-30] 1.30 AW (사용자 2026-10-27 "같은 회사 '계산 방법' 사진 → 규칙 줄이 읽히고 통상시급 21,618원 근처. 인식은 되지만 그 이후 입력
 * 방식이 불편함. 좀 더 편하게"): what a photo was read as, on one full screen instead of a narrow dialog of everything with windows
 * over it (R3 A-3). On top the photos (a tap shows one whole); then two tabs, '명세서 줄' and '회사 규칙' (one when there is one); under
 * them, fixed, what the month comes to now and one button. Words: what was read is a 칸, putting it in is 적용.
 *
 * - 명세서 줄: [SlipCheck], each line fixed where it is ([PHOTO-31]).
 * - 회사 규칙: the 통상시급 card ([HourlyCard], [PHOTO-33]) and the 칸 ([RuleSheetCells], [PHOTO-32]), the same list as 칸만으로 적기.
 *
 * [reviewAsk] goes up when the button sends the user to the yellow lines.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PhotoResultScreen(
    title: String,
    photos: List<Bitmap>,
    read: PayslipRead,
    unsure: Set<Int>,
    same: Boolean?,
    fromPhoto: Boolean,
    onSame: (Boolean) -> Unit,
    onSlip: (PayslipRead, Set<Int>) -> Unit,
    sheet: RuleSheetState?,
    host: RuleSheetHost?,
    rulesOnly: Boolean,
    preview: SheetPreview?,
    working: Boolean,
    action: SheetAction,
    onDismiss: () -> Unit,
    onRetake: () -> Unit,
    reviewAsk: Int = 0,
    plaza: CompanyWayText? = null,
    startTab: ResultTab? = null,
    slipExtras: @Composable ColumnScope.() -> Unit = {},
    rulesExtras: @Composable ColumnScope.() -> Unit = {},
) {
    val tabs = buildList {
        if (read.lines.isNotEmpty() || !rulesOnly || sheet == null) add(ResultTab.SLIP)
        if (sheet != null) add(ResultTab.RULES)
    }
    var tabIndex by rememberSaveable { mutableIntStateOf(tabs.indexOf(startTab).coerceAtLeast(0)) }
    // The yellow lines are on the payslip's tab: going to them shows it.
    var lastAsk by rememberSaveable { mutableIntStateOf(reviewAsk) }
    if (reviewAsk != lastAsk) { lastAsk = reviewAsk; tabs.indexOf(ResultTab.SLIP).takeIf { it >= 0 }?.let { tabIndex = it } }
    val tab = tabs.getOrElse(tabIndex) { tabs.first() }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, "닫기") } },
                        actions = { TextButton(onClick = onRetake, modifier = Modifier.heightIn(min = 48.dp)) { Text("다시 하기") } },
                    )
                },
                bottomBar = { SheetPreviewBar(preview, working, action, fallback = slipFallback(read)) },
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
                    Column(Modifier.widthIn(max = 640.dp).fillMaxWidth()) {
                        if (photos.isNotEmpty()) Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) { photos.forEach { PhotoThumb(it, Modifier.size(width = 112.dp, height = 84.dp)) } }
                        if (tabs.size > 1) TabRow(selectedTabIndex = tabIndex) {
                            tabs.forEachIndexed { i, t ->
                                Tab(selected = tabIndex == i, onClick = { tabIndex = i }, modifier = Modifier.heightIn(min = 48.dp), text = {
                                    Text(t.label + when (t) {
                                        ResultTab.SLIP -> if (read.lines.isNotEmpty()) " ${read.lines.size}" else ""
                                        ResultTab.RULES -> sheet?.sheet?.cells?.size?.takeIf { it > 0 }?.let { " $it" }.orEmpty()
                                    })
                                })
                            }
                        }
                        Column(
                            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            read.month?.let { Text("${it.year}년 ${it.monthValue}월 명세서", style = MaterialTheme.typography.titleSmall) }
                            when (tab) {
                                ResultTab.SLIP -> {
                                    if (read.lines.isEmpty()) Hint("금액을 찾지 못했어요. 아래 ‘칸 더하기’로 명세서 줄을 직접 넣거나, 이름과 금액이 함께 보이게 다시 찍어 주세요.")
                                    SlipCheck(read, unsure, same, fromPhoto = fromPhoto && read.lines.isNotEmpty(), onAnswer = onSame, onChange = onSlip, reviewAsk = reviewAsk)
                                    // [READ-08] What the tax estimate (묶음 W) starts from: 공제 합계 and 실수령.
                                    val net = read.net
                                    if (read.deducted != null || net != null) Text(
                                        listOfNotNull(read.deducted?.let { "공제 ${won(it)}" }, net?.let { "실수령 ${won(it)}" }).joinToString(" · "),
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    slipExtras()
                                }
                                ResultTab.RULES -> if (sheet != null && host != null) {
                                    if (read.lines.isEmpty()) Hint("명세서 없이 회사 계산 방법만으로 계산해요. 기본급·시간처럼 꼭 필요한 값은 급여 화면이 바로 넣게 알려 줘요.")
                                    // [J133-77] 시급·일당 is its own hourly value; the card is for 월급.
                                    if (host.profile.wageType == com.bamnat.core.pay.WageType.MONTHLY) HourlyCard(preview?.profile ?: host.profile, host.month,
                                        sheet.sheet.common, sheet::terms, plaza = plaza, slipBase = read.amount(SlipRole.BASE))
                                    RuleSheetCells(sheet, host, empty = "사진에서 규칙을 찾지 못했어요. 아래 ‘칸 더하기’로 직접 넣어도 돼요. 자주 쓰는 수당을 고르면 금액만 넣으면 돼요.")
                                    rulesExtras()
                                }
                            }
                            Spacer(Modifier.height(16.dp))
                        }
                    }
                }
            }
        }
    }
}

/** Under a payslip with nothing worked out: what it says it paid. */
private fun slipFallback(read: PayslipRead): String? =
    (read.gross ?: read.lines.filter { it.isEarning }.sumOf { it.amount ?: 0 }.takeIf { it > 0 })?.let { "명세서 지급 ${won(it)} · ${read.lines.size}줄" }
