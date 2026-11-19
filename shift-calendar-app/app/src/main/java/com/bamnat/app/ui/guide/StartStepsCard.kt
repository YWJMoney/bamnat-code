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
import androidx.compose.material.icons.filled.Close
import com.bamnat.app.ui.components.FilledTonalButton
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
 * ([CalendarScreen] leaves it out), so a new user sees one thing to do at a time. X hides it for good; each step
 * can also be found later (설정 › 알람, 처음 설정 다시 받기, 회사 규칙 광장).
 */
object StartStepsState {
    private const val FILE = "start_steps"
    private const val K_DISMISSED = "dismissed"
    private const val K_COMPANY = "company_picked"

    fun dismissed(c: AppContainer): Boolean = c.appContext.getSharedPreferences(FILE, 0).getBoolean(K_DISMISSED, false)

    fun dismiss(c: AppContainer) = c.appContext.getSharedPreferences(FILE, 0).edit().putBoolean(K_DISMISSED, true).apply()

    /** [J133-52] A company (or its public cycle) was chosen in the first setup, so 회사 고르기 is done already. */
    /**
     * [J133-52] The first setup's source is a company's own (on the server, a public cycle, or a coworker's file naming one). 1.38 통합
     * 9차 (새로 깐 교대 폰, '3조 2교대 일반형'에서 직접 만들기 → 시작 단계가 아예 안 나옴): a template has a name too, but it is no
     * company, so 우리 회사 고르기 is still to do.
     */
    internal fun fromCompany(source: com.bamnat.core.setup.SetupSource?): Boolean =
        source != null && source.kind != com.bamnat.core.setup.SetupSource.Kind.TEMPLATE && source.name.isNotBlank()

    fun companyPicked(c: AppContainer) = c.appContext.getSharedPreferences(FILE, 0).edit().putBoolean(K_COMPANY, true).apply()

    private const val K_UPDATER = "came_by_update"

    /**
     * True when the calendar came by an update: set up on an app older than [current] ([setupVersion], kept since 1.38), or
     * set up before 1.38 kept a version and the app was updated since ([updated]). Null while nothing is set up yet.
     * 1.38 통합 8차 (같은 날 옛 판 설치·덮어 깔기 뒤 시계를 11/11로 옮기면 시작 단계가 다시): versions, not days, so a setup
     * and an update on one day are told apart and moving the clock changes nothing.
     */
    internal fun cameByUpdate(setupVersion: Int?, current: Int, setupDone: Boolean, updated: Boolean): Boolean? = when {
        setupVersion != null -> setupVersion < current
        !setupDone -> null
        else -> updated
    }

    /** [cameByUpdate] for this phone, decided once and kept, so it never changes afterwards. */
    fun cameByUpdate(c: AppContainer): Boolean {
        val p = c.appContext.getSharedPreferences(FILE, 0)
        if (p.contains(K_UPDATER)) return p.getBoolean(K_UPDATER, false)
        val v = cameByUpdate(c.firstSetup.version(), com.bamnat.app.BuildConfig.VERSION_CODE,
            setupDone = c.settingsRepository.current.workStyle != null || c.firstSetup.day() != null, updated = c.updatedAt != null)
            ?: return false
        p.edit().putBoolean(K_UPDATER, v).apply()
        return v
    }

    /** Today's rows for this phone, or null for no card. [hasShifts]: the calendar on screen has days. */
    fun rows(c: AppContainer, hasShifts: Boolean, style: WorkStyle? = c.settingsRepository.current.workStyle): List<StartSteps.Row>? {
        val s = c.settingsRepository.current
        // 1.38 PC 4a5c3828 (1.36에서 업데이트로 온 사람 달력 맨 위에 시작 단계와 '알림 2개 더 보기'): the steps are for a new phone's
        // first week; a calendar set up before the app was last updated is not new.
        if (cameByUpdate(c)) return null
        // [J133-26] 1.33 (실사용 테스트 "식당·현장 달력에 '근무 넣기'·'우리 회사 고르기'"): steps of the calendar on screen's own work.
        // [J133-71] 1.33 (사용자 "알바 파트타임 같은 유형에서 회사 검색같은것은 의미가 없지 않아?"): only 교대·일근 pick a company.
        val companyApplies = c.companyApi.enabled && style?.usesCompany != false
        val ownDays = style == WorkStyle.INVEST || style == WorkStyle.BUSINESS || style == WorkStyle.FIELD
        val store = c.companyStore
        return StartSteps.rows(
            installed = c.installedAt?.atZone(Today.zone())?.toLocalDate(), today = Today.date(), dismissed = dismissed(c),
            alarmsOn = s.alarmsEnabled, hasShifts = hasShifts || ownDays,
            companyLinked = store.applied.value != null || store.member.value != null ||
                c.appContext.getSharedPreferences(FILE, 0).getBoolean(K_COMPANY, false), companyApplies = companyApplies,
            style = style,
        )
    }
}

/**
 * [J133-16] 1.33 (실사용 테스트 "큰 폰 첫 화면에 달력 격자가 없음"): folded to one line, the next step and its button, so the month
 * grid is in view under it; 모두 보기 opens the three.
 */
@Composable
fun StartStepsCard(rows: List<StartSteps.Row>, onDismiss: () -> Unit, modifier: Modifier = Modifier, onShifts: (() -> Unit)? = null) {
    // 03df48fb Y6 ('근무 넣기'가 처음 설정 다시 받기를 열어요): the calendar paints the days itself; the first setup only when it cannot.
    fun open(step: StartSteps.Step) = if (step == StartSteps.Step.SHIFTS && onShifts != null) onShifts() else openStep(step)
    var expanded by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    val next = rows.firstOrNull { !it.done }
    SoftCard(modifier.padding(horizontal = 16.dp, vertical = 6.dp), highlighted = true) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = if (expanded) 16.dp else 8.dp), verticalArrangement = Arrangement.spacedBy(if (expanded) 8.dp else 2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(StartSteps.title(rows), style = if (expanded) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f))
                TextButton(onClick = { expanded = !expanded }, modifier = Modifier.heightIn(min = 48.dp)) { Text(if (expanded) "접기" else "모두 보기") }
                // [J133-68] 1.33 (중간 빌드 확인: 카드마다 닫는 모양이 달랐어요): the same X as the other calendar cards, folded or not.
                androidx.compose.material3.IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, "시작 단계 닫기", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (!expanded) {
                next?.let { row ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("다음: ${row.label}", style = MaterialTheme.typography.bodyMedium,
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
                    .semantics(mergeDescendants = true) { contentDescription = if (row.done) row.doneLabel else "${row.label}, 아직 안 했어요" }) {
                    Icon(if (row.done) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked, contentDescription = null,
                        tint = if (row.done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(if (row.done) row.doneLabel else row.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    if (!row.done) FilledTonalButton(onClick = { open(row.step) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(row.step.action) }
                }
            }
        }
    }
}

private fun openStep(step: StartSteps.Step) = when (step) {
    StartSteps.Step.ALARM -> SettingsLinks.open(SettingsPage.ALARM, "알람 사용")
    StartSteps.Step.SHIFTS -> NoticeLinks.open(NoticeLinks.Target.SETUP_AGAIN)
    StartSteps.Step.COMPANY -> NoticeLinks.open(NoticeLinks.Target.COMPANY_FIND)
}
