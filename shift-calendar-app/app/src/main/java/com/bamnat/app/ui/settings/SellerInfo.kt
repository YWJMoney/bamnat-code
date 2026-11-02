package com.bamnat.app.ui.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * 1.36 [MON-12] 사업자 정보: what the 전자상거래법 (제10조, 제13조) asks a seller to show, the same words as
 * 이용약관 제2조 and 처리방침 8항 (https://ywjmoney.github.io/-/terms/). A change here is a change there,
 * and a 변경 신고 at the 구청 within 15 days.
 */
object SellerInfo {
    const val NAME = "밤낮"
    const val OWNER = "양원준"
    const val BUSINESS_NO = "327-25-02565"
    const val MAIL_ORDER_NO = "제2026-전남여수-0424호"
    const val ADDRESS = "전남광주통합특별시 여수시 안산1길 179, 2동 105호"
    const val PHONE = "010-6408-8825"
    const val MAIL = "yoj0790@naver.com"

    /** 공정거래위원회 사업자 정보 공개: the 신고 as the government shows it. */
    const val CHECK_URL = "https://www.ftc.go.kr/bizCommPop.do?wrkr_no=3272502565"

    /** One line for the screens that sell (설정 › 프로). */
    const val SHORT = "판매: $NAME(대표 $OWNER), 사업자등록번호 $BUSINESS_NO, 통신판매업 신고 $MAIL_ORDER_NO."

    val lines: List<Pair<String, String>> = listOf(
        "상호" to NAME,
        "대표자" to OWNER,
        "사업자등록번호" to BUSINESS_NO,
        "통신판매업 신고번호" to MAIL_ORDER_NO,
        "사업장 주소" to ADDRESS,
        "전화" to PHONE,
        "전자우편" to MAIL,
        "호스팅 제공자" to "Cloudflare, Inc.(앱 서버), GitHub, Inc.(약관·처리방침 페이지)",
    )
}

/**
 * 1.36 [MON-12] The 사업자 정보 card, from 설정 › 개인정보·앱 정보 › 앱 정보 and from 설정 › 프로. Each line copies
 * whole with one tap (a long press picks only a piece between hyphens); 전화 and 전자우편 also call or write.
 */
@Composable
fun SellerInfoDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    fun open(uri: String) = runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("사업자 정보") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                SellerInfo.lines.forEach { (label, value) ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(value, style = MaterialTheme.typography.bodyMedium)
                        }
                        when (label) {
                            "전화" -> TextButton(onClick = { open("tel:${SellerInfo.PHONE}") }) { Text("전화") }
                            // No mail app: the address is copied instead of nothing happening.
                            "전자우편" -> TextButton(onClick = {
                                if (open("mailto:${SellerInfo.MAIL}").isFailure) {
                                    clipboard.setText(AnnotatedString(SellerInfo.MAIL))
                                    Toast.makeText(context, "메일 앱이 없어 주소를 복사했어요", Toast.LENGTH_SHORT).show()
                                }
                            }) { Text("메일") }
                            else -> {}
                        }
                        TextButton(onClick = {
                            clipboard.setText(AnnotatedString(value))
                            Toast.makeText(context, "$label 복사했어요", Toast.LENGTH_SHORT).show()
                        }) { Text("복사") }
                    }
                }
            }
        },
        dismissButton = { TextButton(onClick = { open(SellerInfo.CHECK_URL) }) { Text("공정위에서 확인") } },
        confirmButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
    )
}
