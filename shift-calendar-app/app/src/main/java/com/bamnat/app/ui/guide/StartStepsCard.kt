package com.bamnat.app.ui.guide

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bamnat.app.AppContainer
import com.bamnat.app.NoticeLinks
import com.bamnat.app.ui.company.SoftCard
import com.bamnat.app.ui.settings.SettingsLinks
import com.bamnat.app.ui.settings.SettingsPage
import com.bamnat.core.guide.StartSteps
import com.bamnat.core.time.Today
import com.bamnat.core.work.WorkStyle

/**
 * [GUIDE-20] 1.31 P2: the first week's 시작 단계 card over the calendar. While it shows, the question card waits
 * ([CalendarScreen] leaves it out), so a new user sees one thing to do at a time. 그만 보기 hides it for good; each step
 * can also be found later (설정 › 알람, 처음 설정 다시 받기, 회사 규칙 광장).
 */
object StartStepsState {
    private const val FILE = "start_steps"
    private const val K_DISMISSED = "dismissed"
    private const val K_COMPANY = "company_picked"

    fun dismissed(c: AppContainer): Boolean = c.appContext.getSharedPreferences(FILE, 0).getBoolean(K_DISMISSED, false)

    fun dismiss(c: AppContainer) = c.appContext.getSharedPreferences(FILE, 0).edit().putBoolean(K_DISMISSED, true).apply()

    /** [J133-52] A company (or its public cycle) was chosen in the first setup, so 회사 고르기 is done already. */
    fun companyPicked(c: AppContainer) = c.appContext.getSharedPreferences(FILE, 0).edit().putBoolean(K_COMPANY, true).apply()

    /** Today's rows for this phone, or null for no card. [hasShifts]: the calendar on screen has days. */
    fun rows(c: AppContainer, hasShifts: Boolean, style: WorkStyle? = c.settingsRepository.current.workStyle): List<StartSteps.Row>? {
        val s = c.settingsRepository.current
        // [J133-26] 1.33 (실사용 테스트 "식당·현장 달력에 '근무 넣기'·'우리 회사 고르기'"): steps of the calendar on screen's own work.
        val companyApplies = c.companyApi.enabled && style != WorkStyle.INVEST && style != WorkStyle.BUSINESS && style != WorkStyle.FIELD
        val ownDays = style == WorkStyle.INVEST || style == WorkStyle.BUSINESS || style == WorkStyle.FIELD
        val store = c.companyStore
        return StartSteps.rows(
            installed = c.installedAt?.atZone(Today.zone())?.toLocalDate(), today = Today.date(), dismissed = dismissed(c),
            alarmsOn = s.alarmsEnabled, hasShifts = hasShifts || ownDays,
            companyLinked = store.applied.value != null || store.member.value != null ||
                c.appContext.getSharedPreferences(FILE, 0).getBoolean(K_COMPANY, false), companyApplies = companyApplies,
        )
    }
}

/**
 * [J133-16] 1.33 (실사용 테스트 "큰 폰 첫 화면에 달력 격자가 없음"): folded to one line, the next step and its button, so the month
 * grid is in view under it; 모두 보기 opens the three.
 */
@Composable
fun StartStepsCard(rows: List<StartSteps.Row>, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    var expanded by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    val next = rows.firstOrNull { !it.done }
    SoftCard(modifier.padding(horizontal = 16.dp, vertical = 6.dp), highlighted = true) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = if (expanded) 16.dp else 8.dp), verticalArrangement = Arrangement.spacedBy(if (expanded) 8.dp else 2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(StartSteps.title(rows), style = if (expanded) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f))
                TextButton(onClick = { expanded = !expanded }, modifier = Modifier.heightIn(min = 48.dp)) { Text(if (expanded) "접기" else "모두 보기") }
            }
            if (!expanded) {
                next?.let { row ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("다음: ${row.step.label}", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        FilledTonalButton(onClick = { open(row.step) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(row.step.action) }
                    }
                }
                return@Column
            }
            Text("이것만 해 두면 밤낮을 바로 쓸 수 있어요. 나머지 기능은 그 뒤에 하나씩 알려 드려요.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            rows.forEach { row ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 48.dp)
                    .semantics(mergeDescendants = true) { contentDescription = if (row.done) row.step.doneLabel else "${row.step.label}, 아직 안 했어요" }) {
                    Icon(if (row.done) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked, contentDescription = null,
                        tint = if (row.done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(if (row.done) row.step.doneLabel else row.step.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    if (!row.done) FilledTonalButton(onClick = { open(row.step) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(row.step.action) }
                }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("그만 보기") }
        }
    }
}

private fun open(step: StartSteps.Step) = when (step) {
    StartSteps.Step.ALARM -> SettingsLinks.open(SettingsPage.ALARM, "알람 사용")
    StartSteps.Step.SHIFTS -> NoticeLinks.open(NoticeLinks.Target.SETUP_AGAIN)
    StartSteps.Step.COMPANY -> NoticeLinks.open(NoticeLinks.Target.COMPANY_FIND)
}
