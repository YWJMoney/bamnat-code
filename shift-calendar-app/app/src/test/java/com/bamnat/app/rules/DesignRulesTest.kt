package com.bamnat.app.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 2.0.0 [RVP-83] 디자인 품질 조건(`docs/디자인-품질-조건.md` 3절 '세는 시험')을 사람 눈 대신 지켜요. 공통 부품과 값은
 * `docs/design/공통-부품.md`. 숫자는 줄어드는 쪽으로만 가요: 줄이면 아래 천장도 같이 내려 주세요(그래야 다시 늘지 않아요).
 *
 * - 조건 5: 공통 값 밖 모서리 0곳 ([cornersAreOnTheScale]), 공통 값 밖 여백은 늘지 않아요 ([paddingOffTheScaleOnlyGoesDown])
 * - 조건 4: 8~9sp 글자 0곳 ([noTextUnderTenSp])
 * - 조건 9: 44dp보다 작은 누르는 곳 0곳 ([pressedThingsAreAtLeast44dp])
 * - 조건 2: 한 화면(함수)에 채운 단추 하나 ([oneFilledButtonPerScreen])
 * - 조건 7: 토스트·화면 코드 속 색 값은 늘지 않아요 ([toastsAndRawColoursOnlyGoDown])
 * - 조건 7·10: 알림 줄은 앱에 하나(AppSnack), 화면이 따로 만들지 않아요 ([oneLineForTheWholeApp])
 * - 조건 3·5: 카드 바탕은 조용한 면, primaryContainer는 고른 것·채운 단추에만 ([cardsStandOnTheQuietGround])
 * - 조건 6: 주의는 Tone.Caution, 위험은 Tone.Danger. 일요일 빨강인 달력 색 warning은 화면이 쓰지 않아요 ([cautionIsNotSundaysRed])
 * - 조건 5: 바탕이 있는 단추는 앱의 것, 모서리 '각지게'를 따라요 ([buttonsFollowTheCorners])
 * - 조건 12: 위 막대는 앱의 것 하나, 큰 글자에서 제목이 잘리지 않아요 ([oneTopBarThatHoldsItsTitle])
 * - 조건 1: 달력 시작 높이는 화면 사진 시험 `CalendarStartScreenshotTest`가 봐요.
 *
 * 실패하면 무엇을 써야 하는지 메시지가 알려 줘요. 정말 예외라면(그림 속 휴대폰 틀처럼) 이 파일의 예외 목록에 까닭과 함께 넣어요.
 */
class DesignRulesTest {
    private val app = File("src/main/java/com/bamnat/app")
    private val wear = File("../wear/src/main/java/com/bamnat/wear")

    private class Src(val path: String, val text: String) {
        /** Lines with a modifier chain that goes on with `.` on the next lines joined to the line it starts on. */
        val joined: List<Pair<Int, String>> by lazy {
            val out = ArrayList<Pair<Int, String>>()
            text.split('\n').forEachIndexed { i, l ->
                if (out.isNotEmpty() && l.trimStart().startsWith(".")) out[out.lastIndex] = out.last().first to out.last().second + l.trim()
                else out.add(i + 1 to l)
            }
            out
        }
    }

    private fun sources(root: File, skip: (String) -> Boolean = { false }): List<Src> =
        root.walkTopDown().filter { it.isFile && it.extension == "kt" }
            .map { Src(it.relativeTo(root).invariantSeparatorsPath, it.readText()) }
            .filter { !skip(it.path) }.sortedBy { it.path }.toList()

    /** Screens: everything but home-screen widgets (Glance has its own corners and sizes) and the theme that defines the values. */
    private val screens by lazy { sources(app) { it.startsWith("widget/") || it == "ui/theme/Theme.kt" || it == "ui/components/Kani.kt" } }

    // ---- 조건 5: 모서리 ----

