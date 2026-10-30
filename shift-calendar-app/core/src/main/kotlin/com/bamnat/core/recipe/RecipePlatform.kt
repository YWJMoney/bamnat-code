package com.bamnat.core.recipe

import java.security.MessageDigest
import java.text.Normalizer

/**
 * [RCX-16] 1.35 아이폰 형식 (계획/아이폰판-검토보고서.md, KMP): the only place in the recipe format that needs the JVM.
 * Everything else in this package is plain Kotlin, so moving the recipe format to a shared (commonMain) module means turning
 * this object into `expect object RecipePlatform` with an Android/JVM `actual` (this file) and an iOS `actual`
 * (CFStringNormalize / CommonCrypto CC_SHA256, NSBundle for the emoji list). RecipePlatformTest keeps it that way.
 */
internal object RecipePlatform {
    fun nfc(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFC)

    fun nfkc(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFKC)

    fun utf8(s: String): ByteArray = s.toByteArray(Charsets.UTF_8)

    fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)

    /** A text file shipped with the format (the emoji list), or null when it is not there. */
    fun resource(path: String): String? =
        RecipePlatform::class.java.getResourceAsStream(path)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
}

/** Plain-Kotlin number writing the format needs, the same on every platform. */
internal object RecipeNumbers {
    /** [v] as 1,234,567. */
    fun grouped(v: Long): String {
        val digits = kotlin.math.abs(v).toString()
        val out = StringBuilder()
        digits.forEachIndexed { i, c -> if (i > 0 && (digits.length - i) % 3 == 0) out.append(','); out.append(c) }
        return (if (v < 0) "-" else "") + out
    }

    /** Hundredths as JavaScript writes them: 1.5, 0.25, -3.1 (no trailing zeros, no exponent). */
    fun cents(c: Long): String {
        val sign = if (c < 0) "-" else ""
        val a = kotlin.math.abs(c)
        val frac = (a % 100).toString().padStart(2, '0').trimEnd('0')
        return sign + (a / 100) + (if (frac.isEmpty()) "" else ".$frac")
    }

    fun hex(bytes: ByteArray): String = bytes.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
}
