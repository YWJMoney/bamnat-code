package com.bamnat.app.ui.sleep

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.Watch
import com.bamnat.app.ui.components.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import com.bamnat.app.ui.components.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Tune
import com.bamnat.app.ui.components.Button
import androidx.compose.material3.Surface
import com.bamnat.app.ui.settings.InquiryLinks
import com.bamnat.app.ui.settings.InquiryStart
import com.bamnat.app.ui.settings.SettingsLinks
import com.bamnat.app.ui.settings.SettingsPage
import com.bamnat.app.AppContainer
import com.bamnat.app.data.AppSettings
import com.bamnat.app.ui.Fmt
import com.bamnat.app.ui.UndoMessage
import com.bamnat.app.ui.components.SectionCard
import com.bamnat.app.sleep.SleepAutoWatch
import com.bamnat.app.sleep.SleepRecordService
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import com.bamnat.app.ui.components.StatTile
import com.bamnat.app.ui.components.TimePickerDialog
import com.bamnat.app.ui.theme.LocalCalendarColors
import com.bamnat.core.sleep.SleepDebt
import com.bamnat.core.sleep.SleepEntry
import com.bamnat.core.sleep.SleepInsights
import com.bamnat.core.sleep.SleepMerge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import com.bamnat.core.time.Today

/**
 * [SLP-16] The sleep log as something to use: what the two taps are for, the user's own averages
 * after a night shift, before a morning start and on a day off, a few plain findings, the last two
 * weeks as bars, and every record with a way to fix, add or delete one.
 */
class SleepViewModel(private val container: AppContainer) : ViewModel() {
    val settings: StateFlow<AppSettings> = container.settingsRepository.settings

    /** 1.38 [HLTH-01] [compare]: the last 4 weeks of 계획 대 실제, null while `health.compare` is off (Phase 3, 꺼진 채). */
    data class State(val log: List<SleepEntry>, val report: SleepInsights.Report, val today: LocalDate,
        val compare: com.bamnat.core.sleep.PlanVsActual.Summary? = null)

