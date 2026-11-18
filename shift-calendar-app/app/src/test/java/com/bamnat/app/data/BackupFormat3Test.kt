package com.bamnat.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.data.db.AppDatabase
import com.bamnat.core.money.MoneyEntry
import com.bamnat.core.money.MoneyKind
import com.bamnat.core.model.ShiftOverride
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate
import java.util.Base64
import java.util.zip.ZipInputStream

/**
 * [REL-15] 327: the backup file of format 3. Photos go in as files with a sum for every part, a changed or cut file
 * is turned away before anything changes, a password seals the whole zip, files of format 2 still open, and a
 * restore that fails halfway leaves the phone's data as it was.
 */
@RunWith(AndroidJUnit4::class)
class BackupFormat3Test {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    private val root = File(context.cacheDir, "format3-test")
    private val receipts = File(root, "receipts")
    private val contracts = File(root, "contracts")
    private val settings = SettingsRepository(context)
    private val day = LocalDate.of(2026, 10, 20)

    private fun manager(shareLinks: com.bamnat.app.share.ShareLinkBackup? = null) =
        BackupManager(db, settings, photos = BackupPhotos(receipts) { File(contracts, it) }, staging = File(root, "staging"), shareLinks = shareLinks)

    @Before fun setUp() = runBlocking<Unit> {
        root.deleteRecursively()
        receipts.mkdirs()
        File(receipts, "r1.jpg").writeBytes(ByteArray(3000) { (it % 251).toByte() })
        ScheduleRepository(db).ensureDefaults()
        MoneyRepository(db).save(MoneyEntry(date = day, kind = MoneyKind.EXPENSE, amount = 5_000, category = "식비", receipt = "r1.jpg"))
    }

    @After fun tearDown() {
        db.close()
        root.deleteRecursively()
    }

    private fun file(b: BackupManager, password: CharArray? = null): ByteArray =
        ByteArrayOutputStream().also { runBlocking { b.exportFile(it, password) } }.toByteArray()

    @Test fun aZipWithTheTextThePhotosAndTheirSums() {
        val bytes = file(manager())
        val names = ArrayList<String>()
        ZipInputStream(bytes.inputStream()).use { z -> while (true) names += (z.nextEntry ?: break).name }
        assertEquals(listOf(BackupArchive.JSON, "photos/receipts/r1.jpg", BackupArchive.SUMS), names)
        val json = manager().read(bytes.inputStream())
        assertFalse("the photo is not text inside the JSON any more", JSONObject(json).has("photos"))
    }

    @Test fun thePhotosComeBackAsTheyWere() = runBlocking {
        val b = manager()
        val bytes = file(b)
        val photo = File(receipts, "r1.jpg").readBytes()
        File(receipts, "r1.jpg").delete()
        val json = b.read(bytes.inputStream())
        assertEquals(1, b.summarize(json).photos)
        b.restore(json)
        assertTrue(File(receipts, "r1.jpg").readBytes().contentEquals(photo))
        assertFalse("nothing waits after the restore", File(root, "staging").exists())
    }

    /** [MPH-03] 1.38 메모 사진: a photo a memo (or the trash) points at goes in the file and comes back; one no memo names does not. */
    @Test fun memoPhotosGoAndComeBack() = runBlocking {
        val memos = File(root, "memo-photos").apply { mkdirs() }
        val name = "0123abcd-0000.jpg"
        File(memos, name).writeBytes(ByteArray(2000) { (it % 199).toByte() })
        File(memos, "9999ffff-0000.jpg").writeBytes(ByteArray(100))
        MemoRepository(db).save(com.bamnat.core.memo.Memo(title = "사진 메모", body = "위\n⟦사진 $name 50 왼쪽⟧\n아래"))
        val b = BackupManager(db, settings, photos = BackupPhotos(receipts, memos) { File(contracts, it) }, staging = File(root, "staging"))
        val bytes = ByteArrayOutputStream().also { b.exportFile(it, null) }.toByteArray()
        val names = ArrayList<String>()
        ZipInputStream(bytes.inputStream()).use { z -> while (true) names += (z.nextEntry ?: break).name }
        assertTrue(names.toString(), "photos/memos/$name" in names)
        assertFalse(names.toString(), names.any { it.contains("9999ffff") })
        val photo = File(memos, name).readBytes()
        File(memos, name).delete()
        val json = b.read(bytes.inputStream())
        b.restore(json)
        assertTrue(File(memos, name).readBytes().contentEquals(photo))
    }

