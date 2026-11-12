package com.bamnat.app.screenshots

import android.app.KeyguardManager
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.data.PhoneMove
import com.bamnat.core.boss.StaffMember
import com.bamnat.core.boss.Workplace
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 2.0.0 [RVL-02][RVL-06][RVL-07][RVL-09][RVL-13] 갈래 L 1차의 화면 (디자인 품질 조건): 달력 위 안내 카드 셋(불러오기 멈춤, 새 휴대폰으로
 * 옮겨 옴, 앱 잠금 쉼)과 닫은 뒤 설정의 한 줄, '옮겨 온 것 확인' 시트, 암호 없는 백업에서 빠지는 것을 먼저 말하는 저장 창. 밝은·어두운 × 글자 100%·200% × 폴드
 * 접은 폭·편 폭. 카드는 공통 안내 카드 한 모양이고 채운 단추는 없어요(글자 단추 하나).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RvlDataScreenshotTest : ScreenshotBase() {
    private val c get() = app.container

    private fun stopped(): Screen.() -> Unit = {
        c.restoreStopped.value = true
        settle()
        assertTrue(appears("불러오기가 멈춰 전 기록으로"))
    }

    @Test fun stoppedFoldLight() = shoot("rvl02_stopped_fold_light_100", FOLDED, stopped())
    @Test fun stoppedFoldDarkHuge() = shoot("rvl02_stopped_fold_dark_200", FOLDED_NIGHT, fontScale = 2f, stopped())
    @Test fun stoppedOpenLight() = shoot("rvl02_stopped_open_light_100", UNFOLDED, stopped())

    /** A phone the data came to by Android's own backup, with a 리더's roster to join again (sealed part came). */
    private fun arrived(sealed: Boolean = true) {
        val noBackup = app.noBackupFilesDir
        File(noBackup, "phone_here").delete()
        File(noBackup, "phone_moved.json").delete()
        File(app.filesDir, PhoneMove.CARRY).apply { parentFile!!.mkdirs() }.writeText(JSONObject().toString())
        val keys = File(app.dataDir, PhoneMove.KEYS).apply { parentFile!!.mkdirs() }
        if (sealed) keys.writeText(JSONObject().put(PhoneMove.RECEIVED, JSONArray().put(
            JSONObject().put("id", "AbCdEfGh12345678").put("title", "3병동 김간호").put("mine", true).put("adopted", true))).toString())
        else keys.delete()
        c.phoneMove.arrive()
    }

    private fun moved(sealed: Boolean = true): Screen.() -> Unit = {
        arrived(sealed)
        settle()
        assertTrue(appears("새 휴대폰으로 옮겨 왔어요"))
    }

    @Test fun movedFoldLight() = shoot("rvl06_moved_fold_light_100", FOLDED, moved())
    @Test fun movedFoldDark() = shoot("rvl06_moved_fold_dark_100", FOLDED_NIGHT, moved())
    @Test fun movedFoldLightHuge() = shoot("rvl06_moved_fold_light_200", FOLDED, fontScale = 2f, moved())
    @Test fun movedFoldDarkHuge() = shoot("rvl06_moved_fold_dark_200", FOLDED_NIGHT, fontScale = 2f, moved())
    @Test fun movedOpenLight() = shoot("rvl06_moved_open_light_100", UNFOLDED, moved())
    @Test fun movedOpenDarkHuge() = shoot("rvl06_moved_open_dark_200", UNFOLDED_NIGHT, fontScale = 2f, moved())
    @Test fun movedUnsealedFoldLight() = shoot("rvl06_moved_unsealed_fold_light_100", FOLDED, moved(sealed = false))

    /**
     * 총괄 검토 #458: '옮겨 온 것 확인' is a sheet; each thing to do again is a row that goes there, done ones ticked. [sleep]: a phone whose
     * settings came with 수면 기록 and 방해 금지 on (쓰던 분만), so those rows show too.
     */
    private fun movedSheet(sleep: Boolean = false): Screen.() -> Unit = {
        if (sleep) c.settingsRepository.update { it.copy(healthSleep = true, sleepDnd = true) }
        moved().invoke(this)
        tapText("새 휴대폰으로 옮겨 왔어요")
        settle()
        assertTrue(appears("그대로 왔어요"))
        assertTrue(appears("받은 근무표 1개 다시 잇기"))
        if (sleep) assertTrue(appears("자는 동안 방해 금지 접근 허용"))
        else assertTrue(!shows("방해 금지"))
    }

    /**
     * 200%: the sheet's last line (the photos) is below the first screen, where '아래 더 있음' stands at the foot (a picture only: the line
     * has no words for a screen reader, which scrolls by itself), and is reached by scrolling inside the sheet (조건 12). The _end shot
     * shows the foot without it.
     */
    private fun movedSheetEnd(): Screen.() -> Unit = {
        movedSheet(sleep = true).invoke(this)
        assertTrue(!shows("사진도 백업"))
        repeat(6) { scrollUp(400) }
        assertTrue(appears("사진도 백업"))
        assertTrue(shows("옮겨 온 것 확인"))
    }

    @Test fun movedSheetFoldLight() = shoot("rvl06_moved_sheet_fold_light_100", FOLDED, movedSheet())
    @Test fun movedSheetFoldDark() = shoot("rvl06_moved_sheet_fold_dark_100", FOLDED_NIGHT, movedSheet(sleep = true))
    @Test fun movedSheetFoldDarkHuge() = shoot("rvl06_moved_sheet_fold_dark_200", FOLDED_NIGHT, fontScale = 2f, movedSheet(sleep = true))
    @Test fun movedSheetFoldDarkHugeEnd() = shoot("rvl06_moved_sheet_fold_dark_200_end", FOLDED_NIGHT, fontScale = 2f, movedSheetEnd())
    @Test fun movedSheetOpenLight() = shoot("rvl06_moved_sheet_open_light_100", UNFOLDED, movedSheet(sleep = true))
    @Test fun movedSheetOpenDarkHuge() = shoot("rvl06_moved_sheet_open_dark_200", UNFOLDED_NIGHT, fontScale = 2f, movedSheet())

    /** 앱 잠금 on, but the phone has no lock of its own (Robolectric's keyguard is not secure). */
    private fun resting(): Screen.() -> Unit = {
        shadowOf(app.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).setIsDeviceSecure(false)
        c.settingsRepository.update { it.copy(appLock = true) }
        settle()
        assertTrue(appears("휴대폰 잠금이 없어 앱 잠금이 쉬고 있어요"))
    }

    @Test fun restingFoldLight() = shoot("rvl09_resting_fold_light_100", FOLDED, resting())
    @Test fun restingFoldDarkHuge() = shoot("rvl09_resting_fold_dark_200", FOLDED_NIGHT, fontScale = 2f, resting())
    @Test fun restingOpenLight() = shoot("rvl09_resting_open_light_100", UNFOLDED, resting())

    /**
     * 총괄 검토 #458 (조건 3): the card has an X; closed, it stays closed (also after the app comes back to the front) and the state is one
     * line by the switch in 설정 › 개인정보, with the way to the phone's lock.
     */
    private fun restingClosed(): Screen.() -> Unit = {
        resting().invoke(this)
        tapText("이 안내 닫기", exact = true)
        assertTrue(gone("휴대폰 잠금이 없어 앱 잠금이 쉬고 있어요"))
        assertTrue(!com.bamnat.app.AppLock.restingCardShown(app, on = true))
        settingsPage(com.bamnat.app.ui.settings.SettingsPage.PRIVACY.title)
        reveal("앱 잠금", exact = true)
        assertTrue(appears(com.bamnat.app.ui.settings.APP_LOCK_RESTING_LINE))
        assertTrue(appears("잠금 설정", exact = true))
    }

    @Test fun restingClosedFoldLight() = shoot("rvl09_resting_closed_settings_fold_light_100", FOLDED, restingClosed())
    @Test fun restingClosedFoldDarkHuge() = shoot("rvl09_resting_closed_settings_fold_dark_200", FOLDED_NIGHT, fontScale = 2f, restingClosed())

    /** The phone gets its lock back: the closed mark is forgotten, so a later rest is said again. */
    @Test fun restingCardComesBackAfterTheLockReturns() {
        val keyguard = shadowOf(app.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager)
        keyguard.setIsDeviceSecure(false)
        assertTrue(com.bamnat.app.AppLock.restingCardShown(app, on = true))
        com.bamnat.app.AppLock.closeRestingCard(app)
        assertTrue(!com.bamnat.app.AppLock.restingCardShown(app, on = true))
        keyguard.setIsDeviceSecure(true)
        assertTrue(!com.bamnat.app.AppLock.restingCardShown(app, on = true))
        keyguard.setIsDeviceSecure(false)
        assertTrue(com.bamnat.app.AppLock.restingCardShown(app, on = true))
    }

    /** A 사장님 with a place, two staff and a link of their own saves without a password: the dialog says first what stays out. */
    private fun plainSave(): Screen.() -> Unit = {
        runBlocking {
            c.phaseThree.saveWorkplace(Workplace("w1", "가나다카페", fivePlus = false, payday = 10))
            c.phaseThree.saveStaff(StaffMember("s1", "박마바", base = 10_320), "w1")
            c.phaseThree.saveStaff(StaffMember("s2", "이사아", base = 10_320), "w1", sort = 1)
        }
        content { com.bamnat.app.ui.settings.ExportPasswordDialog(onDismiss = {}, onExport = {}) }
        settle()
        assertTrue(appears("암호를 걸어야 들어가요"))
        // 총괄 검토 #458 (조건 2): one filled button, the safer save; the other two are text buttons.
        assertTrue(appears("암호 걸고 저장"))
    }

    @Test fun plainSaveFoldLight() = shoot("rvl13_plain_save_fold_light_100", FOLDED, plainSave())
    @Test fun plainSaveFoldDark() = shoot("rvl13_plain_save_fold_dark_100", FOLDED_NIGHT, plainSave())
    @Test fun plainSaveFoldLightHuge() = shoot("rvl13_plain_save_fold_light_200", FOLDED, fontScale = 2f, plainSave())
    @Test fun plainSaveOpenDarkHuge() = shoot("rvl13_plain_save_open_dark_200", UNFOLDED_NIGHT, fontScale = 2f, plainSave())

    companion object {
        const val FOLDED = "w369dp-h905dp-port-420dpi"
        const val FOLDED_NIGHT = "w369dp-h905dp-port-night-420dpi"
        const val UNFOLDED = "w707dp-h823dp-port-420dpi"
        const val UNFOLDED_NIGHT = "w707dp-h823dp-port-night-420dpi"
    }
}
