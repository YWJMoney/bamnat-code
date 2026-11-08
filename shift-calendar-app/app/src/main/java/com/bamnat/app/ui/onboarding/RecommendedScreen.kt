package com.bamnat.app.ui.onboarding

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Info
import com.bamnat.app.ui.withBar
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import com.bamnat.app.ui.components.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.bamnat.app.AppContainer
import com.bamnat.app.data.ConsentChecks
import com.bamnat.app.data.RecommendedSettings
import com.bamnat.app.sleep.HealthSleep
import com.bamnat.app.ui.components.SystemSettings
import com.bamnat.app.ui.components.markNotificationAsked
import com.bamnat.core.consent.ConsentItem
import com.bamnat.core.consent.RecommendedFeature
import com.bamnat.core.consent.RecommendedPlan
import com.bamnat.core.consent.SleepDevice
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.launch

/**
 * [ONBOARD-03] 추천 기능 한 번에 켜기: one screen in place of the consent pages. The features worth having
 * are already switched on, with why it is fine to leave them on and where to turn them off; one tap saves
 * them and asks the phone, in turn, for what they need ([RecommendedPlan.asks]). Each question can be
 * refused and the next one comes; nothing that goes to a server is here (D42). Background location and
 * notification access are asked on their own screens, right after their own notice (Play policy).
 * [ONBOARD-04] [firstStart] false is the same screen from settings or the 1.22 update notice: the phone's
 * own values, saved as they are.
 */
