package com.bamnat.app.logic

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.bamnat.app.AppContainer
import com.bamnat.app.NoticeLinks
import com.bamnat.app.R
import com.bamnat.app.appContainer
import com.bamnat.core.formula.ButtonPlace
import com.bamnat.core.formula.LogicButton
import com.bamnat.core.formula.LogicVerb
import com.bamnat.core.time.Today
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * 1.39 [FX-04] 밖에서 누르기 (조사 18장 FXW-01 위젯 단추 + 새로 발굴 '빠른 설정·바로가기'): the buttons a work puts on the 단추 위젯, the 빠른 설정
 * tile and the app icon's long-press menu, pressed without opening the app. One press runs as a chip in the app does ([LogicRunner.run]),
 * says what it did in a short line and redraws the widget and tile. Without 프로 or a 하루 써 보기 they say so and open the 프로 screen.
 */
object FxOutside {
    /** The buttons for [place] that run now (the rest rest), the widget's at most [MAX_WIDGET]. */
    fun buttons(c: AppContainer, place: ButtonPlace): List<LogicButton> = LogicRunner.logic(c).buttons.filter { it.place == place }

    /** The ones kept for [place] whatever runs (to say "프로에서 켜져요" instead of "단추가 없어요"). */
    fun kept(c: AppContainer, place: ButtonPlace): List<LogicButton> = LogicRunner.logic(c, all = true).buttons.filter { it.place == place }

    /** "물 한 잔 · 6/8잔": a button's label and, when it counts a 값 칸, the number now. */
    fun line(c: AppContainer, b: LogicButton): String = listOfNotNull(
        listOfNotNull(b.emoji, b.label).joinToString(" "),
        b.act.takeIf { it.verb.value }?.let { FxValues.show(c, it.arg) },
    ).joinToString(" · ")

    /**
     * Presses [id] for [date] from outside; the line to say. [ButtonPlace] does not matter (an 알림 단추 names any button), but a button
     * whose 보이기 조건 is false that day is not pressed.
     */
    suspend fun press(c: AppContainer, id: String, date: LocalDate = Today.date()): String {
        val all = LogicRunner.logic(c, all = true).button(id) ?: return "이 단추가 작품에서 빠졌어요"
        if (!FxAccess.on(c) && LogicRunner.logic(c).button(id) == null) {
            LogicStore(c.appContext).log(all.label, LogicRunner.PRO_REST)
            return "프로 기능 장치라 지금은 쉬어요. 앱에서 하루 써 보거나 프로로 켜요"
        }
        val b = LogicRunner.logic(c).button(id) ?: all
        val f = runCatching { LogicRunner.facts(c) }.getOrNull() ?: return "지금은 누를 수 없어요. 앱을 열어 주세요"
        if (b.show.isNotEmpty() && com.bamnat.core.formula.Formula.eval(b.show, LogicRunner.scopeNow(f, date))?.truthy != true) {
            LogicStore(c.appContext).log(b.label, "밖에서 눌렀지만 이 날 보이기 조건이 맞지 않아 안 했어요")
            return "‘${b.label}’${com.bamnat.core.text.Josa.eunneun(b.label)} 오늘 조건에 맞지 않아요"
        }
        val r = runCatching { LogicRunner.pressButton(c, b, date, LogicRunner.scopeNow(f, date), quiet = true) }
        if (r.isFailure) { LogicStore(c.appContext).log(b.label, "하다가 멈췄어요"); return "‘${b.label}’${com.bamnat.core.text.Josa.eulreul(b.label)} 하다가 멈췄어요" }
        changed(c)
        // PC-99: what was done, its {수식} worked out as it is now (not the formula's letters).
        return if (b.act.verb.value && b.act.after == null) line(c, b) else "‘${b.label}’: ${LogicRunner.sayNow(b.act, f, date)}"
    }

