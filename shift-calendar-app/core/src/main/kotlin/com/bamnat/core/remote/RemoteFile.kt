package com.bamnat.core.remote

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.time.LocalDate
import java.util.Base64

/**
 * [REMOTE-01] One remote data file (결정 D3): `remote/v1/<kind>.json` at the top of the repository, served by
 * GitHub Pages next to the privacy policy (`https://ywjmoney.github.io/-/remote/v1/`), with `<kind>.json.sig`
 * beside it holding the Ed25519 signature of the file's exact bytes in base64 (and, from 1.27, `<kind>.json.sig2`
 * with the signature by the 2026-10-26 key, [RemoteKeys.SIGNATURES]). The same JSON ships inside
 * the app (core resources), so a phone that never goes online has the data the release had.
 *
 * ```
 * {"format": 1, "kind": "presets", "version": 2026102201, "issued": "2026-10-22", "minApp": 33,
 *  "source": "...", "data": {...}}
 * ```
 * [version] only grows (날짜 + 두 자리); a phone takes a downloaded file only when it is newer than what it
 * has. [minApp] is the lowest versionCode that understands [data]; an older app ignores the file.
 */
data class RemoteFile(
    val kind: String,
    val version: Long,
    val issued: LocalDate,
    val minApp: Int,
    val source: String,
    val data: JsonObj,
)

object RemoteFiles {
    const val FORMAT = 1L
    const val PRESETS = "presets"
    const val RATES = "rates"
    const val WAGES = "wages"
    const val TAXTABLE = "taxtable"
    /** [REMOTE-05] 공무원 봉급표 ([com.bamnat.core.pay.PublicPayTable]), 1.23. */
    const val PUBLICPAY = "publicpay"
    /** [REMOTE-05] 공휴일표 ([com.bamnat.core.holiday.KoreanHolidays]), 1.23. */
    const val HOLIDAYS = "holidays"
    /** [INV-36] 투자 세금 기준 ([com.bamnat.core.invest.InvestTaxes]), 1.23. */
    const val TAXES = "taxes"
    /** [REMOTE-07] 제조사별 알람 설정 안내 ([AlarmGuides]); the frame is K's, the words L's. 1.23. */
    const val ALARM_GUIDE = "alarm-guide"
    /** [SPAT-01] 회사별 교대 주기 공개 자료 ([com.bamnat.core.plaza.PublicCycles]), 1.26 결정 D80. */
    const val CYCLES = "cycles"
    /** 1.32 [LEDX-24] 가맹점 이름 사전 ([com.bamnat.core.money.MerchantNames]): server → phone only. */
    const val MERCHANTS = "merchants"
    /** The files the app reads; each ships inside the app and is published at [path]. */
    val KINDS = listOf(PRESETS, RATES, WAGES, TAXTABLE, PUBLICPAY, HOLIDAYS, TAXES, ALARM_GUIDE, CYCLES, MERCHANTS)

    /** Where the files live on the site, below the app's `bamnat.remoteBaseUrl`. */
    fun path(kind: String) = "remote/v1/$kind.json"

    /** Reads [bytes] strictly (UTF-8, JSON, envelope); any fault is a [RemoteFileException]. */
    fun parse(bytes: ByteArray): RemoteFile {
        val text = try {
            Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString()
        } catch (e: java.nio.charset.CharacterCodingException) {
            throw RemoteFileException("not UTF-8")
        }
        @Suppress("UNCHECKED_CAST")
        val o = JsonObj(Json.parse(text.removePrefix("﻿")) as? Map<String, Any?> ?: throw RemoteFileException("not an object"))
        if (o.long("format") != FORMAT) throw RemoteFileException("format")
        val issued = try { LocalDate.parse(o.str("issued")) } catch (e: java.time.format.DateTimeParseException) { throw RemoteFileException("issued") }
        return RemoteFile(o.str("kind"), o.long("version"), issued, o.int("minApp"), o.strOr("source", ""), o.obj("data"))
    }

    /** True when [signature] (base64 of 64 bytes) is one of [keys]' (base64 of 32 bytes) signature of [bytes]. */
    fun verify(bytes: ByteArray, signature: String, keys: List<String> = RemoteKeys.TRUSTED): Boolean {
        val sig = try { Base64.getDecoder().decode(signature.trim()) } catch (e: IllegalArgumentException) { return false }
        // [PERF-19] 1.28 AO: the newest keys first. Files are signed with the 2026-10-26 key (listed just before its spare), and each
        // key that does not match costs a whole check (tens of milliseconds on a phone, BigInteger arithmetic).
        return keys.asReversed().any { k -> Ed25519.verify(Base64.getDecoder().decode(k), bytes, sig) }
    }

