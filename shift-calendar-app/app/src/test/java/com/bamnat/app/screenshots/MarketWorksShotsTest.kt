package com.bamnat.app.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import android.provider.Settings
import android.util.Base64
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.MainActivity
import com.bamnat.app.data.PersonalCalendar
import com.bamnat.app.recipe.RecipePieces
import com.bamnat.app.recipe.RecipeRepository
import com.bamnat.core.recipe.RecipeCodec
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.abs

/**
 * 2.0.0 [RVR-21] (검토 SD-07, 스튜디오 제작 오류 점검 5장 S6-3) 장터 작품을 하나씩 실제로 넣어(받는 사람이 '넣기'를 누를 때와 같은
 * RecipeRepository.apply, 갈래 S EdgeWorksScreenshotTest와 같은 길) 달력을 한 장씩 찍고, 되돌리기로 처음 달력으로 돌아와요.
 *
 * - 늘 (shots-b): 가장자리 작품 모두와 출시 작품 80개마다 하나. 넣다가·그리다가 넘어지는 작품이 없고, 되돌리면 처음 달력과 같아야 해요.
 * - `-Pbamnat.marketShots=all` (CI 'market' 일): 작품 전체를 찍어 build/market-shots/shots.json(작품마다 24×40 색 지문)과
 *   png/에 남겨요. `=market-139`처럼 이름 앞부분이나 `=market-136#15,market-136#16`처럼 이름들을 주면 그 작품들만(사진도 남기고,
 *   되돌린 뒤 달라진 달력은 <이름>_back.png로). tools/market_shots.py compare가 지난 통합 빌드의 shots.json과 대어 크게 달라진 작품만 알려요(실패 아님).
 *
 * 폰의 '애니메이션 삭제'(움직임 끄기와 같은 길)를 켜고 찍어요: 움직이는 작품도 빌드마다 같은 장면이 나와야 비교가 돼요.
 *
 * 되돌린 뒤는 원래 크기 사진끼리 대요: 칸 하나에 남은 점(5dp)도 잡히게. 그 전에 잠깐 뜨는 알림 줄(작품의 '앱 열 때 알림' 규칙이
 * 넣자마자 띄우는 것)은 시간이 지나 사라진 뒤에 찍어요. 처음 달력은 화면이 더 바뀌지 않을 때까지 기다려 찍어요(가계부·일감은 달력보다
 * 늦게 와요: 일찍 찍으면 첫 작품부터 '달라졌다'가 돼요).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class MarketWorksShotsTest : ScreenshotBase() {
    /** -Pbamnat.marketShots: "all", or the start of the names to take ("market-139", "edge:"); unset, the sample. */
    private val mode: String? = System.getProperty("bamnat.marketShots")?.takeIf { it.isNotBlank() }
    private val all = mode == "all"

    private fun capture(scenario: ActivityScenario<MainActivity>): Bitmap {
        lateinit var bmp: Bitmap
        scenario.onActivity { a ->
            val v = a.window.decorView
            bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
            v.draw(Canvas(bmp))
        }
        return bmp
    }

    /** Shots [steady] took beyond the first, over the run (a screen that never stands still costs every try). */
    private var steadyTries = 0

    /** Settles until two shots in a row are the same, so data that comes after the first frame (the money book) is on it. */
    private fun steady(scenario: ActivityScenario<MainActivity>): Bitmap {
        settle()
        var last = capture(scenario)
        repeat(STEADY_TRIES) {
            steadyTries++
            Thread.sleep(50)
            settle()
            val now = capture(scenario)
            if (changedPixels(now, last) == 0) return now
            last = now
        }
        return last
    }

    @Test fun everyWorkDressesTheCalendarAndComesOff() {
        val works = MarketWorks.all().let { list ->
            when {
                all -> list
                mode != null && ',' in mode -> mode.split(',').map { it.trim() }.toSet().let { keys -> list.filter { it.key in keys } }
                mode != null -> list.filter { it.key.startsWith(mode) }
                else -> list.filterIndexed { i, w -> w.edge || i % 80 == 0 }
            }
        }
        assertTrue("docs/recipes에서 작품을 못 찾았어요", works.isNotEmpty())
        val c = app.container
        Settings.Global.putFloat(app.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        RuntimeEnvironment.setQualifiers("w360dp-h640dp-mdpi")
        compose.mainClock.autoAdvance = false
        val out = File("build/market-shots").apply { deleteRecursively() }
        val png = File(out, "png").apply { if (mode != null) mkdirs() }
        val shots = JSONObject()
        val errors = ArrayList<String>()
        val drift = ArrayList<String>()
        lateinit var base: Bitmap
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val first = steady(scenario)
            base = first
            if (mode != null) File(png, "_start.png").outputStream().use { first.compress(Bitmap.CompressFormat.PNG, 100, it) }
            for ((n, w) in works.withIndex()) {
                if (mode != null && n % 25 == 0) File(out, "progress.txt").apply { parentFile!!.mkdirs() }.writeText("$n/${works.size}")
                val row = JSONObject().put("title", w.title)
                var id: Long? = null
                try {
                    val r = RecipeCodec.read(w.body)
                    val picks = r.pieces.map { RecipeRepository.Pick(it.kind, RecipePieces.Mode.REPLACE) }
                    id = runBlocking { c.recipes.apply(r, "SHOT" + n.toString().padStart(4, '0'), 1, PersonalCalendar.MAIN, picks, emptyMap()) }
                    // Not waited for to stand still: a work that moves (반짝임, 흐르는 무늬) never does. Two settles, as before.
                    settle()
                    settle()
                    val bmp = capture(scenario)
                    row.put("fp", Base64.encodeToString(fingerprint(bmp), Base64.NO_WRAP))
                    // Pixels the work changed: 0 for a work that should dress the calendar is told (tools/market_shots.py 'unchanged').
                    row.put("px", changedPixels(bmp, base))
                    if (mode != null) File(png, w.key.replace(Regex("[^A-Za-z0-9._-]"), "_") + ".png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                } catch (e: Throwable) {
                    val why = "${e.javaClass.simpleName}: ${e.message?.take(160)}"
                    row.put("error", why)
                    errors += "${w.key} ${w.title}: $why"
                } finally {
                    if (id != null) runCatching { runBlocking { c.recipes.undo(id) } }.onFailure { errors += "${w.key} 되돌리기: ${it.message?.take(120)}" }
                }
                // 되돌리기 puts the calendar back as it was: checked after each work, so a work that leaves something behind is named.
                var backShot = steady(scenario)
                var left = changedPixels(backShot, base)
                if (left > DRIFT_PIXELS) {
                    // A message the work showed when it went on (its 'on open' rule) times out first, as it would on the phone. Only
                    // here: the test clock draws every frame of the wait.
                    compose.mainClock.advanceTimeBy(MESSAGE_GONE_MS)
                    backShot = steady(scenario)
                    left = changedPixels(backShot, base)
                }
                if (left > DRIFT_PIXELS) {
                    if (mode != null) File(png, w.key.replace(Regex("[^A-Za-z0-9._-]"), "_") + "_back.png").outputStream()
                        .use { backShot.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    drift += "${w.key} ${w.title} (${left}px)"
                    base = backShot // the next works are compared with what is now there
                }
                shots.put(w.key, row)
            }
        }
        out.mkdirs()
        System.err.println("market shots: ${works.size} works, steady tries $steadyTries")
        File(out, "shots.json").writeText(JSONObject().put("all", all).put("steadyTries", steadyTries).put("size", JSONArray(listOf(W, H))).put("works", shots)
            .put("drift", JSONArray(drift)).toString())
        assertTrue("넣거나 그리다 넘어진 작품 ${errors.size}개:\n" + errors.take(20).joinToString("\n"), errors.isEmpty())
        assertTrue("되돌린 뒤 달력이 처음과 달라요 ${drift.size}개:\n" + drift.take(20).joinToString("\n"), drift.isEmpty())
    }

    companion object {
        /** The fingerprint: the shot shrunk to W×H, three bytes a pixel (tools/market_shots.py reads the same). */
        const val W = 24
        const val H = 40
        /** Pixels (of 360×640) that may differ after 되돌리기: a 5dp dot left in one cell is about 20. */
        const val DRIFT_PIXELS = 12
        /** A channel must move this much for a pixel to count as changed (text antialiasing moves a few). */
        const val PIXEL_STEP = 40
        /** Longer than the longest message the app shows (SnackbarDuration.Long is 10 s). */
        const val MESSAGE_GONE_MS = 12_000L
        const val STEADY_TRIES = 6

        fun fingerprint(b: Bitmap): ByteArray {
            val s = Bitmap.createScaledBitmap(b, W, H, true)
            val px = IntArray(W * H).also { s.getPixels(it, 0, W, 0, 0, W, H) }
            return ByteArray(W * H * 3).also { o ->
                px.forEachIndexed { i, p -> o[i * 3] = (p shr 16).toByte(); o[i * 3 + 1] = (p shr 8).toByte(); o[i * 3 + 2] = p.toByte() }
            }
        }

        fun changedPixels(a: Bitmap, b: Bitmap): Int {
            if (a.width != b.width || a.height != b.height) return a.width * a.height
            val n = a.width * a.height
            val pa = IntArray(n).also { a.getPixels(it, 0, a.width, 0, 0, a.width, a.height) }
            val pb = IntArray(n).also { b.getPixels(it, 0, b.width, 0, 0, b.width, b.height) }
            var count = 0
            for (i in 0 until n) {
                val x = pa[i]
                val y = pb[i]
                if (x == y) continue
                if (abs((x shr 16 and 0xFF) - (y shr 16 and 0xFF)) > PIXEL_STEP || abs((x shr 8 and 0xFF) - (y shr 8 and 0xFF)) > PIXEL_STEP ||
                    abs((x and 0xFF) - (y and 0xFF)) > PIXEL_STEP) count++
            }
            return count
        }
    }
}