    /**
     * [RVM-14] PC-35 (위젯·타일 '잠들어요'를 눌러도 수면 화면이 안 열리고 시각만 적혀요): the screen pressing [id] opens (its 동작 줄's
     * 화면 열기 with no wait), or null. Pressed outside, such a button opens the app on that screen and is pressed there ([openIntent]).
     */
    fun opens(c: AppContainer, id: String): NoticeLinks.Target? = LogicRunner.logic(c).button(id)?.steps(true)
        ?.firstOrNull { it.verb == LogicVerb.SCREEN && it.after == null }?.let { LogicRunner.screenOf(it.arg) }

    /** [RVM-14] Opens the app on [target] and presses [id] there, for [date] (an 알림 단추's day). */
    fun openIntent(ctx: Context, id: String, target: NoticeLinks.Target, date: LocalDate? = null, notice: String? = null): Intent =
        NoticeLinks.intent(ctx, target, payload = if (target == NoticeLinks.Target.DAY) (date ?: Today.date()).toString() else null)
            .setData(android.net.Uri.parse("bamnat-fx://open/$id/${date ?: ""}/${notice?.hashCode() ?: ""}"))
            .putExtra(PRESS, id).apply { if (date != null) putExtra(PRESS_DATE, date.toString()); if (notice != null) putExtra(PRESS_NOTICE, notice) }

