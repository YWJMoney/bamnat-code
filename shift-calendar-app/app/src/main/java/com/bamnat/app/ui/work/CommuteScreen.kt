package com.bamnat.app.ui.work

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import com.bamnat.app.ui.components.AlertDialog
import com.bamnat.app.ui.components.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import com.bamnat.app.ui.components.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.bamnat.app.AppContainer
import com.bamnat.app.alarm.Notifications
import com.bamnat.app.commute.CommuteRecorder
import com.bamnat.app.data.CommutePrefs
import com.bamnat.app.data.PersonalCalendar
import com.bamnat.app.ui.Fmt
import com.bamnat.app.ui.components.SectionCard
import com.bamnat.app.ui.components.WarningBanner
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * [DAYW-07] 출퇴근 자동 기록: off by default. Turning it on first says plainly what the location is used for
 * (Google Play's prominent disclosure for location in the background), then asks for location, then for
 * 'always allow', and takes where the phone is now as the workplace. Turning it off removes the geofence
 * and the workplace point.
 */
class CommuteViewModel(private val container: AppContainer) : ViewModel() {
    private val prefs = CommutePrefs.get(container.appContext)
    val state: StateFlow<CommutePrefs.State> = prefs.state
    val calendars: StateFlow<List<PersonalCalendar>> = container.calendars.calendars
    private val _locating = kotlinx.coroutines.flow.MutableStateFlow(false)
    val locating: StateFlow<Boolean> = _locating
    private val _error = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    /** Where the phone is now becomes the workplace; true when it was found. */
    fun useHere(then: () -> Unit = {}) {
        if (!CommuteRecorder.hasForeground(container.appContext)) return
        _locating.value = true
        _error.value = null
        viewModelScope.launch {
            val loc = CommuteRecorder.here(container.appContext)
            _locating.value = false
            if (loc == null) {
                _error.value = "지금 위치를 찾지 못했어요. 위치(GPS)가 켜져 있는지 보고, 회사 안에서 다시 눌러 주세요."
                return@launch
            }
            prefs.setPlace(loc.latitude, loc.longitude)
            CommuteRecorder.sync(container.appContext)
            then()
        }
    }

    /** [RVH-35] 검토 U02-09 On without a place yet: it is taken a while after the next shift starts. */
    fun turnOnLater() { prefs.setPlaceNext(true); turnOn() }

    fun turnOn() {
        if (prefs.current.calendarId.let { container.calendars.get(it) } == null || !prefs.current.enabled) {
            prefs.setCalendar(container.calendars.active.value)
        }
        prefs.setEnabled(true)
        CommuteRecorder.sync(container.appContext)
    }

    fun turnOff() = CommuteRecorder.turnOff(container.appContext)

    fun setRadius(m: Int) { prefs.setRadius(m); CommuteRecorder.sync(container.appContext) }
    fun setAuto(auto: Boolean) = prefs.setAuto(auto)
    fun setOvertimeSlack(minutes: Int) = prefs.setOvertimeSlack(minutes)
    fun setOvertime(way: com.bamnat.core.work.Commute.OvertimeWay) = prefs.setOvertime(way)
    fun setCalendar(id: String) = prefs.setCalendar(id)
    fun resync() = CommuteRecorder.sync(container.appContext)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CommuteScreen(vm: CommuteViewModel, onBack: () -> Unit) {
    val s by vm.state.collectAsStateWithLifecycle()
    val calendars by vm.calendars.collectAsStateWithLifecycle()
    val locating by vm.locating.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var fine by remember { mutableStateOf(CommuteRecorder.hasForeground(context)) }
    var background by remember { mutableStateOf(CommuteRecorder.hasBackground(context)) }
    var disclosure by remember { mutableStateOf(false) }
    // [RVH-35] 검토 U02-09 Turning it on away from work: the place is taken at the next shift.
    var later by rememberSaveable { mutableStateOf(false) }
    var placeAt by remember { mutableStateOf<java.time.LocalDateTime?>(null) }
    var away by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(s.enabled, s.hasPlace, s.placeNext, s.calendarId) {
        placeAt = if (s.enabled && !s.hasPlace && s.placeNext) CommuteRecorder.placeTime(context) else null
    }
    LaunchedEffect(s.lat, s.lng, s.radius, fine) {
        away = if (s.hasPlace && fine) CommuteRecorder.lastHere(context)?.let { here ->
            val d = FloatArray(1)
            android.location.Location.distanceBetween(here.latitude, here.longitude, s.lat!!, s.lng!!, d)
            com.bamnat.core.work.Commute.distanceWords(d[0].toDouble(), s.radius)
        } else null
    }
    var askAlways by remember { mutableStateOf(false) }
    var refused by remember { mutableIntStateOf(0) }
    var canNotify by remember { mutableStateOf(Notifications.canPost(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        fine = CommuteRecorder.hasForeground(context)
        background = CommuteRecorder.hasBackground(context)
        canNotify = Notifications.canPost(context)
        vm.resync()
    }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { canNotify = Notifications.canPost(context) }
    // [DAYW-07] Asking first needs the notice: every way of turning it on (or of choosing to be asked) asks for it.
    fun askNotices(auto: Boolean = vm.state.value.auto) {
        if (!auto && !Notifications.canPost(context) && Build.VERSION.SDK_INT >= 33) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    fun turnOn() {
        // 검토 U02-09 The place notice is a notice: asked for even with 바로 적기.
        if (later && !s.hasPlace) { vm.turnOnLater(); askNotices(auto = false) } else { vm.turnOn(); askNotices() }
    }
    val backgroundAsk = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        background = ok || CommuteRecorder.hasBackground(context)
        if (background) turnOn() else refused++
    }
    val foregroundAsk = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        fine = result[Manifest.permission.ACCESS_FINE_LOCATION] == true || CommuteRecorder.hasForeground(context)
        when {
            !fine -> refused++
            later -> if (CommuteRecorder.hasBackground(context)) turnOn() else askAlways = true
            else -> vm.useHere { if (CommuteRecorder.hasBackground(context)) turnOn() else askAlways = true }
        }
    }

    fun start() {
        when {
            !fine -> foregroundAsk.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            later && !s.hasPlace -> if (background) turnOn() else askAlways = true
            !s.hasPlace -> vm.useHere { if (CommuteRecorder.hasBackground(context)) turnOn() else askAlways = true }
            !background -> askAlways = true
            else -> turnOn()
        }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("출퇴근 자동 기록") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") } },
        )
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionCard(title = "회사에 도착하면 출근, 나오면 퇴근") {
                Text(DISCLOSURE, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
            if (refused > 0 && (!fine || !background)) {
                WarningBanner("위치 권한이 없어서 켜지 못했어요",
                    "휴대폰 설정 > 애플리케이션 > 밤낮 > 권한 > 위치에서 ‘항상 허용’과 ‘정확한 위치’를 켜 주세요.")
                OutlinedButton(onClick = {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
                }, modifier = Modifier.fillMaxWidth()) { Text("권한 설정 열기") }
            }
            if (!s.enabled) {
                Button(onClick = { later = false; disclosure = true }, enabled = !locating, modifier = Modifier.fillMaxWidth()) {
                    Text(if (locating) "지금 위치를 찾는 중…" else "켜기 (회사에서 눌러 주세요)")
                }
                // [RVH-35] 검토 WORK-16·U02-09 Away from work now: the place is taken at the next shift instead.
                TextButton(onClick = { later = true; disclosure = true }, enabled = !locating, modifier = Modifier.fillMaxWidth()) {
                    Text("회사가 아니에요 · 다음 출근 때 정하기")
                }
                Text("지금 있는 곳을 회사 위치로 저장해요. 회사가 아니면 다음 근무가 시작하고 ${com.bamnat.core.work.Commute.PLACE_AFTER_MINUTES}분 뒤 있는 곳으로 정해요.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                return@Column
            }
            if (!fine || !background) {
                WarningBanner("위치 권한이 ‘항상 허용’이 아니에요", "앱을 닫아 두면 출퇴근을 적지 못해요. 위치 권한에서 ‘항상 허용’을 골라 주세요.")
                OutlinedButton(onClick = { start() }, modifier = Modifier.fillMaxWidth()) { Text("권한 다시 확인") }
            }
            if (!s.auto && !canNotify) {
                WarningBanner("알림이 꺼져 있어요", "‘물어보고 적기’는 알림으로 물어봐요. 알림을 켜거나 ‘바로 적기’를 골라 주세요.")
                OutlinedButton(onClick = {
                    context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                }, modifier = Modifier.fillMaxWidth()) { Text("알림 켜기") }
            }
            SectionCard(title = "회사 위치") {
                // [RVH-35] 검토 WORK-16 Said in words, not as 위도·경도 numbers.
                Text(when {
                    s.hasPlace -> "정해 둔 곳에서 ${s.radius}m 안을 회사로 봐요"
                    s.placeNext -> placeAt?.let { "${Fmt.monthDay(it.toLocalDate())} ${Fmt.time(it.toLocalTime())}쯤(근무 시작 ${com.bamnat.core.work.Commute.PLACE_AFTER_MINUTES}분 뒤) 있는 곳을 회사로 정해요" }
                        ?: "2주 안에 근무가 없어 아직 정하지 못해요. 회사에서 아래 단추를 눌러 주세요"
                    else -> "아직 정하지 않았어요"
                }, style = MaterialTheme.typography.bodyMedium)
                away?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { vm.useHere() }, enabled = fine && !locating, modifier = Modifier.fillMaxWidth()) {
                    Text(if (locating) "지금 위치를 찾는 중…" else "지금 있는 곳을 회사로")
                }
                Spacer(Modifier.height(8.dp))
                Text("범위", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(100, 150, 300, 500).forEach { m -> FilterChip(selected = s.radius == m, onClick = { vm.setRadius(m) }, label = { Text("${m}m") }) }
                }
                Text("건물이 크거나 위치가 자주 튀면 범위를 넓혀 주세요.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            SectionCard(title = "적는 방법") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = !s.auto, onClick = { vm.setAuto(false); askNotices(auto = false) }, label = { Text("물어보고 적기") })
                    FilterChip(selected = s.auto, onClick = { vm.setAuto(true) }, label = { Text("바로 적기") })
                }
                Text(if (s.auto) "도착·퇴근을 바로 달력에 적고 알림으로 알려 줘요." else "알림에서 ‘맞아요’를 누르면 달력에 적어요.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (calendars.size > 1) {
                    Spacer(Modifier.height(8.dp))
                    Text("적을 달력", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        calendars.forEach { c -> FilterChip(selected = s.calendarId == c.id, onClick = { vm.setCalendar(c.id) }, label = { Text(c.name) }) }
                    }
                }
                // [RVH-34] 검토 WORK-15·U02-10 잔업 goes into the pay: how long after the end is still leaving, and whether it is asked.
                Spacer(Modifier.height(8.dp))
                Text("잔업", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    com.bamnat.core.work.Commute.OvertimeWay.entries.forEach { w ->
                        FilterChip(selected = s.overtime == w, onClick = { vm.setOvertime(w) }, label = { Text(w.label) })
                    }
                }
                if (s.overtime != com.bamnat.core.work.Commute.OvertimeWay.NONE) {
                    Text("끝나고 이만큼은 나가는 길로 봐요", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        com.bamnat.core.work.Commute.OVERTIME_SLACK_CHOICES.forEach { m ->
                            FilterChip(selected = s.overtimeSlack == m, onClick = { vm.setOvertimeSlack(m) }, label = { Text("${m}분") })
                        }
                    }
                }
                Text(when (s.overtime) {
                    com.bamnat.core.work.Commute.OvertimeWay.ASK -> "끝나고 ${s.overtimeSlack}분보다 늦게 나오면 ‘잔업으로 적을까요?’를 알림으로 한 번 물어요."
                    com.bamnat.core.work.Commute.OvertimeWay.WRITE -> "끝나고 ${s.overtimeSlack}분보다 늦게 나오면 잔업으로 적어요. 예상 급여에도 들어가요."
                    com.bamnat.core.work.Commute.OvertimeWay.NONE -> "회사가 승인한 잔업만 세는 분께 맞아요. 잔업은 달력에서 직접 적어요."
                }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                // [RVH-34] 디자인 품질 조건 4 (200%에서 열 줄이 넘는 설명): one line with ⓘ once it has been read (공통 부품 Explain).
                com.bamnat.app.ui.components.Explain(
                    key = "commute.rules",
                    text = "늦게 도착하면 출근 시각을, 일찍 나오면 퇴근 시각을, 늦게 나오면 잔업을, 쉬는 날 다녀오면 특근을 적어요. " +
                        "점심때 잠깐 나갔다 ${CommuteRecorder.LEAVE_WAIT_MINUTES}분 안에 돌아오면 퇴근으로 보지 않아요.",
                    summary = "늦거나 일찍 오가면 그 시각을 적어요",
                )
            }
            SectionCard(title = "최근 기록") {
                val arrived = s.arrivedAt
                Text(listOfNotNull(
                    arrived?.let { "도착 ${Fmt.monthDay(it.toLocalDate())} ${Fmt.time(it.toLocalTime())}" },
                    s.leftAt?.let { "나감 ${Fmt.monthDay(it.toLocalDate())} ${Fmt.time(it.toLocalTime())}" },
                    s.pendingLeave?.let { "나감 ${Fmt.time(it.toLocalTime())} (돌아오는지 기다리는 중)" },
                ).joinToString("\n").ifEmpty { "아직 기록이 없어요" }, style = MaterialTheme.typography.bodyMedium)
            }
            OutlinedButton(onClick = vm::turnOff, modifier = Modifier.fillMaxWidth()) { Text("끄기 (회사 위치도 지워요)") }
            Text("위치 권한까지 거두려면 휴대폰 설정 > 애플리케이션 > 밤낮 > 권한에서 바꿀 수 있어요.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    if (disclosure) AlertDialog(
        onDismissRequest = { disclosure = false },
        title = { Text("위치 정보 사용 안내") },
        text = { Text(DISCLOSURE + "\n\n다음 화면에서 위치 권한을 허용해 주세요.") },
        confirmButton = { TextButton(onClick = { disclosure = false; start() }) { Text("동의하고 계속") } },
        dismissButton = { TextButton(onClick = { disclosure = false }) { Text("괜찮아요") } },
    )
    if (askAlways) AlertDialog(
        onDismissRequest = { askAlways = false },
        title = { Text("‘항상 허용’을 골라 주세요") },
        text = {
            Text("앱을 닫아 두어도 회사에 들어오고 나가는 것을 알려면 위치를 ‘항상 허용’해야 해요. " +
                "다음 화면의 위치 권한에서 ‘항상 허용’을 골라 주세요. 위치는 휴대폰 밖으로 보내지 않아요.")
        },
        confirmButton = {
            TextButton(onClick = {
                askAlways = false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) backgroundAsk.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION) else turnOn()
            }) { Text("계속") }
        },
        dismissButton = { TextButton(onClick = { askAlways = false }) { Text("나중에") } },
    )
}

/** [DAYW-07] What the location is for, said before anything is asked (Play's prominent disclosure). */
private const val DISCLOSURE =
    "밤낮은 출퇴근 시각을 자동으로 적기 위해, 앱을 닫아 두거나 쓰지 않을 때도 위치 정보를 사용해요. " +
        "회사 위치(한 곳)에 들어오면 출근, 나가면 퇴근 시각을 달력에 적거나 적을지 물어봐요.\n" +
        "· 위치는 이 휴대폰 안에서만 쓰고, 밖으로 보내지 않아요.\n" +
        "· 저장하는 것은 회사 위치 한 곳과 적은 출퇴근 시각뿐이에요. 다니는 길은 저장하지 않아요.\n" +
        "· 기본으로 꺼져 있고, 언제든 끌 수 있어요. 끄면 회사 위치도 지워요."
