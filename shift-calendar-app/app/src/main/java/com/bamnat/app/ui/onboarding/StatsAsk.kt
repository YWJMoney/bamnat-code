package com.bamnat.app.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * 1.39 [OBS2-05] 첫 설정 끝의 사용 통계 묻기 (사용자 2026-11-08 관측 범위 카드 '첫 설정에서 통계 동의 묻기').
 *
 * 1.38 [ONBOARD-05]의 체크 안 된 작은 줄은 지나치기 쉬워서, 실서버에서 사용 통계를 보내는 휴대폰이 하루 1대였어요(Play 월간 활성 6).
 * 같은 자리에서 작은 카드로 묻고, 사용자가 '보낼게요'·'보내지 않을래요' 중 하나를 직접 골라요. 두 단추는 같은 모양과 무게예요.
 * 기본값은 계속 꺼짐이고, 고르지 않고 넘어가도 꺼짐이에요(D42, 개인정보 보호법 제22조). 무엇이 가고 무엇이 안 가는지, 얼마나
 * 남는지는 '보낼 내용 보기'에서 고르기 전에 볼 수 있어요(제15조 제2항). 고른 뒤에는 한 줄로 접히고, 거기서 다시 바꿀 수 있어요.
 * 7차 PC 1-12부터는 요약 화면 본문이 아니라 첫 설정 아래 막대, '저장하고 달력 보기' 바로 위에 있어요(SetupHost).
 * 같은 값(설정 shareUsageStats)을 켜고 끄므로 첫 설정 누른 횟수(SETUP-20)와 설정 › 개인정보·앱 정보가 그대로 따라와요.
 */
@Composable
internal fun StatsAsk(on: Boolean, onChange: (Boolean) -> Unit) {
    // 이 화면에서 한 번 고르면 접혀요. 켜진 채로 들어왔으면(다시 설정 등) 이미 고른 것으로 봐요.
    var answered by rememberSaveable { mutableStateOf(on) }
    var show by rememberSaveable { mutableStateOf(false) }
    // 1.39 7차 PC 1-12: 요약 화면 맨 아래에 있으면 '이대로 저장할까요?'에서 스크롤해야 보여서, 저장 단추 바로 위(아래 막대)에 작게 둬요.
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        if (answered) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = 14.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Text(
                    if (on) "사용 통계를 보내요. 고마워요" else "사용 통계를 보내지 않아요",
                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { answered = false }) { Text("바꾸기") }
            }
        } else {
            Column(Modifier.padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.Insights, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Text("앱을 고치는 데 도움을 주실래요? (선택)", style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f).semantics { heading() })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "기능 이름과 횟수만 보내요. 근무·금액·메모는 보내지 않아요.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { show = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text("보낼 내용 보기") }
                }
                Row(Modifier.fillMaxWidth().padding(end = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onChange(false); answered = true }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                        Text("보내지 않을래요")
                    }
                    OutlinedButton(onClick = { onChange(true); answered = true }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                        Text("보낼게요")
                    }
                }
            }
        }
    }
    if (show) {
        AlertDialog(
            onDismissRequest = { show = false },
            title = { Text("보낼 내용") },
            text = { Text(STATS_WHAT + "\n\n보내지 않아도 앱은 똑같이 돼요. 설정 › 개인정보·앱 정보에서 언제든 바꿔요.") },
            confirmButton = { TextButton(onClick = { show = false }) { Text("확인") } },
        )
    }
}

/** 고르기 전에 보여 주는 목록. 처음 깐 휴대폰에는 아직 보낼 꾸러미가 없어서, 설정의 '보낼 내용 보기' 대신 무엇이 가는지를 말로 보여요. */
internal const val STATS_WHAT = "보내는 것\n" +
    "· 그날 쓴 기능 이름과 횟수 (예: 달력 열기 3번, 근무 바꾸기 1번)\n" +
    "· 앱 판 (예: 1.39.0)\n" +
    "· 처리방침 3.12 시행일부터: 오늘 처음 연 휴대폰인지, 며칠 만에 다시 열었는지 표시, 그리고 첫 설정에서 고른 방식과 누른 횟수(한 번)\n" +
    "· 같은 날부터: 고른 일하는 방식(교대·일근 등 하나), 다시 열었을 때 바로 전에 연 날 쓴 기능 이름과 7일·30일 안에 왔는지\n\n" +
    "보내지 않는 것\n" +
    "· 휴대폰 번호·기기 번호, 근무표, 금액, 메모·글, 위치, 설치한 날\n\n" +
    "서버에는 날짜별 합계만 400일 남고, 어느 휴대폰이 보냈는지는 남지 않아요. 앱이 켜지는 시간 같은 더 자세한 항목은 따로 묻고, 묻지 않으면 보내지 않아요."