    /** [RVM-14] The app opened by [openIntent]: the button is pressed now, on screen, and what it did shows in the app's line. */
    fun pressOpened(c: AppContainer, intent: Intent?) {
        if (intent == null || intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return
        val id = intent.getStringExtra(PRESS) ?: return
        intent.removeExtra(PRESS)
        val date = intent.getStringExtra(PRESS_DATE)?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: Today.date()
        intent.removeExtra(PRESS_DATE)
        // An 알림 단추: its 알림 goes, as when pressed without opening the app.
        intent.getStringExtra(PRESS_NOTICE)?.let { com.bamnat.app.alarm.Notifications.cancel(c.appContext, FxPressReceiver.NOTICE_ID, it) }
        intent.removeExtra(PRESS_NOTICE)
        c.appScope.launch { LogicRunner.say(press(c, id, date)) }
    }

    // ---- [RVM-16] PC-68 빠른 설정에 넣기 ----

    private const val TILE_FILE = "fx_tile"
    private const val TILE_IN = "in"
    private const val TILE_NO = "no"

    /**
     * [RVM-16] PC-68 (작품을 넣은 뒤 빠른 설정 타일을 편집 화면에서 직접 찾아 넣어야 해요): the put-in work has a 빠른 설정 button, the phone can
     * add a tile when asked (안드로이드 13부터), and it is not there yet nor was the offer closed.
     */
    fun tileOffer(c: AppContainer): LogicButton? {
        if (Build.VERSION.SDK_INT < 33) return null
        val p = c.appContext.getSharedPreferences(TILE_FILE, Context.MODE_PRIVATE)
        if (p.getBoolean(TILE_IN, false) || p.getBoolean(TILE_NO, false)) return null
        return kept(c, ButtonPlace.TILE).firstOrNull()
    }

    /** [RVM-16] The offer closed with its X: not asked again (빠른 설정 편집 still adds it). */
    fun closeTileOffer(c: AppContainer) = c.appContext.getSharedPreferences(TILE_FILE, Context.MODE_PRIVATE).edit().putBoolean(TILE_NO, true).apply()

    /** [RVM-16] The tile was added or taken away ([FxButtonTile.onTileAdded]·[FxButtonTile.onTileRemoved]). */
    internal fun tileIn(ctx: Context, on: Boolean) = ctx.getSharedPreferences(TILE_FILE, Context.MODE_PRIVATE).edit().putBoolean(TILE_IN, on).apply()

    /** [RVM-16] Asks the system to add the '밤낮 단추' tile ([b] named on it); [said] gets the line to show. From a screen on show only. */
    fun askTile(activity: android.app.Activity, b: LogicButton, said: (String) -> Unit) {
        if (Build.VERSION.SDK_INT < 33) return
        val bar = activity.getSystemService(android.app.StatusBarManager::class.java) ?: return
        runCatching {
            bar.requestAddTileService(ComponentName(activity, FxButtonTile::class.java), listOfNotNull(b.emoji, b.label).joinToString(" "),
                android.graphics.drawable.Icon.createWithResource(activity, R.drawable.ic_tile_money_add), activity.mainExecutor) { r ->
                when (r) {
                    android.app.StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED, android.app.StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> {
                        tileIn(activity, true); said("빠른 설정에 넣었어요. 화면 위에서 내려 눌러요")
                    }
                    android.app.StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED -> said("넣지 않았어요. 빠른 설정 편집에서 언제든 넣을 수 있어요")
                    else -> said("지금은 넣을 수 없어요. 빠른 설정 편집에서 ‘밤낮 단추’를 끌어다 넣어요")
                }
            }
        }.onFailure { said("지금은 넣을 수 없어요. 빠른 설정 편집에서 ‘밤낮 단추’를 끌어다 넣어요") }
    }

    private const val PRESS = "fx_press"
    private const val PRESS_DATE = "fx_press_date"
    private const val PRESS_NOTICE = "fx_press_notice"

    /** A 값 칸 or the buttons changed: the 단추 위젯 and the tile draw again. */
    fun changed(c: AppContainer) {
        c.appScope.launch { runCatching { com.bamnat.app.widget.FxButtonWidget.redraw(c.appContext) } }
        runCatching { TileService.requestListeningState(c.appContext, ComponentName(c.appContext, FxButtonTile::class.java)) }
    }

    /** The app icon's long-press menu holds the 바로가기 buttons that run now (at most four), and the widget and tile follow. */
    fun sync(c: AppContainer) {
        runCatching {
            val ctx = c.appContext
            val list = buttons(c, ButtonPlace.SHORTCUT).take(MAX_SHORTCUTS).map { b ->
                ShortcutInfoCompat.Builder(ctx, "fx_${b.id}")
                    .setShortLabel(listOfNotNull(b.emoji, b.label).joinToString(" ").take(24))
                    // PC-99: a label kept by the launcher has no {수식} letters (the number would go stale); those places are left out.
                    .setLongLabel("${b.label}: ${LogicRunner.sayNow(b.act, null, Today.date())}".take(48))
                    .setIcon(IconCompat.createWithResource(ctx, R.drawable.ic_tile_money_add))
                    .setIntent(Intent(ctx, FxPressActivity::class.java).setAction(FxPressActivity.ACTION).putExtra(FxPressActivity.ID, b.id))
                    .build()
            }
            // Only our own 기능 단추 shortcuts are replaced; none left, they are all taken away.
            val ours = ShortcutManagerCompat.getDynamicShortcuts(ctx).map { it.id }.filter { it.startsWith("fx_") }
            val keep = list.map { it.id }.toSet()
            ShortcutManagerCompat.removeDynamicShortcuts(ctx, ours.filter { it !in keep })
            if (list.isNotEmpty()) ShortcutManagerCompat.addDynamicShortcuts(ctx, list)
        }
        changed(c)
    }

    const val MAX_WIDGET = 4
    const val MAX_SHORTCUTS = 4
}

/** 1.39 [FX-05] An 알림 단추 (and nothing else): presses the button it names, for the day the 알림 was about. Not exported. */
class FxPressReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val c = context.appContainer
        val id = intent.getStringExtra(ID) ?: return
        val date = intent.getStringExtra(DATE)?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: Today.date()
        val done = goAsync()
        c.appScope.launch {
            try {
                // 1.39 [FX-15] '10분 뒤 다시': the same 알림 again later (its words as shown, its buttons).
                val again = com.bamnat.core.formula.LogicAct.AGAIN[id]
                val said = if (again != null) {
                    val title = intent.getStringExtra(TAG).orEmpty()
                    val then = intent.getStringArrayListExtra(THEN).orEmpty().take(com.bamnat.core.formula.LogicAct.MAX_THEN)
                    LogicRunner.later(c, com.bamnat.core.formula.LogicAct(LogicVerb.NOTIFY, intent.getStringExtra(TEXT).orEmpty().ifBlank { title },
                        then = then, after = again), date, title, again = true)
                    "${com.bamnat.core.formula.LogicAct.afterWords(again)} 뒤에 다시 알려요"
                } else FxOutside.press(c, id, date)
                withContext(Dispatchers.Main) { Toast.makeText(context.applicationContext, said, Toast.LENGTH_SHORT).show() }
                intent.getStringExtra(TAG)?.let { com.bamnat.app.alarm.Notifications.cancel(context, NOTICE_ID, it) }
            } finally { done.finish() }
        }
    }

    companion object {
        private const val ID = "id"
        private const val DATE = "date"
        private const val TAG = "tag"
        private const val TEXT = "text"
        private const val THEN = "then"
        internal const val NOTICE_ID = 7601

        /** 1.39 [FX-15] '10분 뒤 다시' ([id] one of [com.bamnat.core.formula.LogicAct.AGAIN]) on an 알림 titled [tag] saying [text]. */
        fun again(ctx: Context, id: String, date: LocalDate, tag: String, text: String, then: List<String>, req: Int): PendingIntent = PendingIntent.getBroadcast(
            ctx, req,
            Intent(ctx, FxPressReceiver::class.java).setData(android.net.Uri.parse("bamnat-fx://again/$id/$date/${tag.hashCode()}")).putExtra(ID, id)
                .putExtra(DATE, date.toString()).putExtra(TAG, tag).putExtra(TEXT, text).putStringArrayListExtra(THEN, ArrayList(then)),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        fun pending(ctx: Context, id: String, date: LocalDate, tag: String, req: Int): PendingIntent =
            // [RVM-14] PC-35: an 알림 단추 that opens a screen ('잠들어요' → 수면) opens the app there and is pressed on screen.
            ctx.appContainer.let { c -> FxOutside.opens(c, id) }?.let { target ->
                PendingIntent.getActivity(ctx, req, FxOutside.openIntent(ctx, id, target, date, tag), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            } ?: PendingIntent.getBroadcast(
            ctx, req,
            Intent(ctx, FxPressReceiver::class.java).setData(android.net.Uri.parse("bamnat-fx://press/$id/$date")).putExtra(ID, id).putExtra(DATE, date.toString()).putExtra(TAG, tag),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}

/**
 * 1.39 [FX-04] 빠른 설정 '밤낮 단추': the first 빠른 설정 button of the works, its number under its name. The user adds it by editing the
 * quick settings panel; nothing is added for them.
 */
class FxButtonTile : TileService() {
    // [RVM-16] So the 넣은 뒤 offer stops once the tile is there.
    override fun onTileAdded() = FxOutside.tileIn(applicationContext, true)
    override fun onTileRemoved() = FxOutside.tileIn(applicationContext, false)

    override fun onStartListening() {
        val c = applicationContext.appContainer
        FxOutside.tileIn(applicationContext, true)
        val b = runCatching { FxOutside.buttons(c, ButtonPlace.TILE).firstOrNull() }.getOrNull()
        val kept = runCatching { FxOutside.kept(c, ButtonPlace.TILE).firstOrNull() }.getOrNull()
        qsTile?.apply {
            state = if (b != null) Tile.STATE_INACTIVE else Tile.STATE_UNAVAILABLE
            label = (b ?: kept)?.let { listOfNotNull(it.emoji, it.label).joinToString(" ") } ?: "밤낮 단추"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) subtitle = when {
                b != null -> runCatching { FxOutside.line(c, b).substringAfter(" · ", "밤낮") }.getOrDefault("밤낮")
                kept != null -> "프로에서 켜져요"
                else -> "스튜디오에서 만들어요"
            }
            updateTile()
        }
    }

    override fun onClick() {
        val c = applicationContext.appContainer
        val b = FxOutside.buttons(c, ButtonPlace.TILE).firstOrNull()
        if (b == null) {
            // Nothing runs here: the 프로 screen (a kept one resting) or the 스튜디오's 규칙·단추.
            val kept = FxOutside.kept(c, ButtonPlace.TILE).isNotEmpty()
            val intent = NoticeLinks.intent(this, if (kept) NoticeLinks.Target.PRO else NoticeLinks.Target.MARKET)
            if (Build.VERSION.SDK_INT >= 34) startActivityAndCollapse(PendingIntent.getActivity(this, 3702, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            else @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated") startActivityAndCollapse(intent)
            return
        }
        // [RVM-14] PC-35: a button that opens a screen ('잠들어요' → 수면) folds the panel and opens the app there, pressed on screen.
        FxOutside.opens(c, b.id)?.let { target ->
            val intent = FxOutside.openIntent(this, b.id, target)
            if (Build.VERSION.SDK_INT >= 34) startActivityAndCollapse(PendingIntent.getActivity(this, 3703, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            else @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated") startActivityAndCollapse(intent)
            return
        }
        c.appScope.launch {
            val said = FxOutside.press(c, b.id)
            withContext(Dispatchers.Main) {
                Toast.makeText(applicationContext, said, Toast.LENGTH_SHORT).show()
                onStartListening()
            }
        }
    }
}

/**
 * 1.39 [FX-04] 앱 바로가기: presses the button the shortcut names. [RVM-15] PC-69 ('집중 시작'을 눌러도 작은 창이 그대로라 세 번 눌렀어요):
 * the press closes the shortcut menu and says what it did in a short band at the bottom ('🎧 집중 · 3/4번') for a moment, over whatever
 * was on screen; a touch anywhere closes it sooner. A button that opens a screen opens the app there instead ([FxOutside.opens]).
 */
class FxPressActivity : androidx.activity.ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent?.takeIf { it.action == ACTION }?.getStringExtra(ID)
        if (id == null) { finish(); return }
        val c = applicationContext.appContainer
        FxOutside.opens(c, id)?.let { target -> startActivity(FxOutside.openIntent(this, id, target)); finish(); return }
        val said = androidx.compose.runtime.mutableStateOf<String?>(null)
        // Pressed once, even when the window is made again (a turn of the phone).
        if (savedInstanceState == null) c.appScope.launch {
            said.value = FxOutside.press(c, id)
            kotlinx.coroutines.delay(BAND_MS)
            withContext(Dispatchers.Main) { if (!isFinishing) finish() }
        } else { finish(); return }
        setContent {
            com.bamnat.app.ui.theme.BamnatTheme {
                PressBand(said.value, onClose = { finish() })
            }
        }
    }

    companion object {
        /** How long the band stays (the same as a short 띠 in the app). */
        const val BAND_MS = 2_500L
        const val ACTION = "com.bamnat.app.FX_PRESS"
        const val ID = "id"
    }
}

/** [RVM-15] The band a 바로가기 press shows: what it did, at the bottom, in the app's colours. */
@androidx.compose.runtime.Composable
private fun PressBand(said: String?, onClose: () -> Unit) {
    androidx.compose.foundation.layout.Box(
        androidx.compose.ui.Modifier.fillMaxSize().clickable(interactionSource = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null, onClick = onClose)
            .navigationBarsPadding().padding(16.dp),
        contentAlignment = androidx.compose.ui.Alignment.BottomCenter,
    ) {
        androidx.compose.material3.Surface(
            color = androidx.compose.material3.MaterialTheme.colorScheme.inverseSurface,
            contentColor = androidx.compose.material3.MaterialTheme.colorScheme.inverseOnSurface,
            shape = androidx.compose.material3.MaterialTheme.shapes.medium,
            shadowElevation = 6.dp,
            modifier = androidx.compose.ui.Modifier.widthIn(max = 640.dp).fillMaxWidth().semantics { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite },
        ) {
            androidx.compose.material3.Text(said ?: "누르는 중이에요", style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
                modifier = androidx.compose.ui.Modifier.padding(horizontal = 16.dp, vertical = 14.dp))
        }
    }
}