@Composable
fun RecommendedScreen(
    container: AppContainer, firstStart: Boolean, onDone: () -> Unit, onBrowse: (() -> Unit)? = null,
    /** [SETUP-15] The kind of work picked on the screen before; null when skipped (and from settings). */
    style: WorkStyle? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val available = remember { ConsentChecks.available(context, container.healthSleep, container.consentStore) }
    val start = remember { container.settingsRepository.current }
    // [SETUP-15] The first start shows only that kind of work's notices; from settings every one, as the phone has them.
    val plan = remember(style, firstStart) { RecommendedPlan.forStyle(style.takeIf { firstStart }) }
    var on by rememberSaveable {
        mutableStateOf((if (firstStart) plan.start else RecommendedSettings.on(start)).map { it.key })
    }
    var watch by rememberSaveable { mutableStateOf(!firstStart && start.healthSleep) }
    // [ONBOARD-05] Usage statistics go to a server: their own yes, never ticked beforehand (D42).
    var stats by rememberSaveable { mutableStateOf(false) }
    // 1.39 사용자 결정 2026-11-08 (수면은 밤낮의 핵심, 첫 설정 끝에 한 번 권하기): which place of the bar 잠 takes; -1 keeps the bar.
    val sleepKey = com.bamnat.core.layout.TabSlots.FEATURE + com.bamnat.core.guide.Place.SLEEP.name
    val bar = remember(start) { com.bamnat.core.layout.TabSlots.resolve(start.tabSlots, start.tabOrder, com.bamnat.app.ui.TabPicks.known) }
    val sleepSwaps = remember(bar, firstStart) { if (firstStart) com.bamnat.core.layout.TabSlots.swapChoices(bar, sleepKey) else emptyList() }
    var sleepSlot by rememberSaveable { mutableIntStateOf(-1) }

    // Read again when the user comes back from a settings screen or a system question.
    var refresh by remember { mutableIntStateOf(0) }
    var granted by remember { mutableStateOf(emptySet<ConsentItem>()) }
    LaunchedEffect(refresh) { granted = ConsentChecks.granted(context, container.healthSleep) }

    // The questions in turn: null while not asking; [asked] is how far it got, kept across a turned phone.
    var queue by rememberSaveable { mutableStateOf<List<String>?>(null) }
    var asked by rememberSaveable { mutableIntStateOf(0) }
    var waitingForSettings by rememberSaveable { mutableStateOf(false) }
    val finish = {
        if (firstStart) container.consentStore.markFirstStartDone(available)
        container.consentStore.markRecommendOffered()
        onDone()
    }
    val advance = { asked++; refresh++ }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        refresh++
        if (waitingForSettings) { waitingForSettings = false; advance() }
    }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { advance() }
    val calendar = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { advance() }
    val health = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { got ->
        if (got.containsAll(HealthSleep.PERMISSIONS)) turnOnSleep(container, scope)
        advance()
    }
    val current = queue?.getOrNull(asked)?.let(ConsentItem::ofKey)
    LaunchedEffect(queue, asked) {
        val q = queue ?: return@LaunchedEffect
        if (asked >= q.size) { queue = null; finish(); return@LaunchedEffect }
        when (current) {
            ConsentItem.NOTIFICATIONS -> if (Build.VERSION.SDK_INT >= 33) {
                markNotificationAsked(context)
                notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else { waitingForSettings = true; SystemSettings.appNotifications(context) }
            ConsentItem.ALARMS -> {
                val pkg = Uri.parse("package:${context.packageName}")
                waitingForSettings = true
                if (Build.VERSION.SDK_INT >= 34) SystemSettings.open(context, Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkg))
                else if (Build.VERSION.SDK_INT >= 31) SystemSettings.open(context, Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkg))
                else { waitingForSettings = false; advance() }
            }
            ConsentItem.CALENDAR -> calendar.launch(ConsentChecks.READ_CALENDAR)
            ConsentItem.SLEEP -> when (container.healthSleep.sdkStatus()) {
                HealthConnectClient.SDK_AVAILABLE ->
                    if (container.healthSleep.granted()) { turnOnSleep(container, scope); advance() }
                    else runCatching { health.launch(HealthSleep.PERMISSIONS) }.onFailure { advance() }
                else -> advance()
            }
            // [STAT-01] An app setting with its own yes, not a system question: nothing to ask here.
            ConsentItem.USAGE_STATS, null -> advance()
        }
    }

    val save: (Boolean) -> Unit = { ask ->
        val chosen = on.mapNotNull(RecommendedFeature::ofKey).toSet()
        container.settingsRepository.update {
            RecommendedSettings.apply(it, chosen).let { s -> if (watch) s else s.copy(healthSleep = false) }.let { s ->
                if (sleepSlot !in sleepSwaps) s
                else s.withBar(com.bamnat.core.layout.TabSlots.replace(
                    com.bamnat.core.layout.TabSlots.resolve(s.tabSlots, s.tabOrder, com.bamnat.app.ui.TabPicks.known), sleepSlot, sleepKey))
            }
        }
        if (sleepSlot in sleepSwaps) container.telemetry.event("bar_sleep", "setup")
        container.consentStore.setSleepDevice(if (watch) SleepDevice.WEARS else SleepDevice.NONE)
        if (firstStart && stats) container.setUsageStats(true)
        val asks = if (ask) RecommendedPlan.asks(available, granted, watch) else emptyList()
        if (asks.isEmpty()) finish() else { asked = 0; queue = asks.map { it.key } }
    }

    // [A11Y-04] At a big font the pinned buttons took half the screen and the features scrolled in a slit
    // (notes/1.22-285/F.md 4장 3번): from 150% the buttons go at the end of the one page that scrolls.
    val pinned = androidx.compose.ui.platform.LocalDensity.current.fontScale < 1.5f
    val actions: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth().padding(bottom = 12.dp, top = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // [ONBOARD-06] With large text the 허용할 것 card can sit below the fold: what the button asks, said beside it.
            val toAsk = RecommendedPlan.asks(available, granted, watch)
            if (queue == null && toAsk.isNotEmpty()) {
                Text("누르면 휴대폰이 허용 ${toAsk.size}개를 차례로 물어요. 거절해도 넘어가요",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth())
            }
            Button(onClick = { save(true) }, enabled = queue == null, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Text(if (firstStart) "추천대로 켜고 시작하기" else "이대로 저장")
            }
            if (firstStart) {
                TextButton(onClick = { save(false) }, enabled = queue == null, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("허용은 나중에 할게요")
                }
                // [EASY-01] 우선 둘러볼게요: the recommended features on, nothing asked yet, and an example calendar next.
                if (onBrowse != null) {
                    TextButton(onClick = { onBrowse(); save(false) }, enabled = queue == null, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text("우선 둘러볼게요")
                    }
                }
            }
        }
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 600.dp).fillMaxSize().padding(horizontal = 20.dp)) {
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!firstStart) {
                            IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") }
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(if (firstStart) "밤낮을 제대로 쓰는 설정" else "추천 기능 한 번에 켜기",
                            style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                    }
                    // [SETUP-15] Said once, so the shorter list reads as chosen for this work, not as missing.
                    if (firstStart && style != null && style != WorkStyle.SHIFT) {
                        Text("${com.bamnat.app.ui.setup.STYLE_CHOICES.first { it.style == style }.title.substringBefore(" · ")}에 맞춰 골라 두었어요.", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    Text(plan.reassure, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    val features = if (firstStart) plan.features else RecommendedFeature.entries
                    if (features.isNotEmpty() || ConsentItem.SLEEP in available) Section("켜 둘 기능") {
                        features.forEachIndexed { i, f ->
                            if (i > 0) HorizontalDivider()
                            SwitchLine(f.title, f.what, f.okay, f.key in on) { v -> on = if (v) on + f.key else on - f.key }
                        }
                        if (ConsentItem.SLEEP in available) {
                            if (features.isNotEmpty()) HorizontalDivider()
                            SwitchLine("워치로 잰 잠 함께 보기", "워치·링이 잰 수면으로 부족한 잠을 계산해요",
                                "워치나 링을 차고 잘 때만 켜요. 수면만 읽고 휴대폰 밖으로 보내지 않아요", watch) { watch = it }
                        }
                        Text("이미 켜져 있어요: ${plan.alreadyOn}", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        plan.leftOff?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    } else Notice(Icons.Filled.CheckCircle, "이미 켜져 있어요: ${plan.alreadyOn}." + (plan.leftOff?.let { " $it" } ?: ""))

                    // 1.39 사용자 2026-11-08 ("잠 기능도 첫 설정에서 '이런 것도 돼요'로 알려 줘"): every first setup, with the bar question.
                    if (firstStart) SleepBarOffer(bar, sleepSwaps, style, sleepSlot,
                        soundOn = remember { runCatching { container.flags.on(com.bamnat.core.flags.FlagCatalog.SLEEP_SOUND) }.getOrDefault(false) }) { sleepSlot = it }

                    val asks = RecommendedPlan.shown(available, watch)
                    if (asks.isNotEmpty()) Section("허용할 것") {
                        Text(if (firstStart) "아래 버튼을 누르면 휴대폰이 차례로 물어요. 하나를 거절해도 다음으로 넘어가요."
                            else "꺼진 것만 휴대폰이 차례로 물어요.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        asks.forEach { item -> PermissionLine(item, item in granted, asking = item == current) }
                    }

                    Notice(Icons.Filled.Lock, "근무표·메모·급여는 이 휴대폰에만 있어요. 위 기능은 서버로 아무것도 보내지 않아요.")
                    Notice(Icons.Outlined.Info, "끄고 싶으면 ${RecommendedPlan.TURN_OFF}에서 언제든 꺼요. 설정 찾기에 ‘끄기’라고 써도 돼요. " +
                        "출퇴근 자동 기록(위치)과 결제 알림 읽기는 쓰는 화면에서 따로 켜요.")
                    if (firstStart) StatsLine(stats) { stats = it }
                    if (!pinned) actions()
                }
                if (pinned) actions()
            }
        }
    }
}

/** Health Connect allowed: read sleep from now on, as 설정 › 알림과 잠 does. */
private fun turnOnSleep(container: AppContainer, scope: kotlinx.coroutines.CoroutineScope) {
    container.settingsRepository.update { it.copy(healthSleep = true) }
    scope.launch { container.healthSleep.refresh(true, force = true) }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
            content()
        }
    }
}

