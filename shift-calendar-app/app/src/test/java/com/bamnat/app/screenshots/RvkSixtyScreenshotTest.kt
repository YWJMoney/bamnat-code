package com.bamnat.app.screenshots

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 2.0.0 갈래 K 13차 [RVK-60] (검토 I06ab-01, DB 37): 분류를 고정 id로. 분류 고치기 › 수입 › 급여 이름 바꾸기: before, a box said 급여 keeps
 * its name; now the field opens and says the sums stay as they are. 지출 › 카페 → 커피, 저장, 다시 열기: before, 카페 came back at the end of
 * the list beside 커피; now it does not, and 커피 keeps 카페's picture. The code before draws the same scenes. Today is 2026-10-14.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RvkSixtyScreenshotTest : ScreenshotBase() {
    private fun editor(wide: Boolean, then: Screen.() -> Unit): Screen.() -> Unit = ledgerPage(null, wide) {
        tapText("돈 적기", exact = true, scrolls = 0)
        tapText("분류 고치기", exact = true)
        then()
    }

    private fun salaryRename(wide: Boolean = false): Screen.() -> Unit = editor(wide) {
        tapText("수입", exact = true, scrolls = 0)
        tapText("급여 이름 바꾸기", exact = true)
        // The code before shows a box instead of a field.
        runCatching { typeInto("분류 이름", "월급") }
        settle()
    }

    private fun cafeRenamed(wide: Boolean = false): Screen.() -> Unit = editor(wide) {
        tapText("카페 이름 바꾸기", exact = true)
        typeInto("분류 이름", "커피")
        tapText("바꾸기", exact = true, scrolls = 0)
        tapText("지출 분류 저장", exact = true, scrolls = 0)
        settle()
        tapText("분류 고치기", exact = true)
        reveal("기타 (늘 마지막)", exact = true)
    }

    /** The top of the list after: 커피 where 카페 was, with its cup. */
    private fun cafeRenamedTop(): Screen.() -> Unit = editor(false) {
        tapText("카페 이름 바꾸기", exact = true)
        typeInto("분류 이름", "커피")
        tapText("바꾸기", exact = true, scrolls = 0)
        tapText("지출 분류 저장", exact = true, scrolls = 0)
        settle()
        tapText("분류 고치기", exact = true)
    }

    @Test fun salaryRenameLight() = shoot("rvk60_01_salary_rename_folded", FOLDED, salaryRename())
    @Test fun salaryRenameDarkBig() = shoot("rvk60_02_salary_rename_folded_dark_200", DARK, 2f, salaryRename())
    @Test fun salaryRenameWide() = shoot("rvk60_03_salary_rename_unfolded", UNFOLDED, salaryRename(wide = true))
    @Test fun renamedListLight() = shoot("rvk60_04_renamed_list_folded", FOLDED, cafeRenamed())
    @Test fun renamedListDarkBig() = shoot("rvk60_05_renamed_list_folded_dark_200", DARK, 2f, cafeRenamed())
    @Test fun renamedTopLight() = shoot("rvk60_07_renamed_top_folded", FOLDED, cafeRenamedTop())
    @Test fun renamedListWide() = shoot("rvk60_06_renamed_list_unfolded", UNFOLDED, cafeRenamed(wide = true))

    private companion object {
        const val DARK = "w369dp-h905dp-port-night-420dpi"
    }
}