    /**
     * A downloaded file the phone may use: signed by a trusted key, the [kind] asked for, and meant for an
     * app at [appVersion] or older. Anything else is a [RemoteFileException] and the phone keeps its data.
     */
    fun open(bytes: ByteArray, signature: String, kind: String, appVersion: Int, keys: List<String> = RemoteKeys.TRUSTED): RemoteFile {
        if (!verify(bytes, signature, keys)) throw RemoteFileException("signature")
        return openChecked(bytes, kind, appVersion)
    }

    /**
     * [PERF-19] 1.28 AO: a file whose signature this phone already checked (the same bytes, signature, keys and app;
     * see the app's RemoteFilesStore): only what it is and which app it is for are looked at again.
     */
    fun openChecked(bytes: ByteArray, kind: String, appVersion: Int): RemoteFile {
        val f = parse(bytes)
        if (f.kind != kind) throw RemoteFileException("kind ${f.kind}")
        if (f.minApp > appVersion) throw RemoteFileException("needs app ${f.minApp}")
        return f
    }

    /**
     * [PERF-19] What a checked file's mark holds: a SHA-256 over its bytes, its signature, the trusted keys and the app
     * version, so a changed file, signature, key list (a key dropped in a later app) or app makes the check run again.
     */
    fun checkedMark(bytes: ByteArray, signature: String, keys: List<String>, appVersion: Int): String {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        md.update(bytes)
        md.update(0)
        md.update(signature.trim().toByteArray(Charsets.US_ASCII))
        md.update(0)
        md.update(keys.joinToString(",").toByteArray(Charsets.US_ASCII))
        md.update(0)
        md.update(appVersion.toString().toByteArray(Charsets.US_ASCII))
        val hex = "0123456789abcdef"
        return buildString(64) { for (b in md.digest()) append(hex[(b.toInt() shr 4) and 15]).append(hex[b.toInt() and 15]) }
    }

    /** The copy built into the app (trusted as part of the signed APK); null only if packaging lost it. */
    fun bundledBytes(kind: String): ByteArray? =
        RemoteFiles::class.java.getResourceAsStream("/com/bamnat/core/remote/v1/$kind.json")?.use { it.readBytes() }

    fun bundled(kind: String): RemoteFile? = bundledBytes(kind)?.let { runCatching { parse(it) }.getOrNull() }
}

/**
 * [REMOTE-01] The public keys whose signatures the app trusts, base64 of the raw 32 bytes. The private key
 * is kept only in the project's private `release-key/` folder (결정 D3), never in this repository. A second
 * key can be added here one release before switching to it.
 */
object RemoteKeys {
    val TRUSTED: List<String> = listOf(
        // 2026-10-22 key, private half in the project's release-key/bamnat-remote-ed25519.pem.
        "6e6J4SWfNWAY8142S4+UDOjBJz+4/njmfjxATwAl55I=",
        // [REMOTE-04] 2026-10-23 spare key (키 교체 대비), private half in release-key/bamnat-remote-ed25519-2.pem.
        // Files are still signed with the first key; this one lets a later release switch keys, or recover from a
        // lost one, without stranding phones that never updated.
        "fzoA27EsvTUDa4EA/xtL/z4039vz1TSwmTr48lksmXQ=",
        // [REMOTE-08] 2026-10-26 key (결정 D83): the two keys above were lost with the old project, so 1.27 signs
        // every file again with this one, private half in release-key/bamnat-remote-ed25519-2026-10-26.pem. The
        // new signature goes in <kind>.json.sig2 so the old .sig files that 1.26 and older apps check stay as
        // they were (those apps never trust this key).
        "DjM9UzLI9MlnEWqpweAItpsvGvzNsFo8FNcai5M/NPY=",
        // [REMOTE-08] its spare, private half in release-key/bamnat-remote-ed25519-2026-10-26-2.pem.
        "ey5slM4bow7nLuljDW6ZCc2pVVIUN0o5dFkWsBQUc/E=",
    )

    /**
     * [REMOTE-08] Where a file's signature is, in the order a phone asks: the 2026-10-26 keys sign `.sig2`; `.sig`
     * holds the old signature for apps up to 1.26 and is only there while the file has not changed since.
     */
    val SIGNATURES: List<String> = listOf(".sig2", ".sig")
}
