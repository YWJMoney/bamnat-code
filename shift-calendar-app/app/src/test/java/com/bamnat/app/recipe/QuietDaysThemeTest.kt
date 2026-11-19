package com.bamnat.app.recipe

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.BamnatApp
import com.bamnat.core.recipe.BuiltInThemes
import com.bamnat.core.recipe.ThemeLease
import com.bamnat.core.time.Today
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration

/**
 * 2.0.0 [RVM-60] 검토 RCP-14·U09b-05 (깐 지 3일 안에 세 번째 테마를 입으면 '광고를 준비하는 중…' 15초 뒤에야 그냥 입혀짐): in the quiet
 * days no theme asks for an ad, the free count runs as before, and after them the third theme asks as it always did.
 */
@RunWith(AndroidJUnit4::class)
class QuietDaysThemeTest {
    private val c get() = ApplicationProvider.getApplicationContext<BamnatApp>().container
    private val themes = BuiltInThemes.of(WorkStyle.SHIFT)

    @After fun letGo() { c.wardrobe.adsSeen = null }

    @Test fun theThirdThemeGoesOnAtOnceInTheFirstDays() = runBlocking {
        c.wardrobe.adsSeen = ThemeWardrobe.AdsSeen(on = true, pro = false, installedAt = Today.instant().minus(Duration.ofHours(5)))
        assertTrue(c.wardrobe.adsQuiet())
        val before = c.wardrobe.lease.value.freeUsed
        themes.take(ThemeLease.FREE_FIRST).forEach { assertTrue(c.wardrobe.wear(it, "builtin:${it.title}", own = false)) }
        assertEquals("처음 두 벌은 전처럼 무료로 세요", before + ThemeLease.FREE_FIRST, c.wardrobe.lease.value.freeUsed)
        val third = themes[ThemeLease.FREE_FIRST]
        assertFalse("세 번째도 광고를 묻지 않아요", c.wardrobe.needsAdFor("builtin:${third.title}", third.title))
        assertTrue("그 까닭을 한 줄로 말해요", c.wardrobe.quietFor("builtin:${third.title}", third.title))
        assertEquals("앱을 깐 뒤 3일 동안은 광고 없이 바로 입어요", com.bamnat.app.ui.recipe.quietLine(c.wardrobe.quietDays()))

        // After the quiet days the third asks for an ad, as it always did.
        c.wardrobe.adsSeen = ThemeWardrobe.AdsSeen(on = true, pro = false, installedAt = Today.instant().minus(Duration.ofDays(4)))
        assertFalse(c.wardrobe.adsQuiet())
        assertTrue(c.wardrobe.needsAdFor("builtin:${third.title}", third.title))
        assertFalse(c.wardrobe.quietFor("builtin:${third.title}", third.title))
        // 프로 never asks, quiet or not.
        c.wardrobe.adsSeen = ThemeWardrobe.AdsSeen(on = true, pro = true, installedAt = Today.instant().minus(Duration.ofHours(5)))
        assertFalse(c.wardrobe.needsAdFor("builtin:${third.title}", third.title))
        assertFalse(c.wardrobe.quietFor("builtin:${third.title}", third.title))
        c.wardrobe.takeOff()
    }
}