/**
 * 1.39 사용자 결정 2026-11-08: '잠을 아래 메뉴에 둘까요?' once, at the end of the first setup. Nothing is picked beforehand (the bar
 * stays as it is unless a place is chosen); the place chosen becomes 잠 and its screen waits under the 나 button.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun SleepBarOffer(bar: List<com.bamnat.core.layout.TabSlots.Slot>, swaps: List<Int>, style: WorkStyle?, chosen: Int, soundOn: Boolean, onPick: (Int) -> Unit) {
    Section("잠도 밤낮이 챙겨요") {
        SleepPromo.lines(style, soundOn).forEach { line ->
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Outlined.Bedtime, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 2.dp, end = 8.dp).size(16.dp))
                Text(line, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Text("잠 기록과 소리는 이 휴대폰 안에만 남아요. 일정에 맞춘 참고용이고 진단이 아니에요.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (swaps.isEmpty()) return@Section
        HorizontalDivider()
        Text("아래 메뉴에 ‘잠’을 둘까요?", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text("고른 칸이 ‘잠’이 되고, 그 칸의 화면은 위쪽 ‘나’ 단추에서 열려요. 나중에 ‘나 › 아래 메뉴 고치기’에서 바꿔요.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            com.bamnat.app.ui.components.FilterChip(selected = chosen < 0, onClick = { onPick(-1) },
                label = { Text("두지 않기") })
            swaps.forEach { i ->
                val name = bar[i].let { s ->
                    (s as? com.bamnat.core.layout.TabSlots.Group)?.name
                        ?: com.bamnat.app.ui.TabPicks.of(s.keys.first(), style)?.label ?: s.keys.first()
                }
                com.bamnat.app.ui.components.FilterChip(selected = chosen == i, onClick = { onPick(i) },
                    label = { Text("$name 대신") },
                    leadingIcon = if (chosen == i) ({ Icon(Icons.Outlined.Bedtime, null, Modifier.size(18.dp)) }) else null)
            }
        }
    }
}

/**
 * 1.39 사용자 2026-11-08: what 잠 does, in the words of the kind of work (야간 is said to 교대 only, 오늘의 흐름 to work in shifts and
 * hours); 소리 재기 only while its flag is open.
 */