    /**
     * Corners are `MaterialTheme.shapes` (extraSmall 6 · small 10 · medium 14 · large 20 · extraLarge 28, 설정 › 모서리 따라 바뀜),
     * [androidx.compose.foundation.shape.CircleShape] or a percent (`RoundedCornerShape(50)`: 알약). A written dp is only for a
     * hairline, a bar or a dot (4dp or less). Exceptions are drawings that are not the app's own surface.
     */
    @Test fun cornersAreOnTheScale() {
        val allowed = mapOf(
            "ui/recipe/MarketShot.kt" to "장터 작품 사진 속 휴대폰 틀(앱 화면이 아니라 그림)",
            "ui/recipe/RecipeStudio.kt" to "스튜디오 미리보기 속 휴대폰 틀(그림)",
            "ui/calendar/MonthGrid.kt" to "cellShape: @Composable 밖 기본값(달력 칸 8dp, 칸 꾸미기 모서리가 덮어씀)",
            "ui/theme/PhotoStyleUi.kt" to "frameShape fallback: @Composable 밖 기본값(사진 틀 6dp)",
        )
        val dp = Regex("""RoundedCornerShape\(([^()]*)\)""")
        val bad = screens.flatMap { s ->
            s.joined.flatMap { (n, l) ->
                dp.findAll(l).mapNotNull { m ->
                    val values = Regex("""(\d+(?:\.\d+)?)\.dp""").findAll(m.groupValues[1]).map { it.groupValues[1].toFloat() }.toList()
                    if (values.none { it > 4f }) null
                    else if (allowed.containsKey(s.path) && allowedLine(s.path, l)) null
                    else "${s.path}:$n ${m.value}"
                }.toList()
            }
        }
        assertEquals(
            "공통 값 밖 모서리 → MaterialTheme.shapes.extraSmall/small/medium/large/extraLarge, CircleShape, 알약은 RoundedCornerShape(50) (docs/design/공통-부품.md 2절)",
            emptyList<String>(), bad,
        )
    }

    private fun allowedLine(path: String, line: String): Boolean = when (path) {
        "ui/calendar/MonthGrid.kt" -> "look?.corner" in line
        "ui/theme/PhotoStyleUi.kt" -> "fallback: Shape" in line
        else -> true
    }

    // ---- 조건 5: 여백 ----

    /** The spacing scale (docs/design/공통-부품.md 2절). 1dp is a hairline next to a border; it is counted like the rest. */
    private val spacingScale = setOf(0f, 2f, 4f, 6f, 8f, 12f, 16f, 20f, 24f, 32f, 40f, 48f, 56f, 64f, 72f, 80f, 88f, 96f)

    private fun offScaleSpacing(): List<String> {
        val call = Regex("""\.padding\(([^()]*)\)|spacedBy\(([^(),]*)[,)]""")
        val num = Regex("""(\d+(?:\.\d+)?)\.dp""")
        return sources(app).flatMap { s ->
            call.findAll(s.text).flatMap { m ->
                num.findAll(m.groupValues[1] + m.groupValues[2]).map { it.groupValues[1].toFloat() }
                    .filter { it !in spacingScale }.map { v -> "${s.path} ${v}dp" }
            }.toList()
        }
    }

    @Test fun paddingOffTheScaleOnlyGoesDown() {
        val found = offScaleSpacing()
        report("공통 값 밖 여백(padding·spacedBy)", found.size, PADDING_CEILING, 0)
        assertTrue(
            "공통 값 밖 여백이 ${found.size}곳으로 늘었어요(천장 $PADDING_CEILING). 0·2·4·6·8·12·16·20·24·32… 중에서 골라 주세요(docs/design/공통-부품.md 2절). " +
                "줄였다면 PADDING_CEILING을 ${found.size}로 내려 주세요.",
            found.size <= PADDING_CEILING,
        )
    }

    // ---- 조건 4: 글자 ----

    /** No text under 10sp, nor a text that may shrink under 10sp (autoSize minimum), in the app or on the watch. */
    @Test fun noTextUnderTenSp() {
        val small = Regex("""(?<![\d.])(\d(?:\.\d+)?)\.sp\b""")
        val bad = (screens + sources(wear).map { Src("wear/" + it.path, it.text) }).flatMap { s ->
            s.joined.flatMap { (n, l) ->
                small.findAll(l).filter { m ->
                    val before = l.substring(0, m.range.first).trimEnd()
                    listOf("letterSpacing =", "lineHeight =", "stepSize =").none { before.endsWith(it) }
                }.map { "${s.path}:$n ${it.value}" }.toList()
            }
        }
        // 2.0.0 P 4/4 (UIX-11): the 현장 칸 막대 글자, the last two waiting, are 10sp now; none is left to wait.
        assertEquals("8~9sp 글자는 쓰지 않아요: 10sp 이상, 줄일 땐 한 단계 낮은 글자 모양(MaterialTheme.typography)",
            emptyList<String>(), bad)
    }

    // ---- 조건 9: 누르는 곳 ----

