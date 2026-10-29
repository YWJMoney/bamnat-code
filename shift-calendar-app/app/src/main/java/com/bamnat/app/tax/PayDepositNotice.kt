package com.bamnat.app.tax

import android.content.Context
import androidx.core.app.NotificationCompat
import com.bamnat.app.NoticeLinks
import com.bamnat.app.R
import com.bamnat.app.alarm.Notifications
import com.bamnat.core.pay.PayGaps
import java.time.YearMonth
import kotlin.math.abs

/**
 * D 1.32 [PAYA-05] 579: "급여 2,830,000원 들어왔어요 · 예상보다 52,300원 적어요" when a pay deposit is read from a bank's
 * notification, opening 근무·급여 at 월급날 비교. In 나갈 돈·받을 돈's channel; off in 설정 › 알림과 잠 › 돈과 휴가.
 */
object PayDepositNotice {
    const val ID = 7207

    fun text(amount: Long, expected: Long?): Pair<String, String> {
        val title = "급여 ${"%,d".format(amount)}원이 들어왔어요"
        val body = when {
            expected == null || expected <= 0 -> "급여 계산을 맞추면 예상과 바로 비교해 드려요"
            abs(amount - expected) <= PayGaps.TOLERANCE -> "예상(${"%,d".format(expected)}원)과 거의 같아요"
            amount < expected -> "예상보다 ${"%,d".format(expected - amount)}원 적어요. 눌러서 무엇이 다른지 봐요"
            else -> "예상보다 ${"%,d".format(amount - expected)}원 많아요"
        }
        return title to body
    }

    fun post(context: Context, calendarId: String, payMonth: YearMonth, amount: Long, expected: Long?) {
        val (title, body) = text(amount, expected)
        val n = NotificationCompat.Builder(context, Notifications.MONEY_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText("${payMonth.monthValue}월분 · $body")
            .setContentIntent(Notifications.open(context, NoticeLinks.Target.PAY_GAP, 2210, calendar = calendarId))
            .setAutoCancel(true)
            .build()
        Notifications.post(context, ID, n)
    }
}