internal object SleepPromo {
    fun lines(style: WorkStyle?, soundOn: Boolean): List<String> = listOfNotNull(
        "‘잠들었어요’·‘깼어요’ 두 번이면 잔 시간이 쌓이고, ${com.bamnat.core.sleep.SleepInsights.groupsWord(style)} 평균을 따로 보여 줘요",
        "근무에 맞춰 오늘 잘 시간과 낮잠 때를 달력 위 한 줄로 알려 줘요".takeIf { style == null || style.usesPayEngine },
        "잠드는 소리: 빗소리·파도·소음을 섞어 틀고, 정한 시간에 서서히 꺼져요",
        "자는 동안 코골이·잠꼬대를 휴대폰 안에서만 재고, 아침에 들어 봐요".takeIf { soundOn },
        "깬 뒤 아침 한 장에 답하면 피곤한 아침과 함께 온 것(늦은 카페인·야식…)을 내 기록에서 찾아 줘요",
    )
}

/** A feature: its name, what it does, why it is fine on, and its switch; the whole row toggles. */
@Composable
private fun SwitchLine(title: String, what: String, okay: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).toggleable(value = on, role = Role.Switch, onValueChange = onChange).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(what, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(okay, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = on, onCheckedChange = null)
    }
}

/**
 * [ONBOARD-05] 사용 통계 보내기 on the first start: a box the user ticks, said plainly as going to the
 * developer, and that the app is the same without it (D42, 개인정보 보호법 제22조). What goes, why and how long
 * it is kept are said before the yes (제15조 제2항); it is not called 익명, as the policy treats it as personal data.
 */
@Composable
internal fun StatsLine(on: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(value = on, role = Role.Checkbox, onValueChange = onChange).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Checkbox(checked = on, onCheckedChange = null)
        Column(Modifier.weight(1f)) {
            Text("개발자에게 사용 통계 보내기 (선택)", style = MaterialTheme.typography.bodyLarge)
            Text("어떤 기능을 하루에 몇 번 썼는지만 보내 앱을 고치는 데 써요. 근무·금액·메모·휴대폰 정보는 보내지 않고, " +
                "서버에는 날짜별 합계만 400일 남아요. 체크하지 않아도 앱은 똑같이 돼요. 설정 › 개인정보·앱 정보에서 바꿔요.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** What the phone will be asked, and whether it is allowed already. */
@Composable
private fun PermissionLine(item: ConsentItem, allowed: Boolean, asking: Boolean) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(if (allowed) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked, null,
            tint = if (allowed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title(), style = MaterialTheme.typography.bodyLarge)
            Text(
                when {
                    allowed -> "허용되어 있어요"
                    asking -> "지금 휴대폰이 묻고 있어요"
                    else -> item.why()
                },
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Notice(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        }
    }
}
