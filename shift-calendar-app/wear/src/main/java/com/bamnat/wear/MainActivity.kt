package com.bamnat.wear

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CompactButton
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.bamnat.core.layout.Contrast
import com.bamnat.core.work.WatchDay
import com.bamnat.core.work.WatchDays
import com.bamnat.core.work.WatchPayload
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import com.bamnat.core.time.Today

/**
 * [SHFT-08][REL-09] The watch app: 오늘 (today's shift, tomorrow's and the next alarm), 앞으로 7일 (today and six days on as a
 * list) and 월 보기 (a month at a glance, swiping to the next month). Swipe left and right between them.
 * It shows the watch's last copy of the phone's days with when they came, and asks the phone for fresh ones.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WatchStore.load(this)
        setContent { MaterialTheme { WatchApp() } }
    }
}

@Composable
private fun WatchApp() {
    val context = LocalContext.current
    val payload by WatchStore.payload.collectAsState()
    var refreshing by remember { mutableStateOf(false) }
    // [J133-38] 순번 718: no phone answered the last ask.
    var noPhone by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun refresh() {
        refreshing = true
        scope.launch { noPhone = runCatching { !WatchStore.refresh(context) }.getOrDefault(false); refreshing = false }
    }
    LaunchedEffect(Unit) { refresh() }
    // 1.38 [LEDH-14] 워치로 돈 적기 opens over the pages from 오늘's button and goes back after sending.
    var writing by remember { mutableStateOf(false) }
    val pager = rememberPagerState(pageCount = { 3 })
    val shown = payload
    if (writing && shown != null) {
        MoneyPage(shown, onDone = { writing = false })
        return
    }
    HorizontalPager(pager, Modifier.fillMaxSize().background(Color.Black)) { page ->
        val p = payload
        when {
            p == null -> Empty(refreshing, noPhone, ::refresh)
            page == 0 -> TodayPage(p, refreshing, ::refresh, onMoney = { writing = true })
            page == 1 -> WeekPage(p)
            else -> MonthPage(p)
        }
    }
}

@Composable
private fun Empty(refreshing: Boolean, noPhone: Boolean, onRefresh: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("밤낮", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        // [J133-38] 순번 718 (1.32 실사용 테스트: 휴대폰이 없는데도 '휴대폰에서 다시 받기'만 있었어요).
        Text(if (noPhone && !refreshing) "휴대폰과 연결되어 있지 않아요. 휴대폰의 Wear OS 앱에서 이 시계가 연결됐는지 확인해 주세요."
            else "휴대폰의 밤낮을 한 번 열면 근무가 여기로 와요.", textAlign = TextAlign.Center, fontSize = 13.sp, color = WearTone.Body)
        Spacer(Modifier.height(8.dp))
        RefreshChip(refreshing, onRefresh)
    }
}

@Composable
private fun RefreshChip(refreshing: Boolean, onRefresh: () -> Unit) {
    Button(
        onClick = onRefresh,
        enabled = !refreshing,
        colors = ButtonDefaults.filledTonalButtonColors(),
        label = { Text(if (refreshing) "받는 중…" else "휴대폰에서 다시 받기", fontSize = 12.sp) },
    )
}

private fun notificationsOn(context: Context) =
    android.os.Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

/** [A11Y-03][WEAR-02] White or dark ink, whichever stands out more on the shift's colour: the phone's rule, from core. */
private fun inkOn(bg: Color): Color = Color(Contrast.inkOn(bg.toArgb().toLong() and 0xFFFFFFFFL).toInt())

private fun color(d: WatchDay) = Color(ShiftTileService.readable(d.colorArgb))

/** [WEAR-01] The clock to the minute, moving on by itself, so "알람까지" never stands still (I 리뷰 I25). */
@Composable
private fun rememberMinute(): LocalDateTime {
    val now by produceState(Today.dateTime().withSecond(0).withNano(0)) {
        while (true) {
            val t = Today.dateTime()
            delay(60_000L - (t.second * 1000L + t.nano / 1_000_000))
            value = Today.dateTime().withSecond(0).withNano(0)
        }
    }
    return now
}