    val state: StateFlow<State?> = combine(
        container.sleepRepository.recent(days = SleepInsights.WINDOW_DAYS + SleepDebt.WINDOW_DAYS),
        container.healthSleep.sleeps,
        // [SRCH-13] 1.29 AS: the shared reading (live), not a whole-history read of its own.
        container.scheduleRepository.live,
        settings,
        container.flags.byKey,
    ) { manual, measured, snapshot, s, flags ->
        val log = SleepMerge.merge(manual, measured)
        val today = Today.date()
        val compare = if (flags[com.bamnat.core.flags.FlagCatalog.HEALTH_COMPARE]?.on != true) null else com.bamnat.core.sleep.PlanVsActual.summary(
            com.bamnat.core.sleep.PlanVsActual.nights(log, snapshot.resolver, today.minusDays(com.bamnat.core.sleep.PlanVsActual.WINDOW_DAYS - 1), today,
                s.alarmsEnabled, s.sleepTargetMinutes, s.commuteMinutes))
        State(log, SleepInsights.report(log, snapshot.resolver, today, s.sleepTargetMinutes), today, compare)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val health = container.healthSleep.state

    /** 1.38 [SLEEP-01][SLEEP-03] The top of the screen: what the phone caught, and how the user woke. */
    data class Morning(val offers: List<com.bamnat.core.sleep.SleepAuto.Segment>, val sleep: SleepEntry?,
        val check: com.bamnat.core.sleep.SleepCheck?, val autoShown: Boolean, val autoOn: Boolean)

    /** Bumped when an answer lives outside the database (아니에요 on a caught sleep). */
    private val autoTick = kotlinx.coroutines.flow.MutableStateFlow(0)

    val morning: StateFlow<Morning?> = combine(
        container.sleepRepository.recent(days = 3),
        container.healthSleep.sleeps,
        container.sleepRepository.checks(days = 3),
        combine(settings, container.flags.byKey) { s, f -> s.sleepAuto to (f[com.bamnat.core.flags.FlagCatalog.SLEEP_AUTO]?.on == true) },
        autoTick,
    ) { manual, measured, checks, (autoOn, autoFlag), _ ->
        val log = SleepMerge.merge(manual, measured)
        val now = Today.dateTime()
        val offers = if (autoOn && autoFlag) SleepAutoWatch.candidates(container.appContext, manual + measured, now) else emptyList()
        val sleep = com.bamnat.core.sleep.SleepMorning.latest(log, now)
        Morning(offers, sleep, sleep?.let { com.bamnat.core.sleep.SleepMorning.checkFor(it, checks) }, autoFlag, autoOn)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** 1.38 [SLEEP-06][SLEEP-07] The last measured night and its sounds; null while `sleep.sound` is off. */
    data class Night(val last: SleepRecordService.Companion.LastNight?, val events: List<com.bamnat.app.data.db.SleepEventEntity>,
        val work: List<TimelineSpan> = emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val night: StateFlow<Night?> = combine(com.bamnat.app.sleep.SleepRecordService.recording, container.flags.byKey) { rec, f ->
        rec to (f[com.bamnat.core.flags.FlagCatalog.SLEEP_SOUND]?.on == true)
    }.flatMapLatest { (_, on) ->
        if (!on) kotlinx.coroutines.flow.flowOf(null)
        else {
            val last = SleepRecordService.lastNight(container.appContext)
            if (last == null) kotlinx.coroutines.flow.flowOf(Night(null, emptyList()))
            else container.sleepRepository.events(last.start, last.end.plusMinutes(1)).map { ev ->
                // [SLEEP-21] The work around that night, on the same axis.
                val resolver = container.scheduleRepository.snapshot.first().resolver
                val work = (last.start.toLocalDate().minusDays(1)..last.end.toLocalDate()).let { r ->
                    generateSequence(r.start) { it.plusDays(1) }.takeWhile { !it.isAfter(r.endInclusive) }.toList()
                }.flatMap { com.bamnat.core.sleep.SleepSchedule.work(resolver.resolve(it)) }
                    .map { TimelineSpan(it.from, it.to, work = true, color = it.colorArgb?.let { c -> androidx.compose.ui.graphics.Color(c.toInt()) }) }
                Night(last, ev, work)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** 1.38 [SLEEP-10][SLEEP-13][SLEEP-20] 적응 한 줄, 원인 찾기, 회복 도전. */
    data class Extras(val adapt: com.bamnat.core.sleep.ShiftAdapt.Step?, val causes: List<com.bamnat.core.sleep.SleepCauses.Finding>,
        val challenge: com.bamnat.core.sleep.RecoveryChallenge.Progress?)

    private val challengeStart = kotlinx.coroutines.flow.MutableStateFlow(SleepChallengeStore.start(container.appContext))

    val extras: StateFlow<Extras?> = combine(
        container.scheduleRepository.live, container.sleepRepository.checks(days = 90), state, challengeStart,
    ) { snapshot, checks, st, start ->
        val today = Today.date()
        val work = (-4L..com.bamnat.core.sleep.ShiftAdapt.LEAD_DAYS.toLong() + 1).flatMap { com.bamnat.core.sleep.SleepSchedule.work(snapshot.resolver.resolve(today.plusDays(it))) }
        val challenge = start?.let { from ->
            val s = settings.value
            com.bamnat.core.sleep.RecoveryChallenge.progress(from, com.bamnat.core.sleep.PlanVsActual.nights(st?.log.orEmpty(), snapshot.resolver,
                from, today, s.alarmsEnabled, s.sleepTargetMinutes, s.commuteMinutes), today)
        }
        Extras(com.bamnat.core.sleep.ShiftAdapt.stepFor(today, work), com.bamnat.core.sleep.SleepCauses.find(checks), challenge)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** 1.38 [SLEEP-22] 바꾼 것 전후: the day the user marked and what they changed, with the 7 days on each side. */
    data class Mark(val day: LocalDate, val what: String, val result: com.bamnat.core.sleep.BeforeAfter.Result?)

    private val markDay = kotlinx.coroutines.flow.MutableStateFlow(SleepChallengeStore.mark(container.appContext))

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val mark: StateFlow<Mark?> = markDay.flatMapLatest { m ->
        if (m == null) kotlinx.coroutines.flow.flowOf(null)
        else {
            val (day, what) = m
            val from = day.minusDays(com.bamnat.core.sleep.BeforeAfter.DAYS + 1).atStartOfDay()
            val to = day.plusDays(com.bamnat.core.sleep.BeforeAfter.DAYS + 1).atStartOfDay()
            combine(container.sleepRepository.events(from, to), container.sleepRepository.checks(days = 120), state) { events, checks, st ->
                val zone = Today.zone()
                fun local(ms: Long) = java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(ms), zone)
                // A measured night is a sleep with sounds heard in it; its snoring by the morning it ended.
                val snore = st?.log.orEmpty().filter { it.end != null && !it.end!!.isBefore(from) && it.start.isBefore(to) }.mapNotNull { e ->
                    val inside = events.filter { ev -> local(ev.startMillis).let { t -> !t.isBefore(e.start) && t.isBefore(e.end) } }
                    if (inside.isEmpty()) null
                    else e.end!!.toLocalDate() to com.bamnat.core.sleep.SoundSummary.of(inside.map { ev ->
                        com.bamnat.core.sleep.SoundEvent(ev.startMillis, ev.endMillis, com.bamnat.core.sleep.SoundKind.of(ev.kind), ev.level)
                    }).snoreMinutes
                }.toMap()
                val mood = checks.mapNotNull { c -> c.mood?.let { c.end.toLocalDate() to it.value } }.toMap()
                Mark(day, what, com.bamnat.core.sleep.BeforeAfter.compare(day, snore, mood))
            }
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun markChange(what: String) {
        val day = Today.date()
        SleepChallengeStore.setMark(container.appContext, day, what)
        markDay.value = day to what
    }
    fun clearMark() { SleepChallengeStore.setMark(container.appContext, null, null); markDay.value = null }

    fun startChallenge() { challengeStart.value = SleepChallengeStore.begin(container.appContext, Today.date()) }
    fun stopChallenge() { SleepChallengeStore.end(container.appContext); challengeStart.value = null }

    fun keepClip(e: com.bamnat.app.data.db.SleepEventEntity, keep: Boolean) {
        container.appScope.launch { container.sleepRepository.keepClip(e.id, keep) }
    }

    fun acceptAuto(s: com.bamnat.core.sleep.SleepAuto.Segment, start: LocalDateTime = s.start, end: LocalDateTime = s.end) {
        container.appScope.launch {
            val fixed = start != s.start || end != s.end
            if (fixed) {
                // Into the log as the user's own times; the caught piece is not offered again, and trust starts over.
                container.sleepRepository.add(start, end)
                SleepAutoWatch.decline(container.appContext, s)
            } else SleepAutoWatch.accept(container.appContext, s)
            autoTick.value++
            _messages.emit(UndoMessage("${SleepInsights.hours(Duration.between(start, end).toMinutes())} 잠을 넣었어요"))
        }
    }

    fun declineAuto(s: com.bamnat.core.sleep.SleepAuto.Segment) {
        SleepAutoWatch.decline(container.appContext, s)
        autoTick.value++
    }

    fun saveSound(mix: com.bamnat.core.sleep.NoiseMix, minutes: Int) {
        container.settingsRepository.update { it.copy(noiseMix = mix.encode(), noiseTimerMinutes = minutes) }
    }

    fun setSmartWake(minutes: Int) {
        container.settingsRepository.update { it.copy(smartWakeMinutes = minutes) }
        // The window follows the next alarm: plan it again now.
        container.appScope.launch { container.rescheduleAlarms() }
    }

    fun setNightLook(on: Boolean) = container.settingsRepository.update { it.copy(sleepNightLook = on) }

    fun setAuto(on: Boolean) {
        // The notices job follows the setting and subscribes or lets go (SleepAutoWatch.sync).
        container.settingsRepository.update { it.copy(sleepAuto = on) }
    }

    fun answer(sleep: SleepEntry, check: com.bamnat.core.sleep.SleepCheck?, mood: com.bamnat.core.sleep.SleepMood?,
        tags: Set<com.bamnat.core.sleep.SleepTag>) {
        container.appScope.launch { container.sleepRepository.answer(sleep, mood, tags, check?.note, check) }
    }

    fun unanswer(check: com.bamnat.core.sleep.SleepCheck) {
        container.appScope.launch { container.sleepRepository.unanswer(check.id) }
    }

    private val _messages = MutableSharedFlow<UndoMessage>(extraBufferCapacity = 4)
    val messages: SharedFlow<UndoMessage> = _messages.asSharedFlow()

    fun refreshHealth() {
        viewModelScope.launch {
            container.healthSleep.refresh(settings.value.healthSleep)
            // 1.38 [SLEEP-09] 헬스 커넥트에 쓰기: the last 30 days of the app's own sleeps; the same sleep replaces itself.
            if (settings.value.healthWrite) container.healthSleep.write(container.sleepRepository.recent(days = 30).first())
        }
    }

    fun add(start: LocalDateTime, end: LocalDateTime) {
        container.appScope.launch {
            val id = container.sleepRepository.add(start, end)
            _messages.emit(UndoMessage("${SleepInsights.hours(Duration.between(start, end).toMinutes())} 수면을 적었어요") {
                container.sleepRepository.delete(id)
            })
        }
    }

    fun edit(before: SleepEntry, start: LocalDateTime, end: LocalDateTime?) {
        container.appScope.launch {
            container.sleepRepository.edit(before.id, start, end)
            _messages.emit(UndoMessage("기록을 고쳤어요") { container.sleepRepository.edit(before.id, before.start, before.end) })
        }
    }

    fun delete(entry: SleepEntry) {
        container.appScope.launch {
            container.sleepRepository.delete(entry.id)
            _messages.emit(UndoMessage("기록을 지웠어요") { container.sleepRepository.restore(entry) })
        }
    }

    fun runUndo(m: UndoMessage) { m.undo?.let { u -> container.appScope.launch { u() } } }

    /** [UX-06] "잠들었어요" on the sleep screen too, as on the calendar; the 자는 중 notice follows the log. */
    fun fellAsleep() {
        container.appScope.launch {
            val asleep = container.sleepRepository.fellAsleep()
            val at = Fmt.time(asleep.entry.start.toLocalTime())
            _messages.emit(
                if (asleep.started) UndoMessage("${at}부터 자는 중으로 적었어요") { container.sleepRepository.delete(asleep.entry.id) }
                else UndoMessage("이미 ${at}부터 자는 중이에요"),
            )
        }
    }

    /** [UX-06] "깼어요": the open sleep ends now. */
    fun wokeUp() {
        container.appScope.launch {
            val sleep = container.sleepRepository.wokeUp() ?: return@launch
            val minutes = sleep.minutes ?: 0L
            _messages.emit(UndoMessage(
                if (minutes > SleepDebt.MAX_SLEEP_MINUTES) "16시간이 넘게 열려 있던 기록이라 수면 통계에서 뺐어요"
                else "${SleepInsights.hours(minutes)} 잤어요",
            ) { container.sleepRepository.reopen(sleep.id) })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepScreen(vm: SleepViewModel, wide: Boolean, onBack: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    // 1.38 [DSG-07] Warm and dark at night and while asleep, so opening the screen in bed does not glare.
    val sleeping = state?.log?.any { it.end == null && !it.imported } == true
    com.bamnat.app.ui.theme.NightSurface(com.bamnat.app.ui.theme.rememberNightMode(settings.sleepNightLook, sleeping)) {
        SleepScreenBody(vm, wide, onBack)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SleepScreenBody(vm: SleepViewModel, wide: Boolean, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val health by vm.health.collectAsStateWithLifecycle()
    val morning by vm.morning.collectAsStateWithLifecycle()
    val night by vm.night.collectAsStateWithLifecycle()
    val extras by vm.extras.collectAsStateWithLifecycle()
    val mark by vm.mark.collectAsStateWithLifecycle()
    var fixing by remember { mutableStateOf<com.bamnat.core.sleep.SleepAuto.Segment?>(null) }
    var editing by remember { mutableStateOf<SleepEntry?>(null) }
    var adding by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm) {
        vm.refreshHealth()
        vm.messages.collect { m ->
            val r = snackbar.showSnackbar(m.message, actionLabel = m.undo?.let { m.actionLabel }, duration = SnackbarDuration.Short)
            if (r == SnackbarResult.ActionPerformed) vm.runUndo(m)
        }
    }
    val target = settings.sleepTargetMinutes

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("수면 기록") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") } },
                // [UX-06] Where the target and the watch are set, and help, from the screen that uses them.
                actions = {
                    IconButton(onClick = { SettingsLinks.open(SettingsPage.NOTICES, "목표 수면 시간") }) {
                        Icon(Icons.Outlined.Tune, "수면 설정")
                    }
                    IconButton(onClick = { InquiryLinks.ask(InquiryStart(about = "수면 기록", help = true)) }) {
                        Icon(Icons.AutoMirrored.Outlined.HelpOutline, "수면 기록 도움말")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { adding = true }, icon = { Icon(Icons.Filled.Add, "기록 추가") }, text = { Text("기록 추가") })
        },
    ) { padding ->
        val s = state ?: return@Scaffold
        val r = s.report
        val summary: @Composable ColumnScope.() -> Unit = {
            morning?.let { m ->
                // [SLEEP-01] Newest first: the one the user just woke from.
                m.offers.lastOrNull()?.let { o ->
                    AutoOfferCard(o, onYes = { vm.acceptAuto(o) }, onFix = { fixing = o }, onNo = { vm.declineAuto(o) })
                }
                // [SLEEP-03] 아침 한 장, once there is no caught sleep left to settle.
                if (m.offers.isEmpty()) m.sleep?.let { sl ->
                    MorningCard(sl, m.check, onAnswer = { mood, tags -> vm.answer(sl, m.check, mood, tags) },
                        onUndo = { m.check?.let(vm::unanswer) })
                }
            }
            NowCard(s.log.firstOrNull { it.end == null && !it.imported }, onAsleep = vm::fellAsleep, onAwake = vm::wokeUp)
            // 1.38 [SLEEP-04] 잠드는 소리, next to 잠들었어요.
            SleepSoundCard(settings.noiseMix, settings.noiseTimerMinutes, vm::saveSound)
            // 1.38 [SLEEP-06][SLEEP-07] 자는 동안 소리, once `sleep.sound` is open.
            night?.let { n -> SleepNightCard(n.last, n.events, vm::keepClip, settings.smartWakeMinutes, vm::setSmartWake, n.work) }
            // Design: an empty sleep screen points at where to start.
            if (s.log.isEmpty()) com.bamnat.app.ui.components.EmptyState("아직 잠 기록이 없어요",
                "자기 전에 ‘잠들었어요’, 깨면 ‘깼어요’를 누르거나 아래 ‘기록 추가’로 적어요",
                com.bamnat.app.ui.components.KaniMood.Point)
            else if (r.loggedDays < 7) HowItWorks(settings.workStyle)
            WeekTiles(r, target)
            // 1.38 [SLEEP-10][SLEEP-13][SLEEP-20]
            extras?.adapt?.let { AdaptCard(it) }
            if (r.findings.isNotEmpty()) Findings(r.findings)
            extras?.let { CausesCard(it.causes) }
            Groups(r, target)
            s.compare?.let { PlanCompare(it) }
            extras?.let { e -> ChallengeCard(e.challenge, vm::startChallenge, vm::stopChallenge) }
            // 1.38 [SLEEP-22] 바꾼 것 전후 비교.
            if (s.log.isNotEmpty()) BeforeAfterCard(mark, vm::markChange, vm::clearMark)
        }
        val history: @Composable ColumnScope.() -> Unit = {
            Bars(s.log, s.today, target)
            Records(s.log, r, s.today) { e -> if (!e.imported) editing = e }
            morning?.takeIf { it.autoShown }?.let { m -> SectionCard(title = null) { SleepAutoSwitch(m.autoOn, vm::setAuto) } }
            SectionCard(title = null) {
                com.bamnat.app.ui.settings.SwitchRow("밤에는 어두운 색", "밤 10시~아침 6시와 자는 중에는 이 화면을 눈부시지 않은 어두운 색으로 보여 줘요",
                    settings.sleepNightLook) { on -> vm.setNightLook(on) }
            }
            healthNote(health)?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("수면 기록은 이 휴대폰과 백업 파일에만 남아요. 일정에 맞춘 참고용 안내이고, 진단이 아니에요.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (wide) {
            Row(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) { summary(); Spacer(Modifier.height(80.dp)) }
                Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) { history(); Spacer(Modifier.height(80.dp)) }
            }
        } else {
            Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) { summary(); history(); Spacer(Modifier.height(80.dp)) }
        }
    }

    if (adding) {
        SleepEditDialog(null, onDismiss = { adding = false }, onDelete = null) { start, end ->
            adding = false
            if (end != null) vm.add(start, end)
        }
    }
    fixing?.let { o ->
        SleepEditDialog(SleepEntry(0, o.start, o.end), onDismiss = { fixing = null }, onDelete = null) { start, end ->
            fixing = null
            if (end != null) vm.acceptAuto(o, start, end)
        }
    }
    editing?.let { e ->
        SleepEditDialog(e, onDismiss = { editing = null }, onDelete = { vm.delete(e); editing = null }) { start, end ->
            editing = null
            vm.edit(e, start, end)
        }
    }
}

/**
 * 1.38 [HLTH-01][P3-04] 계획 대 실제 (Phase 3 M23): the last 4 weeks' planned sleeps against what was recorded, newest first.
 * "견주기" only: no 예측, no 진단 (설계 2-3 위험). Shown while `health.compare` is on.
 */
@Composable
private fun PlanCompare(sum: com.bamnat.core.sleep.PlanVsActual.Summary) {
    SectionCard(title = "근무에 맞춘 계획과 실제 잠") {
        Text(sum.headline ?: "계획한 잠을 ${com.bamnat.core.sleep.PlanVsActual.MIN_FOR_SUMMARY}번 넘게 기록하면 4주 평균을 보여 줘요",
            style = MaterialTheme.typography.titleSmall)
        sum.afterNightDiff?.takeIf { sum.enough }?.let { d ->
            Text(if (d < 0) "야간 뒤 잠은 계획보다 평균 ${SleepInsights.hours(-d)} 적어요" else "야간 뒤 잠은 계획만큼이에요",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val recent = sum.nights.filter { it.verdict != com.bamnat.core.sleep.PlanVsActual.Verdict.NO_RECORD }.sortedByDescending { it.planFrom }.take(6)
        for (n in recent) {
            Text("${Fmt.monthDay(n.date)} ${n.kind.label} · ${n.verdict.label}", style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 6.dp))
            Text(n.line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("근무와 알람으로 잡은 계획 잠과 워치·휴대폰이 잰 잠(또는 적은 잠)을 휴대폰 안에서 견줘요. 저장하거나 보내지 않아요.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
    }
}

/**
 * [UX-06] The two taps where the sleep is looked at: "잠들었어요", or "깼어요" while a sleep is open. Before,
 * only the calendar's day had them, so the screen about sleep could not start one.
 */
@Composable
private fun NowCard(open: SleepEntry?, onAsleep: () -> Unit, onAwake: () -> Unit) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Bedtime, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(if (open == null) "지금 자려고요?" else "${Fmt.time(open.start.toLocalTime())}부터 자는 중",
                    style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                // 1.39 [FLOW-29] 조건은 미리 (PC 실사용 11-06: 짧은 잠이 왜 안 남는지 끝에서야 알았음): said before sleeping.
                Text(if (open == null) "누르고 자면, 일어나서 ‘깼어요’로 끝내요. ${com.bamnat.core.sleep.SleepMorning.MIN_MINUTES / 60}시간 넘게 자야 아침 기분을 물어요"
                    else "일어났으면 ‘깼어요’를 눌러요",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Spacer(Modifier.width(8.dp))
            if (open == null) Button(onClick = onAsleep, modifier = Modifier.heightIn(min = 48.dp)) { Text("잠들었어요") }
            else Button(onClick = onAwake, modifier = Modifier.heightIn(min = 48.dp)) { Text("깼어요") }
        }
    }
}

/** [SLP-16] What the taps are for, said where the user first wonders. */
@Composable
private fun HowItWorks(style: com.bamnat.core.work.WorkStyle?) {
    SectionCard(title = "수면 기록은 이렇게 쓰여요") {
        listOf(
            // 1.38 PC 12.4.1 빈틈 2: 현장·가게·투자 have no 오늘의 흐름 card to press in.
            if (style?.usesPayEngine == false) "잠들 때 ‘잠들었어요’, 깼을 때 ‘깼어요’를 누르면 돼요. 오늘 날짜와 자는 중 알림에 버튼이 있어요."
            else "잠들 때 ‘잠들었어요’, 깼을 때 ‘깼어요’를 누르면 돼요. 달력 위 오늘의 흐름 카드, 오늘 날짜, 자는 중 알림에 버튼이 있어요.",
            "깜빡했으면 여기서 ‘기록 추가’로 나중에 적어도 돼요. 워치 기록은 설정 > 알림과 잠에서 헬스 커넥트를 켜면 함께 세요.",
            // 1.38 사용자 2026-11-06 (교대가 없는 유형에 주·야): 야간 뒤 is said to 교대 only.
            when (style) {
                null, com.bamnat.core.work.WorkStyle.SHIFT -> "기록이 쌓이면 야간 뒤·아침 출근 전·쉬는 날 평균을 따로 보여 주고, 퇴근길 카드가 내 평균으로 알려 줘요."
                com.bamnat.core.work.WorkStyle.INVEST -> "기록이 쌓이면 평일과 쉬는 날 평균을 따로 보여 줘요."
                else -> "기록이 쌓이면 일하는 날과 쉬는 날 평균을 따로 보여 주고, 퇴근길 카드가 내 평균으로 알려 줘요."
            },
        ).forEachIndexed { i, line ->
            Row(Modifier.padding(vertical = 3.dp)) {
                Text("${i + 1}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.width(20.dp))
                Text(line, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun WeekTiles(r: SleepInsights.Report, target: Int) {
    val w = r.week
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile("최근 7일 평균", if (w.days == 0) "—" else SleepInsights.hours(w.averageMinutes), Modifier.weight(1f),
            caption = "목표 ${SleepInsights.hours(target.toLong())}")
        StatTile("목표보다 부족", if (w.days == 0) "—" else if (w.shortMinutes == 0L) "없음" else SleepInsights.hours(w.shortMinutes),
            Modifier.weight(1f), caption = "기록한 ${w.days}일 합계", warning = w.shortMinutes >= 4 * 60)
        StatTile("기록한 날", "${r.loggedDays}일", Modifier.weight(1f), caption = "최근 ${SleepInsights.WINDOW_DAYS}일")
    }
}

@Composable
private fun Findings(findings: List<SleepInsights.Finding>) {
    val cal = LocalCalendarColors.current
    SectionCard(title = "내 기록에서 보이는 것") {
        findings.forEachIndexed { i, f ->
            if (i > 0) HorizontalDivider(Modifier.padding(vertical = 8.dp))
            val (icon, tint) = when (f.tone) {
                SleepInsights.Tone.WARN -> Icons.Filled.WarningAmber to cal.warning
                SleepInsights.Tone.GOOD -> Icons.Filled.CheckCircle to cal.income
                SleepInsights.Tone.NOTE -> Icons.Filled.Info to MaterialTheme.colorScheme.primary
            }
            Row {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.padding(top = 2.dp).size(20.dp))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(f.title, style = MaterialTheme.typography.titleSmall)
                    Text(f.text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** Average per kind of day against the target, as bars that read at any font size. */
@Composable
private fun Groups(r: SleepInsights.Report, target: Int) {
    if (r.groups.isEmpty()) return
    val cal = LocalCalendarColors.current
    SectionCard(title = "근무별 평균") {
        val max = maxOf(target.toLong(), r.groups.maxOf { it.averageMinutes }).coerceAtLeast(1)
        r.groups.forEach { g ->
            Column(Modifier.padding(vertical = 6.dp).semantics(mergeDescendants = true) {}) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(g.context.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(SleepInsights.hours(g.averageMinutes), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                        color = if (target - g.averageMinutes >= SleepInsights.SHORT_MINUTES) cal.warning else MaterialTheme.colorScheme.onSurface)
                }
                Spacer(Modifier.height(4.dp))
                Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
                    Box(Modifier.fillMaxWidth(g.averageMinutes.toFloat() / max).fillMaxHeight().clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.primary))
                }
                Text(
                    "${g.count}번 · 보통 ${g.averageBedtime?.let(SleepInsights::clock) ?: "--:--"}에 잠들어요" +
                        if (!g.enough) " · 기록이 더 있으면 정확해져요" else "",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        if (r.naps > 0) {
            Text("짧은 낮잠 ${r.naps}번은 평균에서 뺐어요.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // 1.38 [SLEEP-02] Sleeps a few hours apart with no work between are one rest, not two short nights.
        if (r.splitRests > 0) {
            Text("나눠 잔 ${r.splitRests}번은 근무 사이 한 번의 쉼으로 합쳐 셌어요.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** The last 14 days by the day the user woke up, with the target as a dashed line. */
@Composable
private fun Bars(log: List<SleepEntry>, today: LocalDate, target: Int) {
    val days = (13 downTo 0).map { today.minusDays(it.toLong()) }
    val byDay = log.filter { (it.minutes ?: 0) in 1..SleepDebt.MAX_SLEEP_MINUTES }.groupBy { it.wakeDate }
        .mapValues { (_, l) -> l.sumOf { it.minutes!! } }
    val values = days.map { byDay[it] ?: 0L }
    // 1.38 [DSG-04] 그래프 규칙: one colour, the longest (잘 잠) and the shortest (부족) only, the target dashed.
    val bars = days.zip(values).map { (d, v) ->
        com.bamnat.app.ui.components.ChartBar(
            label = if (d == today) "오늘" else Fmt.shortDate(d), value = v.toDouble(),
            tone = when {
                v <= 0 -> com.bamnat.app.ui.theme.Tone.Plain
                v < target - SleepInsights.SHORT_MINUTES -> com.bamnat.app.ui.theme.Tone.Caution
                v >= target -> com.bamnat.app.ui.theme.Tone.Good
                else -> com.bamnat.app.ui.theme.Tone.Plain
            },
            text = if (v <= 0) "기록 없음" else SleepInsights.hours(v),
        )
    }
    SectionCard(title = "최근 2주") {
        com.bamnat.app.ui.components.BarChart(bars,
            com.bamnat.app.ui.components.Baseline(target.toDouble(), "점선 목표 ${SleepInsights.hours(target.toLong())}"))
    }
}

@Composable
private fun Records(log: List<SleepEntry>, r: SleepInsights.Report, today: LocalDate, onOpen: (SleepEntry) -> Unit) {
    val contexts = r.nights.associate { it.entry.id to it.context }
    val shown = log.filter { e -> (e.wakeDate ?: e.start.toLocalDate()) >= today.minusDays(SleepInsights.WINDOW_DAYS - 1) }
    SectionCard(title = "기록 ${shown.size}개") {
        if (shown.isEmpty()) {
            Text("아직 기록이 없어요. 오늘 잠들 때 ‘잠들었어요’를 눌러 보세요.", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@SectionCard
        }
        shown.forEachIndexed { i, e ->
            if (i > 0) HorizontalDivider()
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(enabled = !e.imported) { onOpen(e) }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(if (e.imported) Icons.Filled.Watch else Icons.Filled.Bedtime, contentDescription = if (e.imported) "워치 기록" else null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(Fmt.dayTitle(e.wakeDate ?: e.start.toLocalDate()), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        listOfNotNull(
                            "${hm(e.start)}–${e.end?.let(::hm) ?: "자는 중"}",
                            contexts[e.id]?.label,
                            if (e.imported) "워치·헬스 커넥트" else null,
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(e.minutes?.let { if (it > SleepDebt.MAX_SLEEP_MINUTES) "통계 제외" else SleepInsights.hours(it) } ?: "",
                    style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

private fun hm(t: LocalDateTime) = "%02d:%02d".format(t.hour, t.minute)

private fun healthNote(state: com.bamnat.app.sleep.HealthSleep.State): String? = when (state) {
    com.bamnat.app.sleep.HealthSleep.State.READY -> "헬스 커넥트 기록을 함께 보여 줘요. 직접 적은 기록과 겹치면 직접 기록을 써요."
    com.bamnat.app.sleep.HealthSleep.State.NEEDS_PERMISSION -> "헬스 커넥트 수면 읽기 허용이 꺼져 있어요. 설정 > 알림과 잠에서 다시 켤 수 있어요."
    else -> null
}

/**
 * [SLP-16] A sleep written or corrected by hand: the night it ended on, when it began and ended.
 * The end may be the next day of the start; an open sleep ([initial] with no end) may stay open.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SleepEditDialog(
    initial: SleepEntry?,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)?,
    onSave: (LocalDateTime, LocalDateTime?) -> Unit,
) {
    val now = remember { Today.dateTime().withSecond(0).withNano(0) }
    var wake by rememberSaveable { mutableStateOf((initial?.end ?: initial?.start ?: now).toLocalDate().toString()) }
    var start by rememberSaveable { mutableStateOf((initial?.start?.toLocalTime() ?: LocalTime.of(23, 0)).toString()) }
    var end by rememberSaveable { mutableStateOf((initial?.end?.toLocalTime() ?: LocalTime.of(7, 0)).toString()) }
    var open by rememberSaveable { mutableStateOf(initial != null && initial.end == null) }
    var picking by remember { mutableStateOf<String?>(null) }
    var pickDay by remember { mutableStateOf(false) }
    val wakeDate = LocalDate.parse(wake)
    if (pickDay) {
        val last = now.toLocalDate()
        val state = androidx.compose.material3.rememberDatePickerState(initialSelectedDateMillis = wakeDate.toEpochDay() * 86_400_000L,
            selectableDates = remember(last) {
                object : androidx.compose.material3.SelectableDates {
                    override fun isSelectableDate(utcTimeMillis: Long) = !LocalDate.ofEpochDay(utcTimeMillis / 86_400_000L).isAfter(last)
                    override fun isSelectableYear(year: Int) = year <= last.year
                }
            })
        com.bamnat.app.ui.components.DatePickerDialog(onDismissRequest = { pickDay = false }, confirmButton = {
            TextButton(onClick = { state.selectedDateMillis?.let { wake = LocalDate.ofEpochDay(it / 86_400_000L).toString() }; pickDay = false }) { Text("고르기") }
        }, dismissButton = { TextButton(onClick = { pickDay = false }) { Text("닫기") } }) { androidx.compose.material3.DatePicker(state) }
    }
    val endTime = LocalTime.parse(end)
    val startTime = LocalTime.parse(start)
    // A start later in the day than the end began the evening before.
    val endAt = wakeDate.atTime(endTime)
    val startAt = if (startTime.isAfter(endTime) || startTime == endTime) wakeDate.minusDays(1).atTime(startTime) else wakeDate.atTime(startTime)
    val minutes = Duration.between(startAt, endAt).toMinutes()
    val valid = open || (minutes in 1..SleepDebt.MAX_SLEEP_MINUTES && !endAt.isAfter(now.plusMinutes(1)))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "수면 기록 추가" else "수면 기록 고치기") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("깬 날", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val today = now.toLocalDate()
                    listOf(today.minusDays(2), today.minusDays(1), today).forEach { d ->
                        FilterChip(selected = wakeDate == d, onClick = { wake = d.toString() },
                            label = { Text(when (d) { today -> "오늘"; today.minusDays(1) -> "어제"; else -> Fmt.shortDate(d) }) })
                    }
                    // 1.38 PC 9차: any earlier day too (지난주 기록을 나중에 넣을 때).
                    FilterChip(selected = wakeDate.isBefore(now.toLocalDate().minusDays(2)), onClick = { pickDay = true },
                        label = { Text(if (wakeDate.isBefore(now.toLocalDate().minusDays(2))) Fmt.shortDate(wakeDate) else "다른 날") })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { picking = "start" }, modifier = Modifier.weight(1f)) {
                        Text("잠든 시각 ${start}")
                    }
                    OutlinedButton(onClick = { picking = "end" }, enabled = !open, modifier = Modifier.weight(1f)) {
                        Text(if (open) "자는 중" else "깬 시각 ${end}")
                    }
                }
                Text(
                    when {
                        open -> "아직 자는 중으로 둬요."
                        !valid && minutes > SleepDebt.MAX_SLEEP_MINUTES -> "16시간이 넘어요. 시각을 다시 확인해 주세요."
                        !valid && endAt.isAfter(now) -> "아직 오지 않은 시각이에요."
                        else -> "${Fmt.shortDate(startAt.toLocalDate())} ${hm(startAt)}부터 ${SleepInsights.hours(minutes)}"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (valid) MaterialTheme.colorScheme.onSurfaceVariant else LocalCalendarColors.current.warning,
                )
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                if (open) onSave(wakeDate.atTime(startTime).let { if (it.isAfter(now)) it.minusDays(1) else it }, null)
                else onSave(startAt, endAt)
            }) { Text("저장") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text("지우기", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("취소") }
            }
        },
    )
    when (picking) {
        "start" -> TimePickerDialog("잠든 시각", startTime, onDismiss = { picking = null }) { start = it.toString(); picking = null }
        "end" -> TimePickerDialog("깬 시각", endTime, onDismiss = { picking = null }) { end = it.toString(); open = false; picking = null }
    }
}

/** 1.38 [SLEEP-20] When the user began 회복 도전, and [SLEEP-22] the day they marked a change, on this phone. */
internal object SleepChallengeStore {
    private const val PREFS = "sleep_challenge"
    private const val K_START = "start"
    private fun prefs(c: android.content.Context) = c.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
    fun start(c: android.content.Context): LocalDate? = prefs(c).getString(K_START, null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    fun begin(c: android.content.Context, day: LocalDate): LocalDate { prefs(c).edit().putString(K_START, day.toString()).apply(); return day }
    fun end(c: android.content.Context) = prefs(c).edit().remove(K_START).apply()
    fun mark(c: android.content.Context): Pair<LocalDate, String>? {
        val day = prefs(c).getString("mark_day", null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
        return day to prefs(c).getString("mark_what", null).orEmpty()
    }
    fun setMark(c: android.content.Context, day: LocalDate?, what: String?) =
        prefs(c).edit().apply { if (day == null) remove("mark_day").remove("mark_what") else putString("mark_day", day.toString()).putString("mark_what", what) }.apply()
}
