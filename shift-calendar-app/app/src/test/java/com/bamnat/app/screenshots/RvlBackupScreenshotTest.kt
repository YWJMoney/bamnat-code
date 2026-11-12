package com.bamnat.app.screenshots

import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.backup.AutoBackup
import com.bamnat.app.backup.AutoBackupTest
import com.bamnat.app.data.BackupManager
import com.bamnat.app.tier.Feature
import com.bamnat.app.tier.Tier
import com.bamnat.app.ui.settings.MOVE_ROW
import com.bamnat.app.ui.settings.ONE_FILE
import com.bamnat.app.ui.settings.PICK_FOLDER
import com.bamnat.app.ui.settings.SAVE_ONCE
import com.bamnat.app.ui.settings.SEND_BACKUP
import com.bamnat.app.ui.setup.RESTORE_START_TITLE
import com.bamnat.core.memo.Memo
import com.bamnat.core.time.Today
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Duration

/**
 * 2.0.0 [RVL-49][RVL-50][RVL-51][RVL-52] 갈래 L 7차의 화면 (디자인 품질 조건): 매일 자동 백업을 폴더에 날짜별 7일치로(켜는 창의 채운 '폴더 고르기',
 * 위치 바꾸기, 설정 줄의 '폴더에 N일치'), 프로가 끝났을 때의 줄과 '지금 한 번 저장', 불러오기 전 '며칠 전 파일·줄어드는 것' 먼저, 새 휴대폰으로
 * 옮기기의 '만들고 보내기'와 새 폰의 '이 백업으로 시작할까요?'. 밝은·어두운 × 글자 100%·200% × 폴드 접은 폭·편 폭.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RvlBackupScreenshotTest : ScreenshotBase() {
    private val c get() = app.container
    private val password = "밤낮1234"

    @After fun off() = runBlocking {
        runCatching { c.autoBackup.turnOff() }
        c.settingsRepository.update { it.copy(previewTier = null) }
        Unit
    }

    private fun Screen.waitFor(what: String, done: () -> Boolean) {
        val until = System.currentTimeMillis() + 20_000
        while (!done()) {
            check(System.currentTimeMillis() < until) { "Timed out waiting for $what" }
            Thread.sleep(20)
            settle()
        }
        settle()
    }

    private fun folder() = File(app.filesDir, "drive/밤낮 백업").apply { deleteRecursively(); mkdirs() }

    /** 자동 백업 on in a folder (or one file), with its first backup made. */
    private fun on(inFolder: Boolean) {
        c.keyBox = AutoBackupTest.FakeKeyBox()
        val where = if (inFolder) Uri.fromFile(folder())
        else Uri.fromFile(File(app.filesDir, "drive/${AutoBackup.FILE_NAME}").apply { parentFile!!.mkdirs(); delete() })
        val r = runBlocking { c.autoBackup.turnOn(where, password.toCharArray(), folder = inFolder) }
        assertTrue("$r", r is AutoBackup.Result.Ok)
    }

    // ---- RVL-52: a folder, seven days kept ----
    private fun folderRow(): Screen.() -> Unit = {
        on(inFolder = true)
        settingsPage("데이터")
        reveal("위치 바꾸기", exact = true)
        assertTrue(appears("밤낮 백업 폴더에 1일치"))
    }

    @Test fun folderRowFoldLight() = shoot("rvl52_row_fold_light_100", FOLDED, folderRow())
    @Test fun folderRowFoldDarkHuge() = shoot("rvl52_row_fold_dark_200", FOLDED_NIGHT, fontScale = 2f, folderRow())
    @Test fun folderRowOpenLight() = shoot("rvl52_row_open_light_100", UNFOLDED, folderRow())

    /** Turning it on: the password, then the filled 폴더 고르기 or 파일 하나로 for Google Drive. */
    private fun asking(): Screen.() -> Unit = {
        settingsPage("데이터")
        reveal(com.bamnat.app.ui.settings.AUTO_BACKUP_ROW, exact = true)
        tapText(com.bamnat.app.ui.settings.AUTO_BACKUP_ROW, exact = true)
        assertTrue(appears("자동 백업 암호"))
        assertTrue(appears(PICK_FOLDER, exact = true))
        assertTrue(appears(ONE_FILE, exact = true))
    }

    @Test fun askingFoldLight() = shoot("rvl52_password_fold_light_100", FOLDED, asking())
    @Test fun askingFoldDarkHuge() = shoot("rvl52_password_fold_dark_200", FOLDED_NIGHT, fontScale = 2f, asking())
    @Test fun askingOpenLight() = shoot("rvl52_password_open_light_100", UNFOLDED, asking())

    private fun where(): Screen.() -> Unit = {
        on(inFolder = false)
        settingsPage("데이터")
        reveal("위치 바꾸기", exact = true)
        tapText("위치 바꾸기", exact = true)
        assertTrue(appears("어디에 저장할까요?"))
        assertTrue(appears(PICK_FOLDER, exact = true))
    }

    @Test fun whereFoldLight() = shoot("rvl52_where_fold_light_100", FOLDED, where())
    @Test fun whereFoldDarkHuge() = shoot("rvl52_where_fold_dark_200", FOLDED_NIGHT, fontScale = 2f, where())

    // ---- RVL-51: 프로 ended ----
    private fun stopped(): Screen.() -> Unit = {
        on(inFolder = true)
        runBlocking {
            c.settingsRepository.update { it.copy(previewTier = Tier.FREE) }
            c.featureGate.state.first { it.locked(Feature.AUTO_BACKUP) }
        }
        settingsPage("데이터")
        reveal(SAVE_ONCE, exact = true)
        assertTrue(appears("매일 저장은 쉬고 있어요"))
    }

    @Test fun stoppedFoldLight() = shoot("rvl51_stopped_fold_light_100", FOLDED, stopped())
    @Test fun stoppedFoldDarkHuge() = shoot("rvl51_stopped_fold_dark_200", FOLDED_NIGHT, fontScale = 2f, stopped())
    @Test fun stoppedOpenLight() = shoot("rvl51_stopped_open_light_100", UNFOLDED, stopped())

    @Test fun savingOnceWhileStopped() = shoot("rvl51_saved_once_fold_light_100", FOLDED) {
        stopped().invoke(this)
        val before = c.autoBackup.state.value.lastOk!!
        tapText(SAVE_ONCE, exact = true)
        waitFor("the save") { c.autoBackup.state.value.lastOk?.isAfter(before) == true || c.autoBackup.state.value.lastOk != before }
        assertTrue(appears("매일 저장은 프로를 다시 쓰면 이어져요"))
    }

    // ---- RVL-50: what a restore changes, first ----
    /** A file saved 12 days ago with fewer memos and money lines than the app has now, opened from a messenger. */
    private fun olderFile(): Uri {
        val json = runBlocking { JSONObject(c.backupManager.export()) }
        json.put("exportedAt", Today.instant().minus(Duration.ofDays(12)).toEpochMilli())
        runBlocking {
            repeat(8) { i -> c.memoRepository.save(Memo(date = Today.date().minusDays(i.toLong()), title = "메모 ${i + 1}", body = "")) }
        }
        return Uri.fromFile(File(app.cacheDir, "older.${BackupManager.EXTENSION}").apply { writeText(json.toString()) })
    }

    private fun restoring(): Screen.() -> Unit = {
        val file = olderFile()
        settingsPage("데이터")
        com.bamnat.app.IncomingFiles.read(Intent(Intent.ACTION_VIEW, file))
        settle()
        assertTrue(appears("백업을 불러올까요?"))
        assertTrue(appears("이 파일은 12일 전 것이에요"))
        assertTrue(appears("메모 8개가 줄어요"))
    }

    @Test fun restoringFoldLight() = shoot("rvl50_restore_fold_light_100", FOLDED, restoring())
    @Test fun restoringFoldDarkHuge() = shoot("rvl50_restore_fold_dark_200", FOLDED_NIGHT, fontScale = 2f, restoring())
    @Test fun restoringOpenLight() = shoot("rvl50_restore_open_light_100", UNFOLDED, restoring())

    // ---- RVL-49: to a new phone in one go ----
    private fun moving(): Screen.() -> Unit = {
        settingsPage("데이터")
        reveal(MOVE_ROW, exact = true)
        tapText(MOVE_ROW, exact = true)
        assertTrue(appears(SEND_BACKUP, exact = true))
        assertTrue(appears("파일로 저장", exact = true))
    }

    @Test fun movingFoldLight() = shoot("rvl49_move_fold_light_100", FOLDED, moving())
    @Test fun movingFoldDarkHuge() = shoot("rvl49_move_fold_dark_200", FOLDED_NIGHT, fontScale = 2f, moving())
    @Test fun movingOpenLight() = shoot("rvl49_move_open_light_100", UNFOLDED, moving())

    @Test fun sendingOpensAndroidsSendWithTheCheckedFile() = shoot("rvl49_send_password_fold_light_100", FOLDED) {
        moving().invoke(this)
        tapText(SEND_BACKUP, exact = true)
        assertTrue(appears("암호 걸고 보내기", exact = true))
        tapText("암호 없이", exact = true)
        val shadow = shadowOf(app)
        var started: Intent? = null
        waitFor("보내기") { started = shadow.nextStartedActivity; started != null }
        val chooser = started!!
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        @Suppress("DEPRECATION") val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals(Intent.ACTION_SEND, send.action)
        assertEquals(BackupManager.FILE_MIME, send.type)
        @Suppress("DEPRECATION") assertNotNull(send.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
        assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        // The file that goes opens as a backup.
        val made = File(app.cacheDir, "share").listFiles().orEmpty().single { it.extension == BackupManager.EXTENSION }
        assertTrue(made.name.startsWith("밤낮-백업-"))
        assertTrue(made.inputStream().use { c.backupManager.read(it) }.isNotEmpty())
    }

    /** The new phone: the file it got, opened before any setup, starts the app in one tap. */
    private fun starting(): Screen.() -> Unit = {
        val json = runBlocking { JSONObject(c.backupManager.export()) }
        json.put("exportedAt", Today.instant().minus(Duration.ofDays(1)).toEpochMilli())
        val file = Uri.fromFile(File(app.cacheDir, "got.${BackupManager.EXTENSION}").apply { writeText(json.toString()) })
        val vm = com.bamnat.app.ui.setup.SetupViewModel(c)
        content { com.bamnat.app.ui.setup.SetupHost(vm) }
        settle()
        vm.readFile(app, file)
        waitFor("the file") { vm.pendingRestore.value != null }
        assertTrue(appears(RESTORE_START_TITLE))
        assertTrue(appears("어제 저장한 파일이에요"))
        assertTrue(appears("이 백업으로 시작", exact = true))
    }

    @Test fun startingFoldLight() = shoot("rvl49_start_fold_light_100", FOLDED, starting())
    @Test fun startingFoldDarkHuge() = shoot("rvl49_start_fold_dark_200", FOLDED_NIGHT, fontScale = 2f, starting())
    @Test fun startingOpenLight() = shoot("rvl49_start_open_light_100", UNFOLDED, starting())

    companion object {
        const val FOLDED_NIGHT = "w369dp-h905dp-port-night-420dpi"
    }
}
