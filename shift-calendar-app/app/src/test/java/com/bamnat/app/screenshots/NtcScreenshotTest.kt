package com.bamnat.app.screenshots

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.alarm.TodayShiftNotice
import com.bamnat.app.logic.LogicRunner
import com.bamnat.app.recipe.MyRecipes
import com.bamnat.app.recipe.RecipeApi
import com.bamnat.app.recipe.RecipeDoc
import com.bamnat.app.recipe.RecipeItem
import com.bamnat.app.recipe.RecipePage
import com.bamnat.app.recipe.RecipeTalk
import com.bamnat.app.recipe.Sent
import com.bamnat.app.recipe.Votes
import com.bamnat.core.formula.Logic
import com.bamnat.core.recipe.Recipe
import com.bamnat.core.recipe.RecipeCodec
import com.bamnat.core.work.WorkStyle
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Instant
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.getOrNull

/**
 * 1.39 [NTC-02][NTC-03][NTC-04] 겹치는 알림 정리, numbers 1590~1595, with the real 장터 작품 '열자마자 오늘 정리' (logic.mjs '오늘 요약 한 줄',
 * `docs/recipes/market-136.json`) on a made-up server, the lock-screen 오늘 근무 on (사용자 2026-11-08 사진: 알림 두 개가 함께 떴어요).
 * 9차 PC 확인은 로컬 장터에서 그 작품을 찾지 못해(hold) 이 흐름을 클라우드 화면 시험으로 대신 봐요.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class NtcScreenshotTest : ScreenshotBase() {
    private val c get() = app.container
    private val code = "NTCSUM23"

    private class One(val r: Recipe, val code: String) : RecipeApi {
        val item = RecipeItem(code, r.title, r.summary, r.styles, r.cover, null, 1, likes = 12, dislikes = 0, users = 40, kept = null,
            official = true, publicData = false, parent = null, at = Instant.parse("2026-11-01T00:00:00Z"), tabs = emptyList(), scope = "logic")
        override val enabled = true
        override suspend fun list(style: WorkStyle?, sort: String, q: String, cursor: Int, kind: String?) = RecipePage(listOf(item), null)
        override suspend fun show(code: String, device: String?, version: Int?) = RecipeDoc(item, RecipeCodec.hash(r), RecipeCodec.write(r), "public", mine = false)
        override suspend fun publish(device: String, recipe: Recipe, scope: String) = Sent("NEWC2345", 1, RecipeCodec.hash(recipe), "review", scope)
        override suspend fun newVersion(device: String, code: String, recipe: Recipe, scope: String?) = Sent(code, 2, RecipeCodec.hash(recipe), "public", scope ?: "public")
        override suspend fun delete(device: String, code: String) {}
        override suspend fun vote(device: String, code: String, value: Int?, reason: String?, opinion: String?) = Votes(1, 0, value)
        override suspend fun applied(code: String, kept: Boolean) {}
        override suspend fun flag(device: String, code: String, reason: String) {}
        override suspend fun mine(device: String) = MyRecipes(0, 3, emptyList())
        override suspend fun talk(device: String?, code: String) = RecipeTalk(emptyList(), null)
        override suspend fun talkPost(device: String, code: String, body: String, kind: String, parent: String?, usingDays: Int?) = "p1"
        override suspend fun talkVote(device: String, post: String, value: Int?, reason: String?, opinion: String?) = Votes(0, 0, value)
    }

    /** The 장터's own work, read from the market file the server is filled from. */
    private val summaryWork: Recipe = JSONArray(File("../docs/recipes/market-136.json").readText()).let { a ->
        (0 until a.length()).map { RecipeCodec.read(a.getJSONObject(it).getJSONObject("recipe").toString()) }.first { it.title == "열자마자 오늘 정리" }
    }

    private fun Screen.detail() {
        c.recipeApi = One(summaryWork, code)
        val vm = com.bamnat.app.ui.recipe.RecipeDetailViewModel(c, code, com.bamnat.app.data.PersonalCalendar.MAIN)
        // [RVM-13] With this server the work is read and 내 달력 미리 보기 is worked out inside the constructor (on the main thread,
        // measured 2026-11-12: the overlap is there before the screen is drawn). So a wait that runs out is never "still running":
        // either the overlap was not found as the work was read, or something took it away after. What the state was here is kept
        // for the message, beside what it is when the wait ends.
        val first = said(vm)
        content { com.bamnat.app.ui.recipe.RecipeDetailScreen(vm, onBack = {}, onRemake = {}, onMaker = {}) }
        settle()
        // 2.0.0 (#444 CI shots-b): a tap on 넣기 made before the preview was in did not open the 넣기 창. Every test here turns the
        // lock-screen 오늘 근무 on first, so the preview is in once the overlap it finds is in the state.
        check(waitFor { vm.state.value.pieces.any { it.overlaps.isNotEmpty() } }) {
            "내 달력 미리 보기에 겹침이 없어요. 만든 바로 뒤: $first / 기다린 뒤: ${said(vm)} " +
                "now=${c.recipes.pieces.overlaps(summaryWork.pieces.single().values).size}"
        }
    }

    /** The detail's state and the settings the overlap is worked out from, in one line. */
    private fun said(vm: com.bamnat.app.ui.recipe.RecipeDetailViewModel): String {
        val s = vm.state.value
        return "recipe=${s.recipe?.title} doc=${s.doc?.hash?.take(8)} error=${s.error} message=${s.message} " +
            "pieces=${s.pieces.map { "${it.kind}:on=${it.on},fits=${it.fits},lines=${it.lines.size},overlaps=${it.overlaps.size}" }} " +
            "today=${c.settingsRepository.current.todayShiftNotice} logic=${c.settingsRepository.current.logic.length}"
    }

    private fun lockOn(on: Boolean = true) = c.settingsRepository.update { it.copy(workStyle = WorkStyle.SHIFT, todayShiftNotice = on, logic = "") }

    private val shown get() = shadowOf(app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).activeNotifications.toList()

    /**
     * Up to ten seconds, in steps of 50ms. 2.0.0 [QA-10] (#460 CI shots-b, 79줄): three seconds ran out on a slow runner while the
     * 내 달력 미리 보기 was still on its own thread. A wait that passes ends at once, so only a real failure takes the ten.
     */
    private fun waitFor(ok: () -> Boolean): Boolean {
        repeat(200) { if (ok()) return true; Thread.sleep(50); settle() }
        return ok()
    }

    /** 상세: 넣기 단추 아래에 겹친다는 한 줄 (접힌 '자세히 보기'에 묻히지 않게). */
    @Test fun theDetailSaysItOverlaps() = shoot("1590_ntc_detail", PHONE) {
        lockOn()
        detail()
        assertTrue(scrolledTo("⚠ ‘오늘 요약’은 켜 둔 ‘잠금 화면 오늘 근무’ 알림과 같은 말을 해요 · 넣을 때 바꿀지 골라요", exact = true))
    }

    /** 넣기 창: '기존 설정을 바꿀까요?'와 두 칩, 그 아래 알림 한눈에(켜 둔 것·더 켤 수 있는 것). */
    @Test fun putInAsksFirst() = shoot("1591_ntc_putin_ask", PHONE) {
        lockOn()
        detail()
        tapText("규칙 1개 넣기", exact = true)
        settle()
        assertTrue(appears("기존 설정을 바꿀까요?", exact = true))
        assertTrue(appears("기존 것 끄고 이걸로", exact = true))
        assertTrue(appears("둘 다 켜고 하나로 합치기", exact = true))
    }

    /** 넣기 창 아래: 알림 한눈에를 펼치면 켜 둔 것·더 켤 수 있는 것 두 목록과 스위치. */
    @Test fun putInListsTheNotices() = shoot("1596_ntc_putin_board", PHONE) {
        lockOn()
        detail()
        tapText("규칙 1개 넣기", exact = true)
        settle()
        assertTrue(scrolledTo("알림 한눈에", exact = true))
        tapText("알림 한눈에 펼치기", exact = true)
        settle()
        assertTrue(scrolledTo("켜 둔 알림", exact = true))
        assertTrue(scrolledTo("더 켤 수 있는 알림", exact = true))
    }

    /** 둘 다 켜고 하나로 합치기 → 하루 처음 앱을 열면 알림은 하나(잠금 화면 오늘 근무에 '이달 쓴 돈' 한 줄), 규칙 알림은 따로 안 떠요. */
    @Test fun mergedThereIsOneNotice() = shoot("1592_ntc_merged", PHONE) {
        lockOn()
        detail()
        tapText("규칙 1개 넣기", exact = true)
        settle()
        tapText("둘 다 켜고 하나로 합치기", exact = true)
        settle()
        assertTrue(appears("잠금 화면 ‘오늘 근무’ 알림 하나에 이 규칙의 나머지 말을 한 줄로 더해요. 알림은 하나만 와요", exact = true))
        tapText("넣기", exact = true)
        settle()
        assertTrue(waitFor { Logic.decode(c.settingsRepository.current.logic).rules.any { it.name == "오늘 요약" } })
        assertTrue("잠금 화면 오늘 근무는 켜진 채", c.settingsRepository.current.todayShiftNotice)
        // [RVM-19] PC-48: a 앱을 열면 rule waits for the first opening after the day it went in; this stands for that opening.
        com.bamnat.app.logic.LogicStore(app).lastOpen = null
        LogicRunner.onOpen(c)
        assertTrue(waitFor { TodayShiftNotice.folded(app) != null })
        val ours = shown.filter { it.packageName == app.packageName }
        assertTrue("규칙 알림(7601)은 따로 뜨지 않아요: ${ours.map { it.id }}", ours.none { it.id == 7601 })
        val today = ours.single { it.id == TodayShiftNotice.ID }.notification
        val text = today.extras.getCharSequence(NotificationCompat.EXTRA_TEXT).toString()
        assertTrue("한 알림에 이달 쓴 돈: $text", text.contains("이달 쓴 돈"))
        assertFalse("‘~’ 빈칸 없음: $text", text.contains(" ~ "))
        assertEquals("잠금 화면에는 근무만", NotificationCompat.VISIBILITY_PRIVATE, today.visibility)
        assertFalse(today.publicVersion.extras.getCharSequence(NotificationCompat.EXTRA_TEXT).toString().contains("쓴 돈"))
        // Shown as the notice reads: 제목 · 내용 (the shade is not drawn in a Robolectric shot).
        content {
            androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.padding(24.dp)) {
                androidx.compose.material3.Text("알림창 (알림 ${ours.size}개)", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                ours.forEach { n ->
                    androidx.compose.material3.Text(n.notification.extras.getCharSequence(NotificationCompat.EXTRA_TITLE).toString(),
                        style = androidx.compose.material3.MaterialTheme.typography.titleSmall, modifier = androidx.compose.ui.Modifier.padding(top = 12.dp))
                    androidx.compose.material3.Text((n.notification.extras.getCharSequence(NotificationCompat.EXTRA_BIG_TEXT)
                        ?: n.notification.extras.getCharSequence(NotificationCompat.EXTRA_TEXT)).toString())
                }
            }
        }
    }

    /** 기존 것 끄고 이걸로 → 잠금 화면 오늘 근무 꺼짐, 되돌리기 → 다시 켜짐. */
    @Test fun replaceTurnsTheOldOneOffAndUndoBringsItBack() = shoot("1593_ntc_replace", PHONE) {
        lockOn()
        detail()
        tapText("규칙 1개 넣기", exact = true)
        settle()
        tapText("기존 것 끄고 이걸로", exact = true)
        settle()
        tapText("넣기", exact = true)
        settle()
        assertTrue(waitFor { !c.settingsRepository.current.todayShiftNotice })
        assertTrue(waitFor { shown.none { it.id == TodayShiftNotice.ID } })
        // [RVM-08] With 넣기 on the bottom bar, the snackbar's 되돌리기 (one tap) comes first; the bar's asks once more.
        tapText("되돌리기", exact = true)
        settle()
        if (shows("넣기 전으로 되돌릴까요?", exact = true)) { tapText("되돌리기", exact = true); settle() }
        assertTrue(waitFor { c.settingsRepository.current.todayShiftNotice })
        assertTrue(waitFor { Logic.decode(c.settingsRepository.current.logic).rules.none { it.name == "오늘 요약" } })
    }

    /** 반대 방향: 규칙이 있는데 설정에서 잠금 화면 오늘 근무를 켜면 같은 물음. 제목은 한 번만(9차 PC 확인 하). */
    @Test fun turningTheNoticeOnAsks() = shoot("1594_ntc_turn_on_ask", PHONE) {
        lockOn(false)
        c.settingsRepository.update { it.copy(logic = Logic.encode(Logic.from(summaryWork.pieces.single().values))) }
        settingsPage("알림과 잠")
        settle()
        assertTrue(appears("켜 둔", exact = false))
        assertFalse("카드 제목 아래 줄에 '알림 한눈에'가 다시 나오지 않아요", nodes().count { n ->
            n.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text)?.any { it.text == "알림 한눈에" } == true } > 1)
        tapText("잠금 화면 오늘 근무", exact = true)
        settle()
        assertTrue(appears("기존 설정을 바꿀까요?", exact = true))
        assertTrue(appears("규칙 끄고 켜기", exact = true))
        assertTrue(appears("합쳐서 켜기", exact = true))
    }

    /** 알림 한눈에 펼침: 겹침 표시와 두 목록. */
    @Test fun theBoardMarksTheOverlap() = shoot("1595_ntc_board", PHONE) {
        lockOn()
        c.settingsRepository.update { it.copy(logic = Logic.encode(Logic.from(summaryWork.pieces.single().values))) }
        settingsPage("알림과 잠")
        settle()
        assertTrue(appears("겹침 1", exact = false))
        tapText("알림 한눈에 펼치기", exact = true)
        settle()
        assertTrue(appears("‘오늘 요약’ 규칙과 겹쳐 하나로 합쳐 보여요", exact = false))
    }
}
