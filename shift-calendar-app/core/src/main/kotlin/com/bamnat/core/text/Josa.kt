package com.bamnat.core.text

/** 1.38 PC 9차 ("팀 요금제이"): 이/가, 을/를, 은/는 by the last letter's 받침, for words the code puts into a sentence. */
object Josa {
    private fun batchim(w: String): Boolean? = w.trim().lastOrNull()?.let { c -> if (c in '가'..'힣') (c - '가') % 28 != 0 else null }

    fun iga(w: String): String = if (batchim(w) == false) "가" else "이"
    fun eulreul(w: String): String = if (batchim(w) == false) "를" else "을"
    fun eunneun(w: String): String = if (batchim(w) == false) "는" else "은"
    /** 1.39 PC NAV2 ("‘연차’과 묶을 화면"): 과/와. */
    fun gwawa(w: String): String = if (batchim(w) == false) "와" else "과"
    /** 2.0.0 [RVL-36] ("주간으로 읽었어요", "연차로"): 으로/로, 로 after no 받침 or ㄹ. */
    fun euro(w: String): String {
        val c = w.trim().lastOrNull()?.takeIf { it in '가'..'힣' } ?: return "로"
        val end = (c - '가') % 28
        return if (end == 0 || end == 8) "로" else "으로"
    }
}
