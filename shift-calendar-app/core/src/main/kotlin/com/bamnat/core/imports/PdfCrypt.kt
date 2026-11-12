package com.bamnat.core.imports

import java.math.BigInteger
import java.nio.charset.Charset
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 2.0.0 [RVL-41] (검토 U07b-10·I07bb-01) A PDF's standard lock (ISO 32000-2 7.6.4, the "Standard" security handler): RC4 of 40 to
 * 128 bits (R2·R3), AES-128 (R4, AESV2) and AES-256 (R5·R6, AESV3). A payslip the company mails is often locked with the
 * 생년월일; the password typed opens it here, on the phone, and is kept nowhere: the key it makes lives as long as the file
 * being read. The empty password is tried first, so a file locked only against printing or copying opens without asking.
 * Written from the standard, no library: the file's strings and streams are decrypted object by object.
 */
internal class PdfCrypt private constructor(
    private val key: ByteArray,
    private val v: Int,
    /** How strings and streams are locked: AES, RC4, or not at all (null, the Identity filter). */
    private val strings: Method?,
    private val streams: Method?,
) {
    enum class Method { RC4, AES }

    fun string(num: Int, gen: Int, b: ByteArray): ByteArray = crypt(strings, num, gen, b)
    fun stream(num: Int, gen: Int, b: ByteArray): ByteArray = crypt(streams, num, gen, b)

    private fun crypt(m: Method?, num: Int, gen: Int, b: ByteArray): ByteArray {
        m ?: return b
        val k = if (v >= 5) key else objectKey(num, gen, m == Method.AES)
        return if (m == Method.AES) aesOpen(k, b) else rc4(k, b)
    }

    /** Algorithm 1: the file key with the object's number and generation (and "sAlT" for AES). */
    private fun objectKey(num: Int, gen: Int, aes: Boolean): ByteArray {
        val md = MessageDigest.getInstance("MD5")
        md.update(key)
        md.update(byteArrayOf(num.toByte(), (num shr 8).toByte(), (num shr 16).toByte(), gen.toByte(), (gen shr 8).toByte()))
        if (aes) md.update(byteArrayOf(0x73, 0x41, 0x6C, 0x54))
        return md.digest().copyOf(minOf(key.size + 5, 16))
    }

    /** What a password does to a locked file. */
    sealed interface Opened {
        class Key(val crypt: PdfCrypt) : Opened
        /** The password (or none) does not open it. */
        data object Wrong : Opened
        /** Not the standard lock (a certificate's, or one this reader does not know): only the system's renderer may open it. */
        data object Unknown : Opened
    }

    companion object {
        private val PAD = intArrayOf(
            0x28, 0xBF, 0x4E, 0x5E, 0x4E, 0x75, 0x8A, 0x41, 0x64, 0x00, 0x4E, 0x56, 0xFF, 0xFA, 0x01, 0x08,
            0x2E, 0x2E, 0x00, 0xB6, 0xD0, 0x68, 0x3E, 0x80, 0x2F, 0x0C, 0xA9, 0xFE, 0x64, 0x53, 0x69, 0x7A,
        ).map { it.toByte() }.toByteArray()

        /**
         * The key for [encrypt] (the file's /Encrypt dictionary, references resolved by [get]) and [id0] (the first /ID string),
         * with the empty password first, then [password] as the user's and as the owner's.
         */
        fun open(encrypt: Map<String, Any?>, id0: ByteArray, password: CharArray?, get: (Any?) -> Any?): Opened {
            fun num(k: String): Double? = get(encrypt[k]) as? Double
            fun str(k: String): ByteArray? = (get(encrypt[k]) as? PdfText.PStr)?.bytes
            if ((get(encrypt["Filter"]) as? PdfText.Name)?.v != "Standard") return Opened.Unknown
            val v = num("V")?.toInt() ?: 0
            val r = num("R")?.toInt() ?: return Opened.Unknown
            val o = str("O") ?: return Opened.Unknown
            val u = str("U") ?: return Opened.Unknown
            val p = num("P")?.toLong()?.toInt() ?: 0
            val meta = get(encrypt["EncryptMetadata"]) as? Boolean ?: true
            // How strings and streams are locked: V4·V5 name a crypt filter each, V1·V2 are RC4.
            @Suppress("UNCHECKED_CAST") val filters = (get(encrypt["CF"]) as? Map<String, Any?>).orEmpty()
            fun method(which: String): Method? {
                if (v < 4) return Method.RC4
                val name = (get(encrypt[which]) as? PdfText.Name)?.v ?: "Identity"
                if (name == "Identity") return null
                @Suppress("UNCHECKED_CAST") val cf = (get(filters[name]) as? Map<String, Any?>).orEmpty()
                return when ((get(cf["CFM"]) as? PdfText.Name)?.v) {
                    "AESV2", "AESV3" -> Method.AES
                    "V2" -> Method.RC4
                    "None" -> null
                    else -> if (v >= 5) Method.AES else Method.RC4
                }
            }
            val strings = method("StrF")
            val streams = method("StmF")
            val tries = buildList {
                add(CharArray(0))
                if (password != null && password.isNotEmpty()) add(password)
            }
            if (r >= 5) {
                val oe = str("OE") ?: return Opened.Unknown
                val ue = str("UE") ?: return Opened.Unknown
                if (o.size < 48 || u.size < 48 || oe.size < 32 || ue.size < 32) return Opened.Unknown
                for (pw in tries) {
                    val b = String(pw).toByteArray(Charsets.UTF_8).let { if (it.size > 127) it.copyOf(127) else it }
                    aes256Key(r, b, o, u, oe, ue)?.let { return Opened.Key(PdfCrypt(it, 5, strings, streams)) }
                }
                return Opened.Wrong
            }
            val bits = if (v >= 4) {
                // AESV2 is 128 bits; a V2 crypt filter may give its own length (in bytes, or bits by some writers).
                @Suppress("UNCHECKED_CAST") val cf = (get(filters[(get(encrypt["StmF"]) as? PdfText.Name)?.v]) as? Map<String, Any?>).orEmpty()
                (get(cf["Length"]) as? Double)?.toInt()?.let { if (it <= 16) it * 8 else it } ?: num("Length")?.toInt() ?: 128
            } else num("Length")?.toInt() ?: 40
            val n = if (r == 2) 5 else (bits / 8).coerceIn(5, 16)
            for (pw in tries) for (b in encodings(pw)) {
                userKey(b, o, u, p, id0, r, n, meta)?.let { return Opened.Key(PdfCrypt(it, v, strings, streams)) }
                if (pw.isEmpty()) continue
                ownerKey(b, o, u, p, id0, r, n, meta)?.let { return Opened.Key(PdfCrypt(it, v, strings, streams)) }
            }
            return Opened.Wrong
        }

        /**
         * A password's bytes as the program that locked the file may have made them: PDFDocEncoding (Latin letters and digits,
         * a 생년월일), else the Korean Windows code page, else UTF-8.
         */
        private fun encodings(pw: CharArray): List<ByteArray> {
            val s = String(pw)
            if (s.all { it.code <= 0xFF }) return listOf(s.toByteArray(Charsets.ISO_8859_1))
            return listOfNotNull(CP949?.let { s.toByteArray(it) }, s.toByteArray(Charsets.UTF_8)).distinctBy { it.toList() }
        }

        private val CP949: Charset? = listOf("x-windows-949", "MS949", "windows-949", "EUC-KR")
            .firstNotNullOfOrNull { runCatching { Charset.forName(it) }.getOrNull() }

        private fun padded(pw: ByteArray): ByteArray = ByteArray(32) { if (it < pw.size) pw[it] else PAD[it - pw.size] }

        private fun md5(vararg parts: ByteArray): ByteArray = MessageDigest.getInstance("MD5").run { parts.forEach(::update); digest() }

        /** Algorithm 2: the file key from a user password; Algorithms 4·5: it is the right one when it makes /U. */
        private fun userKey(pw: ByteArray, o: ByteArray, u: ByteArray, p: Int, id0: ByteArray, r: Int, n: Int, meta: Boolean): ByteArray? {
            val md = MessageDigest.getInstance("MD5")
            md.update(padded(pw))
            md.update(o.copyOf(32))
            md.update(byteArrayOf(p.toByte(), (p shr 8).toByte(), (p shr 16).toByte(), (p shr 24).toByte()))
            md.update(id0)
            if (r >= 4 && !meta) md.update(byteArrayOf(-1, -1, -1, -1))
            var h = md.digest()
            if (r >= 3) repeat(50) { h = md5(h.copyOf(n)) }
            val key = h.copyOf(n)
            val ok = if (r == 2) rc4(key, PAD).contentEquals(u.copyOf(32))
            else {
                var x = rc4(key, md5(PAD, id0))
                for (i in 1..19) x = rc4(ByteArray(n) { (key[it].toInt() xor i).toByte() }, x)
                x.contentEquals(u.copyOf(16))
            }
            return key.takeIf { ok }
        }

        /** Algorithm 7: an owner password gives the user's back from /O, which then makes the key. */
        private fun ownerKey(pw: ByteArray, o: ByteArray, u: ByteArray, p: Int, id0: ByteArray, r: Int, n: Int, meta: Boolean): ByteArray? {
            var h = md5(padded(pw))
            if (r >= 3) repeat(50) { h = md5(h) }
            val k = h.copyOf(n)
            var user = o.copyOf(32)
            if (r == 2) user = rc4(k, user)
            else for (i in 19 downTo 0) user = rc4(ByteArray(n) { (k[it].toInt() xor i).toByte() }, user)
            return userKey(user, o, u, p, id0, r, n, meta)
        }

        /** R5·R6 (AES-256): the password checked against /U then /O, the file key unwrapped from /UE or /OE. */
        private fun aes256Key(r: Int, pw: ByteArray, o: ByteArray, u: ByteArray, oe: ByteArray, ue: ByteArray): ByteArray? {
            val u48 = u.copyOf(48)
            if (hash(r, pw, u.copyOfRange(32, 40), ByteArray(0)).contentEquals(u.copyOf(32))) {
                return aesRaw(hash(r, pw, u.copyOfRange(40, 48), ByteArray(0)), ue.copyOf(32))
            }
            if (hash(r, pw, o.copyOfRange(32, 40), u48).contentEquals(o.copyOf(32))) {
                return aesRaw(hash(r, pw, o.copyOfRange(40, 48), u48), oe.copyOf(32))
            }
            return null
        }

        /** R5: SHA-256; R6: Algorithm 2.B, SHA-256·384·512 by turns over AES-128 rounds. */
        private fun hash(r: Int, pw: ByteArray, salt: ByteArray, udata: ByteArray): ByteArray {
            var k = MessageDigest.getInstance("SHA-256").run { update(pw); update(salt); update(udata); digest() }
            if (r == 5) return k
            var i = 0
            while (true) {
                val one = pw + k + udata
                val k1 = ByteArray(one.size * 64).also { buf -> for (j in 0 until 64) one.copyInto(buf, j * one.size) }
                val e = Cipher.getInstance("AES/CBC/NoPadding").run {
                    init(Cipher.ENCRYPT_MODE, SecretKeySpec(k.copyOf(16), "AES"), IvParameterSpec(k.copyOfRange(16, 32)))
                    doFinal(k1)
                }
                val alg = when (BigInteger(1, e.copyOf(16)).mod(BigInteger.valueOf(3)).toInt()) { 0 -> "SHA-256"; 1 -> "SHA-384"; else -> "SHA-512" }
                k = MessageDigest.getInstance(alg).digest(e)
                i++
                if (i >= 64 && (e.last().toInt() and 0xFF) <= i - 32) break
            }
            return k.copyOf(32)
        }

        /** AES-256 with no IV and no padding: how /UE and /OE keep the file key. */
        private fun aesRaw(key: ByteArray, data: ByteArray): ByteArray = Cipher.getInstance("AES/CBC/NoPadding").run {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(ByteArray(16)))
            doFinal(data)
        }

        /** A string or stream locked with AES: its first 16 bytes the IV, padded to 16 (padding taken off when it is there). */
        internal fun aesOpen(key: ByteArray, b: ByteArray): ByteArray {
            if (b.size < 32) return ByteArray(0)
            val body = b.copyOfRange(16, 16 + (b.size - 16) / 16 * 16)
            val out = runCatching {
                Cipher.getInstance("AES/CBC/NoPadding").run {
                    init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(b.copyOf(16)))
                    doFinal(body)
                }
            }.getOrElse { return ByteArray(0) }
            val pad = out.last().toInt() and 0xFF
            return if (pad in 1..16 && pad <= out.size && (out.size - pad until out.size).all { (out[it].toInt() and 0xFF) == pad }) out.copyOf(out.size - pad) else out
        }

        internal fun rc4(key: ByteArray, data: ByteArray): ByteArray {
            val s = IntArray(256) { it }
            var j = 0
            for (i in 0 until 256) {
                j = (j + s[i] + (key[i % key.size].toInt() and 0xFF)) and 0xFF
                val t = s[i]; s[i] = s[j]; s[j] = t
            }
            var a = 0; var b = 0
            return ByteArray(data.size) { k ->
                a = (a + 1) and 0xFF
                b = (b + s[a]) and 0xFF
                val t = s[a]; s[a] = s[b]; s[b] = t
                (data[k].toInt() xor s[(s[a] + s[b]) and 0xFF]).toByte()
            }
        }
    }
}
