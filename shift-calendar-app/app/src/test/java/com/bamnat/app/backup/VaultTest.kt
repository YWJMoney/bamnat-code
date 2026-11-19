package com.bamnat.app.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.data.BackupManager
import com.bamnat.app.data.ImportUndo
import com.bamnat.app.data.PersonalCalendar
import com.bamnat.app.data.PhoneMove
import com.bamnat.app.data.ScheduleRepository
import com.bamnat.app.data.SettingsFilesBackup
import com.bamnat.app.data.SettingsRepository
import com.bamnat.app.data.db.AppDatabase
import com.bamnat.app.share.ShareGroupStore
import com.bamnat.app.share.ShareLinkBackup
import com.bamnat.app.share.ShareLinkState
import com.bamnat.app.share.ShareLinkStore
import com.bamnat.app.tier.ProStore
import com.bamnat.core.time.Today
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * 2.0.0 [RVL-77] (검토 I12b-03) 링크 열쇠·복구 코드 금고: the keys are never plain words in the phone's files, a plain backup still leaves
 * them out (now the 우리 조 code too), and a phone whose key went gets them back from the copy Android's backup carries.
 */
@RunWith(AndroidJUnit4::class)
class VaultTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    private val box = GenBox()
    private val dir = File(context.cacheDir, "vault-test").apply { deleteRecursively(); mkdirs() }

    /** A key store whose key can be lost: what one key sealed the next cannot open. */
    class GenBox : KeyBox {
        var key = 1
        override fun wrap(secret: ByteArray) = byteArrayOf(key.toByte()) + secret.reversedArray()
        override fun unwrap(blob: ByteArray): ByteArray =
            if (blob.isEmpty() || blob[0].toInt() != key) throw KeyBox.KeyGone() else blob.copyOfRange(1, blob.size).reversedArray()
        override fun forget() { key++ }
        fun lose() { key++ }
    }

    @Before fun setUp() {
        Vault.box = box
        listOf("share_link", "pro", "team_board_links", "share_group", Vault.PREFS).forEach {
            context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    @After fun close() {
        db.close()
        dir.deleteRecursively()
        Vault.box = KeystoreKeyBox("bamnat_key_vault")
    }

    private fun raw(prefs: String): String = context.getSharedPreferences(prefs, Context.MODE_PRIVATE).all.values.joinToString(" ")

    private fun link(): ShareLinkStore = ShareLinkStore(context, PersonalCalendar.MAIN).apply {
        save(ShareLinkState("AbCdEfGh12345678", "https://share.example/s/AbCdEfGh12345678", "dtok-secret", Today.instant().plusSeconds(86_400), "내 근무"))
    }

    @Test fun theKeysAreNeverPlainWordsInThePhonesFiles() {
        val links = link()
        val pro = ProStore(context).apply { setCode("ABCD-EFGH-JKLM-NPQR") }
        ShareGroupStore(context).save("QWERTY", null)
        assertFalse(raw("share_link").contains("dtok-secret"))
        assertFalse(raw("pro").contains("ABCD-EFGH"))
        assertFalse(raw("share_group").contains("QWERTY"))
        assertTrue(raw("share_link").contains(Vault.MARK))
        // Read back as they were, by a store made afresh (the app opened again).
        assertEquals("dtok-secret", ShareLinkStore(context, PersonalCalendar.MAIN).load()?.deleteToken)
        assertEquals("ABCD-EFGH-JKLM-NPQR", ProStore(context).current.code)
        assertEquals("QWERTY", ShareGroupStore(context).code.value)
        assertEquals("dtok-secret", links.load()?.deleteToken)
        assertEquals("ABCD-EFGH-JKLM-NPQR", pro.current.code)
    }

    @Test fun aKeyWrittenPlainBeforeIsReadAndSealedInItsPlace() {
        context.getSharedPreferences("share_group", Context.MODE_PRIVATE).edit().putString("code", "OLDONE").commit()
        assertEquals("OLDONE", ShareGroupStore(context).code.value)
        assertFalse(raw("share_group").contains("OLDONE"))
        assertEquals("OLDONE", ShareGroupStore(context).code.value)
    }

    @Test fun aPhoneThatCannotSealKeepsTheKeyAsBefore() {
        Vault.box = object : KeyBox {
            override fun wrap(secret: ByteArray): ByteArray = throw java.security.KeyStoreException("no key store")
            override fun unwrap(blob: ByteArray): ByteArray = throw KeyBox.KeyGone()
            override fun forget() {}
        }
        link()
        assertEquals("dtok-secret", ShareLinkStore(context, PersonalCalendar.MAIN).load()?.deleteToken)
        assertTrue(Vault.check(context.getSharedPreferences(Vault.PREFS, Context.MODE_PRIVATE)))
    }

    /** The copies a restore and an import keep hold every key: sealed too, and a plain one of before still reads. */
    @Test fun theFullCopiesAreSealed() {
        val f = File(dir, "before_import.json")
        Vault.writeFile(f, """{"shareLinks":[{"token":"dtok-secret"}]}""")
        assertFalse(String(f.readBytes(), Charsets.ISO_8859_1).contains("dtok-secret"))
        assertEquals("""{"shareLinks":[{"token":"dtok-secret"}]}""", Vault.readFile(f))
        f.writeText("""{"old":true}""")
        assertEquals("""{"old":true}""", Vault.readFile(f))
        Vault.writeFile(f, "x")
        box.lose()
        assertNull(Vault.readFile(f))
    }

    @Test fun anImportCanStillBeTakenBack() = runBlocking {
        ScheduleRepository(db).ensureDefaults()
        val backups = BackupManager(db, SettingsRepository(context))
        val undo = ImportUndo(File(dir, "before_import.json"), backups)
        undo.save()
        assertFalse(File(dir, "before_import.json").readText(Charsets.ISO_8859_1).contains("\"calendars\""))
        undo.undo()
        assertNull(undo.savedAt.value)
    }

    /** A plain file leaves the 우리 조 code out and says so; a sealed one and the phone-to-phone copy hold it. */
    @Test fun theGroupCodeGoesOnlyWithAPassword() = runBlocking {
        ScheduleRepository(db).ensureDefaults()
        ShareGroupStore(context).save("QWERTY", null)
        val files = SettingsFilesBackup(context, { listOf(PersonalCalendar.MAIN) }) {}
        val backups = BackupManager(db, SettingsRepository(context), settingsFiles = { files })
        assertFalse(backups.export().contains("QWERTY"))
        assertTrue(backups.exportSealed().contains("QWERTY"))
        assertFalse(backups.carryOut().toString().contains("QWERTY"))
        assertTrue(backups.carryKeys().toString().contains("QWERTY"))
        val gaps = backups.plainLeavesOut()
        assertTrue(gaps.groupCode)
        assertEquals("우리 조 코드는 암호를 걸어야 들어가요", gaps.line)
        // A plain file put in keeps the phone's code; the carry of keys brings it to a new phone.
        backups.restore(backups.export())
        assertEquals("QWERTY", ShareGroupStore(context).code.value)
        val keys = backups.carryKeys()
        ShareGroupStore(context).clear()
        backups.carryIn(keys)
        assertEquals("QWERTY", ShareGroupStore(context).code.value)
    }

    /** The phone's key went: the keys come back from the copy Android's backup carries, before that copy is written again. */
    @Test fun aPhoneWhoseKeyWentGetsItsKeysBack() = runBlocking {
        ScheduleRepository(db).ensureDefaults()
        val vault = context.getSharedPreferences(Vault.PREFS, Context.MODE_PRIVATE)
        assertTrue(Vault.check(vault))
        link()
        val pro = ProStore(context).apply { setCode("ABCD-EFGH-JKLM-NPQR") }
        val share = ShareLinkBackup({ listOf(PersonalCalendar.MAIN) }, { ShareLinkStore(context, it) })
        val backups = BackupManager(db, SettingsRepository(context), shareLinks = share, pro = { pro })
        val move = PhoneMove(File(dir, "carry.json"), File(dir, "keys.json"), File(dir, "here"), File(dir, "moved.json"),
            out = backups::carryOut, outKeys = backups::carryKeys, received = { JSONArray() }, take = backups::carryIn)
        move.arrive()
        move.keep()
        assertTrue(File(dir, "keys.json").readText().contains("dtok-secret"))

        box.lose()
        Vault.box = box // the app opened again: nothing opened is remembered
        assertNull(ShareLinkStore(context, PersonalCalendar.MAIN).load())
        assertFalse(Vault.check(vault))
        assertEquals(1, move.keysBack())
        assertEquals("dtok-secret", ShareLinkStore(context, PersonalCalendar.MAIN).load()?.deleteToken)
        assertEquals("ABCD-EFGH-JKLM-NPQR", ProStore(context).current.code)
        // Sealed again by the new key, and the copy written again holds them.
        assertFalse(raw("share_link").contains("dtok-secret"))
        move.keep()
        assertTrue(File(dir, "keys.json").readText().contains("dtok-secret"))
        assertTrue(Vault.check(vault))
    }
}
