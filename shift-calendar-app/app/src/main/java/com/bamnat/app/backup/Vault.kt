package com.bamnat.app.backup

import android.content.SharedPreferences
import java.io.File
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 2.0.0 [RVL-77] (검토 I12b-03) 링크 열쇠·복구 코드 금고: the keys this phone holds for the user (a share link's key, the 프로 recovery code,
 * a team board's key, the copies a restore or an import keeps) are written sealed by one key of the Android key store that never
 * leaves the phone, instead of as plain words in a preference file anyone with the phone's files could read.
 *
 * - A value reads back as it was written; one written before (plain) is read as it is and sealed the next time it is written
 *   ([SharedPreferences.secret] seals it at once).
 * - A phone whose key store fails keeps the value as before (plain), so a key is never lost to sealing.
 * - When the phone's key is gone (a reset lock screen on some phones) a sealed value cannot be read: [lost] says so, and the app
 *   takes the keys back from the copy Android's own backup carries ([com.bamnat.app.data.PhoneMove.keysBack]) before that copy is
 *   written again.
 * - Backups: a file saved without a password still leaves these keys out (as before); the sealed file and the phone-to-phone copy
 *   hold them in the clear inside their own seal, since a new phone has a key store of its own.
 */
object Vault {
    const val MARK = "kv1:"
    private val FILE_MARK = "KVF1".toByteArray(Charsets.US_ASCII)
    private const val ALIAS = "bamnat_key_vault"

    /** The phone's key store; tests put an in-memory box in its place. */
    @Volatile var box: KeyBox = KeystoreKeyBox(ALIAS)
        set(value) { field = value; opened.clear(); lost.set(false) }

    /** True once a sealed value could not be opened on this phone (its key went). */
    val lost = AtomicBoolean(false)

    // Opening goes through the key store each time; a value read often (a link's key at each update) is opened once.
    private val opened = ConcurrentHashMap<String, String>()
    private val random = SecureRandom()

    fun isSealed(stored: String?): Boolean = stored?.startsWith(MARK) == true

    /** [plain] sealed, or [plain] itself when this phone cannot seal. */
    fun seal(plain: String?): String? {
        if (plain == null || isSealed(plain)) return plain
        return runCatching { MARK + Base64.getEncoder().encodeToString(box.wrap(plain.toByteArray(Charsets.UTF_8))) }
            .onSuccess { opened[it] = plain }
            .getOrDefault(plain)
    }

    /** What [seal] was given; a plain value as it is; null when it cannot be opened here (and [lost] is set). */
    fun open(stored: String?): String? {
        if (stored == null || !isSealed(stored)) return stored
        opened[stored]?.let { return it }
        return runCatching { String(box.unwrap(Base64.getDecoder().decode(stored.substring(MARK.length))), Charsets.UTF_8) }
            .onSuccess { if (opened.size < 64) opened[stored] = it }
            // Never forgets the key here: a value an older key sealed fails too, and forgetting would lose all written since.
            .getOrElse {
                lost.set(true)
                null
            }
    }

    /**
     * Whether this phone's key still opens what it sealed: a small sealed word kept in [prefs] is opened (and written the first time).
     * False sets [lost]. Called at the app's start, before the carry of keys is written again.
     */
    fun check(prefs: SharedPreferences): Boolean {
        val stored = prefs.getString(CANARY, null)
        if (stored == null || !isSealed(stored)) {
            seal(CANARY_WORD)?.takeIf(::isSealed)?.let { prefs.edit().putString(CANARY, it).apply() }
            return true
        }
        opened.remove(stored)
        val ok = open(stored) == CANARY_WORD
        if (!ok) {
            lost.set(true)
            // The key there may be one that no longer works at all: then it is forgotten, and a new one made, once.
            var fresh = seal(CANARY_WORD)
            if (fresh != null && isSealed(fresh)) {
                opened.remove(fresh)
                if (open(fresh) != CANARY_WORD) {
                    runCatching { box.forget() }
                    fresh = seal(CANARY_WORD)
                }
            }
            prefs.edit().apply { if (fresh != null && isSealed(fresh)) putString(CANARY, fresh) else remove(CANARY) }.apply()
        }
        return ok
    }

    /**
     * A file's words sealed: a fresh key for the file seals them (a large copy is not sent through the key store), and that key is
     * sealed by the phone's. Written whole (a part file renamed). A phone that cannot seal writes them plain.
     */
    fun writeFile(file: File, text: String) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        val sealed = runCatching {
            val key = ByteArray(32).also(random::nextBytes)
            val wrapped = box.wrap(key)
            val iv = ByteArray(12).also(random::nextBytes)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
            val body = cipher.doFinal(bytes)
            key.fill(0)
            FILE_MARK + byteArrayOf((wrapped.size shr 8).toByte(), wrapped.size.toByte()) + wrapped + iv + body
        }.getOrDefault(bytes)
        file.parentFile?.mkdirs()
        val part = File(file.parentFile, file.name + ".part")
        part.writeBytes(sealed)
        if (!part.renameTo(file)) {
            file.writeBytes(sealed)
            part.delete()
        }
    }

    /** What [writeFile] wrote (or a plain file of before); null when there is none or this phone's key no longer opens it. */
    fun readFile(file: File): String? {
        if (!file.isFile) return null
        val all = file.readBytes()
        if (all.size < FILE_MARK.size || !all.copyOfRange(0, FILE_MARK.size).contentEquals(FILE_MARK)) return String(all, Charsets.UTF_8)
        return runCatching {
            var at = FILE_MARK.size
            val n = ((all[at].toInt() and 0xff) shl 8) or (all[at + 1].toInt() and 0xff)
            at += 2
            val key = box.unwrap(all.copyOfRange(at, at + n))
            at += n
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, all, at, 12))
            key.fill(0)
            String(cipher.doFinal(all, at + 12, all.size - at - 12), Charsets.UTF_8)
        }.getOrElse {
            lost.set(true)
            null
        }
    }

    const val PREFS = "key_vault"
    private const val CANARY = "canary"
    private const val CANARY_WORD = "밤낮"
}

/** 2.0.0 [RVL-77] A key kept in a preference file: opened from the [Vault], and a plain one of before sealed in its place. */
fun SharedPreferences.secret(key: String): String? {
    val raw = getString(key, null) ?: return null
    val value = Vault.open(raw)
    if (value != null && !Vault.isSealed(raw)) Vault.seal(raw)?.takeIf(Vault::isSealed)?.let { edit().putString(key, it).apply() }
    return value
}

/** 2.0.0 [RVL-77] Writes [value] sealed (removes the key for null). */
fun SharedPreferences.Editor.putSecret(key: String, value: String?): SharedPreferences.Editor =
    if (value == null) remove(key) else putString(key, Vault.seal(value))