    /**
     * A pressed thing is 48dp (44dp at the least): a modifier chain that is pressed and sized under 44dp needs
     * `minimumInteractiveComponentSize()` first (it keeps the look, the touch grows to 48dp) or `heightIn(min = 48.dp)`;
     * an [androidx.compose.material3.IconButton] keeps its own 48dp (no `Modifier.size` under 44dp on it).
     * A chip's X is [com.bamnat.app.ui.components.ChipClear].
     */
    @Test fun pressedThingsAreAtLeast44dp() {
        val sized = Regex("""\.(?:size|height|requiredSize|requiredHeight)\((\d+)\.dp\)""")
        val pressed = Regex("""\.(clickable|combinedClickable|selectable|toggleable)\b""")
        val bad = screens.flatMap { s ->
            s.joined.flatMap { (n, l) ->
                val chains = pressed.findAll(l).mapNotNull { m ->
                    val before = l.substring(0, m.range.first)
                    val k = before.lastIndexOf("Modifier")
                    if (k < 0) return@mapNotNull null
                    val chain = before.substring(k)
                    if ("minimumInteractiveComponentSize" in chain || "heightIn(min = 4" in chain || "sizeIn(min" in chain) return@mapNotNull null
                    val sizes = sized.findAll(chain).map { it.groupValues[1].toInt() }.toList()
                    if (sizes.isNotEmpty() && sizes.min() < 44) "${s.path}:$n" else null
                }.toList()
                val icons = Regex("""IconButton\([^\n]*?modifier = Modifier[^,)]*?\.size\((\d+)\.dp\)""").findAll(l)
                    .filter { it.groupValues[1].toInt() < 44 }.map { "${s.path}:$n IconButton ${it.groupValues[1]}dp" }.toList()
                chains + icons
            }
        }
        assertEquals("44dp보다 작은 누르는 곳 → Modifier.minimumInteractiveComponentSize() 먼저, 또는 heightIn(min = 48.dp) (docs/design/공통-부품.md 4절)",
            emptyList<String>(), bad)
    }

    // ---- 조건 2: 채운 단추 ----

    /**
     * Filled [androidx.compose.material3.Button]s in one top-level function (a screen, a 창 or a sheet): one at most. The
     * files below already had more and may only lose them; a new file starts at one. The others are TextButton (2 at most) or
     * go under '더 보기' (docs/design/공통-부품.md 3절).
     */
    @Test fun oneFilledButtonPerScreen() {
        val filled = Regex("""(?<![A-Za-z0-9_])Button\(""")
        val now = screens.filter { it.path != "ui/components/AppButton.kt" }.associate { s ->
            s.path to s.text.split(Regex("""\n(?=(?:private |internal |public )?fun )""")).sumOf { part ->
                (filled.findAll(part).count() - 1).coerceAtLeast(0)
            }
        }.filterValues { it > 0 }
        val grown = now.filter { (path, extra) -> extra > (FILLED_EXTRA[path] ?: 0) }
        report("채운 단추가 둘 이상인 화면(넘친 수)", now.values.sum(), FILLED_EXTRA.values.sum(), 0)
        assertEquals("한 화면에 채운 단추는 하나: 나머지는 TextButton(2개까지)이나 '더 보기'로. 줄였다면 FILLED_EXTRA도 내려 주세요.",
            emptyMap<String, Int>(), grown)
    }

    // ---- 조건 7: 토스트·색 ----

    @Test fun toastsAndRawColoursOnlyGoDown() {
        val toasts = sources(app).sumOf { Regex("""Toast\.makeText""").findAll(it.text).count() }
        val colours = screens.filter { it.path.startsWith("ui/") && !it.path.startsWith("ui/theme/") }
            .sumOf { Regex("""Color\(0x""").findAll(it.text).count() }
        report("토스트", toasts, TOAST_CEILING, 0)
        report("화면 코드 속 색 값 Color(0x…)", colours, COLOUR_CEILING, 0)
        assertTrue("토스트가 $toasts 곳으로 늘었어요: 알림 줄(AppSnack.show)이나 OfflineLine을 써요", toasts <= TOAST_CEILING)
        assertTrue("화면 코드 속 색 값이 $colours 곳으로 늘었어요: MaterialTheme.colorScheme, Tone.color(), LocalCalendarColors를 써요", colours <= COLOUR_CEILING)
    }

