package com.bamnat.app.review

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * 2.0.0 [RVQ-50] 검토 U22-08·U22b-05: 밤낮's own page in Play, for '업데이트하기' where a fix or a work needs a newer app
 * (문의의 '○○에서 고쳤어요', 장터의 '업데이트가 필요해요'). The Play app first, the web page when there is none.
 */
object PlayPage {
    fun open(context: Context) {
        val id = context.packageName
        val play = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$id")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(play)
        } catch (_: ActivityNotFoundException) {
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$id"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }
}
