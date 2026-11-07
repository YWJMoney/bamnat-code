package com.bamnat.app.logic

import android.app.Activity
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
        if (b.show.isNotEmpty() && com.bamnat.core.formula.Formula.eval(b.show, f.scope(date))?.truthy != true) {
            LogicStore(c.appContext).log(b.label, "밖에서 눌렀지만 이 날 보이기 조건이 맞지 않아 안 했어요")
            return "‘${b.label}’은 오늘 조건에 맞지 않아요"
        }
        val r = runCatching { LogicRunner.pressButton(c, b, date, f.scope(date), quiet = true) }
        if (r.isFailure) { LogicStore(c.appContext).log(b.label, "하다가 멈췄어요"); return "‘${b.label}’을 하다가 멈췄어요" }
        changed(c)
        return if (b.act.verb.value && b.act.after == null) line(c, b) else "‘${b.label}’: ${b.act.say()}"
    }

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
                    .setLongLabel("${b.label}: ${b.act.say()}".take(48))
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
        private const val NOTICE_ID = 7601

        /** 1.39 [FX-15] '10분 뒤 다시' ([id] one of [com.bamnat.core.formula.LogicAct.AGAIN]) on an 알림 titled [tag] saying [text]. */
        fun again(ctx: Context, id: String, date: LocalDate, tag: String, text: String, then: List<String>, req: Int): PendingIntent = PendingIntent.getBroadcast(
            ctx, req,
            Intent(ctx, FxPressReceiver::class.java).setData(android.net.Uri.parse("bamnat-fx://again/$id/$date/${tag.hashCode()}")).putExtra(ID, id)
                .putExtra(DATE, date.toString()).putExtra(TAG, tag).putExtra(TEXT, text).putStringArrayListExtra(THEN, ArrayList(then)),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        fun pending(ctx: Context, id: String, date: LocalDate, tag: String, req: Int): PendingIntent = PendingIntent.getBroadcast(
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
    override fun onStartListening() {
        val c = applicationContext.appContainer
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
        c.appScope.launch {
            val said = FxOutside.press(c, b.id)
            withContext(Dispatchers.Main) {
                Toast.makeText(applicationContext, said, Toast.LENGTH_SHORT).show()
                onStartListening()
            }
        }
    }
}

/** 1.39 [FX-04] 앱 바로가기: presses the button the shortcut names and closes, never showing a screen of its own. */
class FxPressActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent?.takeIf { it.action == ACTION }?.getStringExtra(ID)
        if (id == null) { finish(); return }
        val c = applicationContext.appContainer
        c.appScope.launch {
            val said = FxOutside.press(c, id)
            withContext(Dispatchers.Main) { Toast.makeText(applicationContext, said, Toast.LENGTH_SHORT).show() }
        }
        finish()
    }

    companion object {
        const val ACTION = "com.bamnat.app.FX_PRESS"
        const val ID = "id"
    }
}