    /**
     * 2.0.0 [RVP-01] (통합 HQ-06, 조건 3·5) A card's ground is the quiet one (`surfaceContainerLow`, as [com.bamnat.app.ui.components.NudgeCard]).
     * `primaryContainer` is what is picked or filled (a chosen chip, a picked choice card, a tonal button): in the dark it is the same
     * strong blue, and a card on it read as one big button (G #447 사진 08). A ground that is `primaryContainer` only while picked is fine.
     */
    @Test fun cardsStandOnTheQuietGround() {
        val ground = Regex("""(?<![A-Za-z])(color|containerColor) = MaterialTheme\.colorScheme\.primaryContainer(?!\.copy)""")
        val bad = screens.flatMap { s ->
            s.text.split('\n').mapIndexedNotNull { i, l -> if (ground.containsMatchIn(l) && !l.contains("if (") && !l.contains("CircleShape")) "${s.path}:${i + 1}" else null }
        }
        report("카드 바탕 primaryContainer", bad.size, 0, 0)
        assertEquals("카드 바탕은 surfaceContainerLow(조용한 면)예요. primaryContainer는 고른 것·채운 단추에만(docs/design/공통-부품.md 2절)", emptyList<String>(), bad)
    }

    /**
     * 2.0.0 [RVP-23] 뿌리 고침 4 (조건 7·10): one line for the whole app. A screen with a `SnackbarHostState()` or `SnackbarHost(…)` of
     * its own had its own queue, and its 되돌리기 waited behind another's. Now [com.bamnat.app.ui.components.AppSnack]: `rememberLane()`
     * for a screen that still calls `showSnackbar`, `AppSnackHost()` where the line shows.
     */
    @Test fun oneLineForTheWholeApp() {
        val own = Regex("""SnackbarHostState\(\)|(?<![A-Za-z.])SnackbarHost\(""")
        val bad = screens.filter { it.path != "ui/components/AppSnack.kt" }.flatMap { s ->
            s.text.split('\n').mapIndexedNotNull { i, l -> if (own.containsMatchIn(l)) "${s.path}:${i + 1}" else null }
        }
        assertEquals("화면이 따로 만든 알림 줄: AppSnack.rememberLane()과 AppSnackHost()를 써요(docs/design/공통-부품.md 9절)", emptyList<String>(), bad)
    }

    /**
     * 2.0.0 [RVP-13] (검토 HQ-54·UIX-15, 조건 6) The calendar colours' `warning`·`warningContainer` are Sunday's red under another name:
     * screens that wrote '확인할 것' in them painted a thing to check as a danger, and one screen had red four or five times. A screen says
     * 주의 with `Tone.Caution` and 위험 (넘김·밀림·법 위반·울리지 않을 알람) with `Tone.Danger` (`Tone.color()`, `Tone.container()`, or the
     * one alert line [com.bamnat.app.ui.components.ToneLine]).
     */
    @Test fun cautionIsNotSundaysRed() {
        val read = Regex("""(?<![A-Za-z0-9_])(\w+)\.warning(Container)?\b(?!\s*=[^=])""")
        val notColours = setOf("standing") // 마을 돌봄의 경고(서버가 보낸 글)
        val bad = screens.filter { !it.path.startsWith("ui/theme/") }.flatMap { s ->
            s.joined.flatMap { (n, l) -> read.findAll(l).filter { it.groupValues[1] !in notColours }.map { "${s.path}:$n" }.toList() }
        }
        report("화면이 쓴 달력 색 warning(일요일 빨강)", bad.size, 0, 0)
        assertEquals("주의는 Tone.Caution.color()·ToneLine, 위험(넘김·밀림·법 위반)은 Tone.Danger. 바탕은 Tone.container() (docs/design/공통-부품.md 4절)",
            emptyList<String>(), bad)
    }

    /**
     * 2.0.0 [RVP-85] 검토 HQ-53 (조건 12): the app has one top bar, [com.bamnat.app.ui.components.TopAppBar]. Material's own is 64dp
     * whatever its title, so a title that took two lines at 글자 200% or on a folded screen lost its second line under the bar's edge.
     */
    @Test fun oneTopBarThatHoldsItsTitle() {
        val material = Regex("""import androidx\.compose\.material3\.(TopAppBar|CenterAlignedTopAppBar|MediumTopAppBar|LargeTopAppBar)$|androidx\.compose\.material3\.(?:CenterAligned|Medium|Large)?TopAppBar\(""")
        val bad = screens.filter { it.path != "ui/components/TopBar.kt" }.flatMap { s ->
            s.text.split('\n').mapIndexedNotNull { i, l -> if (material.containsMatchIn(l.trim())) "${s.path}:${i + 1}" else null }
        }
        assertEquals("위 막대는 com.bamnat.app.ui.components.TopAppBar 하나예요(큰 글자에서 제목이 잘리지 않아요, docs/design/공통-부품.md)",
            emptyList<String>(), bad)
    }

