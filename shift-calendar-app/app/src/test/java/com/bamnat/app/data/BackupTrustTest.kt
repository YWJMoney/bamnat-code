package com.bamnat.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.data.db.AppDatabase
import com.bamnat.app.data.db.SleepEventEntity
import com.bamnat.app.share.ShareLinkBackup
import com.bamnat.app.share.ShareLinkState
import com.bamnat.app.share.ShareLinkStore
import com.bamnat.app.tier.ProStore
import com.bamnat.core.boss.StaffMember
import com.bamnat.core.boss.Workplace
import com.bamnat.core.time.Today
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * 2.0.0 갈래 L, 검토 12장의 믿음 줄: 되돌리기가 사업장·직원·링크 열쇠까지 돌려놓고(DATA-01), 중간에 멈춘 불러오기는 다음에 열 때 전으로
 * 돌아가며(DATA-08), 암호 없는 파일에는 열쇠가 없고(DATA-04), 같은 휴대폰 되살리기는 밤 소리 클립을 다시 잇고(DATA-05), 구글 백업·휴대폰
 * 옮기기는 반쪽이 아니에요(DATA-02·DATA-10).
 */
@RunWith(AndroidJUnit4::class)
class BackupTrustTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    private val store = PhaseThreeStore(db) { 1L }
    private val dir = File(context.cacheDir, "backup-trust").apply { deleteRecursively(); mkdirs() }

    @After fun close() {
        db.close()
        dir.deleteRecursively()
    }

    private suspend fun ledger() {
        ScheduleRepository(db).ensureDefaults()
        store.saveWorkplace(Workplace("w1", "가나다카페", fivePlus = false, payday = 10))
        store.saveStaff(StaffMember("s1", "박마바", base = 10_320, clockId = "C1", clockToken = "ctok"), "w1")
    }

    private fun link(): Pair<ShareLinkStore, ShareLinkBackup> {
        val links = ShareLinkStore(context, PersonalCalendar.MAIN)
        links.save(ShareLinkState("AbCdEfGh12345678", "https://share.example/s/AbCdEfGh12345678", "dtok-secret", Today.instant().plusSeconds(86_400), "내 근무"))
        return links to ShareLinkBackup({ listOf(PersonalCalendar.MAIN) }, { k -> if (k == PersonalCalendar.MAIN) links else ShareLinkStore(context, k) })
    }

    /** DATA-01·T12-01: an import replaced the 대장 and made a new place; taking it back brings the old one whole and takes the new one away. */
    @Test fun theWholeCopyBringsTheLedgerBackAndTakesANewOneAway() = runBlocking {
        ledger()
        val backups = BackupManager(db, SettingsRepository(context))
        val before = backups.exportFull()
        store.deleteStaff("s1")
        store.saveWorkplace(Workplace("w2", "새 가게", fivePlus = true, payday = 25))
        backups.restore(before)
        assertEquals(listOf("w1"), store.workplaces.first().map { it.id })
        assertEquals(listOf("박마바"), store.staff("w1").first().map { it.name })
        assertEquals("출퇴근 링크 열쇠도 그대로", "ctok", store.staff("w1").first().single().clockToken)
    }

    /** DATA-01·T12-03: the copy keeps the keys of the links the user made, so a link made after a plain backup can still be changed. */
    @Test fun theWholeCopyKeepsTheLinkKeys() = runBlocking {
        ScheduleRepository(db).ensureDefaults()
        val (links, share) = link()
        val backups = BackupManager(db, SettingsRepository(context), shareLinks = share)
        val before = JSONObject(backups.exportFull())
        assertEquals("dtok-secret", before.getJSONArray("shareLinks").getJSONObject(0).getString("token"))
        links.clear()
        backups.restore(before.toString())
        assertEquals("dtok-secret", links.link.value?.deleteToken)
    }

    /** DATA-04: a file without a password has no key that changes a link and no 프로 recovery code; a sealed one has both. */
    @Test fun aPlainFileHasNoKeys() = runBlocking {
        val (_, share) = link()
        val pro = ProStore(context).apply { setCode("ABCD-EFGH-JKLM-NPQR") }
        val backups = BackupManager(db, SettingsRepository(context), shareLinks = share, pro = { pro })
        ScheduleRepository(db).ensureDefaults()
        val plain = backups.export()
        assertFalse(plain.contains("dtok-secret"))
        assertFalse(plain.contains("ABCD-EFGH-JKLM-NPQR"))
        val sealed = backups.exportSealed()
        assertTrue(sealed.contains("dtok-secret"))
        assertTrue(sealed.contains("ABCD-EFGH-JKLM-NPQR"))
        // The phone's own code stays when a plain file is put in.
        backups.restore(plain)
        assertEquals("ABCD-EFGH-JKLM-NPQR", pro.current.code)
        val gaps = backups.plainLeavesOut()
        assertEquals(1, gaps.links)
        assertTrue(gaps.proCode)
    }

    /** DATA-13·U12-01: the save dialog's words for what a plain file leaves out, in the user's own words. */
    @Test fun theSaveDialogSaysWhatAPlainFileLeavesOut() {
        assertTrue(PlainLeavesOut.NONE.isEmpty)
        val boss = PlainLeavesOut(workplaces = 1, staff = 3, links = 0, proCode = false)
        assertEquals("직원 3명과 급여 대장은 암호를 걸어야 들어가요", boss.line)
        val all = PlainLeavesOut(workplaces = 1, staff = 0, links = 2, proCode = true)
        assertEquals("사업장과 급여 대장, 공유 링크 2개를 고치는 열쇠, 프로 복구 코드는 암호를 걸어야 들어가요", all.line)
        assertEquals("공유 링크 1개를 고치는 열쇠는 암호를 걸어야 들어가요", PlainLeavesOut(0, 0, 1, false).line)
    }

    /** DATA-08: the app closed in the middle of a restore; the next start puts the data back as it was, once, and never loops. */
    @Test fun aRestoreCutShortIsPutBackAtTheNextStart() = runBlocking {
        ledger()
        val guard = File(dir, "guard")
        val backups = BackupManager(db, SettingsRepository(context), guard = guard)
        assertFalse("나중에 열 것이 없으면 아무 일도 없어요", backups.finishInterrupted())
        // What the restore keeps first, then the half-written state it stopped in.
        guard.mkdirs()
        File(guard, "before_restore.json").writeText(backups.exportFull())
        store.deleteStaff("s1")
        assertTrue(backups.finishInterrupted())
        assertEquals(listOf("박마바"), store.staff("w1").first().map { it.name })
        assertFalse(File(guard, "before_restore.json").exists())
        assertFalse(File(guard, "before_restore.json.tried").exists())
        // A start that stopped while putting it back gives up rather than stopping again.
        File(guard, "before_restore.json.tried").writeText("{}")
        assertFalse(backups.finishInterrupted())
        assertFalse(File(guard, "before_restore.json.tried").exists())
    }

    /** DATA-08: a restore that finishes leaves no copy behind. */
    @Test fun aRestoreThatFinishesLeavesNoGuard() = runBlocking {
        ledger()
        val guard = File(dir, "guard2")
        val backups = BackupManager(db, SettingsRepository(context), guard = guard)
        backups.restore(backups.export())
        assertFalse(File(guard, "before_restore.json").exists())
    }

    /** DATA-05·U12-10: on the same phone the night's sounds join their clips again (보관 too); a clip not on this phone is not pointed at. */
    @Test fun clipsJoinAgainOnTheSamePhone() = runBlocking {
        ScheduleRepository(db).ensureDefaults()
        val clips = File(dir, "sleep_clips").apply { mkdirs() }
        File(clips, "c1.m4a").writeText("x")
        db.sleepDao().insertEvent(SleepEventEntity(startMillis = 1_000, endMillis = 2_000, kind = "SNORE", level = 40f, file = "c1.m4a", keep = true))
        db.sleepDao().insertEvent(SleepEventEntity(startMillis = 3_000, endMillis = 4_000, kind = "TALK", level = 30f, file = "gone.m4a"))
        db.sleepDao().insertEvent(SleepEventEntity(startMillis = 5_000, endMillis = 6_000, kind = "SNORE", level = 30f, file = "../escape.m4a"))
        val backups = BackupManager(db, SettingsRepository(context), sleepClips = clips)
        val plain = backups.export()
        db.sleepDao().clearEvents()
        backups.restore(plain)
        val back = db.sleepDao().allEvents().sortedBy { it.startMillis }
        assertEquals(listOf("c1.m4a", null, null), back.map { it.file })
        assertEquals(listOf(true, false, false), back.map { it.keep })
    }

    /** DATA-02·DATA-10·U12-03: the carry is taken in once on a phone the data came to, and never on the phone it was made on. */
    @Test fun aPhoneTheDataCameToTakesTheCarryOnce() {
        val carry = File(dir, "files/carry/settings.json")
        val keys = File(dir, "root/carry_keys/keys.json")
        var taken: JSONObject? = null
        fun move(here: File, state: File) = PhoneMove(carry, keys, here, state,
            out = { JSONObject().put("teams", JSONObject().put("main", "A,B")) },
            outKeys = { JSONObject().put("pro", JSONObject().put("code", "ABCD-EFGH-JKLM-NPQR")) },
            received = { JSONArray().put(JSONObject().put("id", "LeaderLink12345678").put("title", "3병동").put("mine", true).put("follow", "AUTO")) },
            take = { taken = it; 2 })
        // The old phone: nothing came, so nothing is taken; then the carry is written.
        val old = move(File(dir, "old/here"), File(dir, "old/state.json"))
        assertNull(old.arrive())
        old.keep()
        assertTrue(carry.exists() && keys.exists())
        assertNull(taken)
        // The new phone: the no-backup mark did not come.
        val new = move(File(dir, "new/here"), File(dir, "new/state.json"))
        val moved = new.arrive()!!
        assertEquals("A,B", taken!!.getJSONObject("teams").getString("main"))
        assertEquals("열쇠는 따로 온 것이 이겨요", "ABCD-EFGH-JKLM-NPQR", taken!!.getJSONObject("pro").getString("code"))
        assertFalse("받은 링크는 설정으로 넣지 않고 기다려요", taken!!.has(PhoneMove.RECEIVED))
        assertEquals(2, moved.links)
        assertEquals(listOf("LeaderLink12345678"), moved.waiting.map { it.id })
        assertTrue(moved.waiting.single().mine)
        assertEquals("AUTO", moved.waiting.single().follow)
        assertTrue(moved.keys)
        // Once only, and the card's state lasts until closed.
        taken = null
        assertNull(new.arrive())
        assertNull(taken)
        val again = move(File(dir, "new/here"), File(dir, "new/state.json"))
        assertEquals(moved.waiting, again.moved.value?.waiting)
        again.joined(listOf("LeaderLink12345678"))
        assertEquals(emptyList<PhoneMove.Waiting>(), again.moved.value?.waiting)
        again.close()
        assertNull(again.moved.value)
    }

    /** DATA-02: before Android 9 the keys are never written (Android cannot be asked to take them only sealed there). */
    @Test fun withoutSealingTheKeysStayOnThePhone() {
        val keys = File(dir, "k/carry_keys/keys.json")
        val move = PhoneMove(File(dir, "k/carry.json"), keys, File(dir, "k/here"), File(dir, "k/state.json"),
            out = { JSONObject() }, outKeys = { JSONObject().put("pro", JSONObject().put("code", "X")) }, received = { JSONArray() },
            take = { 0 }, sealedKeys = false)
        move.arrive()
        move.keep()
        assertFalse(keys.exists())
    }

    /** U12-03: a cloud backup without the sealed part says what it held (only counts) — and says nothing when it held nothing. */
    @Test fun theCardSaysWhatTheSealedPartHeldOnlyWhenItHeldSomething() {
        fun moveOf(d: String, outKeys: () -> JSONObject, received: () -> JSONArray) = PhoneMove(File(dir, "$d/carry.json"), File(dir, "$d/keys.json"),
            File(dir, "$d/here"), File(dir, "$d/state.json"), out = { JSONObject() }, outKeys = outKeys, received = received, take = { 0 })
        // A follower with a code: the old phone writes, the cloud leaves the keys out, the new phone says both.
        val follower = { JSONArray().put(JSONObject().put("id", "LeaderLink12345678").put("title", "3병동")) }
        moveOf("a", { JSONObject().put("pro", JSONObject().put("code", "X")) }, follower).apply { arrive(); keep() }
        assertFalse(File(dir, "a/carry.json").readText().contains("LeaderLink12345678"))
        File(dir, "a/keys.json").delete()
        File(dir, "a/here").delete()
        val lost = moveOf("a", { JSONObject() }, { JSONArray() }).arrive()!!
        assertFalse(lost.keys)
        assertEquals(1, lost.lostReceived)
        assertEquals("받은 근무표 1개는 보낸 분께 링크를 다시 받아 주세요",
            com.bamnat.app.ui.settings.movedLine(lost, notify = true, exact = true))
        // A 가게 with no link, no code and nothing followed: nothing was lost, and the card does not say so.
        moveOf("b", { JSONObject() }, { JSONArray() }).apply { arrive(); keep() }
        File(dir, "b/keys.json").delete()
        File(dir, "b/here").delete()
        val shop = moveOf("b", { JSONObject() }, { JSONArray() }).arrive()!!
        assertTrue(shop.keys)
        assertEquals(0, shop.lostReceived)
        assertEquals(0, shop.parts)
        assertEquals("설정도 그대로 옮겼어요", com.bamnat.app.ui.settings.movedLine(shop, notify = true, exact = true, ringing = false))
    }

    /** DATA-02: what the carry holds is every preference file the backup carries apart from settings.xml (which Android takes itself). */
    @Test fun theCarryHoldsWhatAndroidLeavesOut() = runBlocking {
        val (_, share) = link()
        val pro = ProStore(context).apply { setCode("ABCD-EFGH-JKLM-NPQR") }
        val backups = BackupManager(db, SettingsRepository(context), guide = GuideStore.get(context), teams = TeamStore(context),
            dutyCodes = { DutyCodeBook(context) }, shareLinks = share, pro = { pro },
            settingsFiles = { SettingsFilesBackup(context, { listOf(PersonalCalendar.MAIN) }) {} })
        val out = backups.carryOut()
        val keys = backups.carryKeys()
        val kept = mapOf("guide" to "featuresOff", "team_rosters" to "teams", "duty_codes" to "dutyCodes", "pro" to "pro", "share_link" to "shareLinks")
        for (file in SettingsFilesBackup.ELSEWHERE - "settings") {
            val key = kept[file] ?: error("$file: 옮기기에 들어갈 자리가 없어요")
            assertTrue("$file → $key", out.has(key) || keys.has(key))
        }
        assertTrue("PARTS는 settingsFiles로", out.has("settingsFiles"))
        assertFalse("열쇠는 설정 쪽에 없어요", out.toString().contains("dtok-secret") || out.toString().contains("ABCD-EFGH-JKLM-NPQR"))
        assertTrue(keys.toString().contains("dtok-secret"))
        assertTrue(keys.toString().contains("ABCD-EFGH-JKLM-NPQR"))
    }

    /** DATA-02 (3): Android's rules take the carry with the files, and the keys only sealed (cloud) or by a phone-to-phone move. */
    @Test fun theBackupRulesTakeTheCarryAndSealTheKeys() {
        val res = listOf("src/main/res/xml", "app/src/main/res/xml").map(::File).first { it.isDirectory }
        val cloud = File(res, "data_extraction_rules.xml").readText()
        val cloudPart = cloud.substringAfter("<cloud-backup").substringBefore("</cloud-backup>")
        val movePart = cloud.substringAfter("<device-transfer").substringBefore("</device-transfer>")
        assertTrue(Regex("""<include domain="root" path="carry_keys/" requireFlags="clientSideEncryption" />""").containsMatchIn(cloudPart))
        assertTrue(movePart.contains("""<include domain="root" path="carry_keys/" />"""))
        val old = File(res, "backup_rules.xml").readText()
        assertTrue(Regex("""<include domain="root" path="carry_keys/" requireFlags="clientSideEncryption" />""").containsMatchIn(old))
        for (rules in listOf(cloudPart, movePart, old)) {
            assertTrue("files 전체가 가요", rules.contains("""<include domain="file" path="." />"""))
            assertFalse("carry/는 빼지 않아요", Regex("""<exclude[^>]*path="carry""").containsMatchIn(rules))
            assertEquals("설정 파일은 settings.xml 하나만", listOf("settings.xml"),
                Regex("""domain="sharedpref" path="([^"]+)"""").findAll(rules).map { it.groupValues[1] }.toList())
        }
        assertTrue(PhoneMove.CARRY.startsWith("carry/"))
        assertTrue(PhoneMove.KEYS.startsWith("carry_keys/"))
    }

    /** U12-03 (조건 4·13): the moved card's second line, two things at most, the first what its button does; no alarm talk without an alarm. */
    @Test fun theMovedLineSaysWhatToDoFirst() {
        val at = java.time.Instant.parse("2026-11-12T00:00:00Z")
        val leader = PhoneMove.Waiting("AbCdEfGh12345678", "3병동 김간호", mine = true, adopted = true, follow = null)
        val m = PhoneMove.Moved(at, links = 1, waiting = listOf(leader), keys = true, parts = 5)
        assertEquals("3병동 김간호 근무표를 다시 이어 주세요", com.bamnat.app.ui.settings.movedLine(m, notify = true, exact = true))
        assertEquals(com.bamnat.app.ui.settings.MovedTodo.RELINK, com.bamnat.app.ui.settings.movedTodo(m, notify = true, exact = true))
        val shop = PhoneMove.Moved(at, links = 0, waiting = emptyList(), keys = false, parts = 5)
        val line = com.bamnat.app.ui.settings.movedLine(shop, notify = true, exact = true, ringing = false)
        assertFalse(line.contains("알람"))
        assertEquals("공유 링크 열쇠는 옛 휴대폰 백업 파일로 옮겨요", line)
        assertEquals(com.bamnat.app.ui.settings.MovedTodo.NONE, com.bamnat.app.ui.settings.movedTodo(shop, notify = true, exact = true))
        assertEquals("알림을 다시 허용해 주세요 · 알람은 다시 걸었어요",
            com.bamnat.app.ui.settings.movedLine(shop.copy(keys = true), notify = false, exact = true))
    }

    /** 조건 13: the '옮겨 온 것 확인' window names what came in the words of each kind of work. */
    @Test fun theMovedWindowSpeaksEachKindOfWork() {
        for (style in listOf(com.bamnat.core.work.WorkStyle.BUSINESS, com.bamnat.core.work.WorkStyle.INVEST, com.bamnat.core.work.WorkStyle.FIELD)) {
            val words = com.bamnat.app.ui.settings.movedSettings(style) + com.bamnat.app.ui.settings.movedRecords(style)
            assertFalse("$style: $words", listOf("조", "근무 약어", "입사일", "희망 오프").any { it in words })
        }
        assertTrue("모든 조" in com.bamnat.app.ui.settings.movedSettings(com.bamnat.core.work.WorkStyle.SHIFT))
        assertEquals("매매·메모 기록과 달력", com.bamnat.app.ui.settings.movedRecords(com.bamnat.core.work.WorkStyle.INVEST))
    }
}