/** Internal for the watch screen shots (WatchScreenshotTest). */
@Composable
internal fun TodayPage(p: WatchPayload, refreshing: Boolean, onRefresh: () -> Unit, onMoney: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val now = rememberMinute()
    val today = now.toLocalDate()
    val d = WatchDays.on(p.days, today)
    val list = rememberScalingLazyListState()
    // 2.0.0 [RVI-11] (검토 ALM-10·23) 오늘 알람 끄기 takes two taps and says what came of it; 출근·퇴근 says so too.
    var said by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(armed) { if (armed) { delay(5_000L); armed = false } }
    val clock = WatchDays.clockNow(p, now)
    var clockSaid by remember { mutableStateOf<String?>(null) }
    ScalingLazyColumn(Modifier.fillMaxSize(), state = list, horizontalAlignment = Alignment.CenterHorizontally) {
        // [WEAR-04] 1.31: which calendar this is, now that the phone can send another one than 내 달력.
        item { Text("${p.calendar.ifBlank { "오늘" }} · ${WatchText.date(today)}", fontSize = 12.sp, color = WearTone.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        item {
            if (d == null) Text(WatchText.notSent(p), fontSize = 16.sp)
            else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(d.name, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = color(d), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (d.times.isNotEmpty()) Text(d.times, fontSize = 16.sp)
            }
        }
        WatchDays.on(p.days, today.plusDays(1))?.let { t -> item { Text("내일 ${t.line}", fontSize = 13.sp, color = WearTone.Body) } }
        WatchText.alarm(p, now)?.let { a -> item { Text(a, fontSize = 12.sp, color = WearTone.Caution, textAlign = TextAlign.Center) } }
        // 1.38 [SLEEP-14] 2.0.0 [RVI-08] 오늘 잘 시간 while it is still the night or day it was worked out for.
        WatchDays.sleepLineNow(p, now).takeIf { it.isNotBlank() }?.let { s -> item { Text(s, fontSize = 12.sp, color = WearTone.Body) } }
        // [WEAR-03] This week against 주 52시간. 2.0.0 [RVI-08] Not last week's on a Monday.
        WatchDays.weekText(p, today)?.let { w -> item { Text(w, fontSize = 12.sp, color = WearTone.Body) } }
        // [WEAR-03] 오늘 알람 끄기 without taking the phone out, and an 알바's 출근·퇴근 (I 리뷰 N7 c·d).
        // 2.0.0 [RVI-11] (검토 ALM-10·23) Two taps and what came of it, with 되돌리기; [RVI-08] whether today has one is the watch's own reading.
        said?.let { (line, undo) ->
            item { Text(line, fontSize = 12.sp, color = WearTone.Caution, textAlign = TextAlign.Center) }
            if (undo) item {
                CompactButton(onClick = { scope.launch { WatchStore.send(context, WatchDays.ALARM_ON_PATH, today.toString()); said = null } },
                    label = { Text("되돌리기", fontSize = 12.sp) })
            }
        }
        if (said == null && WatchDays.alarmTodayNow(p, now)) item {
            Button(onClick = {
                if (!armed) armed = true else {
                    armed = false
                    said = "끄는 중…" to false
                    scope.launch {
                        val ok = runCatching { WatchStore.send(context, WatchDays.ALARM_OFF_PATH, today.toString()) }.getOrDefault(false)
                        said = if (ok) "오늘 알람을 껐어요" to true else "휴대폰과 연결되어 있지 않아 못 껐어요" to false
                    }
                }
            }, colors = ButtonDefaults.filledTonalButtonColors(), modifier = Modifier.fillMaxWidth(),
                label = { Text(if (armed) "한 번 더 누르면 꺼요" else "오늘 알람 끄기", fontSize = 13.sp) })
        }
        // 2.0.0 [RVI-08] (검토 ALM-09·T03-06) A new working day's 출근 even before the phone sends again.
        if (clock != WatchPayload.Clock.NONE) item {
            val out = clock == WatchPayload.Clock.OUT
            Button(onClick = {
                scope.launch {
                    // 2.0.0 [RVI-11] (검토 ALM-23) Said either way, as 돈 적기 does.
                    val ok = runCatching { WatchStore.send(context, WatchDays.CLOCK_PATH, if (out) "out" else "in") }.getOrDefault(false)
                    clockSaid = if (ok) (if (out) "퇴근을 적었어요" else "출근을 적었어요") else "휴대폰과 연결되어 있지 않아 못 보냈어요"
                }
            }, colors = ButtonDefaults.buttonColors(), modifier = Modifier.fillMaxWidth(),
                label = { Text(if (out) "퇴근 적기" else "출근 적기", fontSize = 13.sp) })
        }
        clockSaid?.let { s -> item { Text(s, fontSize = 12.sp, color = WearTone.Caution, textAlign = TextAlign.Center) } }
        // 1.38 [LEDH-14] 워치로 돈 적기.
        item {
            Button(onClick = onMoney, colors = ButtonDefaults.filledTonalButtonColors(), modifier = Modifier.fillMaxWidth(),
                label = { Text("돈 적기", fontSize = 13.sp) })
        }
        // [WEAR-06] 1.31: the phone's alarm can show here only with the notification permission.
        if (p.ringOnWatch && !notificationsOn(context)) item {
            var asked by remember { mutableStateOf(false) }
            val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { asked = true }
            if (!asked || !notificationsOn(context)) Button(onClick = { ask.launch(Manifest.permission.POST_NOTIFICATIONS) }, colors = ButtonDefaults.filledTonalButtonColors(),
                modifier = Modifier.fillMaxWidth(), label = { Text("워치 알람 알림 허용", fontSize = 13.sp) })
        }
        item { Text(WatchText.synced(p, now), fontSize = 11.sp, color = WearTone.Muted, textAlign = TextAlign.Center) }
        item { RefreshChip(refreshing, onRefresh) }
        item { Text("옆으로 넘기면 앞으로 7일 · 월 보기", fontSize = 10.sp, color = WearTone.Hint) }
    }
}

@Composable
private fun WeekPage(p: WatchPayload) {
    val today = Today.date()
    val days = (0L until 7L).map { today.plusDays(it) }
    ScalingLazyColumn(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        item { Text("앞으로 7일", fontSize = 14.sp, fontWeight = FontWeight.Bold) }
        items(days) { date ->
            val d = WatchDays.on(p.days, date)
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${date.dayOfMonth} ${WatchText.dayOfWeek(date)}", fontSize = 13.sp, modifier = Modifier.width(44.dp),
                    color = if (date == today) Color.White else Color(0xFFB8BFCC), fontWeight = if (date == today) FontWeight.Bold else FontWeight.Normal)
                Box(Modifier.size(10.dp).clip(CircleShape).background(d?.let(::color) ?: Color.DarkGray))
                Spacer(Modifier.width(6.dp))
                Text(d?.line ?: "-", fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun MonthPage(p: WatchPayload) {
    val today = Today.date()
    var offset by remember { mutableIntStateOf(0) }
    // [WEAR-01] As far as the phone's days reach, not a fixed month either side (I 리뷰 I24).
    val range = remember(p) { WatchDays.monthRange(p.days, today) }
    val month = YearMonth.from(today).plusMonths(offset.toLong())
    // [WEAR-01] The phone's first day of the week (I 리뷰 I23).
    val grid = WatchDays.monthGrid(month.year, month.monthValue, p.weekStart)
    ScalingLazyColumn(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // [WEAR-01] Real buttons with a finger-sized target instead of 18sp letters (I 리뷰 P26).
                CompactButton(onClick = { offset-- }, enabled = offset > range.first, colors = ButtonDefaults.filledTonalButtonColors(),
                    label = { Text("‹", fontSize = 18.sp) })
                Text("${month.monthValue}월", fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp))
                CompactButton(onClick = { offset++ }, enabled = offset < range.last, colors = ButtonDefaults.filledTonalButtonColors(),
                    label = { Text("›", fontSize = 18.sp) })
            }
        }
        item {
            Row(Modifier.padding(horizontal = 14.dp)) {
                WatchDays.weekHead(p.weekStart).forEach {
                    Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 10.sp, color = WearTone.Muted)
                }
            }
        }
        items(grid) { week ->
            Row(Modifier.padding(horizontal = 14.dp)) {
                week.forEach { date ->
                    Column(Modifier.weight(1f).padding(1.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        if (date != null) {
                            val d = WatchDays.on(p.days, date)
                            val badge = when {
                                d == null -> Color(0xFF222222)
                                d.off -> Color(0xFF333A46)
                                else -> Color(d.colorArgb.toInt())
                            }
                            Text("${date.dayOfMonth}", fontSize = 10.sp, color = if (date == today) Color.White else WearTone.Muted,
                                fontWeight = if (date == today) FontWeight.Bold else FontWeight.Normal)
                            Box(Modifier.fillMaxWidth().height(15.dp).clip(RoundedCornerShape(3.dp))
                                .background(badge), contentAlignment = Alignment.Center) {
                                Text(d?.label?.take(1) ?: "", fontSize = 10.sp, color = inkOn(badge), maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
        item { Text("받은 기간 밖의 날은 비어 있어요", fontSize = 10.sp, color = WearTone.Hint) }
    }
}



/**
 * 1.38 [LEDH-14] 워치로 돈 적기: the amount by ± 1천·1만 (or turning the crown), then a category to send it with. The phone writes it
 * to the main 가계부 as today's spending. Nothing is kept on the watch; with no phone near it says so and nothing is lost on the phone.
 */
@Composable
private fun MoneyPage(p: WatchPayload, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var amount by remember { mutableStateOf(0L) }
    var note by remember { mutableStateOf<String?>(null) }
    val categories = p.moneyCategories.ifEmpty { com.bamnat.core.work.WatchMoney.FALLBACK }
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    androidx.activity.compose.BackHandler { onDone() }
    ScalingLazyColumn(
        Modifier.fillMaxSize().background(Color.Black)
            .onRotaryScrollEvent { e -> amount = (amount + (if (e.verticalScrollPixels > 0) 1_000L else -1_000L)).coerceIn(0, com.bamnat.core.work.WatchMoney.MAX_AMOUNT); true }
            .focusRequester(focus).focusable(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item { Text("돈 적기 · 오늘", fontSize = 12.sp, color = WearTone.Muted) }
        item { Text(String.format(java.util.Locale.KOREA, "%,d원", amount), fontSize = 24.sp, fontWeight = FontWeight.Bold) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                com.bamnat.core.work.WatchMoney.STEPS.forEach { step ->
                    CompactButton(onClick = { amount = (amount - step).coerceAtLeast(0) }, label = { Text("−${step / 1000}천".replace("10천", "1만"), fontSize = 11.sp) })
                    CompactButton(onClick = { amount = (amount + step).coerceAtMost(com.bamnat.core.work.WatchMoney.MAX_AMOUNT) },
                        label = { Text("+${step / 1000}천".replace("10천", "1만"), fontSize = 11.sp) })
                }
            }
        }
        // 2.0.0 [RVI-10] (검토 U03-10) One tap to an amount: the ones written most lately on the phone, else 5천·1만·2만.
        item {
            val chips = p.recentAmounts.ifEmpty { com.bamnat.core.work.WatchMoney.CHIPS }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                chips.forEach { a -> CompactButton(onClick = { amount = a }, colors = ButtonDefaults.filledTonalButtonColors(),
                    label = { Text(com.bamnat.core.work.WatchMoney.short(a), fontSize = 11.sp, maxLines = 1) }) }
            }
        }
        note?.let { n -> item { Text(n, fontSize = 12.sp, color = WearTone.Caution, textAlign = TextAlign.Center) } }
        items(categories) { cat ->
            Button(
                onClick = {
                    val line = com.bamnat.core.work.WatchMoney.Line(java.util.UUID.randomUUID().toString(), amount, cat, com.bamnat.core.time.Today.millis())
                    scope.launch {
                        val sent = runCatching { WatchStore.send(context, WatchDays.MONEY_PATH, com.bamnat.core.work.WatchMoney.encode(line)) }.getOrDefault(false)
                        if (sent) onDone() else note = "휴대폰과 연결되어 있지 않아 못 보냈어요"
                    }
                },
                enabled = amount > 0, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.filledTonalButtonColors(),
                label = { Text(cat, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            )
        }
        item { Text("금액을 맞춘 뒤 분류를 누르면 휴대폰 가계부에 적혀요", fontSize = 10.sp, color = WearTone.Hint, textAlign = TextAlign.Center) }
        item { CompactButton(onClick = onDone, label = { Text("닫기", fontSize = 11.sp) }) }
    }
}