    /**
     * 2.0.0 [RVP-05] 검토 PC-128 (조건 5): buttons with a ground follow 모서리 too. Material's are round whatever the theme, so with '각지게'
     * the cards and chips went square and the buttons beside them stayed round. The app's own ([com.bamnat.app.ui.components.Button],
     * `FilledTonalButton`, `OutlinedButton`, `FilledTonalIconButton`, `OutlinedIconButton`) take `MaterialTheme.pill`.
     */
    @Test fun buttonsFollowTheCorners() {
        val kinds = "Button|OutlinedButton|FilledTonalButton|ElevatedButton|FilledIconButton|FilledTonalIconButton|OutlinedIconButton"
        val material = Regex("""^import androidx\.compose\.material3\.($kinds)$|androidx\.compose\.material3\.($kinds)\(""")
        val own = setOf("ui/components/AppButton.kt", "ui/components/Buttons.kt")
        val bad = screens.filter { it.path !in own }.flatMap { s ->
            s.text.split('\n').mapIndexedNotNull { i, l -> if (material.containsMatchIn(l.trim())) "${s.path}:${i + 1}" else null }
        }
        assertEquals("바탕이 있는 단추는 com.bamnat.app.ui.components의 것을 써요(모서리 '각지게'를 따라요, docs/design/공통-부품.md)",
            emptyList<String>(), bad)
    }

    /** The numbers for the PR (도약 숫자): build/reports/design-rules.txt. */
    private fun report(what: String, now: Int, ceiling: Int, goal: Int) {
        val f = File("build/reports/design-rules.txt").apply { parentFile?.mkdirs() }
        val line = "$what: 지금 $now · 천장 $ceiling · 목표 $goal"
        val old = if (f.exists()) f.readLines().filterNot { it.startsWith("$what:") } else emptyList()
        f.writeText((old + line).sorted().joinToString("\n") + "\n")
    }

    companion object {
        /** 2.0.0 첫 PR(갈래 P)에서 센 값 471, 2/3(H·D가 들어간 통합 위)에서 470. */
        const val PADDING_CEILING = 470
        /** 46 at the first PR; 43 once 테마 기간·새 판 said theirs on the app's line (2.0.0 [RVP-23]); 42 with 급여 리포트 on `ReportShare.orTell` (RVP-69). */
        const val TOAST_CEILING = 42
        const val COLOUR_CEILING = 74

        /** Screens that had more than one filled button when the rule came (2.0.0): how many over one, by file. */
        val FILLED_EXTRA: Map<String, Int> = mapOf(
            "ui/company/SendGuardUi.kt" to 5,
            "ui/company/CompanyNudges.kt" to 3,
            "ui/team/LeaderTeamScreen.kt" to 3,
            "ui/calendar/ShareDialog.kt" to 2,
            "ui/company/PublicCycleUi.kt" to 2,
            "ui/company/SharedPayLock.kt" to 2,
            "ui/pay/PayScreen.kt" to 2,
            "ui/pay/PayslipMatchDialog.kt" to 2,
            "ui/recipe/RecipeMakeScreen.kt" to 2,
            "ui/calendar/DayDetail.kt" to 1,
            "ui/calendar/ReceivedCalendarUi.kt" to 1,
            "ui/company/CompanyFindScreen.kt" to 1,
            "ui/company/CompanyReportScreen.kt" to 1,
            "ui/company/PayFindSheet.kt" to 1,
            "ui/company/ShareWayNudge.kt" to 1,
            "ui/guide/AskCard.kt" to 1,
            "ui/imports/AttendanceImportScreen.kt" to 1,
            "ui/imports/CalendarImportScreen.kt" to 1,
            "ui/pay/PayQuickSetup.kt" to 1,
            "ui/pay/PayslipCheck.kt" to 1,
            "ui/pay/PayslipImport.kt" to 1,
            "ui/pay/ScanButtons.kt" to 1,
            "ui/pro/ProShareCard.kt" to 1,
            "ui/recipe/FxMarket.kt" to 1,
            "ui/recipe/ThemeSheet.kt" to 1,
            "ui/settings/InquiryScreen.kt" to 1,
            "ui/setup/SetupOrder.kt" to 1,
            "ui/sleep/NightScreen.kt" to 1,
            "ui/sleep/SleepScreen.kt" to 1,
            "ui/stats/WorkTypeCards.kt" to 1,
        )
    }
}