    /** [ALRM-04] 증명 사진's fingerprints belong to this phone and this home: neither backup carries them. */
    @Test fun thePhotoMissionFingerprintsStayOnThePhone() = runBlocking {
        val refs = com.bamnat.app.alarm.PhotoReferences(context).apply { clear(); add(0x0123456789ABCDEFL, "욕실 거울") }
        settings.update { it.copy(alarmMission = "photo") }
        val json = manager().export()
        assertTrue("the choice goes", json.contains("\"alarmMission\": \"photo\""))
        assertFalse(json.contains("욕실 거울") || json.contains("123456789abcdef"))
        // Android's own backup and phone-to-phone copy take settings.xml alone, not photo_mission.xml.
        for (rules in listOf("backup_rules.xml", "data_extraction_rules.xml")) {
            val xml = File("src/main/res/xml/$rules").readText()
            assertFalse(rules, xml.contains(com.bamnat.app.alarm.PhotoReferences.FILE))
            assertFalse(rules, Regex("""domain="sharedpref" path="\."""").containsMatchIn(xml))
        }
        refs.clear()
    }

    /**
     * 2.0.0 [RVP-73] (검토 REST-15) 내 글꼴 (2~20MB each) stay out of Android's cloud backup: past its 25MB the schedule database does not
     * go up either. A phone-to-phone transfer still takes them.
     */
    @Test fun myFontsStayOutOfTheCloudBackup() {
        val cloud = File("src/main/res/xml/data_extraction_rules.xml").readText().substringAfter("<cloud-backup>").substringBefore("</cloud-backup>")
        val transfer = File("src/main/res/xml/data_extraction_rules.xml").readText().substringAfter("<device-transfer>").substringBefore("</device-transfer>")
        val old = File("src/main/res/xml/backup_rules.xml").readText()
        assertTrue("cloud", cloud.contains("""<exclude domain="file" path="fonts/" />"""))
        assertTrue("Android 11 and below", old.contains("""<exclude domain="file" path="fonts/" />"""))
        assertFalse("a new phone still gets them", transfer.contains("fonts/"))
    }

    @Test fun aChangedFileIsTurnedAway() {
        val bytes = file(manager())
        // One byte of the photo (stored as it is) changed: its sum no longer matches.
        val at = String(bytes, Charsets.ISO_8859_1).indexOf("photos/receipts/r1.jpg") + "photos/receipts/r1.jpg".length + 100
        bytes[at] = (bytes[at] + 1).toByte()
        try {
            manager().read(bytes.inputStream())
            fail("a changed file opened")
        } catch (e: BackupException) {
            assertEquals("백업 파일이 손상됐어요", e.message)
        }
    }

    @Test fun aCutFileIsTurnedAway() {
        val bytes = file(manager())
        try {
            manager().read(bytes.copyOf(bytes.size / 2).inputStream())
            fail("half a file opened")
        } catch (e: BackupException) {
            assertEquals("백업 파일이 손상됐어요", e.message)
        }
    }

    @Test fun aPasswordSealsTheWholeFile() = runBlocking {
        val b = manager()
        val bytes = file(b, "secret12".toCharArray())
        assertFalse("no photo name readable", String(bytes, Charsets.ISO_8859_1).contains("r1.jpg"))
        val locked = b.read(bytes.inputStream())
        assertTrue(b.isSealed(locked))
        try {
            b.unseal(locked, "wrong".toCharArray())
            fail("opened with a wrong password")
        } catch (e: BackupException) {
            assertEquals("암호가 맞지 않아요", e.message)
        }
        File(receipts, "r1.jpg").delete()
        val json = b.unseal(locked, "secret12".toCharArray())
        assertEquals(1, b.summarize(json).photos)
        b.restore(json)
        assertTrue(File(receipts, "r1.jpg").isFile)
    }

    /**
     * 2.0.0 [RVH-33] 검토 WORK-19 A kind of day's hours before a change '오늘부터만' go in the backup and come back, so last month's hours
     * stay after a restore; a kind with none says nothing of it (a backup read by an older app finds no new key).
     */
    @Test fun theHoursBeforeAChangeGoAndComeBack() = runBlocking {
        val repo = ScheduleRepository(db)
        val night = repo.shiftTypes.first().first { it.hasTimes }
        val changed = night.withTimesFrom(night.copy(end = night.end!!.plusHours(1)), day)
        repo.saveShiftType(changed)
        val b = manager()
        val json = b.read(file(b).inputStream())
        assertEquals(1, Regex("\"timeHistory\"").findAll(json).count())
        db.shiftTypeDao().getAll().forEach { db.shiftTypeDao().update(it.copy(timeHistory = "")) }
        b.restore(json)
        val back = repo.shiftTypes.first().first { it.name == night.name }
        assertEquals(night.end, back.on(day.minusDays(1)).end)
        assertEquals(night.end!!.plusHours(1), back.on(day).end)
    }

    /**
     * 2.0.0 [RVH-44][RVH-45] 결정 19번 (설계 4-4절): a kind of day of two parts and an hour's leave go in the backup and come back. An app
     * before 2.0.0 reads "break"·"breakStart" only, so they hold the break and the gap together from the gap's start (the same minutes
     * worked), and "leave" keeps the nearest part of a day, never a whole one.
     */
    @Test fun twoPartsAndAnHoursLeaveGoAndComeBack() = runBlocking {
        val repo = ScheduleRepository(db)
        val first = repo.shiftTypes.first().first { it.hasTimes }
        val shop = first.copy(start = java.time.LocalTime.of(11, 0), end = java.time.LocalTime.of(22, 0), breakMinutes = 30,
            breakStart = java.time.LocalTime.of(12, 0), gapStart = java.time.LocalTime.of(15, 0), gapMinutes = 120, gapAlarm = true)
        repo.saveShiftType(shop)
        val hours = com.bamnat.core.model.Leave.hours(com.bamnat.core.model.LeaveKind.ANNUAL, 120, fromStart = true)
        repo.updateDay(day) { it.copy(leave = hours, customGapStart = java.time.LocalTime.of(16, 0), customGapMinutes = 60) }
        val b = manager()
        val json = b.read(file(b).inputStream())
        val o = JSONObject(json)
        val t = o.getJSONArray("shiftTypes").let { a -> (0 until a.length()).map { a.getJSONObject(it) } }.first { it.optInt("gap") > 0 }
        assertEquals("옛 앱: 휴게 + 구간 사이", 150, t.getInt("break"))
        assertEquals("옛 앱: 구간 사이 시작부터", 15 * 60, t.getInt("breakStart"))
        assertEquals(30, t.getInt("rest")); assertEquals(12 * 60, t.getInt("restStart"))
        assertEquals(15 * 60, t.getInt("gapStart")); assertEquals(120, t.getInt("gap")); assertTrue(t.getBoolean("gapAlarm"))
        val d = o.getJSONArray("days").let { a -> (0 until a.length()).map { a.getJSONObject(it) } }.first { it.has("leaveMinutes") }
        assertEquals(120, d.getInt("leaveMinutes"))
        assertEquals("옛 앱: 반반차(오전), 하루가 아님", com.bamnat.core.model.Leave(quarters = 1, fromStart = true),
            com.bamnat.core.model.Leave.fromCode(d.getInt("leave")))
        // A kind of one part says nothing new (an older app's backup reads exactly as before).
        o.getJSONArray("shiftTypes").let { a -> (0 until a.length()).map { a.getJSONObject(it) } }.filter { it.optInt("gap") == 0 }
            .forEach { assertFalse(it.has("rest") || it.has("gapStart") || it.has("gapAlarm")) }

        repo.saveShiftType(first)
        repo.updateDay(day) { com.bamnat.core.model.DayEntry(day) }
        b.restore(json)
        assertEquals(shop.copy(timeHistory = emptyList()), repo.shiftTypes.first().first { it.id == shop.id }.copy(timeHistory = emptyList()))
        val back = repo.entryNow(day)!!
        assertEquals(hours, back.leave)
        assertEquals(java.time.LocalTime.of(16, 0), back.customGapStart)
        assertEquals(60, back.customGapMinutes)
    }

    /** 2.0.0 [RVH-44] A backup from before 2.0.0: its "break" is the break, never guessed to be a gap. */
    @Test fun anOldBackupsBreakStaysABreak() = runBlocking {
        val repo = ScheduleRepository(db)
        val b = manager()
        val o = JSONObject(b.read(file(b).inputStream()))
        val types = o.getJSONArray("shiftTypes")
        val t = (0 until types.length()).map { types.getJSONObject(it) }.first { it.has("start") && !it.optBoolean("off") }
        t.put("break", 120).put("breakStart", 15 * 60)
        b.restore(o.toString())
        val back = repo.shiftTypes.first().first { it.id == t.getLong("id") }
        assertEquals(120, back.breakMinutes)
        assertEquals(java.time.LocalTime.of(15, 0), back.breakStart)
        assertFalse(back.hasGap)
    }

    @Test fun aFileOfFormat2StillOpens() = runBlocking {
        val b = manager()
        val old = JSONObject(b.export()).put("photos", JSONObject().put("receipts", JSONObject().put("r1.jpg", Base64.getEncoder().encodeToString(byteArrayOf(7, 7)))))
            .toString(1)
        File(receipts, "r1.jpg").delete()
        val json = b.read(old.toByteArray().inputStream())
        assertEquals(old, json)
        assertEquals(1, b.summarize(json).photos)
        b.restore(json)
        assertTrue(File(receipts, "r1.jpg").readBytes().contentEquals(byteArrayOf(7, 7)))
    }

    @Test fun aRestoreThatFailsHalfwayLeavesThePhoneAsItWas() = runBlocking {
        var calls = 0
        // The third look at the calendars fails, as a full disk would: the first two are this test's export and the
        // copy the restore keeps before it changes anything; the third comes after the database was written.
        val links = com.bamnat.app.share.ShareLinkBackup({ if (++calls == 3) throw java.io.IOException("disk full") else emptyList() }, { error("no store") })
        val schedule = ScheduleRepository(db)
        val json = manager(links).export()
        schedule.setOverride(day, ShiftOverride.Off)
        try {
            manager(links).restore(json)
            fail("the restore went through")
        } catch (e: BackupException) {
            assertEquals("백업을 불러오지 못했어요. 지금 데이터는 그대로예요", e.message)
        }
        val now = schedule.snapshot.first()
        assertTrue("the change made after the backup is still here", now.resolver.resolve(day).isOff)
    }
}
