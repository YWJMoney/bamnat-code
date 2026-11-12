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
 * 옮겨 옴, 앱 잠금 쉼), '옮겨 온 것 확인' 창, 암호 없는 백업에서 빠지는 것을 먼저 말하는 저장 창. 밝은·어두운 × 글자 100%·200% × 폴드
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

    private fun movedDialog(): Screen.() -> Unit = {
        moved().invoke(this)
        tapText("새 휴대폰으로 옮겨 왔어요")
        settle()
        assertTrue(appears("그대로 왔어요"))
    }

    @Test fun movedDialogFoldLight() = shoot("rvl06_moved_dialog_fold_light_100", FOLDED, movedDialog())
    @Test fun movedDialogFoldDarkHuge() = shoot("rvl06_moved_dialog_fold_dark_200", FOLDED_NIGHT, fontScale = 2f, movedDialog())
    @Test fun movedDialogOpenLight() = shoot("rvl06_moved_dialog_open_light_100", UNFOLDED, movedDialog())

    /** 앱 잠금 on, but the phone has no lock of its own (Robolectric's keyguard is not secure). */
    private fun resting(): Screen.() -> Unit = {
        shadowOf(app.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).setIsDeviceSecure(false)
        c.settingsRepository.update { it.copy(appLock = true) }
        settle()
        assertTrue(appears("휴대폰 잠금이 없어 앱 잠금이 쉬고 있어요"))
    }

    @Test fun restingFoldLight() = shoot("rvl09_resting_fold_light_100", FOLDED, resting())
    @Test fun restingFoldDarkHuge() = shoot("rvl09_resting_fold_dark_200", FOLDED_NIGHT, fontScale = 2f, resting())

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
